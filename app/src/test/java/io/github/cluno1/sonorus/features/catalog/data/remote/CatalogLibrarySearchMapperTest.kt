package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchType
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsTranslation
import io.github.cluno1.sonorus.features.catalog.domain.CatalogWorkNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLibrarySearchMapperTest {
    private val gson = Gson()
    private val workId = "11111111-1111-4111-8111-111111111111"
    private val scoreId = "22222222-2222-4222-8222-222222222222"
    private val renditionId = "33333333-3333-4333-8333-333333333333"
    private val albumId = "66666666-6666-4666-8666-666666666666"

    @Test
    fun mapsSearchResponseFromContract() {
        val json = """
            {
              "query": "352", "type": "number", "fallback": null,
              "items": [{
                "work_id": "$workId", "title": "上主坚定的爱", "match_field": "number",
                "match_language": null, "snippet": "上主坚定的爱永不止息",
                "matched_number": {"key": "heavenSong_num", "value": 352},
                "numbers": [{"key": "ihope_num", "value": 140}, {"key": "heavenSong_num", "value": 352}],
                "has_lyrics": true, "default_score_id": "$scoreId",
                "songs": [{"rendition_id": "$renditionId", "album_id": "$albumId", "title": "上主坚定的爱", "track_no": 140}]
              }]
            }
        """.trimIndent()
        val result = CatalogDtoMapper.librarySearch(
            gson.fromJson(json, LibrarySearchDto::class.java),
            CatalogLibrarySearchType.LYRICS,
        )
        assertEquals(CatalogLibrarySearchType.NUMBER, result.type)
        assertNull(result.fallback)
        val item = result.items.single()
        assertEquals(workId, item.workId)
        assertEquals(CatalogWorkNumber("heavenSong_num", 352), item.matchedNumber)
        assertEquals(listOf("ihope_num", "heavenSong_num"), item.numbers.map { it.key })
        assertTrue(item.hasLyrics)
        assertEquals(scoreId, item.defaultScoreId)
        assertEquals(140, item.songs.single().trackNo)
    }

    @Test
    fun mapsTagDiscoveryAndTaggedWorkPageWithoutChangingNumberResults() {
        val discovery = """
            {"query":"声部","type":"tags","fallback":null,"items":[],
             "tag_items":[{"tag_key":"多声部","label":"多声部","work_count":2}]}
        """.trimIndent()
        val tags = CatalogDtoMapper.librarySearch(
            gson.fromJson(discovery, LibrarySearchDto::class.java),
            CatalogLibrarySearchType.TAGS,
        )
        assertEquals(CatalogLibrarySearchType.TAGS, tags.type)
        assertTrue(tags.items.isEmpty())
        assertEquals("多声部", tags.tagItems.single().key)
        assertEquals(2, tags.tagItems.single().workCount)

        val workPage = """
            {"tag":{"tag_key":"多声部","label":"多声部","work_count":2},
             "items":[{"work_id":"$workId","title":"示例作品","match_field":"tag",
                       "numbers":[{"key":"ihope_num","value":256}],"has_lyrics":true,
                       "default_score_id":"$scoreId",
                       "songs":[{"rendition_id":"$renditionId","album_id":"$albumId","title":"录音名"}]}],
             "next_cursor":"opaque-next"}
        """.trimIndent()
        val page = CatalogDtoMapper.tagWorksPage(gson.fromJson(workPage, LibraryTagWorksPageDto::class.java))
        assertEquals("opaque-next", page.nextCursor)
        assertEquals(workId, page.items.single().workId)
        assertEquals(scoreId, page.items.single().defaultScoreId)
        assertEquals(1, page.items.single().songs.size)
        assertEquals(CatalogWorkNumber("ihope_num", 256), page.items.single().numbers.single())
    }

    @Test
    fun toleratesMissingOptionalFieldsAndDropsInvalidItems() {
        val json = """
            {
              "query": "上主", "type": "lyrics", "fallback": "opencc_t2s",
              "items": [
                {"work_id": "not-a-uuid", "title": "Broken"},
                {"work_id": "$workId", "title": "Title", "default_score_id": "bad",
                 "songs": [{"rendition_id": "bad", "album_id": "$albumId", "title": "x"}]}
              ]
            }
        """.trimIndent()
        val result = CatalogDtoMapper.librarySearch(
            gson.fromJson(json, LibrarySearchDto::class.java),
            CatalogLibrarySearchType.LYRICS,
        )
        assertEquals("opencc_t2s", result.fallback)
        val item = result.items.single()
        assertNull(item.defaultScoreId)
        assertNull(item.matchedNumber)
        assertTrue(item.numbers.isEmpty())
        assertTrue(item.songs.isEmpty())
        assertFalse(item.hasLyrics)
    }

    @Test
    fun parsesNumberMetaInContractAndStoredForms() {
        val numbers = CatalogDtoMapper.workNumbers(
            JsonParser.parseString(
                """[{"key": "ihope_num", "value": 256}, {"heavenSong_num": "352"}, {"key": "x"}, 5, {"a": 1, "b": 2}]""",
            ),
        )
        assertEquals(
            listOf(CatalogWorkNumber("ihope_num", 256), CatalogWorkNumber("heavenSong_num", 352)),
            numbers,
        )
        assertTrue(CatalogDtoMapper.workNumbers(null).isEmpty())
        assertTrue(CatalogDtoMapper.workNumbers(JsonParser.parseString("{}")).isEmpty())
    }

    @Test
    fun parsesWorkTranslationsAsListOrObject() {
        val fromList = CatalogDtoMapper.workLyricsTranslations(
            JsonParser.parseString("""[{"language": "zh_Hant", "lyrics": "上主"}, {"language": "en", "lyrics": " "}]"""),
        )
        assertEquals(listOf(CatalogLyricsTranslation("zh-Hant", "上主")), fromList)
        val fromObject = CatalogDtoMapper.workLyricsTranslations(
            JsonParser.parseString("""{"en": "Lord", "pinyin": null}"""),
        )
        assertEquals(listOf(CatalogLyricsTranslation("en", "Lord")), fromObject)
    }

    @Test
    fun bundleWithoutIssue107FieldsStillMaps() {
        val json = """
            {"work": {"id": "$workId", "canonical_title": "Title", "language": "zh-Hans", "status": "active",
                      "revision": 1, "aliases": [], "credits": [], "created_at": "now", "updated_at": "now"},
             "arrangements": [], "bundle_version": 1}
        """.trimIndent()
        val work = CatalogDtoMapper.bundle(gson.fromJson(json, WorkBundleDto::class.java)).work
        assertNull(work.lyrics)
        assertEquals(emptyList<CatalogWorkNumber>(), work.numberMeta)
        assertEquals(emptyList<CatalogLyricsTranslation>(), work.lyricsTranslations)
    }

    @Test
    fun bundleMapsWorkLyricsAndNumbers() {
        val json = """
            {"work": {"id": "$workId", "canonical_title": "Title", "language": "zh-Hans", "status": "active",
                      "revision": 1, "aliases": [], "credits": [], "created_at": "now", "updated_at": "now",
                      "lyrics": "上主坚定的爱", "lyrics_translations": [{"language": "en", "lyrics": "Steadfast love"}],
                      "number_meta": [{"key": "ihope_num", "value": 140}]},
             "arrangements": [], "bundle_version": 1}
        """.trimIndent()
        val work = CatalogDtoMapper.bundle(gson.fromJson(json, WorkBundleDto::class.java)).work
        assertEquals("上主坚定的爱", work.lyrics)
        assertEquals(listOf(CatalogWorkNumber("ihope_num", 140)), work.numberMeta)
        assertEquals("en", work.lyricsTranslations.orEmpty().single().language)
    }
}
