package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CatalogRoomMapperTest {
    private val roomJson = """
        {"id":"8d0c7c71-1111-4a0e-9c55-000000000001","name":"周日 聚会","service_date":"2026-11-01",
         "expires_at":"2026-11-01T16:00:00Z","created_by":{"user_id":"alice","display_name":"小明"},
         "item_count":2,"revision":3,"can_delete":true,
         "sections":[{"id":"8d0c7c71-1111-4a0e-9c55-0000000000a1","name":null,"position":1,"assignee":null,
           "items":[
            {"id":"8d0c7c71-1111-4a0e-9c55-0000000000b2","position":2,"section_position":2,
             "work":{"work_id":"8d0c7c71-1111-4a0e-9c55-0000000000c2","title":"诗歌 2","match_field":"room",
                     "numbers":[{"key":"ihope_num","value":2}],"has_lyrics":true},
             "added_by":{"user_id":"carol","display_name":null},"added_at":"2026-10-25T04:00:00Z","can_remove":false},
            {"id":"8d0c7c71-1111-4a0e-9c55-0000000000b1","position":1,"section_position":1,
             "work":{"work_id":"8d0c7c71-1111-4a0e-9c55-0000000000c1","title":"诗歌 1","match_field":"room",
                     "numbers":[{"key":"ihope_num","value":1}],"has_lyrics":true},
             "added_by":{"user_id":"alice","display_name":"小明"},"added_at":"2026-10-25T04:00:00Z","can_remove":true}
           ]}]}
    """.trimIndent()

    @Test
    fun `房间详情解析出有序歌曲、添加人和权限`() {
        val room = CatalogRoomMapper.room(Gson().fromJson(roomJson, RoomDto::class.java))
        assertEquals(LocalDate.of(2026, 11, 1), room.serviceDate)
        assertEquals(3, room.revision)
        assertTrue(room.isPlainList)
        val items = room.sections.single().items
        assertEquals(listOf("诗歌 1", "诗歌 2"), items.map { it.work.title })
        assertEquals(listOf(1, 2), items.map { it.position })
        assertEquals("小明", items[0].addedBy.displayName)
        assertNull(items[1].addedBy.displayName)
        assertEquals(listOf(true, false), items.map { it.canRemove })
        assertEquals(1, items[0].work.numbers.size)
    }

    @Test
    fun `列表带作品查询时解析已添加信息`() {
        val json = """
            {"items":[{"id":"r1","name":"周日","service_date":"2026-11-01","expires_at":"2026-11-01T16:00:00Z",
              "created_by":{"user_id":"alice","display_name":"小明"},"item_count":6,"revision":7,"can_delete":false,
              "sections":[{"id":"s1","name":null,"position":1,"assignee":null,"item_count":6}],
              "contains_work":{"item_id":"i5","section_id":"s1","position":5,"added_by":{"user_id":"bob","display_name":"小红"}}}]}
        """.trimIndent()
        val rooms = CatalogRoomMapper.rooms(Gson().fromJson(json, RoomListDto::class.java))
        assertEquals(5, rooms.single().containsWork?.position)
        assertEquals("小红", rooms.single().containsWork?.addedBy?.displayName)
        assertEquals(6, rooms.single().sections.single().itemCount)
    }

    @Test
    fun `缺少必需字段时明确失败而不是显示错乱`() {
        val broken = roomJson.replace("\"revision\":3,", "")
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRoomMapper.room(Gson().fromJson(broken, RoomDto::class.java))
        }
        val noSections = roomJson.substringBefore("\"sections\"") + "\"sections\":[]}"
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRoomMapper.room(Gson().fromJson(noSections, RoomDto::class.java))
        }
    }

    @Test
    fun `设备只为房间的五个写接口签名`() {
        assertTrue(isCatalogRoomWrite("POST", "/v2/rooms"))
        assertTrue(isCatalogRoomWrite("DELETE", "/v2/rooms/r1"))
        assertTrue(isCatalogRoomWrite("POST", "/v2/rooms/r1/items"))
        assertTrue(isCatalogRoomWrite("DELETE", "/v2/rooms/r1/items/i1"))
        assertTrue(isCatalogRoomWrite("PUT", "/v2/rooms/r1/order"))
        assertFalse(isCatalogRoomWrite("PATCH", "/v2/rooms/r1"))
        assertFalse(isCatalogRoomWrite("PUT", "/v2/rooms/r1"))
        assertFalse(isCatalogRoomWrite("POST", "/v2/rooms/r1/items/i1"))
        assertFalse(isCatalogRoomWrite("POST", "/v2/works"))
    }
}
