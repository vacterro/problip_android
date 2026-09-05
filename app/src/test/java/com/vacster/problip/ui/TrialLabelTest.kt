package com.vacster.problip.ui

import com.vacster.problip.trial.TrialAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The access label users read on every premium row. Pure, so the whole matrix is
 * pinned here instead of on a device. Localization turned the label into a
 * SEMANTIC value ([AccessLabel]) that the screens resolve to localized text;
 * these pins hold the semantics stable.
 *
 * Developer Access must never read OWNED: it expires, a purchase does not, and a
 * label that claims a purchase the user never made is the one mistake that cannot
 * be undone by waiting.
 *
 * The one-second tick behind [rememberTrialNow] is a composable and stays out of
 * this suite (no Robolectric, no Compose test runtime). Its observable effect is
 * covered below: the moment a trial expires the label must be the offer to start
 * a new one, never a stale "TRIAL 00:01".
 */
class TrialLabelTest {

    private val now = 1_700_000_000_000L

    @Test
    fun freeContentCarriesNoLabelAtAll() {
        assertNull(accessLabel(free = true, owned = false, expiryMillis = null, nowMillis = now))
        // Even with everything granted: a free row has nothing to advertise.
        assertNull(
            accessLabel(
                free = true,
                owned = true,
                developerAccess = true,
                expiryMillis = now + TrialAccess.DURATION_MS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun aRealPlayPurchaseReadsOwned() {
        assertEquals(
            AccessLabelKind.OWNED,
            accessLabel(free = false, owned = true, expiryMillis = null, nowMillis = now)?.kind,
        )
    }

    @Test
    fun developerAccessReadsDevAndNeverOwned() {
        val label = accessLabel(
            free = false,
            owned = false,
            developerAccess = true,
            expiryMillis = null,
            nowMillis = now,
        )
        assertEquals(AccessLabelKind.DEV, label?.kind)
    }

    @Test
    fun anActiveTrialCountsDownAsMinutesAndSeconds() {
        assertEquals(
            AccessLabel(AccessLabelKind.TRIAL, "05:00"),
            accessLabel(
                free = false,
                owned = false,
                expiryMillis = now + TrialAccess.DURATION_MS,
                nowMillis = now,
            ),
        )
        assertEquals(
            AccessLabel(AccessLabelKind.TRIAL, "04:37"),
            accessLabel(
                free = false,
                owned = false,
                expiryMillis = now + 277_000L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun contentWithNoAccessOffersTheFiveMinuteTrial() {
        assertEquals(
            AccessLabelKind.TRY,
            accessLabel(free = false, owned = false, expiryMillis = null, nowMillis = now)?.kind,
        )
    }

    @Test
    fun anExpiredTrialOffersANewOneInsteadOfShowingAStaleCountdown() {
        // The expiry boundary itself is already expired, exactly like TrialAccess.
        assertEquals(
            AccessLabelKind.TRY,
            accessLabel(free = false, owned = false, expiryMillis = now, nowMillis = now)?.kind,
        )
        assertEquals(
            AccessLabelKind.TRY,
            accessLabel(free = false, owned = false, expiryMillis = now - 1L, nowMillis = now)?.kind,
        )
    }

    @Test
    fun ownershipOutranksDeveloperAccessWhichOutranksARunningTrial() {
        val expiry = now + TrialAccess.DURATION_MS
        assertEquals(
            AccessLabelKind.OWNED,
            accessLabel(
                free = false,
                owned = true,
                developerAccess = true,
                expiryMillis = expiry,
                nowMillis = now,
            )?.kind,
        )
        // Developer Access has no timer of its own, so a granted item must not
        // advertise a trial (TrialAccess.startTrial would refuse to start one).
        assertEquals(
            AccessLabelKind.DEV,
            accessLabel(
                free = false,
                owned = false,
                developerAccess = true,
                expiryMillis = expiry,
                nowMillis = now,
            )?.kind,
        )
    }
}
