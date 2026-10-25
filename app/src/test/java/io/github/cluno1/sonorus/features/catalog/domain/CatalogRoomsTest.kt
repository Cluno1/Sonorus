package io.github.cluno1.sonorus.features.catalog.domain

import io.github.cluno1.sonorus.features.catalog.presentation.nextSunday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogRoomsTest {
    private val alice = CatalogRoomUser("alice", "小明")

    private fun work(title: String) = CatalogLibrarySearchItem(
        workId = "w-$title", title = title, matchField = "room", matchLanguage = null, snippet = null,
        matchedNumber = null, numbers = emptyList(), hasLyrics = true, defaultScoreId = null, songs = emptyList(),
    )

    private fun item(title: String, position: Int) =
        CatalogRoomItem(id = "i-$title", position = position, work = work(title), addedBy = alice, canRemove = true)

    private fun room(vararg sections: CatalogRoomSection) = CatalogRoom(
        id = "r", name = "周日", serviceDate = LocalDate.of(2026, 11, 1), createdBy = alice,
        itemCount = sections.sumOf { it.items.size }, revision = 3, canDelete = true, sections = sections.toList(),
    )

    private val plain = room(CatalogRoomSection("s1", null, 1, null, listOf(item("A", 1), item("B", 2), item("C", 3))))
    private val twoSections = room(
        CatalogRoomSection("open", "开场", 1, null, listOf(item("A", 1), item("B", 2))),
        CatalogRoomSection("end", "结束", 2, alice, listOf(item("C", 3))),
    )

    private fun titles(rows: List<CatalogRoomRow>) = rows.map {
        when (it) {
            is CatalogRoomRow.Header -> "#${it.section.name}"
            is CatalogRoomRow.Song -> "${it.item.work.title}@${it.sectionId}"
        }
    }

    @Test
    fun `只有默认段时是一张没有段标题的普通列表`() {
        assertTrue(plain.isPlainList)
        assertEquals(listOf("A@s1", "B@s1", "C@s1"), titles(CatalogRoomOrder.rows(plain)))
        assertFalse(twoSections.isPlainList)
        assertEquals(listOf("#开场", "A@open", "B@open", "#结束", "C@end"), titles(CatalogRoomOrder.rows(twoSections)))
    }

    @Test
    fun `拖动后排序号连续且提交的顺序包含全部歌曲`() {
        val moved = CatalogRoomOrder.move(CatalogRoomOrder.rows(plain), from = 2, to = 0)
        assertEquals(listOf("C@s1", "A@s1", "B@s1"), titles(moved))
        assertEquals(mapOf("i-C" to 1, "i-A" to 2, "i-B" to 3), CatalogRoomOrder.ordinals(moved))
        assertEquals(
            listOf(CatalogRoomSectionOrder("s1", listOf("i-C", "i-A", "i-B"))),
            CatalogRoomOrder.toOrder(plain, moved),
        )
    }

    @Test
    fun `上下移动越过段标题时歌曲换到相邻的段`() {
        val rows = CatalogRoomOrder.rows(twoSections)
        val down = CatalogRoomOrder.step(rows, "i-B", up = false)
        assertEquals(listOf("#开场", "A@open", "#结束", "B@end", "C@end"), titles(down))
        assertEquals(
            listOf(CatalogRoomSectionOrder("open", listOf("i-A")), CatalogRoomSectionOrder("end", listOf("i-B", "i-C"))),
            CatalogRoomOrder.toOrder(twoSections, down),
        )
        val up = CatalogRoomOrder.step(rows, "i-C", up = true)
        assertEquals(listOf("#开场", "A@open", "B@open", "C@open", "#结束"), titles(up))
    }

    @Test
    fun `第一首不能再上移，最后一首不能再下移，也不能移到第一个段标题之上`() {
        val rows = CatalogRoomOrder.rows(twoSections)
        assertFalse(CatalogRoomOrder.canStep(rows, "i-A", up = true))
        assertFalse(CatalogRoomOrder.canStep(rows, "i-C", up = false))
        assertTrue(CatalogRoomOrder.canStep(rows, "i-A", up = false))
        assertEquals(rows, CatalogRoomOrder.move(rows, from = 1, to = 0))
        val plainRows = CatalogRoomOrder.rows(plain)
        assertFalse(CatalogRoomOrder.canStep(plainRows, "i-A", up = true))
        assertTrue(CatalogRoomOrder.canStep(plainRows, "i-B", up = true))
    }

    @Test
    fun `添加面板按聚会日期排序，自己负责的段排在最前`() {
        fun summary(id: String, date: LocalDate, sections: List<CatalogRoomSectionSummary> = emptyList()) =
            CatalogRoomSummary(id, id, date, alice, 0, 1, false, sections, null)
        val later = summary("later", LocalDate.of(2026, 11, 8))
        val sooner = summary("sooner", LocalDate.of(2026, 11, 1))
        val mine = CatalogRoomSectionSummary("sec", "圣餐", 2, CatalogRoomUser("bob", null), 0)
        val assigned = summary("assigned", LocalDate.of(2026, 11, 15), listOf(mine))

        // Today nobody is assigned: one target per room, nearest gathering first.
        assertEquals(listOf("sooner", "later"), rankRoomTargets(listOf(later, sooner), "bob").map { it.room.id })
        val ranked = rankRoomTargets(listOf(later, sooner, assigned), "bob")
        assertEquals(listOf("assigned", "sooner", "later", "assigned"), ranked.map { it.room.id })
        assertTrue(ranked.first().assignedToMe)
        assertEquals("sec", ranked.first().section?.id)
        // Recommendation only: every room is still offered.
        assertEquals(setOf("assigned", "sooner", "later"), ranked.drop(1).map { it.room.id }.toSet())
    }

    @Test
    fun `没有昵称时显示用户加编号末四位`() {
        assertEquals("小明", roomUserLabel(alice, "用户"))
        assertEquals("用户 9f3c", roomUserLabel(CatalogRoomUser("device-user-9f3c", "  "), "用户"))
    }

    @Test
    fun `连点只发出一次写入，完成后才能再写`() {
        val gate = CatalogRoomWriteGate()
        assertEquals(1, (1..3).count { gate.tryStart() })
        gate.finish()
        assertTrue(gate.tryStart())
    }

    @Test
    fun `默认聚会日期是即将到来的周日`() {
        assertEquals(LocalDate.of(2026, 11, 1), nextSunday(LocalDate.of(2026, 10, 25).plusDays(1)))
        assertEquals(LocalDate.of(2026, 10, 25), nextSunday(LocalDate.of(2026, 10, 25)))
        assertEquals(LocalDate.of(2026, 11, 1), nextSunday(LocalDate.of(2026, 10, 31)))
    }
}
