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

/**
 * MAIN STATS strip display value. Exact and locale-formatted while it plausibly
 * fits a quarter-width cell (< 100K), deterministic compact rounding only for
 * genuinely huge lifetime totals: 1.2K / 14K / 1.3M. Grouping digits would
 * overflow a quarter cell long before 100K.
 */
@Composable
internal fun formatBlipCountForStrip(count: Long): String =
    if (count < 100_000L) formatBlipCount(count) else blipCountCompact(count)

/** Pure compact rounding for values too wide for a quarter-width strip cell (>= 100K). */
internal fun blipCountCompact(count: Long): String = when {
    count < 100_000L -> count.toString()
    count < 1_000_000L -> {
        val tenthsK = count / 100L
        if (tenthsK % 10L == 0L) "${tenthsK / 10L}K"
        else String.format(java.util.Locale.US, "%.1fK", tenthsK / 10.0)
    }
    else -> {
        val tenthsM = count / 100_000L
        if (tenthsM % 10L == 0L) "${tenthsM / 10L}M"
        else String.format(java.util.Locale.US, "%.1fM", tenthsM / 10.0)
    }
}
