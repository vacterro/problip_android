package com.vacster.problip.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.audio.Volume
import com.vacster.problip.billing.ProductCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.ManualInterval
import com.vacster.problip.theme.ThemeCatalog
import com.vacster.problip.trial.TrialAccess
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
        /** Premium content id -> wall-clock trial expiry, survives process death. */
        val trialExpiries: Map<String, Long> = emptyMap(),
        /** Wall-clock end of the hidden seven-day Developer Access; 0 = never unlocked. */
        val developerAccessExpiryMillis: Long = 0L,
        /** Premium Manual Interval bounds, remembered across trials and purchases. */
        val manualFromSeconds: Int = ManualInterval.DEFAULT_FROM_SECONDS,
        val manualToSeconds: Int = ManualInterval.DEFAULT_TO_SECONDS,
        /**
         * Slow Main-screen visibility preference for the blip counter. It never
         * touches recording: the count is kept even while hidden.
         */
        val showBlipCounter: Boolean = true,
        /**
         * Slow preference for the premium Blip Glow effect. ON means "show the
         * effect when access permits" — it grants nothing by itself.
         */
        val blipGlowEnabled: Boolean = true,
    )

    val settings: Flow<Settings> = dataStore.data
        .catch { e ->
            // Unreadable store falls back to defaults instead of crashing the app.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            val manual = manualBounds(p)
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
                trialExpiries = parseTrials(p[TRIALS]),
                developerAccessExpiryMillis = p[DEVELOPER_EXPIRY]?.coerceAtLeast(0L) ?: 0L,
                manualFromSeconds = manual.first,
                manualToSeconds = manual.second,
                showBlipCounter = p[SHOW_COUNTER] ?: true,
                blipGlowEnabled = p[BLIP_GLOW] ?: true,
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

    /**
     * Read-modify-write of the trial expiries inside one atomic store edit, so two
     * rapid taps cannot lose each other's trial. Only trialable ids are stored.
     */
    suspend fun updateTrialExpiries(transform: (Map<String, Long>) -> Map<String, Long>) {
        dataStore.edit { prefs ->
            prefs[TRIALS] = encodeTrials(transform(parseTrials(prefs[TRIALS])))
        }
    }

    /** Absolute wall-clock end of Developer Access; survives process death and reboot. */
    suspend fun setDeveloperAccessExpiry(expiryMillis: Long) {
        dataStore.edit { it[DEVELOPER_EXPIRY] = expiryMillis.coerceAtLeast(0L) }
    }

    /** Validated bounds only; the scheduler must never see FROM > TO or 0 seconds. */
    suspend fun setManualInterval(fromSeconds: Int, toSeconds: Int) {
        val (from, to) = ManualInterval.sanitize(fromSeconds, toSeconds)
        dataStore.edit {
            it[MANUAL_FROM] = from
            it[MANUAL_TO] = to
        }
    }

    /** Main-screen visibility of the blip counter; recording is unaffected. */
    suspend fun setShowBlipCounter(show: Boolean) {
        dataStore.edit { it[SHOW_COUNTER] = show }
    }

    /** Blip Glow preference; access is decided elsewhere, never here. */
    suspend fun setBlipGlowEnabled(enabled: Boolean) {
        dataStore.edit { it[BLIP_GLOW] = enabled }
    }

    /**
     * Drops both temporary grants in ONE edit, so access recomputes once instead of
     * flickering through a half-cleared state. Purchases, volume, interval, pool,
     * theme, the counter/glow preferences are deliberately left alone: this returns
     * the app to free + owned. Lifetime statistics live in their own store and are
     * never reset — they back the permanent 100K Premium reward.
     */
    suspend fun clearTemporaryAccess() {
        dataStore.edit { prefs ->
            prefs.remove(TRIALS)
            prefs.remove(DEVELOPER_EXPIRY)
        }
    }

    private fun manualBounds(p: Preferences): Pair<Int, Int> = ManualInterval.sanitize(
        p[MANUAL_FROM] ?: ManualInterval.DEFAULT_FROM_SECONDS,
        p[MANUAL_TO] ?: ManualInterval.DEFAULT_TO_SECONDS,
    )

    private fun parseTrials(csv: String?): Map<String, Long> {
        if (csv.isNullOrBlank()) return emptyMap()
        val parsed = csv.split(',').mapNotNull { part ->
            val id = part.substringBefore(':').trim()
            val millis = part.substringAfter(':', "").trim().toLongOrNull() ?: return@mapNotNull null
            id to millis
        }.toMap()
        return TrialAccess.sanitize(parsed)
    }

    private fun encodeTrials(expiries: Map<String, Long>): String =
        TrialAccess.sanitize(expiries)
            .entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }

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
        private val TRIALS = stringPreferencesKey("trial_expiries")
        private val DEVELOPER_EXPIRY = longPreferencesKey("developer_access_expiry")
        private val MANUAL_FROM = intPreferencesKey("manual_from_seconds")
        private val MANUAL_TO = intPreferencesKey("manual_to_seconds")
        private val LEGACY_SOUND = stringPreferencesKey("sound")
        private val SHOW_COUNTER = booleanPreferencesKey("show_blip_counter")
        private val BLIP_GLOW = booleanPreferencesKey("blip_glow_enabled")

        fun fromContext(context: Context): SettingsRepository =
            SettingsRepository(context.problipDataStore)
    }
}
