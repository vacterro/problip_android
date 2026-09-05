package com.vacster.problip.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hidden two-finger chord as policy: twenty seconds of title hold, then a
 * START/STOP tap while that finger is still down. The Compose multi-touch wiring
 * itself is a physical-device check; every failure condition is decided here.
 */
class DeveloperGestureTest {

    private fun gesture() = DeveloperGesture()

    @Test
    fun holdShorterThanTwentySecondsDoesNotUnlock() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(19_000L)
        assertFalse(g.armed)
        assertFalse(g.consumeIfArmedAndTitleStillHeld())
    }

    @Test
    fun releasingTheTitleBeforeTheConfirmationDoesNotUnlock() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        g.titleReleased()
        assertFalse(g.consumeIfArmedAndTitleStillHeld())
    }

    @Test
    fun twentySecondHoldPlusConfirmationWhileHeldUnlocks() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        assertTrue(g.armed)
        assertTrue(g.consumeIfArmedAndTitleStillHeld())
    }

    @Test
    fun successfulConfirmationDisarmsImmediately() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        assertTrue(g.consumeIfArmedAndTitleStillHeld())
        assertFalse(g.armed)
        // Still physically held, but the arm is spent: this tap must start/stop.
        assertTrue(g.titleHeld)
        assertFalse(g.consumeIfArmedAndTitleStillHeld())
    }

    @Test
    fun cancelledTitleHoldDisarms() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        // Pointer cancellation arrives as a release; nothing partial is kept.
        g.titleReleased()
        assertFalse(g.armed)
        assertFalse(g.titleHeld)
    }

    @Test
    fun secondUnlockNeedsAnotherQualifyingHold() {
        val g = gesture()
        g.titlePressed()
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        assertTrue(g.consumeIfArmedAndTitleStillHeld())

        g.titleReleased()
        g.titlePressed()
        // A fresh press starts disarmed even though the previous hold qualified.
        assertFalse(g.armed)
        g.titleHeldFor(5_000L)
        assertFalse(g.consumeIfArmedAndTitleStillHeld())
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        assertTrue(g.consumeIfArmedAndTitleStillHeld())
    }

    @Test
    fun armingRequiresThePointerToStillBeDown() {
        val g = gesture()
        g.titlePressed()
        g.titleReleased()
        // A timer that fires after the finger left must not arm anything.
        g.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
        assertFalse(g.armed)
    }
}
