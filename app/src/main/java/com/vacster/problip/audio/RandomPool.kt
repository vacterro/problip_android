package com.vacster.problip.audio

import com.vacster.problip.core.RandomSource
import com.vacster.problip.core.KotlinRandomSource

/**
 * Picks the next sound of a random pool: uniform choice from the active pool,
 * independent per blip (an optional no-repeat rule is a v1.1 feature).
 */
class RandomPool(private val random: RandomSource = KotlinRandomSource) {

    /** Uniform pick from [pool]; null when the pool is empty. */
    fun next(pool: List<String>): String? {
        if (pool.isEmpty()) return null
        if (pool.size == 1) return pool[0]
        val index = random.nextLong(0, (pool.size - 1).toLong()).toInt()
        return pool[index]
    }
}
