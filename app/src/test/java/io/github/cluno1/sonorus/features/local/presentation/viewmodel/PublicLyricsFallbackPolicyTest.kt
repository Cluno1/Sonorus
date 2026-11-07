package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicLyricsFallbackPolicyTest {
    @Test
    fun `本机保存的歌词优先于后端共享歌词`() {
        assertEquals("本机", PublicLyricsFallbackPolicy.lyricsToShow("本机", "共享"))
    }

    @Test
    fun `没有本机歌词时显示后端共享歌词`() {
        assertEquals("共享", PublicLyricsFallbackPolicy.lyricsToShow(null, "共享"))
    }

    @Test
    fun `后端读写失败且本机没有歌词时也尝试自动匹配`() {
        assertTrue(PublicLyricsFallbackPolicy.shouldTryFallback(hasLocal = false, lyricsMissing = false, loadFailed = true))
    }

    @Test
    fun `本机已有歌词时不再自动匹配`() {
        assertFalse(PublicLyricsFallbackPolicy.shouldTryFallback(hasLocal = true, lyricsMissing = true, loadFailed = true))
    }

    @Test
    fun `后端正常且有歌词时不自动匹配`() {
        assertFalse(PublicLyricsFallbackPolicy.shouldTryFallback(hasLocal = false, lyricsMissing = false, loadFailed = false))
    }

    @Test
    fun `公共歌曲保存失败时提供本机保存而本地歌曲不提供`() {
        assertTrue(PublicLyricsFallbackPolicy.offerLocalSave(isPublicSong = true))
        assertFalse(PublicLyricsFallbackPolicy.offerLocalSave(isPublicSong = false))
    }
}
