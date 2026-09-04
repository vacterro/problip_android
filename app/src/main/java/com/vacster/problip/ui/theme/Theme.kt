package com.vacster.problip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.vacster.problip.theme.ThemeCatalog

/**
 * Applies the effective theme for [themeId]: locked/unknown ids render as
 * Classic unless [ownsThemePack] proves the Themes Pack entitlement. Switching
 * is instant because only the CompositionLocal-provided palette changes.
 */
@Composable
fun ProblipTheme(
    themeId: String? = ThemeCatalog.CLASSIC.id,
    ownsThemePack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val entry = ThemeCatalog.effective(themeId, ownsThemePack)
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
