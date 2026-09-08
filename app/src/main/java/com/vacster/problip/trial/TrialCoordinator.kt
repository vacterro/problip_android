package com.vacster.problip.trial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-level temporary access: five-minute content trials and the seven-day
 * Developer Access override. The persisted timestamps are the truth; this only
 * turns them into the live [PremiumAccess] the service and the UI both read.
 *
 * Scheduling is deliberately minimal: one delay until the NEXT expiry — whichever
 * of the trials or Developer Access comes first — then a recompute that schedules
 * the one after it. No one-second ticker drives access, no AlarmManager and no
 * WorkManager; the timestamps are simply re-evaluated on the next process start.
 *
 * Writes go through [update] so the read-modify-write happens inside the store's
 * own atomic edit: rapid repeated taps then cannot lose each other's changes.
 */
class TrialCoordinator(
    private val persisted: Flow<TemporaryAccess>,
    private val update: suspend ((Map<String, Long>) -> Map<String, Long>) -> Unit,
    private val setDeveloperExpiry: suspend (Long) -> Unit,
    private val clearTemporary: suspend () -> Unit,
    private val scope: CoroutineScope,
    private val clock: TrialClock = SystemTrialClock,
    /** The stats-side 100K reward, combined HERE so callers read one authority. */
    earnedPremium: StateFlow<Boolean> = MutableStateFlow(false),
    /**
     * The stats-side readiness signal: true once the persisted earned state has
     * been loaded from its DataStore. The first [ready] publication waits for
     * it, so `ready == true` guarantees the access snapshot already contains
     * the initialized earned value — a cold start can never resolve its first
     * plan against `earned = false` while the stats read is still in flight.
     * The default (always ready) is for callers that construct the coordinator
     * with an already-initialized earned flow.
     */
    private val earnedReady: StateFlow<Boolean> = MutableStateFlow(true),
) {

    private val _expiries = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Every known trial expiry, for the informational countdown in the UI. */
    val expiries: StateFlow<Map<String, Long>> = _expiries.asStateFlow()

    private val _developerExpiryMillis = MutableStateFlow(0L)

    /** Developer Access expiry, for the Settings screen. Zero when never unlocked. */
    val developerExpiryMillis: StateFlow<Long> = _developerExpiryMillis.asStateFlow()

    private val _access = MutableStateFlow(PremiumAccess())

    /**
     * The effective-access authority: what is temporarily granted right now,
     * plus the permanent earned reward. Every caller reads this one flow — no
     * call site combines TrialCoordinator.access with the stats repository.
     */
    val access: StateFlow<PremiumAccess> = _access.asStateFlow()

    /**
     * False until the persisted timestamps have been evaluated once. A cold start
     * must not resolve its first session from the empty placeholder [access]: a
     * live trial or Developer Access restored from disk arrives a moment later,
     * and by then the pool and the interval would already be wrong.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _earnedPremium = MutableStateFlow(false)

    init {
        // The earned reward is an input to the SAME recompute, so a persisted
        // reward and a restored trial can never disagree with one authority.
        // Before the first snapshot exists, track() owns the recompute: setting
        // ready from here would advertise a placeholder as initialized.
        scope.launch {
            earnedPremium.collect { earned ->
                _earnedPremium.value = earned
                if (_ready.value) recompute()
            }
        }
    }

    fun start() {
        scope.launch {
            persisted.distinctUntilChanged().collectLatest { track(it) }
        }
    }

    /** Recomputes access from the current snapshots, then reschedules nothing. */
    private fun recompute() {
        val now = clock.nowMillis()
        val temporary = TemporaryAccess(_expiries.value, _developerExpiryMillis.value)
        _access.value = PremiumAccess(
            activeTrials = TrialAccess.activeIds(temporary.trialExpiries, now),
            developerAccess = PremiumAccess.developerActive(
                temporary.developerExpiryMillis,
                now,
            ),
            earnedPremium = _earnedPremium.value,
        )
        _ready.value = true
    }

    /** Recomputes access, then sleeps exactly until the next expiration is due. */
    private suspend fun track(temporary: TemporaryAccess) {
        // Atomic cold start: the FIRST ready must already contain the initialized
        // earned value, so temporary access never outruns the stats read. The
        // earned collector recomputes immediately once the reward arrives later.
        earnedReady.first { it }
        while (true) {
            val now = clock.nowMillis()
            _expiries.value = TrialAccess.sanitize(temporary.trialExpiries)
            _developerExpiryMillis.value = temporary.developerExpiryMillis
            recompute()
            val next = nextExpiryMillis(temporary, now) ?: return
            delay(next - now)
        }
    }

    /** Earliest still-future expiry of either kind; null when nothing is running. */
    private fun nextExpiryMillis(temporary: TemporaryAccess, nowMillis: Long): Long? {
        val trial = TrialAccess.nextExpiryMillis(temporary.trialExpiries, nowMillis)
        val developer = temporary.developerExpiryMillis.takeIf { it > nowMillis }
        return listOfNotNull(trial, developer).minOrNull()
    }

    /**
     * Starts the trial of [contentId], or leaves a running one exactly as it is.
     * Expired records are pruned in the same write.
     */
    suspend fun startTrial(contentId: String, owned: Boolean) {
        if (!TrialAccess.isTrialable(contentId) || owned) return
        val now = clock.nowMillis()
        update { current ->
            TrialAccess.startTrial(
                expiries = TrialAccess.prune(current, now),
                contentId = contentId,
                nowMillis = now,
                owned = false,
            )
        }
    }

    /**
     * Hidden developer convenience: seven days of global premium access from now.
     * Performing the gesture again resets the window instead of stacking it.
     */
    suspend fun enableDeveloperAccess() {
        setDeveloperExpiry(PremiumAccess.developerExpiryFrom(clock.nowMillis()))
    }

    /**
     * Drops every temporary grant in one store edit, so access recomputes once.
     * Play ownership lives in another key and is deliberately untouched.
     */
    suspend fun resetTemporaryAccess() {
        clearTemporary()
    }
}
