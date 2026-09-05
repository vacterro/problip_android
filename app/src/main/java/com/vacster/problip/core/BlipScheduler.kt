package com.vacster.problip.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** Injectable suspend delay so tests can run on virtual time. */
fun interface DelayBoundary {
    suspend fun delay(ms: Long)
}

/** Production delay boundary backed by kotlinx.coroutines.delay. */
object CoroutineDelayBoundary : DelayBoundary {
    override suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}

/** Minimal playback callback the core needs; real audio arrives in a later wave. */
fun interface BlipPlayer {
    /** Returns false when playback failed; the scheduler then enters ERROR. */
    fun play(): Boolean
}

/**
 * One START creates exactly one loop: initial delay, play, interval delay, play, ...
 * STOP cancels the loop. Repeated START and repeated STOP are no-ops.
 * The core has no Android, UI, audio-engine or billing dependencies.
 */
class BlipScheduler(
    private val scope: CoroutineScope,
    private val player: BlipPlayer,
    private val delayBoundary: DelayBoundary = CoroutineDelayBoundary,
    private val randomSource: RandomSource = KotlinRandomSource,
    initialInterval: IntervalConfig = IntervalConfig.DEFAULT,
) {
    /**
     * Plain monitor instead of a Mutex.tryLock(): a dropped STOP is worse than a
     * few microseconds of blocking. tryLock() silently discarded whichever call
     * lost the race, so a STOP issued while START was still installing the loop
     * left the loop running with the UI showing STOPPED.
     */
    private val lock = Any()
    private var loopJob: Job? = null

    private val _state = MutableStateFlow(ProblipState.STOPPED)
    val state: StateFlow<ProblipState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Interval used for the NEXT wait; may be changed while running without
     * spawning a second loop.
     *
     * Assigning a DIFFERENT value discards the PULSE phase, which is the whole
     * switching contract in one line: entering PULSE, leaving it, or losing its
     * entitlement mid-session all reset the alternation, while the service
     * re-assigning an equal value (a volume change, a purchase, an unrelated
     * setting) leaves the phase alone.
     */
    @Volatile
    var interval: IntervalConfig = initialInterval
        set(value) {
            if (field != value) {
                field = value
                pulseShortSlot = true
            }
        }

    /**
     * PULSE phase, owned by the session: true means the next wait is the 5 s slot.
     * Written by the loop and by the [interval] setter, never persisted — STOP
     * discards it and the next START begins short again.
     */
    @Volatile
    private var pulseShortSlot = true

    fun start() {
        synchronized(lock) {
            if (loopJob?.isActive == true) return
            _error.value = null
            _state.value = ProblipState.STARTING
            pulseShortSlot = true
            loopJob = scope.launch {
                val self = coroutineContext.job
                try {
                    delayBoundary.delay(INITIAL_DELAY_MS)
                    while (true) {
                        if (!player.play()) {
                            _state.value = ProblipState.ERROR
                            _error.value = "Playback failed"
                            return@launch
                        }
                        _state.value = ProblipState.RUNNING
                        delayBoundary.delay(nextDelayMs())
                    }
                } catch (e: CancellationException) {
                    // A cancelled loop must never clobber the state of a newer session.
                    if (loopJob === self) _state.value = ProblipState.STOPPED
                    throw e
                }
            }
        }
    }

    /** Reads the current interval once, then advances the PULSE phase. */
    private fun nextDelayMs(): Long {
        val shortSlot = pulseShortSlot
        pulseShortSlot = !shortSlot
        return interval.nextDelayMs(randomSource, shortSlot)
    }

    fun stop() {
        synchronized(lock) {
            loopJob?.cancel()
            loopJob = null
            pulseShortSlot = true
            _state.value = ProblipState.STOPPED
        }
    }

    companion object {
        /** First playback happens ~500 ms after START, regardless of interval. */
        const val INITIAL_DELAY_MS = 500L
    }
}
