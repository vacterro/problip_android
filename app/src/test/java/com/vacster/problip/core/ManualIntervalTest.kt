package com.vacster.problip.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Manual Interval math. Invalid bounds must never reach [BlipScheduler], so every
 * rejection is checked on the resolved config, not on the raw input. The access
 * fallback itself lives in [PremiumIntervalTest].
 */
class ManualIntervalTest {

    private val random = RandomSource { from, _ -> from }

    @Test
    fun equalBoundsBehaveAsAFixedInterval() {
        assertEquals(IntervalConfig.Fixed(1_000L), ManualInterval.config(1, 1))
        assertEquals(IntervalConfig.Fixed(3_600_000L), ManualInterval.config(3600, 3600))
    }

    @Test
    fun differentBoundsBecomeAUniformRangeInMillis() {
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), ManualInterval.config(4, 7))
        assertEquals(4_000L, ManualInterval.config(4, 7).nextDelayMs(random))
    }

    @Test
    fun outOfRangeSecondsAreClampedIntoOneToThreeThousandSixHundred() {
        assertEquals(1 to 7, ManualInterval.sanitize(0, 7))
        assertEquals(1 to 4, ManualInterval.sanitize(-30, 4))
        assertEquals(60 to 3600, ManualInterval.sanitize(60, 9_999))
        assertEquals(IntervalConfig.Random(1_000L, 7_000L), ManualInterval.config(0, 7))
    }

    @Test
    fun fromGreaterThanToNeverReachesTheScheduler() {
        assertEquals(7 to 10, ManualInterval.sanitize(10, 7))
        assertEquals(IntervalConfig.Random(7_000L, 10_000L), ManualInterval.config(10, 7))
    }
}
