package com.vacster.problip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
                surface = colors.Surface,
                primary = colors.Gold,
                onPrimary = colors.BDark,
                secondary = colors.Bevel,
                onSecondary = colors.TextMain,
                error = colors.Danger,
            ),
            content = content,
        )
    }
}
