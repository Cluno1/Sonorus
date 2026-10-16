package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogWorkNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogWorkLyricsPolicyTest {
    private val renditionId = "33333333-3333-4333-8333-333333333333"
    private val albumId = "66666666-6666-4666-8666-666666666666"

    @Test
    fun mapsKnownAndUnknownNumberKeys() {
        assertEquals("ihope", workNumberKeyLabel("ihope_num"))
        assertEquals("Heaven Song", workNumberKeyLabel("heavenSong_num"))
        assertEquals("hymnal", workNumberKeyLabel("hymnal_num"))
        assertEquals("custom", workNumberKeyLabel("custom"))
        assertEquals("Heaven Song 352", CatalogWorkNumber("heavenSong_num", 352).displayLabel())
    }

    @Test
    fun formatsNumbersInServerOrder() {
        val numbers = listOf(
            CatalogWorkNumber("other_num", 7),
            CatalogWorkNumber("heavenSong_num", 352),
            CatalogWorkNumber("ihope_num", 256),
        )
        assertEquals("ihope 256 · Heaven Song 352 · other 7", formatWorkNumbers(numbers))
        assertEquals("", formatWorkNumbers(emptyList()))
    }

    @Test
    fun numberSearchOnlyRunsForDigitQueries() {
        assertTrue(isWorkNumberQuery("256"))
        assertTrue(isWorkNumberQuery(" 2 56 "))
        assertFalse(isWorkNumberQuery(""))
        assertFalse(isWorkNumberQuery("   "))
        assertFalse(isWorkNumberQuery("256a"))
        assertFalse(isWorkNumberQuery("-1"))
        assertFalse(isWorkNumberQuery("２５６"))
    }

    @Test
    fun stripsLrcTimestampsFromWorkLyrics() {
        assertEquals(
            "上主坚定的爱\n永不止息",
            workLyricsDisplayText("[00:01.20]上主坚定的爱\n[00:05.00][00:30.00]永不止息\n"),
        )
        assertEquals("[Chorus] Amen", workLyricsDisplayText("[Chorus] Amen"))
    }

    @Test
    fun playableSongsSurviveNavigationArgumentRoundTrip() {
        val songs = listOf(CatalogLibrarySearchSong(renditionId, albumId, "Title", trackNo = 3))
        val decoded = decodeWorkLyricsSongs(encodeWorkLyricsSongs(songs), "Title")
        assertEquals(listOf(CatalogLibrarySearchSong(renditionId, albumId, "Title", null)), decoded)
    }

    @Test
    fun emptySongListIsEncodedAsKnownEmpty() {
        assertEquals(NO_PLAYABLE_WORK_SONGS, encodeWorkLyricsSongs(emptyList()))
        assertTrue(decodeWorkLyricsSongs(NO_PLAYABLE_WORK_SONGS, "Title").isEmpty())
        assertTrue(decodeWorkLyricsSongs(null, "Title").isEmpty())
        assertTrue(decodeWorkLyricsSongs("broken,a:b:c", "Title").isEmpty())
    }
}
