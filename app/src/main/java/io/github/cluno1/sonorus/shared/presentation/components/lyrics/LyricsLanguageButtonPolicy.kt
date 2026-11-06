package io.github.cluno1.sonorus.shared.presentation.components.lyrics

/** issue 163/170: what the lyrics language side button offers. */
internal enum class LyricsLanguageButtonKind { NONE, CATALOG, TRANSLATION }

/**
 * Catalog lyrics with more than one effective language → language menu. Lyrics with no catalog
 * languages but line translations → 原/译/原译. Anything else (one catalog language, or nothing to
 * switch) → no button: a button with a single choice is a fake entry.
 */
internal fun lyricsLanguageButtonKind(catalogLanguageCount: Int, hasTranslation: Boolean): LyricsLanguageButtonKind =
    when {
        catalogLanguageCount > 1 -> LyricsLanguageButtonKind.CATALOG
        catalogLanguageCount == 0 && hasTranslation -> LyricsLanguageButtonKind.TRANSLATION
        else -> LyricsLanguageButtonKind.NONE
    }
