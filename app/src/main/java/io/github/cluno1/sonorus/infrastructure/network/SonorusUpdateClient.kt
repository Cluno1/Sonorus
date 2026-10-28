/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.network

import android.content.Context
import android.os.Build
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogDeviceAuthClient
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SonorusUpdateAsset(
    val abi: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class SonorusUpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val applicationId: String,
    val signingCertificateSha256: String,
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String,
    val minimumAndroidSdk: Int,
    val mandatory: Boolean,
    val releaseNotes: List<String>,
    val assets: List<SonorusUpdateAsset>,
    val signatureAlgorithm: String,
    val manifestSignature: String,
)

data class VerifiedSonorusUpdate(
    val manifest: SonorusUpdateManifest,
    val asset: SonorusUpdateAsset,
    val downloadUrl: String,
    val etag: String?,
    val lastModified: String?,
)

sealed interface SonorusUpdateFetchResult {
    data object NotModified : SonorusUpdateFetchResult
    data class Available(val update: VerifiedSonorusUpdate) : SonorusUpdateFetchResult
}

internal fun interface ManifestSignatureVerifier {
    fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean
}

internal object TinkEd25519ManifestSignatureVerifier : ManifestSignatureVerifier {
    override fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean =
        try {
            Ed25519Verify(publicKey).verify(signature, message)
            true
        } catch (_: GeneralSecurityException) {
            false
        }
}

/** Canonical JSON: UTF-8, lexicographically sorted object keys, compact encoding, no HTML escaping. */
internal object SonorusManifestCanonicalizer {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun payload(manifest: SonorusUpdateManifest): ByteArray {
        val element = gson.toJsonTree(manifest).asJsonObject
        element.remove("manifestSignature")
        return canonicalize(element).toByteArray(Charsets.UTF_8)
    }

    private fun canonicalize(element: JsonElement): String = when {
        element is JsonNull -> "null"
        element.isJsonObject -> element.asJsonObject.entrySet()
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") {
                gson.toJson(it.key) + ":" + canonicalize(it.value)
            }
        element.isJsonArray -> element.asJsonArray.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalize(it)
        }
        else -> gson.toJson(element)
    }
}

internal object SonorusUpdateValidator {
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")
    private val allowedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal")

    fun validate(
        manifest: SonorusUpdateManifest,
        expectedChannel: String,
        expectedApplicationId: String,
        expectedSigningCertificateSha256: String,
        publicKeyBase64: String,
        verifier: ManifestSignatureVerifier = TinkEd25519ManifestSignatureVerifier,
    ) {
        require(manifest.schemaVersion == 1) { "Unsupported update manifest schema" }
        require(manifest.channel == expectedChannel) { "Update channel does not match this build" }
        require(manifest.applicationId == expectedApplicationId) { "Update package does not match this app" }
        require(manifest.versionCode > 0 && manifest.versionName.isNotBlank()) { "Invalid update version" }
        require(manifest.minimumAndroidSdk >= 1) { "Invalid minimum Android SDK" }
        require(manifest.signatureAlgorithm == "Ed25519") { "Unsupported manifest signature algorithm" }
        require(normalizeDigest(manifest.signingCertificateSha256) == normalizeDigest(expectedSigningCertificateSha256)) {
            "Update signing certificate does not match this installation"
        }
        require(manifest.assets.isNotEmpty()) { "Update manifest contains no APK assets" }
        manifest.assets.forEach { asset ->
            require(asset.abi in allowedAbis) { "Unsupported update ABI" }
            require(asset.fileName.matches(Regex("^[A-Za-z0-9._-]+\\.apk$"))) { "Invalid update file name" }
            require(asset.sizeBytes > 0) { "Invalid update size" }
            require(asset.sha256.lowercase(Locale.ROOT).matches(sha256Pattern)) { "Invalid update SHA-256" }
            require(asset.url.isNotBlank()) { "Missing update URL" }
        }

        val publicKey = decodeBase64(publicKeyBase64, "Update manifest public key is not configured")
        require(publicKey.size == 32) { "Update manifest public key must contain 32 bytes" }
        val signature = decodeBase64(manifest.manifestSignature, "Invalid update manifest signature")
        require(signature.size == 64) { "Update manifest signature must contain 64 bytes" }
        require(verifier.verify(publicKey, signature, SonorusManifestCanonicalizer.payload(manifest))) {
            "Update manifest signature verification failed"
        }
    }

