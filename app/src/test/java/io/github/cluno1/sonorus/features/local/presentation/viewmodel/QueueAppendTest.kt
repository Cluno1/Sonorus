package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAppendTest {
    @Test
    fun `整组入队恰好调用一次，顺序与页面一致`() {
        val calls = mutableListOf<Pair<List<String>, Int>>()
        queueWholeCollection(listOf("a", "b", "c", "d"), toItem = { it.uppercase() }) { items, skipped ->
            calls += items to skipped
        }
        assertEquals(1, calls.size)
        assertEquals(listOf("A", "B", "C", "D") to 0, calls.single())
    }

    @Test
    fun `无法入队的歌曲被跳过并计数，其余顺序不变`() {
        val calls = mutableListOf<Pair<List<String>, Int>>()
        queueWholeCollection(listOf("a", "x", "b", "y", "c"), toItem = { it.takeIf { s -> s !in setOf("x", "y") } }) { items, skipped ->
            calls += items to skipped
        }
        assertEquals(1, calls.size)
        assertEquals(listOf("a", "b", "c") to 2, calls.single())
    }

    @Test
    fun `全部无法入队时仍然调用一次，让用户得到提示`() {
        var calls = 0
        queueWholeCollection(listOf("x"), toItem = { null as String? }) { items, skipped ->
            calls++
            assertEquals(emptyList<String>(), items)
            assertEquals(1, skipped)
        }
        assertEquals(1, calls)
    }

    @Test
    fun `提示结果按加入和跳过数量区分`() {
        assertEquals(QueueAppendOutcome.ADDED, queueAppendOutcome(added = 12, skipped = 0))
        assertEquals(QueueAppendOutcome.PARTIAL, queueAppendOutcome(added = 10, skipped = 2))
        assertEquals(QueueAppendOutcome.NONE, queueAppendOutcome(added = 0, skipped = 3))
    }
}
