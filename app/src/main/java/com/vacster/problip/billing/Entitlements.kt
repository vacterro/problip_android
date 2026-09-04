package com.vacster.problip.billing

/** Domain purchase snapshot; converted from BillingClient types at the repository edge. */
data class PurchaseInfo(
    val token: String,
    val productIds: List<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
)

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Billing connection state, kept Android-free so UI state rules stay unit-testable. */
enum class BillingConnection { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Ownership rule: only completed (PURCHASED) purchases grant entitlements.
 * PENDING never unlocks until Play reports it purchased.
 */
fun toOwned(purchases: List<PurchaseInfo>): Set<String> =
    purchases
        .filter { it.state == PurchaseState.PURCHASED }
        .flatMap { it.productIds }
        .toSet()

/**
 * Products awaiting payment completion. Distinct from owned on purpose: the UI
 * must say "waiting for Play" instead of either unlocking or offering to buy
 * the same product twice.
 */
fun toPending(purchases: List<PurchaseInfo>): Set<String> =
    purchases
        .filter { it.state == PurchaseState.PENDING }
        .flatMap { it.productIds }
        .toSet()
