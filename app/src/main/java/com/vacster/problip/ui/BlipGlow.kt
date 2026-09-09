package com.vacster.problip.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.ui.theme.LocalProblipColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Maximum accent overlay alpha: a soft tint, never a white flash. */
internal const val GLOW_MAX_ALPHA = 0.25f

/** Rise + brief hold + decay, ~330 ms total — unmistakable but soft at phone refresh rates. */
internal const val GLOW_RISE_MS = 60
internal const val GLOW_HOLD_MS = 70
internal const val GLOW_DECAY_MS = 210

/**
 * The premium BLIP GLOW: the whole Main softly lights up with the active Wintage
 * accent for one blip.
 *
 * The wash is drawn ABOVE the composed content (drawContent() first, then the
 * accent rect), so opaque Surface/Raised panels no longer hide it — the physical
 * expectation "the whole Main lights up" is met.
 *
 * It reuses the existing [ProblipSession.blips] signal the playback lamp already
 * observes — no second event bus, no scheduler timing, no widget flash, no
 * notification update, and no animation while Main is absent
 * ([repeatOnLifecycle] RESUMED only). The shared flow carries no replay, so
 * returning to Main never replays an old blip.
 *
 * [active] is the whole gate: preference ON AND effective access granting Glow.
 * Expiring access simply stops future events; the session is untouched.
 *
 * One [Animatable] makes the pulse deterministic: rise to peak, brief hold,
 * decay to zero. A blip arriving mid-pulse cancels and restarts it from the new
 * event (collectLatest); pulses never queue after playback.
 */
@Composable
internal fun Modifier.blipGlow(active: Boolean): Modifier {
    val alpha = remember { Animatable(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, active) {
        if (!active) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ProblipSession.blips.collectLatest {
                alpha.snapTo(0f)
                alpha.animateTo(GLOW_MAX_ALPHA, tween(GLOW_RISE_MS, easing = LinearEasing))
                delay(GLOW_HOLD_MS.toLong())
                alpha.animateTo(0f, tween(GLOW_DECAY_MS, easing = LinearEasing))
            }
        }
    }
    val accent = LocalProblipColors.current.Gold
    return drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0f) drawRect(color = accent, alpha = a)
    }
}
