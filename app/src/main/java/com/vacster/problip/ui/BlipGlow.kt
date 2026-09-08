package com.vacster.problip.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.ui.theme.LocalProblipColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Maximum accent overlay alpha: a soft tint, never a white flash. */
internal const val GLOW_MAX_ALPHA = 0.25f

/** Rise + decay, inside the accepted 150–350 ms total. */
internal const val GLOW_RISE_MS = 60
internal const val GLOW_DECAY_MS = 200

/**
 * The premium BLIP GLOW: the whole Main background tints with the active Wintage
 * accent for one blip.
 *
 * It reuses the existing [ProblipSession.blips] signal the playback lamp already
 * observes — no second event bus, no widget flash, no notification update, and
 * no animation while Main is absent ([repeatOnLifecycle] RESUMED only). The
 * shared flow carries no replay, so returning to Main never replays an old blip.
 *
 * [active] is the whole gate: preference ON AND effective access granting Glow.
 * Expiring access simply stops future events; the session is untouched.
 */
@Composable
internal fun Modifier.blipGlow(active: Boolean): Modifier {
    if (!active) return this
    var alpha by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(
            durationMillis = if (alpha > 0f) GLOW_RISE_MS else GLOW_DECAY_MS,
            easing = LinearOutSlowInEasing,
        ),
        label = "blipGlow",
    )
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, active) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ProblipSession.blips.collectLatest {
                alpha = GLOW_MAX_ALPHA
                delay(GLOW_RISE_MS.toLong())
                alpha = 0f
            }
        }
    }
    val accent = LocalProblipColors.current.Gold
    return drawBehind {
        if (animated > 0f) drawRect(color = accent, alpha = animated)
    }
}
