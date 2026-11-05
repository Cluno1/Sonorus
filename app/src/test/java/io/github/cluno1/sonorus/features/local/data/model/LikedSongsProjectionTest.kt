package io.github.cluno1.sonorus.features.local.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LikedSongsProjectionTest {
    private fun merge(library: List<String>, likedPlaylist: List<String>, favorites: Set<String>) =
        LikedSongsProjection.merge(library, likedPlaylist, favorites) { it }

    @Test
    fun `曲库里没有的公共歌曲也显示在 Liked`() {
        assertEquals(
            listOf("local", "SUBSONIC::feiniao:1"),
            merge(listOf("local"), listOf("SUBSONIC::feiniao:1"), setOf("local", "SUBSONIC::feiniao:1")),
        )
    }

    @Test
    fun `同时在曲库和 Liked 歌单的歌只显示一次`() {
        assertEquals(listOf("local"), merge(listOf("local"), listOf("local"), setOf("local")))
    }

    @Test
    fun `取消收藏后歌单快照不再显示`() {
        assertEquals(emptyList<String>(), merge(emptyList(), listOf("SUBSONIC::feiniao:1"), emptySet()))
    }
}
