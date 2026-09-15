package com.vacster.problip.audio

import android.content.Context
import com.vacster.problip.core.KotlinRandomSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SoundPool engine for short blips. One instance owns exactly one SoundPool;
 * release() tears it down. Volume is applied per play (blips are ~100 ms).
 * Each play picks one sound uniformly from the active random pool.
 *
 * Android routes output normally (speaker/Bluetooth/headphones); no audio focus
 * is requested — the product intent is to blip over existing media.
 *
 * Resource ownership model (PERF-004 + the W12 delta contract):
 * - [loaded] maps a logical sound id to exactly ONE committed native sample.
 *   A superseded mapping is unloaded before its replacement is committed, so a
 *   logical id never owns multiple live physical samples.
 * - An in-flight physical load lives in [inFlight] and is SHARED: overlapping
 *   preparations requiring the same missing sound issue one engine.load() and
 *   await the same completion behind that per-resource boundary — never a
 *   global prepare mutex (which would recreate the PERF-004 serialization).
 * - Only delta.toLoad (sounds neither committed nor in flight) creates load
 *   calls; unchanged samples are reused.
 * - activePool changes ONLY in [activate], which requires every wanted id to
 *   have a usable committed sample — NOT that loaded keys equal the wanted set
 *   — so shrinking a pool works while obsolete samples are still loaded, and
 *   they are unloaded only at the accepted activation boundary.
 * - One preparation deadline bounds the whole concurrent wait: N missing
 *   sounds cost approximately the slowest load (or the deadline), never the
 *   sum of N sequential per-load timeouts.
 *
 * Thread confinement: loadFor()/activate() run on the SessionPlanAuthority
 * worker while play() runs on the scheduler loop and release() on the main
 * thread; every access to the tables, the active pool and the release flag is
 * guarded by [lock]. loadFor() does NOT hold [lock] across its suspending
 * waits, so concurrent preparations (and plays) interleave freely.
 */
