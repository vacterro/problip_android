package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.theme.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The effective-access policy: free || owned || activeTrial || developerAccess.
 * Pure, so every combination is checked here instead of on a device.
 */
class PremiumAccessTest {

    private val glass = SoundCatalog.GLASS.id
    private val dracula = ThemeCatalog.DRACULA.id
    private val manual = TrialAccess.FEATURE_MANUAL_INTERVAL
    private val pulse = TrialAccess.FEATURE_PULSE_INTERVAL

    @Test
    fun developerAccessLastsExactlySevenDays() {
        assertEquals(604_800_000L, PremiumAccess.DEVELOPER_ACCESS_DURATION_MS)
        assertEquals(1_604_800_000L, PremiumAccess.developerExpiryFrom(1_000_000_000L))
    }

    @Test
    fun nothingIsGrantedWithoutAnEntitlement() {
        val access = PremiumAccess()
        assertFalse(access.grants(glass))
        assertFalse(access.grants(dracula))
        assertFalse(access.grantsManualInterval(ownsCustomizationPack = false))
        assertFalse(access.grantsPulseInterval(ownsCustomizationPack = false))
        assertTrue(access.grants(SoundCatalog.ORIGINAL.id, free = true))
    }

    @Test
    fun developerAccessGrantsEveryPremiumSoundThemeAndFeature() {
        val access = PremiumAccess(developerAccess = true)
        assertTrue(access.grants(glass))
        assertTrue(access.grants(dracula))
        assertTrue(access.grantsManualInterval(ownsCustomizationPack = false))
        assertTrue(access.grantsPulseInterval(ownsCustomizationPack = false))
        // A global override, not a hand-written list: every trialable id is covered,
        // which is what makes later premium content work with no change here.
        assertEquals(TrialAccess.ALL_IDS, access.grantedIds)
    }

    @Test
    fun grantedIdsAreTheRunningTrialsWithoutDeveloperAccess() {
        val access = PremiumAccess(activeTrials = setOf(glass))
        assertEquals(setOf(glass), access.grantedIds)
        assertTrue(access.grants(glass))
        assertFalse(access.grants(dracula))
    }

    @Test
    fun purchasesAndRunningTrialsSurviveDeveloperAccessExpiry() {
        // Developer Access has ended; the other two entitlements are independent.
        val expired = PremiumAccess(activeTrials = setOf(dracula), developerAccess = false)
        assertTrue(expired.grants(glass, owned = true))
        assertTrue(expired.grants(dracula))
        assertFalse(expired.grants(SoundCatalog.BONK.id))
    }

    @Test
    fun manualIntervalRidesThePackPurchaseOrItsOwnTrial() {
        assertTrue(PremiumAccess().grantsManualInterval(ownsCustomizationPack = true))
        assertTrue(
            PremiumAccess(activeTrials = setOf(manual))
                .grantsManualInterval(ownsCustomizationPack = false),
        )
        assertTrue(
            PremiumAccess(developerAccess = true)
                .grantsManualInterval(ownsCustomizationPack = false),
        )
        assertFalse(
            PremiumAccess(activeTrials = setOf(glass))
                .grantsManualInterval(ownsCustomizationPack = false),
        )
    }

    @Test
    fun pulseIntervalRidesThePackPurchaseOrItsOwnTrial() {
        assertTrue(PremiumAccess().grantsPulseInterval(ownsCustomizationPack = true))
        assertTrue(
            PremiumAccess(activeTrials = setOf(pulse))
                .grantsPulseInterval(ownsCustomizationPack = false),
        )
        assertTrue(
            PremiumAccess(developerAccess = true)
                .grantsPulseInterval(ownsCustomizationPack = false),
        )
        assertFalse(
            PremiumAccess(activeTrials = setOf(glass))
                .grantsPulseInterval(ownsCustomizationPack = false),
        )
    }

    @Test
    fun thePremiumIntervalTrialsAreIndependentOfEachOther() {
        // Two separate five-minute trials on one purchase: neither tap unlocks the other.
        val manualOnly = PremiumAccess(activeTrials = setOf(manual))
        assertTrue(manualOnly.grantsManualInterval(ownsCustomizationPack = false))
        assertFalse(manualOnly.grantsPulseInterval(ownsCustomizationPack = false))

        val pulseOnly = PremiumAccess(activeTrials = setOf(pulse))
        assertTrue(pulseOnly.grantsPulseInterval(ownsCustomizationPack = false))
        assertFalse(pulseOnly.grantsManualInterval(ownsCustomizationPack = false))

        // The pack covers both, which is why PULSE needs no new Play SKU.
        val owner = PremiumAccess()
        assertTrue(owner.grantsManualInterval(ownsCustomizationPack = true))
        assertTrue(owner.grantsPulseInterval(ownsCustomizationPack = true))
    }

    @Test
    fun developerAccessIsActiveOnlyBeforeItsExpiry() {
        assertTrue(PremiumAccess.developerActive(expiryMillis = 1_001L, nowMillis = 1_000L))
        // The boundary itself is already expired, same rule as the trials.
        assertFalse(PremiumAccess.developerActive(expiryMillis = 1_000L, nowMillis = 1_000L))
        assertFalse(PremiumAccess.developerActive(expiryMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun remainingLabelCountsDownDaysThenHoursThenMinutes() {
        val now = 1_000_000L
        assertEquals(
            "6d 23h",
            PremiumAccess.formatDeveloperRemaining(now + 604_800_000L - 60_000L, now),
        )
        assertEquals("7d 0h", PremiumAccess.formatDeveloperRemaining(now + 604_800_000L, now))
        assertEquals("2h 5m", PremiumAccess.formatDeveloperRemaining(now + 7_500_000L, now))
        assertEquals("4m", PremiumAccess.formatDeveloperRemaining(now + 240_000L, now))
        assertEquals("0m", PremiumAccess.formatDeveloperRemaining(now - 1L, now))
    }
}