    private fun decodeBase64(value: String, error: String): ByteArray {
        require(value.isNotBlank()) { error }
        return runCatching { Base64.getDecoder().decode(value) }
            .recoverCatching { Base64.getUrlDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException(error) }
    }

    private fun normalizeDigest(value: String): String = value
        .replace(":", "")
        .trim()
        .lowercase(Locale.ROOT)
}

internal object SonorusUpdateAssetSelector {
    fun select(assets: List<SonorusUpdateAsset>, supportedAbis: List<String>): SonorusUpdateAsset? {
        supportedAbis.forEach { abi -> assets.firstOrNull { it.abi == abi }?.let { return it } }
        return assets.firstOrNull { it.abi == "universal" }
    }
}

internal object SonorusUpdateCosRedirectPolicy {
    private const val HOST = "sonorus-updates-1328751369.cos.ap-guangzhou.myqcloud.com"
    private val privateHeaderPrefixes = listOf("x-rhythm-", "x-sonorus-")
    private val requiredQueryParameters = setOf(
        "q-ak",
        "q-sign-time",
        "q-key-time",
        "q-signature",
    )

    fun validate(url: HttpUrl, expectedPath: String) {
        require(url.scheme == "https" && url.host == HOST && url.port == 443) {
            "APK redirect left the trusted COS origin"
        }
        require(url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty()) {
            "APK redirect contains userinfo"
        }
        require(url.encodedPath == expectedPath && url.fragment == null) {
            "APK redirect does not match the signed manifest identity"
        }
        require(url.queryParameter("q-sign-algorithm") == "sha1") {
            "APK redirect is not a COS v5 signed URL"
        }
        require(url.queryParameter("q-header-list") == "host") {
            "APK redirect does not bind the COS host"
        }
        requiredQueryParameters.forEach { name ->
            require(url.queryParameterValues(name).singleOrNull().isNullOrBlank().not()) {
                "APK redirect is missing COS signature data"
            }
        }
    }

    fun sanitize(request: Request): Request {
        val builder = request.newBuilder()
            .removeHeader("Authorization")
            .removeHeader("Proxy-Authorization")
            .removeHeader("Cookie")
            .removeHeader("If-Match")
            .removeHeader("If-None-Match")
        request.headers.names()
            .filter { name -> privateHeaderPrefixes.any(name.lowercase(Locale.ROOT)::startsWith) }
            .forEach(builder::removeHeader)
        return builder.build()
    }
}

