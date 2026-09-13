package com.vacster.problip.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vacster.problip.core.IntervalMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * W2-004 Part A: the ONE write-failure contract of every SettingsRepository
 * mutation, exercised against a fake DataStore whose edit path throws.
 * Deterministic: no sleeps, no real filesystem.
 *
 * A. expected IOException -> IoFailure result, no uncaught exception, store unchanged
 * B. unexpected programming failure -> propagates (NOT swallowed as IoFailure)
 * C. cancellation -> stays cancellation (NOT translated into a result)
 */
class SettingsWriteContractTest {

    /** Values the store already holds before every failure scenario. */
    private object Seed {
        val VOLUME = 42
        val INTERVAL = IntervalMode.FIXED_10S
        val SOUNDS = "sound_original,sound_glass"
        val THEME = "theme_classic"
        val TRIALS = "sound_glass:5000"
        val DEVELOPER = 1_700_000_000_000L
        val MANUAL_FROM = 7
        val MANUAL_TO = 10
        val COUNTER_MODE = BlipCounterMode.STATS
        val SHOW_COUNTER = false
        val GLOW = false
    }

    private fun seeded(): Preferences = preferencesOf(
        intPreferencesKey("volume") to Seed.VOLUME,
        stringPreferencesKey("interval") to Seed.INTERVAL.name,
        stringPreferencesKey("sounds") to Seed.SOUNDS,
        stringPreferencesKey("theme") to Seed.THEME,
        stringPreferencesKey("trial_expiries") to Seed.TRIALS,
        longPreferencesKey("developer_access_expiry") to Seed.DEVELOPER,
        intPreferencesKey("manual_from_seconds") to Seed.MANUAL_FROM,
        intPreferencesKey("manual_to_seconds") to Seed.MANUAL_TO,
        stringPreferencesKey("blip_counter_mode") to Seed.COUNTER_MODE.name,
        booleanPreferencesKey("show_blip_counter") to Seed.SHOW_COUNTER,
        booleanPreferencesKey("blip_glow_enabled") to Seed.GLOW,
    )

    /**
     * In-memory DataStore seam: the edit path either throws the injected
     * Throwable BEFORE any transform runs (so nothing commits) or applies the
     * transform like the real store. Serialized under a mutex like the real
     * per-file actor. A null failure is the committing behavior.
     */
    private class FakeDataStore(
        initial: Preferences,
        private val failure: Throwable?,
    ) : DataStore<Preferences> {

        private val state = MutableStateFlow(initial)
        private val lock = Mutex()

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            lock.withLock {
                failure?.let { throw it }
                state.value = transform(state.value)
                state.value
            }
    }

    private fun failingRepo(failure: Throwable): SettingsRepository =
        SettingsRepository(FakeDataStore(seeded(), failure))

    /** A.1: every mutation family returns IoFailure on an IOException store. */
    @Test
    fun everyMutationReportsIoFailureWhenTheStoreThrowsIOException() = runBlocking {
        val repo = failingRepo(IOException("disk full"))

        assertEquals(SettingsWriteResult.IoFailure, repo.setVolume(30))
        assertEquals(SettingsWriteResult.IoFailure, repo.setInterval(IntervalMode.FIXED_30S))
        assertEquals(
            SettingsWriteResult.IoFailure,
            repo.setSelectedSounds(setOf("sound_glass", "sound_wood")),
        )
        assertEquals(
            SettingsWriteResult.IoFailure,
            repo.updateSelectedSounds { it + "sound_bonk" },
        )
        assertEquals(SettingsWriteResult.IoFailure, repo.setTheme("theme_dracula"))
        assertEquals(SettingsWriteResult.IoFailure, repo.setOwnedProducts(setOf("theme_pack")))
        assertEquals(
            SettingsWriteResult.IoFailure,
            repo.updateTrialExpiries { it + ("sound_wood" to 9_000L) },
        )
        assertEquals(SettingsWriteResult.IoFailure, repo.setDeveloperAccessExpiry(123L))
        assertEquals(SettingsWriteResult.IoFailure, repo.setManualInterval(20, 30))
        assertEquals(SettingsWriteResult.IoFailure, repo.setShowBlipCounter(true))
        assertEquals(SettingsWriteResult.IoFailure, repo.setBlipCounterMode(BlipCounterMode.OFF))
        assertEquals(SettingsWriteResult.IoFailure, repo.setBlipGlowEnabled(true))
        assertEquals(SettingsWriteResult.IoFailure, repo.clearTemporaryAccess())
    }

    /** A.2: after all those failed writes the previously persisted state is unchanged. */
    @Test
    fun failedWritesLeaveThePreviouslyPersistedStateUntouched() = runBlocking {
        val repo = failingRepo(IOException("disk full"))

        repo.setVolume(30)
        repo.setInterval(IntervalMode.FIXED_30S)
        repo.updateSelectedSounds { it - "sound_glass" }
        repo.setTheme("theme_dracula")
        repo.setOwnedProducts(setOf("theme_pack"))
        repo.updateTrialExpiries { it + ("sound_wood" to 9_000L) }
        repo.setDeveloperAccessExpiry(1L)
        repo.setManualInterval(20, 30)
        repo.setShowBlipCounter(true)
        repo.setBlipCounterMode(BlipCounterMode.OFF)
        repo.setBlipGlowEnabled(true)
        repo.clearTemporaryAccess()

        val s = repo.settings.first()
        assertEquals(Seed.VOLUME, s.volumePercent)
        assertEquals(Seed.INTERVAL, s.intervalMode)
        assertEquals(setOf("sound_original", "sound_glass"), s.selectedSounds)
        assertEquals(Seed.THEME, s.themeId)
        assertEquals(emptySet<String>(), s.ownedProducts)
        assertEquals(mapOf("sound_glass" to 5_000L), s.trialExpiries)
        assertEquals(Seed.DEVELOPER, s.developerAccessExpiryMillis)
        assertEquals(Seed.MANUAL_FROM, s.manualFromSeconds)
        assertEquals(Seed.MANUAL_TO, s.manualToSeconds)
        assertEquals(Seed.COUNTER_MODE, s.blipCounterMode)
        assertEquals(Seed.SHOW_COUNTER, s.showBlipCounter)
        assertEquals(Seed.GLOW, s.blipGlowEnabled)
    }

    /** B: a non-IOException programming failure is NOT swallowed as IoFailure. */
    @Test
    fun unexpectedProgrammingFailurePropagatesInsteadOfBecomingIoFailure() {
        val repo = failingRepo(IllegalStateException("programmer error"))

        assertThrows(IllegalStateException::class.java) { runBlocking { repo.setVolume(10) } }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.updateSelectedSounds { it } }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.clearTemporaryAccess() }
        }
    }

    /** C: cancellation remains cancellation, never a result and never a swallow. */
    @Test
    fun cancellationStaysCancellation() {
        val repo = failingRepo(CancellationException("cancelled"))

        assertThrows(CancellationException::class.java) { runBlocking { repo.setVolume(10) } }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo.setTheme("theme_classic") }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { repo.clearTemporaryAccess() }
        }
    }

    /** The success path still reports Success through the same single policy. */
    @Test
    fun committingEditsStillReportSuccess() = runBlocking {
        val repo = SettingsRepository(FakeDataStore(seeded(), failure = null))

        assertEquals(SettingsWriteResult.Success, repo.setVolume(30))
        assertEquals(SettingsWriteResult.Success, repo.clearTemporaryAccess())
        assertEquals(30, repo.settings.first().volumePercent)
    }
}
