package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.data.CatalogDeviceCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CatalogAccessTokenCoordinatorTest {
    private var now = 1_000_000L

    private fun credentials(token: String, expiresAt: Long) = CatalogDeviceCredentials(
        serverUrl = "https://catalog.test",
        userId = "user",
        deviceId = "device",
        sessionId = "session",
        accessToken = token,
        accessTokenExpiresAtEpochSeconds = expiresAt,
        sessionExpiresAt = "2027-01-13T10:19:24Z",
    )

    private class FakeStore(@Volatile var stored: CatalogDeviceCredentials) {
        val refreshes = AtomicInteger()
    }

    private fun refresher(store: FakeStore, delayMillis: Long = 0): (CatalogDeviceCredentials) -> CatalogDeviceCredentials = {
        val count = store.refreshes.incrementAndGet()
        if (delayMillis > 0) Thread.sleep(delayMillis)
        credentials("fresh-$count", now + 600).also { store.stored = it }
    }

    @Test
    fun `过期 token 被多个调用方同时使用时只续期恰好 1 次`() {
        val coordinator = CatalogAccessTokenCoordinator(clock = { now })
        val store = FakeStore(credentials("expired", now - 82_000))
        val callers = 4
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(callers)
        val results = (1..callers).map {
            pool.submit<String> {
                start.await()
                coordinator.usable({ store.stored }, refresher(store, delayMillis = 50)).accessToken
            }
        }
        start.countDown()
        val tokens = results.map { it.get(5, TimeUnit.SECONDS) }
        pool.shutdownNow()

        assertEquals(1, store.refreshes.get())
        assertEquals(List(callers) { "fresh-1" }, tokens)
    }

    @Test
    fun `未过期 token 直接复用不续期`() {
        val coordinator = CatalogAccessTokenCoordinator(clock = { now })
        val store = FakeStore(credentials("valid", now + 300))

        val result = coordinator.usable({ store.stored }, refresher(store))

        assertEquals("valid", result.accessToken)
        assertEquals(0, store.refreshes.get())
    }

    @Test
    fun `被拒 token 即使有效期看似未过也强制续期恰好 1 次`() {
        // Issue 115: the rejected token was paired with an expiry written by another refresher.
        val coordinator = CatalogAccessTokenCoordinator(clock = { now })
        val store = FakeStore(credentials("stale", now + 590))

        val result = coordinator.usable({ store.stored }, refresher(store), rejectedToken = "stale")

        assertEquals("fresh-1", result.accessToken)
        assertEquals(1, store.refreshes.get())
    }

    @Test
    fun `被拒 token 已被其他调用方替换为新 token 时不再续期`() {
        val coordinator = CatalogAccessTokenCoordinator(clock = { now })
        val store = FakeStore(credentials("other-fresh", now + 590))

        val result = coordinator.usable({ store.stored }, refresher(store), rejectedToken = "stale")

        assertEquals("other-fresh", result.accessToken)
        assertEquals(0, store.refreshes.get())
    }

    @Test
    fun `重新登记标记的恢复尝试在间隔内只放行 1 次`() {
        val coordinator = CatalogAccessTokenCoordinator(clock = { now }, recoveryIntervalSeconds = 60)

        assertTrue(coordinator.tryBeginRecovery())
        assertFalse(coordinator.tryBeginRecovery())
        now += 59
        assertFalse(coordinator.tryBeginRecovery())
        now += 1
        assertTrue(coordinator.tryBeginRecovery())
    }
}
