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
    FIXED_30S,
    PULSE,
    MANUAL;

    /**
     * Preset timing. The two premium modes carry no access decision of their own:
     * MANUAL also needs the persisted FROM/TO, so both resolve through
     * [PremiumInterval.effectiveConfig], which falls back to the free preset when
     * the entitlement is missing.
     */
    fun toConfig(): IntervalConfig = when (this) {
        RANDOM_4_7 -> IntervalConfig.Random(4_000, 7_000)
        FIXED_5S -> IntervalConfig.Fixed(5_000)
        FIXED_10S -> IntervalConfig.Fixed(10_000)
        FIXED_15S -> IntervalConfig.Fixed(15_000)
        FIXED_20S -> IntervalConfig.Fixed(20_000)
        FIXED_30S -> IntervalConfig.Fixed(30_000)
        PULSE -> IntervalConfig.Pulse()
        MANUAL -> IntervalConfig.Random(4_000, 7_000)
    }
}

/** Resolved timing for the scheduler loop; safe to swap while a loop is running. */
sealed interface IntervalConfig {
    data class Random(val minMs: Long, val maxMs: Long) : IntervalConfig
    data class Fixed(val intervalMs: Long) : IntervalConfig

    /**
     * Premium PULSE: 5 s, then a fresh random 10–20 s, alternating forever. The
     * long pauses are deliberate scheduler values, never Doze or a released wake
     * lock, so they stay accurate with the screen off.
     *
     * The alternating phase is NOT stored here — this stays a plain value so the
     * service can re-assign it on every settings emission without resetting
     * anything. [BlipScheduler] owns the phase.
     */
    data class Pulse(
        val shortMs: Long = SHORT_MS,
        val longMinMs: Long = LONG_MIN_MS,
        val longMaxMs: Long = LONG_MAX_MS,
    ) : IntervalConfig {
        companion object {
            const val SHORT_MS = 5_000L
            const val LONG_MIN_MS = 10_000L
            const val LONG_MAX_MS = 20_000L
        }
    }

    /**
     * Delay for the next wait. [shortSlot] is only read by [Pulse]; it is true by
     * default because a fresh PULSE always starts on its short slot.
     */
    fun nextDelayMs(random: RandomSource, shortSlot: Boolean = true): Long = when (this) {
        is Random -> random.nextLong(minMs, maxMs)
        is Fixed -> intervalMs
        is Pulse -> if (shortSlot) shortMs else random.nextLong(longMinMs, longMaxMs)
    }

    companion object {
        val DEFAULT: IntervalConfig = IntervalMode.RANDOM_4_7.toConfig()
    }
}
