package com.vacster.problip.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        repo4.setTheme("theme_terminal") // locked ids persist for post-purchase activation
        assertEquals("theme_terminal", repo4.settings.first().themeId)

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
}
