package com.vacster.problip.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The ONE owner of which desired session plan is current (W2-002).
 *
 * ProblipService used to have two independent asynchronous appliers — the live
 * settings/ownership/access collector and the cold-start barrier — both able to
 * call audio.prepare(...), write preparedPool, set scheduler.interval and start
 * the scheduler. Because SoundPoolAudioPlayer.prepare() commits activePool
 * internally after a potentially multi-second load, a stale slow prepare could
 * commit as current: an expired premium trial could reach the first playback, a
 * refund could be resurrected in the pool, preparedPool could disagree with the
 * engine, and the failure of an obsolete prepare could tear down a session whose
 * newer plan was valid. Worse, a newer plan observed while the scheduler was
 * still STOPPED was dropped entirely (the old collector skipped prepare when
 * STOPPED) and never re-applied until an unrelated source emission arrived.
 *
 * Every desired plan is submitted here and receives a monotonically increasing
 * revision. Exactly one worker loads the newest desired plan's pool through
 * [loadPool] and revalidates its revision afterwards: only the still-newest
 * revision is accepted through [accept] — the one transition that commits the
 * audio pool, preparedPool, the effective interval, the notification and the
 * first scheduler start — while a superseded revision's success AND failure are
 * discarded as stale work. Submissions conflate: a plan submitted while an
 * older one is still loading replaces it as the desired plan with no second
 * source emission required, and the worker picks it up the moment the older
 * load settles.
 *
 * First-start gating: [submit]'s startEligible flag marks that the cold-start
 * readiness barrier (persisted settings + ownership + temporary access +
 * earned-entitlement) has resolved for this session. The flag LATCHES: once any
 * start-eligible plan has been submitted, every subsequently accepted plan may
 * perform the first scheduler start, so a live emission that supersedes the
 * barrier's own plan cannot silently suppress the start. Before the latch, a
 * live collector plan may still be accepted (it prepares audio, preparedPool,
 * interval and notification state) but the scheduler is not started — the very
 * first blip still honours the full readiness barrier (CORE-003).
 *
 * The worker is a single coroutine on [scope]; no second applier may exist.
 * Cancellation (session teardown) propagates into the suspended load and kills
 * the worker; [stop] additionally rejects all later submissions, so no stale
 * plan completion can activate audio or mutate the scheduler after the session
 * token is dead.
 *
 * Android-free apart from the single worker launch: every transition is
 * deterministic on the JVM (no sleeps in its tests).
 */
class SessionPlanAuthority<P>(
    private val scope: CoroutineScope,
    private val loadPool: suspend (P) -> Boolean,
    private val accept: (plan: P, firstSchedulerStart: Boolean) -> Unit,
    private val onCurrentPlanLoadFailure: (plan: P) -> Unit,
) {

    private sealed interface Decision {
        data class Accept(val firstSchedulerStart: Boolean) : Decision
        object Fail : Decision
        object Stale : Decision
    }

    private val lock = Any()

    /** Newest allocated revision; every submit strictly increases it. */
    private var newestRevision = 0L

    /** The conflated desired plan — always the one with [newestRevision]. */
    private var desired: P? = null

    /**
     * Sticky: true once any start-eligible plan was submitted, i.e. the
     * cold-start barrier has resolved. Accepted plans may then start the
     * scheduler (if it has not started yet).
     */
    private var startLatched = false

    /** Whether the scheduler has already been started through an accepted plan. */
    private var started = false

    private var stopped = false
    private var worker: Job? = null

    /** Conflated wake-up signal for the worker; equals the newest submit's revision. */
    private val tick = MutableStateFlow(0L)

    /**
     * Records [plan] as the newest desired plan and returns its revision.
     * [startEligible] latches the cold-start readiness barrier as resolved
     * (see class docs). Ignored after [stop] (teardown): the session is gone,
     * nothing may apply.
     */
    fun submit(plan: P, startEligible: Boolean = true): Long {
        val revision: Long
        synchronized(lock) {
            if (stopped) return newestRevision
            newestRevision += 1
            desired = plan
            if (startEligible) startLatched = true
            revision = newestRevision
        }
        tick.value = revision
        return revision
    }

    /**
     * Starts the single plan-application worker. Calling this again is a no-op
     * (exactly one worker); returns the worker's Job so the session can cancel
     * it with the rest of its teardown jobs, or null after [stop].
     */
    fun startWorker(): Job? {
        synchronized(lock) {
            if (stopped || worker != null) return worker
            worker = scope.launch { runWorkerLoop() }
            return worker
        }
    }

    /** Rejects every further submission and cancels the worker. */
    fun stop() {
        synchronized(lock) {
            stopped = true
            worker?.cancel()
            worker = null
        }
    }

    private suspend fun runWorkerLoop() {
        var seen = 0L
        while (true) {
            tick.first { it > seen }
            val (revision, plan) = synchronized(lock) {
                seen = newestRevision
                val current = requireNotNull(desired) { "tick advanced without a desired plan" }
                newestRevision to current
            }
            // Load OUTSIDE any lock. Cancellation stays cancellation (teardown);
            // every other load error becomes a plain load failure so the stale/
            // current rules below — not the exception's timing — decide what it
            // may do to the session.
            val loaded = try {
                loadPool(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            // Revalidate AFTER the potentially slow load: only the still-newest
            // revision may commit. A superseded success leaves its reusable
            // loaded samples cached but changes nothing else; a superseded
            // failure is discarded as stale work and must not fail the session.
            val decision = synchronized(lock) {
                when {
                    revision != newestRevision || stopped -> Decision.Stale
                    loaded -> {
                        val firstStart = startLatched && !started
                        if (firstStart) started = true
                        Decision.Accept(firstStart)
                    }
                    else -> Decision.Fail
                }
            }
            when (decision) {
                is Decision.Accept -> accept(plan, decision.firstSchedulerStart)
                is Decision.Fail -> onCurrentPlanLoadFailure(plan)
                is Decision.Stale -> Unit
            }
        }
    }
}
