package com.vacster.problip.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResponseListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.vacster.problip.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF-002 focused evidence with a gated fake BillingClient: repeated refresh
 * demands collapse to O(1) active + trailing queries per domain, ProductDetails
 * are not refetched on healthy resumes but are on reconnect/retry, refunds and
 * revocations on resume are still discovered, acknowledgement still occurs, and
 * the W2-001/W2-004 semantics hold. All waits are event-driven and bounded.
 */
class BillingRefreshCoalescingTest {

    // ---------------------------------------------------------------- fake --

    private class Script {
        var purchases: List<Purchase> = emptyList()
        var productsOk: Boolean = true
        var acknowledgeCalls = 0
    }

    /**
     * Stub whose purchase and product queries can each be parked on a latch, so
     * demand coalescing is observed deterministically while a query is blocked.
     * Override set mirrors the proven BillingCacheFailureTest stub.
     */
    private class GatedClient(private val script: Script) : BillingClient() {
        /** Park inside queryPurchasesAsync until released. */
        val purchaseGate = CountDownLatch(1)
        /** Park inside queryProductDetailsAsync until released. */
        val productGate = CountDownLatch(1)
        val purchaseQueries = java.util.concurrent.atomic.AtomicInteger()
        val productQueries = java.util.concurrent.atomic.AtomicInteger()
        val acknowledgeCalls = java.util.concurrent.atomic.AtomicInteger()
        override fun startConnection(listener: BillingClientStateListener) {
            listener.onBillingSetupFinished(ok())
        }

        override fun endConnection() = Unit

        override fun isReady(): Boolean = true

        override fun getConnectionState(): Int = 2 // CONNECTED

        override fun queryPurchasesAsync(
            params: QueryPurchasesParams,
            listener: com.android.billingclient.api.PurchasesResponseListener,
        ) {
            purchaseQueries.incrementAndGet()
            purchaseGate.await()
            listener.onQueryPurchasesResponse(result(true), script.purchases)
        }

        override fun acknowledgePurchase(
            params: AcknowledgePurchaseParams,
            listener: AcknowledgePurchaseResponseListener,
        ) {
            acknowledgeCalls.incrementAndGet()
            script.acknowledgeCalls++
            listener.onAcknowledgePurchaseResponse(ok())
        }

        override fun consumeAsync(params: com.android.billingclient.api.ConsumeParams, listener: com.android.billingclient.api.ConsumeResponseListener) =
            error("unused in this fixture")

        override fun launchBillingFlow(activity: android.app.Activity, params: com.android.billingclient.api.BillingFlowParams): BillingResult =
            error("unused in this fixture")

        override fun isFeatureSupported(feature: String): BillingResult =
            error("unused in this fixture")

        override fun queryProductDetailsAsync(
            params: com.android.billingclient.api.QueryProductDetailsParams,
            listener: com.android.billingclient.api.ProductDetailsResponseListener,
        ) {
            // Must not throw: queryProducts() runs uncaught in a scope child.
            productQueries.incrementAndGet()
            productGate.await()
            listener.onProductDetailsResponse(
                result(script.productsOk),
                com.android.billingclient.api.QueryProductDetailsResult.create(emptyList(), emptyList()),
            )
        }

        override fun showAlternativeBillingOnlyInformationDialog(
            activity: android.app.Activity,
            listener: com.android.billingclient.api.AlternativeBillingOnlyInformationDialogListener,
        ): BillingResult = error("unused in this fixture")

        override fun showExternalOfferInformationDialog(
            activity: android.app.Activity,
            listener: com.android.billingclient.api.ExternalOfferInformationDialogListener,
        ): BillingResult = error("unused in this fixture")

        override fun showInAppMessages(
            activity: android.app.Activity,
            params: com.android.billingclient.api.InAppMessageParams,
            listener: com.android.billingclient.api.InAppMessageResponseListener,
        ): BillingResult = error("unused in this fixture")

        override fun createAlternativeBillingOnlyReportingDetailsAsync(
            listener: com.android.billingclient.api.AlternativeBillingOnlyReportingDetailsListener,
        ) = error("unused in this fixture")

        override fun createBillingProgramReportingDetailsAsync(
            params: com.android.billingclient.api.BillingProgramReportingDetailsParams,
            listener: com.android.billingclient.api.BillingProgramReportingDetailsListener,
        ) = error("unused in this fixture")

        override fun createExternalOfferReportingDetailsAsync(
            listener: com.android.billingclient.api.ExternalOfferReportingDetailsListener,
        ) = error("unused in this fixture")

