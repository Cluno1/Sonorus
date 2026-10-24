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
}
