package com.vacster.problip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.audio.SoundEntry
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.PremiumInterval
import com.vacster.problip.core.ProblipState
import com.vacster.problip.trial.TrialAccess
import com.vacster.problip.ui.theme.LocalProblipColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onOpenSettings: () -> Unit,
    onMinimize: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val access by viewModel.access.collectAsState()
    val trialExpiries by viewModel.trialExpiries.collectAsState()

    val running = state == ProblipState.STARTING || state == ProblipState.RUNNING
    // The whole catalog is visible: tapping a locked sound starts its trial. The
    // checkmarks follow the EFFECTIVE pool, so an expired trial visibly drops out
    // and the Original-Blip fallback is what the user sees selected.
    val effectivePool = SoundCatalog.playableSelection(settings.selectedSounds, owned, access.grantedIds)
    val now = rememberTrialNow(enabled = access.activeTrials.isNotEmpty())
    val manualAccessible = access.grantsManualInterval(viewModel.ownsThemePack())
    val pulseAccessible = access.grantsPulseInterval(viewModel.ownsThemePack())
    // A premium pick whose trial died shows as the free preset, matching what the
    // scheduler is actually running.
    val effectiveMode = PremiumInterval.effectiveMode(
        mode = settings.intervalMode,
        manualAccessible = manualAccessible,
        pulseAccessible = pulseAccessible,
    )

    // Hidden Developer Access chord. Deliberately not persisted: rotation or
    // process recreation cancels it.
    val gesture = remember { DeveloperGesture() }
    val gestureScope = rememberCoroutineScope()
    var developerUnlocked by remember { mutableStateOf(false) }

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
                // No visible hint, no long-press menu: the title is just a title
                // until it has been held for twenty seconds.
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            gesture.titlePressed()
                            val arming = gestureScope.launch {
                                delay(DeveloperGesture.HOLD_MILLIS)
                                gesture.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
                            }
                            // Keeps observing this pointer while the timer runs;
                            // release AND cancellation both disarm.
                            tryAwaitRelease()
                            arming.cancel()
                            gesture.titleReleased()
                        },
                    )
                },
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
        // [4-7] [5] [10] [15] / [20] [30] [PULSE] / [MANUAL]
        listOf(
            IntervalMode.entries.take(4),
            IntervalMode.entries.drop(4).take(3),
            IntervalMode.entries.drop(7),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { mode ->
                    PresetButton(
                        label = mode.label(),
                        selected = effectiveMode == mode,
                        onClick = { viewModel.setIntervalMode(mode) },
                    )
                }
            }
        }
        if (effectiveMode == IntervalMode.PULSE) {
            Text(
                text = "5s / 10-20s",
                color = P.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            IntervalTrialLabel(
                featureId = TrialAccess.FEATURE_PULSE_INTERVAL,
                owned = viewModel.ownsThemePack(),
                developerAccess = access.developerAccess,
                trialExpiries = trialExpiries,
                nowMillis = now,
            )
        }
        if (effectiveMode == IntervalMode.MANUAL) {
            ManualIntervalEditor(
                fromSeconds = settings.manualFromSeconds,
                toSeconds = settings.manualToSeconds,
                onCommit = viewModel::setManualInterval,
            )
            IntervalTrialLabel(
                featureId = TrialAccess.FEATURE_MANUAL_INTERVAL,
                owned = viewModel.ownsThemePack(),
                developerAccess = access.developerAccess,
                trialExpiries = trialExpiries,
                nowMillis = now,
            )
        }

        SectionLabel("SOUND")
        SoundCatalog.all.forEach { entry ->
            PoolRow(
                entry = entry,
                checked = entry.id in effectivePool,
                label = trialLabel(
                    free = entry.free,
                    owned = entry.id in owned,
                    developerAccess = access.developerAccess,
                    expiryMillis = trialExpiries[entry.id],
                    nowMillis = now,
                ),
                onToggle = { viewModel.toggleSound(entry.id) },
            )
        }

        NavRow(label = "SOUNDS", onClick = onOpenSounds)
        NavRow(label = "THEMES", onClick = onOpenThemes)
        NavRow(label = "SETTINGS", onClick = onOpenSettings)

        Spacer(modifier = Modifier.weight(1f))

        error?.let {
            Text(
                text = it,
                color = P.Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }

        // Backgrounds the app without stopping the session; the primary action
        // stays START/STOP directly below it.
        MinimizeButton(onClick = onMinimize)

        BigButton(
            text = if (running) "STOP" else "START",
            onClick = {
                // The armed chord consumes this tap: it must neither start nor
                // stop the session.
                if (gesture.consumeIfArmedAndTitleStillHeld()) {
                    viewModel.enableDeveloperAccess()
                    developerUnlocked = true
                } else if (running) {
                    viewModel.stop()
                } else {
                    onStartRequested()
                }
            },
        )
    }

    if (developerUnlocked) {
        DeveloperUnlockedDialog(onDismiss = { developerUnlocked = false })
    }
}

