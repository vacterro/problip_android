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
 * W2-004 Parts C+D: compound premium commands must never claim success after a
 * failed prerequisite persistence, and a successfully persisted trial is never
 * rolled back when the second write fails. W2-003 latest-intent semantics must
 * hold unchanged. Deterministic JVM: plain StateFlow seams, virtual time, no
 * sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumActionsFailureTest {

    private val original = SoundCatalog.ORIGINAL.id
    private val glass = SoundCatalog.GLASS.id
    private val wood = SoundCatalog.WOOD.id
    private val dracula = ThemeCatalog.DRACULA.id

    private class Fixture {
        val ownershipReady = MutableStateFlow(false)
        val trialsReady = MutableStateFlow(false)
        val owned = MutableStateFlow<Set<String>>(emptySet())
        val access = MutableStateFlow(PremiumAccess())
        val pool = MutableStateFlow(setOf(SoundCatalog.ORIGINAL.id))
        val expiries = MutableStateFlow(emptyMap<String, Long>())

        /** Fails only the NEXT required trial write, then recovers. */
        var failNextTrial = false

        /** Fails only the NEXT pool write, then recovers. */
        var failNextPool = false

        /**
         * When set, the trial seam suspends on this gate BEFORE returning, so a
         * test can flip the latest-intent flag while the command is in flight
         * (deterministic mid-flight staleness, no sleeps).
         */
        var trialGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        val persistedTrials = mutableListOf<String>()
        var trialWriteCount = 0
        var poolWriteCount = 0

        fun actions() = PremiumActions(
            ownershipReady = ownershipReady,
            trialsReady = trialsReady,
            owned = owned,
            access = access,
            startTrial = lambda@{ contentId, ownedFlag ->
                trialWriteCount++
                trialGate?.await()
                if (ownedFlag || failNextTrial) {
                    if (failNextTrial && !ownedFlag) {
                        failNextTrial = false
                        return@lambda PersistenceOutcome.IO_FAILED
                    }
                    return@lambda PersistenceOutcome.NOT_REQUIRED
                }
                val before = expiries.value
                expiries.value = TrialAccess.startTrial(expiries.value, contentId, 1_000L, owned = false)
                if (expiries.value != before) persistedTrials += contentId
                PersistenceOutcome.PERSISTED
            },
            updateSelectedSounds = { transform ->
                poolWriteCount++
                if (failNextPool) {
                    failNextPool = false
                    return@PremiumActions PersistenceOutcome.IO_FAILED
                }
                pool.value = transform(pool.value)
                PersistenceOutcome.PERSISTED
            },
        )

        fun releaseAll() {
            ownershipReady.value = true
            trialsReady.value = true
        }
    }

    /** D: locked interval trial write fails -> IO_FAILED propagates to the caller. */
    @Test
    fun intervalTrialFailureSurfacesIoFailed() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()
        f.failNextTrial = true

        assertEquals(
            PersistenceOutcome.IO_FAILED,
            actions.startIntervalTrialIfLocked(IntervalMode.MANUAL),
        )
        assertEquals(1, f.trialWriteCount)
    }

    /** D: already-granted / free interval is NOT_REQUIRED, no trial write at all. */
    @Test
    fun freeOrGrantedIntervalRequiresNoTrialWrite() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        assertEquals(
            PersistenceOutcome.NOT_REQUIRED,
            actions.startIntervalTrialIfLocked(IntervalMode.FIXED_10S),
        )
        assertEquals(0, f.trialWriteCount)

        f.access.value = PremiumAccess(activeTrials = setOf(TrialAccess.FEATURE_MANUAL_INTERVAL))
        assertEquals(
            PersistenceOutcome.NOT_REQUIRED,
            actions.startIntervalTrialIfLocked(IntervalMode.MANUAL),
        )
        assertEquals(0, f.trialWriteCount)
    }

    /** G: locked sound TRY — trial persistence fails -> pool is NOT mutated. */
    @Test
    fun trySoundWithFailedTrialDoesNotTouchThePool() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()
        f.failNextTrial = true

        assertEquals(
            PersistenceOutcome.IO_FAILED,
            actions.trySound(glass),
        )
        assertEquals(setOf(original), f.pool.value)
        assertTrue(f.persistedTrials.isEmpty())
    }

    /** H: trial persists but the pool write fails -> trial stays, old pool stays. */
    @Test
    fun trySoundWithFailedPoolWriteKeepsTheTrialAndTheOldPool() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()
        f.failNextPool = true

        assertEquals(
            PersistenceOutcome.IO_FAILED,
            actions.trySound(glass),
        )
        // No compensating rollback: the successfully persisted trial REMAINS.
        assertEquals(listOf(glass), f.persistedTrials)
        assertTrue(glass in f.expiries.value)
        // The selected pool remains at its previously persisted value.
        assertEquals(setOf(original), f.pool.value)
    }

    /** F: locked theme trial write fails -> IO_FAILED. */
    @Test
    fun themeTrialFailureSurfacesIoFailed() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()
        f.failNextTrial = true

        assertEquals(
            PersistenceOutcome.IO_FAILED,
            actions.startThemeTrialIfLocked(dracula, free = false),
        )
    }

    /** F: free or granted theme is NOT_REQUIRED, no trial write at all. */
    @Test
    fun freeOrGrantedThemeRequiresNoTrialWrite() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        assertEquals(
            PersistenceOutcome.NOT_REQUIRED,
            actions.startThemeTrialIfLocked(ThemeCatalog.CLASSIC.id, free = true),
        )
        f.access.value = PremiumAccess(developerAccess = true)
        assertEquals(
            PersistenceOutcome.NOT_REQUIRED,
            actions.startThemeTrialIfLocked(dracula, free = false),
        )
        assertEquals(0, f.trialWriteCount)
    }

    /**
     * I: W2-003 regression — a stale TRY continuation whose trial persisted is
     * still unable to overwrite a newer sound selection; it reports
     * NOT_REQUIRED (no claim made) and the newer intent's pool wins.
     */
    @Test
    fun staleIntentAfterPersistedTrialCannotOverwriteANewerSelection() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        var staleIsCurrent = true
        var staleOutcome: PersistenceOutcome? = null
        // Gate the trial seam so the command is deterministically in flight
        // when the newer intent supersedes it.
        f.trialGate = kotlinx.coroutines.CompletableDeferred()
        val staleJob = launch { staleOutcome = actions.trySound(wood) { staleIsCurrent } }
        runCurrent()
        // A newer sound command superseded the TRY while it was in flight.
        staleIsCurrent = false
        f.trialGate?.complete(Unit)
        staleJob.join()

        // The stale command makes no claim (its final pool write was
        // suppressed), while its trial side effect legitimately persisted.
        assertEquals(PersistenceOutcome.NOT_REQUIRED, staleOutcome)
        // ...its trial side effect legitimately persisted (B5 invariant preserved),
        assertTrue(wood in f.expiries.value)
        // ...but the final pool mutation was suppressed: only the original stays.
        assertEquals(setOf(original), f.pool.value)
    }

    /** W2-003 regression: the newest sound intent's selection still wins. */
    @Test
    fun newestSoundIntentStillWinsWithResultHandlingAdded() = runTest {
        val f = Fixture()
        f.releaseAll()
        val actions = f.actions()

        var firstCurrent = true
        f.trialGate = kotlinx.coroutines.CompletableDeferred()
        val first = launch { actions.trySound(wood) { firstCurrent } }
        runCurrent()
        firstCurrent = false
        f.trialGate?.complete(Unit)
        first.join()

        // The newer command (glass) commits after the stale one was suppressed.
        actions.trySound(glass) { true }
        // The stale command performed NO pool mutation (suppressed final
        // write), so the pool holds original + the newer command's glass only.
        assertEquals(setOf(original, glass), f.pool.value)
        // The stale command's trial still persisted (never cancelled).
        assertTrue(wood in f.expiries.value)
    }
}
