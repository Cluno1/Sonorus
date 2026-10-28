package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * issue 127: opening one online lyrics page.
 *
 * Invariants
 * - Each attempt is capped at [ATTEMPT_TIMEOUT_MS]; a timeout or an unreachable server is retried
 *   once on its own, so the user does not have to press "retry" for a stalled connection.
 * - A server answer (404 "no lyrics", 5xx, bad data) is final: retrying it only repeats the error.
 *
 * Why this way
 * - The backend answers a single song in well under a second, and stalled opens never reached it
 *   (2026-10-28 logs). The stall sits in the phone's fresh connection plus device nonce round trip,
 *   which the default 12 s connect / 30 s read timeouts left spinning; a second attempt was fast.
 *
 * What breaks silently if changed
 * - Retrying 4xx/5xx doubles the load on the platforms for answers that cannot change.
 * - Dropping the cap brings back a spinner that can last 40 s before the error card shows.
 */
internal object ExternalLyricsLoader {
    const val ATTEMPT_TIMEOUT_MS = 8_000L
    const val ATTEMPTS = 2

    suspend fun <T> load(
        attempts: Int = ATTEMPTS,
        timeoutMs: Long = ATTEMPT_TIMEOUT_MS,
        onRetry: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        fetch: suspend () -> Result<T>,
    ): Result<T> {
        var last: Result<T> = Result.failure(IllegalStateException("no attempt made"))
        for (attempt in 1..attempts) {
            last = try {
                withTimeout(timeoutMs) { fetch() }
            } catch (error: TimeoutCancellationException) {
                Result.failure(CatalogFailure.Unreachable(error))
            }
            val error = last.exceptionOrNull() ?: return last
            if (error !is CatalogFailure.Unreachable || attempt == attempts) return last
            onRetry(attempt, error)
        }
        return last
    }
}
