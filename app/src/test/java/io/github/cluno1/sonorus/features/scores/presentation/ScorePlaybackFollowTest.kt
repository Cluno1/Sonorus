package io.github.cluno1.sonorus.features.scores.presentation

import alphaTab.midi.MidiTickLookupFindBeatResultCursorMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScorePlaybackFollowTest {
    @Test
    fun scrollOffsetKeepsCurrentSystemInUpperViewport() {
        assertEquals(
            -288.0,
            scoreFollowScrollOffset(viewportHeightPx = 1600, density = 2f),
            0.0001,
        )
    }

    @Test
    fun invalidViewportDoesNotCreateAnOffset() {
        assertEquals(0.0, scoreFollowScrollOffset(viewportHeightPx = 0, density = 2f), 0.0)
        assertEquals(0.0, scoreFollowScrollOffset(viewportHeightPx = 1600, density = 0f), 0.0)
    }

    @Test
    fun tailSpaceLetsFinalSystemReachTheSameReadingPosition() {
        assertEquals(1024, scoreFollowTailSpace(viewportHeightPx = 1600))
        assertEquals(
            13_024,
            scoreFollowContentMinHeight(scoreHeightPx = 12_000, viewportHeightPx = 1600),
        )
        assertEquals(0, scoreFollowTailSpace(viewportHeightPx = 0))
    }

    @Test
    fun cursorTransitionStaysContinuousAndRejectsInvalidDurations() {
        assertEquals(
            ScoreCursorTransition(targetX = 140.0, durationMillis = 800L),
            stableScoreCursorTransition(
                startX = 100.0,
                endX = 120.0,
                durationMillis = 400.0,
                cursorMode = MidiTickLookupFindBeatResultCursorMode.ToNextBext,
            ),
        )
        assertEquals(
            ScoreCursorTransition(targetX = 120.0, durationMillis = 0L),
            stableScoreCursorTransition(
                startX = 100.0,
                endX = 120.0,
                durationMillis = -1.0,
                cursorMode = MidiTickLookupFindBeatResultCursorMode.ToEndOfBeat,
            ),
        )
        assertEquals(
            ScoreCursorTransition(targetX = 120.0, durationMillis = 400L),
            stableScoreCursorTransition(
                startX = 100.0,
                endX = 120.0,
                durationMillis = 400.0,
                cursorMode = MidiTickLookupFindBeatResultCursorMode.ToNextBext,
                hasFollowingBeat = false,
            ),
        )
    }
}

class ScorePlaybackFollowIssue119Test {
    @Test
    fun `动画上一帧写入的位置没被挪动时不算用户滚动`() {
        org.junit.Assert.assertFalse(scoreFollowUserMoved(lastApplied = null, actual = 1200))
        org.junit.Assert.assertFalse(scoreFollowUserMoved(lastApplied = 1200, actual = 1200))
        org.junit.Assert.assertFalse(scoreFollowUserMoved(lastApplied = 1200, actual = 1203))
    }

    @Test
    fun `用户拖动或回到顶部让位置偏离超过容差就算用户滚动`() {
        org.junit.Assert.assertTrue(scoreFollowUserMoved(lastApplied = 1200, actual = 1196))
        org.junit.Assert.assertTrue(scoreFollowUserMoved(lastApplied = 1200, actual = 0))
    }

    @Test
    fun `暂停后从行中间续播，从当前位置接着滚剩余时长`() {
        val segment = scoreFollowSegment(systemStartScroll = 1000, targetScroll = 1600, systemDurationMs = 10_000, elapsedFraction = 0.25)
        org.junit.Assert.assertEquals(ScoreFollowSegment(startScroll = 1150, durationMs = 7_500), segment)
    }

    @Test
    fun `行首开始时与原来一致，异常进度被夹紧`() {
        org.junit.Assert.assertEquals(ScoreFollowSegment(1000, 10_000), scoreFollowSegment(1000, 1600, 10_000, 0.0))
        org.junit.Assert.assertEquals(ScoreFollowSegment(1000, 10_000), scoreFollowSegment(1000, 1600, 10_000, Double.NaN))
        org.junit.Assert.assertEquals(ScoreFollowSegment(1600, 0), scoreFollowSegment(1000, 1600, 10_000, 1.7))
    }
}
