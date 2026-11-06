package io.github.cluno1.sonorus.shared.presentation.components.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmMessageBusTest {
    private val fallbacks = mutableListOf<RhythmMessage>()
    private val bus = RhythmMessageBus { fallbacks += it }

    @Test
    fun `有可见宿主时只投递到应用内，不弹系统 Toast`() {
        bus.hostVisible()
        assertTrue(bus.post("已添加到队列", RhythmMessageKind.SUCCESS))
        assertEquals("已添加到队列", bus.latest.value?.text)
        assertEquals(0, fallbacks.size)
    }

    @Test
    fun `没有可见宿主时恰好兜底一次`() {
        assertFalse(bus.post("连接失败", RhythmMessageKind.ERROR))
        assertEquals(1, fallbacks.size)
        assertEquals("连接失败", fallbacks.single().text)
        assertNull(bus.latest.value)
    }

    @Test
    fun `连续多条只保留最新一条，不排队`() {
        bus.hostVisible()
        repeat(100) { bus.post("第 $it 首", RhythmMessageKind.INFO) }
        assertEquals("第 99 首", bus.latest.value?.text)
        assertEquals(0, fallbacks.size)
    }

    @Test
    fun `宿主隐藏后改走兜底，计数不会减成负数`() {
        bus.hostVisible()
        bus.hostHidden()
        bus.hostHidden()
        bus.post("已保存", RhythmMessageKind.SUCCESS)
        assertEquals(1, fallbacks.size)
        bus.hostVisible()
        assertTrue(bus.post("已保存", RhythmMessageKind.SUCCESS))
        assertEquals(1, fallbacks.size)
    }

    @Test
    fun `已显示的消息被标记后不会在重建的宿主上再显示`() {
        bus.hostVisible()
        bus.post("已删除", RhythmMessageKind.SUCCESS)
        val shown = bus.latest.value!!
        bus.consumed(shown.id - 1)
        assertEquals(shown, bus.latest.value)
        bus.consumed(shown.id)
        assertNull(bus.latest.value)
    }
}
