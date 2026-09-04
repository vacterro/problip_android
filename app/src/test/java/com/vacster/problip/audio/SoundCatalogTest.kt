package com.vacster.problip.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundCatalogTest {

    @Test
    fun catalogHasStableIdsInRoadmapOrder() {
        assertEquals(
            listOf(
                "sound_original",
                "sound_glass",
                "sound_wood",
                "sound_soft_bell",
                "sound_bonk",
                "sound_space",
            ),
            SoundCatalog.all.map { it.id },
        )
    }

    @Test
    fun onlyOriginalIsFreeInThisWave() {
        // Premium entries exist in the catalog but stay locked until W7 Billing.
        assertEquals(listOf("sound_original"), SoundCatalog.playable.map { it.id })
    }

    @Test
    fun playableSelectionFiltersPremiumAndNeverEmpty() {
        assertEquals(
            setOf("sound_original"),
            SoundCatalog.playableSelection(setOf("sound_original", "sound_glass")),
        )
        // Premium-only or empty selections fall back to the original blip.
        assertEquals(setOf("sound_original"), SoundCatalog.playableSelection(setOf("sound_glass")))
        assertEquals(setOf("sound_original"), SoundCatalog.playableSelection(setOf("sound_bonk", "sound_space")))
        assertEquals(setOf("sound_original"), SoundCatalog.playableSelection(emptySet()))
    }
}
