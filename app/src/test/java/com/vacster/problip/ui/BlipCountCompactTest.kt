package com.vacster.problip.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The MAIN-ONLY compact display formatter for lifetime totals that overflow a quarter-width cell (>= 100K). Locale is injected — tests never depend on the machine locale. */
class BlipCountCompactTest {

    @Test
    fun belowOneHundredKIsExact() {
        assertEquals("100", blipCountCompact(100L, Locale.US))
        assertEquals("99999", blipCountCompact(99_999L, Locale.US))
    }

    @Test
    fun boundaryAtOneHundredK() {
        assertEquals("100K", blipCountCompact(100_000L, Locale.US))
        assertEquals("100K", formatBlipCountForStrip(100_000L, Locale.US))
        assertEquals("100K", formatBlipCountForStrip(100_000L, Locale.GERMANY))
    }

    @Test
    fun thousandsRoundToOneDecimal() {
        assertEquals("123.4K", blipCountCompact(123_400L, Locale.US))
        assertEquals("999.4K", blipCountCompact(999_499L, Locale.US))
    }

    @Test
    fun millionsRoundToOneDecimal() {
        assertEquals("1.3M", blipCountCompact(1_300_000L, Locale.US))
        assertEquals("14M", blipCountCompact(14_000_000L, Locale.US))
    }

    @Test
    fun englishDecimalSeparatorIsPeriod() {
        assertEquals("123.4K", blipCountCompact(123_400L, Locale.US))
        assertEquals("1.3M", blipCountCompact(1_300_000L, Locale.US))
    }

    @Test
    fun commaDecimalLocaleUsesComma() {
        assertEquals("123,4K", blipCountCompact(123_400L, Locale.GERMANY))
        assertEquals("1,3M", blipCountCompact(1_300_000L, Locale.GERMANY))
    }

    @Test
    fun exactIntegerBoundaryIsLocaleFormattedInStrip() {
        assertEquals("99.999", formatBlipCountForStrip(99_999L, Locale.GERMANY))
        assertEquals("99,999", formatBlipCountForStrip(99_999L, Locale.US))
    }

    @Test
    fun accessibilityExactValueStaysIndependentOfCompactDisplay() {
        assertEquals("123,400", formatBlipCountExact(123_400L, Locale.US))
        assertEquals("123.4K", formatBlipCountForStrip(123_400L, Locale.US))
        assertEquals("123.400", formatBlipCountExact(123_400L, Locale.GERMANY))
        assertEquals("123,4K", formatBlipCountForStrip(123_400L, Locale.GERMANY))
    }
}
