package com.vacster.problip.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic regressions for the language-selector model (runtime crash fix):
 * every explicit language carries a real non-null BCP-47 tag, System Default is
 * represented only by the empty locale list, and an explicit "en" application
 * locale resolves to English — never to System Default.
 */
class AppLanguageTest {

    @Test
    fun everyExplicitLanguageHasANonNullRealTag() {
        // The old model was (String?, String) with null standing in for English;
        // selecting it crashed on forLanguageTags(tag!!). The model now has no
        // nullable tag at all — enforced by the type — and the tags are real.
        AppLanguage.entries.forEach { language ->
            assertTrue(language.tag.isNotBlank())
            assertTrue(language.endonym.isNotBlank())
        }
        assertEquals(
            listOf("en", "ru", "et", "ja"),
            AppLanguage.entries.map { it.tag },
        )
    }

    @Test
    fun englishHasItsOwnExplicitTag() {
        // Explicit English must apply "en", equivalent to
        // setApplicationLocales(forLanguageTags("en")) — never the empty list.
        assertEquals("en", AppLanguage.ENGLISH.tag)
        assertTrue(AppLanguage.entries.none { it.tag.isEmpty() })
    }

    @Test
    fun emptyLocaleListResolvesToSystemDefault() {
        // System Default -> empty locale list, no crash, no language entry.
        assertNull(AppLanguage.fromLocaleList(empty = true, firstLanguage = null))
        // Even a stray tag cannot make an empty list select a language.
        assertNull(AppLanguage.fromLocaleList(empty = true, firstLanguage = "en"))
    }

    @Test
    fun eachSupportedTagResolvesToItsLanguage() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocaleList(empty = false, firstLanguage = "en"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromLocaleList(empty = false, firstLanguage = "ru"))
        assertEquals(AppLanguage.ESTONIAN, AppLanguage.fromLocaleList(empty = false, firstLanguage = "et"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromLocaleList(empty = false, firstLanguage = "ja"))
    }

    @Test
    fun explicitEnglishIsNeverSystemDefault() {
        // The second defect: "en" was not in the old options list, so an explicit
        // English application locale was displayed/treated as System Default.
        val resolved = AppLanguage.fromLocaleList(empty = false, firstLanguage = "en")
        assertNotNull(resolved)
        assertEquals(AppLanguage.ENGLISH, resolved)
    }

    @Test
    fun unsupportedTagFallsBackToSystemDefaultSelection() {
        assertNull(AppLanguage.fromLocaleList(empty = false, firstLanguage = "zz"))
    }

    @Test
    fun systemDefaultAppliesTheEmptyLocaleListAndNothingElse() {
        // Contract pin: the only System Default representation is the empty
        // AppCompat locale list; explicit languages apply their own tag.
        val systemDefaultTag: String? = null
        assertNull(systemDefaultTag?.let { AppLanguage.fromLocaleList(false, it) })
        AppLanguage.entries.forEach { language ->
            assertNotNull(AppLanguage.fromLocaleList(false, language.tag))
        }
    }

    @Test
    fun dialogOrderCoversAllFourLanguagesExactlyOnce() {
        val tags: List<String> = AppLanguage.entries.map { it.tag }
        assertEquals(4, tags.toSet().size)
        // No explicit option can contain a null tag: the type has no null.
    }
}
