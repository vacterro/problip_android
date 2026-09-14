package com.vacster.problip.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PERF-002: demand coalescing for one Billing query domain.
 *
 * A plain Mutex serializes work but does not coalesce demand: 100 resume
 * triggers while one query is in flight would enqueue 100 queued coroutines,
 * each re-running the whole query after it. This limiter bounds the work to
 * O(1): at most ONE active query plus at most ONE trailing rerun, no matter
 * how many demands arrive while the active one runs. Demands arriving during
 * the trailing run collapse into one newer trailing run, so the last request
 * always produces a fresh authoritative answer.
 *
 * The trailing run is scheduled only if a demand arrived while a query was
 * actually running ([running] was true at demand time). A demand that finds
 * the limiter idle runs immediately — the trigger is the work.
 *
 * [guard] is the existing W2-001 correctness mutex (FIFO publication order
 * inside one query). Coalescing happens BEFORE the guard: collapsed demands
 * never enqueue on it at all.
 *
 * Test seam: [dispatch] and [running] take an explicit dispatcher so tests can
 * park the fake client deterministically.
 */
internal class RefreshLimiter(
    private val scope: CoroutineScope,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val stateLock = Any()
    private var running = false
    private var trailingRequested = false

    /**
     * Record a refresh demand. Runs the body immediately when idle; otherwise
     * collapses into the single trailing rerun.
     */
    fun request(runBody: suspend () -> Unit) {
        val mustRun: Boolean
        synchronized(stateLock) {
            if (running) {
                trailingRequested = true
                return
            }
            running = true
            mustRun = true
        }
        scope.launch(dispatcher) {
            var again = true
            while (again) {
                again = false
                runBody()
                synchronized(stateLock) {
                    if (trailingRequested) {
                        // One authoritative rerun covers every demand that
                        // arrived during the finished query; demands arriving
                        // DURING this rerun set the flag again.
                        trailingRequested = false
                        again = true
                    } else {
                        running = false
                    }
                }
            }
        }
    }

    /** True while a query (active or trailing) is executing. Test seam. */
    val isBusy: Boolean
        get() = synchronized(stateLock) { running }
}
