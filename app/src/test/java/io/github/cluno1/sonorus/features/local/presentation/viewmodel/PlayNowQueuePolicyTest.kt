package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/** issue 162: tapping one song inserts it right after the playing song and keeps the queue. */
class PlayNowQueuePolicyTest {

    @Test
    fun insertsAfterPlayingSongKeepingEveryOtherSongInOrder() {
        val queue = listOf("1", "2", "B", "4", "5")

        val (updated, newIndex) = PlayNowQueuePolicy.insertAfterCurrent(queue, currentIndex = 2, item = "A")

        assertEquals(listOf("1", "2", "B", "A", "4", "5"), updated)
        assertEquals(3, newIndex)
    }

    @Test
    fun playingLastSongAppendsTheTappedSong() {
        val (updated, newIndex) = PlayNowQueuePolicy.insertAfterCurrent(listOf("1", "B"), currentIndex = 1, item = "A")

        assertEquals(listOf("1", "B", "A"), updated)
        assertEquals(2, newIndex)
    }

    @Test
    fun emptyQueueStartsWithTheTappedSong() {
        assertEquals(0, PlayNowQueuePolicy.insertIndex(currentIndex = -1, itemCount = 0))
        assertEquals(listOf("A") to 0, PlayNowQueuePolicy.insertAfterCurrent(emptyList(), currentIndex = -1, item = "A"))
    }
}