/** First-party update transport. Every request stays on the enrolled Catalog origin and is device-signed. */
class SonorusUpdateClient(
    context: Context,
    private val credentials: CatalogCredentialsStore = CatalogCredentialsStore(context),
) {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val http = OkHttpClient.Builder()
        .connectionPool(CatalogHttp.pool)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun latest(ifNoneMatch: String? = null, ifModifiedSince: String? = null): SonorusUpdateFetchResult =
        withContext(Dispatchers.IO) {
            val origin = enrolledOrigin()
            val url = origin.resolve("/v2/app-updates/latest") ?: throw IOException("Invalid update endpoint")
            val request = authenticatedRequest(
                Request.Builder().url(url).get().apply {
                    ifNoneMatch?.let { header("If-None-Match", it) }
                    ifModifiedSince?.let { header("If-Modified-Since", it) }
                }.build(),
                origin,
            )
            http.newCall(request).execute().use { response ->
                if (response.code == 304) return@withContext SonorusUpdateFetchResult.NotModified
                if (!response.isSuccessful) throw IOException("Update server rejected request (${response.code})")
                val body = response.body.string()
                if (body.length > MAX_MANIFEST_CHARS) throw IOException("Update manifest is too large")
                val manifest = runCatching { gson.fromJson(body, SonorusUpdateManifest::class.java) }
                    .getOrElse { throw IOException("Invalid update manifest", it) }
                SonorusUpdateValidator.validate(
                    manifest = manifest,
                    expectedChannel = BuildConfig.UPDATE_CHANNEL,
                    expectedApplicationId = BuildConfig.APPLICATION_ID,
                    expectedSigningCertificateSha256 = credentials.applicationSigningCertificateSha256(),
                    publicKeyBase64 = BuildConfig.UPDATE_MANIFEST_PUBLIC_KEY,
                )
                val asset = SonorusUpdateAssetSelector.select(manifest.assets, Build.SUPPORTED_ABIS.toList())
                    ?: throw IOException("No APK is available for this device ABI")
                val downloadUrl = resolveDownloadUrl(origin, manifest.versionCode, asset)
                SonorusUpdateFetchResult.Available(
                    VerifiedSonorusUpdate(
                        manifest,
                        asset,
                        downloadUrl.toString(),
                        response.header("ETag"),
                        response.header("Last-Modified"),
                    ),
                )
            }
        }

    fun newDownloadCall(request: Request, expectedSha256: String): Call {
        val origin = enrolledOrigin()
        requireDownloadUrl(origin, request.url)
        val segments = request.url.pathSegments
        require(segments.size == 5) { "APK URL has an invalid path" }
        val digest = expectedSha256.lowercase(Locale.ROOT)
        require(digest.matches(Regex("^[0-9a-f]{64}$"))) { "APK SHA-256 is invalid" }
        val expectedCosPath = "/${BuildConfig.UPDATE_CHANNEL}/releases/${segments[3]}/$digest"
        val downloadHttp = http.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val outgoing = chain.request()
                if (CatalogEndpoint.sameOrigin(outgoing.url, origin)) {
                    require(outgoing.url == request.url) { "APK redirect changed the update service path" }
                    chain.proceed(outgoing)
                } else {
                    SonorusUpdateCosRedirectPolicy.validate(outgoing.url, expectedCosPath)
                    chain.proceed(SonorusUpdateCosRedirectPolicy.sanitize(outgoing))
                }
            }
            .build()
        val cosCapableRequest = request.newBuilder()
            .header("X-Sonorus-COS-Redirect", "1")
            .build()
        return downloadHttp.newCall(authenticatedRequest(cosCapableRequest, origin))
    }

    private fun authenticatedRequest(request: Request, origin: HttpUrl): Request {
        require(CatalogEndpoint.sameOrigin(request.url, origin)) { "Update request changed server origin" }
        val unsigned = request.newBuilder()
            .header("User-Agent", "Sonorus/${BuildConfig.VERSION_NAME} (Android)")
            .header("X-Sonorus-Application-ID", BuildConfig.APPLICATION_ID)
            .header("X-Sonorus-Update-Channel", BuildConfig.UPDATE_CHANNEL)
            .header("X-Sonorus-Version-Code", BuildConfig.VERSION_CODE.toString())
            .header("X-Sonorus-Signing-Certificate-SHA256", credentials.applicationSigningCertificateSha256())
            .build()
        val auth = CatalogDeviceAuthClient(origin.toString(), credentials).proof(unsigned)
        return unsigned.newBuilder().apply { auth.forEach(::header) }.build()
    }

    private fun enrolledOrigin(): HttpUrl {
        val server = credentials.loadServerUrl() ?: throw IOException("Music library device is not enrolled")
        require(credentials.loadDevice() != null) { "Music library device is not enrolled" }
        return (CatalogEndpoint.normalize(server) + "/").toHttpUrl()
    }

    private fun resolveDownloadUrl(origin: HttpUrl, versionCode: Int, asset: SonorusUpdateAsset): HttpUrl {
        val resolved = origin.resolve(asset.url) ?: throw IOException("Invalid APK URL")
        requireDownloadUrl(origin, resolved)
        val expectedPath = "/v2/app-updates/files/$versionCode/${asset.fileName}"
        require(resolved.encodedPath == expectedPath) { "APK URL does not match signed manifest identity" }
        require(resolved.query == null && resolved.fragment == null) { "APK URL contains unsupported components" }
        return resolved
    }

    private fun requireDownloadUrl(origin: HttpUrl, url: HttpUrl) {
        require(CatalogEndpoint.sameOrigin(url, origin)) { "APK URL changed server origin" }
        require(url.encodedPath.startsWith("/v2/app-updates/files/")) { "APK URL is outside the update service" }
        require(url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty()) { "APK URL contains userinfo" }
    }

    private companion object {
        const val MAX_MANIFEST_CHARS = 1_000_000
    }
}
