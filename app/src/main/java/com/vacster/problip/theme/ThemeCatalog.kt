package com.vacster.problip.theme

/**
 * Stable theme IDs across settings persistence and (later) Billing.
 * Pure data — no Compose dependency; palettes live in ui/theme.
 */
data class ThemeEntry(
    val id: String,
    val displayName: String,
    val free: Boolean,
)

object ThemeCatalog {
    val CLASSIC = ThemeEntry("theme_classic", "Classic", free = true)
    val TERMINAL = ThemeEntry("theme_terminal", "Terminal", free = false)
    val PHOSPHOR = ThemeEntry("theme_phosphor", "Phosphor", free = false)
    val MIDNIGHT = ThemeEntry("theme_midnight", "Midnight", free = false)
    val AMBER = ThemeEntry("theme_amber", "Amber", free = false)
    val PINK = ThemeEntry("theme_pink", "Pink", free = false)

    val all: List<ThemeEntry> = listOf(CLASSIC, TERMINAL, PHOSPHOR, MIDNIGHT, AMBER, PINK)

    fun byId(id: String): ThemeEntry? = all.firstOrNull { it.id == id }

    fun isValidId(id: String): Boolean = byId(id) != null

    /**
     * The theme that can actually render right now: the stored pick when it is
     * free or the Themes Pack is owned, Classic otherwise.
     */
    fun effective(id: String?, ownsThemePack: Boolean = false): ThemeEntry {
        val entry = id?.let { byId(it) }
        return when {
            entry?.free == true -> entry
            entry != null && ownsThemePack -> entry
            else -> CLASSIC
        }
    }
}
