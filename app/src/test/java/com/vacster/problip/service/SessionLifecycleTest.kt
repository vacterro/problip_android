package com.vacster.problip.service

import com.vacster.problip.core.ProblipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the two W12.1 defects: an ERROR erased by the teardown it
 * triggered, and a failed session that stayed installed so START became a no-op.
 */
private class RecordingPublisher {
    val published = mutableListOf<Pair<ProblipState, String?>>()
    val lifecycle = SessionLifecycle { state, error -> published += state to error }

    val state: ProblipState? get() = published.lastOrNull()?.first
    val error: String? get() = published.lastOrNull()?.second
}

class SessionLifecycleTest {

    @Test
    fun beginPublishesStartingAndHandsOutUsableTokens() {
        val p = RecordingPublisher()
        val first = p.lifecycle.begin()
        assertEquals(ProblipState.STARTING, p.state)
        assertNull(p.error)
        assertTrue(first != SessionLifecycle.NO_SESSION)
        p.lifecycle.stop()
        assertTrue(p.lifecycle.begin() != first)
    }

    @Test
    fun initialPrepareFailureSurvivesTeardownAndDestroy() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()

        // Teardown order in the service: fail() first, then release, then the
        // stopSelf() that ends in onDestroy.
        assertTrue(p.lifecycle.fail(token, "Sound could not be loaded"))
        p.lifecycle.destroy()

        assertEquals(ProblipState.ERROR, p.state)
        assertEquals("Sound could not be loaded", p.error)
        assertTrue(p.lifecycle.failed)
    }

    @Test
    fun runtimePlaybackFailureIsTerminalAndKeepsItsMessage() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()
        p.lifecycle.report(token, ProblipState.RUNNING)

        assertTrue(p.lifecycle.fail(token, "Playback failed"))
        // Late scheduler traffic from the dying loop must not repaint the UI.
        assertFalse(p.lifecycle.report(token, ProblipState.RUNNING))
        assertFalse(p.lifecycle.fail(token, "Playback failed"))

        assertEquals(ProblipState.ERROR, p.state)
        assertEquals("Playback failed", p.error)
    }

    @Test
    fun startAfterErrorOpensAFreshSession() {
        val p = RecordingPublisher()
        val dead = p.lifecycle.begin()
        p.lifecycle.fail(dead, "Playback failed")

        val fresh = p.lifecycle.begin()
        assertTrue(fresh != dead)
        assertEquals(ProblipState.STARTING, p.state)
        assertNull(p.error)
        assertFalse(p.lifecycle.failed)

        assertTrue(p.lifecycle.report(fresh, ProblipState.RUNNING))
        assertEquals(ProblipState.RUNNING, p.state)
    }

    @Test
    fun userStopClearsAPreservedFailure() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()
        p.lifecycle.fail(token, "Sound could not be loaded")

        p.lifecycle.stop()

        assertEquals(ProblipState.STOPPED, p.state)
        assertNull(p.error)
        assertFalse(p.lifecycle.failed)
    }

    @Test
    fun staleFailureNeverTouchesTheSessionTheUserJustStarted() {
        val p = RecordingPublisher()
        val old = p.lifecycle.begin()
        p.lifecycle.stop()
        val fresh = p.lifecycle.begin()
        p.lifecycle.report(fresh, ProblipState.RUNNING)

        // The failure detected on Dispatchers.Default arrives after the restart.
        assertFalse(p.lifecycle.fail(old, "Playback failed"))

        assertEquals(ProblipState.RUNNING, p.state)
        assertNull(p.error)
        assertFalse(p.lifecycle.failed)
    }

    @Test
    fun staleReportCannotResurrectAStoppedSession() {
        val p = RecordingPublisher()
        val old = p.lifecycle.begin()
        p.lifecycle.stop()

        assertFalse(p.lifecycle.report(old, ProblipState.RUNNING))
        assertEquals(ProblipState.STOPPED, p.state)
    }

    @Test
    fun replayedSchedulerStoppedDoesNotIdleAStartingSession() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()

        // The collector subscribes before start(), so the StateFlow replays STOPPED.
        assertFalse(p.lifecycle.report(token, ProblipState.STOPPED))
        assertEquals(ProblipState.STARTING, p.state)
    }

    @Test
    fun errorIsNeverPublishedThroughReport() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()

        assertFalse(p.lifecycle.report(token, ProblipState.ERROR))
        assertEquals(ProblipState.STARTING, p.state)
        assertFalse(p.lifecycle.failed)
    }

    @Test
    fun destroyWithoutAFailureStops() {
        val p = RecordingPublisher()
        val token = p.lifecycle.begin()
        p.lifecycle.report(token, ProblipState.RUNNING)

        p.lifecycle.destroy()

        assertEquals(ProblipState.STOPPED, p.state)
        assertNull(p.error)
    }
}
