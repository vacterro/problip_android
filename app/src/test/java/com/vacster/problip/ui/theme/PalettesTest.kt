package com.vacster.problip.ui.theme

import androidx.compose.ui.graphics.Color
import com.vacster.problip.theme.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    @Test
    fun readableForegroundKeepsAccentWhenPossibleAndFallsBackWithoutRecoloring() {
        assertEquals(PALETTE_CLASSIC.Gold,
            readableOn(PALETTE_CLASSIC.Gold, PALETTE_CLASSIC.TextMain, PALETTE_CLASSIC.Raised))
        assertEquals(PALETTE_VINTAGECLASSIC.TextMain,
            readableOn(PALETTE_VINTAGECLASSIC.Gold, PALETTE_VINTAGECLASSIC.TextMain, PALETTE_VINTAGECLASSIC.Raised))
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.001f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
    }

    @Test
    fun polishForegroundPolicyCoversFiveControlPalettes() {
        listOf(PALETTE_CLASSIC, PALETTE_VINTAGECLASSIC, PALETTE_OLED, PALETTE_DRACULA, PALETTE_NORD)
            .forEach { p ->
                val action = readableOn(p.Gold, p.TextMain, p.Raised)
                val secondary = readableOn(p.TextDim, p.TextMain, p.Surface)
                val edge = readableOn(p.Bevel, p.TextDim, p.Raised, 3f)
                listOf(p.Bg, p.Surface, p.Raised, p.Compare).forEach { background ->
                    assertTrue("action text on $background", contrastRatio(action, background) >= 4.5f)
                    assertTrue("control edge on $background", contrastRatio(edge, background) >= 3f)
                }
                assertTrue(contrastRatio(secondary, p.Surface) >= 4.5f)
                assertTrue(contrastRatio(p.TextMain, p.Bg) >= 4.5f)
                assertTrue(contrastRatio(p.Danger, p.Bg) >= 4.5f)
            }
    }

    @Test
    fun everyCatalogThemeHasItsOwnPalette() {
        // paletteFor never silently falls back for a catalog id.
        ThemeCatalog.all.forEach { entry ->
            val palette = paletteFor(entry.id)
            assertNotEquals(palette.Bg, palette.Gold)
            if (entry.id != ThemeCatalog.CLASSIC.id && entry.id != ThemeCatalog.CUSTOM.id) {
                assertNotEquals("palette missing for ${entry.id}", PALETTE_CLASSIC, palette)
            }
        }
    }

    @Test
    fun unknownIdFallsBackToGoldenDefault() {
        assertEquals(PALETTE_CLASSIC, paletteFor("nope"))
        assertEquals(PALETTE_CLASSIC, paletteFor("theme_pink"))
    }

    @Test
    fun accentsAreDistinctExceptCustomWhichMirrorsGoldenDefault() {
        val accents = ThemeCatalog.all
            .filterNot { it.id == ThemeCatalog.CUSTOM.id }
            .map { paletteFor(it.id).Gold }
        assertEquals(accents.size, accents.toSet().size)
        assertEquals(PALETTE_CLASSIC, PALETTE_CUSTOM)
    }

    // Exact-value pins: Wintage palettes are imported verbatim, so any recolor fails here.

    @Test
    fun goldenDefaultMatchesWintage() {
        assertEquals(Color(0xFF1A1810), PALETTE_CLASSIC.Bg)
        assertEquals(Color(0xFF332E22), PALETTE_CLASSIC.Surface)
        assertEquals(Color(0xFFF0D060), PALETTE_CLASSIC.Gold)
        assertEquals(Color(0xFFD4C89A), PALETTE_CLASSIC.TextMain)
        assertEquals(Color(0xFFD66464), PALETTE_CLASSIC.Danger)
    }

    @Test
    fun darkGoldenMatchesWintage() {
        assertEquals(Color(0xFF342012), PALETTE_GOLDEN.Bg)
        assertEquals(Color(0xFF4A341B), PALETTE_GOLDEN.Surface)
        assertEquals(Color(0xFFD3B57A), PALETTE_GOLDEN.Gold)
        assertEquals(Color(0xFFE2CA95), PALETTE_GOLDEN.TextMain)
        assertEquals(Color(0xFF5B9630), PALETTE_GOLDEN.Success)
    }

    @Test
    fun vintageClassicIsTheLightPalette() {
        assertEquals(Color(0xFFC0C0C0), PALETTE_VINTAGECLASSIC.Bg)
        assertEquals(Color(0xFF000000), PALETTE_VINTAGECLASSIC.TextMain)
        assertEquals(Color(0xFF808080), PALETTE_VINTAGECLASSIC.BDark)
        assertEquals(Color(0xFF7A2020), PALETTE_VINTAGECLASSIC.Danger)
        // The only palette whose text is darker than its background.
        assertTrue(PALETTE_VINTAGECLASSIC.TextMain.red < PALETTE_VINTAGECLASSIC.Bg.red)
    }

    @Test
    fun oledIsTrueBlack() {
        assertEquals(Color(0xFF000000), PALETTE_OLED.Bg)
        assertEquals(Color(0xFF000000), PALETTE_OLED.Compare)
        assertEquals(Color(0xFFFFFFFF), PALETTE_OLED.Gold)
        assertEquals(Color(0xFFCE4444), PALETTE_OLED.Danger)
    }

    @Test
    fun draculaMatchesWintage() {
        assertEquals(Color(0xFF21222C), PALETTE_DRACULA.Bg)
        assertEquals(Color(0xFF44475A), PALETTE_DRACULA.Surface)
        assertEquals(Color(0xFFBD93F9), PALETTE_DRACULA.Gold)
        assertEquals(Color(0xFFF8F8F2), PALETTE_DRACULA.TextMain)
    }

    @Test
    fun nordMatchesWintage() {
        assertEquals(Color(0xFF272C36), PALETTE_NORD.Bg)
        assertEquals(Color(0xFF3B4252), PALETTE_NORD.Surface)
        assertEquals(Color(0xFF88C0D0), PALETTE_NORD.Gold)
        assertEquals(Color(0xFFD8DEE9), PALETTE_NORD.TextMain)
    }
}
