package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.theme.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trial semantics. Every case drives wall-clock time by hand, because "wait five
 * minutes" is not a test and Thread.sleep is not a clock.
 */
class TrialAccessTest {

    private val t0 = 1_000_000L
    private val glass = SoundCatalog.GLASS.id
    private val bonk = SoundCatalog.BONK.id
    private val dracula = ThemeCatalog.DRACULA.id
    private val nord = ThemeCatalog.NORD.id

    @Test
    fun newTrialLastsExactlyFiveMinutes() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        assertEquals(300_000L, TrialAccess.DURATION_MS)
        assertEquals(t0 + 300_000L, started[glass])
    }

    @Test
    fun activeTrialGrantsAccess() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        assertEquals(setOf(glass), TrialAccess.activeIds(started, t0 + 299_999L))
        assertEquals(
            setOf(glass),
            SoundCatalog.playableSelection(
                selected = setOf(glass),
                trials = TrialAccess.activeIds(started, t0 + 1),
            ),
        )
    }

    @Test
    fun trialExpiresAtTheBoundary() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        // The expiry instant itself is already expired: access is now < expiry.
        assertEquals(emptySet<String>(), TrialAccess.activeIds(started, t0 + 300_000L))
        assertEquals(emptySet<String>(), TrialAccess.activeIds(started, t0 + 300_001L))
    }

    @Test
    fun repeatSelectionWhileActiveDoesNotExtendExpiry() {
        val first = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val again = TrialAccess.startTrial(first, glass, t0 + 200_000L, owned = false)
        assertEquals(t0 + 300_000L, again[glass])
    }

    @Test
    fun expiredTrialMayStartAgain() {
        val first = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val later = t0 + 400_000L
        val second = TrialAccess.startTrial(first, glass, later, owned = false)
        assertEquals(later + 300_000L, second[glass])
        assertEquals(setOf(glass), TrialAccess.activeIds(second, later + 1))
    }

    @Test
    fun freeContentNeverNeedsATrial() {
        assertFalse(TrialAccess.isTrialable(SoundCatalog.ORIGINAL.id))
        assertFalse(TrialAccess.isTrialable(ThemeCatalog.CLASSIC.id))
        assertEquals(
            emptyMap<String, Long>(),
            TrialAccess.startTrial(emptyMap(), SoundCatalog.ORIGINAL.id, t0, owned = false),
        )
        assertEquals(
            setOf(SoundCatalog.ORIGINAL.id),
            SoundCatalog.playableSelection(setOf(SoundCatalog.ORIGINAL.id)),
        )
    }

    @Test
    fun ownedSoundIgnoresTrialExpiration() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val afterExpiry = t0 + 600_000L
        assertEquals(
            setOf(glass),
            SoundCatalog.playableSelection(
                selected = setOf(glass),
                owned = setOf(glass),
                trials = TrialAccess.activeIds(started, afterExpiry),
            ),
        )
        // Owning it also means a tap can no longer open a trial record.
        assertEquals(emptyMap<String, Long>(), TrialAccess.startTrial(emptyMap(), glass, t0, owned = true))
    }

    @Test
    fun themePackOwnershipIgnoresPremiumThemeTrialExpiration() {
        val started = TrialAccess.startTrial(emptyMap(), dracula, t0, owned = false)
        val afterExpiry = t0 + 600_000L
        assertEquals(
            ThemeCatalog.DRACULA,
            ThemeCatalog.effective(dracula, ownsThemePack = true, trials = TrialAccess.activeIds(started, afterExpiry)),
        )
    }

    @Test
    fun twoThemeTrialsHaveIndependentExpiries() {
        // 12:00 Dracula -> 12:05, 12:02 Nord -> 12:07, 12:03 Dracula again: no reset.
        val draculaStarted = TrialAccess.startTrial(emptyMap(), dracula, t0, owned = false)
        val bothStarted = TrialAccess.startTrial(draculaStarted, nord, t0 + 120_000L, owned = false)
        val reselected = TrialAccess.startTrial(bothStarted, dracula, t0 + 180_000L, owned = false)

        assertEquals(t0 + 300_000L, reselected[dracula])
        assertEquals(t0 + 420_000L, reselected[nord])
        assertEquals(setOf(nord), TrialAccess.activeIds(reselected, t0 + 300_000L))
        assertEquals(emptySet<String>(), TrialAccess.activeIds(reselected, t0 + 420_000L))
    }

    @Test
    fun twoSoundTrialsHaveIndependentExpiries() {
        val glassStarted = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val bothStarted = TrialAccess.startTrial(glassStarted, bonk, t0 + 60_000L, owned = false)
        assertEquals(t0 + 300_000L, bothStarted[glass])
        assertEquals(t0 + 360_000L, bothStarted[bonk])
        assertEquals(setOf(bonk), TrialAccess.activeIds(bothStarted, t0 + 300_000L))
    }

    @Test
    fun expiredSoundDisappearsFromEffectivePool() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val selected = setOf(SoundCatalog.ORIGINAL.id, glass)
        assertEquals(
            selected,
            SoundCatalog.playableSelection(selected, trials = TrialAccess.activeIds(started, t0 + 1)),
        )
        assertEquals(
            setOf(SoundCatalog.ORIGINAL.id),
            SoundCatalog.playableSelection(selected, trials = TrialAccess.activeIds(started, t0 + 300_000L)),
        )
    }

    @Test
    fun expiredOnlySelectedPremiumSoundFallsBackToOriginal() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        assertEquals(
            setOf(SoundCatalog.ORIGINAL.id),
            SoundCatalog.playableSelection(setOf(glass), trials = TrialAccess.activeIds(started, t0 + 300_000L)),
        )
    }

    @Test
    fun purchaseDuringTrialPreservesAccessAfterOldExpiry() {
        val started = TrialAccess.startTrial(emptyMap(), glass, t0, owned = false)
        val duringTrial = t0 + 167_000L
        assertEquals(setOf(glass), TrialAccess.activeIds(started, duringTrial))
        // Purchase lands, the stale record may be pruned, access must not blink.
        val pruned = TrialAccess.prune(started, t0 + 400_000L)
        assertEquals(emptyMap<String, Long>(), pruned)
        assertEquals(
            setOf(glass),
            SoundCatalog.playableSelection(setOf(glass), owned = setOf(glass), trials = TrialAccess.activeIds(pruned, t0 + 400_000L)),
        )
    }

    @Test
    fun invalidTrialIdCannotBePersistedOrActivated() {
        assertFalse(TrialAccess.isTrialable("sound_nope"))
        assertEquals(emptyMap<String, Long>(), TrialAccess.startTrial(emptyMap(), "sound_nope", t0, owned = false))
        // Junk that already sits in the store is dropped on read, not trusted.
        val junk = mapOf("sound_nope" to t0 + 300_000L, glass to 0L, bonk to -5L)
        assertEquals(emptyMap<String, Long>(), TrialAccess.sanitize(junk))
        assertEquals(emptySet<String>(), TrialAccess.activeIds(junk, t0))
    }

    @Test
    fun processReloadFromFutureExpiryRestoresActiveTrial() {
        val persisted = mapOf(glass to t0 + 300_000L)
        assertEquals(setOf(glass), TrialAccess.activeIds(persisted, t0 + 120_000L))
        assertEquals(t0 + 300_000L, TrialAccess.nextExpiryMillis(persisted, t0 + 120_000L))
    }

    @Test
    fun processReloadFromPastExpiryRestoresNoAccess() {
        val persisted = mapOf(glass to t0 - 1L)
        assertEquals(emptySet<String>(), TrialAccess.activeIds(persisted, t0))
        assertEquals(null, TrialAccess.nextExpiryMillis(persisted, t0))
        assertEquals(emptyMap<String, Long>(), TrialAccess.prune(persisted, t0))
    }

    @Test
    fun countdownRoundsUpAndFloorsAtZero() {
        assertEquals("05:00", TrialAccess.formatRemaining(t0 + 300_000L, t0))
        assertEquals("04:21", TrialAccess.formatRemaining(t0 + 260_400L, t0))
        assertEquals("00:01", TrialAccess.formatRemaining(t0 + 1L, t0))
        assertEquals("00:00", TrialAccess.formatRemaining(t0, t0))
        assertEquals("00:00", TrialAccess.formatRemaining(t0 - 60_000L, t0))
    }

    @Test
    fun trialableIdsAreExactlyThePremiumCatalogPlusPremiumFeatures() {
        assertEquals(SoundCatalog.all.filterNot { it.free }.map { it.id }.toSet(), TrialAccess.SOUND_IDS)
        assertEquals(ThemeCatalog.all.filterNot { it.free }.map { it.id }.toSet(), TrialAccess.THEME_IDS)
        // Features have no catalog entry, so they are counted separately.
        assertEquals(
            setOf(TrialAccess.FEATURE_MANUAL_INTERVAL, TrialAccess.FEATURE_PULSE_INTERVAL),
            TrialAccess.FEATURE_IDS,
        )
        assertTrue(
            TrialAccess.ALL_IDS.size ==
                TrialAccess.SOUND_IDS.size + TrialAccess.THEME_IDS.size + TrialAccess.FEATURE_IDS.size,
        )
        assertTrue(TrialAccess.isTrialable(TrialAccess.FEATURE_MANUAL_INTERVAL))
        assertTrue(TrialAccess.isTrialable(TrialAccess.FEATURE_PULSE_INTERVAL))
    }
}
