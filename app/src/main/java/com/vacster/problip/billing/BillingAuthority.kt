package com.vacster.problip.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One linearizable authority boundary for Billing ownership and connection
 * lifecycle transitions.
 *
 * The persisted ownership cache may initialize an offline process before Play
 * answers, but once Play has answered, Play ownership is irreversible authority
 * for the process — including an EMPTY owned set, which is exactly what a refund
 * or a revocation looks like. A stale cache operation must never resurrect
 * ownership after Play has spoken, so the admissibility check and the ownership
 * publication of a cache seed must be one atomic transition, not two reads of
 * independently thread-safe fields.
 *
 * The same boundary owns the connection state machine: two racing
 * connect()/refresh() callers must produce exactly one BillingClient connection
 * attempt, and stale callbacks from an older connection attempt must not regress
 * a newer one. Each attempt gets a monotonically increasing generation; callbacks
 * carry their generation and are accepted only while they still belong to the
 * current attempt.
 *
 * The class is deliberately Android-free and free of suspend calls: every
 * transition is a short monitor-guarded field update, so it is deterministically
 * testable on the JVM. Suspending work (the local cache write) stays outside.
 */
class BillingAuthority {

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

    private val lock = Any()

    /** Newest connection attempt; callbacks from older attempts are stale. */
    private var connectionGeneration = 0L

    /** Only the first cache seed is accepted; later ones are no-ops. */
    private var seeded = false

    /**
     * Whether Play itself has answered a purchase query in this process. Once it
     * has, its answer is the authority even when EMPTY — a refund or a revocation
     * is exactly an empty answer, so the cache may not be re-applied over it.
     */
    private var playAnswered = false

    /**
     * Offline convenience seed from the local cache; never overrides Play truth.
     * One atomic transition: the admissibility check and the publication happen
     * under the same monitor, so a stale seed continuation can never land after
     * a Play answer has committed.
     */
    fun seedCached(cached: Set<String>) {
        synchronized(lock) {
            if (!seeded) {
                seeded = true
                if (seedableFromCache(current = _owned.value, cached = cached, playAnswered = playAnswered)) {
                    _owned.value = cached
                }
            }
            // Marked initialized on every seed attempt, matching the original
            // repository semantics: an empty first-install cache still counts.
            _ownershipReady.value = true
        }
    }

    /**
     * Commit a successful Play purchase query as the authoritative snapshot.
     * The EMPTY Play result is authoritative — it is not special-cased as
     * "no answer". Returns the exact authoritative owned snapshot the caller
     * must use for the (suspending, lock-free) cache write.
     */
    fun applyPlaySnapshot(owned: Set<String>, pending: Set<String>): Set<String> {
        synchronized(lock) {
            playAnswered = true
            _owned.value = owned
            _pending.value = pending
            _ownershipReady.value = true
            return owned
        }
    }

    /**
     * Atomically claim the DISCONNECTED -> CONNECTING transition. Returns the new
     * generation when the caller won the race and only that caller may invoke
     * BillingClient.startConnection(); returns null when a connection is already
     * being attempted or is established.
     */
    fun beginConnect(): Long? {
        synchronized(lock) {
            if (_connection.value != BillingConnection.DISCONNECTED) return null
            connectionGeneration += 1
            _connection.value = BillingConnection.CONNECTING
            return connectionGeneration
        }
    }

    /**
     * Setup result of the attempt [generation]. Accepted only while that
     * generation is still current AND still CONNECTING (a stale or superseded
     * callback must not regress a newer attempt or re-open a closed one).
     * Returns true when the caller may fire the accepted side effects
     * (refresh purchases / query products, or surface the store error).
     */
    fun onSetupFinished(generation: Long, setupOk: Boolean): Boolean {
        synchronized(lock) {
            if (generation != connectionGeneration) return false
            if (_connection.value != BillingConnection.CONNECTING) return false
            _connection.value = if (setupOk) BillingConnection.CONNECTED else BillingConnection.DISCONNECTED
            return true
        }
    }

    /**
     * Disconnect callback of attempt [generation]. Accepted whenever it still
     * belongs to the current generation and the state machine is mid-flight or
     * established (CONNECTING or CONNECTED), so a current-generation disconnect
     * can never leave the state stuck; a stale generation's callback cannot
     * regress a newer attempt. Returns true when accepted.
     */
    fun onDisconnected(generation: Long): Boolean {
        synchronized(lock) {
            if (generation != connectionGeneration) return false
            if (_connection.value == BillingConnection.DISCONNECTED) return false
            _connection.value = BillingConnection.DISCONNECTED
            return true
        }
    }

    /**
     * BillingClient.startConnection threw synchronously after [generation] was
     * accepted: return that same generation to DISCONNECTED so state cannot stay
     * stuck in CONNECTING. No broader exception policy lives here.
     */
    fun abortConnect(generation: Long) {
        synchronized(lock) {
            if (generation == connectionGeneration && _connection.value == BillingConnection.CONNECTING) {
                _connection.value = BillingConnection.DISCONNECTED
            }
        }
    }
}
