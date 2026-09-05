package com.vacster.problip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vacster.problip.theme.ThemeCatalog

/**
 * Applies the effective theme for [themeId]: a premium palette renders when
 * [ownsThemePack] proves the Themes Pack entitlement or while the theme's own id
 * is in [trials]; anything else renders Classic, so an expired trial cannot keep
 * painting a paid palette. Switching is instant because only the
 * CompositionLocal-provided palette changes.
 */
@Composable
fun ProblipTheme(
    themeId: String? = ThemeCatalog.CLASSIC.id,
    ownsThemePack: Boolean = false,
    trials: Set<String> = emptySet(),
    content: @Composable () -> Unit,
) {
    val entry = ThemeCatalog.effective(themeId, ownsThemePack, trials)
    val colors = paletteFor(entry.id)
    CompositionLocalProvider(LocalProblipColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = colors.Bg,
                onBackground = colors.TextMain,
                surface = colors.Surface,
                onSurface = colors.TextMain,
                onSurfaceVariant = readableOn(colors.TextDim, colors.TextMain, colors.Surface),
                primary = readableOn(colors.Gold, colors.TextMain, colors.Raised),
                onPrimary = colors.Bg,
                secondary = colors.Bevel,
                onSecondary = colors.TextMain,
                error = colors.Danger,
            ),
            content = content,
        )
    }
}

/** Prefer the palette accent, but use its own text token when the surface defeats it. */
internal fun readableOn(
    preferred: Color,
    fallback: Color,
    background: Color,
    minimum: Float = 4.5f,
): Color = if (contrastRatio(preferred, background) >= minimum) preferred else fallback

internal fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}
