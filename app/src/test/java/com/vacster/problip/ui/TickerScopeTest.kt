package com.vacster.problip.ui

import com.vacster.problip.core.IntervalMode
import com.vacster.problip.trial.TrialAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-003 focused evidence (pure JVM, no Compose runtime): the shared
 * one-second tick runs ONLY where a visible countdown actually depends on it.
 *
 * - Main: an unrelated active trial (glow, another sound, the other interval
 *   feature) must NOT enable the Main ticker; a trial on the currently
 *   displayed interval feature or on a listed premium sound must.
 * - Sounds/Themes: only trials of listed items enable the screen tick.
 * - Developer Access / earned Premium grant everything timer-free and never
 *   need a countdown tick.
 *
 * Expiry semantics stay owned by TrialCoordinator: these decisions only gate a
 * display clock, never access itself.
 */
class TickerScopeTest {

    private val now = 1_700_000_000_000L
    private val later = now + TrialAccess.DURATION_MS
    private val past = now - 1L

    // ---------------------------------------------------- mainTickerEnabled --

    @Test
    fun anUnrelatedGlowTrialDoesNotCreateAMainTicker() {
        // Counter mode OFF or not, the glow trial lives on Settings: Main must
        // not run a one-second clock because of it.
        assertFalse(
            mainTickerEnabled(
                intervalMode = IntervalMode.FIXED_5S,
                trialExpiries = mapOf(TrialAccess.FEATURE_BLIP_GLOW to later),
                poolIds = emptyList(),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun theDisplayedIntervalFeatureTrialEnablesTheMainTicker() {
        assertTrue(
            mainTickerEnabled(
                intervalMode = IntervalMode.PULSE,
                trialExpiries = mapOf(TrialAccess.FEATURE_PULSE_INTERVAL to later),
                poolIds = emptyList(),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun theOtherIntervalFeatureTrialDoesNotEnableTheMainTicker() {
        // PULSE is selected; a MANUAL trial is unrelated to what Main displays.
        assertFalse(
            mainTickerEnabled(
                intervalMode = IntervalMode.PULSE,
                trialExpiries = mapOf(TrialAccess.FEATURE_MANUAL_INTERVAL to later),
                poolIds = emptyList(),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun aTrialOnAListedPremiumSoundEnablesTheMainTicker() {
        assertTrue(
            mainTickerEnabled(
                intervalMode = IntervalMode.FIXED_5S,
                trialExpiries = mapOf("sound_glass" to later),
                poolIds = listOf("original", "sound_glass"),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun anExpiredTrialNeverEnablesTheMainTicker() {
        assertFalse(
            mainTickerEnabled(
                intervalMode = IntervalMode.PULSE,
                trialExpiries = mapOf(TrialAccess.FEATURE_PULSE_INTERVAL to past),
                poolIds = listOf("sound_glass"),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun developerAccessAndEarnedPremiumNeverNeedAMainTicker() {
        val expiries = mapOf(TrialAccess.FEATURE_PULSE_INTERVAL to later, "sound_glass" to later)
        assertFalse(
            mainTickerEnabled(
                intervalMode = IntervalMode.PULSE,
                trialExpiries = expiries,
                poolIds = listOf("sound_glass"),
                owned = emptySet(),
                developerAccess = true,
                earnedPremium = false,
                nowMillis = now,
            ),
        )
        assertFalse(
            mainTickerEnabled(
                intervalMode = IntervalMode.PULSE,
                trialExpiries = expiries,
                poolIds = listOf("sound_glass"),
                owned = emptySet(),
                developerAccess = false,
                earnedPremium = true,
                nowMillis = now,
            ),
        )
    }

    // --------------------------------------------------- screenTickerEnabled --

    @Test
    fun aListedSoundTrialEnablesTheSoundsScreenTick() {
        assertTrue(
            screenTickerEnabled(
                listedIds = listOf("original", "sound_glass"),
                owned = emptySet(),
                trialExpiries = mapOf("sound_glass" to later),
                developerAccess = false,
            ),
        )
    }

    @Test
    fun aGlowTrialDoesNotChurnTheSoundsScreen() {
        // FEATURE_BLIP_GLOW is not a listed sound; its countdown lives on Settings.
        assertFalse(
            screenTickerEnabled(
                listedIds = listOf("original", "sound_glass"),
                owned = emptySet(),
                trialExpiries = mapOf(TrialAccess.FEATURE_BLIP_GLOW to later),
                developerAccess = false,
            ),
        )
    }

    @Test
    fun anOwnedItemNeverNeedsTheScreenTick() {
        assertFalse(
            screenTickerEnabled(
                listedIds = listOf("sound_glass"),
                owned = setOf("sound_glass"),
                trialExpiries = mapOf("sound_glass" to later),
                developerAccess = false,
            ),
        )
    }

    @Test
    fun themePackTrialEnablesOnlyTheThemesScreenDecision() {
        val expiries = mapOf("theme_pack" to later)
        assertTrue(
            screenTickerEnabled(
                listedIds = listOf("classic", "theme_pack"),
                owned = emptySet(),
                trialExpiries = expiries,
                developerAccess = false,
            ),
        )
        assertFalse(
            screenTickerEnabled(
                listedIds = listOf("original", "sound_glass"),
                owned = emptySet(),
                trialExpiries = expiries,
                developerAccess = false,
            ),
        )
    }

    @Test
    fun developerAccessSuppressesTheScreenTick() {
        assertFalse(
            screenTickerEnabled(
                listedIds = listOf("sound_glass"),
                owned = emptySet(),
                trialExpiries = mapOf("sound_glass" to later),
                developerAccess = true,
            ),
        )
    }
}