class SoundPoolAudioPlayer internal constructor(
    context: Context?,
    engine: SoundEngine?,
    prepareTimeoutMs: Long,
) : AudioPlayer {

    private val engine: SoundEngine = engine ?: DefaultSoundEngine(requireNotNull(context))

    /** Operation-level preparation deadline (see [PREPARE_TIMEOUT_MS]); test-injectable. */
    private val prepareTimeoutMs: Long = prepareTimeoutMs

    /** Production constructor. */
    constructor(context: Context) : this(context, engine = null, prepareTimeoutMs = PREPARE_TIMEOUT_MS)

    private val picker = RandomPool(KotlinRandomSource)

    private val lock = Any()

    /** logical sound id -> committed native sample id. Guarded by [lock]. */
    private val loaded = HashMap<String, Int>()

    /** sound id -> shared in-flight load. Guarded by [lock]. */
    private val inFlight = HashMap<String, InFlightLoad>()

    /**
     * Load statuses that arrived before their record existed, keyed by sample
     * id. Guarded by [lock].
     *
     * SoundPool can dispatch the completion event from its own thread before
     * load() has even returned the sample id, so an event with no record yet is
     * buffered here and attached to the record the moment it is created.
     */
    private val completed = HashMap<Int, Int>()

    /** Sounds eligible for the next pick; subset of [loaded]'s keys. Guarded by [lock]. */
    private var activePool: List<String> = emptyList()

    /** Guarded by [lock]: after release the engine handle is dead, not reusable. */
    private var released = false

    @Volatile
    private var gain = Volume.DEFAULT_GAIN

    /**
     * One shared physical load for a logical sound id, with its consumer count.
     * The deferred buffers early completion events; a late callback after the
     * record is gone finds nothing and is ignored. Guarded by [lock].
     */
    private class InFlightLoad(val sampleId: Int) {
        val completion = CompletableDeferred<Int>()
        var consumers = 0
    }

    /** One awaited resource of a preparation; [isNew] marks engine loads this call created. */
    private class Need(val id: String, val record: InFlightLoad, val isNew: Boolean)

    init {
        this.engine.setOnLoadCompleteListener { sampleId, status ->
            synchronized(lock) {
                // Only a live in-flight record can accept a completion. After
                // release, failure cleanup or a superseded mapping the record
                // is gone and the late event must not resurrect bookkeeping.
                if (released) return@synchronized
                val record = inFlight.values.firstOrNull { it.sampleId == sampleId }
                if (record != null) record.completion.complete(status)
                else completed[sampleId] = status
            }
        }
    }

    /**
     * Load-then-activate convenience. Implemented over the same lock-free
     * primitives as loadFor()+activate(); it never takes a separate prepare
     * mutex, so there is no non-reentrant nested-Mutex self-deadlock.
     */
    override suspend fun prepare(soundIds: Set<String>): Boolean {
        if (!loadFor(soundIds)) return false
        return activate(soundIds)
    }

    /**
     * Ensures every wanted sound has a usable committed sample, WITHOUT
     * touching activePool — a stale plan may preload safely but must never
     * activate itself (W2-002). Delta-based against committed + shared
     * in-flight resources:
     *
     * 1. unknown ids fail immediately, before any physical load;
     * 2. delta = PoolDelta.between(reusable ids, wanted) — only delta.toLoad
     *    issues new engine.load() calls, and ALL of them are issued up front;
     * 3. missing sounds already in flight are joined, never re-loaded;
     * 4. the joins share one preparation deadline;
     * 5. each success is committed under [lock] atomically with the unload of
     *    the superseded sample — no duplicate physical sample per id;
     * 6. failure/timeout/cancellation unloads samples this preparation created
     *    that nobody committed and nobody else still awaits; the previously
     *    active pool is never touched.
     */
    override suspend fun loadFor(soundIds: Set<String>): Boolean {
        val entries = soundIds.mapNotNull { SoundCatalog.byId(it) }
        if (entries.size != soundIds.size) return false // unknown id: refuse, never crash

        // Stage 1 (synchronous): resolve the delta and issue every missing
        // engine.load() now — no waiting between independent loads.
        val needs = ArrayList<Need>()
        synchronized(lock) {
            if (released) return false
            val reusable = loaded.keys + inFlight.keys
            val delta = PoolDelta.between(reusable, entries.map { it.id }.toSet())
            for (entry in entries) {
                val id = entry.id
                val existing = inFlight[id]
                if (existing != null) {
                    existing.consumers += 1 // dedup: join the shared load
                    needs += Need(id, existing, isNew = false)
                } else if (id in delta.toLoad) {
                    val sampleId = engine.load(entry.resId)
                    if (sampleId == 0) {
                        // Rejected outright (no event will ever come): unwind
                        // what THIS call created or joined, nothing else.
                        abandon(needs)
                        return false
                    }
                    val record = InFlightLoad(sampleId)
                    completed.remove(sampleId)?.let { record.completion.complete(it) }
                    inFlight[id] = record
                    record.consumers += 1
                    needs += Need(id, record, isNew = true)
                }
            }
        }

        // Stage 2 (suspending): await all shared completions under ONE deadline.
        // Caller cancellation stays cancellation (after detaching this
        // consumer); a timeout becomes a bounded ordinary failure.
        val statuses: List<Int>? = try {
            withTimeoutOrNull(prepareTimeoutMs) {
                coroutineScope {
                    needs.map { need -> async { need.record.completion.await() } }.awaitAll()
                }
            }
        } catch (e: CancellationException) {
            detach(needs)
            throw e
        }

        // Stage 3 (synchronous): commit successes, clean this preparation's
        // failures. All consumers run the same commit, so a shared record is
        // committed idempotently; committed records leave [inFlight] and are
        // owned by [loaded] from then on.
        var allUsable = statuses != null && statuses.all { it == LOAD_SUCCESS }
        val committed = HashSet<Need>()
        synchronized(lock) {
            if (statuses != null) {
                for ((need, status) in needs.zip(statuses)) {
                    if (status != LOAD_SUCCESS) {
                        allUsable = false
                        continue
                    }
                    if (released) { // released between await and commit
                        allUsable = false
                        continue
                    }
                    val previous = loaded.put(need.id, need.record.sampleId)
                    if (previous != null && previous != need.record.sampleId) {
                        unloadOutsideLock(previous) // superseded mapping, no leak
                    }
                    if (inFlight[need.id] === need.record) inFlight.remove(need.id)
                    completed.remove(need.record.sampleId)
                    committed += need
                }
            } else {
                allUsable = false
            }
        }
        // Detach and clean: a record nobody committed and nobody else awaits
        // is dropped from the bookkeeping and its physical sample is freed.
        abandon(needs, committed)
        return allUsable
    }

    /**
     * Accepted-plan activation: [wanted] becomes the active random pool if and
     * only if every wanted id has a usable committed sample — obsolete extra
     * loaded samples do NOT block activation (an exact loaded==wanted
     * prerequisite used to make shrinking a pool fail); they are unloaded here,
     * at the accepted boundary, and never before it. Unchanged samples are
     * reused; activePool changes atomically.
     */
    override fun activate(soundIds: Set<String>): Boolean {
        val wanted = soundIds.mapNotNull { SoundCatalog.byId(it)?.id }.toSet()
        if (wanted.size != soundIds.size) return false // unknown id: refuse
        val unloadSampleIds = ArrayList<Int>()
        val result = synchronized(lock) {
            if (released) return false
            // "every wanted id has one usable loaded sample" — NOT loaded==wanted.
            if (!loaded.keys.containsAll(wanted)) return false
            activePool = wanted.toList()
            val delta = PoolDelta.between(loaded.keys.toSet(), wanted)
            for (id in delta.toUnload) {
                loaded.remove(id)?.let { unloadSampleIds += it }
            }
            true
        }
        // Physical unloads happen outside the monitor so the scheduler's play()
        // path is never blocked by engine calls.
        for (sampleId in unloadSampleIds) unloadOutsideLock(sampleId)
        return result
    }

    override fun play(): Boolean {
        synchronized(lock) {
            if (released) return false
            val soundId = picker.next(activePool) ?: return false
            val sampleId = loaded[soundId] ?: return false
            return engine.play(sampleId, gain)
        }
    }

    override fun setVolume(gain: Float) {
        this.gain = gain.coerceIn(0f, 1f)
    }

    /** Test-only observation seams (JVM tests, same package). */
    internal fun loadedKeysForTest(): Set<String> = synchronized(lock) { loaded.keys.toSet() }

    internal fun activePoolForTest(): Set<String> = synchronized(lock) { activePool.toSet() }

    /**
     * Terminal teardown. After release: loadFor/activate/play fail, every
     * suspended waiter completes promptly (release never relies on the
     * preparation deadline), late completion callbacks find no live record and
     * are ignored, and no bookkeeping survives.
     */
    override fun release() {
        val inFlightCopy: List<InFlightLoad>
        synchronized(lock) {
            if (released) return
            released = true
            loaded.clear()
            activePool = emptyList()
            inFlightCopy = inFlight.values.toList()
            inFlight.clear()
            completed.clear()
        }
        // Wake every waiter immediately with a non-success status so a released
        // preparation fails instead of pretending its samples became usable.
        for (record in inFlightCopy) record.completion.complete(RELEASED_STATUS)
        engine.release()
    }

    /**
     * Drops this preparation's consumer seat from every need; a record whose
     * last consumer leaves without being committed is removed from the
     * bookkeeping and its physical sample is freed (when the engine is alive).
     * Needs in [committed] are owned by [loaded] and never freed here.
     */
    private fun abandon(needs: List<Need>, committed: Set<Need> = emptySet()) {
        val toUnload = ArrayList<Int>()
        synchronized(lock) {
            for (need in needs) {
                if (need in committed) continue
                need.record.consumers -= 1
                if (need.record.consumers == 0 && inFlight[need.id] === need.record) {
                    inFlight.remove(need.id, need.record)
                    completed.remove(need.record.sampleId)
                    // A committed mapping owns the sample; everything else is
                    // an orphan of an abandoned attempt.
                    if (loaded[need.id] != need.record.sampleId) {
                        toUnload += need.record.sampleId
                    }
                }
            }
        }
        for (sampleId in toUnload) unloadOutsideLock(sampleId)
    }

    /** Detach-only variant for caller cancellation: no failure claim, no unload of shared work. */
    private fun detach(needs: List<Need>) = abandon(needs)

    /** Engine unload outside the monitor; skipped after release (handle is dead). */
    private fun unloadOutsideLock(sampleId: Int) {
        val alive = synchronized(lock) { !released }
        if (alive) engine.unload(sampleId)
    }

    internal companion object {
        const val LOAD_SUCCESS = 0

        /**
         * One operation-level preparation deadline. Independent missing loads
         * are all issued immediately and their joins share this single bounded
         * window, so worst-case preparation latency is one deadline — never
         * N sequential per-load timeouts (PERF-004).
         */
        internal const val PREPARE_TIMEOUT_MS = 5_000L

        /**
         * Completion status fed to waiters when the engine is released
         * mid-load: deliberately not LOAD_SUCCESS.
         */
        const val RELEASED_STATUS = -1
    }
}
