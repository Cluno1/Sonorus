package io.github.cluno1.sonorus.shared.presentation.components.common

import io.github.cluno1.sonorus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDisplayNameTest {
    @Test
    fun `三个内置歌单按 id 加英文原名换成多语言文案`() {
        assertEquals(R.string.default_playlist_liked, defaultPlaylistNameRes("1", "Liked"))
        assertEquals(R.string.default_playlist_recently_added, defaultPlaylistNameRes("2", "Recently Added"))
        assertEquals(R.string.default_playlist_most_played, defaultPlaylistNameRes("3", "Most Played"))
    }

    @Test
    fun `只是 id 相同而名字不同的歌单保留自己的名字`() {
        assertNull(defaultPlaylistNameRes("1", "我的公共歌单"))
        assertNull(defaultPlaylistNameRes("2", "Liked"))
    }

    @Test
    fun `用户自建歌单不受影响`() {
        assertNull(defaultPlaylistNameRes("1730000000000", "Liked"))
    }
}
