package com.vacster.problip.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic JVM regressions for the W2-001 billing authority boundary:
 * cache seeding and Play snapshots publish through one linearizable transition,
 * connection begins are atomic, and stale callbacks cannot regress a newer
 * connection. No sleeps: barriers, latches and futures only.
 */
class BillingAuthorityTest {

    // ------------------------------------------------------------------
    // A. Play empty always beats cache (regardless of who wins the boundary)
    // ------------------------------------------------------------------

    @Test
    fun `play empty beats cache when cache seeds first`() {
        val authority = BillingAuthority()
        authority.seedCached(setOf("theme_pack"))
        authority.applyPlaySnapshot(owned = emptySet(), pending = emptySet())
        assertEquals(emptySet<String>(), authority.owned.value)
        assertTrue(authority.ownershipReady.value)
    }

    @Test
    fun `play empty beats cache when play answers first`() {
        val authority = BillingAuthority()
        authority.applyPlaySnapshot(owned = emptySet(), pending = emptySet())
        authority.seedCached(setOf("theme_pack"))
        assertEquals(emptySet<String>(), authority.owned.value)
        assertTrue(authority.ownershipReady.value)
    }

    @Test
    fun `racing cache seed and empty play answer always end at play truth`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val rounds = 200
            repeat(rounds) {
                val authority = BillingAuthority()
                val barrier = CyclicBarrier(2)
                val seedDone = CountDownLatch(1)
                val playDone = CountDownLatch(1)

                val cacheTask = pool.submit(Callable {
                    barrier.await()
                    authority.seedCached(setOf("theme_pack"))
                    seedDone.countDown()
                    true
                })
                val playTask = pool.submit(Callable {
                    barrier.await()
                    authority.applyPlaySnapshot(owned = emptySet(), pending = emptySet())
                    playDone.countDown()
                    true
                })
                cacheTask.get(5, TimeUnit.SECONDS)
                playTask.get(5, TimeUnit.SECONDS)

                assertTrue(seedDone.await(1, TimeUnit.SECONDS))
                assertTrue(playDone.await(1, TimeUnit.SECONDS))
                assertEquals("round $it", emptySet<String>(), authority.owned.value)
                assertTrue(authority.ownershipReady.value)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // B. Play different ownership always beats cache
    // ------------------------------------------------------------------

    @Test
    fun `racing cache seed and non-empty play answer always end at play set`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val rounds = 200
            repeat(rounds) {
                val authority = BillingAuthority()
                val barrier = CyclicBarrier(2)
                val cacheTask = pool.submit(Callable {
                    barrier.await()
                    authority.seedCached(setOf("theme_pack"))
                    true
                })
                val playTask = pool.submit(Callable {
                    barrier.await()
                    authority.applyPlaySnapshot(owned = setOf("sound_glass"), pending = emptySet())
                    true
                })
                cacheTask.get(5, TimeUnit.SECONDS)
                playTask.get(5, TimeUnit.SECONDS)
                assertEquals("round $it", setOf("sound_glass"), authority.owned.value)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // C. Offline cache still works
    // ------------------------------------------------------------------

    @Test
    fun `offline cache seeds ownership before play answers`() {
        val authority = BillingAuthority()
        authority.seedCached(setOf("theme_pack"))
        assertEquals(setOf("theme_pack"), authority.owned.value)
        assertTrue(authority.ownershipReady.value)
    }

    @Test
    fun `empty first-install cache still initializes ownership`() {
        val authority = BillingAuthority()
        authority.seedCached(emptySet())
        assertEquals(emptySet<String>(), authority.owned.value)
        assertTrue(authority.ownershipReady.value)
    }

    // ------------------------------------------------------------------
    // D. Play authority is irreversible
    // ------------------------------------------------------------------

    @Test
    fun `cache can never change owned after play answered empty`() {
        val authority = BillingAuthority()
        authority.applyPlaySnapshot(owned = emptySet(), pending = emptySet())
        authority.seedCached(setOf("theme_pack"))
        authority.seedCached(setOf("sound_glass"))
        assertEquals(emptySet<String>(), authority.owned.value)
    }

    @Test
    fun `cache can never change owned after play answered non-empty`() {
        val authority = BillingAuthority()
        authority.applyPlaySnapshot(owned = setOf("sound_wood"), pending = emptySet())
        authority.seedCached(setOf("theme_pack"))
        assertEquals(setOf("sound_wood"), authority.owned.value)
    }

    @Test
    fun `play snapshot does not resurrect cleared pending from cache`() {
        val authority = BillingAuthority()
        authority.applyPlaySnapshot(owned = emptySet(), pending = emptySet())
        authority.seedCached(setOf("theme_pack"))
        assertEquals(emptySet<String>(), authority.pending.value)
    }

    // ------------------------------------------------------------------
    // E. Cache seeds once
    // ------------------------------------------------------------------

    @Test
    fun `second cache seed does not replace the accepted initialization`() {
        val authority = BillingAuthority()
        authority.seedCached(setOf("sound_glass"))
        authority.seedCached(setOf("theme_pack"))
        assertEquals(setOf("sound_glass"), authority.owned.value)
        assertTrue(authority.ownershipReady.value)
    }

    @Test
    fun `second cache seed never applies after play answered`() {
        val authority = BillingAuthority()
        authority.seedCached(setOf("sound_glass"))
        authority.applyPlaySnapshot(owned = setOf("sound_glass"), pending = emptySet())
        authority.seedCached(setOf("theme_pack"))
        assertEquals(setOf("sound_glass"), authority.owned.value)
    }

    // ------------------------------------------------------------------
    // F. Connect race
    // ------------------------------------------------------------------

    @Test
    fun `concurrent beginConnect admits exactly one caller`() {
        val authority = BillingAuthority()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val barrier = CyclicBarrier(2)
            val admitted = AtomicInteger(0)
            val tasks = listOf(
                pool.submit(Callable {
                    barrier.await()
                    authority.beginConnect()?.also { admitted.incrementAndGet() }
                }),
                pool.submit(Callable {
                    barrier.await()
                    authority.beginConnect()?.also { admitted.incrementAndGet() }
                }),
            )
            val generations = tasks.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals("exactly one admitted", 1, admitted.get())
            assertEquals(BillingConnection.CONNECTING, authority.connection.value)

            val winner = generations.filterNotNull().single()
            assertNotNull(winner)
            assertTrue(generations.count { it == null } == 1)

            // The rejected caller retries only after the winner's attempt ends.
            authority.onDisconnected(winner)
            assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)
            val next = authority.beginConnect()
            assertNotNull(next)
            assertNotEquals(winner, next)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `beginConnect is refused while connecting or connected`() {
        val authority = BillingAuthority()
        val first = authority.beginConnect()
        assertNotNull(first)
        assertNull(authority.beginConnect())
        authority.onSetupFinished(first!!, setupOk = true)
        assertEquals(BillingConnection.CONNECTED, authority.connection.value)
        assertNull(authority.beginConnect())
    }

    // ------------------------------------------------------------------
    // G. Stale connection callback
    // ------------------------------------------------------------------

    @Test
    fun `stale generation callbacks never regress a newer connection`() {
        val authority = BillingAuthority()
        val gen1 = authority.beginConnect()!!
        // Generation 1 fails, then a newer attempt begins and connects.
        assertTrue(authority.onSetupFinished(gen1, setupOk = false))
        assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)

        val gen2 = authority.beginConnect()!!
        assertNotEquals(gen1, gen2)
        assertTrue(authority.onSetupFinished(gen2, setupOk = true))
        assertEquals(BillingConnection.CONNECTED, authority.connection.value)

        // Late gen1 callbacks must change nothing.
        assertFalse(authority.onSetupFinished(gen1, setupOk = true))
        assertFalse(authority.onDisconnected(gen1))
        assertEquals(BillingConnection.CONNECTED, authority.connection.value)
    }

    @Test
    fun `setup callback of a generation that is no longer connecting is ignored`() {
        val authority = BillingAuthority()
        val gen1 = authority.beginConnect()!!
        assertTrue(authority.onDisconnected(gen1))
        assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)

        // gen1 is still current but no longer CONNECTING: the late setup
        // callback must not re-open the connection.
        assertFalse(authority.onSetupFinished(gen1, setupOk = true))
        assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)
    }

    // ------------------------------------------------------------------
    // H. Current disconnect
    // ------------------------------------------------------------------

    @Test
    fun `current generation disconnect ends the connection and a new attempt is admitted`() {
        val authority = BillingAuthority()
        val gen1 = authority.beginConnect()!!
        assertTrue(authority.onSetupFinished(gen1, setupOk = true))
        assertEquals(BillingConnection.CONNECTED, authority.connection.value)

        assertTrue(authority.onDisconnected(gen1))
        assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)

        val gen2 = authority.beginConnect()
        assertNotNull(gen2)
        assertNotEquals(gen1, gen2)
        assertEquals(BillingConnection.CONNECTING, authority.connection.value)
    }

    // ------------------------------------------------------------------
    // I. Start contract: only the granted caller starts the client
    // ------------------------------------------------------------------

    @Test
    fun `only the granted caller invokes the start action`() {
        val authority = BillingAuthority()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val barrier = CyclicBarrier(2)
            val startCount = AtomicInteger(0)
            val tasks = (1..2).map {
                pool.submit(Callable {
                    barrier.await()
                    val generation = authority.beginConnect()
                    if (generation != null) {
                        // In BillingRepository this block is the only place
                        // BillingClient.startConnection is invoked.
                        startCount.incrementAndGet()
                        authority.onSetupFinished(generation, setupOk = true)
                    }
                    true
                })
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals("startConnection attempted once", 1, startCount.get())
            assertEquals(BillingConnection.CONNECTED, authority.connection.value)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `abortConnect releases a stuck connecting generation`() {
        val authority = BillingAuthority()
        val gen = authority.beginConnect()!!
        assertEquals(BillingConnection.CONNECTING, authority.connection.value)

        authority.abortConnect(gen)
        assertEquals(BillingConnection.DISCONNECTED, authority.connection.value)

        // A fresh attempt is admitted with a newer generation.
        val next = authority.beginConnect()
        assertNotNull(next)
        assertNotEquals(gen, next)
    }

    @Test
    fun `abortConnect does not touch a newer generation`() {
        val authority = BillingAuthority()
        val gen1 = authority.beginConnect()!!
        authority.abortConnect(gen1)
        val gen2 = authority.beginConnect()!!
        authority.abortConnect(gen1)
        assertEquals(BillingConnection.CONNECTING, authority.connection.value)
        authority.onSetupFinished(gen2, setupOk = true)
        assertEquals(BillingConnection.CONNECTED, authority.connection.value)
    }
}
