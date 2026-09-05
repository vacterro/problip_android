package com.vacster.problip.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.vacster.problip.core.KotlinRandomSource
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SoundPool engine for short blips. One instance owns exactly one SoundPool;
 * release() tears it down. Volume is applied per play (blips are ~100 ms).
 * Each play picks one sound uniformly from the active random pool.
 *
 * Android routes output normally (speaker/Bluetooth/headphones); no audio focus
 * is requested — the product intent is to blip over existing media.
 *
 * Thread confinement: prepare() runs on a service coroutine while play() runs on
 * the scheduler loop and release() on the main thread, so every access to the
 * sample table, the active pool and the SoundPool handle itself goes through
 * [lock]. That also closes the play-after-release window, which is a native
 * crash rather than an exception.
 */
class SoundPoolAudioPlayer(context: Context) : AudioPlayer {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val picker = RandomPool(KotlinRandomSource)

    private val lock = Any()

    /** sound id -> loaded sample id. Guarded by [lock]. */
    private val loaded = HashMap<String, Int>()

    /** Sounds eligible for the next pick; subset of [loaded]'s keys. Guarded by [lock]. */
    private var activePool: List<String> = emptyList()

    /** Guarded by [lock]: after release the SoundPool handle is dead, not reusable. */
    private var released = false

    @Volatile
    private var gain = Volume.DEFAULT_GAIN

    /**
     * Load statuses that arrived before their waiter existed, and the waiters
     * themselves, keyed by sample id. Guarded by [lock].
     *
     * SoundPool can dispatch the completion event from its own thread before
     * load() has even returned the sample id, so a single "expected id" field
     * loses the event and leaves the loading coroutine suspended forever. Every
     * load now has its own slot, and an early status is buffered until its
     * waiter shows up.
     */
    private val completed = HashMap<Int, Int>()
    private val waiters = HashMap<Int, CancellableContinuation<Int>>()

    /** One pool switch at a time: concurrent prepares double-load the same sample. */
    private val prepareLock = Mutex()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val waiter = synchronized(lock) {
                waiters.remove(sampleId) ?: run {
                    completed[sampleId] = status
                    null
                }
            }
            waiter?.resumeWith(Result.success(status))
        }
    }

    override suspend fun prepare(soundIds: Set<String>): Boolean = prepareLock.withLock {
        val entries = soundIds.mapNotNull { SoundCatalog.byId(it) }
        if (entries.size != soundIds.size) return false // unknown id: refuse, never crash
        val wanted = entries.map { it.id }
        val delta = synchronized(lock) {
            if (released) return false
            PoolDelta.between(loaded.keys.toSet(), wanted.toSet())
        }
        // Load first: a failed switch must not drop samples that still play.
        for (entry in entries.filter { it.id in delta.toLoad }) {
            if (!load(entry)) return false
        }
        synchronized(lock) {
            if (released) return false
            for (id in delta.toUnload) {
                loaded.remove(id)?.let { soundPool.unload(it) }
            }
            activePool = wanted
        }
        return true
    }

    private suspend fun load(entry: SoundEntry): Boolean {
        val sampleId = synchronized(lock) {
            if (released) 0 else soundPool.load(appContext, entry.resId, PRIORITY)
        }
        // Rejected outright (or released underneath us): no event will ever come.
        if (sampleId == 0) return false
        // A load that never completes must not hang startup forever; the session
        // reports a sound failure instead of staying in STARTING.
        val status = withTimeoutOrNull(LOAD_TIMEOUT_MS) { awaitLoad(sampleId) }
        if (status != LOAD_SUCCESS) {
            // Never keep a half-loaded sample: the next prepare() would treat the
            // sound as ready, and play() would hand SoundPool a dead sample id.
            synchronized(lock) {
                waiters.remove(sampleId)
                completed.remove(sampleId)
                if (!released) soundPool.unload(sampleId)
            }
            return false
        }
        synchronized(lock) {
            if (released) return false
            loaded[entry.id] = sampleId
        }
        return true
    }

    private suspend fun awaitLoad(sampleId: Int): Int = suspendCancellableCoroutine { cont ->
        val early = synchronized(lock) {
            completed.remove(sampleId) ?: run {
                waiters[sampleId] = cont
                null
            }
        }
        if (early != null) cont.resumeWith(Result.success(early))
        cont.invokeOnCancellation { synchronized(lock) { waiters.remove(sampleId) } }
    }

    override fun play(): Boolean {
        synchronized(lock) {
            if (released) return false
            val soundId = picker.next(activePool) ?: return false
            val sampleId = loaded[soundId] ?: return false
            return soundPool.play(sampleId, gain, gain, PRIORITY, NO_LOOP, NORMAL_RATE) != 0
        }
    }

    override fun setVolume(gain: Float) {
        this.gain = gain.coerceIn(0f, 1f)
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            loaded.clear()
            activePool = emptyList()
            completed.clear()
            soundPool.release()
        }
    }

    companion object {
        private const val PRIORITY = 1
        private const val NO_LOOP = 0
        private const val NORMAL_RATE = 1f
        private const val LOAD_SUCCESS = 0
        private const val LOAD_TIMEOUT_MS = 5_000L
    }
}
