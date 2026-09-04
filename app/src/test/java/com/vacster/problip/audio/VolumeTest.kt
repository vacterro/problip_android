package com.vacster.problip.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {

    @Test
    fun percentMapsToGain() {
        assertEquals(0.0f, Volume.percentToGain(0), 0f)
        assertEquals(1.0f, Volume.percentToGain(100), 0f)
        assertEquals(0.05f, Volume.percentToGain(5), 0f)
        assertEquals(0.5f, Volume.percentToGain(50), 0f)
    }

    @Test
    fun percentClampsOutsideRange() {
        assertEquals(0.0f, Volume.percentToGain(-5), 0f)
        assertEquals(1.0f, Volume.percentToGain(150), 0f)
        assertEquals(0, Volume.gainToPercent(-0.1f))
        assertEquals(100, Volume.gainToPercent(1.5f))
    }

    @Test
    fun roundTripIsExactForEveryPercent() {
        for (p in 0..100) {
            assertEquals(p, Volume.gainToPercent(Volume.percentToGain(p)))
        }
    }

    @Test
    fun defaultMatchesWindowsProduct() {
        assertEquals(5, Volume.DEFAULT_PERCENT)
        assertEquals(0.05f, Volume.DEFAULT_GAIN, 0f)
    }
}
