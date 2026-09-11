package com.vacster.problip.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.vacster.problip.R
import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.core.IntervalMode
import com.vacster.problip.core.PremiumInterval
import com.vacster.problip.core.ProblipState
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.settings.BlipCounterMode
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
    onExit: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val access by viewModel.access.collectAsState()
    val trialExpiries by viewModel.trialExpiries.collectAsState()
    val statsRecord by viewModel.statsRecord.collectAsState()

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
    var helpOpen by remember { mutableStateOf(false) }
    val feedback = clickFeedback()
    val view = LocalView.current

    val themesA11y = stringResource(R.string.themes_label)
    val settingsA11y = stringResource(R.string.settings_label)

    // The premium glow: the same successful-blip signal the lamp uses, gated by
    // the preference and the access check. Runs only while Main is composed and
    // never replays on return — the shared flow has no replay cache.
    val glowAccessible = access.grantsBlipGlow(viewModel.ownsThemePack())
    val glowActive = settings.blipGlowEnabled && glowAccessible

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(P.Bg).blipGlow(glowActive)) {
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
                // Mark + wordmark inside ONE gesture hit target: the hidden
                // Developer Access chord depends on holding the PROBLIP title
                // for twenty seconds, so the mark is part of that same target
                // (decorative to screen readers, never a second tap surface).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
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
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_problip_mark),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(P.Gold),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
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
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (access.developerAccess) {
                        Text(
                            text = stringResource(R.string.developer_label),
                            color = P.Gold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )
                    }
                    if (access.earnedPremium) {
                        Text(
                            text = stringResource(R.string.earned_label),
                            color = P.Gold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )
                    }
                    StatusText(state)
                    HelpButton(onClick = { helpOpen = true })
                }
            }

            // The counter presentation area: OFF renders nothing, TOTAL keeps
            // the one-line legacy total, STATS shows the four-period strip.
            // Recording and the 100K reward run regardless of the mode, and
            // the strip recomputes at midnight without a new blip.
            when (settings.blipCounterMode) {
                BlipCounterMode.OFF -> Unit
                BlipCounterMode.TOTAL ->
                    Text(
                        text = stringResource(R.string.blips_format, formatBlipCount(statsRecord.totalCount)),
                        color = P.TextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                    )
                BlipCounterMode.STATS -> {
                    val calendarEpoch = rememberCalendarEpoch()
                    val stats = remember(statsRecord, calendarEpoch) { viewModel.currentStats() }
                    MainStatsStrip(
                        stats =
                            MainStatsValues(
                                today = stats.todayCount,
                                week = stats.weekCount,
                                month = stats.monthCount,
                                total = stats.totalCount,
                            )
                    )
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
                        SectionLabel(
                            stringResource(
                                if (narrowLandscape) R.string.volume_label_short else R.string.volume_label
                            )
                        )
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
                            text = stringResource(R.string.volume_percent, dragPercent),
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
                            SectionLabel(stringResource(R.string.interval_label))
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
                                    earnedPremium = access.earnedPremium,
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
                                        label = stringResource(mode.labelRes()),
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
                                    text = stringResource(R.string.pulse_hint),
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
                        val poolCount = effectivePool.size
                        SoundSummaryRow(
                            summary =
                                if (poolCount == 1) {
                                    SoundCatalog.byId(effectivePool.first())!!.displayName
                                } else {
                                    pluralStringResource(
                                        if (narrowLandscape) R.plurals.sounds_selected_short
                                        else R.plurals.sounds_selected,
                                        poolCount,
                                        poolCount,
                                    )
                                },
                            label =
                                accessLabel(
                                    free = premiumSounds.isEmpty(),
                                    owned = premiumSounds.all { it.id in owned },
                                    earnedPremium = access.earnedPremium,
                                    developerAccess = access.developerAccess,
                                    expiryMillis = firstExpiry,
                                    nowMillis = now,
                                )?.text(),
                            onClick = onOpenSounds,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            NavRow(
                                stringResource(
                                    if (narrowLandscape) R.string.themes_label_short else R.string.themes_label
                                ),
                                onOpenThemes,
                                Modifier.weight(1f).semantics { contentDescription = themesA11y },
                            )
                            NavRow(
                                stringResource(
                                    if (narrowLandscape) R.string.settings_label_short else R.string.settings_label
                                ),
                                onOpenSettings,
                                Modifier.weight(1f).semantics { contentDescription = settingsA11y },
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
                // Adaptive placement, same controls in both shapes: compact
                // viewports put MINIMIZE, EXIT and START/STOP on one line, roomy
                // ones give the primary button its own full-width row.
                if (compact) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MinimizeButton(onClick = onMinimize, modifier = Modifier.weight(1f), compact = true)
                        ExitButton(onClick = onExit, modifier = Modifier.weight(1f), compact = true)
                        StartStopButton(
                            running = running,
                            modifier = Modifier.weight(1.5f),
                            gesture = gesture,
                            onDeveloperUnlocked = {
                                viewModel.enableDeveloperAccess()
                                developerUnlocked = true
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onStart = {
                                feedback()
                                onStartRequested()
                            },
                            onStop = {
                                feedback()
                                viewModel.stop()
                            },
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MinimizeButton(onClick = onMinimize, modifier = Modifier.weight(1f), compact = false)
                        ExitButton(onClick = onExit, modifier = Modifier.weight(1f), compact = false)
                    }
                    StartStopButton(
                        running = running,
                        modifier = Modifier.fillMaxWidth(),
                        gesture = gesture,
                        onDeveloperUnlocked = {
                            viewModel.enableDeveloperAccess()
                            developerUnlocked = true
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        onStart = {
                            feedback()
                            onStartRequested()
                        },
                        onStop = {
                            feedback()
                            viewModel.stop()
                        },
                    )
                }
            }
        }
    }

    if (developerUnlocked) {
        DeveloperUnlockedDialog(onDismiss = { developerUnlocked = false })
    }
    if (helpOpen) {
        HelpDialog(onDismiss = { helpOpen = false })
    }
}

/**
 * The dominant primary action. It is also the ONLY control that may confirm the
 * hidden Developer Access chord: `?`, MINIMIZE and EXIT consume their taps as
 * themselves, never as chord confirmations.
 */
@Composable
private fun StartStopButton(
    running: Boolean,
    modifier: Modifier,
    gesture: DeveloperGesture,
    onDeveloperUnlocked: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    BigButton(
        text = stringResource(if (running) R.string.action_stop else R.string.action_start),
        modifier = modifier,
        onClick = {
            // The armed chord consumes this tap: it must neither start nor
            // stop the session.
            if (gesture.consumeIfArmedAndTitleStillHeld()) {
                onDeveloperUnlocked()
            } else if (running) {
                onStop()
            } else {
                onStart()
            }
        },
    )
}

/**
 * Secondary header control: visually secondary, an adequate 44 dp target, and
 * never part of the hidden Developer gesture — only START/STOP may confirm it.
 */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val description = stringResource(R.string.help_a11y)
    Box(
        modifier =
            Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                .background(if (pressed) P.Compare else P.Surface)
                .border(1.dp, P.Edge)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    onClick()
                }
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Access label beside the interval heading: "TRIAL 04:37" while its five minutes run, "TRY 5 MIN"
 * when a tap would start them, "OWNED" once the Customization Pack is bought, "EARNED" while the
 * 100K reward grants it, "DEV" while Developer Access grants it.
 *
 * [owned] and [developerAccess] stay separate: neither has a timer, so neither may advertise a
 * trial, but only a purchase may claim ownership.
 */
@Composable
private fun IntervalTrialLabel(
    featureId: String,
    owned: Boolean,
    earnedPremium: Boolean,
    developerAccess: Boolean,
    trialExpiries: Map<String, Long>,
    nowMillis: Long,
) {
    val label =
        accessLabel(
            free = false,
            owned = owned,
            earnedPremium = earnedPremium,
            developerAccess = developerAccess,
            expiryMillis = trialExpiries[featureId],
            nowMillis = nowMillis,
        ) ?: return
    Text(
        text = label.text(),
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
            label = stringResource(R.string.manual_from),
            value = fromText,
            onValueChange = { fromText = it.filter(Char::isDigit).take(4) },
            onCommit = commit,
        )
        SecondsField(
            label = stringResource(R.string.manual_to),
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
            text = stringResource(R.string.seconds_suffix),
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
                text = stringResource(R.string.developer_unlocked_title),
                color = P.ActionText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = stringResource(R.string.developer_unlocked_body),
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            BigButton(text = stringResource(R.string.ok), onClick = onDismiss)
        }
    }
}

