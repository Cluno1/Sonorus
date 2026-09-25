/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.streaming.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSubsonicPlaybackPolicyTest {
    private val trackId = "cos-0123456789abcdef:mix.1"
    private val mediaId = "SUBSONIC::$trackId"
    private val uri = "streaming://track/SUBSONIC%3A%3Acos-0123456789abcdef%3Amix.1"
    private val cacheKey = "lan-subsonic:$trackId"

    @Test
    fun `accepts exact opaque identity`() {
        assertEquals(mediaId, LanSubsonicPlaybackPolicy.mediaIdFromPlaybackUri(uri))
        assertEquals(trackId, LanSubsonicPlaybackPolicy.trackIdFromMediaId(mediaId))
        assertEquals(cacheKey, LanSubsonicPlaybackPolicy.cacheKey(mediaId))
        assertTrue(
            LanSubsonicPlaybackPolicy.allowsMediaSessionItem(
                enabled = true,
                mediaId = mediaId,
                uri = uri,
                customCacheKey = cacheKey,
            ),
        )
    }

    @Test
    fun `builds percent encoded one segment URI`() {
        assertEquals(uri, LanSubsonicPlaybackPolicy.playbackUri(mediaId))
    }

    @Test
    fun `rejects disabled mismatched and arbitrary sources`() {
        assertFalse(LanSubsonicPlaybackPolicy.allowsMediaSessionItem(false, mediaId, uri, cacheKey))
        assertFalse(LanSubsonicPlaybackPolicy.allowsMediaSessionItem(true, mediaId, uri, "lan-subsonic:other"))
        assertFalse(
            LanSubsonicPlaybackPolicy.allowsMediaSessionItem(
                true,
                mediaId,
                "https://192.168.31.44:18086/rest/stream.view?id=$trackId",
                cacheKey,
            ),
        )
        assertFalse(
            LanSubsonicPlaybackPolicy.allowsMediaSessionItem(
                true,
                "JELLYFIN::$trackId",
                uri,
                cacheKey,
            ),
        )
        assertNull(LanSubsonicPlaybackPolicy.mediaIdFromPlaybackUri("streaming://other/$mediaId"))
        assertNull(LanSubsonicPlaybackPolicy.mediaIdFromPlaybackUri("streaming://track/$mediaId/extra"))
    }

    @Test
    fun `rejects invalid gateway track IDs`() {
        assertNull(LanSubsonicPlaybackPolicy.trackIdFromMediaId("SUBSONIC::with space"))
        assertNull(LanSubsonicPlaybackPolicy.trackIdFromMediaId("SUBSONIC::../secret"))
        assertNull(LanSubsonicPlaybackPolicy.trackIdFromMediaId("SUBSONIC::${"a".repeat(129)}"))
    }
}
