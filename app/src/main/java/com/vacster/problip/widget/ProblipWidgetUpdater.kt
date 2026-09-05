package com.vacster.problip.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vacster.problip.MainActivity
import com.vacster.problip.R
import com.vacster.problip.service.ProblipSession

/**
 * Renders every widget instance from the live session state. Called from real
 * lifecycle events only (widget tap, app START/STOP, service RUNNING, notification
 * STOP, playback ERROR, teardown) — there is no periodic refresh.
 */
object ProblipWidgetUpdater {

    /** Refreshes all placed instances; a no-op when the user has no widget. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, ProblipWidgetProvider::class.java))
        update(context, manager, ids)
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, render(context))
    }

    private fun render(context: Context): RemoteViews {
        val ui = widgetUi(ProblipSession.state.value)
        return RemoteViews(context.packageName, R.layout.widget_problip).apply {
            setTextViewText(R.id.widget_status, ui.status)
            setTextViewText(R.id.widget_button, "[ ${ui.buttonLabel} ]")
            setOnClickPendingIntent(R.id.widget_button, togglePendingIntent(context))
            // The title opens the app: the widget itself must never ask for a
            // runtime permission, and the full UI is where that flow lives.
            setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent(context))
        }
    }

    private fun togglePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TOGGLE,
        Intent(context, ProblipWidgetProvider::class.java)
            .setAction(ProblipWidgetProvider.ACTION_TOGGLE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Distinct from the notification's request codes so no PendingIntent is reused. */
    private const val REQUEST_TOGGLE = 100
    private const val REQUEST_OPEN = 101
}
