package com.vacster.problip.audio

import com.vacster.problip.core.BlipPlayer

/**
 * Engine-agnostic short-audio surface. `play` returning false is the contract
 * that turns a broken engine into the scheduler's ERROR state, so an unprepared
 * or failed engine can never look like RUNNING.
 *
 * W2-002: resource loading and pool activation are separate operations. A slow
 * load for a plan that is later superseded must never make that stale pool the
 * active one; only an explicit [activate] on an accepted current plan commits
 * the random pool. [prepare] remains as the combined convenience for callers
 * that load-and-activate in one step under their own authority.
 */
interface AudioPlayer : BlipPlayer {
    /**
     * Loads every sound of [soundIds] and makes that set the active random
     * pool. True only when all sounds are genuinely usable. Calling prepare
     * again with a different set switches the pool for future plays; the
     * scheduler loop is never touched by this.
     */
    suspend fun prepare(soundIds: Set<String>): Boolean

    /**
     * Ensures every sound of [soundIds] is loaded WITHOUT changing the active
     * pool. Slow, but a stale completion only leaves reusable samples cached.
     * True when all requested sounds are loaded and usable.
     */
    suspend fun loadFor(soundIds: Set<String>): Boolean

    /**
     * Makes [soundIds] the active random pool, verifying the samples are
     * genuinely loaded (never a half-loaded placeholder). Switching pools stays
     * synchronized against play/release. Call only from the accepted plan
     * transition of the session-plan authority.
     */
    fun activate(soundIds: Set<String>): Boolean

    /** Engine gain 0.0..1.0, applied to future plays. Never touches scheduling. */
    fun setVolume(gain: Float)

    /** Frees the underlying engine. The instance is unusable afterwards. */
    fun release()
}
