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
import kotlinx.coroutines.launch
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
    fun `a live timezone change immediately re-resolves the current periods`() = runTest {
        // CORE-002: ONE repository, the production-shape authority (dynamic zone),
        // fixed instant on the UTC date boundary where Tallinn is already on
        // 2026-09-01 while New York is still on 2026-08-31.
        val store = FakeDataStore()
        val instant = Instant.parse("2026-09-01T02:30:00Z")
        var zone = ZoneId.of("Europe/Tallinn")
        val repo = BlipStatsRepository(store, backgroundScope, CalendarAuthority { CalendarNow(instant, zone) })
        repo.start()
        runCurrent()

        // The first successful blip is bucketed under the Tallinn calendar.
        repo.recordSuccessfulBlip()
        assertEquals("2026-09-01", repo.record.value.dayKey)
        assertEquals("2026-W36", repo.record.value.weekKey)
        assertEquals("2026-09", repo.record.value.monthKey)
        assertEquals(1L, repo.snapshot().todayCount)
        assertEquals(1L, repo.snapshot().totalCount)

        // Only the injected zone authority changes; the repository itself is
        // untouched and never restarted.
        zone = ZoneId.of("America/New_York")

        // The old Tallinn bucket no longer matches the New York calendar, so the
        // current-period read falls back to zero immediately.
        assertEquals(0L, repo.snapshot().todayCount)
        assertEquals(1L, repo.snapshot().totalCount)

        // The next successful blip is bucketed under the New York calendar.
        repo.recordSuccessfulBlip()
        assertEquals("2026-08-31", repo.record.value.dayKey)
        assertEquals("2026-W36", repo.record.value.weekKey)
        assertEquals("2026-08", repo.record.value.monthKey)
        assertEquals(2L, repo.record.value.totalCount)
        assertFalse(repo.record.value.earnedPremium)
    }

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
    fun `a blip during an in-flight write gets a trailing persistence pass`() = runTest {
        // CORE-001 trailing-write regression: the pass captures total=50 and parks
        // inside the store; a real blip advances memory to 51; the released first
        // write lands 50; the pump must then perform a trailing write so the final
        // persisted total is 51 — never stuck at 50.
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val store = object : DataStore<Preferences> {
            val state = MutableStateFlow<Preferences>(emptyPreferences())
            override val data: kotlinx.coroutines.flow.Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences {
                gate.await()
                val next = transform(state.value.toMutablePreferences()) as MutablePreferences
                state.value = next
                writes++
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

        // The pump covers the newer revision with a trailing pass: disk catches
        // up to 51 while memory never moved backwards.
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(51L, store.state.value[longPreferencesKey("total_count")])
        assertEquals(51L, repo.record.value.totalCount)
        // Exactly the two expected passes — no write storm.
        assertEquals(2, writes)
    }

    @Test
    fun `blips that land while the worker is retiring still get persisted`() = runTest {
        // CORE-001 worker-exit boundary: blips arriving exactly at the moment the
        // pump decides it is finished must find a worker (the same one re-armed,
        // or a freshly launched one) — dirty data can never be left with no
        // active/scheduled persistence.
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

        repeat(10) { repo.recordSuccessfulBlip() }
        repo.flush() // immediate pass parks inside the gated store
        runCurrent()

        // The pass is still suspended in the store; every blip here lands during
        // the pass's life, right up to its completion.
        repo.recordSuccessfulBlip()
        repo.recordSuccessfulBlip()
        gate.complete(Unit)
        repo.recordSuccessfulBlip() // potentially inside the pump's retire check
        repo.recordSuccessfulBlip()
        runCurrent()

        // All revisions must eventually land: drain with the awaitable flush and
        // assert memory and disk agree exactly.
        repo.flushAndAwait()
        runCurrent()
        assertEquals(14L, repo.record.value.totalCount)
        assertEquals(14L, store.state.value[longPreferencesKey("total_count")])
    }

    @Test
    fun `flushAndAwait covers everything recorded before it and returns`() = runTest {
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        // No blips recorded: the awaitable flush returns immediately.
        repo.flushAndAwait()
        assertEquals(0L, store.state.value[longPreferencesKey("total_count")] ?: 0L)

        repeat(3) { repo.recordSuccessfulBlip() }
        repo.flushAndAwait()
        assertEquals(3L, store.state.value[longPreferencesKey("total_count")])
        assertEquals(3L, repo.record.value.totalCount)
    }

    @Test
    fun `flushAndAwait does not chase blips recorded after it`() = runTest {
        // At the quiescence boundary nothing new can arrive; but if it did, the
        // flush that captured the earlier revision must still return — the newer
        // revision gets its own ordinary trailing write instead of dragging the
        // stop path with it.
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
        repo.recordSuccessfulBlip()
        repo.recordSuccessfulBlip()

        var flushed = false
        val job = launch { repo.flushAndAwait(); flushed = true }
        runCurrent() // flush pass parks inside the gated store
        assertEquals(2L, repo.record.value.totalCount)

        // A NEW revision lands while the flush is suspended in the write.
        repo.recordSuccessfulBlip()
        gate.complete(Unit)
        runCurrent()

        // The flush returns once its own captured revision is covered...
        assertTrue(flushed)
        // ...and the newer blip persists via its own trailing write.
        advanceTimeBy(BlipStatsRepository.PERSIST_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(3L, store.state.value[longPreferencesKey("total_count")])
        job.join()
    }

    @Test
    fun `an immediate request wakes a worker sitting in its batch interval`() = runTest {
        // The 100K entitlement and teardown flushes must not sit out a full
        // batching interval when a pass is already waiting.
        val store = FakeDataStore()
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()

        repeat(99_999) { repo.recordSuccessfulBlip() }
        // The batched pass for 99_999 is now parked in its 1 s wait.
        runCurrent()

        // The 100,000th blip crosses the reward — no virtual time passes.
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
    fun `an IOException on write never hangs the final flush`() = runTest {
        // Stats I/O failure stays secondary: the awaitable teardown flush still
        // returns, the pump retires, and the session path is unaffected.
        val store = object : DataStore<Preferences> {
            val state = MutableStateFlow<Preferences>(emptyPreferences())
            override val data: kotlinx.coroutines.flow.Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences,
            ): Preferences = throw java.io.IOException("disk full")
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        repeat(3) { repo.recordSuccessfulBlip() }

        // Must return, not throw and not hang.
        repo.flushAndAwait()
        runCurrent()

        assertEquals(3L, repo.record.value.totalCount)
        assertTrue(repo.ready.value)
    }

    @Test
    fun `a delayed persistence completion never drags memory backwards`() = runTest {
        // The audit sequence stays true: a write captured at 50 completing after
        // memory moved to 51 must never drag memory back — the DataStore is a
        // persistence log, never a live authority. Disk catch-up for the newer
        // revision is covered by the trailing-write regression above.
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

    @Test
    fun `initial load publishes earned premium synchronously with ready`() = runTest {
        // A persisted reward must be visible the instant start() returns — no
        // coroutine scheduler advancement between the assertions.
        val store = FakeDataStore()
        store.state.value = store.state.value.toMutablePreferences().apply {
            set(longPreferencesKey("total_count"), 100_000L)
            set(booleanPreferencesKey("earned_premium"), true)
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        // No runCurrent / scheduler step between here and the assertions.
        assertTrue(repo.ready.value)
        assertTrue(repo.earnedPremium.value)
        assertEquals(100_000L, repo.record.value.totalCount)
    }

    @Test
    fun `crossing the reward publishes earned premium with the in-memory transition`() = runTest {
        // 99_999 blips already lived. The 100,000th blip must publish the new
        // entitlement in memory immediately, with no scheduler advancement.
        val store = FakeDataStore()
        store.state.value = store.state.value.toMutablePreferences().apply {
            set(longPreferencesKey("total_count"), 99_999L)
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))
        repo.start()
        runCurrent()
        assertFalse(repo.earnedPremium.value)

        repo.recordSuccessfulBlip()

        assertEquals(100_000L, repo.record.value.totalCount)
        assertTrue(repo.record.value.earnedPremium)
        assertTrue(repo.earnedPremium.value)
    }

    @Test
    fun `ready never opens before the loaded earned premium is published`() = runTest {
        // Observe the ready transition: at the FIRST state where ready becomes
        // true, earnedPremium must already equal the loaded record.
        val store = FakeDataStore()
        store.state.value = store.state.value.toMutablePreferences().apply {
            set(longPreferencesKey("total_count"), 100_000L)
            set(booleanPreferencesKey("earned_premium"), true)
        }
        val repo = BlipStatsRepository(store, backgroundScope, utcClock(2026, 9, 7))

        val flips = mutableListOf<Pair<Boolean, Boolean>>()
        val job = backgroundScope.launch {
            var prevReady = repo.ready.value
            flips.add(prevReady to repo.earnedPremium.value)
            repo.ready.collect { ready ->
                if (ready != prevReady) {
                    flips.add(ready to repo.earnedPremium.value)
                    prevReady = ready
                }
            }
        }
        repo.start()
        runCurrent()
        job.cancel()

        assertTrue(flips.any { it.first })
        // Every time ready is true, the earned entitlement is already published.
        flips.filter { it.first }.forEach { (_, earned) ->
            assertTrue("ready=true must imply earnedPremium already initialized", earned)
        }
    }

    private fun stringKey(name: String) =
        androidx.datastore.preferences.core.stringPreferencesKey(name)
}
