package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.data.remote.isCatalogWorkLyricWrite
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsVariant
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogWorkLyricsEditTest {
    private fun work(lyricsLanguage: String?, language: String?) = WorkSummary(
        id = "8d0c7c71-1111-4a0e-9c55-000000000001",
        canonicalTitle = "神的国度",
        language = language,
        status = "active",
        revision = 3,
        aliases = emptyList(),
        credits = emptyList(),
        createdAt = "2026-10-25T00:00:00Z",
        updatedAt = "2026-10-25T00:00:00Z",
        lyricsLanguage = lyricsLanguage,
    )

    @Test
    fun `编辑的是页面上正在显示的那个语言版本`() {
        val target = workLyricsEditTarget(
            work("zh-Hans", "zh-Hans"),
            CatalogLyricsVariant(language = "en", lyrics = "  In the Kingdom\nhand in hand  "),
            deviceLanguage = "zh-CN",
        )
        assertEquals(WorkLyricsEditTarget("en", "In the Kingdom\nhand in hand"), target)
    }

    @Test
    fun `带时间戳的版本以纯文本打开`() {
        val target = workLyricsEditTarget(
            work("zh-Hans", null),
            CatalogLyricsVariant(language = "zh-Hans", lyrics = "[00:01.00]第一句\n[00:05.00]第二句", format = "lrc"),
            deviceLanguage = "en",
        )
        assertEquals("第一句\n第二句", target.text)
    }

    @Test
    fun `没有歌词时用作品语言新建，und 不作为可写语言`() {
        assertEquals(WorkLyricsEditTarget("zh-Hans", ""), workLyricsEditTarget(work(null, "zh-Hans"), null, "en"))
        assertEquals("en", workLyricsEditTarget(work("und", "und"), null, "en").language)
        assertEquals("zh-Hant", workLyricsEditTarget(work("und", null), null, "zh-Hant").language)
    }

    @Test
    fun `设备只为作品歌词的窄接口签名`() {
        assertTrue(isCatalogWorkLyricWrite("PUT", "/v2/works/w1/lyrics/zh-Hans"))
        assertFalse(isCatalogWorkLyricWrite("PATCH", "/v2/works/w1"))
        assertFalse(isCatalogWorkLyricWrite("PUT", "/v2/works/w1"))
        assertFalse(isCatalogWorkLyricWrite("PUT", "/v2/works/w1/lyrics"))
        assertFalse(isCatalogWorkLyricWrite("DELETE", "/v2/works/w1/lyrics/en"))
    }
}
