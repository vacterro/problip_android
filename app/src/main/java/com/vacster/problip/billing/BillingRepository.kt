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
 */
class BillingRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val cacheOwned: suspend (Set<String>) -> Unit,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        // PBL 9: the client retries a dropped Play connection itself, so a
        // disconnect between two user actions no longer shows an empty store.
        .enableAutoServiceReconnection()
        .build()

    private val _connection = MutableStateFlow(BillingConnection.DISCONNECTED)
    val connection: StateFlow<BillingConnection> = _connection.asStateFlow()

    private val _owned = MutableStateFlow<Set<String>>(emptySet())
    val owned: StateFlow<Set<String>> = _owned.asStateFlow()

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
    private val _ownershipReady = MutableStateFlow(false)
    val ownershipReady: StateFlow<Boolean> = _ownershipReady.asStateFlow()

    /** Purchases Play accepted but has not completed; never grants access. */
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

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
     * request is also the last one to publish.
     */
    private val purchaseQueryLock = Mutex()
    private val productQueryLock = Mutex()

    @Volatile
    private var seeded = false

    /**
     * Whether Play itself has answered a purchase query in this process. Once it
     * has, its answer is the authority even when EMPTY — a refund or a revocation
     * is exactly an empty answer, so the cache may not be re-applied over it.
     */
    @Volatile
    private var playAnswered = false

    /**
     * Offline convenience seed from the local cache; never overrides Play truth.
     * Marks ownership initialized either way, so a first-run install with an empty
     * cache does not make a cold start wait for the network.
     */
    fun seedCached(cached: Set<String>) {
        if (!seeded) {
            seeded = true
            if (seedableFromCache(_owned.value, cached, playAnswered)) _owned.value = cached
        }
        _ownershipReady.value = true
    }

    fun connect() {
        if (_connection.value != BillingConnection.DISCONNECTED) return
        _connection.value = BillingConnection.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connection.value = BillingConnection.CONNECTED
                    _error.value = null
                    refreshPurchases()
                    queryProducts()
                } else {
                    _connection.value = BillingConnection.DISCONNECTED
                    _error.value = R.string.error_store_unavailable
                }
            }

            override fun onBillingServiceDisconnected() {
                _connection.value = BillingConnection.DISCONNECTED
                // Next connect()/resume reconnects and re-queries purchases.
            }
        })
    }

    /**
     * Resume/retry entry point. connect() alone is NOT a restore: it returns
     * immediately while the client is already connected, so ownership changes
     * made elsewhere (another device, a refund, a revocation) were never picked
     * up for as long as the process kept its connection. Query explicitly.
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
        scope.launch(Dispatchers.IO) {
            purchaseQueryLock.withLock {
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                val result = client.queryPurchasesAsync(params)
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val purchases = result.purchasesList.map(::toPurchaseInfo)
                    // Play has spoken: from here its answer supersedes the cache
                    // even when empty, so a refund or a revocation really revokes.
                    playAnswered = true
                    _owned.value = toOwned(purchases)
                    _pending.value = toPending(purchases)
                    _ownershipReady.value = true
                    cacheOwned(_owned.value)
                    acknowledgeIfNeeded(purchases)
                    // A success must retract its own previous failure, otherwise a
                    // recovered store keeps showing "could not check your
                    // purchases" forever. Only this message is cleared, so a fresh
                    // price failure from the parallel query survives.
                    _error.compareAndSet(PURCHASE_QUERY_FAILED, null)
                } else {
                    _error.value = PURCHASE_QUERY_FAILED
                }            }
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

    fun queryProducts() {
        scope.launch(Dispatchers.IO) {
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
                    _error.compareAndSet(PRICE_QUERY_FAILED, null)
                } else {
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
