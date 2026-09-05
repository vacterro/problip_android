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

/**
 * Whether the persisted ownership cache may seed the live snapshot.
 *
 * The cache exists so a cold start with no network still knows what was bought;
 * it must never become the authority. Once Play has answered ([playAnswered]),
 * its answer stands even when it is EMPTY — that is exactly what a refund or a
 * revocation looks like, and re-applying the cache there would resurrect
 * ownership Play just took away.
 */
fun seedableFromCache(
    current: Set<String>,
    cached: Set<String>,
    playAnswered: Boolean,
): Boolean = !playAnswered && current.isEmpty() && cached.isNotEmpty()
