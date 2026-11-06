package io.github.cluno1.sonorus.features.local.data.device

import io.github.cluno1.sonorus.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLyricsPolicyTest {
    private fun candidate(provider: DevicePublicMetadataProvider, platform: String? = null) = DeviceLyricsCandidate(
        externalId = "1", title = "t", artist = "a", album = "", durationSeconds = null, confidence = 0.9,
        lyrics = LyricsData("plain", "[00:01.00]synced"), provider = provider, platform = platform,
    )

    @Test
    fun 回存优先使用带时间轴的歌词() {
        assertEquals("[00:01.00]synced", SharedLyricsPolicy.lrc(LyricsData("plain", "[00:01.00]synced")))
        assertEquals("plain", SharedLyricsPolicy.lrc(LyricsData("plain", " ")))
        assertNull(SharedLyricsPolicy.lrc(LyricsData(" ", null)))
    }

    @Test
    fun 后端歌词按是否带时间轴还原并标记为共享来源() {
        val synced = SharedLyricsPolicy.lyricsData("[00:01.00]x")
        assertEquals("[00:01.00]x", synced.syncedLyrics)
        assertNull(synced.plainLyrics)
        assertEquals("x", SharedLyricsPolicy.lyricsData("x").plainLyrics)
        assertEquals(SharedLyricsPolicy.SHARED_SOURCE, synced.source)
    }

    @Test
    fun 没有真实歌手的歌曲不共享() {
        val placeholders = setOf("未知艺术家")
        listOf("", "  ", "<unknown>", "Unknown", "unknown artist", "Unknown Artist 2", "未知艺术家").forEach {
            assertNull(it, SharedLyricsPolicy.shareableArtist(it, placeholders))
        }
        assertEquals("Taylor Swift", SharedLyricsPolicy.shareableArtist(" Taylor Swift ", placeholders))
    }

    @Test
    fun 来源标记包含平台且不超过后端长度() {
        assertEquals("LRCLIB", SharedLyricsPolicy.provider(candidate(DevicePublicMetadataProvider.LRCLIB)))
        assertEquals(
            "CHINESE_LYRICS:netease",
            SharedLyricsPolicy.provider(candidate(DevicePublicMetadataProvider.CHINESE_LYRICS, "netease")),
        )
        assertEquals(40, SharedLyricsPolicy.provider(candidate(DevicePublicMetadataProvider.CHINESE_LYRICS, "x".repeat(60))).length)
    }
}
