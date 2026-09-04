package com.vacster.problip.service

import com.vacster.problip.core.IntervalMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblipNotificationTest {

    @Test
    fun describeMatchesRoadmapExample() {
        // Roadmap W4 notification example: "Random 4–7 sec" + "Original Blip • 5%".
        assertEquals(
            "Random 4–7 sec • Original Blip • 5%",
            ProblipNotification.describe(IntervalMode.RANDOM_4_7, "Original Blip", 5),
        )
    }

    @Test
    fun describeCoversEveryFixedInterval() {
        assertEquals("Every 5 sec • Original Blip • 100%", ProblipNotification.describe(IntervalMode.FIXED_5S, "Original Blip", 100))
        assertEquals("Every 10 sec • Original Blip • 0%", ProblipNotification.describe(IntervalMode.FIXED_10S, "Original Blip", 0))
        assertEquals("Every 15 sec • Original Blip • 50%", ProblipNotification.describe(IntervalMode.FIXED_15S, "Original Blip", 50))
        assertEquals("Every 20 sec • Original Blip • 35%", ProblipNotification.describe(IntervalMode.FIXED_20S, "Original Blip", 35))
        assertEquals("Every 30 sec • Original Blip • 1%", ProblipNotification.describe(IntervalMode.FIXED_30S, "Original Blip", 1))
    }

    @Test
    fun soundLabelNamesThePlayableSelection() {
        // Single playable sound: its display name, whatever is stored around it.
        assertEquals("Original Blip", ProblipNotification.soundLabel(setOf("sound_original")))
        assertEquals("Original Blip", ProblipNotification.soundLabel(setOf("sound_original", "sound_glass")))
        // Premium-only or empty selections resolve to the original blip in W5;
        // from W7 (Billing) a multi-own pool labels as "Random pool".
        assertEquals("Original Blip", ProblipNotification.soundLabel(setOf("sound_glass")))
        assertEquals("Original Blip", ProblipNotification.soundLabel(emptySet()))
    }
}
