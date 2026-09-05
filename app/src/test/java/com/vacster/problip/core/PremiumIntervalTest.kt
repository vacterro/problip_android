package com.vacster.problip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The premium interval gate: MANUAL and PULSE only reach the scheduler with an
 * entitlement, and each mode is gated by its own flag so one trial can never
 * unlock the other. Everything here is pure, so it is checked on the resolved
 * config rather than on any UI state.
 */
class PremiumIntervalTest {

    private fun config(
        mode: IntervalMode,
        manual: Boolean = false,
        pulse: Boolean = false,
        fromSeconds: Int = 2,
        toSeconds: Int = 3,
    ) = PremiumInterval.effectiveConfig(
        mode = mode,
        fromSeconds = fromSeconds,
        toSeconds = toSeconds,
        manualAccessible = manual,
        pulseAccessible = pulse,
    )

    private fun mode(mode: IntervalMode, manual: Boolean = false, pulse: Boolean = false) =
        PremiumInterval.effectiveMode(
            mode = mode,
            manualAccessible = manual,
            pulseAccessible = pulse,
        )

    @Test
    fun manualWithoutEntitlementFallsBackToTheFreePreset() {
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), config(IntervalMode.MANUAL))
        assertEquals(IntervalMode.RANDOM_4_7, mode(IntervalMode.MANUAL))
    }

    @Test
    fun manualWithEntitlementUsesTheStoredBounds() {
        assertEquals(
            IntervalConfig.Random(2_000L, 3_000L),
            config(IntervalMode.MANUAL, manual = true),
        )
        assertEquals(IntervalMode.MANUAL, mode(IntervalMode.MANUAL, manual = true))
    }

    @Test
    fun pulseWithoutEntitlementFallsBackToTheFreePreset() {
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), config(IntervalMode.PULSE))
        assertEquals(IntervalMode.RANDOM_4_7, mode(IntervalMode.PULSE))
    }

    @Test
    fun pulseWithEntitlementSchedulesFiveSecondsAndTenToTwenty() {
        assertEquals(
            IntervalConfig.Pulse(5_000L, 10_000L, 20_000L),
            config(IntervalMode.PULSE, pulse = true),
        )
        assertEquals(IntervalMode.PULSE, mode(IntervalMode.PULSE, pulse = true))
    }

    @Test
    fun eachPremiumModeIsGatedByItsOwnFlag() {
        // A live MANUAL trial must not unlock PULSE, and the reverse.
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), config(IntervalMode.PULSE, manual = true))
        assertEquals(IntervalMode.RANDOM_4_7, mode(IntervalMode.PULSE, manual = true))
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), config(IntervalMode.MANUAL, pulse = true))
        assertEquals(IntervalMode.RANDOM_4_7, mode(IntervalMode.MANUAL, pulse = true))
    }

    @Test
    fun freePresetsIgnorePremiumBoundsAndAccessEntirely() {
        assertEquals(IntervalConfig.Fixed(10_000L), config(IntervalMode.FIXED_10S, manual = true))
        assertEquals(IntervalConfig.Fixed(30_000L), config(IntervalMode.FIXED_30S))
        assertEquals(IntervalMode.FIXED_30S, mode(IntervalMode.FIXED_30S))
        assertEquals(IntervalMode.RANDOM_4_7, mode(IntervalMode.RANDOM_4_7))
    }

    @Test
    fun pulseLongSlotStaysInsideTenToTwentySecondsInclusive() {
        val pulse = IntervalConfig.Pulse()
        assertEquals(5_000L, pulse.nextDelayMs(RandomSource { _, _ -> 12_345 }, shortSlot = true))
        // The bounds are handed to the source as-is, both ends included.
        assertEquals(10_000L, pulse.nextDelayMs(RandomSource { from, _ -> from }, shortSlot = false))
        assertEquals(20_000L, pulse.nextDelayMs(RandomSource { _, to -> to }, shortSlot = false))

        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
        repeat(200_000) {
            val v = pulse.nextDelayMs(KotlinRandomSource, shortSlot = false)
            assertTrue("pulse long slot $v outside 10000..20000", v in 10_000..20_000)
            if (v < min) min = v
            if (v > max) max = v
        }
        assertEquals(10_000L, min)
        assertEquals(20_000L, max)
    }

    @Test
    fun aFreshPulseAlwaysStartsOnItsShortSlot() {
        // The default argument is what the scheduler relies on for a new session.
        assertEquals(5_000L, IntervalConfig.Pulse().nextDelayMs(RandomSource { _, _ -> 19_000 }))
    }
}
