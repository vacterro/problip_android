package com.vacster.problip.audio

import com.vacster.problip.core.BlipPlayer

/**
 * Engine-agnostic short-audio surface. `play` returning false is the contract
 * that turns a broken engine into the scheduler's ERROR state, so an unprepared
 * or failed engine can never look like RUNNING.
 */
interface AudioPlayer : BlipPlayer {
    /**
     * Loads every sound of [soundIds] and makes that set the active random
     * pool. True only when all sounds are genuinely usable. Calling prepare
     * again with a different set switches the pool for future plays; the
     * scheduler loop is never touched by this.
     */
    suspend fun prepare(soundIds: Set<String>): Boolean

    /** Engine gain 0.0..1.0, applied to future plays. Never touches scheduling. */
    fun setVolume(gain: Float)

    /** Frees the underlying engine. The instance is unusable afterwards. */
    fun release()
}
