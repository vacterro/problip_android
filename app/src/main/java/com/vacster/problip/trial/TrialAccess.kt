package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.theme.ThemeCatalog

/**
 * Five-minute trials for unowned premium content. Pure and Android-free.
 *
 * A trial is one persisted wall-clock expiry per content id; access exists while
 * `now < expiry`, so the boundary itself is already expired. Trials are
 * deliberately repeatable — no counters, cooldowns, accounts or server checks —
 * but re-selecting content that is still inside its trial must never push the
 * expiry further out, otherwise repeated taps would be a permanent unlock.
 *
 * Not a security boundary: moving the device clock defeats it, which is an
 * acceptable trade for a 0.49 EUR sound. A purchase is what has no timer.
 */
object TrialAccess {

    const val DURATION_MS = 300_000L

    /** Premium features have no catalog entry, so they carry their own stable ids. */
    const val FEATURE_MANUAL_INTERVAL = "feature_manual_interval"
    const val FEATURE_PULSE_INTERVAL = "feature_pulse_interval"
    const val FEATURE_BLIP_GLOW = "feature_blip_glow"

    /** Trialable ids are exactly the premium catalog entries; free content needs no trial. */
    val SOUND_IDS: Set<String> = SoundCatalog.all.filterNot { it.free }.map { it.id }.toSet()
    val THEME_IDS: Set<String> = ThemeCatalog.all.filterNot { it.free }.map { it.id }.toSet()
    val FEATURE_IDS: Set<String> =
        setOf(FEATURE_MANUAL_INTERVAL, FEATURE_PULSE_INTERVAL, FEATURE_BLIP_GLOW)
    val ALL_IDS: Set<String> = SOUND_IDS + THEME_IDS + FEATURE_IDS

    fun isTrialable(contentId: String): Boolean = contentId in ALL_IDS

    /** Persisted trials are untrusted input: unknown ids and junk expiries are dropped. */
    fun sanitize(expiries: Map<String, Long>): Map<String, Long> =
        expiries.filterKeys { isTrialable(it) }.filterValues { it > 0L }

    fun activeIds(expiries: Map<String, Long>, nowMillis: Long): Set<String> =
        sanitize(expiries).filterValues { it > nowMillis }.keys

    /** Earliest expiry still in the future: the only one worth scheduling a wake-up for. */
    fun nextExpiryMillis(expiries: Map<String, Long>, nowMillis: Long): Long? =
        sanitize(expiries).values.filter { it > nowMillis }.minOrNull()

    /** Expired records grant nothing, so they may be dropped on the next write. */
    fun prune(expiries: Map<String, Long>, nowMillis: Long): Map<String, Long> =
        sanitize(expiries).filterValues { it > nowMillis }

    /**
     * The map to persist after the user selected [contentId]. Returns the current
     * map unchanged when the id is not trialable, when the content is already
     * [owned] (a purchase has no timer), and when a trial is still running — that
     * last rule is what keeps rapid repeated taps from extending anything.
     */
    fun startTrial(
        expiries: Map<String, Long>,
        contentId: String,
        nowMillis: Long,
        owned: Boolean,
    ): Map<String, Long> {
        val current = sanitize(expiries)
        if (!isTrialable(contentId) || owned) return current
        val stillRunning = (current[contentId] ?: 0L) > nowMillis
        if (stillRunning) return current
        return current + (contentId to nowMillis + DURATION_MS)
    }

    /** Whole seconds left, rounded up. Informational only. */
    fun remainingSeconds(expiryMillis: Long, nowMillis: Long): Long =
        ((expiryMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1000L

    /** "04:21" countdown label. Never used for an access decision. */
    fun formatRemaining(expiryMillis: Long, nowMillis: Long): String {
        val seconds = remainingSeconds(expiryMillis, nowMillis)
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}
