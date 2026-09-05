package com.vacster.problip.ui

import com.vacster.problip.R
import com.vacster.problip.billing.BillingConnection

/**
 * One row state for the secondary store screens. Distinct by design (W11 gate):
 * a locked item, a purchase waiting for Play, and a store that has not loaded
 * yet must never look the same.
 */
enum class StoreItemState {
    /** Free with the app; nothing to buy. */
    INCLUDED,

    /** Purchase completed; the content is unlocked. */
    OWNED,

    /** Play accepted the purchase but has not completed it; do not offer to buy again. */
    PENDING,

    /** Locked, price known, tap buys it. */
    PURCHASABLE,

    /** Locked, price not known yet; the store is still connecting or querying. */
    LOADING,

    /** Locked and the store answered without this product; buying is impossible now. */
    UNAVAILABLE,
}

/**
 * Pure state rule shared by the Sounds and Themes screens.
 *
 * Ownership wins over everything (Play is the authority), then pending, then
 * price availability. Without the connection input, "no price yet" would be
 * indistinguishable from "product missing from the store".
 */
fun storeItemState(
    productId: String,
    free: Boolean,
    owned: Set<String>,
    pending: Set<String>,
    price: String?,
    connection: BillingConnection,
): StoreItemState = when {
    free -> StoreItemState.INCLUDED
    productId in owned -> StoreItemState.OWNED
    productId in pending -> StoreItemState.PENDING
    !price.isNullOrBlank() -> StoreItemState.PURCHASABLE
    connection != BillingConnection.CONNECTED -> StoreItemState.LOADING
    else -> StoreItemState.UNAVAILABLE
}

/**
 * Resource id of the short right-hand label; null means "render nothing".
 * PURCHASABLE displays the Play price itself, so it carries no label resource.
 */
fun storeItemLabelRes(state: StoreItemState, price: String?): Int? = when (state) {
    StoreItemState.INCLUDED -> R.string.included_label
    StoreItemState.OWNED -> R.string.owned_label
    StoreItemState.PENDING -> R.string.store_pending
    StoreItemState.PURCHASABLE -> null
    StoreItemState.LOADING -> R.string.store_loading
    StoreItemState.UNAVAILABLE -> R.string.store_unavailable
}
