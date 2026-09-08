package com.vacster.problip.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import com.vacster.problip.R
import com.vacster.problip.trial.PremiumAccess
import com.vacster.problip.trial.TrialAccess
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Compact settings screen: statistics, visual preferences, temporary access and
 * the in-app language. Language rides the AppCompat per-app locale mechanism —
 * the platform is the only authority, no second DataStore key exists for it.
 *
 * BLIP GLOW semantics ([GlowUiPolicy]): the toggle means ONLY the preference and
 * never starts a trial; the separate TRY 5 MIN action starts the reusable
 * five-minute trial, and a running trial cannot be extended, exactly like every
 * other trial.
 */
@Composable
fun SettingsScreen(
    viewModel: ProblipViewModel,
    onBack: () -> Unit,
) {
    val access by viewModel.access.collectAsState()
    val developerExpiry by viewModel.developerExpiryMillis.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val statsRecord by viewModel.statsRecord.collectAsState()
    val trialExpiries by viewModel.trialExpiries.collectAsState()
    val ownsPack = viewModel.ownsThemePack()
    val glowTrialRunning = trialExpiries[TrialAccess.FEATURE_BLIP_GLOW]
        ?.let { it > System.currentTimeMillis() } == true
    val now = rememberTrialNow(enabled = access.developerAccess || glowTrialRunning)
    var languageDialogOpen by remember { mutableStateOf(false) }

    // Period display must roll over while Settings stays open: all day/week/
    // month transitions happen at local midnight, so one UI-only delay to the
    // next boundary and one recomposition flag are enough — no WorkManager, no
    // ticker, no background anything.
    var periodBump by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(durationUntilNextLocalMidnight(ZoneId.systemDefault()).toMillis())
            periodBump++
        }
    }
    StoreScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        SectionLabel(stringResource(R.string.statistics_section))
        // Keyed on periodBump so the midnight wake recomputes the snapshot even
        // when no new blip has arrived and statsRecord has not emitted.
        val stats = remember(statsRecord, periodBump) { viewModel.currentStats() }
        StatRow(stringResource(R.string.stats_today), formatBlipCount(stats.todayCount))
        StatRow(stringResource(R.string.stats_week), formatBlipCount(stats.weekCount))
        StatRow(stringResource(R.string.stats_month), formatBlipCount(stats.monthCount))
        StatRow(stringResource(R.string.stats_total), formatBlipCount(stats.totalCount))
        Text(
            text =
                if (stats.earnedPremium) {
                    stringResource(R.string.premium_reward_earned)
                } else {
                    stringResource(
                        R.string.premium_reward_progress,
                        formatBlipCount(stats.totalCount),
                        formatBlipCount(viewModel.premiumRewardBlips),
                    )
                },
            color = if (stats.earnedPremium) P.Gold else P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )

        SectionLabel(stringResource(R.string.visual_section))
        ToggleRow(
            label = stringResource(R.string.show_blip_counter),
            checked = settings.showBlipCounter,
            onToggle = viewModel::setShowBlipCounter,
        )
        val glowHasAccess = access.grantsBlipGlow(ownsPack)
        val glowInteraction = GlowUiPolicy.onToggleToggled(settings.blipGlowEnabled)
        ToggleRow(
            label = stringResource(R.string.blip_glow),
            checked = settings.blipGlowEnabled,
            onToggle = {
                // The toggle means ONLY the preference: never a trial. The trial
                // lives behind the separate TRY action below, so the default-ON
                // preference can never be switched off as a trial side effect.
                when (glowInteraction) {
                    is GlowInteraction.SetEnabled -> viewModel.setBlipGlowEnabled(glowInteraction.enabled)
                    GlowInteraction.StartTrial, GlowInteraction.None -> Unit
                }
            },
        )
        // Separate access action: offered (and tappable) only while access is absent.
        if (GlowUiPolicy.tryOffered(glowHasAccess)) {
            Text(
                text = stringResource(R.string.try_5_min),
                color = P.ActionText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(36.dp)
                        .clickable(role = Role.Button) { viewModel.tryBlipGlow() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } else {
            GlowAccessLabel(
                owned = ownsPack,
                earned = access.earnedPremium,
                developerAccess = access.developerAccess,
                expiryMillis = trialExpiries[TrialAccess.FEATURE_BLIP_GLOW],
                nowMillis = now,
            )
        }

        SectionLabel(stringResource(R.string.temporary_access))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.developer_access),
                color = P.TextMain,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (access.developerAccess) {
                    stringResource(
                        R.string.remaining_format,
                        PremiumAccess.formatDeveloperRemaining(developerExpiry, now),
                    )
                } else {
                    stringResource(R.string.status_off)
                },
                color = if (access.developerAccess) P.Gold else P.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
        }

        BigButton(
            text = stringResource(R.string.reset_temporary_access),
            fontSize = 13.sp,
            onClick = viewModel::resetTemporaryAccess,
        )
        Text(
            text = stringResource(R.string.reset_temporary_access_hint),
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )

        LanguageRow(onOpen = { languageDialogOpen = true })
    }

    if (languageDialogOpen) {
        LanguageDialog(onDismiss = { languageDialogOpen = false })
    }
}

