package com.vacster.problip.service

import com.vacster.problip.R
import com.vacster.problip.core.IntervalMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProblipNotificationTest {

    @Test
    fun describeMatchesRoadmapExample() {
        // Roadmap W4 notification example: "Random 4–7 sec" + "Original Blip • 5%".
        // The interval text is passed in so describe() stays locale-agnostic.
        assertEquals(
            "Random 4–7 sec • Original Blip • 5%",
            ProblipNotification.describe("Random 4–7 sec", "Original Blip", 5),
        )
    }

    @Test
    fun describeCoversEveryFixedInterval() {
        assertEquals("Every 5 sec • Original Blip • 100%", ProblipNotification.describe("Every 5 sec", "Original Blip", 100))
        assertEquals("Every 10 sec • Original Blip • 0%", ProblipNotification.describe("Every 10 sec", "Original Blip", 0))
        assertEquals("Every 15 sec • Original Blip • 50%", ProblipNotification.describe("Every 15 sec", "Original Blip", 50))
        assertEquals("Every 20 sec • Original Blip • 35%", ProblipNotification.describe("Every 20 sec", "Original Blip", 35))
        assertEquals("Every 30 sec • Original Blip • 1%", ProblipNotification.describe("Every 30 sec", "Original Blip", 1))
    }

    @Test
    fun describeCoversBothPremiumIntervals() {
        // The notification names what is actually scheduled, so PULSE says both slots.
        assertEquals(
            "Pulse 5 / 10–20 sec • Original Blip • 5%",
            ProblipNotification.describe("Pulse 5 / 10–20 sec", "Original Blip", 5),
        )
        assertEquals(
            "Manual • Original Blip • 5%",
            ProblipNotification.describe("Manual", "Original Blip", 5),
        )
    }

    @Test
    fun soundLabelNamesThePlayableSelection() {
        // Single playable sound: its display name, whatever is stored around it.
        // The generic pool/fallback labels are passed in so the function stays pure.
        assertEquals(
            "Original Blip",
            ProblipNotification.soundLabel(setOf("sound_original"), poolLabel = "Random pool", fallbackLabel = "Blip"),
        )
        assertEquals(
            "Original Blip",
            ProblipNotification.soundLabel(setOf("sound_original", "sound_glass"), poolLabel = "Random pool", fallbackLabel = "Blip"),
        )
        // Premium-only or empty selections resolve to the original blip in W5;
        // from W7 (Billing) a multi-own pool labels as the localized pool name.
        assertEquals(
            "Original Blip",
            ProblipNotification.soundLabel(setOf("sound_glass"), poolLabel = "Random pool", fallbackLabel = "Blip"),
        )
        assertEquals(
            "Original Blip",
            ProblipNotification.soundLabel(emptySet(), poolLabel = "Random pool", fallbackLabel = "Blip"),
        )
        assertEquals(
            "Random pool",
            ProblipNotification.soundLabel(
                setOf("sound_original", "sound_glass"),
                owned = setOf("sound_glass"),
                poolLabel = "Random pool",
                fallbackLabel = "Blip",
            ),
        )
    }

    @Test
    fun everyIntervalModeHasItsOwnDescriptionResource() {
        // Localization resolves these per locale; the mapping itself must stay
        // total and distinct over all eight modes.
        val resources =
            IntervalMode.entries.map { mode -> ProblipNotification.intervalLabelRes(mode) }
        assertEquals(resources.distinct(), resources)
        assertEquals(R.string.notif_interval_random, ProblipNotification.intervalLabelRes(IntervalMode.RANDOM_4_7))
        assertEquals(R.string.notif_interval_pulse, ProblipNotification.intervalLabelRes(IntervalMode.PULSE))
        assertEquals(R.string.notif_interval_manual, ProblipNotification.intervalLabelRes(IntervalMode.MANUAL))
    }
}
