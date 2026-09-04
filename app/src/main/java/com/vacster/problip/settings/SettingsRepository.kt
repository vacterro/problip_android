package com.vacster.problip.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.audio.Volume
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.theme.ThemeCatalog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.problipDataStore: DataStore<Preferences> by preferencesDataStore(name = "problip")

/**
 * Local settings only. Nothing here may resurrect a running session:
 * an explicit START always begins a fresh session (see behavior contract).
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    data class Settings(
        val volumePercent: Int = Volume.DEFAULT_PERCENT,
        val intervalMode: IntervalMode = IntervalMode.RANDOM_4_7,
        val selectedSounds: Set<String> = setOf(SoundCatalog.ORIGINAL.id),
        val themeId: String = ThemeCatalog.CLASSIC.id,
        /** Offline convenience cache of Play ownership; Play is the authority. */
        val ownedProducts: Set<String> = emptySet(),
    )

    val settings: Flow<Settings> = dataStore.data
        .catch { e ->
            // Unreadable store falls back to defaults instead of crashing the app.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            Settings(
                volumePercent = p[VOLUME]?.coerceIn(0, 100) ?: Volume.DEFAULT_PERCENT,
                intervalMode = p[INTERVAL]?.let { stored ->
                    IntervalMode.entries.firstOrNull { it.name == stored }
                } ?: IntervalMode.RANDOM_4_7,
                selectedSounds = selectedSounds(p),
                themeId = p[THEME]?.takeIf { ThemeCatalog.isValidId(it) } ?: ThemeCatalog.CLASSIC.id,
                ownedProducts = p[OWNED]?.split(',')
                    ?.map { it.trim() }
                    ?.filter { ProductCatalog.isValid(it) }
                    ?.toSet()
                    ?: emptySet(),
            )
        }

    suspend fun setVolume(percent: Int) {
        dataStore.edit { it[VOLUME] = percent.coerceIn(0, 100) }
    }

    suspend fun setInterval(mode: IntervalMode) {
        dataStore.edit { it[INTERVAL] = mode.name }
    }

    /** Persisted as sorted CSV; unknown ids are dropped, empty falls back to original. */
    suspend fun setSelectedSounds(soundIds: Set<String>) {
        val sanitized = soundIds.mapNotNull { id -> SoundCatalog.byId(id)?.id }.toSet()
        val safe = sanitized.ifEmpty { setOf(SoundCatalog.ORIGINAL.id) }
        dataStore.edit { it[SOUNDS] = safe.sorted().joinToString(",") }
    }

    /** Only catalog ids are stored; unknown ids fall back to Classic. */
    suspend fun setTheme(themeId: String) {
        dataStore.edit { it[THEME] = if (ThemeCatalog.isValidId(themeId)) themeId else ThemeCatalog.CLASSIC.id }
    }

    /** Written only from successful Play purchase queries. */
    suspend fun setOwnedProducts(productIds: Set<String>) {
        val sanitized = productIds.filter { ProductCatalog.isValid(it) }.sorted()
        dataStore.edit { it[OWNED] = sanitized.joinToString(",") }
    }

    private fun selectedSounds(p: Preferences): Set<String> {
        val csv = p[SOUNDS]
        if (csv != null) {
            val ids = csv.split(',').map { it.trim() }.filter { SoundCatalog.byId(it) != null }.toSet()
            if (ids.isNotEmpty()) return ids
        }
        // Migration from the W3 single-sound key, or the default.
        val legacy = p[LEGACY_SOUND]?.let { SoundCatalog.byId(it)?.id }
        return setOf(legacy ?: SoundCatalog.ORIGINAL.id)
    }

    companion object {
        private val VOLUME = intPreferencesKey("volume")
        private val INTERVAL = stringPreferencesKey("interval")
        private val SOUNDS = stringPreferencesKey("sounds")
        private val THEME = stringPreferencesKey("theme")
        private val OWNED = stringPreferencesKey("owned_products")
        private val LEGACY_SOUND = stringPreferencesKey("sound")

        fun fromContext(context: Context): SettingsRepository =
            SettingsRepository(context.problipDataStore)
    }
}
