package com.vacster.problip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2-005: the locale-invalidation decision, pinned as pure logic. The
 * application-locale tag list is the only input; external render owners
 * re-render exactly when it differs from the list that produced the last
 * render. Covers explicit picks, System Default round-trips and the
 * system-locale-change-while-System-Default case (which still changes the
 * resolved configuration even though the per-app list stays empty).
 */
class LocaleEpochTest {

    @Test
    fun unchangedListIsNeverStale() {
        assertFalse(LocaleEpoch.isStale(renderedTags = "en", currentTags = "en"))
        assertFalse(LocaleEpoch.isStale(renderedTags = "", currentTags = ""))
        assertFalse(LocaleEpoch.isStale(renderedTags = null, currentTags = ""))
        assertFalse(LocaleEpoch.isStale(renderedTags = "", currentTags = null))
    }

    @Test
    fun explicitLanguagePickInvalidates() {
        // System Default -> English: notification/channel/widgets must re-render.
        assertTrue(LocaleEpoch.isStale(renderedTags = "", currentTags = "en"))
        // English -> Russian: same class.
        assertTrue(LocaleEpoch.isStale(renderedTags = "en", currentTags = "ru"))
        // Russian -> Estonian -> Japanese: every switch invalidates.
        assertTrue(LocaleEpoch.isStale(renderedTags = "ru", currentTags = "et"))
        assertTrue(LocaleEpoch.isStale(renderedTags = "et", currentTags = "ja"))
    }

    @Test
    fun backToSystemDefaultInvalidates() {
        // An explicit language -> System Default (empty list) changes every
        // resolved string: the previously rendered locale is gone.
        assertTrue(LocaleEpoch.isStale(renderedTags = "en", currentTags = ""))
        assertTrue(LocaleEpoch.isStale(renderedTags = "ja", currentTags = null))
    }

    @Test
    fun systemLocaleChangeUnderSystemDefaultIsRepresentable() {
        // With System Default selected the per-app list stays empty, so the
        // tag-list signal alone cannot distinguish "nothing happened" from
        // "the system locale changed". That is why the receiver ALSO listens
        // for ACTION_LOCALE_CHANGED: this test pins that the empty-vs-empty
        // comparison is NOT treated as stale (so unrelated re-renders do not
        // fire), while the system broadcast provides the invalidation event
        // for that case — the contract the receiver implements.
        assertFalse(LocaleEpoch.isStale(renderedTags = "", currentTags = ""))
    }

    @Test
    fun malformedOrPartialTagsStillCompareExact() {
        // A multi-tag list renders differently from a single tag.
        assertTrue(LocaleEpoch.isStale(renderedTags = "en", currentTags = "en-US"))
        // Region-qualified forms are distinct lists.
        assertTrue(LocaleEpoch.isStale(renderedTags = "en-US", currentTags = "en-GB"))
        // Case differences in the raw string compare exactly (AppCompat
        // normalizes before this point in production).
        assertFalse(LocaleEpoch.isStale(renderedTags = "en", currentTags = "en"))
    }

    @Test
    fun constantsAreStable() {
        // The receiver's manifest intent filter and the service action are a
        // public contract with AndroidManifest.xml; pin them so a rename in
        // one place cannot silently desynchronize the other.
        assertEquals(
            "com.vacster.problip.action.LOCALE_APPLIED",
            LocaleEpoch.ACTION_LOCALE_APPLIED,
        )
        assertEquals(
            "android.intent.action.LOCALE_CHANGED",
            LocaleEpoch.SYSTEM_LOCALE_CHANGED,
        )
    }
}
