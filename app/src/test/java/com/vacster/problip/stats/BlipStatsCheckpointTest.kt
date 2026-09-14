package com.vacster.problip.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-001 focused evidence on virtual time: ordinary successful blips batch
 * into bounded [BlipStatsRepository.PERSIST_INTERVAL_MS] checkpoints whose write
 * count is independent of playback frequency, while the immediate paths (100K
 * reward, flush, flushAndAwait) stay instant and the CORE-001 trailing/target
 * semantics survive. All counts are measured against a fake store — no device,
 * battery or flash-wear measurement is claimed.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlipStatsCheckpointTest {

    private class FixedClock(start: ZonedDateTime) : Clock() {
        var instant = start.toInstant()
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    /** In-memory store that counts commits and can inject a write failure. */
    private open class CountingStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        var writes = 0
        var failNext: java.io.IOException? = null

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            failNext?.let { throw it }
            val next = transform(state.value.toMutablePreferences()) as MutablePreferences
            state.value = next
            writes++
            return next
        }
    }

    /** Store whose updateData parks on a gate before committing. */
    private class GatedStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val gate = CompletableDeferred<Unit>()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            gate.await()
            val next = transform(state.value.toMutablePreferences()) as MutablePreferences
            state.value = next
            return next
        }
    }

    private fun utcClock(y: Int, m: Int, d: Int): FixedClock =
        FixedClock(ZonedDateTime.of(y, m, d, 0, 0, 0, 0, ZoneId.of("UTC")))

    private val interval = BlipStatsRepository.PERSIST_INTERVAL_MS

    @Test
    fun `several blips before the first checkpoint produce one write covering all of them`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(7) { repo.recordSuccessfulBlip() }
        assertEquals("nothing persisted inside the checkpoint window", 0, store.writes)
        assertEquals(7L, repo.record.value.totalCount)

        advanceTimeBy(interval + 1)
        runCurrent()

        assertEquals("one write covers the whole pre-window burst", 1, store.writes)
        assertEquals(7L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `ten minutes at five second playback stays checkpoint bounded`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        // 600 s at one blip per 5 s = 120 blips; each interval window coalesces ~6.
        val windows = 600_000L / interval
        repeat(windows.toInt()) {
            advanceTimeBy(interval)
            repeat((interval / 5_000L).toInt()) { repo.recordSuccessfulBlip() }
            runCurrent()
            // Inside a window at most one write lands.
            assertTrue(store.writes <= it + 1)
        }
        advanceTimeBy(interval + 1)
        runCurrent()

        assertEquals(120L, repo.record.value.totalCount)
        assertEquals(120L, store.state.value[longPreferencesKey("total_count")])
        // Write count is bounded by the number of windows, not the 120 blips.
        assertTrue(
            "writes (${store.writes}) must be window-bounded, not blip-bounded",
            store.writes <= windows.toInt() + 1,
        )
    }

    @Test
    fun `ten minutes at one second playback stays the same order of write count`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        // 600 s at one blip per 1 s = 600 blips — six times the blip rate of the
        // 5 s scenario, but the SAME window-bounded write count.
        val windows = 600_000L / interval
        repeat(windows.toInt()) {
            advanceTimeBy(interval)
            repeat((interval / 1_000L).toInt()) { repo.recordSuccessfulBlip() }
            runCurrent()
        }
        advanceTimeBy(interval + 1)
        runCurrent()

        assertEquals(600L, repo.record.value.totalCount)
        assertEquals(600L, store.state.value[longPreferencesKey("total_count")])
        assertTrue(
            "1 s playback writes (${store.writes}) must stay window-bounded ($windows), not blip-bounded",
            store.writes <= windows.toInt() + 1,
        )
    }

    @Test
    fun `no dirty data means zero writes`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        // Ten minutes of idleness: no blip, no flush — no write at all.
        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(0, store.writes)
    }

    @Test
    fun `crossing 100K wakes the checkpoint immediately`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(99_999) { repo.recordSuccessfulBlip() }
        runCurrent()
        assertEquals("parked inside the ordinary window", 0, store.writes)

        repo.recordSuccessfulBlip() // 100_000: immediate, no virtual time passes
        runCurrent()
        assertTrue(store.writes >= 1)
        assertEquals(true, store.state.value[booleanPreferencesKey("earned_premium")])
        assertEquals(100_000L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `flushAndAwait wakes the checkpoint immediately`() = runTest {
        val store = CountingStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(4) { repo.recordSuccessfulBlip() }
        runCurrent()
        assertEquals(0, store.writes) // parked in the ordinary window

        repo.flushAndAwait() // must not wait out the full interval
        runCurrent()
        assertEquals(1, store.writes)
        assertEquals(4L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `a blip during a physical write produces exactly one trailing pass`() = runTest {
        val store = GatedStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(50) { repo.recordSuccessfulBlip() }
        repo.flush()
        runCurrent() // write of 50 parks inside the store

        // A real blip lands while the physical write is suspended.
        repo.recordSuccessfulBlip() // memory 51 during the write
        // The old write lands its captured 50; the newer revision stays dirty.
        store.gate.complete(Unit)
        runCurrent()
        assertEquals("old write lands its captured 50", 50L, store.state.value[longPreferencesKey("total_count")])
        assertEquals("memory never moved backwards", 51L, repo.record.value.totalCount)

        // The trailing pass is checkpoint-bounded: the ordinary blip does not
        // force an extra write, but the very next window covers revision 51.
        advanceTimeBy(interval + 1)
        runCurrent()
        assertEquals(51L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `immediate request during a physical write skips the next ordinary delay`() = runTest {
        val store = GatedStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(10) { repo.recordSuccessfulBlip() }
        repo.flush()
        runCurrent() // parks inside the store

        // flushAndAwait during the physical write sets immediateRequested, so
        // the trailing pass must run WITHOUT another interval window. The
        // record still holds the same 10 blips, so the committed value is 10.
        var flushed = false
        val job = launch {
            repo.flushAndAwait()
            flushed = true
        }
        store.gate.complete(Unit)
        runCurrent()
        runCurrent()
        assertTrue("trailing immediate pass must not wait another interval", flushed)
        job.join()
        assertEquals(10L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `flush target does not chase future revisions`() = runTest {
        val store = GatedStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(2) { repo.recordSuccessfulBlip() }

        var flushed = false
        val job = launch {
            repo.flushAndAwait()
            flushed = true
        }
        runCurrent() // flush pass parks in the store
        repo.recordSuccessfulBlip() // newer revision during the write
        store.gate.complete(Unit)
        runCurrent()
        assertTrue("flush returns once its captured revision is covered", flushed)
        job.join()
    }

    @Test
    fun `expected IOException does not hang or fail the session path`() = runTest {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences = throw java.io.IOException("disk full")
        }
        val repo = BlipStatsRepository(failing, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(3) { repo.recordSuccessfulBlip() }

        repo.flushAndAwait() // must return, not hang, not throw
        runCurrent()
        assertEquals(3L, repo.record.value.totalCount)
        assertTrue(repo.ready.value)
    }
}
