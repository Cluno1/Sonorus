package io.github.cluno1.sonorus.features.catalog.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicMusicSigningTest {
    @Test
    fun `设备为公共歌曲的歌词写入签名`() {
        assertTrue(isPublicMusicWrite("PUT", "/v2/music/sources/kuwo/songs/3274963/lyrics"))
    }

    @Test
    fun `设备为公共歌曲的元数据写入签名`() {
        assertTrue(isPublicMusicWrite("PUT", "/v2/music/sources/feiniao/songs/228908/metadata"))
    }

    @Test
    fun `设备为本地歌曲的共享歌词写入签名`() {
        assertTrue(isPublicMusicWrite("PUT", "/v2/music/device-lyrics"))
    }

    @Test
    fun `其他方法或路径不签名`() {
        assertFalse(isPublicMusicWrite("POST", "/v2/music/sources/kuwo/songs/1/lyrics"))
        assertFalse(isPublicMusicWrite("DELETE", "/v2/music/device-lyrics"))
        assertFalse(isPublicMusicWrite("PUT", "/v2/music/sources/kuwo/songs/1"))
        assertFalse(isPublicMusicWrite("PUT", "/v2/music/sources/Kuwo/songs/1/lyrics"))
        assertFalse(isPublicMusicWrite("PUT", "/v2/music/sources/kuwo/songs/a.b/lyrics"))
        assertFalse(isPublicMusicWrite("PUT", "/v2/admin/music-sources"))
    }
}
