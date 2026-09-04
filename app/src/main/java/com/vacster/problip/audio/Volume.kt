package com.vacster.problip.audio

import kotlin.math.roundToInt

/** UI volume is 0..100 percent; audio engines take 0.0..1.0 gain. */
object Volume {
    /** Windows Problip default. */
    const val DEFAULT_PERCENT = 5

    val DEFAULT_GAIN = percentToGain(DEFAULT_PERCENT)

    fun percentToGain(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    fun gainToPercent(gain: Float): Int = (gain.coerceIn(0f, 1f) * 100f).roundToInt()
}
