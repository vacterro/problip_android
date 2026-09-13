package com.vacster.problip.trial

/**
 * W2-004 Part D: the small neutral persistence result the trial package speaks,
 * so callers can distinguish "nothing needed persisting", "persisted" and
 * "persistence failed because of expected settings I/O" without the trial
 * package depending on the settings package. The ViewModel wires the real
 * SettingsRepository seams and maps its typed write result onto this.
 */
enum class PersistenceOutcome {
    /** Nothing had to be persisted (already granted, not trialable, suppressed). */
    NOT_REQUIRED,

    /** The persistence edit committed. */
    PERSISTED,

    /** Expected settings I/O failure; nothing was persisted. */
    IO_FAILED,
}
