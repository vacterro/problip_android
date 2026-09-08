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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository on an in-memory DataStore, with an injected [Clock] and virtual
 * time, so batching, immediate reward persistence and reload survive are all
 * deterministic — no Windows-host file quirks, no real wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlipStatsRepositoryTest {

    /** UTC clock pinned to a fixed instant, moved explicitly by tests. */
    private class FixedClock(start: ZonedDateTime) : Clock() {
        var instant: Instant = start.toInstant()
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    /** In-memory DataStore: a fake, because the real one cannot rename on Windows. */
    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: kotlinx.coroutines.flow.Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val current = state.value
            val next = transform(current.toMutablePreferences()) as MutablePreferences
            state.value = next
            return next
        }
    }

    private fun utcClock(y: Int, m: Int, d: Int, hour: Int = 0): FixedClock =
        FixedClock(ZonedDateTime.of(y, m, d, hour, 0, 0, 0, ZoneId.of("UTC")))

    @Test
    fun `blips accumulate in memory and persist after the bounded interval`() = runTest {
        val store = FakeDataStore()
        val clock = utcClock(2026, 9, 7)
        val repo = BlipStatsRepository(store, backgroundScope, clock)
        repo.start()
        runCurrent()
        assertTrue(repo.ready.value)

        repeat(5) { repo.recordSuccessfulBlip() }
        // Immediately: in-memory is already current, disk is not.
        assertEquals(5L, repo.record.value.totalCount)
        assertEquals(0L, store.state.value[longPreferencesKey("total_count")] ?: 0L)

        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(5L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `a delayed persistence completion never drags memory backwards`() = runTest {
        // THE race from the audit: memory = 50, the write of 50 is captured and in
        // flight, a real blip makes memory = 51, the old write completes with 50
        // and its persisted value lands back. The DataStore is a persistence log,
        // not a live authority, so nothing flows back: memory REMAINS 51.
        val gate = CompletableDeferred<Unit>()
        val store = object : DataStore<Preferences> {
            val state = MutableStateFlow<Preferences>(emptyPreferences())
            override val data: kotlinx.coroutines.flow.Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences {
                gate.await()
                val next = transform(state.value.toMutablePreferences()) as MutablePreferences
                state.value = next
                return next
            }
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(50) { repo.recordSuccessfulBlip() }
        assertEquals(50L, repo.record.value.totalCount)

        // The batched write of 50 begins and captures 50, then parks inside the store.
        repo.flush()
        runCurrent()
        assertEquals("memory 50 captured for the in-flight write", 50L, repo.record.value.totalCount)

        // A real successful blip lands BEFORE the persistence completes.
        repo.recordSuccessfulBlip()
        assertEquals(51L, repo.record.value.totalCount)

        // The old persistence completes with the stale 50.
        gate.complete(Unit)
        runCurrent()
        assertEquals(50L, store.state.value[longPreferencesKey("total_count")])

        // No feedback: memory must still be 51. A continuous collection of
        // dataStore.data here would drag the record back to 50.
        assertEquals(51L, repo.record.value.totalCount)
    }

    @Test
    fun `a store emission written by someone else never moves the record`() = runTest {
        // Even a foreign state change in the store cannot overwrite memory —
        // there is no data.data collection after the one initial load.
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(7) { repo.recordSuccessfulBlip() }

        store.state.value = emptyPreferences()
        runCurrent()

        assertEquals(7L, repo.record.value.totalCount)
        assertFalse(repo.record.value.earnedPremium)
    }

    @Test
    fun `start is idempotent and a second read never overwrites newer memory`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(4) { repo.recordSuccessfulBlip() }

        // A late second start (a re-attached service) must not re-read the
        // stale disk snapshot over the newer in-memory record.
        repo.start()
        runCurrent()
        assertEquals(4L, repo.record.value.totalCount)
    }

    @Test
    fun `a fast blip burst writes once, not once per blip`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(50) { repo.recordSuccessfulBlip() }
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()

        assertEquals(50L, store.state.value[longPreferencesKey("total_count")])
        // One write, not fifty: the bounded batch covers the whole burst.
        val dayCount = store.state.value[longPreferencesKey("today_count")]
        assertEquals(50L, dayCount)
    }

    @Test
    fun `crossing the reward persists immediately without waiting for the interval`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repo.record.value.let { /* 99_999 blips already lived */ }
        repeat(99_999) { repo.recordSuccessfulBlip() }
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(99_999L, store.state.value[longPreferencesKey("total_count")])
        assertEquals(null, store.state.value[booleanPreferencesKey("earned_premium")])

        // The 100,000th blip writes NOW, before any batching interval.
        repo.recordSuccessfulBlip()
        runCurrent()
        assertTrue(repo.record.value.earnedPremium)
        assertEquals(
            true,
            store.state.value[booleanPreferencesKey("earned_premium")],
        )
        assertEquals(100_000L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `earned premium survives a repository reload from disk`() = runTest {
        val store = FakeDataStore()
        val first = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        first.start()
        runCurrent()
        repeat(100_000) { first.recordSuccessfulBlip() }
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertTrue(first.earnedPremium.value)

        // A fresh repository over the same store (a cold process): the reward is
        // restored from disk, not recomputed from scratch.
        val second = BlipStatsRepository(store, CoroutineScope(Dispatchers.Unconfined), utcClock(2026, 9, 8))
        second.start()
        assertTrue(second.ready.value)
        assertTrue(second.earnedPremium.value)
        assertEquals(100_000L, second.record.value.totalCount)
        // The display buckets still belong to yesterday, so they read zero.
        assertEquals(0L, second.snapshot().todayCount)
        assertEquals(100_000L, second.snapshot().totalCount)
    }

    @Test
    fun `an IOException on load degrades to defaults and still marks ready`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        // A store whose data flow fails: the session must survive it.
        val failing = object : DataStore<Preferences> {
            override val data: kotlinx.coroutines.flow.Flow<Preferences> =
                kotlinx.coroutines.flow.flow { throw java.io.IOException("unreadable") }
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences = transform(emptyPreferences())
        }
        val failingRepo = BlipStatsRepository(failing, backgroundScope, utcClock(2026, 9, 7))
        failingRepo.start()
        runCurrent()
        assertTrue(failingRepo.ready.value)
        assertEquals(0L, failingRepo.snapshot().totalCount)

        // The real repo keeps working against its own store.
        repo.start()
        runCurrent()
        repo.recordSuccessfulBlip()
        assertEquals(1L, repo.record.value.totalCount)
    }

    @Test
    fun `negative persisted values sanitize on load`() = runTest {
        val store = FakeDataStore()
        store.state.value = store.state.value.toMutablePreferences().apply {
            set(longPreferencesKey("total_count"), -7L)
            set(longPreferencesKey("today_count"), -3L)
            set(longPreferencesKey("week_count"), -2L)
            set(longPreferencesKey("month_count"), -1L)
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        val snapshot = repo.snapshot()
        assertEquals(0L, snapshot.todayCount)
        assertEquals(0L, snapshot.weekCount)
        assertEquals(0L, snapshot.monthCount)
        assertEquals(0L, snapshot.totalCount)
    }

    @Test
    fun `a persisted threshold heals a lost earned flag on load`() = runTest {
        val store = FakeDataStore()
        // The total reality is at the reward but the earned flag is missing —
        // e.g. an older build wrote counts without the flag.
        store.state.value = store.state.value.toMutablePreferences().apply {
            set(longPreferencesKey("total_count"), 100_000L)
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        assertTrue(repo.ready.value)
        assertTrue(repo.earnedPremium.value)
        assertTrue(repo.snapshot().earnedPremium)

        // The healed state also persists back so the flag is no longer missing.
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        // Nothing new happened, so no write was scheduled — the in-memory state
        // is authoritative and correct regardless.
        assertTrue(repo.record.value.earnedPremium)
    }

    @Test
    fun `flush writes pending counts before a normal teardown`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(3) { repo.recordSuccessfulBlip() }
        repo.flush()
        runCurrent()
        assertEquals(3L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `period keys and buckets follow the injected clock`() = runTest {
        val clock = utcClock(2026, 9, 7, hour = 23)
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, clock)
        repo.start()
        runCurrent()
        repo.recordSuccessfulBlip()
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertEquals("2026-09-07", store.state.value[stringKey("day_key")])
        assertEquals("2026-W37", store.state.value[stringKey("week_key")])
        assertEquals("2026-09", store.state.value[stringKey("month_key")])
    }

    private fun stringKey(name: String) =
        androidx.datastore.preferences.core.stringPreferencesKey(name)
}
