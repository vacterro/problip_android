package com.vacster.problip.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.vacster.problip.MainActivity
import com.vacster.problip.R
import com.vacster.problip.service.ProblipSession
import com.vacster.problip.withAppLocale

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
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            // Launchers that report no size keep the compact baseline: the mark
            // alone carries the brand, and nothing is clipped guessing at space.
            val minWidthDp =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    .takeIf { it > 0 }
                    ?: COMPACT_BASELINE_WIDTH_DP
            val titleVisible = WidgetPresentation.titleVisible(minWidthDp)
            manager.updateAppWidget(id, render(context, titleVisible))
        }
    }

    /** The width a size-ignorant launcher is treated as: compact, 1x1 baseline. */
    internal const val COMPACT_BASELINE_WIDTH_DP = 40

    /** Resource mapping the pure state pins in [WidgetStateTest] hold stable. */
    internal fun statusResFor(status: WidgetStatus): Int =
        when (status) {
            WidgetStatus.OFF -> R.string.widget_status_off
            WidgetStatus.STARTING -> R.string.widget_status_starting
            WidgetStatus.RUNNING -> R.string.widget_status_running
            WidgetStatus.ERROR -> R.string.widget_status_error
        }

    internal fun buttonResFor(action: WidgetAction): Int =
        when (action) {
            WidgetAction.START -> R.string.widget_button_start
            WidgetAction.STOP -> R.string.widget_button_stop
        }

    private fun render(context: Context, titleVisible: Boolean): RemoteViews {
        // RemoteViews text is resolved here, outside any activity, so the
        // in-app locale override has to be applied by hand below Android 13.
        val localized = context.withAppLocale()
        val ui = widgetUi(ProblipSession.state.value)
        val statusLabel = localized.getString(statusResFor(ui.status))
        val buttonLabel = localized.getString(buttonResFor(ui.action))
        return RemoteViews(context.packageName, R.layout.widget_problip).apply {
            setTextViewText(R.id.widget_status, statusLabel)
            setTextViewText(R.id.widget_button, "[ $buttonLabel ]")
            setViewVisibility(R.id.widget_title, if (titleVisible) View.VISIBLE else View.GONE)
            setOnClickPendingIntent(R.id.widget_button, togglePendingIntent(context))
            // The mark and (when shown) the title open the app: the widget itself
            // must never ask for a runtime permission, and the full UI is where
            // that flow lives.
            setOnClickPendingIntent(R.id.widget_mark, openAppPendingIntent(context))
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
