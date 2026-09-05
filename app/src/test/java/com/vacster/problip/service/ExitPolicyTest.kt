package com.vacster.problip.service

import com.vacster.problip.core.ProblipState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EXIT must never spawn a service just to stop it. STOPPED has no service,
 * ERROR has already released everything terminal; only STARTING and RUNNING
 * own a foreground service that still needs ACTION_STOP.
 */
class ExitPolicyTest {

    @Test
    fun stoppedExitsWithoutTouchingTheService() {
        assertFalse(ExitPolicy.shouldStopServiceBeforeExit(ProblipState.STOPPED))
    }

    @Test
    fun errorExitsWithoutTouchingTheService() {
        // Terminal resources are already released; starting a service for it
        // would create exactly the foreground session EXIT tears down.
        assertFalse(ExitPolicy.shouldStopServiceBeforeExit(ProblipState.ERROR))
    }

    @Test
    fun startingStopsTheServiceBeforeExit() {
        assertTrue(ExitPolicy.shouldStopServiceBeforeExit(ProblipState.STARTING))
    }

    @Test
    fun runningStopsTheServiceBeforeExit() {
        assertTrue(ExitPolicy.shouldStopServiceBeforeExit(ProblipState.RUNNING))
    }
}
