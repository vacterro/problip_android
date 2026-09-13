package com.vacster.problip.settings

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * W2-004: the ONE write-failure contract of every SettingsRepository mutation.
 *
 * Expected DataStore I/O failures (a full disk, an unwritable store) are
 * runtime conditions, not crashes: the mutation returns [IoFailure] and the
 * persisted store keeps its last committed value. CancellationException stays
 * cancellation — it is rethrown, never translated into a result. Any other
 * Throwable is a programming failure and propagates uncontrolled, exactly
 * like the read side (`data.catch { if (it is IOException) ... else throw it }`).
 */
sealed interface SettingsWriteResult {
    /** DataStore committed the edit. */
    data object Success : SettingsWriteResult

    /**
     * Expected DataStore I/O failure; the edit did NOT commit. The persisted
     * value therefore remains the last successfully persisted value, and this
     * result is the only signal a caller gets — no exception escapes.
     */
    data object IoFailure : SettingsWriteResult

    companion object {
        /**
         * The single internal policy every SettingsRepository mutation runs
         * through: catch expected IOException, rethrow cancellation, let
         * anything unexpected propagate.
         */
        internal suspend fun <T> editPolicy(block: suspend () -> T): SettingsWriteResult =
            try {
                block()
                Success
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                IoFailure
            }
    }
}
