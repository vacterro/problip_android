package com.vacster.problip.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.PremiumInterval
import com.vacster.problip.core.ProblipState
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.trial.TrialAccess
import com.vacster.problip.ui.theme.LocalProblipColors
import com.vacster.problip.ui.theme.readableOn
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
        @Composable
        get() = LocalProblipColors.current.let { readableOn(it.Gold, it.TextMain, it.Bg) }

    val ActionText
        @Composable
        get() = LocalProblipColors.current.let { readableOn(it.Gold, it.TextMain, it.Raised) }

    val Edge
        @Composable
        get() = LocalProblipColors.current.let { readableOn(it.Bevel, it.TextDim, it.Raised, 3f) }

    val TextMain
        @Composable get() = LocalProblipColors.current.TextMain

    val TextDim
        @Composable
        get() = LocalProblipColors.current.let { readableOn(it.TextDim, it.TextMain, it.Surface) }

    val Muted
        @Composable get() = LocalProblipColors.current.Muted

    val Compare
        @Composable get() = LocalProblipColors.current.Compare

    val Success
        @Composable get() = LocalProblipColors.current.Success

    val Danger
        @Composable get() = LocalProblipColors.current.Danger
}

/** Repositions the same three groups for short, wide viewports; no duplicate controls. */
@Composable
private fun MainControlLayout(
    modifier: Modifier,
    sideBySide: Boolean,
    narrowLandscape: Boolean,
    gap: Dp,
    volume: @Composable () -> Unit,
    interval: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { volume() }
            Box { interval() }
            Box { secondary() }
        },
    ) { measurables, constraints ->
        val spacing = gap.roundToPx()
        val intervalWidth =
            if (sideBySide) {
                ((constraints.maxWidth - spacing) * if (narrowLandscape) 0.55f else 0.5f)
                    .roundToInt()
            } else constraints.maxWidth
        val secondaryWidth =
            if (sideBySide) constraints.maxWidth - spacing - intervalWidth else constraints.maxWidth
        val (volumePlaceable, intervalPlaceable, secondaryPlaceable) =
            measurables.mapIndexed { index, measurable ->
                measurable.measure(
                    Constraints(maxWidth = if (index == 1) intervalWidth else secondaryWidth)
                )
            }
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (sideBySide) {
                intervalPlaceable.placeRelative(0, 0)
                volumePlaceable.placeRelative(intervalWidth + spacing, 0)
                secondaryPlaceable.placeRelative(
                    intervalWidth + spacing,
                    volumePlaceable.height + spacing,
                )
            } else {
                volumePlaceable.placeRelative(0, 0)
                intervalPlaceable.placeRelative(0, volumePlaceable.height + spacing)
                secondaryPlaceable.placeRelative(
                    0,
                    volumePlaceable.height + intervalPlaceable.height + 2 * spacing,
                )
            }
        }
    }
}

