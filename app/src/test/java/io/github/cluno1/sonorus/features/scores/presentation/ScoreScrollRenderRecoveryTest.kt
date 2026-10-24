package io.github.cluno1.sonorus.features.scores.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreScrollRenderRecoveryTest {
    @Test
    fun recoveryOnlyRunsAfterTheViewportIsStableForThreeFrames() {
        val detector = ScoreScrollSettleDetector()

        assertFalse(detector.observe(100))
        assertFalse(detector.observe(240))
        assertFalse(detector.observe(410))
        assertFalse(detector.observe(410))
        assertTrue(detector.observe(410))

        // A completed observation is consumed so a new scroll needs its own stable frames.
        assertFalse(detector.observe(410))
    }

    @Test
    fun resetRequiresFreshStableObservations() {
        val detector = ScoreScrollSettleDetector()
        assertFalse(detector.observe(300))
        assertFalse(detector.observe(300))

        detector.reset()

        assertFalse(detector.observe(300))
        assertFalse(detector.observe(300))
        assertTrue(detector.observe(300))
    }

    @Test
    fun `慢速跟随动画偶尔重复一帧不算停稳`() {
        // issue 119: < 1 px/frame linear animation repeats an integer position once at a time.
        val detector = ScoreScrollSettleDetector()
        val slowAnimation = listOf(500, 500, 501, 501, 502, 502, 503, 503, 504)
        assertTrue(slowAnimation.none(detector::observe))
    }

    @Test
    fun `正常的布局请求当帧就被处理，不触发滚动中补跑`() {
        val detector = ScoreStuckLayoutDetector()
        val healthyScroll = listOf(true, false, true, false, false, true, false)
        assertTrue(healthyScroll.none(detector::observe))
    }

    @Test
    fun `布局请求连续两帧未处理即在滚动中补跑一次`() {
        val detector = ScoreStuckLayoutDetector()
        assertFalse(detector.observe(true))
        assertTrue(detector.observe(true))
        // Consumed: a still-stuck request needs two more frames, so a long drag repairs at most
        // every other frame instead of on every one.
        assertFalse(detector.observe(true))
        assertTrue(detector.observe(true))
    }

    @Test
    fun `新一次滚动开始时清零挂起帧数`() {
        val detector = ScoreStuckLayoutDetector()
        assertFalse(detector.observe(true))
        detector.reset()
        assertFalse(detector.observe(true))
        assertTrue(detector.observe(true))
    }
}
