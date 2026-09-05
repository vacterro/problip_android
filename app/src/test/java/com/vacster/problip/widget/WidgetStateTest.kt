package com.vacster.problip.widget

import com.vacster.problip.core.ProblipState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget's whole decision surface. Android-free on purpose: RemoteViews needs
 * no test, the START/STOP contract does.
 */
class WidgetStateTest {

    @Test
    fun stoppedOffersStart() {
        val ui = widgetUi(ProblipState.STOPPED)
        assertEquals("OFF", ui.status)
        assertEquals("START", ui.buttonLabel)
        assertEquals(WidgetAction.START, ui.action)
    }

    @Test
    fun runningOffersStop() {
        val ui = widgetUi(ProblipState.RUNNING)
        assertEquals("RUNNING", ui.status)
        assertEquals(WidgetAction.STOP, ui.action)
    }

    @Test
    fun startingOffersStop() {
        // A session being set up must be cancellable, or a slow start traps the user.
        val ui = widgetUi(ProblipState.STARTING)
        assertEquals("STARTING", ui.status)
        assertEquals(WidgetAction.STOP, ui.action)
    }

    @Test
    fun errorOffersStart() {
        // ERROR is terminal: the next tap starts a fresh session, it does not stop a dead one.
        val ui = widgetUi(ProblipState.ERROR)
        assertEquals("ERROR", ui.status)
        assertEquals(WidgetAction.START, ui.action)
    }
}
