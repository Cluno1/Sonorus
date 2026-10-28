package io.github.cluno1.sonorus.features.local.data.device

import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsMatch
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** issue 127: the Chinese lyrics source follows the LRCLIB matching rules. */
class DeviceChineseLyricsTest {
    private val lrclib = DevicePublicMetadataProvider.LRCLIB
    private val chinese = DevicePublicMetadataProvider.CHINESE_LYRICS

    private fun match(
        source: CatalogExternalLyricsSource,
        id: String,
        title: String = "旅人",
        artist: String = "罗云熙",
        durationMs: Long? = 253_783,
        synced: String? = "[00:13.73]In your eyes",
    ) = CatalogExternalLyricsMatch(source, id, title, listOf(artist), "旅人", durationMs, "In your eyes", synced, null)

    private fun candidate(
        title: String,
        artist: String,
        confidence: Double,
        seconds: Double? = 253.8,
        platform: String = "netease",
        synced: String? = null,
    ) = DeviceLyricsCandidate(
        externalId = "$platform:$title:$artist:$confidence",
        title = title,
        artist = artist,
        album = "",
        durationSeconds = seconds,
        confidence = confidence,
        lyrics = LyricsData("plain", synced, source = DeviceChineseLyrics.SOURCE),
        provider = chinese,
        platform = platform,
    )

    @Test
    fun 中文歌名或歌手先试中文歌词其他歌先试LRCLIB() {
        assertEquals(listOf(chinese, lrclib), DeviceChineseLyrics.automaticOrder(true, "旅人", "Someone"))
        assertEquals(listOf(chinese, lrclib), DeviceChineseLyrics.automaticOrder(true, "Hello", "周杰伦"))
        assertEquals(listOf(lrclib, chinese), DeviceChineseLyrics.automaticOrder(true, "Hello", "Adele"))
    }

    @Test
    fun 关闭中文歌词时只剩LRCLIB() {
        assertEquals(listOf(lrclib), DeviceChineseLyrics.automaticOrder(false, "旅人", "罗云熙"))
    }

    @Test
    fun 假名不算中文但日文汉字算() {
        assertFalse(DeviceChineseLyrics.prefersChinese("もう", "YOASOBI"))
        assertTrue(DeviceChineseLyrics.prefersChinese("夜に駆ける", null))
    }

    @Test
    fun 候选用LRCLIB同一个打分器并标明来源与平台() {
        val input = DeviceMatchInput("旅人", "罗云熙", "旅人", 253_783)
        val (best) = DeviceChineseLyrics.candidates(
            input,
            listOf(match(CatalogExternalLyricsSource.KUGOU, "8ae73bc9")),
        )
        assertEquals(DeviceMetadataMatcher.score(input, "旅人", "罗云熙", "旅人", 253.783), best.confidence, 1e-9)
        assertEquals(chinese, best.provider)
        assertEquals("kugou", best.platform)
        assertEquals("kugou:8ae73bc9", best.externalId)
        assertEquals("[00:13.73]In your eyes", best.lyrics.syncedLyrics)
    }

    @Test
    fun 三个平台的同一首歌合并后能自动采用() {
        val sameSong = listOf(
            candidate("旅人", "罗云熙", 0.86, platform = "netease"),
            candidate("旅人", "罗云熙", 0.86, seconds = 253.0, platform = "tencent"),
            candidate("旅人", "罗云熙", 0.85, seconds = 254.0, platform = "kugou", synced = "[00:01.00]x"),
        )
        // Without collapsing, the 0.03 margin would reject this as ambiguous.
        assertFalse(DeviceMetadataMatcher.isAutomaticMatch(0.86, 0.86, DeviceMetadataRepository.MIN_AUTO_CONFIDENCE))
        assertEquals(1, DeviceChineseLyrics.collapseSameSong(sameSong).size)
        assertEquals("旅人", DeviceChineseLyrics.automaticPick(sameSong, "旅人")?.title)
    }

