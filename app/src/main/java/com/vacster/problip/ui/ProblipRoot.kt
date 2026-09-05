package com.vacster.problip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Four destinations; too few to justify a navigation dependency. */
enum class ProblipRoute { MAIN, SOUNDS, THEMES, SETTINGS }

/**
 * Screen switch for the W11 layout: the utility screen stays first, the store
 * screens are one tap away, and system back always returns to the main screen.
 */
@Composable
fun ProblipRoot(
    viewModel: ProblipViewModel,
    onStartRequested: () -> Unit,
    onPurchaseRequested: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    var route by remember { mutableStateOf(ProblipRoute.MAIN) }

    BackHandler(enabled = route != ProblipRoute.MAIN) { route = ProblipRoute.MAIN }

    when (route) {
        ProblipRoute.MAIN -> ProblipScreen(
            viewModel = viewModel,
            onStartRequested = onStartRequested,
            onOpenSounds = { route = ProblipRoute.SOUNDS },
            onOpenThemes = { route = ProblipRoute.THEMES },
            onOpenSettings = { route = ProblipRoute.SETTINGS },
            onMinimize = onMinimize,
        )
        ProblipRoute.SOUNDS -> SoundsScreen(
            viewModel = viewModel,
            onBack = { route = ProblipRoute.MAIN },
            onPurchaseRequested = onPurchaseRequested,
        )
        ProblipRoute.THEMES -> ThemesScreen(
            viewModel = viewModel,
            onBack = { route = ProblipRoute.MAIN },
            onPurchaseRequested = onPurchaseRequested,
        )
        ProblipRoute.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { route = ProblipRoute.MAIN },
        )
    }
}
