package com.vacster.problip.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vacster.problip.R
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Composition-scoped calendar invalidation shared by both statistics surfaces.
 * A one-shot delay handles normal local midnight. Date/time/timezone broadcasts
 * bump the epoch immediately, cancel the stale delay, and schedule the next
 * boundary using the current system zone. The receiver exists only while this
 * composable is alive.
 */
@Composable
internal fun rememberCalendarEpoch(): Int {
    val context = LocalContext.current
    var epoch by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                epoch++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(epoch) {
        delay(durationUntilNextLocalMidnight(ZoneId.systemDefault()).toMillis())
        epoch++
    }
    return epoch
}

/**
 * The compact Main statistics strip: four equal-width metric cells, TODAY /
 * WEEK / MONTH / TOTAL, one strip — information, not four Material cards, not
 * buttons. Monospace, dim labels, stronger values, existing palette tokens
 * only, no icons, no animation. Read-only state: clearAndSetSemantics exposes
 * the exact values as one readable description instead of column positions.
 */
@Composable
internal fun MainStatsStrip(stats: MainStatsValues, modifier: Modifier = Modifier) {
    val values =
        listOf(
            R.string.stats_short_today to stats.today,
            R.string.stats_short_week to stats.week,
            R.string.stats_short_month to stats.month,
            R.string.stats_short_total to stats.total,
        )
    val a11y = stringResource(
        R.string.stats_strip_a11y,
        stringResource(R.string.stats_today) + " " + formatBlipCount(stats.today),
        stringResource(R.string.stats_week) + " " + formatBlipCount(stats.week),
        stringResource(R.string.stats_month) + " " + formatBlipCount(stats.month),
        stringResource(R.string.stats_total) + " " + formatBlipCount(stats.total),
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = a11y },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { (labelRes, count) ->
            StatCell(
                label = stringResource(labelRes),
                value = formatBlipCountForStrip(count),
                modifier = Modifier.weight(1f).clearAndSetSemantics {},
            )
        }
    }
}

/** The four numbers the strip shows, already resolved against current periods. */
internal data class MainStatsValues(
    val today: Long,
    val week: Long,
    val month: Long,
    val total: Long,
)

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = P.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = value,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
