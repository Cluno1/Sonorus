/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.model

import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPlaylistPolicyTest {
    private val renditionId = "123e4567-e89b-42d3-a456-426614174000"

    @Test
    fun `classifies the three stable playlist sources`() {
        val lanId = LanSubsonicPlaybackPolicy.mediaId("cos-abc123")

        assertEquals(
            UnifiedPlaylistSource.DEVICE_OR_LEGACY,
            UnifiedPlaylistPolicy.source("42"),
        )
        assertEquals(
            UnifiedPlaylistSource.CATALOG,
            UnifiedPlaylistPolicy.source("rhythm-catalog:rendition:$renditionId"),
        )
        assertEquals(
            UnifiedPlaylistSource.LAN_SUBSONIC,
            UnifiedPlaylistPolicy.source(lanId),
        )
    }

    @Test
    fun `canonicalizes remote rows without persisting credentials or expiring urls`() {
        val lanId = LanSubsonicPlaybackPolicy.mediaId("onecloud-abc123")
        val lanUri = LanSubsonicPlaybackPolicy.playbackUri(lanId)

        assertEquals(
            "rhythm-catalog://rendition/$renditionId",
            UnifiedPlaylistPolicy.canonicalUri(
                "rhythm-catalog:rendition:$renditionId:asset:old",
                "https://expired.example/asset.mp3?token=secret",
            ),
        )
        assertEquals(lanUri, UnifiedPlaylistPolicy.canonicalUri(lanId, lanUri))
        assertEquals(
            lanUri,
            UnifiedPlaylistPolicy.canonicalUri(
                lanId,
                "http://192.168.1.10:18086/rest/stream?token=secret",
            ),
        )
        assertFalse(
            UnifiedPlaylistPolicy.canonicalUri(lanId, lanUri).contains("token="),
        )
        assertEquals(
            "rhythm-catalog:rendition:$renditionId",
            UnifiedPlaylistPolicy.canonicalMediaId(
                "rhythm-catalog:rendition:$renditionId:asset:old",
            ),
        )
    }

    @Test
    fun `keeps remote snapshots out of device song table`() {
        assertTrue(
            UnifiedPlaylistPolicy.mayPersistInLibrarySongTable(
                UnifiedPlaylistSource.DEVICE_OR_LEGACY,
            ),
        )
        assertFalse(
            UnifiedPlaylistPolicy.mayPersistInLibrarySongTable(UnifiedPlaylistSource.CATALOG),
        )
        assertFalse(
            UnifiedPlaylistPolicy.mayPersistInLibrarySongTable(UnifiedPlaylistSource.LAN_SUBSONIC),
        )
    }

    @Test
    fun `drops authenticated and expiring remote artwork urls`() {
        val lanId = LanSubsonicPlaybackPolicy.mediaId("track-42")
        assertEquals(
            null,
            UnifiedPlaylistPolicy.canonicalArtworkUri(
                lanId,
                "http://192.168.1.10/rest/getCoverArt?t=token&s=salt",
            ),
        )
        assertEquals(
            null,
            UnifiedPlaylistPolicy.canonicalArtworkUri(
                "rhythm-catalog:rendition:$renditionId",
                "https://bucket.cos.example/song.jpg?signature=temporary",
            ),
        )
        val stableCatalogArtwork =
            "rhythm-catalog://asset/123e4567-e89b-42d3-a456-426614174001"
        assertEquals(
            stableCatalogArtwork,
            UnifiedPlaylistPolicy.canonicalArtworkUri(
                "rhythm-catalog:rendition:$renditionId",
                stableCatalogArtwork,
            ),
        )
        assertEquals(
            "content://media/external/audio/albumart/42",
            UnifiedPlaylistPolicy.canonicalArtworkUri(
                "42",
                "content://media/external/audio/albumart/42",
            ),
        )
    }
}
