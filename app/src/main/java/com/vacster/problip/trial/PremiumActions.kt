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
 *
 * W2-004 Parts C+D: both persistence seams return [PersistenceOutcome]. A
 * compound command (required trial write -> final selection write) never
 * performs its final write when the prerequisite trial persistence failed, and
 * never claims success after a failed write — it returns the outcome so the
 * caller can surface the settings persistence failure. A successfully persisted
 * trial is NEVER rolled back when the second write fails.
 */
class PremiumActions(
    private val ownershipReady: StateFlow<Boolean>,
    private val trialsReady: StateFlow<Boolean>,
    private val owned: StateFlow<Set<String>>,
    private val access: StateFlow<PremiumAccess>,
    private val startTrial: suspend (contentId: String, owned: Boolean) -> PersistenceOutcome,
    private val updateSelectedSounds: suspend ((Set<String>) -> Set<String>) -> PersistenceOutcome,
) {

    /**
     * Latest-intent check for the sound-selection domain (W2-003). Deliberately
     * a minimal functional seam so the trial package keeps no dependency on the
     * UI layer: the ViewModel passes the token allocated at invocation time.
     */
    fun interface SoundIntent {
        fun isCurrent(): Boolean
    }

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
     *
     * W2-003: [intent] is the caller's already-allocated sound-domain command
     * identity. It is passed through to the TRY path unchanged — the delegation
     * must never allocate a second revision (self-staleness). The final pool
     * mutation is suppressed when a newer sound command has superseded this one.
     *
     * W2-004: returns the final persistence outcome ([PersistenceOutcome.IO_FAILED]
     * when the required trial or the pool write failed; a failed trial write stops
     * the command before the pool mutation).
     */
    suspend fun toggleSound(soundId: String, intent: SoundIntent? = null): PersistenceOutcome {
        awaitAuthorities()
        if (!soundAccessible(soundId)) {
            return trySound(soundId, intent)
        }
        if (intent != null && !intent.isCurrent()) return PersistenceOutcome.NOT_REQUIRED
        return updateSelectedSounds { current ->
            val next = if (soundId in current) current - soundId else current + soundId
            if (next.none { soundAccessible(it) }) current else next
        }
    }

    /**
     * Start a sound's trial and select it, never deselect. The store screen's TRY
     * action needs this one-way form: a second tap there must not drop the sound
     * out of the pool the way the sound-selection toggle does.
     *
     * W2-003: [intent] carries the caller's latest-intent revision for the SOUND
     * domain. The trial write (already begun) always finishes — DataStore work is
     * never cancelled — but the final selected-pool mutation happens only when
     * that intent is still current, so a stale TRY continuation can never
     * overwrite a newer sound selection. A null intent (legacy callers) keeps
     * the unconditional behavior.
     *
     * W2-004: when the required trial persistence fails, the selected pool is NOT
     * mutated and [PersistenceOutcome.IO_FAILED] is returned. When the trial
     * persisted but the pool write failed, the trial legitimately stays — no
     * compensating rollback — and the failure is still reported. A stale intent
     * reports [PersistenceOutcome.NOT_REQUIRED]: the command made no claim.
     */
    suspend fun trySound(soundId: String, intent: SoundIntent? = null): PersistenceOutcome {
        awaitAuthorities()
        val granted = soundId in owned.value ||
            access.value.developerAccess ||
            access.value.earnedPremium
        val trial = startTrial(soundId, granted)
        if (trial == PersistenceOutcome.IO_FAILED) return trial
        if (intent != null && !intent.isCurrent()) return PersistenceOutcome.NOT_REQUIRED
        return updateSelectedSounds { it + soundId }
    }

    /** Starts (or declines to extend) the five-minute BLIP GLOW trial. */
    suspend fun tryBlipGlow(): PersistenceOutcome {
        awaitAuthorities()
        return startTrial(
            TrialAccess.FEATURE_BLIP_GLOW,
            themePackOwned() || globalGrant(),
        )
    }

    /**
     * Starts the premium interval trial only when the preset is locked, returning
     * whether the REQUIRED trial persistence committed. [PersistenceOutcome.IO_FAILED]
     * means the caller must NOT persist the premium interval selection; any other
     * outcome (free preset, already granted, trial persisted) permits it.
     */
    suspend fun startIntervalTrialIfLocked(mode: IntervalMode): PersistenceOutcome {
        awaitAuthorities()
        val featureId = premiumFeatureId(mode) ?: return PersistenceOutcome.NOT_REQUIRED
        if (access.value.grants(featureId, owned = themePackOwned())) {
            return PersistenceOutcome.NOT_REQUIRED
        }
        return startTrial(featureId, false)
    }

    /**
     * Starts a premium theme's five-minute trial only when nothing grants it.
     * The Theme Pack purchase permanently unlocks every non-free theme, so it
     * must suppress the trial exactly like a global grant does.
     *
     * W2-004: [PersistenceOutcome.IO_FAILED] means the required theme trial
     * could not be persisted and the locked theme must NOT be persisted as
     * though access existed.
     */
    suspend fun startThemeTrialIfLocked(themeId: String, free: Boolean): PersistenceOutcome {
        awaitAuthorities()
        if (!free && !themePackOwned() && !globalGrant()) return startTrial(themeId, false)
        return PersistenceOutcome.NOT_REQUIRED
    }
}
