package com.vacster.problip.widget

import com.vacster.problip.core.ProblipState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget's whole decision surface. Android-free on purpose: RemoteViews needs
 * no test, the START/STOP contract does. Localization moved the labels into
 * resources, so the pure layer returns SEMANTIC states and the updater maps
 * them — these pins hold the mapping stable.
 */
class WidgetStateTest {

    @Test
    fun stoppedOffersStart() {
        val ui = widgetUi(ProblipState.STOPPED)
        assertEquals(WidgetStatus.OFF, ui.status)
        assertEquals(WidgetAction.START, ui.action)
    }

    @Test
    fun runningOffersStop() {
        val ui = widgetUi(ProblipState.RUNNING)
        assertEquals(WidgetStatus.RUNNING, ui.status)
        assertEquals(WidgetAction.STOP, ui.action)
    }

    @Test
    fun startingOffersStop() {
        // A session being set up must be cancellable, or a slow start traps the user.
        val ui = widgetUi(ProblipState.STARTING)
        assertEquals(WidgetStatus.STARTING, ui.status)
        assertEquals(WidgetAction.STOP, ui.action)
    }

    @Test
    fun errorOffersStart() {
        // ERROR is terminal: the next tap starts a fresh session, it does not stop a dead one.
        val ui = widgetUi(ProblipState.ERROR)
        assertEquals(WidgetStatus.ERROR, ui.status)
        assertEquals(WidgetAction.START, ui.action)
    }

    @Test
    fun everyStatusAndActionHasADistinctResourceMapping() {
        // The updater's when() must cover the whole enum: distinct resources per
        // status, and exactly one button resource per action.
        val statusResources =
            WidgetStatus.entries.map { status ->
                ProblipWidgetUpdater.statusResFor(status)
            }
        assertEquals(statusResources.distinct(), statusResources)
        val actionResources =
            WidgetAction.entries.map { action ->
                ProblipWidgetUpdater.buttonResFor(action)
            }
        assertEquals(actionResources.distinct(), actionResources)
    }
}
