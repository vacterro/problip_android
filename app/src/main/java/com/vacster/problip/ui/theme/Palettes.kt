package com.vacster.problip.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vacster.problip.theme.ThemeCatalog

/** All 13 palette slots every theme must define. */
data class ProblipColors(
    val Bg: Color,
    val Surface: Color,
    val Raised: Color,
    val Bevel: Color,
    val BDark: Color,
    val Gold: Color,
    val TextMain: Color,
    val TextDim: Color,
    val Muted: Color,
    val Compare: Color,
    val Success: Color,
    val Warning: Color,
    val Danger: Color,
)

val LocalProblipColors = staticCompositionLocalOf { PALETTE_CLASSIC }

/**
 * The fifteen Wintage palettes, copied verbatim from the archive's theme JSON files
 * so Problip carries no runtime dependency on it. Wintage tokens map to Problip
 * slots as: background -> Bg, surface -> Surface, surfaceRaised -> Raised,
 * bevelLight -> Bevel, borderDark -> BDark, borderHighlight -> Gold,
 * textPrimary -> TextMain, textSecondary -> TextDim, textMuted -> Muted,
 * compareBack -> Compare, success/warning -> Success/Warning, dangerText -> Danger.
 *
 * Values are never "improved": palette fidelity is the point, and the tests pin
 * several of them exactly so drift shows up as a failure.
 */
private val PALETTES: Map<String, ProblipColors> by lazy {
    mapOf(
        ThemeCatalog.CLASSIC.id to PALETTE_CLASSIC,
        ThemeCatalog.GOLDEN.id to PALETTE_GOLDEN,
        ThemeCatalog.CLAUDECODE.id to PALETTE_CLAUDECODE,
        ThemeCatalog.ANTIGRAVITY.id to PALETTE_ANTIGRAVITY,
        ThemeCatalog.KLITE.id to PALETTE_KLITE,
        ThemeCatalog.FREEBUFF.id to PALETTE_FREEBUFF,
        ThemeCatalog.CODENOMAD.id to PALETTE_CODENOMAD,
        ThemeCatalog.FPDEFAULT.id to PALETTE_FPDEFAULT,
        ThemeCatalog.GOLDENVINTAGE.id to PALETTE_GOLDENVINTAGE,
        ThemeCatalog.VINTAGEDARK.id to PALETTE_VINTAGEDARK,
        ThemeCatalog.VINTAGECLASSIC.id to PALETTE_VINTAGECLASSIC,
        ThemeCatalog.OLED.id to PALETTE_OLED,
        ThemeCatalog.DRACULA.id to PALETTE_DRACULA,
        ThemeCatalog.NORD.id to PALETTE_NORD,
        ThemeCatalog.SOLARIZED.id to PALETTE_SOLARIZED,
    )
}

/** Theme id -> palette. Every ThemeCatalog id has exactly one palette. */
fun paletteFor(themeId: String): ProblipColors = PALETTES[themeId] ?: PALETTE_CLASSIC

/** Golden Default: the free Windows Problip palette (Wintage `goldendefault`). */
val PALETTE_CLASSIC = ProblipColors(
    Bg = Color(0xFF1A1810),
    Surface = Color(0xFF332E22),
    Raised = Color(0xFF3D372A),
    Bevel = Color(0xFF75663D),
    BDark = Color(0xFF100E08),
    Gold = Color(0xFFF0D060),
    TextMain = Color(0xFFD4C89A),
    TextDim = Color(0xFF9C9371),
    Muted = Color(0xFF6E674E),
    Compare = Color(0xFF14120C),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFD66464),
)

/** Dark Golden (Win95): Wintage `golden`. */
internal val PALETTE_GOLDEN = ProblipColors(
    Bg = Color(0xFF342012),
    Surface = Color(0xFF4A341B),
    Raised = Color(0xFF5A4324),
    Bevel = Color(0xFF826941),
    BDark = Color(0xFF1C1208),
    Gold = Color(0xFFD3B57A),
    TextMain = Color(0xFFE2CA95),
    TextDim = Color(0xFFC5AB6E),
    Muted = Color(0xFF95804C),
    Compare = Color(0xFF24170C),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD37676),
)

