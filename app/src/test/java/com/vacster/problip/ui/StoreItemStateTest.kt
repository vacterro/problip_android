package com.vacster.problip.ui

import com.vacster.problip.audio.SoundCatalog
import com.vacster.problip.billing.BillingConnection
import com.vacster.problip.billing.ProductCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** W11 gate: locked, owned, pending, loading and error states must stay distinct. */
class StoreItemStateTest {

    private fun state(
        productId: String = "sound_glass",
        free: Boolean = false,
        owned: Set<String> = emptySet(),
        pending: Set<String> = emptySet(),
        price: String? = null,
        connection: BillingConnection = BillingConnection.CONNECTED,
    ) = storeItemState(productId, free, owned, pending, price, connection)

    @Test
    fun freeEntriesAreIncludedRegardlessOfTheStore() {
        assertEquals(
            StoreItemState.INCLUDED,
            state(productId = "sound_original", free = true, connection = BillingConnection.DISCONNECTED),
        )
    }

    @Test
    fun ownershipWinsOverPendingAndPrice() {
        assertEquals(
            StoreItemState.OWNED,
            state(owned = setOf("sound_glass"), pending = setOf("sound_glass"), price = "€0.49"),
        )
    }

    @Test
    fun pendingWinsOverPriceSoNothingIsBoughtTwice() {
        assertEquals(StoreItemState.PENDING, state(pending = setOf("sound_glass"), price = "€0.49"))
    }

    @Test
    fun priceMakesTheRowPurchasable() {
        assertEquals(StoreItemState.PURCHASABLE, state(price = "€0.49"))
    }

    @Test
    fun missingPriceIsLoadingUntilTheStoreIsConnected() {
        assertEquals(StoreItemState.LOADING, state(connection = BillingConnection.DISCONNECTED))
        assertEquals(StoreItemState.LOADING, state(connection = BillingConnection.CONNECTING))
    }

    @Test
    fun connectedStoreWithoutTheProductIsUnavailable() {
        assertEquals(StoreItemState.UNAVAILABLE, state(connection = BillingConnection.CONNECTED))
    }

    @Test
    fun blankPriceIsNotAPurchasableRow() {
        // Play returns an empty formattedPrice when the offer is missing; that is
        // not a buyable row, and it must not read as a free one either.
        assertEquals(StoreItemState.UNAVAILABLE, state(price = ""))
    }

    @Test
    fun everyStateHasItsOwnLabel() {
        // PURCHASABLE intentionally shows the Play price itself, so it maps to
        // null while every other state carries its own distinct resource.
        val labels = StoreItemState.entries.map { storeItemLabelRes(it, "€0.49") }
        assertEquals(labels.distinct(), labels)
        assertNull(storeItemLabelRes(StoreItemState.PURCHASABLE, null))
        StoreItemState.entries.filterNot { it == StoreItemState.PURCHASABLE }.forEach { state ->
            assertEquals(
                state,
                StoreItemState.entries.first { storeItemLabelRes(it, "€0.49") == storeItemLabelRes(state, "€0.49") },
            )
        }
    }

    @Test
    fun themePackAndSoundsShareTheSameRule() {
        assertEquals(
            StoreItemState.OWNED,
            state(productId = ProductCatalog.THEME_PACK, owned = setOf(ProductCatalog.THEME_PACK)),
        )
        assertEquals(
            StoreItemState.INCLUDED,
            state(productId = SoundCatalog.ORIGINAL.id, free = true),
        )
    }
}
