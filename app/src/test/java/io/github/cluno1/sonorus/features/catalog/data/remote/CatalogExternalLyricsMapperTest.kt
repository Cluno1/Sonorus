package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.Gson
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource
import io.github.cluno1.sonorus.features.catalog.domain.formatExternalLyricsDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** issue 127: `/v2/library/external-lyrics/…` responses as produced by the backend. */
class CatalogExternalLyricsMapperTest {
    private val gson = Gson()

    private fun search(json: String) =
        CatalogExternalLyricsMapper.search(gson.fromJson(json, ExternalLyricsSearchDto::class.java))

    @Test
    fun mapsSearchResponseFromContract() {
        val result = search(
            """
            {"query": "旅人", "type": "external_lyrics", "fallback": null, "unavailable_sources": ["kugou"],
             "items": [
               {"source": "netease", "external_id": "2067068109", "title": "旅人", "artists": ["罗云熙"],
                "album": "旅人", "duration_ms": 253783, "match_field": "title", "snippet": null},
               {"source": "netease", "external_id": "3339809937", "title": "不得不爱", "artists": ["Lambert凌杰"],
                "album": null, "duration_ms": null, "match_field": "lyrics", "snippet": "你掌心的温度"}
             ]}
            """.trimIndent(),
        )
        assertEquals("旅人", result.query)
        assertEquals(listOf("kugou"), result.unavailableSources)
        val (title, lyric) = result.items
        assertEquals(CatalogExternalLyricsSource.NETEASE, title.source)
        assertEquals(listOf("罗云熙"), title.artists)
        assertEquals(253783L, title.durationMs)
        assertEquals("title", title.matchField)
        assertEquals("lyrics", lyric.matchField)
        assertEquals("你掌心的温度", lyric.snippet)
        assertNull(lyric.album)
    }

    @Test
    fun dropsMalformedRowsAndKeepsTheRest() {
        val result = search(
            """
            {"query": "q", "items": [
              {"source": "kuwo", "external_id": "1", "title": "未知来源", "artists": []},
              {"source": "tencent", "external_id": "../x", "title": "非法ID", "artists": []},
              {"source": "tencent", "external_id": "004BzmAS3gqK1T", "title": " ", "artists": []},
              {"source": "tencent", "external_id": "004BzmAS3gqK1T", "title": "旅人", "artists": ["王铮亮", " "]}
            ]}
            """.trimIndent(),
        )
        val item = result.items.single()
        assertEquals("004BzmAS3gqK1T", item.externalId)
        assertEquals(listOf("王铮亮"), item.artists)
    }

    @Test
    fun sameIdOnTheSamePlatformAppearsOnce() {
        val row = """{"source": "kugou", "external_id": "8ae73bc9", "title": "旅人", "artists": ["罗云熙"]}"""
        val result = search("""{"query": "q", "items": [$row, $row,
            {"source": "netease", "external_id": "8ae73bc9", "title": "旅人", "artists": ["罗云熙"]}]}""")
        assertEquals(listOf("kugou:8ae73bc9", "netease:8ae73bc9"), result.items.map { it.key })
    }

    @Test
    fun missingItemsIsAnError() {
        assertThrows(IllegalArgumentException::class.java) { search("""{"query": "q"}""") }
    }

    @Test
    fun mapsLyricsAndRejectsEmptyOnes() {
        val lyrics = CatalogExternalLyricsMapper.lyrics(
            gson.fromJson(
                """{"source": "tencent", "external_id": "004BzmAS3gqK1T", "lyrics": "你掌心的温度\n催开花万丛",
                   "synced_lrc": "[00:16.76]你掌心的温度", "translation": "  "}""",
                ExternalLyricsDto::class.java,
            ),
        )
        assertEquals("你掌心的温度\n催开花万丛", lyrics.lyrics)
        assertNull(lyrics.translation)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogExternalLyricsMapper.lyrics(
                ExternalLyricsDto(source = "netease", externalId = "1", lyrics = " "),
            )
        }
    }

    @Test
    fun mapsMatchCandidatesWithLyricsAndDropsEmptyOnes() {
        val matches = CatalogExternalLyricsMapper.match(
            gson.fromJson(
                """
                {"unavailable_sources": [], "items": [
                  {"source": "kugou", "external_id": "8ae73bc9", "title": "旅人", "artists": ["罗云熙"],
                   "album": "旅人", "duration_ms": 253000, "lyrics": "In your eyes",
                   "synced_lrc": "[00:13.73]In your eyes", "translation": null},
                  {"source": "netease", "external_id": "1", "title": "空歌词", "artists": [], "lyrics": " "},
                  {"source": "kuwo", "external_id": "2", "title": "未知来源", "artists": [], "lyrics": "x"}
                ]}
                """.trimIndent(),
                ExternalLyricsMatchDto::class.java,
            ),
        )
        val match = matches.single()
        assertEquals(CatalogExternalLyricsSource.KUGOU, match.source)
        assertEquals("[00:13.73]In your eyes", match.syncedLrc)
        assertEquals(253000L, match.durationMs)
        assertThrows(IllegalArgumentException::class.java) {
            CatalogExternalLyricsMapper.match(ExternalLyricsMatchDto(items = null))
        }
    }

    @Test
    fun formatsDurationAsMinutesAndSeconds() {
        assertEquals("4:13", formatExternalLyricsDuration(253783L))
        assertEquals("0:05", formatExternalLyricsDuration(5000L))
        assertNull(formatExternalLyricsDuration(null))
        assertNull(formatExternalLyricsDuration(0L))
    }
}
