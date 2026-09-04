package com.vacster.problip.audio

import com.vacster.problip.core.BlipScheduler
import com.vacster.problip.core.DelayBoundary
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.ProblipState
import com.vacster.problip.core.RandomSource
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the AudioPlayer interface as the scheduler sees it:
 * an unusable engine must surface as ERROR, and volume must never disturb
 * the single scheduling loop.
 */
private class FakeAudioPlayer : AudioPlayer {
    var prepared = false
    var preparedIds: Set<String>? = null
    var failNextPlay = false
    var plays = 0
    var lastGain = -1f
    var released = false

    override suspend fun prepare(soundIds: Set<String>): Boolean {
        prepared = soundIds.isNotEmpty()
        preparedIds = soundIds
        return prepared
    }

    override fun play(): Boolean {
        if (!prepared || failNextPlay) return false
        plays++
        return true
    }

    override fun setVolume(gain: Float) {
        lastGain = gain
    }

    override fun release() {
        released = true
    }
}

private class RecordingDelay : DelayBoundary {
    val requested = mutableListOf<Long>()
    override suspend fun delay(ms: Long) {
        requested += ms
        kotlinx.coroutines.delay(ms)
    }
}

private class ScriptedRandom(vararg script: Long) : RandomSource {
    private val values = script
    private var i = 0
    override fun nextLong(fromInclusive: Long, toInclusive: Long): Long = values[i++ % values.size]
}

class AudioWiringTest {

    @Test
    fun unpreparedEngineTurnsStartIntoErrorNeverRunning() = runTest {
        val audio = FakeAudioPlayer() // prepared == false
        val scheduler = BlipScheduler(backgroundScope, audio, RecordingDelay(), ScriptedRandom(-1),
            IntervalMode.FIXED_5S.toConfig())
        scheduler.start()
        runCurrent()
        // The 500 ms initial delay is STARTING, not RUNNING: audio not yet usable.
        assertEquals(ProblipState.STARTING, scheduler.state.value)
        advanceTimeBy(502)
        assertEquals(ProblipState.ERROR, scheduler.state.value)
        assertEquals(0, audio.plays)
        assertEquals("Playback failed", scheduler.error.value)
        scheduler.stop()
    }

    @Test
    fun playFailureMidSessionEntersError() = runTest {
        val audio = FakeAudioPlayer().apply { prepared = true }
        val scheduler = BlipScheduler(backgroundScope, audio, RecordingDelay(), ScriptedRandom(-1),
            IntervalMode.FIXED_5S.toConfig())
        scheduler.start()
        runCurrent()
        advanceTimeBy(5_501)
        assertEquals(2, audio.plays)
        assertEquals(ProblipState.RUNNING, scheduler.state.value)

        audio.failNextPlay = true
        advanceTimeBy(5_000)
        assertEquals(ProblipState.ERROR, scheduler.state.value)
        assertEquals(2, audio.plays)
        scheduler.stop()
    }

    @Test
    fun volumeChangeWhileRunningKeepsSingleLoop() = runTest {
        val audio = FakeAudioPlayer().apply { prepared = true }
        val delays = RecordingDelay()
        val scheduler = BlipScheduler(backgroundScope, audio, delays, ScriptedRandom(-1),
            IntervalMode.FIXED_5S.toConfig())
        scheduler.start()
        runCurrent()
        advanceTimeBy(5_501) // two blips elapsed
        assertEquals(2, audio.plays)

        // Volume goes straight to the engine; the scheduler is not touched.
        audio.setVolume(Volume.percentToGain(50))

        advanceTimeBy(10_001) // two more blips
        assertEquals(4, audio.plays)
        assertEquals(0.5f, audio.lastGain, 0f)
        assertEquals(ProblipState.RUNNING, scheduler.state.value)
        // Exactly one initial delay proves exactly one loop the whole time.
        assertEquals(1, delays.requested.count { it == 500L })
        assertTrue(delays.requested.drop(1).all { it == 5_000L })
        scheduler.stop()
    }
}
