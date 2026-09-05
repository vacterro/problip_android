package com.vacster.problip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Shown while the hidden seven-day override grants the content. */
internal const val DEVELOPER_LABEL = "DEV"

/**
 * Right-hand access label: nothing for free content, OWNED for a purchase, DEV
 * while Developer Access grants it, "TRIAL 04:21" while a trial runs, "TRY 5 MIN"
 * when a tap would start one.
 *
 * [owned] is Play ownership and nothing else, so a temporary grant never claims a
 * purchase. Developer Access is checked BEFORE the expiry, because it has no
 * timer of its own: a granted item must not advertise a trial it cannot start
 * (TrialAccess.startTrial refuses while access already exists).
 */
internal fun trialLabel(
    free: Boolean,
    owned: Boolean,
    developerAccess: Boolean = false,
    expiryMillis: Long?,
    nowMillis: Long,
): String? = when {
    free -> null
    owned -> "OWNED"
    developerAccess -> DEVELOPER_LABEL
    expiryMillis != null && expiryMillis > nowMillis ->
        "TRIAL ${TrialAccess.formatRemaining(expiryMillis, nowMillis)}"
    else -> "TRY 5 MIN"
}