/**
 * The utility screen: status, volume, interval, the playable sound pool and START/STOP. No prices
 * and no locked rows live here — the store is secondary (W11), so buying happens on the Sounds and
 * Themes screens.
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
    // Summarize the EFFECTIVE pool, including the Original-Blip fallback after
    // trial expiry. Selection and the full catalog live on the Sounds screen.
    val effectivePool =
        SoundCatalog.playableSelection(settings.selectedSounds, owned, access.grantedIds)
    val now = rememberTrialNow(enabled = access.activeTrials.isNotEmpty())
    val manualAccessible = access.grantsManualInterval(viewModel.ownsThemePack())
    val pulseAccessible = access.grantsPulseInterval(viewModel.ownsThemePack())
    // A premium pick whose trial died shows as the free preset, matching what the
    // scheduler is actually running.
    val effectiveMode =
        PremiumInterval.effectiveMode(
            mode = settings.intervalMode,
            manualAccessible = manualAccessible,
            pulseAccessible = pulseAccessible,
        )

    // Hidden Developer Access chord. Deliberately not persisted: rotation or
    // process recreation cancels it.
    val gesture = remember { DeveloperGesture() }
    val gestureScope = rememberCoroutineScope()
    var developerUnlocked by remember { mutableStateOf(false) }
    val feedback = clickFeedback()
    val view = LocalView.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(P.Bg)) {
        val compact = maxHeight < 560.dp || LocalDensity.current.fontScale > 1.15f
        val narrowLandscape = maxWidth in 420.dp..<520.dp && maxWidth > maxHeight
        val sideBySide = (maxWidth >= 520.dp || narrowLandscape) && maxHeight < 420.dp
        val gap = if (compact) 3.dp else 8.dp
        val presetHeight = if (compact) 36.dp else 44.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 6.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PROBLIP",
                    color = P.Gold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    // No visible hint, no long-press menu: the title is just a title
                    // until it has been held for twenty seconds.
                    modifier =
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    gesture.titlePressed()
                                    val arming =
                                        gestureScope.launch {
                                            delay(DeveloperGesture.HOLD_MILLIS)
                                            gesture.titleHeldFor(DeveloperGesture.HOLD_MILLIS)
                                        }
                                    // Keeps observing this pointer while the timer runs;
                                    // release AND cancellation both disarm.
                                    tryAwaitRelease()
                                    arming.cancel()
                                    gesture.titleReleased()
                                }
                            )
                        },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (access.developerAccess) {
                        Text(
                            text = DEVELOPER_LABEL,
                            color = P.Gold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )
                    }
                    StatusText(state)
                }
            }

            // This weighted body is measured AFTER the header and action area. Long
            // error messages and optional controls can never displace START/STOP.
            MainControlLayout(
                // Guard the action area even if an IME or an exceptionally small
                // multi-window viewport leaves less than the minimum body size.
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                sideBySide = sideBySide,
                narrowLandscape = narrowLandscape,
                gap = gap,
                volume = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(if (narrowLandscape) "VOL" else "VOLUME")
                        // A drag emits ~60 values per second and every one of them used to be
                        // a DataStore write. The thumb now moves on local state and the gain
                        // is persisted once, on release. Keyed on the persisted value so an
                        // external change still re-seeds the slider.
                        var dragPercent by
                            remember(settings.volumePercent) {
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
                            lineHeight = 18.sp,
                            maxLines = 1,
                        )
                    }
                },
                interval = {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionLabel("INTERVAL")
                            val featureId =
                                when (effectiveMode) {
                                    IntervalMode.PULSE -> TrialAccess.FEATURE_PULSE_INTERVAL
                                    IntervalMode.MANUAL -> TrialAccess.FEATURE_MANUAL_INTERVAL
                                    else -> null
                                }
                            if (featureId != null) {
                                IntervalTrialLabel(
                                    featureId = featureId,
                                    owned = viewModel.ownsThemePack(),
                                    developerAccess = access.developerAccess,
                                    trialExpiries = trialExpiries,
                                    nowMillis = now,
                                )
                            }
                        }
                        IntervalMode.entries.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { mode ->
                                    PresetButton(
                                        label = mode.label(),
                                        selected = effectiveMode == mode,
                                        onClick = { viewModel.setIntervalMode(mode) },
                                        modifier = Modifier.weight(1f).height(presetHeight),
                                        narrow = narrowLandscape,
                                    )
                                }
                            }
                        }
                        // Reserve one detail row for every mode: MANUAL, trial expiry and PULSE
                        // do not move either the sound selector or the primary actions.
                        Box(
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (effectiveMode == IntervalMode.PULSE) {
                                Text(
                                    text = "5s / 10-20s",
                                    color = P.TextDim,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                )
                            }
                            if (effectiveMode == IntervalMode.MANUAL) {
                                ManualIntervalEditor(
                                    fromSeconds = settings.manualFromSeconds,
                                    toSeconds = settings.manualToSeconds,
                                    onCommit = viewModel::setManualInterval,
                                )
                            }
                        }
                    }
                },
                secondary = {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        val premiumSounds =
                            effectivePool.mapNotNull(SoundCatalog::byId).filterNot { it.free }
                        // A mixed pool shows its earliest active trial; per-sound
                        // ownership and timers remain available in Sounds.
                        val firstExpiry =
                            premiumSounds
                                .mapNotNull { entry ->
                                    trialExpiries[entry.id]?.takeIf {
                                        entry.id !in owned && it > now
                                    }
                                }
                                .minOrNull()
                        SoundSummaryRow(
                            summary =
                                if (effectivePool.size == 1) {
                                    SoundCatalog.byId(effectivePool.first())!!.displayName
                                } else {
                                    if (narrowLandscape) "${effectivePool.size} sounds"
                                    else "${effectivePool.size} sounds selected"
                                },
                            label =
                                trialLabel(
                                    free = premiumSounds.isEmpty(),
                                    owned = premiumSounds.all { it.id in owned },
                                    developerAccess = access.developerAccess,
                                    expiryMillis = firstExpiry,
                                    nowMillis = now,
                                ),
                            onClick = onOpenSounds,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            NavRow(
                                if (narrowLandscape) "THEME" else "THEMES",
                                onOpenThemes,
                                Modifier.weight(1f).semantics { contentDescription = "THEMES" },
                            )
                            NavRow(
                                if (narrowLandscape) "SET" else "SETTINGS",
                                onOpenSettings,
                                Modifier.weight(1f).semantics { contentDescription = "SETTINGS" },
                            )
                        }
                    }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Always reserve one line so an error cannot cause a layout jump.
                Text(
                    text = error.orEmpty(),
                    color = P.Danger,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MinimizeButton(onClick = onMinimize, modifier = Modifier.weight(1f))
                    BigButton(
                        text = if (running) "STOP" else "START",
                        modifier = Modifier.weight(1.3f),
                        onClick = {
                            // The armed chord consumes this tap: it must neither start nor
                            // stop the session.
                            if (gesture.consumeIfArmedAndTitleStillHeld()) {
                                viewModel.enableDeveloperAccess()
                                developerUnlocked = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else if (running) {
                                feedback()
                                viewModel.stop()
                            } else {
                                feedback()
                                onStartRequested()
                            }
                        },
                    )
                }
            }
        }
    }

    if (developerUnlocked) {
        DeveloperUnlockedDialog(onDismiss = { developerUnlocked = false })
    }
}

/**
 * Access label beside the interval heading: "TRIAL 04:37" while its five minutes run, "TRY 5 MIN"
 * when a tap would start them, "OWNED" once the Customization Pack is bought, "DEV" while Developer
 * Access grants it.
 *
 * [owned] and [developerAccess] stay separate: neither has a timer, so neither may advertise a
 * trial, but only a purchase may claim ownership.
 */
