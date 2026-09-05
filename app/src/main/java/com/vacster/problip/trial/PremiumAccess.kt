package com.vacster.problip.trial

/**
 * The one effective-access authority. Everything premium — sounds, themes and
 * features like Manual Interval — is accessible when ANY of four things is true:
 *
 *     free || owned || activeTrial || developerAccess
 *
 * Ownership is Play's answer and belongs to the billing package; this type only
 * carries the two temporary grants, so no Compose or service code has to
 * assemble the rule itself.
 *
 * Developer Access is a global override, not a list of ids: it is stored as one
 * expiry timestamp and grants whatever the premium policy protects, including
 * content added later. [grantedIds] is only the projection the id-based catalog
 * helpers need.
 *
 * Not a security boundary — see [TrialAccess].
 */
data class PremiumAccess(
    val activeTrials: Set<String> = emptySet(),
    val developerAccess: Boolean = false,
) {

    /**
     * Ids the catalogs may treat as temporarily granted. Developer Access covers
     * every trialable id at once, which is why new premium content needs no
     * change here.
     */
    val grantedIds: Set<String> = if (developerAccess) TrialAccess.ALL_IDS else activeTrials

    /** The full rule. [free] and [owned] come from the catalog and from Play. */
    fun grants(contentId: String, free: Boolean = false, owned: Boolean = false): Boolean =
        free || owned || developerAccess || contentId in activeTrials

    /** Manual Interval rides the existing theme_pack purchase (no new Play SKU). */
    fun grantsManualInterval(ownsCustomizationPack: Boolean): Boolean =
        grants(TrialAccess.FEATURE_MANUAL_INTERVAL, owned = ownsCustomizationPack)

    /** PULSE rides the same pack as Manual Interval; still no new Play SKU. */
    fun grantsPulseInterval(ownsCustomizationPack: Boolean): Boolean =
        grants(TrialAccess.FEATURE_PULSE_INTERVAL, owned = ownsCustomizationPack)

    companion object {
        const val DEVELOPER_ACCESS_DURATION_MS = 604_800_000L

        /** Seven days from now. Repeating the unlock resets the window, never stacks it. */
        fun developerExpiryFrom(nowMillis: Long): Long = nowMillis + DEVELOPER_ACCESS_DURATION_MS

        fun developerActive(expiryMillis: Long, nowMillis: Long): Boolean = expiryMillis > nowMillis

        /** "6d 23h" / "23h 12m" / "4m". Informational only, never an access decision. */
        fun formatDeveloperRemaining(expiryMillis: Long, nowMillis: Long): String {
            val remaining = (expiryMillis - nowMillis).coerceAtLeast(0L)
            val minutes = remaining / 60_000L
            val hours = minutes / 60L
            val days = hours / 24L
            return when {
                days > 0 -> "${days}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes % 60}m"
                else -> "${minutes}m"
            }
        }
    }
}

/**
 * The persisted temporary-access record: five-minute trial expiries plus the
 * Developer Access expiry, read as one unit so a single recompute covers both.
 */
data class TemporaryAccess(
    val trialExpiries: Map<String, Long> = emptyMap(),
    val developerExpiryMillis: Long = 0L,
)
