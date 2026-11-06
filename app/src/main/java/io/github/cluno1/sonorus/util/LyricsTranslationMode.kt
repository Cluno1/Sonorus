package io.github.cluno1.sonorus.util

import io.github.cluno1.sonorus.shared.data.model.LyricsData

/**
 * issue 163: which side of a line-translated LRC is shown — original, translation, or both.
 *
 * Stored as the existing `showLyricsTranslation` switch plus `lyricsTranslationOnly`, so the
 * player page and the full-screen lyrics page share one choice and the old switch keeps working.
 * This is only for ordinary lyrics; Catalog songs pick a whole lyrics language instead.
 * "Original" / "translation" follow the parser's main line and its attached translation; nothing
 * here guesses by script (e.g. "Chinese first").
 */
enum class LyricsTranslationMode {
    ORIGINAL,
    TRANSLATION,
    BOTH;

    companion object {
        fun fromSettings(showTranslation: Boolean, translationOnly: Boolean): LyricsTranslationMode = when {
            !showTranslation -> ORIGINAL
            translationOnly -> TRANSLATION
            else -> BOTH
        }
    }
}

/**
 * Texts to draw for one lyric line: `first` is the main line, `second` (both mode only) goes
 * below it at the same text size. A line without a translation always shows its original.
 */
fun lyricLineTexts(
    original: String,
    translation: String?,
    mode: LyricsTranslationMode,
): Pair<String, String?> {
    val usableTranslation = translation?.takeIf { it.isNotBlank() }
    return when (mode) {
        LyricsTranslationMode.ORIGINAL -> original to null
        LyricsTranslationMode.TRANSLATION -> (usableTranslation ?: original) to null
        LyricsTranslationMode.BOTH -> original to usableTranslation
    }
}

/** True when at least one parsed line carries a translation, i.e. the 原/译/原译 menu is useful. */
fun lyricsHaveTranslation(lyrics: LyricsData?): Boolean {
    if (lyrics == null || !lyrics.hasLyrics() || lyrics.isErrorMessage()) return false
    lyrics.getWordByWordLyricsOrNull()?.let { wordByWord ->
        val lines = runCatching { RhythmLyricsParser.parseWordByWordLyrics(wordByWord) }.getOrNull().orEmpty()
        if (lines.any { !it.translation.isNullOrBlank() }) return true
    }
    val text = lyrics.getBestLyrics() ?: return false
    return runCatching { LyricsParser.parseLyrics(text) }.getOrNull().orEmpty()
        .any { !it.translation.isNullOrBlank() }
}
