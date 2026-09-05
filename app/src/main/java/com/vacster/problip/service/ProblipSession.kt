package com.vacster.problip.service

import com.vacster.problip.core.ProblipState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live session state shared between the foreground service (writer) and the UI
 * (reader). Replaces the W3 ViewModel-owned runtime: the session now outlives
 * the activity.
 *
 * The state machine itself lives in [SessionLifecycle]; this object is only the
 * process-wide instance plus the flows the UI collects.
 */
object ProblipSession {
    // UI-only signal: no replay on return to Main, no persistence, no widget
    // observer, and no suspended audio producer if the UI is absent or busy.
    private val _blips = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val blips = _blips.asSharedFlow()

    internal fun reportPlayback(succeeded: Boolean) {
        if (succeeded) _blips.tryEmit(Unit)
    }

    private val _state = MutableStateFlow(ProblipState.STOPPED)
    val state: StateFlow<ProblipState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val lifecycle = SessionLifecycle { state, error ->
        _state.value = state
        _error.value = error
    }
}
