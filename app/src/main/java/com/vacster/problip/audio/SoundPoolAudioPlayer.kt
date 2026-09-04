package com.vacster.problip.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.vacster.problip.core.KotlinRandomSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger

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

    /** Sample id of the load currently awaited, for filtering stale events. */
    private val expectedSampleId = AtomicInteger(0)

    override suspend fun prepare(soundIds: Set<String>): Boolean {
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
        val resId = entry.resId
        val status: Int = suspendCancellableCoroutine { cont ->
            // Register the listener before load(): the completion event can be
            // dispatched from another thread as soon as load() returns.
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (sampleId != 0 && sampleId == expectedSampleId.get() && cont.isActive) {
                    cont.resumeWith(Result.success(status))
                }
            }
            val id = synchronized(lock) {
                if (released) 0 else soundPool.load(appContext, resId, PRIORITY)
            }
            if (id == 0) {
                // Rejected outright (or released underneath us): no event will come.
                cont.resumeWith(Result.success(LOAD_REJECTED))
                return@suspendCancellableCoroutine
            }
            expectedSampleId.set(id)
            synchronized(lock) { loaded[entry.id] = id }
        }
        return status == 0
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
            soundPool.release()
        }
    }

    companion object {
        private const val PRIORITY = 1
        private const val NO_LOOP = 0
        private const val NORMAL_RATE = 1f
        private const val LOAD_REJECTED = Int.MIN_VALUE
    }
}
