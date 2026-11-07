package io.github.cluno1.sonorus.features.catalog.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * issue 176: every write the app declares on the Catalog client must be one the device signs.
 * A missing entry made the first lyrics save throw in the OkHttp interceptor and crash the app.
 */
class CatalogSignedWritesContractTest {
    private val signedApis = listOf(
        CatalogApi::class.java, CatalogLyricsWriteApi::class.java, CatalogWorkLyricsWriteApi::class.java,
        CatalogWorkTagsWriteApi::class.java, CatalogRoomsApi::class.java, CatalogAnnouncementsApi::class.java,
        CatalogAdminApi::class.java, CatalogImageApi::class.java, CatalogMusicApi::class.java,
    )

    private fun declaredWrites(): List<Pair<String, String>> = signedApis.flatMap { api ->
        api.declaredMethods.mapNotNull { method ->
            method.getAnnotation(POST::class.java)?.let { "POST" to it.value }
                ?: method.getAnnotation(PUT::class.java)?.let { "PUT" to it.value }
                ?: method.getAnnotation(PATCH::class.java)?.let { "PATCH" to it.value }
                ?: method.getAnnotation(DELETE::class.java)?.let { "DELETE" to it.value }
                ?: method.getAnnotation(HTTP::class.java)?.takeIf { it.method != "GET" }?.let { it.method to it.path }
        }
    }.map { (verb, path) -> verb to "/" + path.substringBefore('?').replace(Regex("\\{[^}]+}"), "x1") }

    @Test
    fun `客户端声明的每个写接口设备都会签名`() {
        val unsigned = declaredWrites().filterNot { (verb, path) -> catalogDeviceMaySign(verb, path) }
        assertEquals(emptyList<Pair<String, String>>(), unsigned)
    }

    @Test
    fun `反射确实收集到了全部写接口`() {
        // Admin 3, rendition/work lyrics and tags 3, images 12, public music 3, rooms 6.
        assertEquals(27, declaredWrites().size)
    }
}
