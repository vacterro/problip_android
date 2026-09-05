package com.vacster.problip.widget

import com.vacster.problip.core.ProblipState

/** What the widget's single button does when tapped. */
enum class WidgetAction { START, STOP }

/** Everything the widget renders, derived only from the live session state. */
data class WidgetUi(
    val status: String,
    val buttonLabel: String,
    val action: WidgetAction,
)

/**
 * Pure widget state mapping, so the START/STOP contract is testable without a
 * launcher. The widget deliberately keeps no running flag of its own: a stored
 * boolean would survive process death and offer STOP for a session that no
 * longer exists. [ProblipState] stays the single authority.
 */
fun widgetUi(state: ProblipState): WidgetUi = when (state) {
    ProblipState.STOPPED -> WidgetUi("OFF", "START", WidgetAction.START)
    ProblipState.STARTING -> WidgetUi("STARTING", "STOP", WidgetAction.STOP)
    ProblipState.RUNNING -> WidgetUi("RUNNING", "STOP", WidgetAction.STOP)
    ProblipState.ERROR -> WidgetUi("ERROR", "START", WidgetAction.START)
}
