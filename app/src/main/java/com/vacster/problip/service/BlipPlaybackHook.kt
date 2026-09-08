package com.vacster.problip.service

import com.vacster.problip.stats.BlipStatsRepository

/** Anything that counts one successful blip; [BlipStatsRepository] is the real one. */
internal fun interface BlipCounter {
    fun recordSuccessfulBlip()
}

/**
 * The ONE successful-playback boundary: a real `audio.play()` that returned true
 * increments the lifetime statistics and reports the existing lamp/glow signal.
 *
 * A failed attempt, a scheduled attempt, START, glow, widget or notification
 * never pass through here — one real successful scheduler playback is one blip.
 *
 * The statistics side is deliberately best-effort: a stats failure must never
 * become a session ERROR, stop the scheduler or stop audio. Audio is primary.
 */
internal object BlipPlaybackHook {

    fun onPlaybackResult(succeeded: Boolean, stats: BlipCounter): Boolean {
        if (succeeded) {
            try {
                stats.recordSuccessfulBlip()
            } catch (_: RuntimeException) {
                // Stats are secondary.
            }
        }
        ProblipSession.reportPlayback(succeeded)
        return succeeded
    }
}

/** The production counter: the app-level statistics repository. */
internal fun BlipStatsRepository.asBlipCounter(): BlipCounter =
    BlipCounter { recordSuccessfulBlip() }
