package com.vacster.problip.ui

/**
 * W2-003: per-domain latest-user-intent authority.
 *
 * Several user commands persist a trial first, suspend in that DataStore write,
 * and only then perform their final selection mutation (interval preset, theme,
 * sound pool). A newer command can commit while an older one is suspended; when
 * the older one resumes it must not overwrite the newer intent. DataStore makes
 * each edit atomic, not the semantic command.
 *
 * Ordering is established at INVOCATION time — the revision is allocated
 * synchronously when the user action fires, before any coroutine launch or
 * readiness suspension — so coroutine scheduling can never reorder user intent.
 * In-flight DataStore writes are never cancelled: a stale command's trial side
 * effect finishes per the existing reusable-trial semantics; only its stale
 * final selection is suppressed.
 *
 * Domains are independent: a theme command never invalidates an interval
 * command, so unrelated selections do not serialize against each other.
 *
 * Android-free and deterministic on the JVM.
 */
class SelectionIntents {

    enum class Domain { INTERVAL, THEME, SOUND }

    /** One allocated user command: domain-bound revision + currency check. */
    class Token internal constructor(
        private val intents: SelectionIntents,
        val domain: Domain,
        internal val revision: Long,
    ) {
        /** True while this command is still the newest intent of its domain. */
        fun isCurrent(): Boolean = intents.isCurrent(domain, revision)
    }

    private val lock = Any()
    private val latest = HashMap<Domain, Long>()

    /** Allocates a new revision for [domain]; the caller's command is now newest. */
    fun begin(domain: Domain): Long = synchronized(lock) {
        val next = (latest[domain] ?: 0L) + 1
        latest[domain] = next
        next
    }

    /**
     * True when [revision] is still the newest allocated revision of [domain],
     * i.e. the command may perform its final selection mutation.
     */
    fun isCurrent(domain: Domain, revision: Long): Boolean = synchronized(lock) {
        latest[domain] == revision
    }

    /**
     * Allocates the caller's command token synchronously at invocation time.
     * Call this in the ViewModel entry point BEFORE viewModelScope.launch.
     */
    fun token(domain: Domain): Token = Token(this, domain, begin(domain))
}