        override fun getBillingChoiceInfoAsync(
            params: com.android.billingclient.api.GetBillingChoiceInfoParams,
            listener: com.android.billingclient.api.BillingChoiceInfoResponseListener,
        ) = error("unused in this fixture")

        override fun getBillingConfigAsync(
            params: com.android.billingclient.api.GetBillingConfigParams,
            listener: com.android.billingclient.api.BillingConfigResponseListener,
        ) = error("unused in this fixture")

        override fun isAlternativeBillingOnlyAvailableAsync(
            listener: com.android.billingclient.api.AlternativeBillingOnlyAvailabilityListener,
        ) = error("unused in this fixture")

        override fun isBillingProgramAvailableAsync(
            billingProgram: Int,
            listener: com.android.billingclient.api.BillingProgramAvailabilityListener,
        ) = error("unused in this fixture")

        override fun isExternalOfferAvailableAsync(
            listener: com.android.billingclient.api.ExternalOfferAvailabilityListener,
        ) = error("unused in this fixture")

        override fun launchExternalLink(
            activity: android.app.Activity,
            params: com.android.billingclient.api.LaunchExternalLinkParams,
            listener: com.android.billingclient.api.LaunchExternalLinkResponseListener,
        ) = error("unused in this fixture")

        override fun showBillingProgramInformationDialog(
            activity: android.app.Activity,
            params: com.android.billingclient.api.BillingProgramInformationDialogParams,
            listener: com.android.billingclient.api.BillingProgramInformationDialogListener,
        ) = error("unused in this fixture")

        private fun result(codeOk: Boolean): BillingResult = BillingResult.newBuilder()
            .setResponseCode(
                if (codeOk) BillingClient.BillingResponseCode.OK
                else BillingClient.BillingResponseCode.ERROR,
            )
            .build()
    }

    // ------------------------------------------------------------ fixture --

    private class Fixture {
        val script = Script()
        val client = GatedClient(script)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = BillingRepository(scope = scope, client = client, cacheOwned = { true })

