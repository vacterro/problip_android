package com.vacster.problip.billing

/**
 * Play Store product ids. Sound products unlock the same-id catalog entry;
 * `theme_pack` unlocks every non-free theme as one pack.
 * All products are non-consumable one-time purchases.
 */
object ProductCatalog {
    const val THEME_PACK = "theme_pack"

    val SOUND_IDS = listOf(
        "sound_glass",
        "sound_wood",
        "sound_soft_bell",
        "sound_bonk",
        "sound_space",
    )

    val all: List<String> = SOUND_IDS + THEME_PACK

    fun isValid(id: String): Boolean = id in all

    fun isThemePack(id: String): Boolean = id == THEME_PACK
}
