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

object ProblipNotification {
    const val CHANNEL_ID = "problip_active"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a Problip session is running"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        settings: SettingsRepository.Settings,
        owned: Set<String> = emptySet(),
        trials: Set<String> = emptySet(),
    ): Notification {
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
            .setContentTitle("Problip running")
            .setContentText(
                describe(
                    settings.intervalMode,
                    soundLabel(settings.selectedSounds, owned, trials),
                    settings.volumePercent,
                ),
            )
            .setContentIntent(openIntent)
            .addAction(0, "STOP", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Display name for a single-sound pool, "Random pool" otherwise. Pure, unit-tested. */
    fun soundLabel(
        selectedSounds: Set<String>,
        owned: Set<String> = emptySet(),
        trials: Set<String> = emptySet(),
    ): String {
        val playable = SoundCatalog.playableSelection(selectedSounds, owned, trials)
        return when (playable.size) {
            1 -> SoundCatalog.byId(playable.first())?.displayName ?: "Blip"
            else -> "Random pool"
        }
    }

    /**
     * Second notification line, e.g. "Random 4–7 sec • Original Blip • 5%".
     * Pure function; unit-tested.
     */
    fun describe(mode: IntervalMode, soundLabel: String, volumePercent: Int): String {
        val interval = when (mode) {
            IntervalMode.RANDOM_4_7 -> "Random 4–7 sec"
            IntervalMode.FIXED_5S -> "Every 5 sec"
            IntervalMode.FIXED_10S -> "Every 10 sec"
            IntervalMode.FIXED_15S -> "Every 15 sec"
            IntervalMode.FIXED_20S -> "Every 20 sec"
            IntervalMode.FIXED_30S -> "Every 30 sec"
            IntervalMode.PULSE -> "Pulse 5 / 10–20 sec"
            IntervalMode.MANUAL -> "Manual"
        }
        return "$interval • $soundLabel • $volumePercent%"
    }
}