private fun IntervalMode.labelRes(): Int =
    when (this) {
        IntervalMode.RANDOM_4_7 -> R.string.interval_random
        IntervalMode.FIXED_5S -> R.string.interval_5s
        IntervalMode.FIXED_10S -> R.string.interval_10s
        IntervalMode.FIXED_15S -> R.string.interval_15s
        IntervalMode.FIXED_20S -> R.string.interval_20s
        IntervalMode.FIXED_30S -> R.string.interval_30s
        IntervalMode.PULSE -> R.string.interval_pulse
        IntervalMode.MANUAL -> R.string.interval_manual
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
    val text =
        stringResource(
            when (state) {
                ProblipState.STARTING -> R.string.status_starting
                ProblipState.RUNNING -> R.string.status_running
                ProblipState.ERROR -> R.string.status_error
                ProblipState.STOPPED -> R.string.status_off
            }
        )
    val color =
        when (state) {
            ProblipState.STARTING -> P.TextDim
            ProblipState.RUNNING -> P.TextMain
            ProblipState.ERROR -> P.Danger
            ProblipState.STOPPED -> P.TextDim
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
                SectionLabel(stringResource(R.string.sound_label))
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
 * stays primary. Tapping it while the title is held just minimizes — it can never confirm the
 * Developer chord.
 */
@Composable
private fun MinimizeButton(onClick: () -> Unit, modifier: Modifier, compact: Boolean) {
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
            text =
                stringResource(
                    if (compact) R.string.action_minimize_short else R.string.action_minimize
                ),
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

/** EXIT twins MINIMIZE at the same utility level; same visual weight, same quietness. */
@Composable
private fun ExitButton(onClick: () -> Unit, modifier: Modifier, compact: Boolean) {
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
            text = stringResource(R.string.action_exit),
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
