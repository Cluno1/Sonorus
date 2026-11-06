package io.github.cluno1.sonorus.shared.presentation.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchPolicyTest {
    @Test
    fun explicitSearchesEnterRecentSearchesTrimmed() {
        assertEquals("周杰伦", universalSearchHistoryEntry("  周杰伦 ", UniversalSearchSubmitSource.USER_INPUT))
        assertEquals("周杰伦", universalSearchHistoryEntry("周杰伦", UniversalSearchSubmitSource.HISTORY))
    }

    @Test
    fun genreShortcutsAndBlankTextDoNotEnterRecentSearches() {
        assertEquals(null, universalSearchHistoryEntry("Rock", UniversalSearchSubmitSource.GENRE))
        assertEquals(null, universalSearchHistoryEntry("   ", UniversalSearchSubmitSource.USER_INPUT))
    }

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

    @Test
    fun `结果不多于预览条数时不出现查看全部`() {
        assertFalse(shouldShowUniversalViewAll(total = 0))
        assertFalse(shouldShowUniversalViewAll(total = UNIVERSAL_SEARCH_PREVIEW_COUNT))
        assertTrue(shouldShowUniversalViewAll(total = UNIVERSAL_SEARCH_PREVIEW_COUNT + 1))
    }

    @Test
    fun `歌单还能继续加载时即使只有一页预览也出现查看全部`() {
        assertTrue(shouldShowUniversalViewAll(total = 2, hasMore = true))
        assertFalse(shouldShowUniversalViewAll(total = 0, hasMore = true))
    }

    @Test
    fun `添加到队列按页面顺序每首恰好加入一次并跳过无法播放的公共结果`() {
        val enqueued = mutableListOf<String>()
        universalSearchQueueSongs(
            localSongs = listOf("local-1", "local-2"),
            publicSongs = listOf("public-1", "broken", "public-2"),
        ) { public -> public.takeUnless { it == "broken" }?.let { "song:$it" } }
            .forEach { enqueued += it }

        assertEquals(listOf("local-1", "local-2", "song:public-1", "song:public-2"), enqueued)
    }
}
