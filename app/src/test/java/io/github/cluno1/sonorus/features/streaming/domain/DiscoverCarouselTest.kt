package io.github.cluno1.sonorus.features.streaming.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiscoverCarouselTest {
    @Test
    fun `三组内容轮流穿插，短的一组用完后继续排其他组`() {
        assertEquals(
            listOf("本地1", "发现1", "公共1", "本地2", "公共2", "公共3"),
            DiscoverCarousel.interleave(listOf(listOf("本地1", "本地2"), listOf("发现1"), listOf("公共1", "公共2", "公共3"))),
        )
    }

    @Test
    fun `轮播数量不足时每组都能露出，不被第一组占满`() {
        val picked = DiscoverCarousel.pick(listOf(List(9) { "本地$it" }, List(9) { "发现$it" }, List(9) { "公共$it" }), 3)
        assertEquals(listOf("本地", "发现", "公共"), picked.map { it.take(2) })
    }

    @Test
    fun `发现歌单 ID 能还原来源和歌单`() {
        assertEquals("feiniao" to "77", DiscoverPlaylistIds.decode(DiscoverPlaylistIds.encode("feiniao", "77")))
    }

    @Test
    fun `公共专辑 ID 不会被当成发现歌单`() {
        assertFalse(DiscoverPlaylistIds.isDiscover("SUBSONIC::album::歌手::专辑"))
    }
}
