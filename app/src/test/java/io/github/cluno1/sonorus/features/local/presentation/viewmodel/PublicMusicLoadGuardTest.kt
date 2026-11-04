package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicMusicLoadGuardTest {
    @Test
    fun connectionTimeoutIsReportedWithoutEscapingTheCoroutine() = runBlocking {
        val timeout = SocketTimeoutException("connect timed out")
        val failures = mutableListOf<Exception>()

        guardPublicMusicLoad(onFailure = failures::add) { throw timeout }

        assertEquals(listOf(timeout), failures)
    }

    @Test
    fun songChangeCancellationStillPropagates() {
        val cancellation = CancellationException("next song")
        val failures = mutableListOf<Exception>()

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { guardPublicMusicLoad(onFailure = failures::add) { throw cancellation } }
        }

        assertSame(cancellation, thrown)
        assertEquals(emptyList<Exception>(), failures)
    }
}
