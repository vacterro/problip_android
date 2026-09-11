package com.vacster.problip.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.vacster.problip.core.BlipPlayer
import com.vacster.problip.core.BlipScheduler
import com.vacster.problip.core.DelayBoundary
import com.vacster.problip.core.IntervalConfig
import com.vacster.problip.core.ProblipState
import com.vacster.problip.stats.BlipStatsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CORE-001 teardown-boundary coverage for the real [BlipScheduler] + real
 * [BlipStatsRepository] wired the way [ProblipService] wires them: a successful
 * playback records through the stats hook, and teardown runs the service's exact
 * order — quiesce the scheduler, then await the final catch-up flush — before
 * taking the persistence snapshot. Exercises the controllable playback boundary
 * that the unit-level scheduler test cannot, on real threads with a real time base.
 */
class TeardownQuiescenceTest {

    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: kotlinx.coroutines.flow.Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value.toMutablePreferences()) as MutablePreferences
            state.value = next
            return next
        }
    }

    private fun utcClock(): Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = LocalDate.of(2026, 9, 7).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    @Test
    fun teardownWaitsForTheInFlightPlayThenPersistsItsBlipExactlyOnce() {
        val playEntered = CountDownLatch(1)
        val releasePlay = CountDownLatch(1)
        val plays = AtomicInteger(0)

        val store = FakeDataStore()
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val stats = BlipStatsRepository(store, scope, utcClock())

        val scheduler = BlipScheduler(
            scope = scope,
            player = BlipPlayer {
                plays.incrementAndGet()
                playEntered.countDown()
                releasePlay.await(5, TimeUnit.SECONDS)
                // The ONE successful-playback boundary the service uses: the count
                // is recorded here, inside the play, exactly like BlipPlaybackHook.
                stats.recordSuccessfulBlip()
                true
            },
            delayBoundary = DelayBoundary { kotlinx.coroutines.delay(it) },
            initialInterval = IntervalConfig.Fixed(5 * 60 * 1000), // far apart: no auto second play
        )

        try {
            runBlocking { stats.start() }
            scheduler.start()

            // The loop is now inside player.play(), the play not yet resolved.
            assertTrue(playEntered.await(5, TimeUnit.SECONDS))

            // STOP begins — the service's teardown order, off the main thread.
            val teardownDone = java.util.concurrent.CompletableFuture<Unit>()
            scope.launch {
                scheduler.stopAndAwaitQuiescence()
                stats.flushAndAwait()
                teardownDone.complete(Unit)
            }

            // While the in-flight play is still resolving, the final persistence
            // snapshot must NOT have been taken yet.
            Thread.sleep(200)
            assertFalse("teardown must wait for the in-flight play", teardownDone.isDone)
            assertEquals(
                "disk must not be snapshotted before the play resolves",
                0L,
                store.state.value[longPreferencesKey("total_count")] ?: 0L,
            )

            // The play resolves successfully — its blip must be counted once.
            releasePlay.countDown()
            runBlocking { teardownDone.get(5, TimeUnit.SECONDS) }

            assertEquals(1, plays.get())
            assertEquals(1L, stats.record.value.totalCount)
            assertEquals(1L, store.state.value[longPreferencesKey("total_count")])
            assertEquals(ProblipState.STOPPED, scheduler.state.value)

            // No further playback after quiescence.
            Thread.sleep(200)
            assertEquals("counted exactly once", 1, plays.get())
        } finally {
            releasePlay.countDown()
            scheduler.stop()
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test
    fun teardownWithoutInFlightPlaybackIsPromptAndIdempotent() {
        val playEntered = CountDownLatch(1)
        val store = FakeDataStore()
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val stats = BlipStatsRepository(store, scope, utcClock())

        val scheduler = BlipScheduler(
            scope = scope,
            player = BlipPlayer {
                playEntered.countDown()
                stats.recordSuccessfulBlip()
                true
            },
            delayBoundary = DelayBoundary { kotlinx.coroutines.delay(it) },
            initialInterval = IntervalConfig.Fixed(5 * 60 * 1000),
        )

        try {
            runBlocking { stats.start() }
            scheduler.start()
            assertTrue(playEntered.await(5, TimeUnit.SECONDS))

            // Two concurrent teardowns (repeated STOP) — no deadlock, no duplicate
            // counts, no competing flushes that strand or double-count data.
            val done1 = java.util.concurrent.CompletableFuture<Unit>()
            val done2 = java.util.concurrent.CompletableFuture<Unit>()
            scope.launch {
                scheduler.stopAndAwaitQuiescence()
                stats.flushAndAwait()
                done1.complete(Unit)
            }
            scope.launch {
                scheduler.stopAndAwaitQuiescence()
                stats.flushAndAwait()
                done2.complete(Unit)
            }

            runBlocking {
                done1.get(5, TimeUnit.SECONDS)
                done2.get(5, TimeUnit.SECONDS)
            }

            // playEntered already fired exactly once (count reached 0) and no
            // second playback can have happened: no duplicate counts.
            assertEquals(0, playEntered.count.toInt())
            assertEquals(1L, stats.record.value.totalCount)
            assertEquals(1L, store.state.value[longPreferencesKey("total_count")])
        } finally {
            scheduler.stop()
            scope.cancel()
            dispatcher.close()
        }
    }
}