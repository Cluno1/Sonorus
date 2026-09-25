/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.streaming.domain.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Strict identity boundary for the private, read-only LAN Subsonic player. */
object LanSubsonicPlaybackPolicy {
    const val MAX_LIBRARY_SONGS = 20_000
    const val MEDIA_ID_PREFIX = "SUBSONIC::"
    const val CACHE_KEY_PREFIX = "lan-subsonic:"

    private val trackIdPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")

    fun trackIdFromMediaId(mediaId: String?): String? {
        val value = mediaId?.takeIf { it.startsWith(MEDIA_ID_PREFIX) } ?: return null
        return value.removePrefix(MEDIA_ID_PREFIX).takeIf(trackIdPattern::matches)
    }

    fun mediaId(trackId: String): String {
        require(trackIdPattern.matches(trackId)) { "Invalid LAN Subsonic track id" }
        return "$MEDIA_ID_PREFIX$trackId"
    }

    fun cacheKey(mediaId: String): String? =
        trackIdFromMediaId(mediaId)?.let { "$CACHE_KEY_PREFIX$it" }

    fun playbackUri(mediaId: String): String {
        require(trackIdFromMediaId(mediaId) != null) { "Invalid LAN Subsonic media id" }
        val encodedMediaId = URLEncoder.encode(mediaId, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "streaming://track/$encodedMediaId"
    }

    fun mediaIdFromPlaybackUri(uri: String?): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (!parsed.scheme.equals("streaming", ignoreCase = true)) return null
        if (!parsed.host.equals("track", ignoreCase = true)) return null
        if (parsed.userInfo != null || parsed.rawQuery != null || parsed.fragment != null) return null
        val segments = parsed.rawPath.orEmpty().trim('/').split('/')
        if (segments.size != 1 || segments.single().isBlank()) return null
        val decoded = runCatching {
            URLDecoder.decode(segments.single(), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return decoded.takeIf { trackIdFromMediaId(it) != null }
    }

    fun allowsMediaSessionItem(
        enabled: Boolean,
        mediaId: String?,
        uri: String?,
        customCacheKey: String?,
    ): Boolean {
        if (!enabled || mediaId.isNullOrBlank()) return false
        val trackId = trackIdFromMediaId(mediaId) ?: return false
        if (mediaIdFromPlaybackUri(uri) != mediaId) return false
        return customCacheKey == "$CACHE_KEY_PREFIX$trackId"
    }

    fun allowsDataSpec(enabled: Boolean, uri: String?, customCacheKey: String?): Boolean {
        val mediaId = mediaIdFromPlaybackUri(uri) ?: return false
        return allowsMediaSessionItem(enabled, mediaId, uri, customCacheKey)
    }

    fun isLanSong(enabled: Boolean, mediaId: String?, uri: String?): Boolean =
        enabled && trackIdFromMediaId(mediaId) != null && mediaIdFromPlaybackUri(uri) == mediaId
}
