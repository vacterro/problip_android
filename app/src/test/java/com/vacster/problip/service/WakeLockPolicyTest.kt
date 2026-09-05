package com.vacster.problip.service

import com.vacster.problip.core.ProblipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wake-lock lifecycle policy: PowerManager itself is not JVM-testable, so these
 * tests drive the real [SessionLifecycle] wired exactly as [ProblipService] wires
 * it — every published state applies [WakeLockPolicy] to the lock — and assert
 * the eight cases that matter for a partial wake lock.
 */
private class FakeLock {
    var held = false
        private set
    var acquires = 0
        private set
    var releases = 0
        private set

    /** Same idempotent contract as SessionWakeLock.apply. */
    fun apply(required: Boolean) {
        if (required) {
            if (!held) {
                held = true
                acquires++
            }
        } else if (held) {
            held = false
            releases++
        }
    }
}

private class WakeLockHarness {
    val lock = FakeLock()
    val lifecycle = SessionLifecycle { state, _ ->
        lock.apply(WakeLockPolicy.requiredFor(state))
    }

    /** Drives a session all the way to RUNNING and returns its token. */
    fun runSession(): Int {
        val token = lifecycle.begin()
        assertTrue(lifecycle.report(token, ProblipState.RUNNING))
        return token
    }
}

class WakeLockPolicyTest {

    @Test
    fun stoppedSessionNeedsNoWakeLock() {
        val h = WakeLockHarness()
        assertFalse(WakeLockPolicy.requiredFor(ProblipState.STOPPED))
        assertFalse(h.lock.held)

        h.lifecycle.stop()
        assertFalse(h.lock.held)
        assertEquals(0, h.lock.acquires)
    }

    @Test
    fun successfulSessionStartRequiresTheWakeLock() {
        val h = WakeLockHarness()
        val token = h.lifecycle.begin()
        // STARTING must not hold it: audio may never load.
        assertFalse(h.lock.held)

        h.lifecycle.report(token, ProblipState.RUNNING)
        assertTrue(h.lock.held)
        assertEquals(1, h.lock.acquires)
    }

    @Test
    fun explicitStopReleasesTheWakeLock() {
        val h = WakeLockHarness()
        h.runSession()

        h.lifecycle.stop()
        assertFalse(h.lock.held)
        assertEquals(1, h.lock.releases)
    }

    @Test
    fun terminalErrorReleasesTheWakeLock() {
        val h = WakeLockHarness()
        val token = h.runSession()

        assertTrue(h.lifecycle.fail(token, "Playback failed"))
        assertFalse(h.lock.held)
        assertEquals(1, h.lock.releases)
    }

    @Test
    fun repeatedStopAndDestroyReleaseOnlyOnce() {
        val h = WakeLockHarness()
        h.runSession()

        h.lifecycle.stop()
        h.lifecycle.stop()
        h.lifecycle.destroy()
        assertFalse(h.lock.held)
        assertEquals(1, h.lock.releases)
    }

    @Test
    fun failedStartupNeverOwnsTheWakeLock() {
        val h = WakeLockHarness()
        val token = h.lifecycle.begin()

        // Audio failed before the scheduler ever reached RUNNING.
        assertTrue(h.lifecycle.fail(token, "Sound could not be loaded"))
        h.lifecycle.destroy()
        assertFalse(h.lock.held)
        assertEquals(0, h.lock.acquires)
    }

    @Test
    fun startAfterErrorAcquiresAgain() {
        val h = WakeLockHarness()
        val dead = h.runSession()
        assertTrue(h.lifecycle.fail(dead, "Playback failed"))
        assertFalse(h.lock.held)

        h.runSession()
        assertTrue(h.lock.held)
        assertEquals(2, h.lock.acquires)
    }

    @Test
    fun staleCleanupCannotReleaseTheCurrentWakeLock() {
        val h = WakeLockHarness()
        val stale = h.runSession()
        h.lifecycle.stop()

        // Session B is the live one; the late failure of A arrives afterwards.
        h.runSession()
        assertFalse(h.lifecycle.fail(stale, "Playback failed"))
        assertFalse(h.lifecycle.report(stale, ProblipState.STOPPED))

        assertTrue(h.lock.held)
        assertEquals(1, h.lock.releases)
    }
}
