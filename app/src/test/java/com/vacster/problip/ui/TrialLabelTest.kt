package com.vacster.problip.ui

import com.vacster.problip.trial.TrialAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The access label users read on every premium row. Pure, so the whole matrix is
 * pinned here instead of on a device.
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
        assertNull(trialLabel(free = true, owned = false, expiryMillis = null, nowMillis = now))
        // Even with everything granted: a free row has nothing to advertise.
        assertNull(
            trialLabel(
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
            "OWNED",
            trialLabel(free = false, owned = true, expiryMillis = null, nowMillis = now),
        )
    }

    @Test
    fun developerAccessReadsDevAndNeverOwned() {
        val label = trialLabel(
            free = false,
            owned = false,
            developerAccess = true,
            expiryMillis = null,
            nowMillis = now,
        )
        assertEquals(DEVELOPER_LABEL, label)
        assertEquals("DEV", label)
    }

    @Test
    fun anActiveTrialCountsDownAsMinutesAndSeconds() {
        assertEquals(
            "TRIAL 05:00",
            trialLabel(
                free = false,
                owned = false,
                expiryMillis = now + TrialAccess.DURATION_MS,
                nowMillis = now,
            ),
        )
        assertEquals(
            "TRIAL 04:37",
            trialLabel(
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
            "TRY 5 MIN",
            trialLabel(free = false, owned = false, expiryMillis = null, nowMillis = now),
        )
    }

    @Test
    fun anExpiredTrialOffersANewOneInsteadOfShowingAStaleCountdown() {
        // The expiry boundary itself is already expired, exactly like TrialAccess.
        assertEquals(
            "TRY 5 MIN",
            trialLabel(free = false, owned = false, expiryMillis = now, nowMillis = now),
        )
        assertEquals(
            "TRY 5 MIN",
            trialLabel(free = false, owned = false, expiryMillis = now - 1L, nowMillis = now),
        )
    }

    @Test
    fun ownershipOutranksDeveloperAccessWhichOutranksARunningTrial() {
        val expiry = now + TrialAccess.DURATION_MS
        assertEquals(
            "OWNED",
            trialLabel(
                free = false,
                owned = true,
                developerAccess = true,
                expiryMillis = expiry,
                nowMillis = now,
            ),
        )
        // Developer Access has no timer of its own, so a granted item must not
        // advertise a trial (TrialAccess.startTrial would refuse to start one).
        assertEquals(
            DEVELOPER_LABEL,
            trialLabel(
                free = false,
                owned = false,
                developerAccess = true,
                expiryMillis = expiry,
                nowMillis = now,
            ),
        )
    }
}
