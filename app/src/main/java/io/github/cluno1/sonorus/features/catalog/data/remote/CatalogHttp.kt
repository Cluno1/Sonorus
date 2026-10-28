package io.github.cluno1.sonorus.features.catalog.data.remote

import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

/**
 * issue 128: one connection pool for every Catalog request in the process.
 *
 * Invariants
 * - Only the pool is shared. Each [CatalogApiClient] / [CatalogDeviceAuthClient] is still built per
 *   call, so it still reads the current server and credentials, and keeps its own timeouts,
 *   redirect policy and signing interceptor. Identity travels in each request's headers, never on
 *   the connection; OkHttp only reuses a connection for the same host, port and TLS setup.
 * - The Dispatcher is not shared: Retrofit suspend calls are enqueued and a shared Dispatcher caps
 *   them at 5 per host, so one long upload plus a few searches would queue reads.
 * - Idle connections are dropped after [IDLE_SECONDS], just under the public server's 60 s
 *   keep-alive, so the client gives up a connection before the server closes it under a request.
 *
 * Why this way
 * - Before, every request built two fresh clients (nonce + real request) and opened two
 *   connections; on a weak network the connect step is what stalled (issue 127, 2026-10-28 logs).
 *
 * Rejected
 * - Caching one CatalogApiClient: every credential or server change would need an invalidation,
 *   and a missed one silently signs with the old identity.
 */
internal object CatalogHttp {
    const val IDLE_SECONDS = 55L

    val pool: ConnectionPool = ConnectionPool(5, IDLE_SECONDS, TimeUnit.SECONDS)
}
