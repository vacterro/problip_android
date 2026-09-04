package com.vacster.problip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vacster.problip.ProblipApp
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.BillingConnection
import com.vacster.problip.billing.BillingRepository
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.billing.ProductUi
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.ProblipState
import com.vacster.problip.service.ProblipService
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.theme.ThemeCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin controller since W4: the session lives in ProblipService and survives
 * the activity. The ViewModel only forwards start/stop and exposes the shared
 * session state plus persisted settings.
 */
class ProblipViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository.fromContext(app)
    private val billing: BillingRepository = ProblipApp.billing(app)

    val state: StateFlow<ProblipState> = ProblipSession.state
    val error: StateFlow<String?> = ProblipSession.error

    /** Play ownership (authority) and localized product prices. */
    val owned: StateFlow<Set<String>> = billing.owned
    val products: StateFlow<Map<String, ProductUi>> = billing.products

    /** Store-only state: purchases awaiting Play, connection, last store failure. */
    val pending: StateFlow<Set<String>> = billing.pending
    val connection: StateFlow<BillingConnection> = billing.connection
    val billingError: StateFlow<String?> = billing.error

    fun ownsThemePack(): Boolean = ProductCatalog.THEME_PACK in billing.owned.value

    /** Manual retry from a store screen after an error. */
    fun retryStore() {
        billing.clearError()
        billing.refresh()
    }

    val settings: StateFlow<SettingsRepository.Settings> = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsRepository.Settings(),
    )

    fun start() = ProblipService.start(getApplication())

    fun stop() = ProblipService.stop(getApplication())

    fun setVolume(percent: Int) {
        viewModelScope.launch { settingsRepo.setVolume(percent) }
    }

    fun setIntervalMode(mode: IntervalMode) {
        viewModelScope.launch { settingsRepo.setInterval(mode) }
    }

    /**
     * Toggle one sound of the random pool. The last playable sound cannot be
     * turned off — the session always needs at least one sound to play.
     */
    fun toggleSound(soundId: String) {
        val current = settings.value.selectedSounds
        val next = if (soundId in current) current - soundId else current + soundId
        val stillPlayable = next.any { id ->
            val entry = SoundCatalog.byId(id)
            entry != null && (entry.free || id in billing.owned.value)
        }
        if (!stillPlayable) return
        viewModelScope.launch { settingsRepo.setSelectedSounds(next) }
    }

    /**
     * Persist the theme pick. Locked ids stay stored for now but render as
     * Classic until the Themes Pack entitlement exists (W7).
     */
    fun setTheme(themeId: String) {
        viewModelScope.launch {
            settingsRepo.setTheme(
                if (ThemeCatalog.isValidId(themeId)) themeId else ThemeCatalog.CLASSIC.id,
            )
        }
    }
}