    @Test
    fun 合并同分时优先带时间轴的版本() {
        val kept = DeviceChineseLyrics.collapseSameSong(
            listOf(
                candidate("旅人", "罗云熙", 0.86, platform = "netease"),
                candidate("旅人", "罗云熙", 0.86, platform = "kugou", synced = "[00:01.00]x"),
            ),
        ).single()
        assertEquals("kugou", kept.platform)
    }

    @Test
    fun 歌手不同或版本后缀不同不算同一首歌() {
        val kept = DeviceChineseLyrics.collapseSameSong(
            listOf(
                candidate("旅人", "罗云熙", 0.86),
                candidate("旅人", "王铮亮", 0.80),
                candidate("旅人 (Live)", "罗云熙", 0.70),
            ),
        )
        assertEquals(3, kept.size)
    }

    @Test
    fun 同一首歌各平台时长差几秒仍合并为一首() {
        val kept = DeviceChineseLyrics.collapseSameSong(
            listOf(
                candidate("旅人", "罗云熙", 0.86, seconds = 253.8, platform = "netease"),
                candidate("旅人", "罗云熙", 0.84, seconds = 249.0, platform = "tencent"),
            ),
        )
        assertEquals("netease", kept.single().platform)
    }

    @Test
    fun 王铮亮不凡的伴奏版不挡住原版自动采用() {
        // Live /match result for 王铮亮《不凡》; the local file is 207 s with no album.
        val input = DeviceMatchInput("不凡", "王铮亮", "", 207_000)
        val candidates = DeviceChineseLyrics.candidates(
            input,
            listOf(
                match(CatalogExternalLyricsSource.NETEASE, "1", "不凡", "王铮亮", 211_802),
                match(CatalogExternalLyricsSource.TENCENT, "2", "不凡", "王铮亮", 88_000),
                match(CatalogExternalLyricsSource.KUGOU, "3", "不凡", "王铮亮", 211_000),
                match(CatalogExternalLyricsSource.NETEASE, "4", "不凡 (伴奏)", "王铮亮", 211_802),
                match(CatalogExternalLyricsSource.TENCENT, "5", "不凡 (三亚农场演唱会版)", "王铮亮", 208_000),
                match(CatalogExternalLyricsSource.TENCENT, "6", "不凡2024", "王铮亮", 252_000),
            ),
        )
        val pick = DeviceChineseLyrics.automaticPick(candidates, "不凡")
        assertEquals("不凡", pick?.title)
        assertTrue(pick!!.confidence >= DeviceMetadataRepository.MIN_AUTO_CONFIDENCE)
    }

    @Test
    fun 本地本身是伴奏时伴奏版照常参与自动采用() {
        val only = listOf(candidate("不凡 (伴奏)", "王铮亮", 0.86))
        assertNull(DeviceChineseLyrics.automaticPick(only, "不凡"))
        assertEquals("不凡 (伴奏)", DeviceChineseLyrics.automaticPick(only, "不凡 (伴奏)")?.title)
    }

    @Test
    fun 不同的歌分数接近时不自动采用() {
        val ambiguous = listOf(candidate("旅人", "罗云熙", 0.80), candidate("旅人", "王铮亮", 0.79))
        assertNull(DeviceChineseLyrics.automaticPick(ambiguous, "旅人"))
    }

    @Test
    fun 低于LRCLIB自动门槛时不自动采用() {
        assertNull(DeviceChineseLyrics.automaticPick(listOf(candidate("旅途", "别人", 0.60)), "旅人"))
    }

    @Test
    fun 来源标签记录歌名歌手匹配度与平台() {
        val pick = candidate("旅人", "罗云熙", 0.867, platform = "tencent")
        assertEquals("DEVICE_CNLYRICS|旅人|罗云熙|86%|tencent", DeviceChineseLyrics.deviceLabel(pick, userSelected = false))
        assertEquals("DEVICE_CNLYRICS_SELECTED|旅人|罗云熙|86%|tencent", DeviceChineseLyrics.deviceLabel(pick, userSelected = true))
    }
}
