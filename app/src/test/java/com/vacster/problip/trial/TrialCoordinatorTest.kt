package com.vacster.problip.trial

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.theme.ThemeCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live temporary access: the coordinator turns persisted timestamps into
 * [PremiumAccess] and recomputes exactly at the next expiry. The fake clock is
 * advanced together with the test dispatcher's virtual time, so no test sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrialCoordinatorTest {

    private val glass = SoundCatalog.GLASS.id
    private val dracula = ThemeCatalog.DRACULA.id

    private class Fixture {
        val store = MutableStateFlow(TemporaryAccess())
        var now = 1_000_000L
    }

    private fun fixture(): Fixture = Fixture()

    private fun coordinator(f: Fixture, scope: kotlinx.coroutines.CoroutineScope) = TrialCoordinator(
        persisted = f.store,
        update = { transform ->
            f.store.value = f.store.value.copy(trialExpiries = transform(f.store.value.trialExpiries))
        },
        setDeveloperExpiry = { expiry -> f.store.value = f.store.value.copy(developerExpiryMillis = expiry) },
        clearTemporary = { f.store.value = TemporaryAccess() },
        scope = scope,
        clock = { f.now },
    )

    @Test
    fun developerUnlockGrantsSevenDaysFromNow() = runTest {
        val f = fixture()
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        trials.enableDeveloperAccess()
        runCurrent()

        assertEquals(f.now + 604_800_000L, f.store.value.developerExpiryMillis)
        assertTrue(trials.access.value.developerAccess)
        assertEquals(TrialAccess.ALL_IDS, trials.access.value.grantedIds)
    }

    @Test
    fun repeatedUnlockResetsTheWindowInsteadOfStackingIt() = runTest {
        val f = fixture()
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        trials.enableDeveloperAccess()
        runCurrent()
        val first = f.store.value.developerExpiryMillis

        // Four days later the gesture is performed again.
        f.now += 345_600_000L
        advanceTimeBy(345_600_000L)
        trials.enableDeveloperAccess()
        runCurrent()

        assertEquals(f.now + 604_800_000L, f.store.value.developerExpiryMillis)
        // The window moved by the elapsed four days; stacking would have added a
        // whole second week on top of the first expiry.
        assertEquals(345_600_000L, f.store.value.developerExpiryMillis - first)
        assertNotEquals(first + 604_800_000L, f.store.value.developerExpiryMillis)
    }

    @Test
    fun developerAccessExpiryRevokesOnlyTheOverride() = runTest {
        val f = fixture()
        // Persisted state of a Developer Access unlocked seven days minus a minute ago.
        f.store.value = TemporaryAccess(developerExpiryMillis = f.now + 60_000L)
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        trials.startTrial(dracula, owned = false)
        runCurrent()
        assertTrue(trials.access.value.developerAccess)

        // Developer Access ends first; the five-minute trial is still running.
        f.now += 60_000L
        advanceTimeBy(60_000L)
        runCurrent()

        assertFalse(trials.access.value.developerAccess)
        assertEquals(setOf(dracula), trials.access.value.activeTrials)
        assertEquals(setOf(dracula), trials.access.value.grantedIds)
    }

    @Test
    fun theSoonerOfTrialAndDeveloperExpiryWakesTheRecompute() = runTest {
        val f = fixture()
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        trials.enableDeveloperAccess()
        trials.startTrial(glass, owned = false)
        runCurrent()

        // The five-minute trial is the nearer expiry; Developer Access still holds.
        f.now += TrialAccess.DURATION_MS
        advanceTimeBy(TrialAccess.DURATION_MS)
        runCurrent()

        assertTrue(trials.access.value.activeTrials.isEmpty())
        assertTrue(trials.access.value.developerAccess)
        assertEquals(TrialAccess.ALL_IDS, trials.access.value.grantedIds)
    }

    @Test
    fun resetClearsDeveloperAccessAndEveryTrialInOneWrite() = runTest {
        val f = fixture()
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        trials.enableDeveloperAccess()
        trials.startTrial(glass, owned = false)
        runCurrent()

        trials.resetTemporaryAccess()
        runCurrent()

        assertEquals(TemporaryAccess(), f.store.value)
        assertEquals(PremiumAccess(), trials.access.value)
        assertTrue(trials.expiries.value.isEmpty())
        assertEquals(0L, trials.developerExpiryMillis.value)
        // A purchase is not part of temporary access and cannot be reset away.
        assertTrue(trials.access.value.grants(glass, owned = true))
    }

    @Test
    fun persistedTimestampsAreEvaluatedImmediatelyOnStart() = runTest {
        val f = fixture()
        f.store.value = TemporaryAccess(
            trialExpiries = mapOf(glass to f.now + 60_000L, dracula to f.now - 1L),
            developerExpiryMillis = f.now + 1_000L,
        )
        val trials = coordinator(f, backgroundScope)
        trials.start()
        runCurrent()

        // Future records restore as active, past ones grant nothing — no alarm involved.
        assertEquals(setOf(glass), trials.access.value.activeTrials)
        assertTrue(trials.access.value.developerAccess)
    }
}
