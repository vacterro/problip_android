package com.vacster.problip.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.vacster.problip.service.ProblipService
import com.vacster.problip.service.ProblipSession

/**
 * Home-screen START/STOP for the real session. The widget owns no scheduler, no
 * audio engine and no persisted state: it renders [ProblipSession] and toggles
 * [ProblipService], so rapid taps are absorbed by the service's idempotent START
 * and a START during ERROR goes through the normal session lifecycle.
 *
 * Deliberately absent: configuration UI, sound/interval controls, theme sync,
 * BOOT_COMPLETED resurrection and periodic updates.
 */
class ProblipWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        ProblipWidgetUpdater.update(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        toggle(context)
        // Immediate feedback; the process-level state observer refreshes again
        // once the service actually reaches its next state.
        ProblipWidgetUpdater.updateAll(context)
    }

    private fun toggle(context: Context) {
        // A widget tap is an explicit user action, so starting the foreground
        // service from the background is permitted. The platform can still refuse
        // (restricted bucket, OEM policy) and that must not crash the launcher.
        try {
            when (widgetUi(ProblipSession.state.value).action) {
                WidgetAction.START -> ProblipService.start(context)
                WidgetAction.STOP -> ProblipService.stop(context)
            }
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) extends this.
            // Nothing to recover: the widget keeps showing the unchanged state.
        } catch (e: SecurityException) {
            // Same outcome for an OEM policy refusal.
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.vacster.problip.widget.action.TOGGLE"
    }
}
