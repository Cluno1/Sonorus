package io.github.cluno1.sonorus.shared.presentation.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchPolicyTest {
    @Test
    fun songQueryMatchesTitleArtistAndAlbumIgnoringCaseAndOuterWhitespace() {
        assertTrue(matchesUniversalSongQuery("  fountain  ", "Fountain of Life", "Asaph", "ihope"))
        assertTrue(matchesUniversalSongQuery("ASAPH", "Fountain of Life", "Asaph", "ihope"))
        assertTrue(matchesUniversalSongQuery(" IHOPE ", "Fountain of Life", "Asaph", "ihope"))
        assertFalse(matchesUniversalSongQuery("missing", "Fountain of Life", "Asaph", "ihope"))
        assertFalse(matchesUniversalSongQuery("   ", "Fountain of Life", "Asaph", "ihope"))
    }

    @Test
    fun albumQueryMatchesAlbumMetadataAndContainedSongTitles() {
        assertTrue(matchesUniversalAlbumQuery(" iHOPE ", "ihope", "Various Artists", emptyList()))
        assertTrue(matchesUniversalAlbumQuery("various", "ihope", "Various Artists", emptyList()))
        assertTrue(matchesUniversalAlbumQuery("生命之泉", "ihope", "Various Artists", listOf("生命之泉")))
        assertFalse(matchesUniversalAlbumQuery("missing", "ihope", "Various Artists", listOf("生命之泉")))
    }

    @Test
    fun managedCatalogSongsNeverExposeLegacyMoreActions() {
        assertFalse(
            shouldShowLegacySongOptions(
                mode = "LOCAL",
                songId = "rhythm-catalog:rendition:11111111-1111-4111-8111-111111111111",
            ),
        )
        assertTrue(shouldShowLegacySongOptions(mode = "LOCAL", songId = "42"))
        assertTrue(
            shouldShowLegacySongOptions(
                mode = "STREAMING",
                songId = "rhythm-catalog:rendition:11111111-1111-4111-8111-111111111111",
            ),
        )
    }

    @Test
    fun lanSongsNeverExposeDeviceMutationActions() {
        assertFalse(
            shouldShowLegacySongOptions(
                mode = "LOCAL",
                songId = "SUBSONIC::track-42",
            ),
        )
    }

    @Test
    fun projectedLanResultsStayInStreamingMode() {
        assertEquals("STREAMING", effectiveUniversalSearchItemMode("LOCAL", "STREAMING"))
        assertEquals("LOCAL", effectiveUniversalSearchItemMode("LOCAL", null))
        assertEquals("STREAMING", effectiveUniversalSearchItemMode("STREAMING", "LOCAL"))
    }

    @Test
    fun emptyStateWaitsForInitialCatalogLoadToFinish() {
        assertFalse(
            shouldShowUniversalSearchEmptyState(
                hasResults = false,
                isLocalLoading = true,
                isStreamingLoading = false,
            ),
        )
        assertFalse(
            shouldShowUniversalSearchEmptyState(
                hasResults = true,
                isLocalLoading = false,
                isStreamingLoading = false,
            ),
        )
        assertTrue(
            shouldShowUniversalSearchEmptyState(
                hasResults = false,
                isLocalLoading = false,
                isStreamingLoading = false,
            ),
        )
    }
}
