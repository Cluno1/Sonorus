package io.github.cluno1.sonorus.features.catalog.domain

import java.util.Locale

/**
 * issue 127: Chinese lyrics from public music platforms, served by the backend's
 * `/v2/library/external-lyrics/…` endpoints (an internal Meting provider). Read-only: these lyrics
 * are never stored in a Work.
 */
enum class CatalogExternalLyricsSource(val wireValue: String) {
    NETEASE("netease"),
    TENCENT("tencent"),
    KUGOU("kugou");

    companion object {
        fun fromWire(value: String?): CatalogExternalLyricsSource? =
            entries.firstOrNull { it.wireValue == value?.trim() }
    }
}

data class CatalogExternalLyricsItem(
    val source: CatalogExternalLyricsSource,
    val externalId: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Long?,
    /** `title` when the title contains the query, otherwise `lyrics` (same rule as issue 107). */
    val matchField: String,
    /** The matched lyric line; only NetEase lyric hits carry one. */
    val snippet: String?,
) {
    /** Stable list key: the same id may exist on two platforms. */
    val key: String get() = "${source.wireValue}:$externalId"
}

data class CatalogExternalLyricsSearchResult(
    val query: String,
    val items: List<CatalogExternalLyricsItem>,
    val unavailableSources: List<String>,
)

data class CatalogExternalLyrics(
    val source: CatalogExternalLyricsSource,
    val externalId: String,
    /** Plain text, timestamps and credit lines already removed by the backend. */
    val lyrics: String,
    val translation: String?,
)

/** "3:47" for search rows; null when the platform reported no length. */
fun formatExternalLyricsDuration(durationMs: Long?): String? {
    if (durationMs == null || durationMs <= 0) return null
    val totalSeconds = durationMs / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
