package io.github.cluno1.sonorus.features.catalog.domain

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogAnnouncementMapper
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAnnouncementsTest {
    private fun body(content: String) =
        Gson().fromJson("""{"type":"doc","content":$content}""", JsonObject::class.java)

    private fun item(id: String, at: Long = 5_000L, important: Boolean = false) =
        CatalogAnnouncementSummary(id, id, "", important, Instant.ofEpochMilli(at), null)

    private class FakeRepository : CatalogAnnouncementsRepository {
        var calls = 0
        val results = ArrayDeque<Result<CatalogAnnouncementFetch>>()
        override suspend fun listAnnouncements(cursor: String?, etag: String?): Result<CatalogAnnouncementFetch> {
            calls++
            return results.removeFirst()
        }
        override suspend fun announcement(id: String): Result<CatalogAnnouncementDetail> = error("unused")
    }

    private class FakePersistence : AnnouncementReadPersistence {
        var saves = 0
        var stored: AnnouncementReadState? = null
        override fun load() = stored
        override fun save(state: AnnouncementReadState) {
            saves++
            stored = state
        }
    }

    private fun fetched(vararg ids: String) = Result.success<CatalogAnnouncementFetch>(
        CatalogAnnouncementFetch.Fetched(CatalogAnnouncementPage(ids.map { item(it) }, null), "e1"),
    )

    @Test
    fun `未知块被跳过并提示需要更新，其余内容保留`() {
        val doc = CatalogAnnouncementMapper.document(
            body("""[{"type":"video"},{"type":"paragraph","content":[{"type":"text","text":"留下"}]}]"""),
        )
        assertTrue(doc.hasUnsupportedContent)
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun `文末空段落被去掉`() {
        val doc = CatalogAnnouncementMapper.document(
            body("""[{"type":"paragraph","content":[{"type":"text","text":"甲"}]},{"type":"paragraph"}]"""),
        )
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun `外部图片不加载`() {
        val doc = CatalogAnnouncementMapper.document(
            body("""[{"type":"image","attrs":{"src":"https://evil.example/a.png"}}]"""),
        )
        assertTrue(doc.blocks.isEmpty())
    }

    @Test
    fun `非 https 链接不可点击`() {
        val doc = CatalogAnnouncementMapper.document(
            body("""[{"type":"paragraph","content":[{"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}]"""),
        )
        assertNull((doc.blocks.single() as AnnouncementBlock.Paragraph).inlines.single().href)
    }

    @Test
    fun `基线之前发布的公告不算未读`() {
        val state = AnnouncementReadState(baselineEpochMillis = 100)
        assertFalse(state.isUnread(item("a", 100)))
        assertTrue(state.isUnread(item("b", 101)))
    }

    @Test
    fun `关闭后的重要公告不再显示提示条`() {
        val state = AnnouncementReadState(100).withDismissed("a")
        assertNull(state.banner(listOf(item("a", 200, important = true))))
    }

    @Test
    fun `30 分钟内重复刷新只请求一次`() = runBlocking {
        val repository = FakeRepository().apply { results += fetched("a") }
        val controller = CatalogAnnouncementsController(repository, FakePersistence(), { "https://a.example" }, { 1_000L })
        controller.refresh()
        controller.refresh()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `拉取失败不抛异常且保留上次列表`() = runBlocking {
        val repository = FakeRepository().apply {
            results += fetched("a")
            results += Result.failure(CatalogFailure.Unreachable(IOException("down")))
        }
        val controller = CatalogAnnouncementsController(repository, FakePersistence(), { "https://a.example" }, { 1_000L })
        controller.refresh()
        controller.refresh(force = true)
        assertEquals(listOf("a"), controller.state.value.items.map { it.id })
    }

    @Test
    fun `重复标记已读只保存一次`() {
        val persistence = FakePersistence()
        val controller = CatalogAnnouncementsController(FakeRepository(), persistence, { "https://a.example" }, { 1_000L })
        val before = persistence.saves
        controller.markRead("a")
        controller.markRead("a")
        assertEquals(before + 1, persistence.saves)
    }
}