/**
 * Access label under a premium interval preset: "TRIAL 04:37" while its five
 * minutes run, "TRY 5 MIN" when a tap would start them, "OWNED" once the
 * Customization Pack is bought, "DEV" while Developer Access grants it.
 *
 * [owned] and [developerAccess] stay separate: neither has a timer, so neither
 * may advertise a trial, but only a purchase may claim ownership.
 */
@Composable
private fun IntervalTrialLabel(
    featureId: String,
    owned: Boolean,
    developerAccess: Boolean,
    trialExpiries: Map<String, Long>,
    nowMillis: Long,
) {
    val label = trialLabel(
        free = false,
        owned = owned,
        developerAccess = developerAccess,
        expiryMillis = trialExpiries[featureId],
        nowMillis = nowMillis,
    ) ?: return
    Text(
        text = label,
        color = P.Gold,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    )
}

/** FROM/TO seconds for the premium manual interval. Only validated values persist. */
@Composable
private fun ManualIntervalEditor(
    fromSeconds: Int,
    toSeconds: Int,
    onCommit: (Int, Int) -> Unit,
) {
    // Keyed on the persisted values so an external change re-seeds the fields.
    var fromText by remember(fromSeconds) { mutableStateOf(fromSeconds.toString()) }
    var toText by remember(toSeconds) { mutableStateOf(toSeconds.toString()) }
    // Blank or mid-edit text is allowed on screen; the last valid value is what
    // gets written, and the repository clamps and orders the pair.
    val commit = {
        onCommit(fromText.toIntOrNull() ?: fromSeconds, toText.toIntOrNull() ?: toSeconds)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondsField(
            label = "FROM",
            value = fromText,
            onValueChange = { fromText = it.filter(Char::isDigit).take(4) },
            onCommit = commit,
        )
        SecondsField(
            label = "TO",
            value = toText,
            onValueChange = { toText = it.filter(Char::isDigit).take(4) },
            onCommit = commit,
        )
    }
}

/** Numeric keyboard, committed on IME done or focus loss — never per keystroke. */
@Composable
private fun SecondsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            ),
            cursorBrush = SolidColor(P.Gold),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onCommit()
                    focus.clearFocus()
                },
            ),
            modifier = Modifier
                .width(56.dp)
                .background(P.Raised)
                .border(1.dp, P.Bevel)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .onFocusChanged { if (!it.isFocused) onCommit() },
        )
        Text(
            text = "s",
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

/** Plain palette modal: no Material celebration, no animation, no network. */
@Composable
private fun DeveloperUnlockedDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(P.Surface)
                .border(1.dp, P.Bevel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "DEVELOPER ACCESS UNLOCKED",
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = "7 DAYS OF PREMIUM ACCESS",
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            BigButton(text = "OK", onClick = onDismiss)
        }
    }
}

private fun IntervalMode.label(): String = when (this) {
    IntervalMode.RANDOM_4_7 -> "4-7"
    IntervalMode.FIXED_5S -> "5"
    IntervalMode.FIXED_10S -> "10"
    IntervalMode.FIXED_15S -> "15"
    IntervalMode.FIXED_20S -> "20"
    IntervalMode.FIXED_30S -> "30"
    IntervalMode.PULSE -> "PULSE"
    IntervalMode.MANUAL -> "MANUAL"
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

/**
 * Pool membership toggle for one catalog sound. [label] carries the access state
 * (OWNED / TRIAL mm:ss / TRY 5 MIN); tapping a locked row starts its trial.
 */
@Composable
private fun PoolRow(
    entry: SoundEntry,
    checked: Boolean,
    label: String?,
    onToggle: () -> Unit,
) {
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
            color = if (checked) P.TextMain else P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        if (label != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = label,
                color = if (label == "OWNED") P.Success else P.Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }
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

/**
 * Secondary action: obvious and next to START/STOP, never hidden in a menu, and
 * always spelled out because an icon alone is ambiguous. Deliberately quieter
 * than [BigButton] so the primary action stays primary.
 */
@Composable
private fun MinimizeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(P.Surface)
            .border(1.dp, P.Bevel)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MINIMIZE",
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
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
