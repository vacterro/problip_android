package com.vacster.problip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vacster.problip.MainActivity
import com.vacster.problip.R
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.settings.SettingsRepository
import com.vacster.problip.withAppLocale

object ProblipNotification {
    const val CHANNEL_ID = "problip_active"
    const val NOTIFICATION_ID = 1

    /**
     * Creating a channel with an existing id updates its user-visible name and
     * description while keeping importance, so calling this on every service
     * start refreshes the channel into the selected locale. One channel, never
     * one per language.
     */
    fun ensureChannel(context: Context) {
        val localized = context.withAppLocale()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localized.getString(R.string.notif_channel_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        settings: SettingsRepository.Settings,
        owned: Set<String> = emptySet(),
        trials: Set<String> = emptySet(),
    ): Notification {
        val localized = context.withAppLocale()
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, ProblipService::class.java).setAction(ProblipService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_problip)
            .setContentTitle(localized.getString(R.string.notif_title))
            .setContentText(
                describe(
                    localized.getString(intervalLabelRes(settings.intervalMode)),
                    soundLabel(
                        settings.selectedSounds,
                        owned,
                        trials,
                        poolLabel = localized.getString(R.string.notif_pool_random),
                        fallbackLabel = localized.getString(R.string.notif_sound_fallback),
                    ),
                    settings.volumePercent,
                ),
            )
            .setContentIntent(openIntent)
            .addAction(0, localized.getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Display name for a single-sound pool, the localized pool label otherwise.
     * Sound display names are canonical product names and stay untranslated;
     * the two generic labels are passed in so this stays pure and unit-tested.
     */
    fun soundLabel(
        selectedSounds: Set<String>,
        owned: Set<String> = emptySet(),
        trials: Set<String> = emptySet(),
        poolLabel: String,
        fallbackLabel: String,
    ): String {
        val playable = SoundCatalog.playableSelection(selectedSounds, owned, trials)
        return when (playable.size) {
            1 -> SoundCatalog.byId(playable.first())?.displayName ?: fallbackLabel
            else -> poolLabel
        }
    }

    /**
     * Second notification line, e.g. "Random 4–7 sec • Original Blip • 5%".
     * Pure function; unit-tested. The interval text is resolved by the caller
     * through [intervalLabelRes] so it follows the locale.
     */
    fun describe(intervalLabel: String, soundLabel: String, volumePercent: Int): String =
        "$intervalLabel • $soundLabel • $volumePercent%"

    /** Localized interval description for the EFFECTIVE mode; pure, unit-tested. */
    fun intervalLabelRes(mode: IntervalMode): Int = when (mode) {
        IntervalMode.RANDOM_4_7 -> R.string.notif_interval_random
        IntervalMode.FIXED_5S -> R.string.notif_interval_5s
        IntervalMode.FIXED_10S -> R.string.notif_interval_10s
        IntervalMode.FIXED_15S -> R.string.notif_interval_15s
        IntervalMode.FIXED_20S -> R.string.notif_interval_20s
        IntervalMode.FIXED_30S -> R.string.notif_interval_30s
        IntervalMode.PULSE -> R.string.notif_interval_pulse
        IntervalMode.MANUAL -> R.string.notif_interval_manual
    }
}
