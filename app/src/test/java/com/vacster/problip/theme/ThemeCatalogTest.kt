package com.vacster.problip.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {

    @Test
    fun catalogHasStableIdsInRoadmapOrder() {
        assertEquals(
            listOf(
                "theme_classic",
                "theme_terminal",
                "theme_phosphor",
                "theme_midnight",
                "theme_amber",
                "theme_pink",
            ),
            ThemeCatalog.all.map { it.id },
        )
    }

    @Test
    fun onlyClassicIsFreeInThisWave() {
        // Themes sell as one pack from W7; every other entry stays locked.
        assertEquals(listOf("theme_classic"), ThemeCatalog.all.filter { it.free }.map { it.id })
    }

    @Test
    fun effectiveRendersFreePicksAndFallsBackToClassic() {
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("theme_classic"))
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("theme_terminal")) // locked
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("nope"))
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective(null))
        assertTrue(ThemeCatalog.isValidId("theme_pink"))
        assertFalse(ThemeCatalog.isValidId("nope"))
    }
}