/** Claude Code: Wintage `claudecode`. */
internal val PALETTE_CLAUDECODE = ProblipColors(
    Bg = Color(0xFF29241D),
    Surface = Color(0xFF3B362A),
    Raised = Color(0xFF484436),
    Bevel = Color(0xFF75644F),
    BDark = Color(0xFF15130F),
    Gold = Color(0xFFD1A27C),
    TextMain = Color(0xFFE0B997),
    TextDim = Color(0xFFC39870),
    Muted = Color(0xFF93704E),
    Compare = Color(0xFF1C1914),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD37575),
)

/** Antigravity: Wintage `antigravity`. */
internal val PALETTE_ANTIGRAVITY = ProblipColors(
    Bg = Color(0xFF1B1F2C),
    Surface = Color(0xFF272B3E),
    Raised = Color(0xFF31354D),
    Bevel = Color(0xFF4B6678),
    BDark = Color(0xFF0D0F17),
    Gold = Color(0xFF7AD0D3),
    TextMain = Color(0xFF95DEE2),
    TextDim = Color(0xFF6EBFC5),
    Muted = Color(0xFF4C8F95),
    Compare = Color(0xFF12151E),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD06D6D),
)

/** K-Lite (MPC-HC): Wintage `klite`. */
internal val PALETTE_KLITE = ProblipColors(
    Bg = Color(0xFF212325),
    Surface = Color(0xFF303235),
    Raised = Color(0xFF3C3F42),
    Bevel = Color(0xFF5E6165),
    BDark = Color(0xFF111213),
    Gold = Color(0xFFA2A5AB),
    TextMain = Color(0xFFB8BABF),
    TextDim = Color(0xFF95989E),
    Muted = Color(0xFF6D6F74),
    Compare = Color(0xFF171819),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD27272),
)

/** FreeBuff: Wintage `freebuff`. */
internal val PALETTE_FREEBUFF = ProblipColors(
    Bg = Color(0xFF1B232B),
    Surface = Color(0xFF28303D),
    Raised = Color(0xFF333B4B),
    Bevel = Color(0xFF506B5F),
    BDark = Color(0xFF0E1116),
    Gold = Color(0xFF89D37A),
    TextMain = Color(0xFFA0E295),
    TextDim = Color(0xFF7AC56E),
    Muted = Color(0xFF55954C),
    Compare = Color(0xFF13181D),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD27272),
)

/** CodeNomad: Wintage `codenomad`. */
internal val PALETTE_CODENOMAD = ProblipColors(
    Bg = Color(0xFF1C242A),
    Surface = Color(0xFF29313C),
    Raised = Color(0xFF343D4A),
    Bevel = Color(0xFF575776),
    BDark = Color(0xFF0E1216),
    Gold = Color(0xFF9D86D1),
    TextMain = Color(0xFFB099DE),
    TextDim = Color(0xFF9C84C8),
    Muted = Color(0xFF675091),
    Compare = Color(0xFF13181D),
    Success = Color(0xFF5B9630),
    Warning = Color(0xFF969630),
    Danger = Color(0xFFD27272),
)

/** Default: Wintage `fpdefault`. */
internal val PALETTE_FPDEFAULT = ProblipColors(
    Bg = Color(0xFF1A1A1A),
    Surface = Color(0xFF2B2B2B),
    Raised = Color(0xFF343434),
    Bevel = Color(0xFF4E555B),
    BDark = Color(0xFF0A0A0A),
    Gold = Color(0xFF839BB0),
    TextMain = Color(0xFFC0C0C0),
    TextDim = Color(0xFF949494),
    Muted = Color(0xFF656565),
    Compare = Color(0xFF141414),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFDB7575),
)

/** Golden Vintage: Wintage `goldenvintage`. */
internal val PALETTE_GOLDENVINTAGE = ProblipColors(
    Bg = Color(0xFF0F0F0F),
    Surface = Color(0xFF2B2B2B),
    Raised = Color(0xFF333333),
    Bevel = Color(0xFF655E4A),
    BDark = Color(0xFF050505),
    Gold = Color(0xFFD6BE76),
    TextMain = Color(0xFFC4BA9F),
    TextDim = Color(0xFF8E8774),
    Muted = Color(0xFF605C50),
    Compare = Color(0xFF0B0B0B),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFD45C5C),
)