/** The supported languages. The tag is the AppCompat locale; the name is its endonym. */
private val LanguageOptions = listOf(
    null to "English",
    "ru" to "Русский",
    "et" to "Eesti",
    "ja" to "日本語",
)

/** One statistics line: label on the left, locale-formatted count on the right. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

/** Localized [toggle_on]/[toggle_off] preference row; the toggle is the whole row. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .clickable(role = Role.Switch) { onToggle(!checked) }
                .padding(horizontal = 8.dp),
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
            text = stringResource(if (checked) R.string.toggle_on else R.string.toggle_off),
            color = if (checked) P.Gold else P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

/**
 * Access label for BLIP GLOW: OWNED via the pack, EARNED via the 100K reward,
 * DEV, a running TRIAL countdown, or the offer to TRY 5 MIN.
 */
@Composable
private fun GlowAccessLabel(
    owned: Boolean,
    earned: Boolean,
    developerAccess: Boolean,
    expiryMillis: Long?,
    nowMillis: Long,
) {
    val label = accessLabel(
        free = false,
        owned = owned,
        earnedPremium = earned,
        developerAccess = developerAccess,
        expiryMillis = expiryMillis,
        nowMillis = nowMillis,
    ) ?: return
    Text(
        text = label.text(),
        color = P.ActionText,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}

/** One compact row: LANGUAGE and the current selection, SYSTEM when unset. */
@Composable
private fun LanguageRow(onOpen: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales()
    val currentName =
        if (current.isEmpty) {
            stringResource(R.string.language_value_system)
        } else {
            LanguageOptions
                .firstOrNull { it.first != null && it.first == current[0]!!.language }
                ?.second
                ?: current[0]!!.displayLanguage
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .background(P.Surface)
                .border(1.dp, P.Edge)
                .clickable(role = Role.Button) { onOpen() }
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.language_label),
            color = P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = currentName,
            color = P.TextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            text = ">",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * One small palette dialog: System Default plus the four supported languages.
 * Selecting applies immediately through AppCompatDelegate; the empty list IS
 * "System Default" and the platform persists the choice itself.
 */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales()
    val selectedTag: String? =
        if (current.isEmpty) null else current[0]?.language?.takeIf { tag ->
            LanguageOptions.any { it.first == tag }
        }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.background(P.Surface).border(1.dp, P.Edge).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.language_label),
                color = P.Gold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
            LanguageOptionRow(
                name = stringResource(R.string.language_option_system),
                selected = selectedTag == null,
            ) {
                onDismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            LanguageOptions.forEach { (tag, name) ->
                LanguageOptionRow(name = name, selected = selectedTag == tag) {
                    onDismiss()
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag!!),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .clickable(role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (selected) "(*)" else "( )",
            color = P.ActionText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Text(
            text = name,
            color = if (selected) P.ActionText else P.TextMain,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

/**
 * The exact one-shot delay from [now] to the NEXT local midnight in [zone] —
 * the only calendar boundary every day/week/month period rolls over at. Pure
 * (instant + zone in, millis out), so tests pin it without a scheduler.
 * DST offsets are respected: the gap is measured against the real start of the
 * next local day, and a hair past the boundary still yields the following day.
 */
internal fun durationUntilNextLocalMidnight(zone: ZoneId, now: Instant = Instant.now()): Duration {
    val here = now.atZone(zone)
    val nextMidnight = here.toLocalDate().plusDays(1).atStartOfDay(zone)
    return Duration.between(here, nextMidnight)
}

/** Long overload for call sites holding epoch millis. */
internal fun durationUntilNextLocalMidnight(zone: ZoneId, nowMillis: Long): Duration =
    durationUntilNextLocalMidnight(zone, Instant.ofEpochMilli(nowMillis))
