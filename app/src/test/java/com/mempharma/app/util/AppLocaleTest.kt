package com.mempharma.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the language preference mapping. Turning the preference
 * into localized resources needs an Android runtime, so only the stateless
 * helpers are exercised here.
 */
class AppLocaleTest {

    @Test
    fun blankOrMissing_meansSystem() {
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise(null))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise(""))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise("   "))
    }

    @Test
    fun englishTags_mapToEnglish() {
        assertEquals(AppLocale.ENGLISH, AppLocale.normalise("en"))
        assertEquals(AppLocale.ENGLISH, AppLocale.normalise("en-GB"))
        assertEquals(AppLocale.ENGLISH, AppLocale.normalise("EN"))
    }

    @Test
    fun portugueseTags_mapToPortuguese() {
        assertEquals(AppLocale.PORTUGUESE, AppLocale.normalise("pt"))
        assertEquals(AppLocale.PORTUGUESE, AppLocale.normalise("pt-PT"))
        assertEquals(AppLocale.PORTUGUESE, AppLocale.normalise("pt-BR"))
        assertEquals(AppLocale.PORTUGUESE, AppLocale.normalise("PT-pt"))
    }

    @Test
    fun systemIsRecognisedCaseInsensitively() {
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise("system"))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise("SYSTEM"))
    }

    @Test
    fun unknownValue_fallsBackToSystem() {
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise("fr"))
        assertEquals(AppLocale.SYSTEM, AppLocale.normalise("not-a-language"))
    }

    @Test
    fun onlyRealLanguages_forceATag() {
        assertNull(AppLocale.forcedTag(AppLocale.SYSTEM))
        assertNull(AppLocale.forcedTag(null))
        assertNull(AppLocale.forcedTag("fr"))
        assertEquals(AppLocale.PORTUGUESE, AppLocale.forcedTag("pt-BR"))
        assertEquals(AppLocale.ENGLISH, AppLocale.forcedTag("en-AU"))
    }

    @Test
    fun selectableLanguages_areUniqueAndOfferSystemFirst() {
        assertEquals(AppLocale.SYSTEM, AppLocale.selectableTags.first())
        assertEquals(AppLocale.selectableTags.size, AppLocale.selectableTags.distinct().size)
        assertTrue(AppLocale.selectableTags.contains(AppLocale.PORTUGUESE))
        assertTrue(AppLocale.selectableTags.contains(AppLocale.ENGLISH))
    }
}
