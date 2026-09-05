package com.vacster.problip.service

import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSignalTest {
    @Test
    fun onlySuccessfulPlaybackSignalsAndDoesNotPublishSessionState() = runTest {
        val before = ProblipSession.state.value
        var signals = 0
        backgroundScope.launch { ProblipSession.blips.collect { signals++ } }
        runCurrent()
        ProblipSession.reportPlayback(false)
        runCurrent()
        assertEquals(0, signals)
        ProblipSession.reportPlayback(true)
        runCurrent()
        assertEquals(1, signals)
        assertEquals(before, ProblipSession.state.value)
    }

    @Test
    fun returningToMainDoesNotReplayBackgroundPlayback() = runTest {
        ProblipSession.reportPlayback(true)
        var signals = 0
        val firstVisit = backgroundScope.launch { ProblipSession.blips.collect { signals++ } }
        runCurrent()
        assertEquals(0, signals)
        firstVisit.cancel()
        runCurrent()
        ProblipSession.reportPlayback(true)
        backgroundScope.launch { ProblipSession.blips.collect { signals++ } }
        runCurrent()
        assertEquals(0, signals)
        ProblipSession.reportPlayback(true)
        runCurrent()
        assertEquals(1, signals)
    }
}
