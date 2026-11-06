package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** issue 163/170: when the icon-only lyrics language button appears, and which menu it opens. */
class LyricsLanguageButtonPolicyTest {

    @Test
    fun severalCatalogLanguagesOpenTheLanguageMenuEvenWithTranslations() {
        assertEquals(LyricsLanguageButtonKind.CATALOG, lyricsLanguageButtonKind(2, hasTranslation = false))
        assertEquals(LyricsLanguageButtonKind.CATALOG, lyricsLanguageButtonKind(3, hasTranslation = true))
    }

    @Test
    fun lineTranslationsWithoutCatalogLanguagesOpenOriginalTranslationMenu() {
        assertEquals(LyricsLanguageButtonKind.TRANSLATION, lyricsLanguageButtonKind(0, hasTranslation = true))
    }

    @Test
    fun nothingToSwitchShowsNoButton() {
        assertEquals(LyricsLanguageButtonKind.NONE, lyricsLanguageButtonKind(0, hasTranslation = false))
        // One catalog language: a single choice is a fake entry, even if lines carry translations.
        assertEquals(LyricsLanguageButtonKind.NONE, lyricsLanguageButtonKind(1, hasTranslation = true))
        assertEquals(LyricsLanguageButtonKind.NONE, lyricsLanguageButtonKind(1, hasTranslation = false))
    }
}
