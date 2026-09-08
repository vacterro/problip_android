package com.vacster.problip.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.problipStatsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "problip_stats",
)

/**
 * Process-level blip statistics on their own Preferences DataStore
 * (`problip_stats`), deliberately separate from SettingsRepository: a count
 * changes on every successful blip, and the settings flow must not emit each
 * time audio plays.
 *
 * The DataStore is a persistence log, NOT a live authority: it is read exactly
 * once during [start] and never fed back. After that read the in-memory record
 * is authoritative for the lifetime of the repository, because continuously
 * collecting `dataStore.data` here races against this repository's OWN writes —
 * an older delayed persistence emission could overwrite a newer in-memory blip
 * and silently lose it. Disk writes never move memory.
 *
 * Persistence is secondary to audio. [recordSuccessfulBlip] updates the
 * in-memory record synchronously and cheaply, and the write happens later —
 * at most one write per [PERSIST_INTERVAL_MS] no matter how fast blips come —
 * so playback never waits on disk I/O. Two exceptions bypass the batching:
 * crossing the 100K reward persists immediately (losing the entitlement once
 * granted is never acceptable), and [flush] serves normal STOP/teardown.
 * A tiny loss window for the display buckets after an abrupt process kill is
 * an accepted trade.
 *
 * A failed or corrupt read never produces a session error, never stops the
 * scheduler and never stops audio: the flows degrade to a safe record. The
 * one entitlement fact, [earnedPremium], participates in the cold-start
 * barrier through [ready] so a persisted reward is honoured from the very
 * first blip instead of being read too late.
 */
class BlipStatsRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    val clock: Clock = Clock.systemDefaultZone(),
) {

    private val _record = MutableStateFlow(BlipStatsRecord())

    /**
     * The in-memory record — authoritative after [start] completes the one
     * initial load. Display code resolves it against the CURRENT [PeriodKeys]
     * (see [BlipStatsLogic.snapshot]) so a finished period reads zero; the
     * stored counts are what survived the last persist.
     */
    val record: StateFlow<BlipStatsRecord> = _record.asStateFlow()

    /**
     * What the UI shows NOW against this repository's own clock: a bucket whose
     * stored period is over displays zero immediately, before the first blip
     * of the new period arrives.
     */
    fun snapshot(): BlipStats = BlipStatsLogic.snapshot(_record.value, PeriodKeys.of(clock))

    /**
     * The reward entitlement. Once true from a persisted read, it never flips
     * back: a transient read failure keeps the in-memory record instead of
     * overwriting a latched reward with an empty one.
     *
     * Published as an explicit [MutableStateFlow] — NOT derived from [_record]
     * — so [start] can initialize it in the same synchronous block that sets
     * [ready]: when the cold-start barrier opens, the entitlement value is
     * already the persisted one, deterministically, with no derived-flow
     * emission scheduled behind it.
     */
    private val _earnedPremium = MutableStateFlow(false)

    val earnedPremium: StateFlow<Boolean> = _earnedPremium.asStateFlow()

    /**
     * False until the persisted statistics have been read once. The cold-start
     * barrier waits for it, exactly like ownership and temporary access.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var persistJob: Job? = null

    /**
     * Performs the ONE initial disk read, sanitizes, publishes the in-memory
     * record and the earned state, and marks the repository ready. After this
     * returns, memory is the authority and later disk writes never flow back.
     */
    suspend fun start() {
        if (_ready.value) return
        val loaded = try {
            readRecord(dataStore.data.first())
        } catch (e: IOException) {
            // Unreadable/corrupt store degrades to defaults; audio and the
            // session must never see this failure.
            BlipStatsRecord()
        }
        _record.value = loaded
        // Publication order is the atomicity contract: record, then earned,
        // then ready — ready == true therefore implies the entitlement was
        // already initialized from the persisted record.
        _earnedPremium.value = loaded.earnedPremium
        _ready.value = true
    }

    /**
     * One real successful playback. In-memory only on the calling path; the
     * disk write is bounded-batched, or immediate when the 100K reward is
     * crossed. Must never throw into the audio path.
     */
    fun recordSuccessfulBlip() {
        val keys = PeriodKeys.of(clock)
        val before = _record.value
        val after = BlipStatsLogic.afterBlip(before, keys)
        _record.value = after
        // Publish the newly-earned access synchronously with the in-memory
        // transition — never waiting on persistence. The reward is
        // irreversible: it is only ever set true, never back to false.
        if (after.earnedPremium) _earnedPremium.value = true
        val crossed = !before.earnedPremium && after.earnedPremium
        schedulePersist(immediate = crossed)
    }

    /**
     * Forces a disk write of the current record — used on normal STOP / EXIT /
     * service teardown so the last blips of a session usually survive.
     */
    fun flush() {
        schedulePersist(immediate = true)
    }

    private fun schedulePersist(immediate: Boolean) {
        if (immediate) {
            persistJob?.cancel()
            persistJob = scope.launch { write() }
            return
        }
        // At most one bounded interval in flight: blips inside the window are
        // covered by the pending write, which always reads the newest record.
        if (persistJob?.isActive == true) return
        persistJob = scope.launch {
            kotlinx.coroutines.delay(PERSIST_INTERVAL_MS)
            write()
        }
    }

    private suspend fun write() {
        try {
            val snapshot = _record.value
            dataStore.edit { prefs ->
                prefs[DAY_KEY] = snapshot.dayKey
                prefs[TODAY_COUNT] = snapshot.todayCount
                prefs[WEEK_KEY] = snapshot.weekKey
                prefs[WEEK_COUNT] = snapshot.weekCount
                prefs[MONTH_KEY] = snapshot.monthKey
                prefs[MONTH_COUNT] = snapshot.monthCount
                prefs[TOTAL_COUNT] = snapshot.totalCount
                if (snapshot.earnedPremium) prefs[EARNED_PREMIUM] = true
            }
        } catch (_: IOException) {
            // Stats are secondary: a failed write must never surface as a
            // session error, never stop the scheduler and never stop audio.
        }
    }

    private fun readRecord(prefs: Preferences): BlipStatsRecord = BlipStatsLogic.sanitize(
        BlipStatsRecord(
            dayKey = prefs[DAY_KEY].orEmpty(),
            todayCount = prefs[TODAY_COUNT] ?: 0L,
            weekKey = prefs[WEEK_KEY].orEmpty(),
            weekCount = prefs[WEEK_COUNT] ?: 0L,
            monthKey = prefs[MONTH_KEY].orEmpty(),
            monthCount = prefs[MONTH_COUNT] ?: 0L,
            totalCount = prefs[TOTAL_COUNT] ?: 0L,
            earnedPremium = prefs[EARNED_PREMIUM] ?: false,
        ),
    )

    companion object {
        /** Bounded batching: at most one ordinary write per interval. */
        const val PERSIST_INTERVAL_MS = 1_000L

        private val DAY_KEY = stringPreferencesKey("day_key")
        private val TODAY_COUNT = longPreferencesKey("today_count")
        private val WEEK_KEY = stringPreferencesKey("week_key")
        private val WEEK_COUNT = longPreferencesKey("week_count")
        private val MONTH_KEY = stringPreferencesKey("month_key")
        private val MONTH_COUNT = longPreferencesKey("month_count")
        private val TOTAL_COUNT = longPreferencesKey("total_count")
        private val EARNED_PREMIUM = booleanPreferencesKey("earned_premium")

        fun fromContext(context: Context, scope: CoroutineScope): BlipStatsRepository =
            BlipStatsRepository(context.problipStatsDataStore, scope)
    }
}
