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

/** Theme id -> palette. Every ThemeCatalog id has exactly one palette. */
fun paletteFor(themeId: String): ProblipColors = when (themeId) {
    ThemeCatalog.TERMINAL.id -> PALETTE_TERMINAL
    ThemeCatalog.PHOSPHOR.id -> PALETTE_PHOSPHOR
    ThemeCatalog.MIDNIGHT.id -> PALETTE_MIDNIGHT
    ThemeCatalog.AMBER.id -> PALETTE_AMBER
    ThemeCatalog.PINK.id -> PALETTE_PINK
    else -> PALETTE_CLASSIC
}

/** Classic: the Windows Problip "Golden Default". */
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

/** Terminal: cyan accents on a near-black console. */
private val PALETTE_TERMINAL = ProblipColors(
    Bg = Color(0xFF0A0F10),
    Surface = Color(0xFF14201F),
    Raised = Color(0xFF1B2A28),
    Bevel = Color(0xFF3D5A55),
    BDark = Color(0xFF050808),
    Gold = Color(0xFF4DE8E8),
    TextMain = Color(0xFFB8E0DC),
    TextDim = Color(0xFF7AA39F),
    Muted = Color(0xFF4E6B68),
    Compare = Color(0xFF081211),
    Success = Color(0xFF2E9E60),
    Warning = Color(0xFF9E9E2E),
    Danger = Color(0xFFE86A6A),
)

/** Phosphor: green CRT glow. */
private val PALETTE_PHOSPHOR = ProblipColors(
    Bg = Color(0xFF051205),
    Surface = Color(0xFF0E200E),
    Raised = Color(0xFF123012),
    Bevel = Color(0xFF2E6B2E),
    BDark = Color(0xFF020802),
    Gold = Color(0xFF3CFF5E),
    TextMain = Color(0xFFA8F0B0),
    TextDim = Color(0xFF6BB874),
    Muted = Color(0xFF3E7A46),
    Compare = Color(0xFF031403),
    Success = Color(0xFF2EA84A),
    Warning = Color(0xFFA8A82E),
    Danger = Color(0xFFFF7A7A),
)

/** Midnight: deep blue night. */
private val PALETTE_MIDNIGHT = ProblipColors(
    Bg = Color(0xFF0B0E1A),
    Surface = Color(0xFF171E33),
    Raised = Color(0xFF1E2742),
    Bevel = Color(0xFF46538A),
    BDark = Color(0xFF060812),
    Gold = Color(0xFF9DB8FF),
    TextMain = Color(0xFFC9D6F2),
    TextDim = Color(0xFF8B9CC7),
    Muted = Color(0xFF56638C),
    Compare = Color(0xFF070A16),
    Success = Color(0xFF3E7AC0),
    Warning = Color(0xFF7A7AC0),
    Danger = Color(0xFFE07A9E),
)

/** Amber: warm orange CRT. */
private val PALETTE_AMBER = ProblipColors(
    Bg = Color(0xFF140D00),
    Surface = Color(0xFF241A06),
    Raised = Color(0xFF2C2008),
    Bevel = Color(0xFF6B4E1E),
    BDark = Color(0xFF0A0700),
    Gold = Color(0xFFFFB300),
    TextMain = Color(0xFFF2D9A8),
    TextDim = Color(0xFFC7A874),
    Muted = Color(0xFF8C6E3E),
    Compare = Color(0xFF0F0B02),
    Success = Color(0xFF7AA02E),
    Warning = Color(0xFFC08A2E),
    Danger = Color(0xFFE06464),
)

/** Pink: synthwave magenta. */
private val PALETTE_PINK = ProblipColors(
    Bg = Color(0xFF140A14),
    Surface = Color(0xFF241526),
    Raised = Color(0xFF2C1B2E),
    Bevel = Color(0xFF6B3D6E),
    BDark = Color(0xFF0A050A),
    Gold = Color(0xFFFF6EC7),
    TextMain = Color(0xFFF2C4E2),
    TextDim = Color(0xFFC77FAD),
    Muted = Color(0xFF8C4E73),
    Compare = Color(0xFF0F0710),
    Success = Color(0xFF2EA87E),
    Warning = Color(0xFFC02E9E),
    Danger = Color(0xFFFF6E6E),
)