@Composable
private fun IntervalTrialLabel(
    featureId: String,
    owned: Boolean,
    developerAccess: Boolean,
    trialExpiries: Map<String, Long>,
    nowMillis: Long,
) {
    val label =
        trialLabel(
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
        lineHeight = 12.sp,
        letterSpacing = 1.sp,
        maxLines = 1,
    )
}

/** FROM/TO seconds for the premium manual interval. Only validated values persist. */
@Composable
private fun ManualIntervalEditor(fromSeconds: Int, toSeconds: Int, onCommit: (Int, Int) -> Unit) {
    // Keyed on the persisted values so an external change re-seeds the fields.
    var fromText by remember(fromSeconds) { mutableStateOf(fromSeconds.toString()) }
    var toText by remember(toSeconds) { mutableStateOf(toSeconds.toString()) }
    // Blank or mid-edit text is allowed on screen; the last valid value is what
    // gets written, and the repository clamps and orders the pair.
    val commit = {
        onCommit(fromText.toIntOrNull() ?: fromSeconds, toText.toIntOrNull() ?: toSeconds)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            lineHeight = 14.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                TextStyle(
                    color = P.TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
            cursorBrush = SolidColor(P.Gold),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        onCommit()
                        focus.clearFocus()
                    }
                ),
            modifier =
                Modifier.width(56.dp)
                    .background(P.Raised)
                    .border(1.dp, P.Edge)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .onFocusChanged { if (!it.isFocused) onCommit() },
        )
        Text(
            text = "s",
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
}

