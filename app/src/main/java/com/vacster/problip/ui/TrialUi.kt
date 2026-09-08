package com.vacster.problip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vacster.problip.R
import com.vacster.problip.trial.TrialAccess
import kotlinx.coroutines.delay

/**
 * One-second tick for the trial countdown, running only while [enabled] — with no
 * trial on screen nothing recomposes.
 *
 * The read happens BEFORE the loop, so the transition to `enabled = false` also
 * produces one final value. Without it the last tick stayed on screen after the
 * trial had already died and a dead trial could read "TRIAL 00:01".
 *
 * Display only. Access is decided by TrialCoordinator against the persisted
 * expiry, so a paused, throttled or never-composed screen cannot extend a trial.
 */
@Composable
internal fun rememberTrialNow(enabled: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        now = System.currentTimeMillis()
        while (enabled) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Semantic access label; composables resolve it to localized text. */
internal enum class AccessLabelKind { OWNED, EARNED, DEV, TRIAL, TRY }

internal data class AccessLabel(val kind: AccessLabelKind, val remaining: String? = null)

/**
 * Right-hand access label: nothing for free content, OWNED for a purchase,
 * EARNED for the local 100K-blip Premium reward, DEV while Developer Access
 * grants it, TRIAL with a countdown while a trial runs, TRY when a tap would
 * start one.
 *
 * [owned] is Play ownership and nothing else, so a temporary grant or an earned
 * reward never claims a purchase. Developer Access is checked BEFORE the expiry,
 * because it has no timer of its own: a granted item must not advertise a trial
 * it cannot start (TrialAccess.startTrial refuses while access already exists).
 *
 * Priority for genuinely owned content: OWNED outranks EARNED. For content
 * unlocked globally by the 100K reward (no per-item ownership), EARNED outranks
 * DEV and TRIAL.
 */
internal fun accessLabel(
    free: Boolean,
    owned: Boolean,
    earnedPremium: Boolean = false,
    developerAccess: Boolean = false,
    expiryMillis: Long?,
    nowMillis: Long,
): AccessLabel? = when {
    free -> null
    owned -> AccessLabel(AccessLabelKind.OWNED)
    earnedPremium -> AccessLabel(AccessLabelKind.EARNED)
    developerAccess -> AccessLabel(AccessLabelKind.DEV)
    expiryMillis != null && expiryMillis > nowMillis ->
        AccessLabel(AccessLabelKind.TRIAL, TrialAccess.formatRemaining(expiryMillis, nowMillis))
    else -> AccessLabel(AccessLabelKind.TRY)
}

/** Localized text of the label, resolved in the caller's composition. */
@Composable
internal fun AccessLabel.text(): String = when (kind) {
    AccessLabelKind.OWNED -> stringResource(R.string.owned_label)
    AccessLabelKind.EARNED -> stringResource(R.string.earned_label)
    AccessLabelKind.DEV -> stringResource(R.string.developer_label)
    AccessLabelKind.TRIAL -> stringResource(R.string.trial_format, remaining.orEmpty())
    AccessLabelKind.TRY -> stringResource(R.string.try_5_min)
}

/** What one Settings interaction with BLIP GLOW may do. Pure, so the policy is testable. */
internal sealed interface GlowInteraction {
    /** Writes the enable/disable preference and NOTHING else — never a trial. */
    data class SetEnabled(val enabled: Boolean) : GlowInteraction

    /** Starts the reusable five-minute trial; the store refuses to extend a live one. */
    object StartTrial : GlowInteraction

    /** The interaction does nothing. */
    object None : GlowInteraction
}

/**
 * The BLIP GLOW interaction contract.
 *
 * The enable/disable toggle means ONLY the preference: it never starts, extends
 * or cancels the five-minute trial. With the preference defaulting ON, tying a
 * trial start to toggling OFF would switch the effect off and start a trial the
 * user can never see. The trial is started by its own TRY action, offered only
 * while no access exists — and because a running trial already grants access,
 * tapping anything while it runs can never extend it. Turning the preference
 * OFF during an active trial keeps the trial timer running and merely suppresses
 * the effect; ON resumes it for the remaining time.
 */
internal object GlowUiPolicy {

    /** The ONLY effect of tapping the BLIP GLOW toggle row. */
    fun onToggleToggled(enabled: Boolean): GlowInteraction =
        GlowInteraction.SetEnabled(!enabled)

    /** The separate TRY action: it starts the trial only while access is absent. */
    fun onTryTapped(hasAccess: Boolean): GlowInteraction =
        if (hasAccess) GlowInteraction.None else GlowInteraction.StartTrial

    /** Whether the TRY action should be offered (shown, clickable) at all. */
    fun tryOffered(hasAccess: Boolean): Boolean = !hasAccess
}
