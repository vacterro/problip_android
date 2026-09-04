package com.vacster.problip.core

/** Injected randomness so the scheduling math is deterministically testable. */
fun interface RandomSource {
    /** Uniform value in [fromInclusive..toInclusive]. */
    fun nextLong(fromInclusive: Long, toInclusive: Long): Long
}

/** Production random source backed by kotlin.random. */
object KotlinRandomSource : RandomSource {
    override fun nextLong(fromInclusive: Long, toInclusive: Long): Long =
        kotlin.random.Random.nextLong(fromInclusive, toInclusive + 1)
}

/** User-selectable interval presets. Enum names double as stable settings keys. */
enum class IntervalMode {
    RANDOM_4_7,
    FIXED_5S,
    FIXED_10S,
    FIXED_15S,
    FIXED_20S,
    FIXED_30S;

    fun toConfig(): IntervalConfig = when (this) {
        RANDOM_4_7 -> IntervalConfig.Random(4_000, 7_000)
        FIXED_5S -> IntervalConfig.Fixed(5_000)
        FIXED_10S -> IntervalConfig.Fixed(10_000)
        FIXED_15S -> IntervalConfig.Fixed(15_000)
        FIXED_20S -> IntervalConfig.Fixed(20_000)
        FIXED_30S -> IntervalConfig.Fixed(30_000)
    }
}

/** Resolved timing for the scheduler loop; safe to swap while a loop is running. */
sealed interface IntervalConfig {
    data class Random(val minMs: Long, val maxMs: Long) : IntervalConfig
    data class Fixed(val intervalMs: Long) : IntervalConfig

    fun nextDelayMs(random: RandomSource): Long = when (this) {
        is Random -> random.nextLong(minMs, maxMs)
        is Fixed -> intervalMs
    }

    companion object {
        val DEFAULT: IntervalConfig = IntervalMode.RANDOM_4_7.toConfig()
    }
}
