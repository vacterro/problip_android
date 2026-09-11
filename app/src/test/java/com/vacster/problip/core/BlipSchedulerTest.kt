package com.vacster.problip.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking

/** Records every requested delay and still virtually suspends for it. */
private class RecordingDelay : DelayBoundary {
    val requested = mutableListOf<Long>()
    override suspend fun delay(ms: Long) {
        requested += ms
        kotlinx.coroutines.delay(ms)
    }
}

private class RecordingPlayer : BlipPlayer {
    var plays = 0
    var failEveryPlay = false
    override fun play(): Boolean {
        if (failEveryPlay) return false
        plays++
        return true
    }
}

/** Deterministic random source cycling through a fixed script. */
private class ScriptedRandom(vararg script: Long) : RandomSource {
    private val values = script
    private var i = 0
    override fun nextLong(fromInclusive: Long, toInclusive: Long): Long = values[i++ % values.size]
}

private class Harness(
    scope: CoroutineScope,
    interval: IntervalConfig,
    random: RandomSource = ScriptedRandom(4000, 7000, 5500),
) {
    val delays = RecordingDelay()
    val player = RecordingPlayer()
    val scheduler = BlipScheduler(scope, player, delays, random, interval)
}

class BlipSchedulerTest {

    // 1. random delays remain in 4000..7000 ms
    @Test
    fun randomDelaysStayWithin4000To7000Ms() = runTest {
        val h = Harness(backgroundScope, IntervalConfig.Random(4000, 7000))
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(60_000)
        // The loop still runs, so every play has been followed by exactly one wait request,
        // preceded by the single initial delay.
        assertEquals(h.player.plays + 1, h.delays.requested.size)
        assertEquals(500L, h.delays.requested.first())
        val script = listOf(4000L, 7000L, 5500L)
        h.delays.requested.drop(1).forEachIndexed { i, ms ->
            assertTrue("delay $ms outside 4000..7000", ms in 4000..7000)
            assertEquals(script[i % script.size], ms)
        }
        h.scheduler.stop()
    }

    // 2. every fixed interval is exact
    @Test
    fun everyFixedIntervalIsExact() = runTest {
        val fixedModes = mapOf(
            IntervalMode.FIXED_5S to 5_000L,
            IntervalMode.FIXED_10S to 10_000L,
            IntervalMode.FIXED_15S to 15_000L,
            IntervalMode.FIXED_20S to 20_000L,
            IntervalMode.FIXED_30S to 30_000L,
        )
        for ((mode, ms) in fixedModes) {
            val h = Harness(backgroundScope, mode.toConfig(), random = ScriptedRandom(-1))
            h.scheduler.start()
            runCurrent()
            advanceTimeBy(500L + 3 * ms + 1)
            // Plays at 500, +ms, +2ms, +3ms; after the last play the next wait is
            // already requested, hence one entry more than the play count.
            assertEquals(List(5) { if (it == 0) 500L else ms }, h.delays.requested)
            assertEquals(4, h.player.plays)
            h.scheduler.stop()
        }
    }

