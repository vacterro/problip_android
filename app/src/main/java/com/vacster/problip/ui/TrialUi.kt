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
internal enum class AccessLabelKind { OWNED, DEV, TRIAL, TRY }

internal data class AccessLabel(val kind: AccessLabelKind, val remaining: String? = null)

/**
 * Right-hand access label: nothing for free content, OWNED for a purchase, DEV
 * while Developer Access grants it, TRIAL with a countdown while a trial runs,
 * TRY when a tap would start one.
 *
 * [owned] is Play ownership and nothing else, so a temporary grant never claims a
 * purchase. Developer Access is checked BEFORE the expiry, because it has no
 * timer of its own: a granted item must not advertise a trial it cannot start
 * (TrialAccess.startTrial refuses while access already exists).
 */
internal fun accessLabel(
    free: Boolean,
    owned: Boolean,
    developerAccess: Boolean = false,
    expiryMillis: Long?,
    nowMillis: Long,
): AccessLabel? = when {
    free -> null
    owned -> AccessLabel(AccessLabelKind.OWNED)
    developerAccess -> AccessLabel(AccessLabelKind.DEV)
    expiryMillis != null && expiryMillis > nowMillis ->
        AccessLabel(AccessLabelKind.TRIAL, TrialAccess.formatRemaining(expiryMillis, nowMillis))
    else -> AccessLabel(AccessLabelKind.TRY)
}

/** Localized text of the label, resolved in the caller's composition. */
@Composable
internal fun AccessLabel.text(): String = when (kind) {
    AccessLabelKind.OWNED -> stringResource(R.string.owned_label)
    AccessLabelKind.DEV -> stringResource(R.string.developer_label)
    AccessLabelKind.TRIAL -> stringResource(R.string.trial_format, remaining.orEmpty())
    AccessLabelKind.TRY -> stringResource(R.string.try_5_min)
}
