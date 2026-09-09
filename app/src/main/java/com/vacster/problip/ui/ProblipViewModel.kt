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
import com.vacster.problip.settings.BlipCounterMode
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.stats.BlipStats
import com.vacster.problip.stats.BlipStatsRecord
import com.vacster.problip.stats.BlipStatsRepository
import com.vacster.problip.stats.PREMIUM_REWARD_BLIPS
import com.vacster.problip.theme.ThemeCatalog
import com.vacster.problip.trial.PremiumAccess
import com.vacster.problip.trial.TrialAccess
import com.vacster.problip.trial.TrialCoordinator
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
    private val trials: TrialCoordinator = ProblipApp.trials(app)
    private val stats: BlipStatsRepository = ProblipApp.stats(app)

    /** Lifetime blip statistics and the 100K Premium reward backed by them. */
    val statsRecord: StateFlow<BlipStatsRecord> = stats.record

    /**
     * The display snapshot for the CURRENT periods, recomputed on each record
     * emission. Stale buckets (yesterday, last week/month) read zero here.
     */
    fun currentStats(): BlipStats = stats.snapshot()
    val premiumRewardBlips: Long get() = PREMIUM_REWARD_BLIPS

    val state: StateFlow<ProblipState> = ProblipSession.state
    val error: StateFlow<String?> = ProblipSession.error

    /** Play ownership (authority) and localized product prices. */
    val owned: StateFlow<Set<String>> = billing.owned
    val products: StateFlow<Map<String, ProductUi>> = billing.products

    /**
     * Temporary access (five-minute trials + seven-day Developer Access), kept
     * separate from [owned] on purpose: Billing must never believe a trial or a
     * developer unlock is a purchase.
     */
    val access: StateFlow<PremiumAccess> = trials.access
    val trialExpiries: StateFlow<Map<String, Long>> = trials.expiries
    val developerExpiryMillis: StateFlow<Long> = trials.developerExpiryMillis

    /** Store-only state: purchases awaiting Play, connection, last store failure (as a string resource). */
    val pending: StateFlow<Set<String>> = billing.pending
    val connection: StateFlow<BillingConnection> = billing.connection
    val billingError: StateFlow<Int?> = billing.error

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

    fun setShowBlipCounter(show: Boolean) {
        viewModelScope.launch { settingsRepo.setShowBlipCounter(show) }
    }

    /** Display-only: never touches statistics recording. */
    fun setBlipCounterMode(mode: BlipCounterMode) {
        viewModelScope.launch { settingsRepo.setBlipCounterMode(mode) }
    }

    fun setBlipGlowEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBlipGlowEnabled(enabled) }
    }

    /** Starts (or declines to extend) the five-minute BLIP GLOW trial. */
    fun tryBlipGlow() {
        viewModelScope.launch {
            trials.startTrial(
                contentId = TrialAccess.FEATURE_BLIP_GLOW,
                owned = ownsThemePack() || access.value.developerAccess || access.value.earnedPremium,
            )
        }
    }

    fun start() = ProblipService.start(getApplication())

    fun stop() = ProblipService.stop(getApplication())

    fun setVolume(percent: Int) {
        viewModelScope.launch { settingsRepo.setVolume(percent) }
    }

    fun setIntervalMode(mode: IntervalMode) {
        viewModelScope.launch {
            // Tapping a premium preset without an entitlement starts its five-minute
            // trial instead of showing a purchase gate. Re-tapping during the trial
            // never extends it (TrialAccess.startTrial decides that).
            premiumFeatureId(mode)?.let { featureId ->
                if (!access.value.grants(featureId, owned = ownsThemePack())) {
                    trials.startTrial(featureId, owned = false)
                }
            }
            settingsRepo.setInterval(mode)
        }
    }

    /** MANUAL and PULSE are the premium presets; the free ones have no trial id. */
    private fun premiumFeatureId(mode: IntervalMode): String? = when (mode) {
        IntervalMode.MANUAL -> TrialAccess.FEATURE_MANUAL_INTERVAL
        IntervalMode.PULSE -> TrialAccess.FEATURE_PULSE_INTERVAL
        else -> null
    }

    /** Both premium intervals ride the existing theme_pack purchase; no new Play SKU. */
    fun manualIntervalAccessible(): Boolean = access.value.grantsManualInterval(ownsThemePack())

    fun pulseIntervalAccessible(): Boolean = access.value.grantsPulseInterval(ownsThemePack())

    /** Only validated bounds are persisted; the repository clamps and orders them. */
    fun setManualInterval(fromSeconds: Int, toSeconds: Int) {
        viewModelScope.launch { settingsRepo.setManualInterval(fromSeconds, toSeconds) }
    }

    /**
     * Hidden developer convenience behind the two-finger title chord: seven days of
     * global premium access, reset (not stacked) on every successful unlock.
     */
    fun enableDeveloperAccess() {
        viewModelScope.launch { trials.enableDeveloperAccess() }
    }

    /** Back to free + genuinely purchased content. Play ownership is untouched. */
    fun resetTemporaryAccess() {
        viewModelScope.launch { trials.resetTemporaryAccess() }
    }

    /** Free, purchased, inside a trial, or under Developer Access. */
    private fun accessible(soundId: String): Boolean {
        val entry = SoundCatalog.byId(soundId) ?: return false
        return access.value.grants(
            contentId = soundId,
            free = entry.free,
            owned = soundId in billing.owned.value,
        )
    }

    /**
     * Toggle one sound of the random pool.
     *
     * Tapping a sound that is neither owned nor inside a trial starts its five
     * minutes and adds it to the pool instead of toggling — that is the only way
     * a trial begins. The blip session is never started as a side effect.
     *
     * The last accessible sound cannot be turned off: the session always needs
     * one sound to play, and a trial member counts as one.
     */
    fun toggleSound(soundId: String) {
        SoundCatalog.byId(soundId) ?: return
        if (!accessible(soundId)) {
            trySound(soundId)
            return
        }
        val current = settings.value.selectedSounds
        val next = if (soundId in current) current - soundId else current + soundId
        if (next.none { accessible(it) }) return
        viewModelScope.launch { settingsRepo.setSelectedSounds(next) }
    }

    /**
     * Start a sound's trial and select it, never deselect. The store screen's TRY
     * action needs this one-way form: a second tap there must not drop the sound
     * out of the pool the way the sound-selection toggle does.
     */
    fun trySound(soundId: String) {
        SoundCatalog.byId(soundId) ?: return
        viewModelScope.launch {
            // An existing grant (Developer Access or the earned reward) means no
            // timer is worth starting.
            val granted = soundId in billing.owned.value ||
                access.value.developerAccess ||
                access.value.earnedPremium
            trials.startTrial(soundId, owned = granted)
            settingsRepo.setSelectedSounds(settings.value.selectedSounds + soundId)
        }
    }

    /**
     * Persist the theme pick. A premium palette without the pack starts its own
     * five-minute trial; re-picking one whose trial is still running selects it
     * without extending the timer, and the stored id survives expiry so a later
     * purchase or a new trial restores it.
     */
    fun setTheme(themeId: String) {
        val entry = ThemeCatalog.byId(themeId) ?: return
        val granted = ownsThemePack() ||
            access.value.developerAccess ||
            access.value.earnedPremium
        viewModelScope.launch {
            if (!entry.free && !granted) trials.startTrial(entry.id, owned = false)
            settingsRepo.setTheme(entry.id)
        }
    }
}
