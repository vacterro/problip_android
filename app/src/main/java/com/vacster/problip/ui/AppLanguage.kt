package com.vacster.problip.ui

/**
 * The supported explicit application languages (runtime crash fix).
 *
 * The language model used to be a nullable `(String?, String)` list with a null
 * tag standing in for English, while the dialog rendered System Default as its
 * own separate row: selecting English then dereferenced the null tag
 * (`forLanguageTags(tag!!)`) and crashed. It also made an explicit "en"
 * application locale indistinguishable from System Default.
 *
 * The corrected model: System Default is represented ONLY by the empty AppCompat
 * locale list — never by a list entry — and every explicit language carries a
 * real, non-null BCP-47 tag. There is no second persisted language setting:
 * AppCompat per-app locales remain the sole authority, and System Default still
 * applies exactly `LocaleListCompat.getEmptyLocaleList()`.
 */
internal enum class AppLanguage(
    val tag: String,
    val endonym: String,
) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    ESTONIAN("et", "Eesti"),
    JAPANESE("ja", "日本語"),
    ;

    companion object {
        /**
         * Resolves the AppCompat application-locale list to the selected explicit
         * language, or null for System Default (the empty locale list). An
         * explicit "en" resolves to [ENGLISH] — never to System Default. Any
         * unsupported tag falls back to System Default for selection display.
         */
        fun fromLocaleList(empty: Boolean, firstLanguage: String?): AppLanguage? =
            if (empty) {
                null
            } else {
                entries.firstOrNull { it.tag == firstLanguage }
            }
    }
}
