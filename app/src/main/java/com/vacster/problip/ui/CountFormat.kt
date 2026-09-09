package com.vacster.problip.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.util.Locale

/**
 * The locale for all count formatting: the CURRENT app Configuration — the
 * per-app language the user picked inside Problip — not the JVM default, so
 * digits/grouping/decimal punctuation follow the selected app language and
 * recompose when it changes.
 */
@Composable
private fun currentAppLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: Locale.getDefault()

/** Locale-aware exact count for the BLIPS line, Settings rows and a11y descriptions. Grouping stays on: 73412 reads "73,412". */
@Composable
internal fun formatBlipCount(count: Long): String =
    formatBlipCountExact(count, currentAppLocale())

/**
 * MAIN STATS strip display value. Exact and locale-formatted while it
 * plausibly fits a quarter-width cell (< 100K), deterministic compact
 * rounding only for genuinely huge lifetime totals: 1.2K / 14K / 1.3M.
 * Grouping digits would overflow a quarter cell long before 100K.
 */
@Composable
internal fun formatBlipCountForStrip(count: Long): String =
    formatBlipCountForStrip(count, currentAppLocale())

internal fun formatBlipCountExact(count: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(count)

internal fun formatBlipCountForStrip(count: Long, locale: Locale): String =
    if (count < 100_000L) formatBlipCountExact(count, locale)
    else blipCountCompact(count, locale)

/** Pure compact rounding for values too wide for a quarter-width strip cell (>= 100K); decimal punctuation follows the injected locale. */
internal fun blipCountCompact(count: Long, locale: Locale): String = when {
    count < 100_000L -> count.toString()
    count < 1_000_000L -> {
        val tenthsK = count / 100L
        if (tenthsK % 10L == 0L) "${tenthsK / 10L}K"
        else String.format(locale, "%.1fK", tenthsK / 10.0)
    }
    else -> {
        val tenthsM = count / 100_000L
        if (tenthsM % 10L == 0L) "${tenthsM / 10L}M"
        else String.format(locale, "%.1fM", tenthsM / 10.0)
    }
}
