package io.github.cluno1.sonorus.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** issue 163: 原 / 译 / 原译 for line-translated lyrics. */
class LyricsTranslationModeTest {

    @Test
    fun settingsMapToTheThreeModes() {
        assertEquals(LyricsTranslationMode.ORIGINAL, LyricsTranslationMode.fromSettings(false, false))
        assertEquals(LyricsTranslationMode.ORIGINAL, LyricsTranslationMode.fromSettings(false, true))
        assertEquals(LyricsTranslationMode.TRANSLATION, LyricsTranslationMode.fromSettings(true, true))
        assertEquals(LyricsTranslationMode.BOTH, LyricsTranslationMode.fromSettings(true, false))
    }

    @Test
    fun eachModeShowsTheMatchingLines() {
        val original = "We were both young when I first saw you"
        val translation = "我第一次见到你时，我们都还年轻"

        assertEquals(original to null, lyricLineTexts(original, translation, LyricsTranslationMode.ORIGINAL))
        assertEquals(translation to null, lyricLineTexts(original, translation, LyricsTranslationMode.TRANSLATION))
        assertEquals(original to translation, lyricLineTexts(original, translation, LyricsTranslationMode.BOTH))
    }

    @Test
    fun lineWithoutTranslationKeepsItsOriginalInEveryMode() {
        LyricsTranslationMode.entries.forEach { mode ->
            assertEquals(mode.name, "Instrumental" to null, lyricLineTexts("Instrumental", " ", mode))
        }
    }
}
