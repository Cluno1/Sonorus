package io.github.cluno1.sonorus.features.local.presentation.viewmodel

/**
 * Sonorus issue 169: "add to queue" on a whole album / artist / folder / search page.
 *
 * Invariants:
 * - The whole collection goes to the player in ONE call, in page order. The old code looped over the
 *   single-song action, which showed one message per song (100 songs = 100 toasts in a row) and, for
 *   public songs, ran one authentication request per song so the queue order followed network timing.
 * - Exactly one message per batch, with how many were added and how many could not be.
 *
 * Generic over the song / entry types so it can be unit-tested without Android `Uri`.
 */
data class QueueAppendItem<S, E>(val song: S, val entry: E?)

/** Builds the items in page order and hands them over exactly once; songs that cannot be queued are counted. */
inline fun <T, I> queueWholeCollection(
    songs: List<T>,
    toItem: (T) -> I?,
    append: (items: List<I>, skipped: Int) -> Unit,
) {
    val items = songs.mapNotNull(toItem)
    append(items, songs.size - items.size)
}

enum class QueueAppendOutcome { ADDED, PARTIAL, NONE }

fun queueAppendOutcome(added: Int, skipped: Int): QueueAppendOutcome = when {
    added <= 0 -> QueueAppendOutcome.NONE
    skipped > 0 -> QueueAppendOutcome.PARTIAL
    else -> QueueAppendOutcome.ADDED
}
