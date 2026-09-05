package com.vacster.problip.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {

    @Test
    fun catalogHoldsSixteenWintagePaletteDesignsInScreenOrder() {
        assertEquals(16, ThemeCatalog.all.size)
        assertEquals(
            listOf(
                "theme_classic",
                "theme_wintage_golden",
                "theme_wintage_claudecode",
                "theme_wintage_antigravity",
                "theme_wintage_klite",
                "theme_wintage_freebuff",
                "theme_wintage_codenomad",
                "theme_wintage_fpdefault",
                "theme_wintage_goldenvintage",
                "theme_wintage_vintagedark",
                "theme_wintage_vintageclassic",
                "theme_wintage_oled",
                "theme_wintage_dracula",
                "theme_wintage_nord",
                "theme_wintage_solarized",
                "theme_wintage_custom",
            ),
            ThemeCatalog.all.map { it.id },
        )
        // Ids are persistence keys, never localized display names.
        assertTrue(ThemeCatalog.all.none { it.id == it.displayName })
    }

    @Test
    fun goldenDefaultKeepsTheStableFreeIdWithoutAPremiumDuplicate() {
        assertEquals("theme_classic", ThemeCatalog.CLASSIC.id)
        assertEquals("Golden Default", ThemeCatalog.CLASSIC.displayName)
        assertEquals(1, ThemeCatalog.all.count { it.displayName == "Golden Default" })
    }

    @Test
    fun exactlyOneThemeIsFree() {
        assertEquals(listOf("theme_classic"), ThemeCatalog.all.filter { it.free }.map { it.id })
    }

    @Test
    fun retiredPlaceholderIdsResolveToGoldenDefault() {
        // Pre-release placeholders were replaced by the Wintage catalog; nothing shipped.
        listOf("theme_terminal", "theme_phosphor", "theme_midnight", "theme_amber", "theme_pink")
            .forEach { old ->
                assertFalse(ThemeCatalog.isValidId(old))
                assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective(old))
            }
    }

    @Test
    fun effectiveRendersFreePicksAndFallsBackToGoldenDefault() {
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("theme_classic"))
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("theme_wintage_dracula")) // locked
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("nope"))
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective(null))
        assertTrue(ThemeCatalog.isValidId("theme_wintage_dracula"))
        assertFalse(ThemeCatalog.isValidId("nope"))
    }

    @Test
    fun premiumTrialThemeRendersDuringItsTrial() {
        assertEquals(
            ThemeCatalog.DRACULA,
            ThemeCatalog.effective("theme_wintage_dracula", trials = setOf("theme_wintage_dracula")),
        )
        // Another theme's trial grants nothing: trials are per content item.
        assertEquals(
            ThemeCatalog.CLASSIC,
            ThemeCatalog.effective("theme_wintage_dracula", trials = setOf("theme_wintage_nord")),
        )
    }

    @Test
    fun premiumTrialThemeFallsBackToGoldenDefaultAfterExpiry() {
        // Expiry is expressed by the id leaving the active set.
        assertEquals(
            ThemeCatalog.CLASSIC,
            ThemeCatalog.effective("theme_wintage_dracula", trials = emptySet()),
        )
    }

    @Test
    fun themePackUnlocksEveryPremiumWintageTheme() {
        ThemeCatalog.all.forEach { entry ->
            assertEquals(entry, ThemeCatalog.effective(entry.id, ownsThemePack = true))
        }
    }

    @Test
    fun purchasingThemePackPreservesTheSelectedPremiumTheme() {
        // The stored pick is never rewritten, so the pack restores it after expiry.
        assertEquals(
            ThemeCatalog.DRACULA,
            ThemeCatalog.effective("theme_wintage_dracula", ownsThemePack = true, trials = emptySet()),
        )
    }
}
