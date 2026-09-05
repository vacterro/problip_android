package com.vacster.problip

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the in-app locale override to contexts outside AppCompatActivity
 * (service, notification, widget). On Android 13+ the system applies per-app
 * locales itself and [AppCompatDelegate.getApplicationLocales] mirrors it;
 * below 13 the override lives only inside AppCompat, so resource lookups made
 * from an application or service context need this wrap.
 */
fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
