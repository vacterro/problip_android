package com.vacster.problip.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Android-free SoundPool seam (PERF-004).
 *
 * android.media.SoundPool cannot run on the ordinary JVM, so its tiny
 * load-bearing surface is abstracted here: production wraps the real SoundPool
 * ([DefaultSoundEngine]), focused deterministic tests inject a fake. The seam is
 * deliberately minimal — anything more would be a private audio framework.
 */
internal interface SoundEngine {
    /**
     * Starts decoding [resId] and returns a positive native sample id, or 0 when
     * the request was rejected outright (no completion event will ever arrive).
     */
    fun load(resId: Int): Int

    /** Frees the native sample. Safe to call for unknown ids. */
    fun unload(sampleId: Int)

    /**
     * Starts playback; a non-zero return means the sample was usable. This is
     * also the only JVM-observable proof that a sample genuinely decoded.
     */
    fun play(sampleId: Int, gain: Float): Boolean

    fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit)

    /** Terminal: the engine handle is dead afterwards. */
    fun release()
}

/** Production wrapper around android.media.SoundPool. */
internal class DefaultSoundEngine(context: Context) : SoundEngine {

    private val appContext = context.applicationContext

    private var onLoadComplete: (sampleId: Int, status: Int) -> Unit = { _, _ -> }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            onLoadComplete(sampleId, status)
        }
    }

    override fun load(resId: Int): Int = soundPool.load(appContext, resId, PRIORITY)

    override fun unload(sampleId: Int) {
        soundPool.unload(sampleId)
    }

    override fun play(sampleId: Int, gain: Float): Boolean =
        soundPool.play(sampleId, gain, gain, PRIORITY, NO_LOOP, NORMAL_RATE) != 0

    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        onLoadComplete = listener
    }

    override fun release() = soundPool.release()

    private companion object {
        const val PRIORITY = 1
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1f
    }
}
