package com.vacster.problip

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.vacster.problip.service.ProblipService
import com.vacster.problip.widget.ProblipWidgetUpdater

/**
 * W2-005: ONE process-level locale-invalidation signal for the external render
 * owners (foreground notification, notification-channel metadata, home-screen
 * widgets).
 *
 * AppCompat per-app locales stay the SOLE language authority — nothing here
 * persists a second language setting. The signal is deliberately dumb: the
 * current application-locale language-tag list compared against the list that
 * produced the last external render. A different list (explicit language pick,
 * System Default switch, or a system-locale change reflected into the empty
 * per-app list) means every already-rendered external surface may show stale
 * text and must re-render.
 *
 * Propagation is event-driven, never polled:
 * 1. an explicit in-app language pick fires [notifyLocaleApplied] directly
 *    after AppCompat applies the new list;
 * 2. a system-locale change arrives as ACTION_LOCALE_CHANGED, received by the
 *    manifest-registered [LocaleChangeReceiver];
 * 3. both paths land in the same place: the running ProblipService re-renders
 *    its channel + notification, and all widgets re-render. No scheduler,
 *    audio engine or session state is touched.
 */
object LocaleEpoch {

    /** Custom action: the app itself applied a new per-app locale list. */
    const val ACTION_LOCALE_APPLIED = "com.vacster.problip.action.LOCALE_APPLIED"

    /** Custom extra carrying the applied tag list; diagnostic only. */
    const val EXTRA_TAGS = "tags"

    /** Manifest-registered system action (Android 13+ self-explanatory name). */
    const val SYSTEM_LOCALE_CHANGED = "android.intent.action.LOCALE_CHANGED"

    /**
     * True when the external surfaces need re-rendering: the current
     * application-locale tag list differs from [renderedTags] (the list that
     * was current at the last external render). Pure; unit-testable.
     */
    fun isStale(renderedTags: String?, currentTags: String?): Boolean =
        currentTags.orEmpty() != renderedTags.orEmpty()

    /** Normalized snapshot of [locales] for later [isStale] comparisons. */
    fun snapshotOf(locales: LocaleListCompat): String = locales.toLanguageTags()

    /** The application's current locale list (AppCompat authority mirror). */
    fun current(): LocaleListCompat = AppCompatDelegate.getApplicationLocales()

    /**
     * Fire the app-internal invalidation event after an explicit language
     * pick. Exported=false explicit-intent broadcasts to our own receiver do
     * not need the background-broadcast exemption below Android 13, and on
     * 13+ the same receiver also listens for the system action.
     */
    fun notifyLocaleApplied(context: Context, tags: String) {
        context.sendBroadcast(
            Intent(ACTION_LOCALE_APPLIED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_TAGS, tags),
        )
    }
}

/**
 * Manifest-registered receiver: one small invalidation path covering BOTH the
 * explicit app-language selection (custom action) and system-locale changes
 * (ACTION_LOCALE_CHANGED). It only re-renders; it owns no state and starts no
 * component, so there is no scheduler, service-lifecycle or audio side effect.
 */
class LocaleChangeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LocaleEpoch.ACTION_LOCALE_APPLIED,
            LocaleEpoch.SYSTEM_LOCALE_CHANGED,
            -> {
                ProblipWidgetUpdater.updateAll(context)
                ProblipService.refreshLocale(context)
            }
        }
    }
}
