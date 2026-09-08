package com.vacster.problip.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware count for the BLIPS line and the reward progress.
 *
 * The locale comes from the CURRENT app Configuration — the per-app language the
 * user picked inside Problip — not from the JVM default, so digits/grouping
 * follow the selected app language and recompose when it changes. Grouping stays
 * on: 73412 reads "73,412".
 */
@Composable
internal fun formatBlipCount(count: Long): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: Locale.getDefault()
    return NumberFormat.getIntegerInstance(locale).format(count)
}