/** Vintage Dark: Wintage `vintagedark`. */
internal val PALETTE_VINTAGEDARK = ProblipColors(
    Bg = Color(0xFF181818),
    Surface = Color(0xFF2B2B2B),
    Raised = Color(0xFF343434),
    Bevel = Color(0xFF4A5258),
    BDark = Color(0xFF0A0A0A),
    Gold = Color(0xFF738EA6),
    TextMain = Color(0xFFC0C0C0),
    TextDim = Color(0xFF8E8E8E),
    Muted = Color(0xFF646464),
    Compare = Color(0xFF121212),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFD45D5D),
)

/**
 * Vintage Classic: Wintage `vintageclassic`, the one LIGHT palette — silver
 * surfaces with black text, exactly as the archive defines it.
 */
internal val PALETTE_VINTAGECLASSIC = ProblipColors(
    Bg = Color(0xFFC0C0C0),
    Surface = Color(0xFFC0C0C0),
    Raised = Color(0xFFD0D0D0),
    Bevel = Color(0xFFF6F6F6),
    BDark = Color(0xFF808080),
    Gold = Color(0xFFF6F6F6),
    TextMain = Color(0xFF000000),
    TextDim = Color(0xFF3A3A3A),
    Muted = Color(0xFF6A6A6A),
    Compare = Color(0xFFD0D0D0),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFF7A2020),
)

/** Dark 2 (OLED): Wintage `oled`, true black. */
internal val PALETTE_OLED = ProblipColors(
    Bg = Color(0xFF000000),
    Surface = Color(0xFF0A0A0A),
    Raised = Color(0xFF141414),
    Bevel = Color(0xFF5C5C5C),
    BDark = Color(0xFF1A1A1A),
    Gold = Color(0xFFFFFFFF),
    TextMain = Color(0xFFA0A0A0),
    TextDim = Color(0xFF777777),
    Muted = Color(0xFF484848),
    Compare = Color(0xFF000000),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFCE4444),
)

/** Dracula: Wintage `dracula`. */
internal val PALETTE_DRACULA = ProblipColors(
    Bg = Color(0xFF21222C),
    Surface = Color(0xFF44475A),
    Raised = Color(0xFF4C526D),
    Bevel = Color(0xFF706A9E),
    BDark = Color(0xFF191A21),
    Gold = Color(0xFFBD93F9),
    TextMain = Color(0xFFF8F8F2),
    TextDim = Color(0xFFB8B8B7),
    Muted = Color(0xFF828285),
    Compare = Color(0xFF191A21),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFDA7373),
)

/** Nord: Wintage `nord`. */
internal val PALETTE_NORD = ProblipColors(
    Bg = Color(0xFF272C36),
    Surface = Color(0xFF3B4252),
    Raised = Color(0xFF3F4758),
    Bevel = Color(0xFF566C7D),
    BDark = Color(0xFF232831),
    Gold = Color(0xFF88C0D0),
    TextMain = Color(0xFFD8DEE9),
    TextDim = Color(0xFFA3A9B3),
    Muted = Color(0xFF777C87),
    Compare = Color(0xFF1D2129),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFDE8282),
)

/** Solarized Dark: Wintage `solarized`. */
internal val PALETTE_SOLARIZED = ProblipColors(
    Bg = Color(0xFF002B36),
    Surface = Color(0xFF073642),
    Raised = Color(0xFF1B444F),
    Bevel = Color(0xFF36667D),
    BDark = Color(0xFF001F27),
    Gold = Color(0xFF51A2DB),
    TextMain = Color(0xFF93A1A1),
    TextDim = Color(0xFF8D9EA1),
    Muted = Color(0xFF426066),
    Compare = Color(0xFF002029),
    Success = Color(0xFF4A7A20),
    Warning = Color(0xFF7A7A20),
    Danger = Color(0xFFDD7D7D),
)
