package com.vacster.problip.service

import android.content.Context
import android.os.PowerManager
import com.vacster.problip.core.ProblipState

/**
 * Whether the session currently in [state] needs the CPU held awake.
 *
 * Kept pure and separate from the lock itself: PowerManager cannot be exercised
 * on the JVM, but the decision is the part that breaks — a lock held past a
 * terminal state drains the battery, a lock missing during RUNNING lets the
 * device suspend between blips.
 */
object WakeLockPolicy {

    /**
     * Only a RUNNING session qualifies. STARTING can wait indefinitely for audio
     * that never loads, and STOPPED/ERROR are terminal, so neither may keep the
     * CPU awake.
     */
    fun requiredFor(state: ProblipState): Boolean = state == ProblipState.RUNNING
}

/**
 * The single PARTIAL_WAKE_LOCK of the service lifecycle, owned by
 * [ProblipService] because the foreground service is the lifecycle authority.
 *
 * It exists because of a physical defect: with the screen off the device
 * suspends between blips, so a 5 s interval stretched to roughly 20 s. Coroutine
 * delays cannot survive CPU suspend on their own.
 *
 * Not reference counted, driven only through [apply], so repeated release
 * (explicit STOP, then teardown, then onDestroy) is harmless.
 */
class SessionWakeLock(context: Context) {

    private val lock: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
            ?.apply { setReferenceCounted(false) }

    /** Held state follows [required] exactly; both directions are idempotent. */
    fun apply(required: Boolean) {
        val lock = lock ?: return
        synchronized(this) {
            if (required) {
                if (!lock.isHeld) lock.acquire()
            } else if (lock.isHeld) {
                lock.release()
            }
        }
    }

    companion object {
        /** One stable tag; per-session tags make Play Vitals unreadable. */
        private const val TAG = "Problip:ActiveSession"
    }
}
