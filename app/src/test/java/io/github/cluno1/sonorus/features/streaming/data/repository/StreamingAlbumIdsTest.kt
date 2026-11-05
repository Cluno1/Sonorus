package io.github.cluno1.sonorus.features.streaming.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAlbumIdsTest {
    @Test
    fun `没有专辑 ID 的歌曲和按名称生成的专辑 ID 相同`() {
        assertEquals(
            StreamingAlbumIds.fromNames("SUBSONIC", "DJ阿赫", "无奈的思绪"),
            StreamingAlbumIds.forSong("SUBSONIC", null, "DJ阿赫", "无奈的思绪"),
        )
    }

    @Test
    fun `有专辑 ID 的歌曲继续使用上游 ID`() {
        assertEquals("SUBSONIC::album::a_slash_b", StreamingAlbumIds.forSong("SUBSONIC", "a/b", "歌手", "专辑"))
    }

    @Test
    fun `没有专辑名的歌曲不属于任何专辑`() {
        assertNull(StreamingAlbumIds.forSong("SUBSONIC", null, "歌手", " "))
    }
}
