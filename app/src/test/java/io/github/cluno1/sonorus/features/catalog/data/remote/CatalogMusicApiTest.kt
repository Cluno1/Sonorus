package io.github.cluno1.sonorus.features.catalog.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMusicApiTest {
    @Test
    fun `reads unified search and writes metadata to exact source path`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"sources":[{"source_id":"feiniao","items":[{"source_id":"feiniao","external_id":"42","title":"旅人","artists":["王铮亮"],"album":"旅人","duration_ms":240000,"artwork_url":"/v2/music/artwork?url=x"}]}]}"""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"source_id":"feiniao","external_id":"42","title":"旅人","artists":["王铮亮"],"mode":"auto"}"""))
            val api = CatalogApiClient(server.url("/").toString(), "test-token").musicApi
            val songs = api.search("旅人").body()!!.sources.single().items
            assertEquals("42", songs.single().externalId)
            assertEquals(240000L, songs.single().durationMs)
            val search = server.takeRequest()
            assertEquals("/v2/music/search?q=%E6%97%85%E4%BA%BA&page=1", search.path)
            assertEquals("Bearer test-token", search.getHeader("Authorization"))

            val saved = api.saveMetadata("feiniao", "42", PublicMusicMetadataWriteDto(
                title = "旅人", artists = listOf("王铮亮"), album = "旅人", durationMs = 240000,
                artworkUrl = null, artistAvatarUrl = null, lyricsLrc = "[00:01]歌词",
                qualities = listOf("mp3"), mode = "auto",
            ))
            assertEquals("auto", saved.body()?.mode)
            val write = server.takeRequest()
            assertEquals("PUT", write.method)
            assertEquals("/v2/music/sources/feiniao/songs/42/metadata", write.path)
            assertTrue(write.body.readUtf8().contains("\"lyrics_lrc\""))
        }
    }
}
