package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.theme.ThemeCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The premium UI action pipeline: readiness barrier + sound-pool authority.
 * The fixture mirrors the real authorities with plain StateFlows — a cold
 * start's placeholders deny everything until the real snapshot lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumActionsTest {

    private val original = SoundCatalog.ORIGINAL.id
    private val glass = SoundCatalog.GLASS.id
    private val wood = SoundCatalog.WOOD.id
    private val bonk = SoundCatalog.BONK.id
    private val dracula = ThemeCatalog.DRACULA.id
    private val nord = ThemeCatalog.NORD.id

    private class Fixture {
        val ownershipReady = MutableStateFlow(false)
        val trialsReady = MutableStateFlow(false)
        val owned = MutableStateFlow<Set<String>>(emptySet())
        val access = MutableStateFlow(PremiumAccess())
        val pool = MutableStateFlow(setOf(SoundCatalog.ORIGINAL.id))
        val persistedTrials = mutableListOf<String>()
        var now = 1_000_000L
        private val expiries = MutableStateFlow(emptyMap<String, Long>())

        fun actions() = PremiumActions(
            ownershipReady = ownershipReady,
            trialsReady = trialsReady,
            owned = owned,
            access = access,
            startTrial = { contentId, ownedFlag ->
                // Mirror the REAL TrialCoordinator pipeline: the pure
                // TrialAccess map decides persistence, so a re-tap during a
                // running trial cannot extend or re-record anything.
                if (!ownedFlag) {
                    val before = expiries.value
                    expiries.value = TrialAccess.startTrial(
                        expiries.value,
                        contentId,
                        now,
                        owned = false,
                    )
                    if (expiries.value != before) persistedTrials += contentId
                    access.value = access.value.copy(
                        activeTrials = TrialAccess.activeIds(expiries.value, now),
                    )
                }
            },
            updateSelectedSounds = { transform -> pool.value = transform(pool.value) },
        )

        fun releaseAll() {
            ownershipReady.value = true
            trialsReady.value = true
        }
    }

    @Test
    fun actionIssuedBeforeReadinessWaitsAndThenUsesTheInitializedOwnership() = runTest {
        // THE CORE-004 cold-start race: the user taps TRY while the ownership
        // placeholder is still empty; the real Play answer says the sound is
        // owned. The action must suspend at the barrier and resolve AFTER the
        // initialized answer: no trial persisted, selection still applied.
        val f = Fixture()
        val actions = f.actions()

        val job = launch { actions.trySound(glass) }
        runCurrent()
        assertTrue("action must stay suspended while unready", f.persistedTrials.isEmpty())
        assertEquals(setOf(original), f.pool.value)

        // Real initialized ownership arrives, then readiness releases.
        f.owned.value = setOf(glass)
        f.releaseAll()
        runCurrent()
        job.join()

        assertTrue(f.persistedTrials.isEmpty())
        assertTrue(glass in f.pool.value)
    }

    @Test
    fun initializedGlobalGrantAlsoPreventsThePlaceholderTrial() = runTest {
        val f = Fixture()
        val actions = f.actions()

        val job = launch { actions.trySound(glass) }
        runCurrent()
        assertTrue(f.persistedTrials.isEmpty())

        // Developer Access was restored from disk before the tap resolves.
        f.access.value = PremiumAccess(developerAccess = true)
        f.releaseAll()
        runCurrent()
        job.join()

        assertTrue(f.persistedTrials.isEmpty())
        assertTrue(glass in f.pool.value)
    }

    @Test
    fun actionsStaySuspendedUntilBothAuthoritiesAreReady() = runTest {
        val f = Fixture()
        val actions = f.actions()

        f.trialsReady.value = true // one authority ready is NOT enough
        val job = launch { actions.trySound(glass) }
        runCurrent()
        assertTrue(f.persistedTrials.isEmpty())
        assertTrue(f.ownershipReady.value.not())

        f.ownershipReady.value = true
        runCurrent()
        job.join()

        assertEquals(listOf(glass), f.persistedTrials)
    }

    @Test
    fun accessibleSoundTogglesAgainstTheCurrentPersistedPool() = runTest {
        val f = Fixture()
        f.pool.value = setOf(original, glass)
        f.access.value = PremiumAccess(activeTrials = setOf(glass))
        f.releaseAll()
        val actions = f.actions()

        actions.toggleSound(wood)
        assertEquals(setOf(original, glass, wood), f.pool.value)

        actions.toggleSound(wood)
        assertEquals(setOf(original, glass), f.pool.value)
    }

    @Test
    fun lastAccessibleSoundCannotBeToggledOff() = runTest {
        val f = Fixture()
        // Persisted pool holds ONLY the trial member; the original was toggled
        // off earlier. Removing glass would leave nothing playable.
        f.pool.value = setOf(glass)
        f.access.value = PremiumAccess(activeTrials = setOf(glass))
        f.releaseAll()
        val actions = f.actions()

        actions.toggleSound(glass)
        assertEquals(setOf(glass), f.pool.value)
    }

    @Test
    fun inaccessiblePremiumSoundStartsExactlyOneNormalTrialAndJoinsThePool() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        actions.trySound(glass)
        assertEquals(listOf(glass), f.persistedTrials)
        assertTrue(glass in f.pool.value)

        // A second TRY while the trial runs never extends or re-persists.
        actions.trySound(glass)
        assertEquals(listOf(glass), f.persistedTrials)
        assertTrue(glass in f.pool.value)
    }

    @Test
    fun tryNeverDeselectsAnAlreadySelectedSound() = runTest {
        val f = Fixture()
        f.pool.value = setOf(original, glass)
        f.access.value = PremiumAccess(activeTrials = setOf(glass))
        f.releaseAll()
        val actions = f.actions()

        actions.trySound(glass)
        assertEquals(setOf(original, glass), f.pool.value)
    }

    @Test
    fun earlyColdStartTapCannotEraseUnrelatedPersistedSelections() = runTest {
        val f = Fixture()
        f.pool.value = setOf(original, glass)
        val actions = f.actions()

        val job = launch { actions.trySound(wood) }
        runCurrent()
        assertTrue(f.persistedTrials.isEmpty())

        f.releaseAll()
        runCurrent()
        job.join()

        assertEquals(listOf(wood), f.persistedTrials)
        assertEquals(setOf(original, glass, wood), f.pool.value)
    }

    @Test
    fun blipGlowTrialSkippedWhenAGlobalGrantAlreadyCoversIt() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        actions.tryBlipGlow()
        assertEquals(listOf(TrialAccess.FEATURE_BLIP_GLOW), f.persistedTrials)

        f.access.value = PremiumAccess(earnedPremium = true)
        actions.tryBlipGlow()
        assertEquals(listOf(TrialAccess.FEATURE_BLIP_GLOW), f.persistedTrials)
    }

    @Test
    fun premiumIntervalTrialStartsOnlyWhileThePresetIsLocked() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        actions.startIntervalTrialIfLocked(IntervalMode.MANUAL)
        assertEquals(listOf(TrialAccess.FEATURE_MANUAL_INTERVAL), f.persistedTrials)

        // Re-tap during the running trial grants through access: no new record.
        f.access.value = PremiumAccess(activeTrials = setOf(TrialAccess.FEATURE_MANUAL_INTERVAL))
        actions.startIntervalTrialIfLocked(IntervalMode.MANUAL)
        assertEquals(listOf(TrialAccess.FEATURE_MANUAL_INTERVAL), f.persistedTrials)

        // A free preset never starts anything.
        actions.startIntervalTrialIfLocked(IntervalMode.FIXED_10S)
        assertEquals(listOf(TrialAccess.FEATURE_MANUAL_INTERVAL), f.persistedTrials)
    }

    @Test
    fun premiumThemeTrialStartsOnlyWhenNothingGrantsIt() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        actions.startThemeTrialIfLocked(dracula, free = false)
        assertEquals(listOf(dracula), f.persistedTrials)

        // A global grant covers the next premium theme: no trial.
        f.access.value = PremiumAccess(developerAccess = true)
        actions.startThemeTrialIfLocked(nord, free = false)
        assertEquals(listOf(dracula), f.persistedTrials)

        // Free themes never start anything.
        actions.startThemeTrialIfLocked(ThemeCatalog.CLASSIC.id, free = true)
        assertEquals(listOf(dracula), f.persistedTrials)
    }
}
