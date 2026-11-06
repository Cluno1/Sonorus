package io.github.cluno1.sonorus.features.local.presentation.viewmodel

/**
 * issue 162: one queue rule for every single-song tap.
 *
 * Invariant: tapping song A while B plays inserts A directly after B and plays A at once.
 * Every song already queued stays, in the same relative order; B is not removed and is not
 * scheduled again, so when A ends the song that used to follow B plays next. Only explicit
 * "play all" / "shuffle" actions on a collection replace the queue, and they never come here.
 *
 * An A that is already somewhere else in the queue is inserted again rather than moved, because
 * moving it would reorder the songs the user already had.
 */
internal object PlayNowQueuePolicy {

    /** Position A takes: right after the playing item, or 0 for an empty queue. */
    fun insertIndex(currentIndex: Int, itemCount: Int): Int =
        if (itemCount <= 0 || currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(itemCount)

    /** The queue after inserting [item] after [currentIndex], with A's index as the new current index. */
    fun <T> insertAfterCurrent(queue: List<T>, currentIndex: Int, item: T): Pair<List<T>, Int> {
        val index = insertIndex(currentIndex, queue.size)
        return queue.toMutableList().apply { add(index, item) } to index
    }
}
