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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * granted is never acceptable), and [flushAndAwait] serves normal STOP/teardown.
 * A tiny loss window for the display buckets after an abrupt process kill is
 * an accepted trade.
 *
 * A failed or corrupt read never produces a session error, never stops the
 * scheduler and never stops audio: the flows degrade to a safe record. The
 * one entitlement fact, [earnedPremium], participates in the cold-start
 * barrier through [ready] so a persisted reward is honoured from the very
 * first blip instead of being read too late.
 *
 * Persistence coalescing invariant (CORE-001 fix):
 * A single serialized worker pumps the in-memory record to disk. Every in-memory
 * advancement sets [dirty] and ensures exactly one worker is running. The worker
 * loops: under a lock it checks [dirty]; if clear it retires itself and completes
 * any pending flush waiter atomically with that check, so a blip arriving between
 * the check and the retirement is observed by the very lock that relaunches the
 * worker — there is never a window where dirty data is left with no active or
 * scheduled worker. Each pass writes the LATEST record (newer than the one that
 * armed the pass), and any advancement made while a write is suspended re-arms
 * [dirty], so completion of an older write always leaves/triggers a trailing pass
 * for the newer revision. [flushAndAwait] waits only for the worker to retire
 * after covering the latest revision, or for the tolerated persistence I/O failure.
 *
 * Calendar authority (CORE-002 fix):
 * [snapshot] and [recordSuccessfulBlip] resolve the current period keys through
 * the injected [CalendarAuthority] every time. Production uses
 * [systemCalendarAuthority], which reads `ZoneId.systemDefault()` dynamically,
 * so a device timezone change while the process lives affects the next
 * statistics calculation immediately — no captured system-default clock, no
 * component recreation. A persisted bucket from the old local calendar simply
 * behaves like any other finished period: it reads zero until the first blip
 * of the new period starts it at one.
 */
class BlipStatsRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val calendarAuthority: CalendarAuthority = systemCalendarAuthority(),
) {

    /** Test constructor: a deterministic injected [Clock], e.g. a fixed one. */
    constructor(dataStore: DataStore<Preferences>, scope: CoroutineScope, clock: Clock) :
        this(dataStore, scope, ClockAuthority(clock))

    private val _record = MutableStateFlow(BlipStatsRecord())

    /**
     * The in-memory record — authoritative after [start] completes the one
     * initial load. Display code resolves it against the CURRENT [PeriodKeys]
     * (see [BlipStatsLogic.snapshot]) so a finished period reads zero; the
     * stored counts are what survived the last persist.
     */
    val record: StateFlow<BlipStatsRecord> = _record.asStateFlow()

    /**
     * What the UI shows NOW against the live calendar authority: a bucket whose
     * stored period is over displays zero immediately, before the first blip
     * of the new period arrives. A timezone change is honoured on the very
     * next call.
     */
    fun snapshot(): BlipStats = BlipStatsLogic.snapshot(_record.value, currentPeriodKeys())

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

    /**
     * Serialized persistence pump state. All reads/writes of these fields happen
     * under [lock] in non-suspending critical sections, so the worker-retirement
     * check and the blip-arrival re-arm are mutually exclusive with no window
     * where dirty data has no owner.
     *
     * [revision] advances with every in-memory record mutation; [coveredRevision]
     * is the newest revision a completed pass covered (a pass writes the LATEST
     * record, so it covers at least its captured target; a tolerated I/O failure
     * advances it too, so the final flush never hangs on a doomed write).
     * [flushAndAwait] waits only for its captured target to be covered — it never
     * chases blips recorded after the quiescence boundary.
     */
    private val lock = Any()
    private var workerJob: Job? = null
    private var dirty = false
    private var immediateRequested = false
    private var wakeSignal: CompletableDeferred<Unit>? = null
    private var revision = 0L
    private var coveredRevision = 0L
    private val flushWaiters = mutableListOf<FlushWaiter>()

    private class FlushWaiter(val target: Long, val deferred: CompletableDeferred<Unit>)

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
        val keys = currentPeriodKeys()
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
     * Fire-and-forget disk write of the current record — used on process death
     * (onDestroy) where no coroutine can await. For normal STOP/teardown use
     * [flushAndAwait].
     */
    fun flush() {
        schedulePersist(immediate = true)
    }

    /**
     * Awaitable final persistence for normal STOP / service teardown. Captures the
     * latest record revision known NOW (the call site must already have quiesced
     * the scheduler, so no new blips can arrive) and returns once the serialized
     * worker has covered that revision — a blip recorded before the capture is
     * guaranteed a trailing write first. Returns immediately when nothing is
     * pending. Never throws into the caller.
     */
    suspend fun flushAndAwait() {
        val waiter = synchronized(lock) {
            val target = revision
            val w = FlushWaiter(target, CompletableDeferred())
            if (target <= coveredRevision) {
                w.deferred.complete(Unit)
            } else {
                dirty = true
                immediateRequested = true
                wakeSignal?.complete(Unit)
                if (workerJob?.isActive != true) workerJob = scope.launch { persistencePump() }
                if (workerJob?.isActive == true) flushWaiters += w
                else w.deferred.complete(Unit)
            }
            w.deferred
        }
        waiter.await()
    }

    /**
     * Arms persistence: marks [dirty] (the revision tracker) and ensures exactly
     * one pump worker is running. [immediate] requests a write with no batching
     * delay and wakes a worker that is currently in its batch interval, so the
     * 100K reward and a normal flush are not held for a full interval.
     */
    private fun schedulePersist(immediate: Boolean) {
        synchronized(lock) {
            revision++
            dirty = true
            if (immediate) {
                immediateRequested = true
                wakeSignal?.complete(Unit)
            }
            if (workerJob?.isActive != true) workerJob = scope.launch { persistencePump() }
        }
    }

    /**
     * The serialized persistence worker. Loops while [dirty]; each pass captures
     * both the record snapshot and its corresponding revision, then writes it. If
     * a newer revision arrives while the write is suspended, [dirty] is re-armed
     * and the completed pass's target is below the new [revision], so the pump
     * performs a trailing pass instead of retiring — an old write can never
     * satisfy persistence for data it never captured. Worker retirement and the
     * [flushAndAwait] wake-up are decided in the same critical section that re-arms
     * on every blip, so retiring can never strand dirty data with no worker.
     */
    private suspend fun persistencePump() {
        try {
            while (true) {
                val wake = CompletableDeferred<Unit>()
                val immediate: Boolean
                val target: Long
                val snapshot: BlipStatsRecord
                synchronized(lock) {
                    if (!dirty) {
                        workerJob = null
                        completeAllFlushWaiters()
                        return
                    }
                    dirty = false
                    immediate = immediateRequested
                    immediateRequested = false
                    target = revision
                    snapshot = _record.value
                    wakeSignal = wake
                }
                val covered: Long
                try {
                    if (!immediate) {
                        withTimeoutOrNull(PERSIST_INTERVAL_MS) { wake.await() }
                    }
                    // Persist the snapshot captured with this pass's revision.
                    // A newer revision that arrived while the write was suspended
                    // re-armed [dirty], so the pump performs a trailing pass for
                    // it instead of retiring — an old write can never satisfy
                    // persistence for data it never captured.
                    write(snapshot)
                    covered = target
                } finally {
                    synchronized(lock) { if (wakeSignal === wake) wakeSignal = null }
                }
                synchronized(lock) {
                    if (covered >= coveredRevision) coveredRevision = covered
                    // Release waiters whose target revision has been covered.
                    releaseCoveredWaiters()
                    if (!dirty) {
                        workerJob = null
                        completeAllFlushWaiters()
                        return
                    }
                }
            }
        } catch (e: CancellationException) {
            synchronized(lock) {
                workerJob = null
                completeAllFlushWaiters()
            }
            throw e
        } catch (t: Throwable) {
            synchronized(lock) {
                workerJob = null
                completeAllFlushWaiters()
            }
        }
    }

    /** Completes (and drops) every waiter whose target revision is now covered. */
    private fun releaseCoveredWaiters() {
        val it = flushWaiters.iterator()
        while (it.hasNext()) {
            val w = it.next()
            if (w.target <= coveredRevision) {
                w.deferred.complete(Unit)
                it.remove()
            }
        }
    }

    /** Completes (and drops) every pending waiter — used whenever the pump retires. */
    private fun completeAllFlushWaiters() {
        while (flushWaiters.isNotEmpty()) {
            flushWaiters.removeAt(flushWaiters.lastIndex).deferred.complete(Unit)
        }
    }

    /**
     * Writes the snapshot captured for this pass to the persistence log. Only
     * IOException is tolerated here (stats are secondary); anything else is a
     * programmer error and escapes to the pump's backstop, which retires the
     * worker without hanging the teardown.
     */
    private suspend fun write(snapshot: BlipStatsRecord) {
        try {
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

    private fun currentPeriodKeys(): PeriodKeys = PeriodKeys.of(calendarAuthority.now())

    private class ClockAuthority(private val clock: Clock) : CalendarAuthority {
        override fun now(): CalendarNow = CalendarNow(clock.instant(), clock.zone)
    }

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
