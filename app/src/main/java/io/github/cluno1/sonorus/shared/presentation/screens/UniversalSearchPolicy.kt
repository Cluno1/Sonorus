package io.github.cluno1.sonorus.shared.presentation.screens

import io.github.cluno1.sonorus.features.catalog.domain.CATALOG_SONG_ID_PREFIX
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import java.util.Locale

internal enum class UniversalSearchSubmitSource { USER_INPUT, HISTORY, GENRE }

/** Explicit searches enter recent searches even without results; genre shortcuts are not user input. */
internal fun universalSearchHistoryEntry(text: String, source: UniversalSearchSubmitSource): String? =
    text.trim().takeIf { it.isNotEmpty() && source != UniversalSearchSubmitSource.GENRE }

internal fun normalizedUniversalSearchQuery(query: String): String =
    query.trim().lowercase(Locale.ROOT)

internal fun matchesUniversalSongQuery(
    query: String,
    title: String,
    artist: String,
    album: String,
    tags: Iterable<String> = emptyList(),
): Boolean {
    val normalized = normalizedUniversalSearchQuery(query)
    if (normalized.isEmpty()) return false
    return title.lowercase(Locale.ROOT).contains(normalized) ||
        artist.lowercase(Locale.ROOT).contains(normalized) ||
        album.lowercase(Locale.ROOT).contains(normalized) ||
        tags.any { it.lowercase(Locale.ROOT).contains(normalized) }
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

/** issue 168: rows each search group shows before its "view all" card, as the songs group does. */
internal const val UNIVERSAL_SEARCH_PREVIEW_COUNT = 3

/** issue 168: the group whose full list is open over the search results. */
internal enum class UniversalSearchAllPage { SONGS, LYRICS, EXTERNAL_LYRICS, PUBLIC_PLAYLISTS, SCORES }

/**
 * issue 168: a group offers "view all" when its full list holds more than the preview, or when more
 * results can still be loaded (public playlists are paged).
 */
internal fun shouldShowUniversalViewAll(
    total: Int,
    previewCount: Int = UNIVERSAL_SEARCH_PREVIEW_COUNT,
    hasMore: Boolean = false,
): Boolean = total > previewCount || (hasMore && total > 0)

/**
 * issue 168: what "add to queue" on the "view all songs" page appends, in the order the page lists
 * them — the same songs, in the same order, that "play all" plays. Public results that cannot be
 * turned into a playable song are left out rather than added as broken entries.
 */
internal fun <Song, Public> universalSearchQueueSongs(
    localSongs: List<Song>,
    publicSongs: List<Public>,
    toPlayable: (Public) -> Song?,
): List<Song> = localSongs + publicSongs.mapNotNull(toPlayable)
