package com.vacster.problip.service

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.core.IntervalConfig
import com.vacster.problip.core.PremiumInterval
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.trial.PremiumAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the very first blip of a cold process must play, and how fast.
 *
 * A cold start has four asynchronous sources and the scheduler may not start
 * before all four are real:
 *
 *  1. persisted Settings (DataStore) — the pool, the interval, the volume;
 *  2. the ownership snapshot — Play's answer, or the persisted cache standing in
 *     for it until Play answers;
 *  3. temporary access — five-minute trials and Developer Access restored from
 *     their persisted timestamps;
 *  4. the statistics/earned-entitlement read — a persisted 100K Premium reward
 *     must reach the access authority before the first plan, or the first
 *     session of a process would briefly fall back to free content.
 *
 * Reading `owned.value` the moment Settings arrived was a race: the cache seed
 * runs in its own coroutine, so a premium sound could fall back to Original and
 * a persisted MANUAL/PULSE pick could start as random 4–7 for the first session
 * of the process — most visibly on a START from the home-screen widget, where
 * Application.onCreate and the service start are microseconds apart.
 *
 * Barriers, not blocking: this suspends on flows, never on the main thread.
 * [readyTimeoutMs] is the safety valve — a snapshot that never becomes ready
 * (a failed disk read) must degrade to free content instead of leaving the user
 * with a foreground notification and no session at all. The valve covers the
 * WHOLE startup transaction: it starts before the first Settings acquisition,
 * so a settings flow that never emits (or completes empty, or fails with an
 * unexpected non-cancellation error) is bounded by the same budget. A Settings
 * snapshot captured before another authority expires stays authoritative for
 * the fallback plan; only never-received settings resolve from the sanitized
 * [SettingsRepository.Settings] defaults, as runtime degradation only — they
 * are never written back to DataStore.
 */
internal object ColdStart {

    /** The barrier default: a DataStore read is milliseconds, so this is only a valve. */
    const val READY_TIMEOUT_MS = 5_000L

    data class Plan(
        val settings: SettingsRepository.Settings,
        val pool: Set<String>,
        val interval: IntervalConfig,
    )

    /**
     * Suspends until the persisted Settings exist and every access snapshot —
     * ownership, temporary access, statistics/earned entitlement — is
     * initialized, then resolves the first pool and interval from them.
     *
     * The snapshots are passed as a ready-flag plus a getter on purpose: the flag
     * says "this value is no longer a placeholder", while the getter reads the
     * newest value at the moment the plan is built, so a Play answer that lands
     * during the wait is used instead of the cache it superseded.
     *
     * Total for every expected source failure: a never-emitting, empty or
     * throwing settings flow resolves through the bounded fallback instead of
     * hanging or escaping. Cancellation stays cancellation — only the settings
     * acquisition itself is guarded; a timeout's own cancellation passes through
     * [withTimeoutOrNull] untouched.
     */
    suspend fun awaitFirstPlan(
        settings: Flow<SettingsRepository.Settings?>,
        ownershipReady: Flow<Boolean>,
        ownedNow: () -> Set<String>,
        accessReady: Flow<Boolean>,
        accessNow: () -> PremiumAccess,
        statsReady: Flow<Boolean> = MutableStateFlow(true),
        readyTimeoutMs: Long = READY_TIMEOUT_MS,
    ): Plan {
        var persisted: SettingsRepository.Settings? = null
        withTimeoutOrNull(readyTimeoutMs) {
            try {
                persisted = settings.filterNotNull().first()
            } catch (failure: Throwable) {
                // A cancelled wait (including the valve firing mid-acquisition)
                // must stay cancellation; everything else degrades in place.
                if (failure is CancellationException) throw failure
            }
            ownershipReady.first { it }
            accessReady.first { it }
            statsReady.first { it }
        }
        return plan(persisted ?: SettingsRepository.Settings(), ownedNow(), accessNow())
    }

    /** Pure resolution of one snapshot triple; the same rules the running session uses. */
    fun plan(
        settings: SettingsRepository.Settings,
        owned: Set<String>,
        access: PremiumAccess,
    ): Plan {
        val ownsPack = ProductCatalog.THEME_PACK in owned
        return Plan(
            settings = settings,
            pool = SoundCatalog.playableSelection(settings.selectedSounds, owned, access.grantedIds),
            interval = PremiumInterval.effectiveConfig(
                mode = settings.intervalMode,
                fromSeconds = settings.manualFromSeconds,
                toSeconds = settings.manualToSeconds,
                manualAccessible = access.grantsManualInterval(ownsPack),
                pulseAccessible = access.grantsPulseInterval(ownsPack),
            ),
        )
    }
}
