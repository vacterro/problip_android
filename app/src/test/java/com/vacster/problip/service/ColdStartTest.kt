package com.vacster.problip.service

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.billing.seedableFromCache
import com.vacster.problip.core.IntervalConfig
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.trial.PremiumAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cold process start: the FIRST session of a process must honour what is on disk.
 *
 * Every test here reproduces the same race the barrier exists for — persisted
 * Settings arrive before the ownership cache has been seeded — and asserts that
 * the plan is built from the late snapshot, not from the empty placeholder. The
 * pre-barrier behaviour is asserted too, so these tests fail if the barrier is
 * ever removed rather than silently passing on timing luck.
 *
 * A START from the home-screen widget is exactly this case with the smallest
 * possible gap: Application.onCreate and the service start are microseconds
 * apart, so the cache seed is still in flight when the session begins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColdStartTest {

    private val glass = SoundCatalog.GLASS.id

    /** Ownership as it looks the instant a process starts: nothing known yet. */
    private class Snapshot {
        val settings = MutableStateFlow<SettingsRepository.Settings?>(null)
        val ownershipReady = MutableStateFlow(false)
        val owned = MutableStateFlow<Set<String>>(emptySet())
        val accessReady = MutableStateFlow(false)
        val access = MutableStateFlow(PremiumAccess())

        /** What the cache seed does a few millis into the process. */
        fun seedCache(ids: Set<String>) {
            owned.value = ids
            ownershipReady.value = true
        }

        fun restoreTemporaryAccess(value: PremiumAccess = PremiumAccess()) {
            access.value = value
            accessReady.value = true
        }
    }

    private suspend fun Snapshot.firstPlan(): ColdStart.Plan = ColdStart.awaitFirstPlan(
        settings = settings,
        ownershipReady = ownershipReady,
        ownedNow = { owned.value },
        accessReady = accessReady,
        accessNow = { access.value },
    )

    @Test
    fun persistedPremiumSoundSurvivesACacheSeedThatLandsAfterSettings() = runTest {
        val s = Snapshot()
        val plan = async { s.firstPlan() }

        // Settings answer first — this is the moment the old code read ownership.
        s.settings.value = SettingsRepository.Settings(selectedSounds = setOf(glass))
        runCurrent()
        assertFalse("the plan must not resolve before ownership is known", plan.isCompleted)
        // Reading ownership here is what produced the Original-Blip fallback.
        assertEquals(
            setOf(SoundCatalog.ORIGINAL.id),
            SoundCatalog.playableSelection(setOf(glass), s.owned.value, emptySet()),
        )

        s.seedCache(setOf(glass))
        s.restoreTemporaryAccess()
        runCurrent()

        assertEquals(setOf(glass), plan.await().pool)
    }

    @Test
    fun persistedManualIntervalSurvivesALateThemePackCacheSeed() = runTest {
        val s = Snapshot()
        val plan = async { s.firstPlan() }

        s.settings.value = SettingsRepository.Settings(
            intervalMode = IntervalMode.MANUAL,
            manualFromSeconds = 12,
            manualToSeconds = 30,
        )
        runCurrent()
        assertFalse(plan.isCompleted)

        s.seedCache(setOf(ProductCatalog.THEME_PACK))
        s.restoreTemporaryAccess()
        runCurrent()

        // Not IntervalConfig.Random(4000, 7000): the persisted MANUAL bounds run.
        assertEquals(IntervalConfig.Random(12_000L, 30_000L), plan.await().interval)
    }

    @Test
    fun persistedPulseIntervalSurvivesALateThemePackCacheSeed() = runTest {
        val s = Snapshot()
        val plan = async { s.firstPlan() }

        s.settings.value = SettingsRepository.Settings(intervalMode = IntervalMode.PULSE)
        runCurrent()
        assertFalse(plan.isCompleted)

        s.seedCache(setOf(ProductCatalog.THEME_PACK))
        s.restoreTemporaryAccess()
        runCurrent()

        assertEquals(IntervalConfig.Pulse(), plan.await().interval)
    }

    @Test
    fun aRestoredTrialIsHonouredByTheFirstBlipToo() = runTest {
        val s = Snapshot()
        val plan = async { s.firstPlan() }

        s.settings.value = SettingsRepository.Settings(
            selectedSounds = setOf(glass),
            intervalMode = IntervalMode.PULSE,
        )
        // Nothing is owned; a five-minute trial and Developer Access are what the
        // persisted timestamps restore, and they arrive after Settings as well.
        s.seedCache(emptySet())
        runCurrent()
        assertFalse("temporary access is part of the barrier", plan.isCompleted)

        s.restoreTemporaryAccess(PremiumAccess(developerAccess = true))
        runCurrent()

        val resolved = plan.await()
        assertEquals(setOf(glass), resolved.pool)
        assertEquals(IntervalConfig.Pulse(), resolved.interval)
    }

    @Test
    fun aSnapshotThatNeverArrivesDegradesToFreeContentInsteadOfHanging() = runTest {
        val s = Snapshot()
        val plan = async { s.firstPlan() }

        s.settings.value = SettingsRepository.Settings(
            selectedSounds = setOf(glass),
            intervalMode = IntervalMode.MANUAL,
        )
        // Ownership never becomes ready (failed disk read): the user must still get
        // a session, so the valve resolves the plan from what is known.
        advanceTimeBy(ColdStart.READY_TIMEOUT_MS + 1)
        runCurrent()

        val resolved = plan.await()
        assertEquals(setOf(SoundCatalog.ORIGINAL.id), resolved.pool)
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), resolved.interval)
    }

    @Test
    fun theCacheSeedsOnlyAnUninitializedSnapshotAndNeverOutlivesAPlayAnswer() {
        val cached = setOf(ProductCatalog.THEME_PACK)
        assertTrue(seedableFromCache(current = emptySet(), cached = cached, playAnswered = false))
        // An empty Play answer IS a refund/revocation; re-seeding would undo it.
        assertFalse(seedableFromCache(current = emptySet(), cached = cached, playAnswered = true))
        // A live snapshot is never overwritten either way.
        assertFalse(seedableFromCache(current = cached, cached = cached, playAnswered = false))
        assertFalse(seedableFromCache(current = emptySet(), cached = emptySet(), playAnswered = false))
    }

    @Test
    fun aRevokedPackFallsBackToTheFreePresetOnTheNextResolution() {
        // Same inputs as the MANUAL cold start, but Play has revoked the pack.
        val plan = ColdStart.plan(
            settings = SettingsRepository.Settings(
                intervalMode = IntervalMode.MANUAL,
                manualFromSeconds = 12,
                manualToSeconds = 30,
            ),
            owned = emptySet(),
            access = PremiumAccess(),
        )
        assertEquals(IntervalConfig.Random(4_000L, 7_000L), plan.interval)
    }
}
