package com.vacster.problip.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W12 regression: prepare() used to call SoundPool.load() for every sound in the
 * pool on every pool change and never unloaded anything, so switching sounds or
 * finishing a purchase mid-session leaked one sample per already-loaded sound.
 */
class PoolDeltaTest {

    @Test
    fun unchangedPoolLoadsAndUnloadsNothing() {
        val pool = setOf(SoundCatalog.ORIGINAL.id, SoundCatalog.GLASS.id)
        val delta = PoolDelta.between(loaded = pool, wanted = pool)
        assertEquals(emptySet<String>(), delta.toLoad)
        assertEquals(emptySet<String>(), delta.toUnload)
        assertTrue(delta.isEmpty)
    }

    @Test
    fun onlyNewSoundsLoadAndOnlyDroppedSoundsUnload() {
        val delta = PoolDelta.between(
            loaded = setOf(SoundCatalog.ORIGINAL.id, SoundCatalog.GLASS.id),
            wanted = setOf(SoundCatalog.GLASS.id, SoundCatalog.WOOD.id),
        )
        assertEquals(setOf(SoundCatalog.WOOD.id), delta.toLoad)
        assertEquals(setOf(SoundCatalog.ORIGINAL.id), delta.toUnload)
    }

    @Test
    fun firstPreparationLoadsEverythingAndEmptyPoolUnloadsEverything() {
        val all = SoundCatalog.all.map { it.id }.toSet()
        assertEquals(all, PoolDelta.between(loaded = emptySet(), wanted = all).toLoad)
        assertEquals(emptySet<String>(), PoolDelta.between(loaded = emptySet(), wanted = all).toUnload)
        assertEquals(all, PoolDelta.between(loaded = all, wanted = emptySet()).toUnload)
    }

    @Test
    fun purchaseGrowingThePoolKeepsTheAlreadyLoadedFreeSound() {
        // Owning a premium sound adds it to the selection; the free blip that is
        // already in memory must not be reloaded.
        val before = SoundCatalog.playableSelection(setOf(SoundCatalog.ORIGINAL.id))
        val after = SoundCatalog.playableSelection(
            selected = setOf(SoundCatalog.ORIGINAL.id, SoundCatalog.SPACE.id),
            owned = setOf(SoundCatalog.SPACE.id),
        )
        val delta = PoolDelta.between(loaded = before, wanted = after)
        assertEquals(setOf(SoundCatalog.SPACE.id), delta.toLoad)
        assertEquals(emptySet<String>(), delta.toUnload)
    }
}
