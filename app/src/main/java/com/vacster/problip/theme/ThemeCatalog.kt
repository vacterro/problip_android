package com.vacster.problip.theme

/**
 * Stable theme IDs across settings persistence and Billing.
 * Pure data — no Compose dependency; palettes live in ui/theme.
 */
data class ThemeEntry(
    val id: String,
    val displayName: String,
    val free: Boolean,
)

/**
 * The fifteen Wintage palette designs. IDs are persistence keys and never change;
 * display names may be reworded freely.
 *
 * Golden Default keeps the original free id [CLASSIC] instead of gaining a second
 * premium entry, so the catalog is one free theme plus fourteen premium ones. The
 * pre-release placeholder palettes (Terminal/Phosphor/Midnight/Amber/Pink) were
 * replaced outright; their stored ids resolve to Golden Default like any unknown
 * value, which is safe because nothing has shipped publicly. Wintage's `custom`
 * palette was removed for release because it duplicated Golden Default exactly;
 * [normalize] maps its stored id back to Golden Default (see [LEGACY_IDS]).
 *
 * List order is the Themes screen order: the free theme first, then Wintage's own
 * `order` metadata.
 */
object ThemeCatalog {
    val CLASSIC = ThemeEntry("theme_classic", "Golden Default", free = true)
    val GOLDEN = ThemeEntry("theme_wintage_golden", "Dark Golden (Win95)", free = false)
    val CLAUDECODE = ThemeEntry("theme_wintage_claudecode", "Claude Code", free = false)
    val ANTIGRAVITY = ThemeEntry("theme_wintage_antigravity", "Antigravity", free = false)
    val KLITE = ThemeEntry("theme_wintage_klite", "K-Lite (MPC-HC)", free = false)
    val FREEBUFF = ThemeEntry("theme_wintage_freebuff", "FreeBuff", free = false)
    val CODENOMAD = ThemeEntry("theme_wintage_codenomad", "CodeNomad", free = false)
    val FPDEFAULT = ThemeEntry("theme_wintage_fpdefault", "Default", free = false)
    val GOLDENVINTAGE = ThemeEntry("theme_wintage_goldenvintage", "Golden Vintage", free = false)
    val VINTAGEDARK = ThemeEntry("theme_wintage_vintagedark", "Vintage Dark", free = false)
    val VINTAGECLASSIC = ThemeEntry("theme_wintage_vintageclassic", "Vintage Classic", free = false)
    val OLED = ThemeEntry("theme_wintage_oled", "Dark 2 (OLED)", free = false)
    val DRACULA = ThemeEntry("theme_wintage_dracula", "Dracula", free = false)
    val NORD = ThemeEntry("theme_wintage_nord", "Nord", free = false)
    val SOLARIZED = ThemeEntry("theme_wintage_solarized", "Solarized Dark", free = false)

    val all: List<ThemeEntry> = listOf(
        CLASSIC,
        GOLDEN,
        CLAUDECODE,
        ANTIGRAVITY,
        KLITE,
        FREEBUFF,
        CODENOMAD,
        FPDEFAULT,
        GOLDENVINTAGE,
        VINTAGEDARK,
        VINTAGECLASSIC,
        OLED,
        DRACULA,
        NORD,
        SOLARIZED,
    )

    /**
     * Stored ids this release no longer exposes, normalized to Golden Default on
     * read. Wintage's editable Custom was imported as a fixed preset that exactly
     * duplicated Golden Default, so local testers holding it keep working instead
     * of seeing an invisible selected theme.
     */
    private val LEGACY_IDS: Map<String, ThemeEntry> = mapOf("theme_wintage_custom" to CLASSIC)

    fun byId(id: String): ThemeEntry? = all.firstOrNull { it.id == id }

    /** Maps a removed-but-stored id to its replacement; unknown ids pass through. */
    fun normalize(id: String): String = LEGACY_IDS[id]?.id ?: id

    fun isValidId(id: String): Boolean = byId(id) != null

    /**
     * The theme that can actually render right now: the stored pick when it is
     * free, when the Customization Pack is owned, or while its own five-minute
     * trial is still running. Golden Default otherwise, so an expired trial can
     * never keep painting a premium palette.
     *
     * The stored pick itself is deliberately left alone — buying the pack or
     * starting another trial restores it without the user re-picking.
     */
    fun effective(
        id: String?,
        ownsThemePack: Boolean = false,
        trials: Set<String> = emptySet(),
    ): ThemeEntry {
        val entry = id?.let { byId(normalize(it)) } ?: return CLASSIC
        return when {
            entry.free -> entry
            ownsThemePack -> entry
            entry.id in trials -> entry
            else -> CLASSIC
        }
    }
}
