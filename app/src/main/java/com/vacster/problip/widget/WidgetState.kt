package com.vacster.problip.widget

import com.vacster.problip.core.ProblipState

/** What the widget's single button does when tapped. */
enum class WidgetAction { START, STOP }

/** Semantic widget status; [ProblipWidgetUpdater] localizes it for display. */
enum class WidgetStatus { OFF, STARTING, RUNNING, ERROR }

/** Everything the widget renders, derived only from the live session state. */
data class WidgetUi(
    val status: WidgetStatus,
    val action: WidgetAction,
)

/**
 * Pure widget state mapping, so the START/STOP contract is testable without a
 * launcher. The widget deliberately keeps no running flag of its own: a stored
 * boolean would survive process death and offer STOP for a session that no
 * longer exists. [ProblipState] stays the single authority.
 */
fun widgetUi(state: ProblipState): WidgetUi = when (state) {
    ProblipState.STOPPED -> WidgetUi(WidgetStatus.OFF, WidgetAction.START)
    ProblipState.STARTING -> WidgetUi(WidgetStatus.STARTING, WidgetAction.STOP)
    ProblipState.RUNNING -> WidgetUi(WidgetStatus.RUNNING, WidgetAction.STOP)
    ProblipState.ERROR -> WidgetUi(WidgetStatus.ERROR, WidgetAction.START)
}
