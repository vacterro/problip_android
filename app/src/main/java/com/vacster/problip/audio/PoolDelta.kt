package com.vacster.problip.audio

/**
 * What a pool switch has to change in the audio engine.
 *
 * Exists because reloading is not free: SoundPool.load() allocates a new sample
 * for every call, so re-preparing an unchanged pool (settings change, new
 * purchase) grew the sample table until the engine ran out of sample memory.
 */
data class PoolDelta(val toLoad: Set<String>, val toUnload: Set<String>) {

    val isEmpty: Boolean get() = toLoad.isEmpty() && toUnload.isEmpty()

    companion object {
        fun between(loaded: Set<String>, wanted: Set<String>): PoolDelta =
            PoolDelta(toLoad = wanted - loaded, toUnload = loaded - wanted)
    }
}
