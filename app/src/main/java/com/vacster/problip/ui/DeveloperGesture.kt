package com.vacster.problip.ui

/**
 * The hidden Developer Access chord, as a pure state machine so the policy is
 * testable without a device: hold the PROBLIP title for twenty seconds and, with
 * that finger still down, tap the main START/STOP button.
 *
 * Time arrives as elapsed millis from a coroutine timer — nothing here sleeps and
 * no clock is read, so a test drives the exact 19-versus-20 second boundary
 * directly. Nothing is persisted either: rotation or process recreation drops the
 * whole gesture, which is intended.
 */
class DeveloperGesture(private val holdMillis: Long = HOLD_MILLIS) {

    /** True while the title finger is physically down. */
    var titleHeld: Boolean = false
        private set

    /** True once the hold qualified and while it is still unconsumed. */
    var armed: Boolean = false
        private set

    /** A fresh press always starts from disarmed: a stale arm may not carry over. */
    fun titlePressed() {
        titleHeld = true
        armed = false
    }

    /** Arms only when the finger is still down and the hold actually qualified. */
    fun titleHeldFor(elapsedMillis: Long) {
        if (titleHeld && elapsedMillis >= holdMillis) armed = true
    }

    /** Release and pointer cancellation are the same thing: the chord is dead. */
    fun titleReleased() {
        titleHeld = false
        armed = false
    }

    /**
     * The START/STOP tap. Consumes the arm and returns true exactly once per
     * qualifying hold; a second tap needs a new twenty-second hold. False means
     * the caller must run the normal start/stop action.
     */
    fun consumeIfArmedAndTitleStillHeld(): Boolean {
        if (!armed || !titleHeld) return false
        armed = false
        return true
    }

    companion object {
        const val HOLD_MILLIS = 20_000L
    }
}
