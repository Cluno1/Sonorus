/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.local.data.model

import io.github.cluno1.sonorus.features.catalog.domain.CATALOG_SONG_ID_PREFIX
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtworkPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import java.net.URI

enum class UnifiedPlaylistSource {
    DEVICE_OR_LEGACY,
    CATALOG,
    LAN_SUBSONIC,
}

/** Stable-source rules for phone-owned playlists that can mix all supported libraries. */
object UnifiedPlaylistPolicy {
    fun source(mediaId: String): UnifiedPlaylistSource = when {
        mediaId.startsWith(CATALOG_SONG_ID_PREFIX) -> UnifiedPlaylistSource.CATALOG
        LanSubsonicPlaybackPolicy.trackIdFromMediaId(mediaId) != null ->
            UnifiedPlaylistSource.LAN_SUBSONIC
        else -> UnifiedPlaylistSource.DEVICE_OR_LEGACY
    }

    fun canonicalMediaId(mediaId: String): String = when (source(mediaId)) {
        UnifiedPlaylistSource.CATALOG -> CATALOG_SONG_ID_PREFIX + mediaId
            .removePrefix(CATALOG_SONG_ID_PREFIX)
            .substringBefore(":asset:")
        UnifiedPlaylistSource.LAN_SUBSONIC,
        UnifiedPlaylistSource.DEVICE_OR_LEGACY,
        -> mediaId
    }

    fun canonicalUri(mediaId: String, uri: String): String = when (source(mediaId)) {
        UnifiedPlaylistSource.CATALOG -> {
            val renditionId = canonicalMediaId(mediaId)
                .removePrefix(CATALOG_SONG_ID_PREFIX)
            CatalogPlaybackPolicy.deferredUri(renditionId)
        }
        UnifiedPlaylistSource.LAN_SUBSONIC ->
            LanSubsonicPlaybackPolicy.playbackUri(mediaId)
        UnifiedPlaylistSource.DEVICE_OR_LEGACY -> uri
    }

    fun canonicalArtworkUri(mediaId: String, artworkUri: String?): String? =
        when (source(mediaId)) {
            UnifiedPlaylistSource.CATALOG -> artworkUri?.takeIf {
                CatalogArtworkPolicy.assetId(it) != null
            }
            UnifiedPlaylistSource.LAN_SUBSONIC -> artworkUri?.takeIf(::isPublicImageUrl)
            UnifiedPlaylistSource.DEVICE_OR_LEGACY -> artworkUri
        }

    /** Signed or authenticated URLs carry a query or user info and must never be persisted. */
    private fun isPublicImageUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return (uri.scheme == "https" || uri.scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null
    }

    fun mayPersistInLibrarySongTable(source: UnifiedPlaylistSource): Boolean =
        source == UnifiedPlaylistSource.DEVICE_OR_LEGACY
}
