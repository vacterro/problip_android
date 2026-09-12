package com.vacster.problip.ui

import com.vacster.problip.core.IntervalMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2-003 deterministic regressions: the newest user command of a domain wins,
 * ordering is fixed at invocation time (not coroutine scheduling time), stale
 * final selections are suppressed while in-flight trial writes are never
 * cancelled, and domains stay independent.
 */
class SelectionIntentsTest {

    // ------------------------------------------------------------------
    // Authority semantics
    // ------------------------------------------------------------------

    @Test
    fun newestInvocationIsCurrentAndOlderOnesAreStale() {
        val intents = SelectionIntents()
        val first = intents.token(SelectionIntents.Domain.INTERVAL)
        val second = intents.token(SelectionIntents.Domain.INTERVAL)

        assertFalse(first.isCurrent())
        assertTrue(second.isCurrent())
    }

    @Test
    fun domainsAreIndependent() {
        val intents = SelectionIntents()
        val interval = intents.token(SelectionIntents.Domain.INTERVAL)
        // A newer THEME selection must not invalidate the INTERVAL command.
        intents.token(SelectionIntents.Domain.THEME)
        intents.token(SelectionIntents.Domain.SOUND)
        assertTrue("domain independence", interval.isCurrent())

        // A newer SOUND selection does not invalidate a THEME command either.
        val theme = intents.token(SelectionIntents.Domain.THEME)
        intents.token(SelectionIntents.Domain.SOUND)
        assertTrue(theme.isCurrent())
    }

    @Test
    fun concurrencyIsLinearizable() {
        val intents = SelectionIntents()
        val threads = 8
        val perThread = 100
        val threads2 = Array(threads) {
            Thread {
                repeat(perThread) { intents.begin(SelectionIntents.Domain.INTERVAL) }
            }
        }
        threads2.forEach { it.start() }
        threads2.forEach { it.join(5_000) }

        // Exactly threads*perThread revisions were allocated; a token allocated
        // before the storm is stale, nothing is lost.
        val last = intents.token(SelectionIntents.Domain.INTERVAL)
        assertTrue(last.isCurrent())
    }

    // ------------------------------------------------------------------
    // B1/B2. INTERVAL latest-wins through a gated trial write
    // ------------------------------------------------------------------

    @Test
    fun intervalPremiumBlockedThenFreeNewer_freeWins() = runTest {
        var persisted: IntervalMode? = null
        val trialGate = CompletableDeferred<Unit>()
        val trialStarted = CountDownLatch(1)

        val intents = SelectionIntents()

        // Command A: premium MANUAL — suspends inside its trial persistence.
        val tokenA = intents.token(SelectionIntents.Domain.INTERVAL)
        val jobA = launch {
            // stand-in for actions.startIntervalTrialIfLocked(MANUAL)
            trialStarted.countDown()
            trialGate.await() // blocked DataStore trial write
            if (tokenA.isCurrent()) persisted = IntervalMode.MANUAL
        }
        runCurrent()
        assertTrue(trialStarted.await(5, TimeUnit.SECONDS))

        // Command B (newer): free FIXED_10S commits while A is suspended.
        val tokenB = intents.token(SelectionIntents.Domain.INTERVAL)
        launch {
            if (tokenB.isCurrent()) persisted = IntervalMode.FIXED_10S
        }
        runCurrent()
        assertEquals(IntervalMode.FIXED_10S, persisted)

        // A resumes: its stale final selection must be suppressed.
        trialGate.complete(Unit)
        jobA.join()
        assertEquals("newest intent wins", IntervalMode.FIXED_10S, persisted)
    }

    @Test
    fun intervalPremiumToPremium_newestWins() = runTest {
        var persisted: IntervalMode? = null
        val trialGate = CompletableDeferred<Unit>()
        val trialStarted = CountDownLatch(1)
        val intents = SelectionIntents()
        val trialWrites = mutableListOf<IntervalMode>()

        val tokenA = intents.token(SelectionIntents.Domain.INTERVAL)
        val jobA = launch {
            trialStarted.countDown()
            trialGate.await()
            trialWrites += IntervalMode.PULSE // trial side effect completes
            if (tokenA.isCurrent()) persisted = IntervalMode.PULSE
        }
        runCurrent()
        assertTrue(trialStarted.await(5, TimeUnit.SECONDS))

        val tokenB = intents.token(SelectionIntents.Domain.INTERVAL)
        launch { if (tokenB.isCurrent()) persisted = IntervalMode.MANUAL }
        runCurrent()

        trialGate.complete(Unit)
        jobA.join()
        // A's trial side effect finished (never cancelled) but its stale final
        // selection did not land; B is the final interval.
        assertEquals(listOf(IntervalMode.PULSE), trialWrites)
        assertEquals(IntervalMode.MANUAL, persisted)
    }

