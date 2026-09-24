package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class CatalogApiClient(
    serverUrl: String,
    legacyToken: String?,
    credentials: CatalogCredentialsStore?,
) {
    private val origin = (CatalogEndpoint.normalize(serverUrl) + "/").toHttpUrl()
    private val legacyAuth = legacyToken?.trim()?.also { require(it.isNotEmpty()) }
    private val deviceAuth = credentials?.loadDevice()?.let {
        CatalogDeviceAuthClient(serverUrl, credentials)
    }

    constructor(serverUrl: String, token: String) : this(serverUrl, token, null)

    constructor(serverUrl: String, credentials: CatalogCredentialsStore) : this(
        serverUrl,
        credentials.loadToken().takeIf { credentials.loadDevice() == null },
        credentials,
    )

    private val httpClient = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val request = chain.request()
                    val precomputedHash = request.header(INTERNAL_CONTENT_SHA256)
                    val builder = request.newBuilder().removeHeader(INTERNAL_CONTENT_SHA256)
                    if (request.url.encodedPath != "/healthz" && CatalogEndpoint.sameOrigin(request.url, origin)) {
                        if (deviceAuth != null) {
                            deviceAuth.proof(request, precomputedHash).forEach(builder::header)
                        } else {
                            builder.header("Authorization", "Bearer ${requireNotNull(legacyAuth)}")
                        }
                    }
                    chain.proceed(builder.build())
                })
                .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(origin)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    val api: CatalogApi = retrofit.create(CatalogApi::class.java)
    val lyricsWriteApi: CatalogLyricsWriteApi = retrofit.create(CatalogLyricsWriteApi::class.java)
    val chorusApi: CatalogChorusApi = retrofit.create(CatalogChorusApi::class.java)
    val adminApi: CatalogAdminApi = retrofit.create(CatalogAdminApi::class.java)
    val imageApi: CatalogImageApi = retrofit.create(CatalogImageApi::class.java)

    fun uploadChorusToSignedUrl(url: String, file: File, mediaType: String) {
        require(CatalogPlaybackPolicy.isSignedObjectStoreUrl(url)) {
            "chorus upload target is not a signed COS URL"
        }
        val request = Request.Builder()
            .url(url)
            .put(ProgressFileRequestBody(file, mediaType.toMediaType()) { _, _ -> })
            .build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "COS upload failed (${response.code})" }
        }
    }

    /**
     * Streams one prepared image directly to COS. The signed URL is consumed in-memory and is
     * deliberately never exposed to Room or preferences.
     */
    suspend fun uploadImageToSignedUrl(
        url: String,
        file: File,
        requiredHeaders: Map<String, String>,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ) {
        require(CatalogPlaybackPolicy.isSignedObjectStoreUrl(url)) {
            "image upload target is not a signed COS URL"
        }
        val normalized = requiredHeaders.entries.associate { it.key.lowercase() to it.value }
        require(normalized.keys.all { it in IMAGE_UPLOAD_HEADERS }) {
            "image upload target requested an unsupported header"
        }
        val contentType = normalized["content-type"]?.toMediaType()
            ?: throw IllegalArgumentException("image upload target omitted Content-Type")
        require(normalized["content-md5"]?.isNotBlank() == true) {
            "image upload target omitted Content-MD5"
        }
        val builder = Request.Builder()
            .url(url)
            .put(ProgressFileRequestBody(file, contentType, onProgress))
        requiredHeaders.forEach { (name, value) -> builder.header(name, value) }
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!continuation.isActive) return
                        if (response.isSuccessful) {
                            continuation.resume(Unit)
                        } else {
                            continuation.resumeWithException(
                                IOException("COS image upload failed (${response.code})"),
                            )
                        }
                    }
                }
            })
        }
    }

    fun downloadImageFromSignedUrl(
        url: String,
        output: OutputStream,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        require(CatalogPlaybackPolicy.isSignedImageDeliveryUrl(url)) {
            "image download target is not a signed delivery URL"
        }
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("image download failed (${response.code})")
            val body = response.body
            val expected = body.contentLength().takeIf { it >= 0 }
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    received += count
                    onProgress(received, expected)
                }
            }
        }
    }

    fun resolveAssetUrl(relativeOrAbsolute: String): HttpUrl {
        val resolved = origin.resolve(relativeOrAbsolute) ?: throw IllegalArgumentException("asset URL is invalid")
        require(CatalogEndpoint.sameOrigin(origin, resolved)) { "asset URL changed origin" }
        require(resolved.encodedUsername.isEmpty() && resolved.encodedPassword.isEmpty()) { "asset URL contains userinfo" }
        require(resolved.fragment == null) { "asset URL contains fragment" }
        return resolved
    }

    private companion object {
        const val INTERNAL_CONTENT_SHA256 = "X-Sonorus-Content-SHA256"
        val IMAGE_UPLOAD_HEADERS = setOf("content-type", "content-md5")
    }
}

private class ProgressFileRequestBody(
    private val file: File,
    private val mediaType: MediaType,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var sent = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
                sent += count
                onProgress(sent, total)
            }
        }
    }
}