/** Plain palette modal: no Material celebration, no animation, no network. */
@Composable
private fun DeveloperUnlockedDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(P.Surface).border(1.dp, P.Edge).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "DEVELOPER ACCESS UNLOCKED",
                color = P.ActionText,
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

private fun IntervalMode.label(): String =
    when (this) {
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
    val owner = LocalLifecycleOwner.current
    var lit by remember { mutableStateOf(false) }
    val active = state == ProblipState.STARTING || state == ProblipState.RUNNING
    LaunchedEffect(owner, active) {
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ProblipSession.blips.collectLatest {
                lit = true
                try {
                    delay(140)
                } finally {
                    lit = false
                }
            }
        }
    }
    val (text, color) =
        when (state) {
            ProblipState.STARTING -> "STARTING" to P.TextDim
            ProblipState.RUNNING -> "RUNNING" to P.TextMain
            ProblipState.ERROR -> "ERROR" to P.Danger
            ProblipState.STOPPED -> "OFF" to P.TextDim
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative lamp; status text remains the accessible description. No live
        // region announcement on every blip, and no replay when returning to Main.
        Box(Modifier.size(6.dp).background(if (lit) P.Gold else P.Bg).border(1.dp, P.Edge))
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = P.TextDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun sliderColors(): SliderColors =
    SliderDefaults.colors(
        thumbColor = P.Gold,
        activeTrackColor = P.Gold,
        inactiveTrackColor = P.Edge,
    )

@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    narrow: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedback = clickFeedback()
    Box(
        modifier =
            modifier
                .background(if (selected || pressed) P.Compare else P.Raised)
                .border(
                    if (selected || pressed) 2.dp else 1.dp,
                    if (selected || pressed) P.ActionText else P.Edge,
                )
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.RadioButton,
                ) {
                    if (!selected) feedback()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) P.ActionText else P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (narrow) 11.sp else 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
        )
    }
}

/**
 * One compact entry point to the sound pool. Access text has its own fixed line beside SOUND, so a
 * countdown never squeezes the selected-pool summary.
 */
@Composable
private fun SoundSummaryRow(summary: String, label: String?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .clickable { onClick() }
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionLabel("SOUND")
                if (label != null) {
                    Text(
                        text = label,
                        color = P.ActionText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = summary,
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = ">",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Entry point to a secondary screen; carries no price or ownership text. */
@Composable
private fun NavRow(label: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier =
            modifier
                .height(36.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .clickable { onClick() }
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = ">",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
    }
}

/**
 * Secondary action: obvious and next to START/STOP, never hidden in a menu, and always spelled out
 * because an icon alone is ambiguous. Deliberately quieter than [BigButton] so the primary action
 * stays primary.
 */
@Composable
private fun MinimizeButton(onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val feedback = clickFeedback()
    Box(
        modifier =
            modifier
                .height(48.dp)
                .background(if (pressed) P.Compare else P.Surface)
                .border(if (pressed) 2.dp else 1.dp, P.Edge)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    feedback()
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MINIMIZE",
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(if (pressed) P.Compare else P.Raised)
                .border(if (pressed) 3.dp else 2.dp, P.ActionText)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = 22.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

/** Standard view feedback respects the user's system setting; no vibration permission. */
@Composable
internal fun clickFeedback(): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
}