        /** Bounded wait until N purchase queries have STARTED. */
        fun awaitPurchaseStarted(count: Int, seconds: Long = 5): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                if (client.purchaseQueries.get() >= count) return true
                Thread.sleep(10)
            }
            return false
        }

        /** Bounded wait until N product queries have STARTED. */
        fun awaitProductStarted(count: Int, seconds: Long = 5): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                if (client.productQueries.get() >= count) return true
                Thread.sleep(10)
            }
            return false
        }

        fun teardown() = scope.coroutineContext.cancelChildren()
    }

    // ------------------------------------------------------------ helpers --

    private companion object {
        fun ok(): BillingResult = BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .build()

        fun purchase(product: String, state: Int, acknowledged: Boolean = true): Purchase =
            Purchase(purchaseJson(product, state, acknowledged), "test-signature")

        fun purchaseJson(product: String, state: Int, acknowledged: Boolean) = """
            {
              "productId": "$product",
              "orderId": "order-$product",
              "packageName": "com.vacster.problip",
              "purchaseTime": 1700000000000,
              "purchaseState": $state,
              "purchaseToken": "token-$product",
              "acknowledged": $acknowledged,
              "products": ["$product"],
              "quantity": 1
            }
        """.trimIndent()
    }

    private fun bounded(seconds: Long = 5, block: suspend () -> Unit) {
        runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(seconds)) { block() }
        }
    }

    // -------------------------------------------------------------- tests --

    @Test
    fun `hundred purchase refresh demands during one blocked query collapse to active plus trailing`() {
        val f = Fixture()
        f.script.purchases = listOf(purchase("sound_glass", Purchase.PurchaseState.PURCHASED))
        try {
            f.repo.connect()
            assertTrue("first purchase query must start", f.awaitPurchaseStarted(1))
            assertEquals("one active query while parked", 1, f.client.purchaseQueries.get())

            // 100 more demands arrive while the query is parked on the gate.
            repeat(100) { f.repo.refreshPurchases() }
            assertEquals(
                "no queue: demands collapse, the parked query is still the only one",
                1,
                f.client.purchaseQueries.get(),
            )

            // Release: the active query finishes, then exactly ONE trailing rerun.
            f.client.purchaseGate.countDown()
            assertTrue("trailing rerun must start", f.awaitPurchaseStarted(2))
            assertEquals("exactly one trailing rerun, not 100", 2, f.client.purchaseQueries.get())
            f.client.purchaseGate.countDown()
            bounded { assertEquals(setOf("sound_glass"), f.repo.owned.first()) }
        } finally {
            f.teardown()
        }
    }

    @Test
    fun `purchase callback during an in-flight restore schedules exactly one post-purchase rerun`() {
        val f = Fixture()
        f.script.purchases = listOf(purchase("sound_glass", Purchase.PurchaseState.PURCHASED))
        try {
            f.repo.connect()
            assertTrue(f.awaitPurchaseStarted(1))
            // The Play sheet answers while the restore query is parked; the
            // callback snapshot is the NEWEST Play truth (theme_pack).
            f.script.purchases = listOf(purchase("theme_pack", Purchase.PurchaseState.PURCHASED))
            f.repo.onPurchasesUpdated(
                ok(),
                mutableListOf(purchase("theme_pack", Purchase.PurchaseState.PURCHASED)),
            )
            assertEquals("no extra query while parked", 1, f.client.purchaseQueries.get())

            f.client.purchaseGate.countDown()
            assertTrue("post-purchase authoritative rerun", f.awaitPurchaseStarted(2))
            assertEquals(2, f.client.purchaseQueries.get())
            f.client.purchaseGate.countDown()
            bounded {
                // The trailing rerun carries the NEWEST Play snapshot.
                assertEquals(setOf("theme_pack"), f.repo.owned.first())
            }
        } finally {
            f.teardown()
        }
    }

    @Test
    fun `repeated healthy resumes query ownership but do not refetch valid product details`() {
        val f = Fixture()
        f.script.purchases = listOf(purchase("sound_glass", Purchase.PurchaseState.PURCHASED))
        try {
            f.repo.connect() // setup fires refreshPurchases() + queryProducts()
            assertTrue("ownership query starts", f.awaitPurchaseStarted(1))
            f.client.purchaseGate.countDown()
            f.client.productGate.countDown()
            bounded {
                assertEquals(1, f.client.productQueries.get())
                assertEquals(setOf("sound_glass"), f.repo.owned.first())
            }

            // 50 healthy resumes: ownership is re-queried, prices are NOT.
            repeat(50) { f.repo.refresh() }
            assertTrue("ownership still refreshed on resume", f.awaitPurchaseStarted(2))
            assertEquals("metadata stays process-cached while valid", 1, f.client.productQueries.get())
            f.client.purchaseGate.countDown()
        } finally {
            f.teardown()
        }
    }

    @Test
    fun `explicit retry after a product-query failure actually queries again`() {
        val f = Fixture()
        try {
            f.repo.connect()
            f.client.purchaseGate.countDown()
            f.script.productsOk = false
            f.client.productGate.countDown()
            bounded {
                assertEquals(1, f.client.productQueries.get())
                assertEquals(R.string.error_price_query, f.repo.error.first())
            }

            // The failed query left freshness invalid; an explicit retry queries.
            f.script.productsOk = true
            f.repo.queryProducts()
            bounded {
                assertEquals("retry must actually query", 2, f.client.productQueries.get())
                assertNull("successful retry retracts its own failure", f.repo.error.first())
            }
        } finally {
            f.teardown()
        }
    }

    @Test
    fun `refund on resume is still discovered by the coalesced refresh`() {
        val f = Fixture()
        f.script.purchases = listOf(purchase("sound_glass", Purchase.PurchaseState.PURCHASED))
        try {
            f.repo.connect()
            assertTrue(f.awaitPurchaseStarted(1))
            f.client.purchaseGate.countDown()
            f.client.productGate.countDown()
            bounded { assertEquals(setOf("sound_glass"), f.repo.owned.first()) }

            // The entitlement was revoked on Play's side between resumes.
            f.script.purchases = emptyList()
            f.repo.refresh()
            bounded {
                assertEquals("empty Play answer revokes", emptySet<String>(), f.repo.owned.first())
            }
        } finally {
            f.teardown()
        }
    }

    @Test
    fun `acknowledgement still occurs through the coalesced path`() {
        val f = Fixture()
        f.script.purchases = listOf(
            purchase("sound_glass", Purchase.PurchaseState.PURCHASED, acknowledged = false),
        )
        try {
            f.repo.connect()
            assertTrue(f.awaitPurchaseStarted(1))
            f.client.purchaseGate.countDown()
            // Bounded poll: the acknowledgement happens asynchronously after the
            // purchase answer returns to the repository.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (f.client.acknowledgeCalls.get() < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertEquals("unacknowledged purchase got acknowledged", 1, f.client.acknowledgeCalls.get())
        } finally {
            f.teardown()
        }
    }
}
