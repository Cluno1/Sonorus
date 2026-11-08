package io.github.cluno1.sonorus.infrastructure.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerExtendPolicyTest {
    @Test
    fun `倒计时中途音量变化不重置睡眠定时`() {
        assertFalse(SleepTimerExtendPolicy.shouldExtend(fadeOut = true, remainingMs = 10 * 60_000L))
        assertFalse(SleepTimerExtendPolicy.shouldExtend(fadeOut = true, remainingMs = 10_001L))
    }

    @Test
    fun `淡出阶段调音量才延长`() {
        assertTrue(SleepTimerExtendPolicy.shouldExtend(fadeOut = true, remainingMs = 10_000L))
        assertTrue(SleepTimerExtendPolicy.shouldExtend(fadeOut = true, remainingMs = 1L))
    }

    @Test
    fun `不淡出的定时或已结束的定时不延长`() {
        assertFalse(SleepTimerExtendPolicy.shouldExtend(fadeOut = false, remainingMs = 5_000L))
        assertFalse(SleepTimerExtendPolicy.shouldExtend(fadeOut = true, remainingMs = 0L))
    }

    @Test
    fun `延长固定五分钟而不是整段时长`() {
        assertEquals(5 * 60_000L, SleepTimerExtendPolicy.EXTEND_MS)
    }
}
