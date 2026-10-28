package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** issue 127: a stalled lyrics open retries once by itself; server answers are final. */
class ExternalLyricsLoaderTest {
    private fun load(vararg answers: suspend () -> Result<String>): Pair<Result<String>, Int> = runBlocking {
        var calls = 0
        val result = ExternalLyricsLoader.load(timeoutMs = 200) { answers[calls++].invoke() }
        result to calls
    }

    @Test
    fun 第一次卡住超时后自动重试一次并成功() {
        val (result, calls) = load({ delay(5_000); Result.success("late") }, { Result.success("歌词") })
        assertEquals("歌词", result.getOrNull())
        assertEquals(2, calls)
    }

    @Test
    fun 连不上服务器时自动重试一次() {
        val (result, calls) = load(
            { Result.failure(CatalogFailure.Unreachable(IOException("reset"))) },
            { Result.success("歌词") },
        )
        assertEquals("歌词", result.getOrNull())
        assertEquals(2, calls)
    }

    @Test
    fun 第一次就成功时恰好请求一次() {
        val (result, calls) = load({ Result.success("歌词") })
        assertEquals("歌词", result.getOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun 平台没有歌词的404不重试() {
        val (result, calls) = load({ Result.failure(CatalogFailure.Server(404)) })
        assertEquals(404, (result.exceptionOrNull() as CatalogFailure.Server).statusCode)
        assertEquals(1, calls)
    }

    @Test
    fun 两次都卡住时只试两次并报连不上() {
        val (result, calls) = load(
            { delay(5_000); Result.success("late") },
            { delay(5_000); Result.success("late") },
        )
        assertTrue(result.exceptionOrNull() is CatalogFailure.Unreachable)
        assertEquals(2, calls)
    }
}
