package com.vacster.problip.service

import com.vacster.problip.core.ProblipState

/**
 * Session state machine, deliberately Android-free so the ERROR-versus-teardown
 * ordering is unit-testable without a device.
 *
 * Generations exist because teardown is asynchronous. A failure is detected on a
 * background dispatcher, publishes ERROR, and then triggers the teardown that
 * releases the engine — and that teardown published STOPPED, erasing the reason
 * the session died before the UI ever showed it. A terminal event now retires its
 * generation, so everything still holding the old token is ignored: late
 * scheduler states, the teardown itself, and onDestroy after a failure.
 */
class SessionLifecycle(private val publish: (ProblipState, String?) -> Unit) {

    private var generation = NO_SESSION
    private var failure: String? = null

    /** True while the last session ended in a failure that must stay visible. */
    val failed: Boolean get() = failure != null

    /** Claims the token a starting session must present on every later call. */
    fun begin(): Int {
        generation++
        failure = null
        publish(ProblipState.STARTING, null)
        return generation
    }

    /**
     * Publishes a live scheduler state. Returns false for a stale token, and for
     * STOPPED/ERROR: those are terminal and only [stop], [fail] and [destroy] may
     * publish them, so a StateFlow replaying its initial STOPPED can no longer
     * knock a starting session back to idle.
     */
    fun report(token: Int, state: ProblipState): Boolean {
        if (token != generation) return false
        if (state != ProblipState.STARTING && state != ProblipState.RUNNING) return false
        publish(state, null)
        return true
    }

    /**
     * Terminal failure. Returns false for a stale token, which is the caller's
     * signal to leave the current session alone instead of tearing down a session
     * the user has already restarted.
     */
    fun fail(token: Int, message: String): Boolean {
        if (token != generation) return false
        failure = message
        generation++
        publish(ProblipState.ERROR, message)
        return true
    }

    /** User STOP: terminal for whatever runs, and clears any failure. */
    fun stop() {
        generation++
        failure = null
        publish(ProblipState.STOPPED, null)
    }

    /** System destroy: keeps a preserved failure visible, otherwise stops. */
    fun destroy() {
        if (failure != null) return
        stop()
    }

    companion object {
        /** Token no session ever owns; [begin] hands out 1 first. */
        const val NO_SESSION = 0
    }
}
