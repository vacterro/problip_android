package com.vacster.problip.service

import com.vacster.problip.core.IntervalConfig
import com.vacster.problip.core.ProblipState
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.trial.PremiumAccess
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2-002 deterministic JVM regressions for the single session-plan authority.
 * The fixture is a fake two-state audio engine whose load can be blocked and
 * released by latches — no sleeps, no Robolectric, no mocking framework.
 *
 * Notation: plan A = first submitted (stale once B exists), plan B = newer.
 */
class SessionPlanAuthorityTest {

    /** A plan distinct by name, with its own interval and snapshot triple. */
    private data class TestPlan(val name: String, val intervalMs: Long)

    private fun planOf(name: String, intervalMs: Long) = DesiredPlan(
        resolved = ColdStart.Plan(
            settings = SettingsRepository.Settings(),
            pool = setOf(name),
            interval = IntervalConfig.Fixed(intervalMs),
        ),
        owned = emptySet(),
        access = PremiumAccess(),
    )

    /** Fake engine: loadFor can be gated; activate always succeeds. */
    private inner class GatedAudio {
        val loaded = MutableStateFlow<Set<String>>(emptySet())
        val active = MutableStateFlow<Set<String>>(emptySet())
        var onLoadEntered: (() -> Unit)? = null
        var loadGate: CompletableDeferred<Unit>? = null
        val loadCalls = AtomicInteger(0)

        /** One-shot: fails exactly the next load that runs, then resets. */
        @Volatile
        var failNextLoad = false

        suspend fun loadFor(pool: Set<String>): Boolean {
            loadCalls.incrementAndGet()
            onLoadEntered?.invoke()
            val gate = loadGate
            if (gate != null) {
                gate.await()
            }
            if (failNextLoad) {
                failNextLoad = false
                return false
            }
            loaded.value = loaded.value + pool
            return true
        }

        fun activate(pool: Set<String>): Boolean {
            if (!loaded.value.containsAll(pool)) return false
            active.value = pool
            return true
        }
    }

    /** Harness wiring a [SessionPlanAuthority] to [GatedAudio] like the service does. */
    private inner class Harness {
        val audio = GatedAudio()
        val acceptedPools = mutableListOf<Set<String>>()
        val acceptedIntervals = mutableListOf<Long>()
        val acceptedNotificationRevisions = mutableListOf<String>()
        var schedulerStarts = 0
        var failedPlans = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val authority = SessionPlanAuthority<DesiredPlan>(
            scope = scope,
            loadPool = { plan -> audio.loadFor(plan.resolved.pool) },
            accept = { plan, firstSchedulerStart ->
                if (!audio.activate(plan.resolved.pool)) throw IllegalStateException("activation failed")
                acceptedPools += plan.resolved.pool
                acceptedIntervals += (plan.resolved.interval as IntervalConfig.Fixed).intervalMs
                acceptedNotificationRevisions += plan.resolved.pool.single()
                if (firstSchedulerStart) schedulerStarts++
            },
            onCurrentPlanLoadFailure = { failedPlans++ },
        )

        fun start() {
            assertNotNull(authority.startWorker())
        }

        fun stop() {
            authority.stop()
            scope.cancel()
        }

        fun submit(name: String, intervalMs: Long, startEligible: Boolean = true): Long {
            val revision = authority.submit(planOf(name, intervalMs), startEligible)
            synchronized(revisions) { revisions[name] = revision }
            return revision
        }

        fun authorityRevisionOf(name: String): Long = synchronized(revisions) { revisions[name] ?: 0L }

        private val revisions = mutableMapOf<String, Long>()
    }

