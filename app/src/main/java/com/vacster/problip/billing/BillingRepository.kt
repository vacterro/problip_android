package com.vacster.problip.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.vacster.problip.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UI-facing product line: localized price comes from Play, never hardcoded. */
data class ProductUi(
    val id: String,
    val title: String,
    val formattedPrice: String,
)

/**
 * Single BillingClient wrapper. Play ownership is the authority; the cached
 * owned set is only a convenience for offline starts until the next query.
 *
 * W2-004 Part E: [cacheOwned] is a BEST-EFFORT seam. It reports whether the
 * local persistence committed (true) or hit its expected local I/O failure
 * (false); a `false` result is a local-convenience fact only and can never
 * abort acknowledgement, the Play authority publication, or the Play-success
 * bookkeeping. Expected failures come back as `false`, not as exceptions, so
 * no broad catch lives here and nothing unexpected is silently swallowed.
 */
class BillingRepository private constructor(
    private val scope: CoroutineScope,
    private val cacheOwned: suspend (Set<String>) -> Boolean,
    clientFactory: (PurchasesUpdatedListener) -> BillingClient,
) : PurchasesUpdatedListener {

    /**
     * Production constructor: the real BillingClient, built against the app
     * context and bound to this repository as the purchases listener.
     */
    constructor(
        context: Context,
        scope: CoroutineScope,
        cacheOwned: suspend (Set<String>) -> Boolean,
    ) : this(scope, cacheOwned, { listener ->
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            // PBL 9: the client retries a dropped Play connection itself, so a
            // disconnect between two user actions no longer shows an empty store.
            .enableAutoServiceReconnection()
            .build()
    })

    /**
     * W2-004 test seam: the client arrives already built so the cache-failure
     * contract is deterministically testable on the JVM without touching the
     * production wiring.
     */
    internal constructor(
        scope: CoroutineScope,
        client: BillingClient,
        cacheOwned: suspend (Set<String>) -> Boolean,
    ) : this(scope, cacheOwned, { _ -> client })

    private val client: BillingClient = clientFactory(this)

    /**
     * One linearizable boundary for connection + ownership transitions: cache
     * seeding and Play snapshots publish through it atomically, and only the
     * caller granted a connection generation may start the BillingClient.
     */
    private val authority = BillingAuthority()
    private val _connection get() = authority.connection
    val connection: StateFlow<BillingConnection> = authority.connection

    /**
     * PERF-002 demand coalescing: at most one purchase query in flight plus at
     * most one trailing rerun, no matter how many triggers arrive (resume,
     * retry, connection lifecycle, purchase callback). The W2-001 mutex below
     * remains the correctness guard for publication order inside one query;
     * collapsed demands never enqueue on it.
     */
    private val purchaseRefresh = RefreshLimiter(scope)

    /** Same O(1) bound for ProductDetails queries. */
    private val productRefresh = RefreshLimiter(scope)

    val owned: StateFlow<Set<String>> = authority.owned

    /**
     * False until [owned] holds an initialized snapshot — either Play's answer or
     * the persisted cache standing in for it. A cold start must not resolve its
     * first session from the empty placeholder, so consumers that need the real
     * ownership (the service's first pool and interval) wait for this instead of
     * reading `owned.value` immediately.
     *
     * Deliberately NOT "Play answered": offline, Play never answers, and the
     * cached seed is a perfectly good snapshot to start from.
     */
    val ownershipReady: StateFlow<Boolean> = authority.ownershipReady

    /** Purchases Play accepted but has not completed; never grants access. */
    val pending: StateFlow<Set<String>> = authority.pending

    private val _products = MutableStateFlow<Map<String, ProductUi>>(emptyMap())
    val products: StateFlow<Map<String, ProductUi>> = _products.asStateFlow()

    /**
     * Last store failure as a string resource id; the UI resolves the text in
     * its own locale. Null once cleared or after a success.
     */
    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /**
     * Full ProductDetails objects kept for launching the purchase flow. Replaced
     * whole instead of mutated: it is written from a Dispatchers.IO query and read
     * from the main thread in [purchase], and a HashMap being cleared under a
     * concurrent read is undefined behavior, not a stale read.
     */
    @Volatile
    private var detailsById: Map<String, ProductDetails> = emptyMap()

    /**
     * One query of each kind at a time. Overlapping refreshes (resume + retry +
     * purchase callback) raced, so an older response could land last and publish
     * ownership that Play had already superseded. The mutex is FIFO, so the newest
     * request is also the last one to publish. PERF-002: demand coalescing
     * happens BEFORE this guard — repeated triggers collapse instead of
     * queueing one coroutine per demand.
     */
    private val purchaseQueryLock = Mutex()
    private val productQueryLock = Mutex()

    /** PERF-002 ProductDetails freshness (process-level, no background timer). */
    @Volatile
    private var productDetailsConnection: Long = -1L
    @Volatile
    private var productDetailsValidForConnection = false

    /**
     * Offline convenience seed from the local cache; never overrides Play truth.
     * Marks ownership initialized either way, so a first-run install with an empty
     * cache does not make a cold start wait for the network. One atomic authority
     * transition: the admissibility check and the publication cannot interleave
     * with a Play answer committing between them.
     */
    fun seedCached(cached: Set<String>) {
        authority.seedCached(cached)
    }

    fun connect() {
        // Atomic DISCONNECTED -> CONNECTING claim: exactly one racing caller
        // receives a generation and may actually invoke startConnection.
        val generation = authority.beginConnect() ?: return
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val setupOk = result.responseCode == BillingClient.BillingResponseCode.OK
                // Accepted only while this generation is still current and
                // CONNECTING; a stale callback changes no state and fires no
                // queries.
                if (authority.onSetupFinished(generation, setupOk)) {
                    if (setupOk) {
                        _error.value = null
                        refreshPurchases()
                        // PERF-002: metadata is not known valid for a NEW
                        // connection — re-query it on (re)connection.
                        productDetailsValidForConnection = false
                        queryProducts()
                    } else {
                        _error.value = R.string.error_store_unavailable
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Only the current generation's disconnect may end the connection;
                // stale callbacks from an older attempt are ignored.
                authority.onDisconnected(generation)
                // Next connect()/resume reconnects and re-queries purchases.
            }
        }
        try {
            client.startConnection(listener)
        } catch (e: Exception) {
            // startConnection threw synchronously after our generation was
            // accepted: return that generation to DISCONNECTED so state cannot
            // stay stuck in CONNECTING, then surface the existing behavior.
            authority.abortConnect(generation)
            _error.value = R.string.error_store_unavailable
        }
    }

    /**
     * Resume/retry entry point. connect() alone is NOT a restore: it returns
     * immediately while the client is already connected, so ownership changes
     * made elsewhere (another device, a refund, a revocation) were never picked
     * up for as long as the process kept its connection. Query explicitly.
     *
     * PERF-002: ownership is re-queried on EVERY healthy resume (refunds and
     * revocations must be discovered), but ProductDetails are process-cached
     * while valid for the current connection — repeated resumes no longer
     * refetch unchanged prices.
     */
    fun refresh() {
        if (_connection.value == BillingConnection.CONNECTED) {
            refreshPurchases()
            queryProducts()
        } else {
            connect() // its own success path re-queries
        }
    }

    /** Re-query owned purchases: the authoritative ownership check. */
    fun refreshPurchases() {
        purchaseRefresh.request {
            purchaseQueryLock.withLock {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                val result = client.queryPurchasesAsync(params)
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val purchases = result.purchasesList.map(::toPurchaseInfo)
                    // Play has spoken: from here its answer supersedes the cache
                    // even when empty, so a refund or a revocation really revokes.
                    // One atomic authority commit; the exact snapshot is captured
                    // for the cache write so nothing can substitute a later read.
                    val authoritativeOwned = authority.applyPlaySnapshot(
                        owned = toOwned(purchases),
                        pending = toPending(purchases),
                    )
                    // W2-004 Part E: the cache write is offline convenience only.
                    // Its LOCAL persistence failure (false) must never become a
                    // Play error: the authority already published the Play snapshot
                    // above, acknowledgement below must still run, and the previous
                    // PURCHASE_QUERY_FAILED state must still be cleared by this
                    // successful query. The next successful refresh repairs the
                    // cache; a later cache write never mutates BillingAuthority.
                    cacheOwned(authoritativeOwned)
                    acknowledgeIfNeeded(purchases)
                    // A success must retract its own previous failure, otherwise a
                    // recovered store keeps showing "could not check your
                    // purchases" forever. Only this message is cleared, so a fresh
                    // price failure from the parallel query survives.
                    _error.compareAndSet(PURCHASE_QUERY_FAILED, null)
                } else {
                    _error.value = PURCHASE_QUERY_FAILED
                }
            }
        }
    }

    private suspend fun acknowledgeIfNeeded(purchases: List<PurchaseInfo>) {
        purchases
            .filter { it.state == PurchaseState.PURCHASED && !it.acknowledged }
            .forEach { info ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(info.token)
                    .build()
                client.acknowledgePurchase(params)
            }
    }

    /**
     * Query product metadata. PERF-002 freshness rule (process-level, no
     * background timer):
     *
     * - query when product metadata is absent (first use / never answered);
     * - query when the current metadata is not known valid for THIS connection
     *   (fresh connect / reconnect);
     * - query after an explicit retry of a product-query failure;
     * - otherwise retain the healthy process-cached ProductDetails: repeated
     *   healthy Activity resumes do not refetch unchanged prices.
     *
     * Demand is bounded to one in-flight query plus at most one trailing rerun.
     */
    fun queryProducts() {
        val validForThisConnection =
            productDetailsValidForConnection &&
                productDetailsConnection == authority.currentGeneration
        if (validForThisConnection) {
            // Healthy process-cached metadata: repeated resumes do not refetch.
            return
        }
        productDetailsConnection = authority.currentGeneration
        productRefresh.request {
            productQueryLock.withLock {
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        ProductCatalog.all.map { id ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(id)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        },
                    )
                    .build()
                val result = client.queryProductDetails(params)
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val details = result.productDetailsList.orEmpty()
                    detailsById = details.associateBy { it.productId }
                    _products.value = details.associate { d ->
                        d.productId to ProductUi(
                            id = d.productId,
                            title = d.title,
                            formattedPrice = d.oneTimePurchaseOfferDetails?.formattedPrice ?: "",
                        )
                    }
                    productDetailsValidForConnection = true
                    _error.compareAndSet(PRICE_QUERY_FAILED, null)
                } else {
                    // The failure stays fresh: a later explicit retry must query.
                    productDetailsValidForConnection = false
                    _error.value = PRICE_QUERY_FAILED
                }
            }
        }
    }

    /** Launches the Play purchase sheet; failures surface through [error]. */
    fun purchase(activity: Activity, productId: String) {
        val detail = detailsById[productId]
        if (detail == null) {
            _error.value = R.string.error_prices_loading
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(detail)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _error.value = R.string.error_purchase_screen
        } else {
            _error.value = null
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    scope.launch(Dispatchers.IO) {
                        acknowledgeIfNeeded(purchases.map(::toPurchaseInfo))
                        // PERF-002: collapses into the one trailing authoritative
                        // rerun when a restore query is already in flight.
                        refreshPurchases()
                    }
                }
            }
            // USER_CANCELED and ITEM_ALREADY_OWNED need no state change here;
            // the next refreshPurchases() is the source of truth either way.
        }
    }

    private fun toPurchaseInfo(p: Purchase) = PurchaseInfo(
        token = p.purchaseToken,
        productIds = p.products,
        state = when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNSPECIFIED
        },
        acknowledged = p.isAcknowledged,
    )

    private companion object {
        /** Kept as constants so a successful query can retract exactly its own failure. */
        val PURCHASE_QUERY_FAILED = R.string.error_purchase_query
        val PRICE_QUERY_FAILED = R.string.error_price_query
    }
}
