package com.vacster.problip.billing

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.theme.ThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {

    private fun purchase(
        vararg ids: String,
        state: PurchaseState = PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
    ) = PurchaseInfo(
        token = "token-" + ids.joinToString("-"),
        productIds = ids.toList(),
        state = state,
        acknowledged = acknowledged,
    )

    @Test
    fun purchasedGrantsEveryProductOfThePurchase() {
        val owned = toOwned(listOf(purchase("sound_glass"), purchase("theme_pack", "sound_wood")))
        assertEquals(setOf("sound_glass", "theme_pack", "sound_wood"), owned)
    }

    @Test
    fun pendingNeverGrantsEntitlement() {
        val owned = toOwned(
            listOf(
                purchase("sound_glass", state = PurchaseState.PENDING, acknowledged = false),
                purchase("sound_wood", state = PurchaseState.UNSPECIFIED),
            ),
        )
        assertEquals(emptySet<String>(), owned)
    }

    @Test
    fun acknowledgementDoesNotDecideOwnership() {
        // Play ownership is the authority; acknowledgement is a separate duty the
        // repository performs, not a condition for unlocking content.
        assertEquals(setOf("sound_bonk"), toOwned(listOf(purchase("sound_bonk", acknowledged = false))))
        assertEquals(emptySet<String>(), toOwned(emptyList()))
    }

    @Test
    fun productIdsAreStableAndNonConsumable() {
        assertEquals(
            listOf("sound_glass", "sound_wood", "sound_soft_bell", "sound_bonk", "sound_space", "theme_pack"),
            ProductCatalog.all,
        )
        assertTrue(ProductCatalog.isValid("theme_pack"))
        assertFalse(ProductCatalog.isValid("sound_original"))
        assertTrue(ProductCatalog.isThemePack("theme_pack"))
        assertFalse(ProductCatalog.isThemePack("sound_glass"))
    }

    @Test
    fun everyPremiumSoundHasExactlyOneProductAndFreeSoundsHaveNone() {
        val premium = SoundCatalog.all.filterNot { it.free }.map { it.id }
        assertEquals(premium, ProductCatalog.SOUND_IDS)
        SoundCatalog.all.filter { it.free }.forEach { assertFalse(ProductCatalog.isValid(it.id)) }
    }

    @Test
    fun ownedProductsUnlockTheirContent() {
        val owned = toOwned(listOf(purchase("sound_glass"), purchase(ProductCatalog.THEME_PACK)))
        assertEquals(
            setOf("sound_original", "sound_glass"),
            SoundCatalog.playableSelection(setOf("sound_original", "sound_glass", "sound_wood"), owned),
        )
        assertEquals(ThemeCatalog.PINK, ThemeCatalog.effective("theme_pink", ownsThemePack = true))
        assertEquals(ThemeCatalog.CLASSIC, ThemeCatalog.effective("theme_pink", ownsThemePack = false))
    }

    @Test
    fun pendingIsReportedSeparatelyFromOwned() {
        val purchases = listOf(
            purchase("sound_glass", state = PurchaseState.PENDING, acknowledged = false),
            purchase("sound_wood"),
            purchase("sound_bonk", state = PurchaseState.UNSPECIFIED),
        )
        assertEquals(setOf("sound_glass"), toPending(purchases))
        assertEquals(setOf("sound_wood"), toOwned(purchases))
    }

    @Test
    fun ownedAndPendingSetsNeverOverlap() {
        val purchases = listOf(
            purchase("theme_pack", state = PurchaseState.PENDING, acknowledged = false),
            purchase("sound_space"),
        )
        val owned = toOwned(purchases)
        val pending = toPending(purchases)
        assertTrue(owned.intersect(pending).isEmpty())
        assertEquals(emptySet<String>(), toPending(emptyList()))
    }
}
