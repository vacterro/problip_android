package com.vacster.problip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The BLIP GLOW Settings interaction contract, pinned as pure policy.
 *
 * The enable/disable toggle means ONLY the preference — it must never start a
 * trial. With the preference defaulting ON, a trial start wired into toggling
 * OFF would switch the effect off and start a five-minute trial the user can
 * never see. The trial belongs to the separate TRY action, offered only while
 * access is absent; a running trial already grants access, so nothing can ever
 * extend it. Toggling OFF during an active trial keeps the timer running and
 * only suppresses the effect.
 */
class GlowUiPolicyTest {

    @Test
    fun theGlowToggleMeansOnlyThePreferenceAndNeverStartsTheTrial() {
        val onToOff = GlowUiPolicy.onToggleToggled(true)
        val offToOn = GlowUiPolicy.onToggleToggled(false)
        assertTrue(onToOff is GlowInteraction.SetEnabled)
        assertTrue(offToOn is GlowInteraction.SetEnabled)
        assertEquals(false, (onToOff as GlowInteraction.SetEnabled).enabled)
        assertEquals(true, (offToOn as GlowInteraction.SetEnabled).enabled)
    }

    @Test
    fun theTryActionStartsTheTrialOnlyWhileAccessIsAbsent() {
        assertTrue(GlowUiPolicy.onTryTapped(hasAccess = false) is GlowInteraction.StartTrial)
        // With access (owned, earned, DEV or a running trial) the tap does
        // nothing: it can neither start a second trial nor extend a live one.
        assertTrue(GlowUiPolicy.onTryTapped(hasAccess = true) is GlowInteraction.None)
    }

    @Test
    fun theTryActionIsOfferedOnlyWithoutAccess() {
        assertFalse(GlowUiPolicy.tryOffered(hasAccess = true))
        assertTrue(GlowUiPolicy.tryOffered(hasAccess = false))
    }

    @Test
    fun togglingOffDoesNotCancelARunningTrial() {
        // The policy output for a toggle is SetEnabled — no trial mutation of
        // any kind exists, so an active trial's expiry is untouched and glow
        // resumes for the remaining time when the preference is ON again.
        val action = GlowUiPolicy.onToggleToggled(enabled = true)
        assertTrue(action is GlowInteraction.SetEnabled)
        assertFalse((action as GlowInteraction.SetEnabled).enabled)
    }
}
