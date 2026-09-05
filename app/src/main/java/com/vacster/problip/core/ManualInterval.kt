package com.vacster.problip.core

/**
 * Premium Manual Interval: the user's own FROM/TO seconds instead of a preset.
 * Pure and Android-free, like the rest of core — access is decided elsewhere and
 * arrives here as a plain boolean, so no entitlement logic reaches the scheduler.
 *
 * Invalid input never becomes an [IntervalConfig]: values are clamped into
 * 1..3600 and ordered, so FROM > TO cannot reach [BlipScheduler] at all.
 */
object ManualInterval {

    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 3600
    const val DEFAULT_FROM_SECONDS = 4
    const val DEFAULT_TO_SECONDS = 7

    /**
     * The pair that is safe to persist and to schedule: both values clamped into
     * range, then ordered. Ordering rather than rejecting keeps a mid-edit
     * "FROM 10, TO 7" from throwing the user's numbers away.
     */
    fun sanitize(fromSeconds: Int, toSeconds: Int): Pair<Int, Int> {
        val from = fromSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        val to = toSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        return minOf(from, to) to maxOf(from, to)
    }

    /** Equal bounds behave as a fixed interval; otherwise uniform random. */
    fun config(fromSeconds: Int, toSeconds: Int): IntervalConfig {
        val (from, to) = sanitize(fromSeconds, toSeconds)
        return if (from == to) {
            IntervalConfig.Fixed(from * 1_000L)
        } else {
            IntervalConfig.Random(from * 1_000L, to * 1_000L)
        }
    }
}
