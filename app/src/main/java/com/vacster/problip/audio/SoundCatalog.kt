package com.vacster.problip.audio

import com.vacster.problip.R

/**
 * Stable sound IDs across settings persistence and (later) Billing products.
 * The scheduler/core never sees entitlement logic: consumers ask for the
 * playable selection and receive plain sound IDs.
 */
data class SoundEntry(
    val id: String,
    val displayName: String,
    val free: Boolean,
    val resId: Int,
)

object SoundCatalog {
    val ORIGINAL = SoundEntry("sound_original", "Original Blip", free = true, resId = R.raw.blip01)
    val GLASS = SoundEntry("sound_glass", "Glass", free = false, resId = R.raw.blip_glass)
    val WOOD = SoundEntry("sound_wood", "Wood", free = false, resId = R.raw.blip_wood)
    val SOFT_BELL = SoundEntry("sound_soft_bell", "Soft Bell", free = false, resId = R.raw.blip_soft_bell)
    val BONK = SoundEntry("sound_bonk", "Bonk", free = false, resId = R.raw.blip_bonk)
    val SPACE = SoundEntry("sound_space", "Space", free = false, resId = R.raw.blip_space)

    val all: List<SoundEntry> = listOf(ORIGINAL, GLASS, WOOD, SOFT_BELL, BONK, SPACE)

    /** Free entries only. Ownership-aware lists are built by the UI from `all`. */
    val playable: List<SoundEntry> = all.filter { it.free }

    fun byId(id: String): SoundEntry? = all.firstOrNull { it.id == id }

    /**
     * The selection that can actually play right now: persisted choice filtered
     * to free, owned or trial-accessible entries, never empty (falls back to the
     * original blip).
     *
     * `owned` comes from Play purchases (billing package) and `trials` from the
     * five-minute trial coordinator; the catalog stays agnostic of both. Ownership
     * and trial access are separate inputs on purpose: an expired trial must drop
     * a sound out of the pool, a purchase must never expire.
     */
    fun playableSelection(
        selected: Set<String>,
        owned: Set<String> = emptySet(),
        trials: Set<String> = emptySet(),
    ): Set<String> {
        val effective = selected.filter { id ->
            val entry = byId(id)
            entry != null && (entry.free || id in owned || id in trials)
        }.toSet()
        return effective.ifEmpty { setOf(ORIGINAL.id) }
    }
}
