package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogApiClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun sendsTokenOnlyToAuthenticatedCatalogRequests() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"status\":\"ok\"}"))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"items\":[],\"next_cursor\":null}"))
        val client = CatalogApiClient(server.url("/").toString(), "top-secret")

        client.api.health()
        client.api.works(limit = 1)

        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("Bearer top-secret", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun 每次新建的客户端复用同一条连接() = runBlocking {
        repeat(2) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"status\":\"ok\"}"))
        }
        CatalogApiClient(server.url("/").toString(), "token").api.health()
        CatalogApiClient(server.url("/").toString(), "token").api.health()

        assertEquals(0, server.takeRequest().sequenceNumber)
        // issue 128: 1 = second request on the same connection, not a new connect.
        assertEquals(1, server.takeRequest().sequenceNumber)
    }

    @Test
    fun 不同服务器不共用连接() = runBlocking {
        val other = MockWebServer()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"status\":\"ok\"}"))
            other.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"status\":\"ok\"}"))
            CatalogApiClient(server.url("/").toString(), "token").api.health()
            CatalogApiClient(other.url("/").toString(), "token").api.health()

            assertEquals(1, server.requestCount)
            assertEquals(1, other.requestCount)
            assertEquals(0, other.takeRequest().sequenceNumber)
        } finally {
            other.close()
        }
    }

    @Test
    fun refusesCrossOriginAssetUrl() {
        val client = CatalogApiClient(server.url("/").toString(), "token")
        assertThrows(IllegalArgumentException::class.java) {
            client.resolveAssetUrl("https://evil.example/v2/assets/11111111-1111-4111-8111-111111111111/content")
        }
    }

    @Test
    fun doesNotFollowRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example/collect"))
        val response = CatalogApiClient(server.url("/").toString(), "token").api.works(limit = 1)
        assertEquals(302, response.code())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun libraryEndpointsUseFrozenIssue12Paths() = runBlocking {
        repeat(3) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
        }
        val client = CatalogApiClient(server.url("/").toString(), "top-secret")
        client.api.librarySongs(cursor = "next", limit = 73)
        client.api.libraryAlbums(limit = 1)
        client.api.libraryAlbum("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

        assertEquals("/v2/library/songs?cursor=next&limit=73", server.takeRequest().path)
        assertEquals("/v2/library/albums?limit=1", server.takeRequest().path)
        assertEquals(
            "/v2/library/albums/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            server.takeRequest().path,
        )
    }

    @Test
    fun deliveredCosAssetNeverReceivesBackendBearer() = runBlocking {
        val objectStore = MockWebServer()
        try {
            objectStore.enqueue(MockResponse().setBody("musicxml"))
            val client = CatalogApiClient(server.url("/").toString(), "top-secret")
            client.api.deliveredAsset(objectStore.url("/score.musicxml").toString())
            assertNull(objectStore.takeRequest().headers["Authorization"])
        } finally {
            objectStore.close()
        }
    }

    @Test
    fun invalidCredentialsCrossAsyncInterceptorAsTransportFailure() {
        val completed = CountDownLatch(1)
        var failure: IOException? = null
        var responseReceived = false
        val client = OkHttpClient.Builder()
            .addInterceptor { throw CatalogFailure.InvalidCredentials() }
            .build()

        client.newCall(Request.Builder().url(server.url("/v2/library/songs")).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    failure = e
                    completed.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    responseReceived = true
                    completed.countDown()
                }
            })

        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertTrue(failure is CatalogFailure.InvalidCredentials)
        assertTrue(!responseReceived)
    }
}
