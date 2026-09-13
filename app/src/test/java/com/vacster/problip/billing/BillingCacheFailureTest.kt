package com.vacster.problip.billing

import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryPurchasesParams
import com.vacster.problip.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * W2-004 Part E/F regressions: a successful Play purchase query is authoritative
 * even when the LOCAL ownership cache write fails. The cache is offline
 * convenience only — its failure must never become PURCHASE_QUERY_FAILED, must
 * never skip acknowledgement, and must never prevent the Play-success
 * bookkeeping. A later successful refresh repairs the cache without ever
 * letting it mutate BillingAuthority.
 *
 * Determinism: the BillingClient stub answers its listener callbacks
 * SYNCHRONOUSLY on the caller's thread (exactly like the real client, whose
 * callbacks arrive on Play's binder thread while the query coroutine stays
 * suspended). The repository coroutine runs on a real Dispatchers.IO scope and
 * each assertion waits for a bounded latch/timeout — no sleep-based race, the
 * waits are event-driven (the cache-write record IS the event).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillingCacheFailureTest {

    // ---------------------------------------------------------------- fake --

    /** Scripted state a test mutates between phases. */
    private class Script {
        var purchases: List<Purchase> = emptyList()
        var queryOk: Boolean = true
        var acknowledgeCalls = 0
    }

    /**
     * BillingClient stub answering listener callbacks synchronously on the
     * calling thread. The abstract base has a public no-arg constructor, so
     * subclassing needs no Context and no Play runtime.
     */
    private class StubClient(private val script: Script) : BillingClient() {
        /** Fired after each purchase-query answer (drives bounded waits). */
        val queryAnswered = java.util.concurrent.CountDownLatch(1)
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
            listener.onQueryPurchasesResponse(result(), script.purchases)
            queryAnswered.countDown()
        }

        override fun acknowledgePurchase(
            params: AcknowledgePurchaseParams,
            listener: AcknowledgePurchaseResponseListener,
        ) {
            script.acknowledgeCalls++
            acknowledged.countDown()
            listener.onAcknowledgePurchaseResponse(ok())
        }

        /** Fired after each acknowledgement (drives bounded waits). */
        val acknowledged = java.util.concurrent.CountDownLatch(1)

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
            listener.onProductDetailsResponse(
                ok(),
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

        private fun result(): BillingResult =
            BillingResult.newBuilder()
                .setResponseCode(
                    if (script.queryOk) BillingClient.BillingResponseCode.OK
                    else BillingClient.BillingResponseCode.ERROR,
                )
                .build()
    }

    // ------------------------------------------------------------ fixture --

    private class Fixture {
        val script = Script()
        val client = StubClient(script)
        /** Every cache-write attempt, whether it "committed" or not. */
        val cacheWrites = mutableListOf<Set<String>>()
        /** Latch fired on EVERY cache-write attempt (the event to await). */
        val cacheWriteAttempt = CountDownLatch(1)
        /** False = the local ownership cache write fails (expected settings I/O). */
        @Volatile var cacheOk: Boolean = false

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = BillingRepository(
            scope = scope,
            client = client,
            cacheOwned = { owned ->
                synchronized(cacheWrites) { cacheWrites += owned }
                cacheWriteAttempt.countDown()
                cacheOk
            },
        )

        /** Bounded, event-driven wait: return once N purchase queries answered. */
        fun awaitQueries(count: Int, seconds: Long = 5): Boolean {
            var remaining = count
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (remaining > 0 && System.nanoTime() < deadline) {
                if (client.queryAnswered.await(50, TimeUnit.MILLISECONDS)) remaining--
            }
            return remaining == 0
        }

        /** Bounded, event-driven wait: return once the Nth cache attempt landed. */
        fun awaitCacheAttempts(count: Int, seconds: Long = 5): Boolean {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                synchronized(cacheWrites) { if (cacheWrites.size >= count) return true }
                cacheWriteAttempt.await(50, TimeUnit.MILLISECONDS)
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

    /** Runs [block] on the IO scope and bounds it in time (no hangs, no sleeps). */
    private fun bounded(seconds: Long = 5, block: suspend () -> Unit) {
        runBlocking {
            withTimeout(TimeUnit.SECONDS.toMillis(seconds)) { block() }
        }
    }

    // -------------------------------------------------------------- tests --

    /** J: Play success + failing cache -> authority, ack and bookkeeping all hold. */
    @Test
    fun cacheFailureDoesNotPreventAuthorityAcknowledgementOrSuccessBookkeeping() {
        val f = Fixture()
        val glass = "sound_glass"
        val pack = "theme_pack"
        val pendingItem = "sound_wood"
        f.script.purchases = listOf(
            purchase(glass, Purchase.PurchaseState.PURCHASED, acknowledged = false),
            purchase(pack, Purchase.PurchaseState.PURCHASED),
            purchase(pendingItem, Purchase.PurchaseState.PURCHASED),
        )
        try {
            f.repo.seedCached(emptySet())
            f.repo.connect()
            // Bounded, event-driven wait: the purchase query has completed when
            // the pipeline reached the cache write (strictly AFTER the authority
            // commit, same locked section), so the assertions below are stable.
            assertTrue("cache write attempt must have happened", f.awaitCacheAttempts(1))
            bounded {
                // J.1: authority owned == the Play snapshot, cache failure or not.
                assertEquals(setOf(glass, pack, pendingItem), f.repo.owned.first())
                // J.2: pending == the empty pending set Play reported.
                assertEquals(emptySet<String>(), f.repo.pending.first())
                // J.3: ownershipReady stays correct.
                assertTrue(f.repo.ownershipReady.first())
            }
            // J.4: acknowledgement was still attempted (exactly the unacknowledged one).
            assertTrue("acknowledgement must be attempted", f.client.acknowledged.await(5, TimeUnit.SECONDS))
            assertEquals(1, f.script.acknowledgeCalls)
            // J.5: no PURCHASE_QUERY_FAILED state exists after the success.
            assertEquals(null, f.repo.error.value)
            // J.6: the cache write was attempted with the exact authoritative
            // snapshot; its failure changes nothing above.
            assertEquals(
                listOf(setOf(glass, pack, pendingItem)),
                synchronized(f.cacheWrites) { f.cacheWrites.toList() },
            )
        } finally {
            f.teardown()
        }
    }

    /** J.b: a genuine Play failure still sets PURCHASE_QUERY_FAILED. */
    @Test
    fun genuinePlayFailureStillSetsThePurchaseQueryError() {
        val f = Fixture()
        f.cacheOk = true
        f.script.queryOk = false
        try {
            f.repo.seedCached(emptySet())
            f.repo.connect()
            assertTrue("purchase query must complete", f.awaitQueries(1))
            bounded {
                assertEquals(R.string.error_purchase_query, f.repo.error.first())
            }
            assertEquals(0, f.script.acknowledgeCalls)
            assertTrue(f.cacheWrites.isEmpty())
        } finally {
            f.teardown()
        }
    }

    /**
     * K: cache recovery — the next successful refresh persists the cache
     * normally and mutates nothing about Play being the authority; stale cached
     * ownership is never resurrected over a Play answer.
     */
    @Test
    fun recoveredCacheWritesNormallyWithoutResurrectingStaleOwnership() {
        val f = Fixture()
        val glass = "sound_glass"
        f.script.purchases = listOf(purchase(glass, Purchase.PurchaseState.PURCHASED))
        try {
            f.repo.seedCached(emptySet())
            f.repo.connect()
            assertTrue("purchase query must complete", f.awaitQueries(1))
            bounded {
                assertEquals(setOf(glass), f.repo.owned.first())
            }

            // Cache recovers; Play answers again — with a REVOKED snapshot.
            f.cacheOk = true
            f.script.purchases = emptyList()
            f.repo.refreshPurchases()
            assertTrue("second purchase query must complete", f.awaitQueries(2))
            bounded {
                assertEquals(emptySet<String>(), f.repo.owned.first())
            }
            // The second cache write committed normally (empty Play snapshot)...
            assertEquals(emptySet<String>(), synchronized(f.cacheWrites) { f.cacheWrites.last() })
            // ...but Play's (empty, authoritative) snapshot was never overwritten.
            assertEquals(emptySet<String>(), f.repo.owned.value)
            assertEquals(null, f.repo.error.value)
        } finally {
            f.teardown()
        }
    }
}
