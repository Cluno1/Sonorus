package io.github.cluno1.sonorus.features.streaming.data.provider

import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogMusicApi
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicLyricsDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicMetadataWriteDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicPageDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSearchDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSongDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSourcesDto
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PublicMusicMetadataSyncTest {
    private val placeholder = "Unknown artist"

    private fun <T> failure(code: Int): Response<T> = Response.error(code, "".toResponseBody())

    private inner class FakeApi(
        val stored: Response<PublicMusicSongDto> = failure(404),
        val saveCode: Int = 200,
    ) : CatalogMusicApi {
        val writes = mutableListOf<PublicMusicMetadataWriteDto>()
        override suspend fun sources(): Response<PublicMusicSourcesDto> = error("unused")
        override suspend fun search(query: String, page: Int): Response<PublicMusicSearchDto> = error("unused")
        override suspend fun recommend(source: String, page: Int): Response<PublicMusicPageDto> = error("unused")
        override suspend fun metadata(source: String, song: String) = stored
        // The production adapter returns no artists from the song detail endpoint.
        override suspend fun song(source: String, song: String, refresh: Boolean) =
            Response.success(PublicMusicSongDto(source, song, qualities = listOf("mp3")))
        override suspend fun lyrics(source: String, song: String, refresh: Boolean) =
            Response.success(PublicMusicLyricsDto("[00:01.00]line"))
        override suspend fun saveMetadata(source: String, song: String, body: PublicMusicMetadataWriteDto): Response<PublicMusicSongDto> {
            writes += body
            if (saveCode != 200) return failure(saveCode)
            return Response.success(PublicMusicSongDto(source, song, title = body.title, artists = body.artists, lyricsLrc = body.lyricsLrc))
        }
    }

    private fun sync(api: CatalogMusicApi, artist: String, manual: Boolean = false) = runBlocking {
        syncPublicMusicMetadata(api, "feiniao", "1", "Song", artist, "", 0, null, manual, setOf(placeholder))
    }

    @Test
    fun streamPathLetsTheBackendChooseTheAudioFormat() {
        assertEquals("/v2/music/sources/feiniao/songs/1/stream?quality=auto", publicMusicStreamPath("feiniao", "1"))
    }

    @Test
    fun placeholderArtistIsNeverWrittenButLyricsStillReturn() {
        val api = FakeApi()
        val result = sync(api, placeholder)
        assertEquals(0, api.writes.size)
        assertTrue(result.writeSkipped)
        assertEquals("[00:01.00]line", result.song?.lyricsLrc)
    }

    @Test
    fun blankArtistIsNeverWritten() {
        val api = FakeApi()
        assertTrue(sync(api, "   ").writeSkipped)
        assertEquals(0, api.writes.size)
    }

    @Test
    fun realArtistIsWrittenExactlyOnce() {
        val api = FakeApi()
        val result = sync(api, "Asaph")
        assertEquals(1, api.writes.size)
        assertEquals(listOf("Asaph"), api.writes.single().artists)
        assertEquals("auto", api.writes.single().mode)
        assertFalse(result.writeSkipped)
    }

    @Test
    fun storedArtistsWinOverLocalArtistAndPlaceholder() {
        val stored = PublicMusicSongDto("feiniao", "1", title = "Song", artists = listOf("Stored"))
        val api = FakeApi(stored = Response.success(stored))
        sync(api, placeholder)
        assertEquals(listOf("Stored"), api.writes.single().artists)
    }

    @Test
    fun storedLyricsAreReturnedWithoutWriting() {
        val stored = PublicMusicSongDto("feiniao", "1", title = "Song", artists = listOf("Stored"), lyricsLrc = "x")
        val api = FakeApi(stored = Response.success(stored))
        assertEquals("x", sync(api, "Asaph").song?.lyricsLrc)
        assertEquals(0, api.writes.size)
    }

    @Test
    fun saveFailureCarriesKindAndHttpCode() {
        val failure = assertThrows(PublicMusicException::class.java) { sync(FakeApi(saveCode = 503), "Asaph") }
        assertEquals(PublicMusicException.Kind.METADATA_SAVE_FAILED, failure.kind)
        assertEquals(503, failure.httpCode)
    }

    @Test
    fun metadataReadFailureCarriesKindAndHttpCode() {
        val failure = assertThrows(PublicMusicException::class.java) { sync(FakeApi(stored = failure(500)), "Asaph") }
        assertEquals(PublicMusicException.Kind.METADATA_READ_FAILED, failure.kind)
        assertEquals(500, failure.httpCode)
    }

    @Test
    fun writeArtistsIgnoreBlankStoredValues() {
        assertEquals(listOf("Local"), publicMusicWriteArtists(listOf(" "), "Local", null, setOf(placeholder)))
        assertNull(publicMusicWriteArtists(emptyList(), placeholder, listOf(""), setOf(placeholder)))
    }
}
