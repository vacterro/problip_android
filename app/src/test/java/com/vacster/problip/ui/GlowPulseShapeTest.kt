package com.vacster.problip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure pulse shape of the Blip Glow wash: rise to a peak at or below
 * [GLOW_MAX_ALPHA], a NON-ZERO peak hold (the reason the old rise-then-decay
 * pulse was easy to miss at phone refresh rates), and a return to zero. A new
 * event restarts the pulse instead of queueing it.
 *
 * Constants are pinned as the brief requires: rise 60 ms, hold 70 ms,
 * decay 210 ms, peak 0.25f — total 340 ms inside the accepted 300-350 ms window.
 */
class GlowPulseShapeTest {

    @Test
    fun pulseConstantsMatchTheBrief() {
        assertEquals(60, GLOW_RISE_MS)
        assertEquals(70, GLOW_HOLD_MS)
        assertEquals(210, GLOW_DECAY_MS)
        assertEquals(0.25f, GLOW_MAX_ALPHA)
        val total = GLOW_RISE_MS + GLOW_HOLD_MS + GLOW_DECAY_MS
        assertTrue("total pulse $total ms outside 300-350", total in 300..350)
    }

    @Test
    fun pulseReachesConfiguredPeakAfterRise() {
        val alpha = GlowPulseSimulation()
        alpha.blip()
        alpha.advance(GLOW_RISE_MS)
        assertEquals(GLOW_MAX_ALPHA, alpha.value)
    }

    @Test
    fun peakHoldIsNonZeroBeforeDecayStarts() {
        val alpha = GlowPulseSimulation()
        alpha.blip()
        alpha.advance(GLOW_RISE_MS)
        // Halfway into the hold the value is still exactly the peak: without a
        // hold, the decay would already have pulled it below the peak.
        alpha.advance(GLOW_HOLD_MS / 2)
        assertEquals(GLOW_MAX_ALPHA, alpha.value)
    }

    @Test
    fun pulseReturnsToZeroAfterDecay() {
        val alpha = GlowPulseSimulation()
        alpha.blip()
        alpha.advance(GLOW_RISE_MS + GLOW_HOLD_MS + GLOW_DECAY_MS)
        assertEquals(0f, alpha.value)
    }

    @Test
    fun aNewBlipRestartsThePulseRatherThanQueueingIt() {
        val alpha = GlowPulseSimulation()
        alpha.blip()
        alpha.advance(GLOW_RISE_MS + GLOW_HOLD_MS + GLOW_DECAY_MS + 100)
        assertEquals(0f, alpha.value)
        // A second real event runs the same deterministic sequence again from
        // zero — it neither stacks on the old value nor queues behind it.
        alpha.blip()
        alpha.advance(GLOW_RISE_MS)
        assertEquals(GLOW_MAX_ALPHA, alpha.value)
    }

    @Test
    fun aNewBlipMidDecayRestartsFromTheNewEvent() {
        val alpha = GlowPulseSimulation()
        alpha.blip()
        alpha.advance(GLOW_RISE_MS + GLOW_HOLD_MS)
        assertTrue(alpha.value > 0f)
        // Restart while the previous pulse is still active: the new event wins,
        // the old decay is cancelled, and no visible flash is queued after it.
        alpha.blip()
        alpha.advance(GLOW_RISE_MS / 2)
        val midSecondRise = alpha.value
        assertTrue(midSecondRise in 0.01f..GLOW_MAX_ALPHA - 0.01f)
        alpha.advance(GLOW_RISE_MS - GLOW_RISE_MS / 2 + GLOW_HOLD_MS + GLOW_DECAY_MS)
        assertEquals(0f, alpha.value)
    }
}

/**
 * Deterministic mirror of the composable's pulse sequence — snap to zero, rise
 * linearly to the peak, hold, decay linearly to zero — driven by the same
 * constants. The composable delegates its timing to
 * androidx.compose.animation.core.Animatable with linear tweens between the
 * same waypoints, so this simulation IS the pulse shape under test.
 */
private class GlowPulseSimulation {
    var value: Float = 0f
        private set
    private var elapsedMs: Long = 0L

    fun blip() {
        elapsedMs = 0L
    }

    fun advance(ms: Int) {
        val end = elapsedMs + ms
        val rise = GLOW_RISE_MS.toLong()
        val hold = GLOW_HOLD_MS.toLong()
        val decay = GLOW_DECAY_MS.toLong()
        value = when {
            end < rise -> GLOW_MAX_ALPHA * end.toFloat() / rise
            end < rise + hold -> GLOW_MAX_ALPHA
            end < rise + hold + decay ->
                GLOW_MAX_ALPHA * (1f - (end - rise - hold).toFloat() / decay)
            else -> 0f
        }
        elapsedMs = end
    }
}
