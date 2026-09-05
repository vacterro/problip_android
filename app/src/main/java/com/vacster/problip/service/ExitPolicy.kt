package com.vacster.problip.service

import com.vacster.problip.core.ProblipState

/**
 * EXIT policy: the service is asked to stop only when a live session could
 * exist. STOPPED needs no service, and ERROR has already released every
 * terminal resource — starting a service just to stop it would create the
 * very foreground session EXIT is meant to tear down. STARTING and RUNNING
 * own a foreground service that must receive ACTION_STOP first.
 */
object ExitPolicy {
    fun shouldStopServiceBeforeExit(state: ProblipState): Boolean =
        state == ProblipState.STARTING || state == ProblipState.RUNNING
}
