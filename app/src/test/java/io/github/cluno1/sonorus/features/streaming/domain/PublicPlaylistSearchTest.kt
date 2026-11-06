package io.github.cluno1.sonorus.features.streaming.domain

import io.github.cluno1.sonorus.features.streaming.data.provider.PublicMusicPlaylist
import io.github.cluno1.sonorus.features.streaming.data.provider.PublicMusicPlaylistSearchPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicPlaylistSearchTest {
    private fun playlist(id: String, source: String = "feiniao") = PublicMusicPlaylist(source, id, "歌单$id", null, 10)
    private fun page(number: Int, vararg ids: String, more: Boolean = true) =
        PublicMusicPlaylistSearchPage(ids.map { playlist(it) }, number, more)

    @Test
    fun 新搜索从第一页开始() {
        val state = PublicPlaylistSearch.start(" 赞美 ")
        assertEquals("赞美", state.query)
        assertEquals(1, PublicPlaylistSearch.nextPage(state.copy(loading = false)))
    }

    @Test
    fun 旧关键词的结果不会混进新搜索() {
        val state = PublicPlaylistSearch.start("新")
        assertSame(state, PublicPlaylistSearch.applyPage(state, "旧", page(1, "1")))
    }

    @Test
    fun 同一页重复到达只追加一次() {
        val first = PublicPlaylistSearch.applyPage(PublicPlaylistSearch.start("a"), "a", page(1, "1", "2"))
        val again = PublicPlaylistSearch.applyPage(first, "a", page(1, "1", "2"))
        assertEquals(listOf("1", "2"), again.items.map { it.externalId })
        assertEquals(1, again.page)
    }

    @Test
    fun 跨页同一歌单只列一次_不同来源同ID分开() {
        val first = PublicPlaylistSearch.applyPage(PublicPlaylistSearch.start("a"), "a", page(1, "1"))
        val second = PublicPlaylistSearch.applyPage(
            PublicPlaylistSearch.loading(first), "a",
            PublicMusicPlaylistSearchPage(listOf(playlist("1"), playlist("1", source = "other")), 2, false),
        )
        assertEquals(listOf("feiniao" to "1", "other" to "1"), second.items.map { it.sourceId to it.externalId })
        assertFalse(second.hasMore)
        assertNull(PublicPlaylistSearch.nextPage(second))
    }

    @Test
    fun 加载中不会再请求下一页() {
        val loaded = PublicPlaylistSearch.applyPage(PublicPlaylistSearch.start("a"), "a", page(1, "1"))
        assertEquals(2, PublicPlaylistSearch.nextPage(loaded))
        assertNull(PublicPlaylistSearch.nextPage(PublicPlaylistSearch.loading(loaded)))
    }

    @Test
    fun 失败保留已列出的结果并可重试() {
        val loaded = PublicPlaylistSearch.applyPage(PublicPlaylistSearch.start("a"), "a", page(1, "1"))
        val failed = PublicPlaylistSearch.fail(PublicPlaylistSearch.loading(loaded), "a")
        assertTrue(failed.failed)
        assertEquals(listOf("1"), failed.items.map { it.externalId })
        assertEquals(2, PublicPlaylistSearch.nextPage(failed))
    }
}
