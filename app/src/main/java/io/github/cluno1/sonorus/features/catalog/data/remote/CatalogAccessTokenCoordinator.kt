package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.data.CatalogDeviceCredentials
import java.io.IOException
import java.time.Instant

/**
 * Process-wide coordination of the short-lived device access token.
 *
 * Invariants:
 * - At most one refresh runs at a time across every [CatalogDeviceAuthClient] instance; a caller
 *   that waited re-reads the store and reuses a token another caller just refreshed.
 * - A token the server rejected is never reused: [usable] with `rejectedToken` refreshes unless the
 *   store already holds a different, fresh token.
 * - An existing re-enrollment mark is retried through the existing device session at most once per
 *   [recoveryIntervalSeconds], so a really revoked device does not hit the server on every request.
 *
 * Why: issue 115 — several clients refreshed in parallel on app start and one 401 caused by a stale
 * read was persisted as "re-enrollment required" while the device and its 90-day session were valid.
 *
 * Silent failure if broken: duplicate refreshes on every start, or a recoverable access-token 401
 * becomes a permanent local block that no request reaches the server to correct.
 *
 * Rejected: per-instance `@Synchronized` (does not span instances); longer token TTL (hides the race).
 */
internal class CatalogAccessTokenCoordinator(
    private val clock: () -> Long = { Instant.now().epochSecond },
    private val recoveryIntervalSeconds: Long = RECOVERY_INTERVAL_SECONDS,
) {
    private val lock = Any()
    private var nextRecoveryAt = Long.MIN_VALUE

    fun usable(
        load: () -> CatalogDeviceCredentials?,
        refresh: (CatalogDeviceCredentials) -> CatalogDeviceCredentials,
        rejectedToken: String? = null,
    ): CatalogDeviceCredentials {
        if (rejectedToken == null) {
            load()?.takeIf(::isFresh)?.let { return it }
        }
        synchronized(lock) {
            val stored = load() ?: throw IOException("设备尚未登记")
            val replaced = rejectedToken == null || stored.accessToken != rejectedToken
            if (replaced && isFresh(stored)) return stored
            return refresh(stored)
        }
    }

    /** True when the caller may try the existing session once to clear a re-enrollment mark. */
    fun tryBeginRecovery(): Boolean = synchronized(lock) {
        val now = clock()
        if (now < nextRecoveryAt) return false
        nextRecoveryAt = now + recoveryIntervalSeconds
        true
    }

    private fun isFresh(credentials: CatalogDeviceCredentials): Boolean =
        credentials.accessTokenExpiresAtEpochSeconds > clock() + REFRESH_MARGIN_SECONDS

    companion object {
        const val REFRESH_MARGIN_SECONDS = 30L
        const val RECOVERY_INTERVAL_SECONDS = 60L
        val shared = CatalogAccessTokenCoordinator()
    }
}
