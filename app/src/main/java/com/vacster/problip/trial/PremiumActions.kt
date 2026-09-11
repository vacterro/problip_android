package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.theme.ThemeCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * The premium UI action pipeline, lifted out of the ViewModel so the
 * ownership/access readiness barrier and the sound-pool read-modify-write are
 * unit-testable against plain [StateFlow] seams.
 *
 * Every trial-starting entry point first awaits [ownershipReady] and
 * [trialsReady] before it reads any ownership or access snapshot, so a cold
 * start can never deny an already-owned or already-granted sound a trial merely
 * because the initial placeholder was still empty. The persisted sound pool is
 * mutated only through [updateSelectedSounds], which reads the CURRENT DataStore
 * value inside its own atomic edit — never the synthetic [SettingsRepository]
 * startup value — so an early tap cannot erase unrelated persisted selections.
 */
class PremiumActions(
    private val ownershipReady: StateFlow<Boolean>,
    private val trialsReady: StateFlow<Boolean>,
    private val owned: StateFlow<Set<String>>,
    private val access: StateFlow<PremiumAccess>,
    private val startTrial: suspend (contentId: String, owned: Boolean) -> Unit,
    private val updateSelectedSounds: suspend ((Set<String>) -> Set<String>) -> Unit,
) {

    /** MANUAL and PULSE are the premium presets; the free ones have no trial id. */
    private fun premiumFeatureId(mode: IntervalMode): String? = when (mode) {
        IntervalMode.MANUAL -> TrialAccess.FEATURE_MANUAL_INTERVAL
        IntervalMode.PULSE -> TrialAccess.FEATURE_PULSE_INTERVAL
        else -> null
    }

    private fun themePackOwned(): Boolean = ProductCatalog.THEME_PACK in owned.value

    private fun globalGrant(): Boolean = access.value.developerAccess || access.value.earnedPremium

    /** Free, purchased, inside a trial, or under Developer Access. */
    fun soundAccessible(soundId: String): Boolean {
        val entry = SoundCatalog.byId(soundId) ?: return false
        return access.value.grants(
            contentId = soundId,
            free = entry.free,
            owned = soundId in owned.value,
        )
    }

    /**
     * Barrier before any ownership/access-dependent decision: ownership-ready and
     * trials-ready must both hold before their snapshots are read. Suspending,
     * not polling.
     */
    private suspend fun awaitAuthorities() {
        ownershipReady.first { it }
        trialsReady.first { it }
    }

    /**
     * Toggle one sound of the random pool. Tapping a sound that is neither owned
     * nor inside a trial starts its five minutes and adds it to the pool instead
     * of toggling — that is the only way a trial begins. The blip session is
     * never started as a side effect.
     *
     * The last accessible sound cannot be turned off: the session always needs
     * one sound to play, and a trial member counts as one.
     */
    suspend fun toggleSound(soundId: String) {
        awaitAuthorities()
        if (!soundAccessible(soundId)) {
            trySound(soundId)
            return
        }
        updateSelectedSounds { current ->
            val next = if (soundId in current) current - soundId else current + soundId
            if (next.none { soundAccessible(it) }) current else next
        }
    }

    /**
     * Start a sound's trial and select it, never deselect. The store screen's TRY
     * action needs this one-way form: a second tap there must not drop the sound
     * out of the pool the way the sound-selection toggle does.
     */
    suspend fun trySound(soundId: String) {
        awaitAuthorities()
        val granted = soundId in owned.value ||
            access.value.developerAccess ||
            access.value.earnedPremium
        startTrial(soundId, granted)
        updateSelectedSounds { it + soundId }
    }

    /** Starts (or declines to extend) the five-minute BLIP GLOW trial. */
    suspend fun tryBlipGlow() {
        awaitAuthorities()
        startTrial(
            TrialAccess.FEATURE_BLIP_GLOW,
            themePackOwned() || globalGrant(),
        )
    }

    /** Starts the premium interval trial only when the preset is locked. */
    suspend fun startIntervalTrialIfLocked(mode: IntervalMode) {
        awaitAuthorities()
        premiumFeatureId(mode)?.let { featureId ->
            if (!access.value.grants(featureId, owned = themePackOwned())) {
                startTrial(featureId, false)
            }
        }
    }

    /** Starts a premium theme's five-minute trial only when nothing grants it. */
    suspend fun startThemeTrialIfLocked(themeId: String, free: Boolean) {
        awaitAuthorities()
        if (!free && !globalGrant()) startTrial(themeId, false)
    }
}
