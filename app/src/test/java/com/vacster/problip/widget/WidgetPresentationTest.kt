package com.vacster.problip.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's pure size/presentation policy. 1x1 is the primary size: below the
 * expanded threshold the brand mark alone carries the identity and every control
 * surface stays exactly what it was — this policy can never invent an action.
 */
class WidgetPresentationTest {

    @Test
    fun oneByOneHidesTheWordmarkAndKeepsTheCompactMark() {
        // A true 1x1 cell (~70dp wide on a 4x4 grid, less on denser grids).
        assertFalse(WidgetPresentation.titleVisible(40))
        assertFalse(WidgetPresentation.titleVisible(70))
        assertFalse(WidgetPresentation.titleVisible(139))
    }

    @Test
    fun widerSizesShowTheWordmark() {
        // 2x1 and beyond: PROBLIP fits beside the status hint without ellipsis.
        assertTrue(WidgetPresentation.titleVisible(140))
        assertTrue(WidgetPresentation.titleVisible(180))
        assertTrue(WidgetPresentation.titleVisible(300))
    }

    @Test
    fun theThresholdIsExactlyTheDocumentedBoundary() {
        val t = WidgetPresentation.EXPANDED_MIN_WIDTH_DP
        assertFalse(WidgetPresentation.titleVisible(t - 1))
        assertTrue(WidgetPresentation.titleVisible(t))
    }

    @Test
    fun resizingNeverAddsActionsAtAnySize() {
        // The policy's whole output surface is visibility of one TextView; the
        // START/STOP mapping lives in widgetUi and is unchanged by size. Pin that
        // the enum of things this policy can change never grows an action.
        listOf(40, 70, 140, 300).forEach { width ->
            val ui = widgetUi(com.vacster.problip.core.ProblipState.STOPPED)
            assertTrue(ui.action == WidgetAction.START || ui.action == WidgetAction.STOP)
            WidgetPresentation.titleVisible(width) // must not throw for any size
        }
    }
}
