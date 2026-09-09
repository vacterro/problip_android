package com.vacster.problip.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The MAIN-ONLY compact display formatter for lifetime totals that overflow a quarter-width cell (>= 100K). */
class BlipCountCompactTest {

    @Test
    fun belowOneHundredKIsExact() {
        assertEquals("100", blipCountCompact(100L))
        assertEquals("99999", blipCountCompact(99_999L))
    }

    @Test
    fun boundaryAtOneHundredK() {
        assertEquals("100K", blipCountCompact(100_000L))
    }

    @Test
    fun thousandsRoundToOneDecimal() {
        assertEquals("123.4K", blipCountCompact(123_400L))
        assertEquals("999.4K", blipCountCompact(999_499L))
    }

    @Test
    fun millionsRoundToOneDecimal() {
        assertEquals("1.3M", blipCountCompact(1_300_000L))
        assertEquals("14M", blipCountCompact(14_000_000L))
    }
}