    // 3. initial delay behaviour
    @Test
    fun firstPlaybackHappensOnlyAfter500MsInitialDelay() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.start()
        assertEquals(ProblipState.STARTING, h.scheduler.state.value)
        runCurrent()
        assertEquals(0, h.player.plays)
        advanceTimeBy(499)
        assertEquals(0, h.player.plays)
        advanceTimeBy(2)
        assertEquals(1, h.player.plays)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        // The initial delay plus the already-requested first 5 s wait.
        assertEquals(listOf(500L, 5_000L), h.delays.requested)
        h.scheduler.stop()
    }

    // 4. Start begins scheduling
    @Test
    fun startBeginsSchedulingAndReachesRunning() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(5_501)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        assertEquals(2, h.player.plays)
        h.scheduler.stop()
    }

    // 5. repeated Start does not duplicate scheduling
    @Test
    fun repeatedStartDoesNotDuplicateTheLoop() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.start()
        runCurrent()
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(15_501)
        // One loop plays at 500, 5500, 10500, 15500. A duplicate loop would double this.
        assertEquals(4, h.player.plays)
        assertEquals(1, h.delays.requested.count { it == 500L })
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        h.scheduler.stop()
    }

    // 6. Stop prevents future playback
    @Test
    fun stopCancelsAllFuturePlayback() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        assertEquals(1, h.player.plays)
        h.scheduler.stop()
        assertEquals(ProblipState.STOPPED, h.scheduler.state.value)
        val requestedAtStop = h.delays.requested.size
        advanceTimeBy(120_000)
        assertEquals(1, h.player.plays)
        assertEquals(requestedAtStop, h.delays.requested.size)
    }

    // 7. repeated Stop is safe
    @Test
    fun repeatedStopIsHarmless() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.stop()
        h.scheduler.stop()
        assertEquals(ProblipState.STOPPED, h.scheduler.state.value)
        h.scheduler.start()
        runCurrent()
        h.scheduler.stop()
        h.scheduler.stop()
        assertEquals(ProblipState.STOPPED, h.scheduler.state.value)
        assertEquals(0, h.player.plays)
    }

    // 8. Start after Stop works
    @Test
    fun startAfterStopWorks() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        assertEquals(1, h.player.plays)
        h.scheduler.stop()
        advanceTimeBy(20_000)
        assertEquals(1, h.player.plays)

        h.scheduler.start()
        runCurrent()
        assertEquals(ProblipState.STARTING, h.scheduler.state.value)
        advanceTimeBy(502)
        assertEquals(2, h.player.plays)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        // Session 1: initial + first wait; session 2: initial + its first wait.
        assertEquals(listOf(500L, 5_000L, 500L, 5_000L), h.delays.requested)
        h.scheduler.stop()
    }

    // ERROR is part of the W1 state list: playback failure must not look like RUNNING.
    @Test
    fun playbackFailureEntersErrorAndStopsScheduling() = runTest {
        val h = Harness(backgroundScope, IntervalMode.FIXED_5S.toConfig())
        h.player.failEveryPlay = true
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        assertEquals(ProblipState.ERROR, h.scheduler.state.value)
        assertEquals(0, h.player.plays)
        assertEquals(listOf(500L), h.delays.requested)

        advanceTimeBy(60_000)
        assertEquals(0, h.player.plays)
        assertEquals(listOf(500L), h.delays.requested)

        // A later START retries cleanly once the failure is gone.
        h.player.failEveryPlay = false
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(502)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        assertEquals(1, h.player.plays)
        h.scheduler.stop()
    }

    @Test
    fun intervalConfigMathIsExact() {
        // Fixed ignores the random source entirely.
        assertEquals(5_000L, IntervalConfig.Fixed(5_000).nextDelayMs(ScriptedRandom(-999)))
        // Random consults the source within its declared bounds.
        assertEquals(6_500L, IntervalConfig.Random(4000, 7000).nextDelayMs(ScriptedRandom(6500)))
        // Modes map to the product-defined values.
        assertEquals(IntervalConfig.Random(4_000, 7_000), IntervalMode.RANDOM_4_7.toConfig())
        assertEquals(IntervalConfig.Fixed(5_000), IntervalMode.FIXED_5S.toConfig())
        assertEquals(IntervalConfig.Fixed(10_000), IntervalMode.FIXED_10S.toConfig())
        assertEquals(IntervalConfig.Fixed(15_000), IntervalMode.FIXED_15S.toConfig())
        assertEquals(IntervalConfig.Fixed(20_000), IntervalMode.FIXED_20S.toConfig())
        assertEquals(IntervalConfig.Fixed(30_000), IntervalMode.FIXED_30S.toConfig())
        assertEquals(IntervalConfig.Pulse(5_000, 10_000, 20_000), IntervalMode.PULSE.toConfig())
        assertEquals(IntervalMode.RANDOM_4_7.toConfig(), IntervalConfig.DEFAULT)
    }

    // PULSE: 5 s, then a deliberately generated random 10-20 s, forever.

    @Test
    fun pulseStartsShortAndThenAlternatesLongAndShort() = runTest {
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = ScriptedRandom(15_000, 20_000, 11_000),
        )
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(500L + 5_000 + 15_000 + 5_000 + 20_000 + 5_000 + 1)
        assertEquals(
            listOf(500L, 5_000L, 15_000L, 5_000L, 20_000L, 5_000L, 11_000L),
            h.delays.requested,
        )
        h.scheduler.stop()
    }

    @Test
    fun pulseAsksTheInjectedRandomForTenToTwentySecondsOnly() = runTest {
        val bounds = mutableListOf<Pair<Long, Long>>()
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = RandomSource { from, to ->
                bounds += from to to
                from
            },
        )
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(60_000)
        // Every long slot re-rolls, and always over the same inclusive window.
        assertTrue(bounds.isNotEmpty())
        assertTrue(bounds.all { it == 10_000L to 20_000L })
        h.scheduler.stop()
    }

    @Test
    fun stopDiscardsThePulsePhaseAndStartBeginsShortAgain() = runTest {
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = ScriptedRandom(15_000),
        )
        h.scheduler.start()
        runCurrent()
        // Stops mid-cycle, right after the first LONG slot was requested.
        advanceTimeBy(500L + 5_000 + 1)
        assertEquals(listOf(500L, 5_000L, 15_000L), h.delays.requested)
        h.scheduler.stop()

        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        assertEquals(
            listOf(500L, 5_000L, 15_000L, 500L, 5_000L),
            h.delays.requested,
        )
        h.scheduler.stop()
    }

    @Test
    fun switchingIntoPulseWhileRunningResetsToTheShortSlot() = runTest {
        val h = Harness(
            backgroundScope,
            IntervalMode.FIXED_30S.toConfig(),
            random = ScriptedRandom(15_000),
        )
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        assertEquals(listOf(500L, 30_000L), h.delays.requested)

        // Same scheduler, same loop: only the interval changes.
        h.scheduler.interval = IntervalMode.PULSE.toConfig()
        advanceTimeBy(30_000)
        assertEquals(listOf(500L, 30_000L, 5_000L), h.delays.requested)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        h.scheduler.stop()
    }

    @Test
    fun leavingPulseAndComingBackResetsToTheShortSlot() = runTest {
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = ScriptedRandom(15_000),
        )
        h.scheduler.start()
        runCurrent()
        // 500, SHORT 5000, LONG 15000 requested.
        advanceTimeBy(500L + 5_000 + 1)
        h.scheduler.interval = IntervalMode.FIXED_10S.toConfig()
        advanceTimeBy(15_000)
        h.scheduler.interval = IntervalMode.PULSE.toConfig()
        advanceTimeBy(10_000)
        assertEquals(
            listOf(500L, 5_000L, 15_000L, 10_000L, 5_000L),
            h.delays.requested,
        )
        h.scheduler.stop()
    }

    @Test
    fun reassigningTheSamePulseConfigNeverResetsThePhase() = runTest {
        // The service re-assigns the interval on every settings emission (volume,
        // purchases, pool changes). An equal value must not restart the alternation.
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = ScriptedRandom(15_000, 12_000),
        )
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        repeat(5) { h.scheduler.interval = IntervalMode.PULSE.toConfig() }
        advanceTimeBy(5_000)
        assertEquals(listOf(500L, 5_000L, 15_000L), h.delays.requested)
        h.scheduler.stop()
    }

    @Test
    fun losingPulseAccessWhileRunningKeepsOneLoopAndFallsBackToRandom() = runTest {
        // What an expiring PULSE trial does: the service swaps in the free preset,
        // never stopping the session or creating a second scheduler.
        val h = Harness(
            backgroundScope,
            IntervalMode.PULSE.toConfig(),
            random = ScriptedRandom(4_500, 6_000),
        )
        h.scheduler.start()
        runCurrent()
        advanceTimeBy(501)
        val playsBefore = h.player.plays

        h.scheduler.interval = IntervalConfig.DEFAULT
        advanceTimeBy(5_000)
        assertEquals(ProblipState.RUNNING, h.scheduler.state.value)
        assertEquals(playsBefore + 1, h.player.plays)
        // The 5 s slot that was already waiting completes, then free random 4-7.
        assertEquals(listOf(500L, 5_000L, 4_500L), h.delays.requested)
        advanceTimeBy(4_500)
        assertEquals(listOf(500L, 5_000L, 4_500L, 6_000L), h.delays.requested)
        h.scheduler.stop()
    }

    @Test
    fun productionRandomSourceHitsInclusiveBounds() {
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        repeat(500_000) {
            val v = KotlinRandomSource.nextLong(4000, 7000)
            if (v < min) min = v
            if (v > max) max = v
        }
        assertEquals(4000L, min)
        assertEquals(7000L, max)
    }

    /**
     * W12 race regression, real threads and real time on purpose. START and STOP
     * used to share a Mutex.tryLock(), so whichever call lost the race was
     * dropped without a trace: a STOP arriving while START still held the lock
     * returned as if it had worked, published STOPPED and left the loop blipping.
     *
     * [SlowStartDispatcher] holds START inside its critical section long enough
     * for STOP to arrive there, which is the exact interleaving that used to be
     * lost. STOP must win: after it returns, nothing may play again.
     */
    @Test
    fun stopIsNeverDroppedWhenItRacesWithStart() {
        val plays = AtomicInteger(0)
        val dispatcher = SlowStartDispatcher(holdMs = 300)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = BlipScheduler(
            scope = scope,
            player = BlipPlayer {
                plays.incrementAndGet()
                true
            },
            delayBoundary = DelayBoundary { kotlinx.coroutines.delay(50) },
            initialInterval = IntervalConfig.Fixed(50),
        )
        try {
            val starter = thread { scheduler.start() }
            Thread.sleep(50) // START is inside its critical section by now
            scheduler.stop() // the call that used to be silently dropped
            assertEquals(ProblipState.STOPPED, scheduler.state.value)

            val playsAtStop = plays.get()
            Thread.sleep(400) // a surviving loop blips every 5 ms
            assertEquals("a dropped STOP left a live loop", playsAtStop, plays.get())
            starter.join()
        } finally {
            scheduler.stop()
            scope.cancel()
            dispatcher.close()
        }
    }

    /**
     * CORE-001 quiescence, real threads on purpose: the play parks like a real
     * engine mid-play, STOP begins while it is in flight, and the awaitable stop
     * must not return until that play has resolved — the resolved success still
     * counts exactly once, and no new iteration can begin after the boundary.
     * (The full teardown composition with the repository lives in
     * TeardownQuiescenceTest; the virtual-time suite cannot park the play without
     * blocking its single test thread.)
     */
    @Test
    fun stopAndAwaitQuiescenceWaitsForAnInFlightPlayToResolve() {
        val playEntered = CountDownLatch(1)
        val releasePlay = CountDownLatch(1)
        val plays = AtomicInteger(0)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = BlipScheduler(
            scope = scope,
            player = BlipPlayer {
                plays.incrementAndGet()
                playEntered.countDown()
                releasePlay.await(5, TimeUnit.SECONDS)
                true
            },
            delayBoundary = DelayBoundary { kotlinx.coroutines.delay(it) },
            initialInterval = IntervalConfig.Fixed(5_000),
        )
        try {
            scheduler.start()
            assertTrue(playEntered.await(5, TimeUnit.SECONDS))

            var quiesced = false
            val stopper = kotlin.concurrent.thread {
                runBlocking { scheduler.stopAndAwaitQuiescence() }
                quiesced = true
            }
            Thread.sleep(200)
            assertFalse("quiescence must wait for the in-flight play", quiesced)

            // The in-flight play resolves successfully and still counts.
            releasePlay.countDown()
            stopper.join(5_000)
            assertTrue(quiesced)
            assertEquals(1, plays.get())
            assertEquals(ProblipState.STOPPED, scheduler.state.value)

            // No new iteration can begin: the loop is fully terminated.
            Thread.sleep(200)
            assertEquals(1, plays.get())
        } finally {
            releasePlay.countDown()
            scheduler.stop()
            scope.cancel()
            dispatcher.close()
        }
    }

    @Test
    fun stopAndAwaitQuiescenceIsIdempotentAndPromptWithNoLoop() = runTest {
        val scheduler = BlipScheduler(
            scope = backgroundScope,
            player = BlipPlayer { true },
            delayBoundary = DelayBoundary { kotlinx.coroutines.delay(it) },
            initialInterval = IntervalConfig.Fixed(5_000),
        )
        // No loop ever ran: both awaits return immediately (prompt STOP).
        scheduler.stopAndAwaitQuiescence()
        scheduler.stopAndAwaitQuiescence()
        assertEquals(ProblipState.STOPPED, scheduler.state.value)

        // After a run: repeated awaitable stop is a no-op, not a deadlock.
        scheduler.start()
        advanceTimeBy(600)
        runCurrent()
        scheduler.stopAndAwaitQuiescence()
        scheduler.stopAndAwaitQuiescence()
        assertEquals(ProblipState.STOPPED, scheduler.state.value)
    }
}

/**
 * Delays only the first dispatch, so BlipScheduler.start() is still holding its
 * lock when the test's STOP arrives.
 */
private class SlowStartDispatcher(private val holdMs: Long) : CoroutineDispatcher() {
    private val executor = Executors.newSingleThreadExecutor()
    private val held = AtomicBoolean(false)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (held.compareAndSet(false, true)) Thread.sleep(holdMs)
        executor.execute(block)
    }

    fun close() = executor.shutdownNow().let { }
}
