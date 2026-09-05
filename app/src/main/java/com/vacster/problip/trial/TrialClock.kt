package com.vacster.problip.trial

/**
 * Injected time so every expiry decision is deterministically testable and
 * `System.currentTimeMillis()` stays out of UI, catalog and service code.
 */
fun interface TrialClock {
    fun nowMillis(): Long
}

/** Production clock: wall-clock epoch millis, the same base as persisted expiries. */
object SystemTrialClock : TrialClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
