package com.vacster.problip.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vacster.problip.core.IntervalMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Note: each test performs at most ONE write per DataStore instance.
 * androidx.datastore cannot rename over an existing file on the Windows host
 * JVM, so repeat writes are verified on device instead (Android fs replaces).
 */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newRepo(): Pair<SettingsRepository, DataStore<Preferences>> {
        val ds = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(tmp.newFolder(), "problip_test.preferences_pb") },
        )
        return SettingsRepository(ds) to ds
    }

    @Test
    fun emptyStoreYieldsProductDefaults() = runBlocking {
        val (repo, _) = newRepo()
        assertEquals(
            SettingsRepository.Settings(
                volumePercent = 5,
                intervalMode = IntervalMode.RANDOM_4_7,
                selectedSounds = setOf("sound_original"),
                themeId = "theme_classic",
            ),
            repo.settings.first(),
        )
    }

    @Test
    fun settersWriteTheirValue() = runBlocking {
        val (repo, _) = newRepo()
        repo.setVolume(35)
        assertEquals(35, repo.settings.first().volumePercent)

        val (repo2, _) = newRepo()
        repo2.setInterval(IntervalMode.FIXED_10S)
        assertEquals(IntervalMode.FIXED_10S, repo2.settings.first().intervalMode)

        val (repo3, _) = newRepo()
        repo3.setSelectedSounds(setOf("sound_original", "sound_glass"))
        assertEquals(setOf("sound_original", "sound_glass"), repo3.settings.first().selectedSounds)

        val (repo4, _) = newRepo()
        repo4.setTheme("theme_wintage_nord") // locked ids persist for post-purchase activation
        assertEquals("theme_wintage_nord", repo4.settings.first().themeId)

        val (repo5, _) = newRepo()
        repo5.setTheme("not_a_theme")
        assertEquals("theme_classic", repo5.settings.first().themeId)
    }

    @Test
    fun setSelectedSoundsDropsUnknownIdsAndNeverStoresEmpty() = runBlocking {
        val (repo, _) = newRepo()
        repo.setSelectedSounds(setOf("sound_original", "made_up_sound"))
        assertEquals(setOf("sound_original"), repo.settings.first().selectedSounds)

        val (repo2, _) = newRepo()
        repo2.setSelectedSounds(emptySet())
        assertEquals(setOf("sound_original"), repo2.settings.first().selectedSounds)
    }

    @Test
    fun setVolumeClampsToValidRange() = runBlocking {
        val (low, _) = newRepo()
        low.setVolume(-3)
        assertEquals(0, low.settings.first().volumePercent)

        val (high, _) = newRepo()
        high.setVolume(150)
        assertEquals(100, high.settings.first().volumePercent)
    }

    @Test
    fun legacySingleSoundKeyMigratesIntoThePool() = runBlocking {
        val (repo, ds) = newRepo()
        ds.edit { it[stringPreferencesKey("sound")] = "sound_glass" }
        // The W3 single-sound key seeds the pool when the CSV key is absent.
        assertEquals(setOf("sound_glass"), repo.settings.first().selectedSounds)
    }

    @Test
    fun garbageValuesFallBackToDefaults() = runBlocking {
        // Raw garbage the way a hand-edited or partially corrupted store would look.
        // Key names are the stable on-disk format of this repository.
        val (repo, ds) = newRepo()
        ds.edit {
            it[intPreferencesKey("volume")] = 999
            it[stringPreferencesKey("interval")] = "GARBAGE"
            it[stringPreferencesKey("sounds")] = "removed_a,removed_b"
            it[stringPreferencesKey("theme")] = "nope"
        }
        val s = repo.settings.first()
        assertEquals(100, s.volumePercent) // clamped, not defaulted
        assertEquals(IntervalMode.RANDOM_4_7, s.intervalMode)
        assertEquals(setOf("sound_original"), s.selectedSounds)
        assertEquals("theme_classic", s.themeId)
    }

    @Test
    fun trialExpiriesRoundTripAndDropUntrialableIds() = runBlocking {
        val (repo, _) = newRepo()
        repo.updateTrialExpiries { current ->
            current + mapOf(
                "sound_glass" to 4_000L,
                "sound_original" to 9_000L, // free: nothing to trial
                "not_a_sound" to 9_000L,
            )
        }
        assertEquals(mapOf("sound_glass" to 4_000L), repo.settings.first().trialExpiries)
    }

    @Test
    fun persistedTrialCsvSurvivesProcessDeathAndIgnoresJunk() = runBlocking {
        // The on-disk format is "id:epochMillis" pairs; a reload is just a re-read.
        val (repo, ds) = newRepo()
        ds.edit {
            it[stringPreferencesKey("trial_expiries")] =
                "sound_glass:5000,theme_wintage_dracula:7000,not_a_sound:9000,sound_bonk:oops,sound_wood"
        }
        assertEquals(
            mapOf("sound_glass" to 5_000L, "theme_wintage_dracula" to 7_000L),
            repo.settings.first().trialExpiries,
        )
    }

    @Test
    fun developerAccessExpirySurvivesProcessDeath() = runBlocking {
        val (repo, _) = newRepo()
        repo.setDeveloperAccessExpiry(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, repo.settings.first().developerAccessExpiryMillis)

        // A junk/negative timestamp reads as "never unlocked", not as access.
        val (repo2, ds) = newRepo()
        ds.edit { it[longPreferencesKey("developer_access_expiry")] = -5L }
        assertEquals(0L, repo2.settings.first().developerAccessExpiryMillis)
    }

    @Test
    fun manualIntervalPersistsOnlyValidatedBounds() = runBlocking {
        val (repo, _) = newRepo()
        // Written the wrong way round: stored ordered, so FROM > TO never persists.
        repo.setManualInterval(10, 7)
        val s = repo.settings.first()
        assertEquals(7, s.manualFromSeconds)
        assertEquals(10, s.manualToSeconds)

        val (repo2, ds) = newRepo()
        ds.edit {
            it[intPreferencesKey("manual_from_seconds")] = 0
            it[intPreferencesKey("manual_to_seconds")] = 99_999
        }
        val clamped = repo2.settings.first()
        assertEquals(1, clamped.manualFromSeconds)
        assertEquals(3600, clamped.manualToSeconds)
    }

    @Test
    fun counterAndGlowPreferencesDefaultOnAndPersist() = runBlocking {
        val (repo, _) = newRepo()
        // Defaults: both ON.
        val defaults = repo.settings.first()
        assertEquals(true, defaults.showBlipCounter)
        assertEquals(true, defaults.blipGlowEnabled)

        // One write per DataStore instance on the Windows host JVM (see class
        // note), so each OFF choice gets its own store and re-read.
        val (offCounter, _) = newRepo()
        offCounter.setShowBlipCounter(false)
        assertEquals(false, offCounter.settings.first().showBlipCounter)

        val (offGlow, _) = newRepo()
        offGlow.setBlipGlowEnabled(false)
        assertEquals(false, offGlow.settings.first().blipGlowEnabled)
    }

    @Test
    fun aMissingGlowPreferenceStillMeansOn() = runBlocking {
        val (repo, _) = newRepo()
        // No BLIP GLOW key ever written: reads ON.
        assertEquals(true, repo.settings.first().blipGlowEnabled)
        assertEquals(true, repo.settings.first().showBlipCounter)
    }
}
