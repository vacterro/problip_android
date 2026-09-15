package com.vacster.problip.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * PERF-004 deterministic JVM regressions for the SoundPool resource lifecycle.
 *
 * The fake engine dispatches completions from its own executor thread (like the
 * real SoundPool) and lets tests gate them with latches, so every concurrency
 * claim — issue-all-then-await, dedup, single deadline, release, cancellation —
 * is proven without sleep-based race assertions.
 */
class SoundPoolAudioPlayerTest {

    // ------------------------------------------------------------------
    // Fake SoundPool engine
    // ------------------------------------------------------------------

    /** Fake engine: async completion dispatch, per-resource gates and failures. */
    private class TrackingEngine : SoundEngine {
        data class Load(val resId: Int, val sampleId: Int)

        val loadEvents = mutableListOf<Load>()
        val unloadCalls = mutableListOf<Int>()
        val plays = AtomicInteger(0)

        /** Gate per resource id: absent = completes immediately (async). */
        val gates = mutableMapOf<Int, CountDownLatch>()

        /** Failure status per resource id (0 = success). */
        val failures = mutableMapOf<Int, Int>()

        val loadCalls = AtomicInteger(0)
        @Volatile var released = false

        @Volatile var listener: ((Int, Int) -> Unit)? = null

        private val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "fake-soundpool").apply { isDaemon = true }
        }

        override fun load(resId: Int): Int {
            val id = loadCalls.incrementAndGet()
            synchronized(loadEvents) { loadEvents += Load(resId, id) }
            executor.execute {
                try {
                    gates[resId]?.await()
                } catch (_: InterruptedException) {
                    return@execute
                }
                listener?.invoke(id, failures[resId] ?: 0)
            }
            return id
        }

        override fun unload(sampleId: Int) {
            unloadCalls += sampleId
        }

        override fun play(sampleId: Int, gain: Float): Boolean {
            if (released) return false
            return plays.incrementAndGet() > 0
        }

        override fun setOnLoadCompleteListener(listener: (Int, Int) -> Unit) {
            this.listener = listener
        }

        override fun release() {
            released = true
        }

        fun loadsOf(resId: Int): List<Load> = synchronized(loadEvents) {
            loadEvents.filter { it.resId == resId }
        }

        fun totalLoads(): Int = synchronized(loadEvents) { loadEvents.size }
    }

    private fun player(e: TrackingEngine, timeoutMs: Long = SoundPoolAudioPlayer.PREPARE_TIMEOUT_MS) =
        SoundPoolAudioPlayer(context = null, engine = e, prepareTimeoutMs = timeoutMs)

    private fun gate(e: TrackingEngine, vararg resIds: Int) {
        for (resId in resIds) e.gates[resId] = CountDownLatch(1)
    }

    private fun openGate(e: TrackingEngine, vararg resIds: Int) {
        for (resId in resIds) e.gates[resId]?.countDown()
    }

    /** Bounded suspending poll: yields the runBlocking event loop so child coroutines progress. */
    private suspend fun awaitTrue(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            kotlinx.coroutines.delay(2)
        }
        return condition()
    }

    private val ORIG = SoundCatalog.ORIGINAL
    private val GLASS = SoundCatalog.GLASS
    private val WOOD = SoundCatalog.WOOD
    private val SPACE = SoundCatalog.SPACE

    // ------------------------------------------------------------------
    // 1. Unchanged pool reuses samples
    // ------------------------------------------------------------------

    @Test
    fun unchangedPoolLoadsEverySoundExactlyOnce() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        val pool = setOf(ORIG.id, GLASS.id, WOOD.id)

        assertTrue(p.loadFor(pool))
        assertTrue(p.loadFor(pool))

        assertEquals(3, e.totalLoads()) // each logical sound physically loaded once
        assertTrue(e.unloadCalls.isEmpty())
        p.release()
    }

    // ------------------------------------------------------------------
    // 2. Grow pool
    // ------------------------------------------------------------------

    @Test
    fun growPoolLoadsOnlyTheNewSoundAndReusesOriginal() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertTrue(p.loadFor(setOf(ORIG.id)))
        assertTrue(p.activate(setOf(ORIG.id)))

        assertTrue(p.loadFor(setOf(ORIG.id, GLASS.id)))
        assertTrue(p.activate(setOf(ORIG.id, GLASS.id)))

        assertEquals(listOf(ORIG.resId, GLASS.resId), synchronized(e.loadEvents) { e.loadEvents.map { it.resId } })
        assertTrue(p.play())
        assertEquals(1, e.plays.get())
        p.release()
    }

    // ------------------------------------------------------------------
    // 3. Shrink pool (mandatory regression)
    // ------------------------------------------------------------------

    @Test
    fun shrinkPoolActivatesWithoutFailingAndUnloadsOnlyAfterAcceptance() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertTrue(p.loadFor(setOf(ORIG.id, GLASS.id)))
        assertTrue(p.activate(setOf(ORIG.id, GLASS.id)))

        // Shrink: loadFor(original) must not reload original, must not fail.
        assertTrue(p.loadFor(setOf(ORIG.id)))
        assertEquals(2, e.totalLoads()) // no duplicate original load

        // Activation succeeds even though glass is still loaded (previously
        // the strict loaded==wanted check rejected the whole switch).
        assertTrue(p.activate(setOf(ORIG.id)))

        // Glass unloaded only at the accepted boundary; original stays usable.
        val glassSample = e.loadsOf(GLASS.resId).single().sampleId
        val origSample = e.loadsOf(ORIG.resId).single().sampleId
        assertTrue(glassSample in e.unloadCalls)
        assertFalse(origSample in e.unloadCalls)
        assertTrue(p.play())
        p.release()
    }

    // ------------------------------------------------------------------
    // 4. Replace pool
    // ------------------------------------------------------------------

    @Test
    fun replacePoolKeepsOldSamplesUntilWoodPreparesThenUnloadsGlass() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertTrue(p.loadFor(setOf(ORIG.id, GLASS.id)))
        assertTrue(p.activate(setOf(ORIG.id, GLASS.id)))

        // Replacement: wood is gated, so nothing is unloaded while it prepares.
        gate(e, WOOD.resId)
        val prepare = launch { p.loadFor(setOf(ORIG.id, WOOD.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 3 }) // only wood loaded (original reused)

        val glassSample = e.loadsOf(GLASS.resId).single().sampleId
        assertFalse(glassSample in e.unloadCalls) // glass still available
        assertTrue(p.play()) // old pool still playable mid-replacement

        openGate(e, WOOD.resId)
        prepare.join()
        assertTrue(p.activate(setOf(ORIG.id, WOOD.id)))

        assertTrue(glassSample in e.unloadCalls) // unloaded at the accepted boundary
        assertTrue(p.play())
        p.release()
    }

    // ------------------------------------------------------------------
    // 5. Failed replacement
    // ------------------------------------------------------------------

    @Test
    fun failedReplacementKeepsOldPoolIntactAndCleansFailedSample() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertTrue(p.loadFor(setOf(ORIG.id, GLASS.id)))
        assertTrue(p.activate(setOf(ORIG.id, GLASS.id)))

        e.failures[WOOD.resId] = 1
        assertFalse(p.loadFor(setOf(ORIG.id, WOOD.id)))

        // Old pool untouched; no incomplete wood mapping survives.
        assertEquals(setOf(ORIG.id, GLASS.id), p.loadedKeysForTest())
        val woodSample = e.loadsOf(WOOD.resId).single().sampleId
        assertTrue("failed physical wood sample cleaned", woodSample in e.unloadCalls)
        val origSample = e.loadsOf(ORIG.resId).single().sampleId
        assertFalse(origSample in e.unloadCalls) // original not reloaded or unloaded
        assertTrue(p.play())
        p.release()
    }

    // ------------------------------------------------------------------
    // 6. Concurrent three loads
    // ------------------------------------------------------------------

    @Test
    fun threeMissingLoadsAreAllIssuedBeforeAnyCompletion() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        gate(e, GLASS.resId, WOOD.resId, SPACE.resId)

        val prepare = launch { p.loadFor(setOf(ORIG.id, GLASS.id, WOOD.id, SPACE.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 4 })
        openGate(e, GLASS.resId, WOOD.resId, SPACE.resId)
        prepare.join()
        assertEquals(setOf(ORIG.id, GLASS.id, WOOD.id, SPACE.id), p.loadedKeysForTest())
        p.release()
    }

    // ------------------------------------------------------------------
    // 7. Six-sound boundary
    // ------------------------------------------------------------------

    @Test
    fun coldSixSoundPreparationIssuesAllLoadsBeforeFirstCompletion() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        val all = SoundCatalog.all
        gate(e, *all.map { it.resId }.toIntArray())

        val prepare = launch { p.loadFor(all.map { it.id }.toSet()) }
        assertTrue(awaitTrue { e.totalLoads() == all.size })
        openGate(e, *all.map { it.resId }.toIntArray())
        prepare.join()
        assertEquals(all.map { it.id }.toSet(), p.loadedKeysForTest())
        p.release()
    }

    // ------------------------------------------------------------------
    // 8. Single deadline
    // ------------------------------------------------------------------

    @Test
    fun timeoutIsOnePreparationDeadlineNotSumOfPerLoadDeadlines() = runBlocking {
        val e = TrackingEngine()
        val p = player(e, timeoutMs = 200)
        gate(e, GLASS.resId, WOOD.resId)

        val start = System.nanoTime()
        val prepare = launch { p.loadFor(setOf(GLASS.id, WOOD.id)) }
        yield(); yield()

        // Gates never open: the operation must fail after ONE deadline window.
        assertTrue(awaitTrue(5_000) { prepare.isCompleted })
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("elapsed=${elapsedMs}ms must be under one serial window (2x200)", elapsedMs < 400)
        assertFalse(prepare.isCancelled)

        // Both orphaned samples cleaned, no committed bookkeeping remains.
        assertTrue(awaitTrue { e.unloadCalls.size == 2 && p.loadedKeysForTest().isEmpty() })
        p.release()
    }

    // ------------------------------------------------------------------
    // 9. Same-sound overlapping prepares
    // ------------------------------------------------------------------

    @Test
    fun overlappingPreparesOfTheSameSoundIssueOnePhysicalLoad() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        gate(e, GLASS.resId)

        val a = launch { p.loadFor(setOf(GLASS.id)) }
        val b = launch { p.loadFor(setOf(GLASS.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 1 })
        openGate(e, GLASS.resId)
        a.join(); b.join()
        assertEquals(setOf(GLASS.id), p.loadedKeysForTest())
        assertEquals("still exactly one physical load", 1, e.totalLoads())
        p.release()
    }

    // ------------------------------------------------------------------
    // 10. Overlapping different prepares + stale A cannot activate
    // ------------------------------------------------------------------

    @Test
    fun overlappingDifferentPreparesShareGlassAndStaleACannotSelfActivate() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        gate(e, GLASS.resId, WOOD.resId, SPACE.resId)

        val a = launch { p.loadFor(setOf(GLASS.id, WOOD.id)) }
        val b = launch { p.loadFor(setOf(GLASS.id, SPACE.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 3 })

        val resIds = synchronized(e.loadEvents) { e.loadEvents.map { it.resId } }
        assertEquals(3, resIds.size)
        assertEquals(1, resIds.count { it == GLASS.resId }) // shared, no duplicate
        assertEquals(1, resIds.count { it == WOOD.resId })
        assertEquals(1, resIds.count { it == SPACE.resId })

        openGate(e, GLASS.resId, WOOD.resId, SPACE.resId)
        a.join(); b.join()

        // W2-002 semantics: loadFor NEVER activates. A stale plan completion
        // cannot make itself active; only the newest accepted plan (B) goes
        // through the activation transition.
        assertTrue(p.activePoolForTest().isEmpty())
        assertTrue(p.activate(setOf(GLASS.id, SPACE.id))) // B, the newest plan
        assertEquals(setOf(GLASS.id, SPACE.id), p.activePoolForTest())
        p.release()
    }

    // ------------------------------------------------------------------
    // 11. Cancellation
    // ------------------------------------------------------------------

    @Test
    fun cancellingPreparationCleansOrphansAndNeverTouchesActivePool() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        // Established working pool.
        assertTrue(p.loadFor(setOf(ORIG.id)))
        assertTrue(p.activate(setOf(ORIG.id)))

        gate(e, GLASS.resId, WOOD.resId)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job: Job = scope.launch { p.loadFor(setOf(GLASS.id, WOOD.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 3 }) // both new loads issued

        job.cancel(CancellationException("plan superseded"))
        assertTrue(awaitTrue { e.unloadCalls.size == 2 }) // orphans cleaned
        assertEquals(setOf(ORIG.id), p.loadedKeysForTest())
        assertTrue(p.play()) // active pool unchanged and playable
        scope.cancel()
        p.release()
    }

    // ------------------------------------------------------------------
    // 12. Release during load
    // ------------------------------------------------------------------

    @Test
    fun releaseDuringOutstandingLoadsWakesWaitersAndIgnoresLateCallbacks() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        gate(e, GLASS.resId, WOOD.resId)

        val prepare = launch { p.loadFor(setOf(GLASS.id, WOOD.id)) }
        assertTrue(awaitTrue { e.totalLoads() == 2 })

        p.release()
        assertTrue("waiters terminated promptly", awaitTrue(1_000) { prepare.isCompleted })
        assertFalse(prepare.isCancelled)
        assertTrue(p.loadedKeysForTest().isEmpty())

        // Late fake callbacks (gates opened after release) must be ignored.
        openGate(e, GLASS.resId, WOOD.resId)
        Thread.sleep(50)
        assertTrue(p.loadedKeysForTest().isEmpty())
        assertFalse(p.loadFor(setOf(ORIG.id)))
        assertFalse(p.activate(setOf(ORIG.id)))
        assertFalse(p.play())
    }

    // ------------------------------------------------------------------
    // 13. prepare() convenience does not deadlock
    // ------------------------------------------------------------------

    @Test
    fun prepareConvenienceCompletesAndActivatesWithoutDeadlock() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        // The historical nested-Mutex path would suspend forever; the wrapper
        // bounds the regression instead of relying on sleep-based proof.
        val result = kotlinx.coroutines.withTimeout(5_000) {
            p.prepare(setOf(ORIG.id, GLASS.id))
        }
        assertTrue(result)
        assertEquals(setOf(ORIG.id, GLASS.id), p.activePoolForTest())
        assertTrue(p.play())
        p.release()
    }

    @Test
    fun prepareSwitchesToANewPoolOnSecondCall() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertTrue(p.prepare(setOf(ORIG.id)))
        assertEquals(setOf(ORIG.id), p.activePoolForTest())
        assertTrue(p.prepare(setOf(ORIG.id, GLASS.id)))
        assertEquals(setOf(ORIG.id, GLASS.id), p.activePoolForTest())
        assertEquals(2, e.totalLoads()) // original reused
        p.release()
    }

    // ------------------------------------------------------------------
    // 14. Unknown id
    // ------------------------------------------------------------------

    @Test
    fun unknownIdFailsSafelyWithoutPhysicalLoads() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)

        assertFalse(p.loadFor(setOf(ORIG.id, "sound_nope")))
        assertEquals(0, e.totalLoads())
        assertTrue(p.loadedKeysForTest().isEmpty())
        assertFalse(p.activate(setOf("sound_nope")))
        p.release()
    }

    // ------------------------------------------------------------------
    // W12-era guard kept green against the new implementation
    // ------------------------------------------------------------------

    @Test
    fun supersededMappingReusesInsteadOfSecondPhysicalLoad() = runBlocking {
        val e = TrackingEngine()
        val p = player(e)
        assertTrue(p.loadFor(setOf(ORIG.id)))
        val first = e.loadsOf(ORIG.resId).single().sampleId
        assertNotEquals(0, first)
        assertTrue(p.loadFor(setOf(ORIG.id)))
        assertEquals("reuse, not a second physical load", 1, e.totalLoads())
        p.release()
    }
}