    private fun awaitAccepted(h: Harness, revision: String): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (h.audio.active.value.contains(revision) &&
                h.acceptedNotificationRevisions.lastOrNull() == revision
            ) return true
            // Bounded progress wait on real threads, not a race assertion:
            // every acceptance path is deterministic given the released gate.
            Thread.sleep(5)
        }
        return false
    }

    // ------------------------------------------------------------------
    // A1. STALE SUCCESS
    // ------------------------------------------------------------------

    @Test
    fun staleSlowLoadSuccessNeverActivatesTheSupersededPlan() {
        val h = Harness()
        h.start()
        try {
            // Plan A begins a load that blocks inside the engine. The entry
            // callback and gate are registered BEFORE the submit so no state
            // transition can slip past them.
            val entered = CountDownLatch(1)
            h.audio.onLoadEntered = { entered.countDown() }
            h.audio.loadGate = CompletableDeferred()
            h.submit("A", 4_000)
            assertTrue("A's load must enter the engine", entered.await(5, TimeUnit.SECONDS))

            // Newer plan B arrives while A is still blocked.
            h.submit("B", 10_000)

            // A's load finishes successfully — too late: B is newest.
            h.audio.loadGate?.complete(Unit)

            // B must become the accepted plan: pool, interval, notification.
            assertTrue(awaitAccepted(h, "B"))
            assertEquals(setOf("B"), h.audio.active.value)
            assertEquals(listOf(10_000L), h.acceptedIntervals)
            assertEquals(listOf("B"), h.acceptedNotificationRevisions)
            // A never activated.
            assertFalse(h.acceptedPools.any { it.contains("A") })
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A2. STALE FAILURE
    // ------------------------------------------------------------------

    @Test
    fun staleLoadFailureDoesNotFailTheSessionAndBStillApplies() {
        val h = Harness()
        h.start()
        try {
            // A must actually be in flight (picked up by the worker) before B
            // supersedes it — otherwise conflation means A is never loaded and
            // the one-shot failure would wrongly hit B.
            val entered = CountDownLatch(1)
            h.audio.onLoadEntered = { entered.countDown() }
            h.audio.loadGate = CompletableDeferred()
            h.submit("A", 4_000)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            h.submit("B", 10_000)

            // A fails after being superseded: must be discarded as stale work.
            // One-shot so B's own load still succeeds.
            h.audio.failNextLoad = true
            h.audio.loadGate?.complete(Unit)

            assertTrue(awaitAccepted(h, "B"))
            assertEquals(0, h.failedPlans) // A's failure never reached failSession
            assertEquals(setOf("B"), h.audio.active.value)
        } finally {
            h.stop()
        }
    }

    @Test
    fun currentPlanLoadFailureStillFailsTheSession() {
        val h = Harness()
        h.start()
        try {
            h.audio.failNextLoad = true
            h.submit("A", 4_000)

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (h.failedPlans == 0 && System.nanoTime() < deadline) Thread.sleep(5)

            assertEquals(1, h.failedPlans) // the current revision's failure surfaces
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A3. COLD-START REVOCATION
    // ------------------------------------------------------------------

    @Test
    fun coldStartPremiumPlanRevokedWhileLoadingCannotBeTheFirstStartedPlan() {
        val h = Harness()
        h.start()
        try {
            // Cold-start plan A (premium) begins a blocked load.
            h.audio.loadGate = CompletableDeferred()
            h.submit("A", 4_000, startEligible = true)

            // Entitlement revoked: fallback plan B becomes newest.
            h.submit("B", 5_000, startEligible = true)

            // A finishes loading — must not activate or start the scheduler.
            h.audio.loadGate?.complete(Unit)

            assertTrue(awaitAccepted(h, "B"))
            assertEquals(1, h.schedulerStarts) // exactly the one B start
            assertEquals(setOf("B"), h.audio.active.value)
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A4. UPDATE WHILE SCHEDULER STILL STOPPED (the mandatory stronger bug)
    // ------------------------------------------------------------------

    @Test
    fun updateArrivingWhileSchedulerIsStillStoppedIsNeverLost() {
        val h = Harness()
        h.start()
        try {
            // A is preparing (blocked) BEFORE any scheduler start.
            h.audio.loadGate = CompletableDeferred()
            h.submit("A", 4_000, startEligible = false)

            // B arrives while the scheduler is still stopped, and NO further
            // source emission follows. B must still become the first accepted
            // and started plan — the conflated desired plan, not a dropped
            // inline apply.
            h.submit("B", 10_000, startEligible = true)
            h.audio.loadGate?.complete(Unit)

            assertTrue(awaitAccepted(h, "B"))
            assertEquals(1, h.schedulerStarts)
            assertEquals(10_000L, h.acceptedIntervals.last())
            assertEquals(setOf("B"), h.audio.active.value)
            assertFalse(h.acceptedPools.any { it.contains("A") })
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A5. STATE COHERENCE
    // ------------------------------------------------------------------

    @Test
    fun acceptedTransitionsKeepPoolIntervalAndNotificationCoherent() {
        val h = Harness()
        h.start()
        try {
            h.submit("A", 4_000)
            assertTrue(awaitAccepted(h, "A"))
            assertEquals(setOf("A"), h.audio.active.value)
            assertEquals(listOf(4_000L), h.acceptedIntervals)
            assertEquals(listOf("A"), h.acceptedNotificationRevisions)

            h.submit("B", 10_000)
            assertTrue(awaitAccepted(h, "B"))
            assertEquals(setOf("B"), h.audio.active.value)
            assertEquals(listOf(4_000L, 10_000L), h.acceptedIntervals)
            assertEquals(listOf("A", "B"), h.acceptedNotificationRevisions)
            // No mixed-revision state: accepted lists always move together.
            assertEquals(h.acceptedIntervals.size, h.acceptedNotificationRevisions.size)
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A6. DUPLICATE START
    // ------------------------------------------------------------------

    @Test
    fun repeatedSubmissionsProduceExactlyOneSchedulerStart() {
        val h = Harness()
        h.start()
        try {
            h.submit("A", 4_000)
            assertTrue(awaitAccepted(h, "A"))
            h.submit("B", 10_000)
            assertTrue(awaitAccepted(h, "B"))
            h.submit("C", 15_000)
            assertTrue(awaitAccepted(h, "C"))

            assertEquals("exactly one scheduler start", 1, h.schedulerStarts)
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // A7. TEARDOWN
    // ------------------------------------------------------------------

    @Test
    fun stopWhileBlockedPreventsTheLaterCompletionFromActivating() {
        val h = Harness()
        h.start()
        h.audio.loadGate = CompletableDeferred()
        h.submit("A", 4_000)

        // STOP (teardown) while the load is blocked.
        h.stop()

        // The load then completes successfully — a stale completion after the
        // session token died: no activation, no accept, no start.
        h.audio.loadGate?.complete(Unit)

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline && h.audio.active.value.isNotEmpty()) Thread.sleep(5)
        assertTrue(h.audio.active.value.isEmpty())
        assertTrue(h.acceptedPools.isEmpty())
        assertTrue(h.acceptedIntervals.isEmpty())
        assertEquals(0, h.schedulerStarts)
    }

    // ------------------------------------------------------------------
    // First-start gating (CORE-003 preservation)
    // ------------------------------------------------------------------

    @Test
    fun plansBeforeTheColdStartLatchPrepareButNeverStartTheScheduler() {
        val h = Harness()
        h.start()
        try {
            // Live collector emissions before the cold-start barrier resolves.
            h.submit("A", 4_000, startEligible = false)
            assertTrue(awaitAccepted(h, "A"))
            assertEquals("no scheduler start before readiness latch", 0, h.schedulerStarts)
            assertEquals(setOf("A"), h.audio.active.value)

            // The barrier resolves: its plan may start the session.
            h.submit("B", 10_000, startEligible = true)
            assertTrue(awaitAccepted(h, "B"))
            assertEquals(1, h.schedulerStarts)
        } finally {
            h.stop()
        }
    }

    @Test
    fun liveEmissionSupersedingTheBarrierPlanStillStartsExactlyOnce() {
        val h = Harness()
        h.start()
        try {
            h.submit("A", 4_000, startEligible = false)
            assertTrue(awaitAccepted(h, "A"))
            // Barrier resolves and is immediately superseded by a newer emission.
            h.submit("B", 10_000, startEligible = true)
            h.submit("C", 15_000)
            assertTrue(awaitAccepted(h, "C"))

            // The latch, not the per-plan flag, owns eligibility: the session
            // still starts exactly once, on the newest accepted plan.
            assertEquals(1, h.schedulerStarts)
        } finally {
            h.stop()
        }
    }

    // ------------------------------------------------------------------
    // Concurrency: racing submits still end at the newest plan
    // ------------------------------------------------------------------

    @Test
    fun racingConcurrentSubmitsAlwaysEndAtTheNewestPlan() {
        val pool = Executors.newFixedThreadPool(2)
        val h = Harness()
        h.start()
        try {
            val barrier = CyclicBarrier(2)
            val tasks = listOf(
                pool.submit(Callable {
                    barrier.await()
                    h.submit("X", 4_000)
                }),
                pool.submit(Callable {
                    barrier.await()
                    h.submit("Y", 10_000)
                }),
            )
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }

            // Both revisions were allocated; the newest one (whichever submit
            // returned the higher revision) wins the conflated desired slot and
            // is the only accepted plan.
            val winner = if (h.authorityRevisionOf("X") > h.authorityRevisionOf("Y")) "X" else "Y"
            assertTrue(awaitAccepted(h, winner))
            assertEquals(setOf(winner), h.audio.active.value)
        } finally {
            h.stop()
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // Bounded-progress helper for the gated-load entry check
    // ------------------------------------------------------------------

    @Test
    fun loadIsSkippedOnlyThroughTheAuthorityNotByDroppingPlans() {
        val h = Harness()
        h.start()
        try {
            // Every submitted plan's pool gets loaded by the worker: nothing is
            // dropped even when the scheduler never starts.
            h.submit("A", 4_000, startEligible = false)
            assertTrue(awaitAccepted(h, "A"))
            assertEquals(1, h.audio.loadCalls.get())
        } finally {
            h.stop()
        }
    }
}
