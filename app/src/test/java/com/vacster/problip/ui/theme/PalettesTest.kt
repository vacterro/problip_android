package com.vacster.problip.ui.theme

import com.vacster.problip.theme.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PalettesTest {

    @Test
    fun everyCatalogThemeHasAPalette() {
        ThemeCatalog.all.forEach { entry ->
            val palette = paletteFor(entry.id)
            // A real palette is fully populated: background and accent differ.
            assertNotEquals(entry.id, palette.Bg.toString() + palette.Gold.toString())
            assertNotEquals(palette.Bg, palette.Gold)
        }
    }

    @Test
    fun unknownIdFallsBackToClassicPalette() {
        assertEquals(PALETTE_CLASSIC, paletteFor("nope"))
    }

    @Test
    fun themesHaveDistinctAccents() {
        val accents = ThemeCatalog.all.map { paletteFor(it.id).Gold }
        assertEquals(accents.size, accents.toSet().size)
    }
}