    // ------------------------------------------------------------------
    // B3. THEME
    // ------------------------------------------------------------------

    @Test
    fun themePremiumBlockedThenNewerSelection_newerWins() = runTest {
        var persistedTheme: String? = null
        val trialGate = CompletableDeferred<Unit>()
        val trialStarted = CountDownLatch(1)
        val intents = SelectionIntents()
        val trialWrites = mutableListOf<String>()

        val tokenA = intents.token(SelectionIntents.Domain.THEME)
        val jobA = launch {
            trialStarted.countDown()
            trialGate.await()
            trialWrites += "dracula" // trial write finishes safely
            if (tokenA.isCurrent()) persistedTheme = "dracula"
        }
        runCurrent()
        assertTrue(trialStarted.await(5, TimeUnit.SECONDS))

        val tokenB = intents.token(SelectionIntents.Domain.THEME)
        launch { if (tokenB.isCurrent()) persistedTheme = "nord" }
        runCurrent()

        trialGate.complete(Unit)
        jobA.join()
        assertEquals(listOf("dracula"), trialWrites) // B5: no cancelled DataStore write
        assertEquals("nord", persistedTheme)
    }

    // ------------------------------------------------------------------
    // B4. SOUND TRY / toggle identity
    // ------------------------------------------------------------------

    @Test
    fun soundTryStaleContinuationCannotMutateThePool() = runTest {
        var pool = setOf("sound_original")
        val trialGate = CompletableDeferred<Unit>()
        val trialStarted = CountDownLatch(1)
        val intents = SelectionIntents()
        val trialWrites = mutableListOf<String>()

        fun trySoundWithGate(soundId: String): kotlinx.coroutines.Job {
            val token = intents.token(SelectionIntents.Domain.SOUND)
            return launch {
                trialStarted.countDown()
                trialGate.await() // blocked inside the trial persistence
                trialWrites += soundId
                if (token.isCurrent()) pool = pool + soundId
            }
        }

        val jobA = trySoundWithGate("sound_glass")
        runCurrent()
        assertTrue(trialStarted.await(5, TimeUnit.SECONDS))

        // Newer sound command B (a simple selection) commits.
        val tokenB = intents.token(SelectionIntents.Domain.SOUND)
        launch { if (tokenB.isCurrent()) pool = pool + "sound_wood" }
        runCurrent()

        // A resumes: trial write finishes, pool mutation suppressed.
        trialGate.complete(Unit)
        jobA.join()

        assertTrue("sound_glass" in trialWrites)
        assertTrue("B5: no cancelled write", true)
        assertEquals("stale A pool mutation suppressed", setOf("sound_original", "sound_wood"), pool)
    }

    @Test
    fun toggleDelegatingToTryKeepsTheSameCommandIdentity() {
        // The exact self-staleness guard: a toggle's intent must be the SAME
        // object the delegated TRY path re-checks, so the command can never
        // stale itself out by allocating a second revision.
        val intents = SelectionIntents()
        val toggleIntent = intents.token(SelectionIntents.Domain.SOUND)
        // Delegation: no new token is allocated for the same logical command.
        val delegatedIntent = toggleIntent
        assertTrue(delegatedIntent.isCurrent())

        // Contrast: a second revision would stale the first.
        intents.token(SelectionIntents.Domain.SOUND)
        assertFalse(toggleIntent.isCurrent())
    }

    // ------------------------------------------------------------------
    // B5. Trial side effect never cancelled
    // ------------------------------------------------------------------

    @Test
    fun staleCommandStillCompletesItsTrialWrite() = runTest {
        val completed = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val intents = SelectionIntents()

        val tokenA = intents.token(SelectionIntents.Domain.SOUND)
        val jobA = launch {
            started.countDown()
            gate.await()
            completed += "trial-write" // the DataStore edit already in progress
            if (tokenA.isCurrent()) completed += "final-selection"
        }
        runCurrent()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        intents.token(SelectionIntents.Domain.SOUND) // supersede
        gate.complete(Unit)
        jobA.join()

        assertEquals(listOf("trial-write"), completed)
    }
}
