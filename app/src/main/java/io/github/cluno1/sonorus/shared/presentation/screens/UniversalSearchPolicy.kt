package io.github.cluno1.sonorus.shared.presentation.screens

import io.github.cluno1.sonorus.features.catalog.domain.CATALOG_SONG_ID_PREFIX
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import java.util.Locale

internal fun normalizedUniversalSearchQuery(query: String): String =
    query.trim().lowercase(Locale.ROOT)

internal fun matchesUniversalSongQuery(
    query: String,
    title: String,
    artist: String,
    album: String,
): Boolean {
    val normalized = normalizedUniversalSearchQuery(query)
    if (normalized.isEmpty()) return false
    return title.lowercase(Locale.ROOT).contains(normalized) ||
        artist.lowercase(Locale.ROOT).contains(normalized) ||
        album.lowercase(Locale.ROOT).contains(normalized)
}

internal fun matchesUniversalAlbumQuery(
    query: String,
    title: String,
    artist: String,
    songTitles: Iterable<String>,
): Boolean {
    val normalized = normalizedUniversalSearchQuery(query)
    if (normalized.isEmpty()) return false
    return title.lowercase(Locale.ROOT).contains(normalized) ||
        artist.lowercase(Locale.ROOT).contains(normalized) ||
        songTitles.any { it.lowercase(Locale.ROOT).contains(normalized) }
}

internal fun shouldShowLegacySongOptions(mode: String, songId: String?): Boolean =
    mode != "LOCAL" || (
        songId?.startsWith(CATALOG_SONG_ID_PREFIX) != true &&
            songId?.startsWith(LanSubsonicPlaybackPolicy.MEDIA_ID_PREFIX) != true
    )

internal fun effectiveUniversalSearchItemMode(
    itemMode: String,
    requiredAppModeForLocalItems: String?,
): String = if (itemMode == "LOCAL") {
    requiredAppModeForLocalItems ?: itemMode
} else {
    itemMode
}

internal fun shouldShowUniversalSearchEmptyState(
    hasResults: Boolean,
    isLocalLoading: Boolean,
    isStreamingLoading: Boolean,
): Boolean = !hasResults && !isLocalLoading && !isStreamingLoading
