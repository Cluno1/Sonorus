package io.github.cluno1.sonorus.shared.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchSessionTest {
    @Test
    fun 返回搜索页时同一请求已有结果就复用不再请求() {
        assertTrue(shouldReuseUniversalSearchAnswer("那时雨", "那时雨", answerValid = true))
    }

    @Test
    fun 换了搜索词或从未请求过都要重新请求() {
        assertFalse(shouldReuseUniversalSearchAnswer("那时雨", "晴天", answerValid = true))
        assertFalse(shouldReuseUniversalSearchAnswer(null, "那时雨", answerValid = true))
    }

    @Test
    fun 离开时请求被取消仍是加载中就要重新请求() {
        assertFalse(shouldReuseUniversalSearchAnswer("那时雨", "那时雨", answerValid = false))
    }

    @Test
    fun 返回键先关查看全部再回结果() {
        assertEquals(UniversalSearchBackStep.CLOSE_ALL_RESULTS, universalSearchBackStep(true, "那时雨", false))
    }

    @Test
    fun 返回键在结果页清空搜索回到最近搜索() {
        assertEquals(UniversalSearchBackStep.CLEAR_SEARCH, universalSearchBackStep(false, "那时雨", false))
        assertEquals(UniversalSearchBackStep.CLEAR_SEARCH, universalSearchBackStep(false, "", true))
    }

    @Test
    fun 返回键在最近搜索才离开搜索页() {
        assertEquals(UniversalSearchBackStep.LEAVE, universalSearchBackStep(false, "", false))
    }

    @Test
    fun 只有新打开的搜索页才自动弹键盘() {
        assertTrue(shouldFocusUniversalSearchOnOpen(true, ""))
        assertFalse(shouldFocusUniversalSearchOnOpen(true, "那时雨"))
        assertFalse(shouldFocusUniversalSearchOnOpen(false, ""))
    }
}
