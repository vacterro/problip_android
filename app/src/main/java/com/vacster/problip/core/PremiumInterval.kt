package com.vacster.problip.core

/**
 * The one place a premium interval turns into timing the scheduler may run.
 *
 * Both premium modes — MANUAL and PULSE — ride the same Customization Pack
 * entitlement, and both arrive here as a plain boolean, so no entitlement logic
 * reaches [BlipScheduler]. A premium pick without access resolves to the free
 * preset, which is what makes an expiring trial fall back to random 4–7 without
 * restarting the service or creating a second scheduler: the running scheduler
 * simply gets a new interval.
 */
object PremiumInterval {

    fun effectiveConfig(
        mode: IntervalMode,
        fromSeconds: Int,
        toSeconds: Int,
        manualAccessible: Boolean,
        pulseAccessible: Boolean,
    ): IntervalConfig = when {
        mode == IntervalMode.MANUAL && manualAccessible -> ManualInterval.config(fromSeconds, toSeconds)
        mode == IntervalMode.PULSE && pulseAccessible -> IntervalConfig.Pulse()
        accessible(mode, manualAccessible, pulseAccessible) -> mode.toConfig()
        else -> IntervalConfig.DEFAULT
    }

    /** The mode the UI and the notification should show, after the access check. */
    fun effectiveMode(
        mode: IntervalMode,
        manualAccessible: Boolean,
        pulseAccessible: Boolean,
    ): IntervalMode =
        if (accessible(mode, manualAccessible, pulseAccessible)) mode else IntervalMode.RANDOM_4_7

    private fun accessible(
        mode: IntervalMode,
        manualAccessible: Boolean,
        pulseAccessible: Boolean,
    ): Boolean = when (mode) {
        IntervalMode.MANUAL -> manualAccessible
        IntervalMode.PULSE -> pulseAccessible
        else -> true
    }
}
