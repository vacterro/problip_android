package com.vacster.problip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.audio.SoundEntry
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.ProblipState
import com.vacster.problip.ui.theme.LocalProblipColors
import kotlin.math.roundToInt

/** Live palette of the active theme (MaterialTheme-style composable getters). */
internal object P {
    val Bg
        @Composable get() = LocalProblipColors.current.Bg
    val Surface
        @Composable get() = LocalProblipColors.current.Surface
    val Raised
        @Composable get() = LocalProblipColors.current.Raised
    val Bevel
        @Composable get() = LocalProblipColors.current.Bevel
    val Gold
        @Composable get() = LocalProblipColors.current.Gold
    val TextMain
        @Composable get() = LocalProblipColors.current.TextMain
    val TextDim
        @Composable get() = LocalProblipColors.current.TextDim
    val Muted
        @Composable get() = LocalProblipColors.current.Muted
    val Compare
        @Composable get() = LocalProblipColors.current.Compare
    val Success
        @Composable get() = LocalProblipColors.current.Success
    val Danger
        @Composable get() = LocalProblipColors.current.Danger
}

/**
 * The utility screen: status, volume, interval, the playable sound pool and
 * START/STOP. No prices and no locked rows live here — the store is secondary
 * (W11), so buying happens on the Sounds and Themes screens.
 */
@Composable
fun ProblipScreen(
    viewModel: ProblipViewModel,
    onStartRequested: () -> Unit,
    onOpenSounds: () -> Unit,
    onOpenThemes: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val owned by viewModel.owned.collectAsState()

    val running = state == ProblipState.STARTING || state == ProblipState.RUNNING
    val unlockedSounds = SoundCatalog.all.filter { it.free || it.id in owned }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(P.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PROBLIP",
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            StatusText(state)
        }

        SectionLabel("VOLUME")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A drag emits ~60 values per second and every one of them used to be
            // a DataStore write. The thumb now moves on local state and the gain
            // is persisted once, on release. Keyed on the persisted value so an
            // external change still re-seeds the slider.
            var dragPercent by remember(settings.volumePercent) {
                mutableIntStateOf(settings.volumePercent)
            }
            Slider(
                value = dragPercent / 100f,
                onValueChange = { dragPercent = (it * 100).roundToInt() },
                onValueChangeFinished = { viewModel.setVolume(dragPercent) },
                modifier = Modifier.weight(1f),
                colors = sliderColors(),
            )
            Text(
                text = "$dragPercent%",
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.width(44.dp),
            )
        }

        SectionLabel("INTERVAL")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IntervalMode.entries.take(4).forEach { mode ->
                PresetButton(
                    label = mode.label(),
                    selected = settings.intervalMode == mode,
                    onClick = { viewModel.setIntervalMode(mode) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IntervalMode.entries.drop(4).forEach { mode ->
                PresetButton(
                    label = mode.label(),
                    selected = settings.intervalMode == mode,
                    onClick = { viewModel.setIntervalMode(mode) },
                )
            }
        }

        SectionLabel("SOUND")
        unlockedSounds.forEach { entry ->
            PoolRow(
                entry = entry,
                checked = entry.id in settings.selectedSounds,
                onToggle = { viewModel.toggleSound(entry.id) },
            )
        }

        NavRow(label = "SOUNDS", onClick = onOpenSounds)
        NavRow(label = "THEMES", onClick = onOpenThemes)

        Spacer(modifier = Modifier.weight(1f))

        error?.let {
            Text(
                text = it,
                color = P.Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }

        BigButton(
            text = if (running) "STOP" else "START",
            onClick = { if (running) viewModel.stop() else onStartRequested() },
        )
    }
}

private fun IntervalMode.label(): String = when (this) {
    IntervalMode.RANDOM_4_7 -> "4-7"
    IntervalMode.FIXED_5S -> "5"
    IntervalMode.FIXED_10S -> "10"
    IntervalMode.FIXED_15S -> "15"
    IntervalMode.FIXED_20S -> "20"
    IntervalMode.FIXED_30S -> "30"
}

@Composable
private fun StatusText(state: ProblipState) {
    val (text, color) = when (state) {
        ProblipState.STARTING, ProblipState.RUNNING -> "RUNNING" to P.Success
        ProblipState.ERROR -> "ERROR" to P.Danger
        ProblipState.STOPPED -> "OFF" to P.Muted
    }
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = P.TextDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun sliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = P.Gold,
    activeTrackColor = P.Gold,
    inactiveTrackColor = P.Raised,
)

@Composable
private fun PresetButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) P.Compare else P.Raised)
            .border(1.dp, P.Bevel)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) P.Gold else P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

/** Pool membership toggle. Only unlocked sounds reach this row. */
@Composable
private fun PoolRow(entry: SoundEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            color = P.Gold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.displayName,
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

/** Entry point to a secondary screen; carries no price or ownership text. */
@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(P.Surface)
            .border(1.dp, P.Bevel)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = ">",
            color = P.Gold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun BigButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(P.Raised)
            .border(1.dp, P.Bevel)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = P.Gold,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 2.sp,
        )
    }
}
