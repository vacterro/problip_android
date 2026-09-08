package com.vacster.problip.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BlipPlaybackHook is the single successful-playback boundary. It must
 * only increment statistics and fire the lamp signal on a real succeeded
 * playback — a failed attempt changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlipPlaybackHookTest {

    @Test
    fun failedPlaybackIncrementsNothingAndReportsFalse() = runTest {
        var counted = false
        val counter = BlipCounter {
            counted = true
            org.junit.Assert.fail("recordSuccessfulBlip must not be called for failed playback")
        }
        val result = BlipPlaybackHook.onPlaybackResult(false, counter)
        runCurrent()
        assertFalse(result)
        assertFalse(counted)
    }

    @Test
    fun successfulPlaybackIncrementsStatsAndReportsTrue() = runTest {
        var counted = 0
        val counter = BlipCounter { counted++ }
        val result = BlipPlaybackHook.onPlaybackResult(true, counter)
        runCurrent()
        assertTrue(result)
        assertEquals(1, counted)
    }

    @Test
    fun statsFailureDoesNotPreventTheTrueResult() = runTest {
        val counter = BlipCounter { throw RuntimeException("disk full") }
        val result = BlipPlaybackHook.onPlaybackResult(true, counter)
        runCurrent()
        assertTrue(result)
    }
}
