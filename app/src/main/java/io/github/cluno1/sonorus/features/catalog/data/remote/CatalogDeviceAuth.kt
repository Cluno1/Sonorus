package io.github.cluno1.sonorus.features.catalog.data.remote

import android.os.Build
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.data.CatalogDeviceCredentials
import io.github.cluno1.sonorus.features.catalog.data.CatalogDeviceKey
import io.github.cluno1.sonorus.features.catalog.data.CatalogSigner
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogIssuedInvite
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class AdminSessionRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
)
internal data class AdminSessionDto(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("expiresIn") val expiresIn: Long,
)
internal data class PasswordAdminSession(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("devices") val devices: AdminDeviceListDto,
)
internal data class InviteRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("replaceExistingDevice") val replaceExistingDevice: Boolean = false,
)
internal data class InviteDto(
    @SerializedName("inviteCode") val inviteCode: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("expiresAt") val expiresAt: String,
)
internal fun InviteDto.toIssuedInvite(): CatalogIssuedInvite = CatalogIssuedInvite(
    inviteCode = inviteCode,
    userId = userId,
    expiresAt = expiresAt,
)
internal data class InviteChallengeRequest(
    @SerializedName("inviteCode") val inviteCode: String,
)
internal data class DeviceNonceRequest(
    @SerializedName("deviceId") val deviceId: String,
)
internal data class NonceDto(
    @SerializedName("nonce") val nonce: String,
    @SerializedName("expiresAt") val expiresAt: String,
)
internal data class EnrollRequest(
    @SerializedName("inviteCode") val inviteCode: String,
    @SerializedName("nonce") val nonce: String,
    @SerializedName("publicKeySpki") val publicKeySpki: String,
    @SerializedName("signature") val signature: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("applicationId") val applicationId: String,
    @SerializedName("signingCertificateSha256") val signingCertificateSha256: String,
)
internal data class RefreshRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("nonce") val nonce: String,
    @SerializedName("signature") val signature: String,
)
internal data class DeviceSessionDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("accessTokenExpiresIn") val accessTokenExpiresIn: Long,
    @SerializedName("sessionExpiresAt") val sessionExpiresAt: String,
)

internal interface CatalogDeviceAuthApi {
    @POST("v2/admin/session")
    suspend fun adminSession(@Body body: AdminSessionRequest): Response<AdminSessionDto>

    @POST("v2/admin/invites")
    suspend fun createInvite(
        @Header("Authorization") authorization: String,
        @Body body: InviteRequest,
    ): Response<InviteDto>

    @GET("v2/admin/devices")
    suspend fun adminDevices(
        @Header("Authorization") authorization: String,
    ): Response<AdminDeviceListDto>

    @POST("v2/admin/devices/{id}/administrator")
    suspend fun grantAdministrator(
        @Header("Authorization") authorization: String,
        @Path("id") deviceId: String,
    ): Response<AdministratorChangeDto>

    @DELETE("v2/admin/devices/{id}/administrator")
    suspend fun revokeAdministrator(
        @Header("Authorization") authorization: String,
        @Path("id") deviceId: String,
    ): Response<AdministratorChangeDto>

    @GET("healthz")
    suspend fun health(): Response<HealthDto>

    @POST("v2/device/challenge")
    suspend fun enrollmentChallenge(@Body body: InviteChallengeRequest): Response<NonceDto>

    @POST("v2/device/enroll")
    suspend fun enroll(@Body body: EnrollRequest): Response<DeviceSessionDto>

    @POST("v2/device/nonce")
    fun nonce(
        @Header("Authorization") authorization: String,
        @Body body: DeviceNonceRequest,
    ): Call<NonceDto>

    @POST("v2/device/session/challenge")
    fun refreshChallenge(
        @Header("X-Rhythm-Session-ID") sessionId: String,
        @Body body: DeviceNonceRequest,
    ): Call<NonceDto>

    @POST("v2/device/session/refresh")
    fun refresh(@Body body: RefreshRequest): Call<DeviceSessionDto>
}

internal object CatalogDeviceCanonical {
    val emptySha256: String = sha256Hex(ByteArray(0))

    fun enrollment(
        nonce: String,
        inviteCode: String,
        keyThumbprint: String,
        applicationId: String,
        signingCertificateSha256: String,
    ): ByteArray = listOf(
        "RHYTHM-ENROLL-V2",
        nonce,
        inviteCode,
        keyThumbprint,
        applicationId,
        signingCertificateSha256.lowercase(),
    ).joinToString("\n").toByteArray()

    fun refresh(deviceId: String, sessionId: String, timestamp: Long, nonce: String): ByteArray =
        "RHYTHM-REFRESH-V1\n$deviceId\n$sessionId\n$timestamp\n$nonce".toByteArray()

    fun request(
        method: String,
        path: String,
        query: String,
        bodySha256: String,
        deviceId: String,
        timestamp: Long,
        nonce: String,
    ): ByteArray = listOf(
        "RHYTHM-DEVICE-V1",
        method.uppercase(),
        path,
        query,
        bodySha256.lowercase(),
        deviceId,
        timestamp.toString(),
        nonce,
    ).joinToString("\n").toByteArray()

    fun publicKeyThumbprint(publicKeySpki: String): String = sha256Hex(
        java.util.Base64.getUrlDecoder().decode(publicKeySpki),
    )

    fun contentSha256(bytes: ByteArray): String = sha256Hex(bytes)

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

internal class CatalogDeviceAuthClient(
    serverUrl: String,
    private val credentials: CatalogCredentialsStore,
    private val signer: CatalogSigner = CatalogDeviceKey,
) {
    private val origin = (CatalogEndpoint.normalize(serverUrl) + "/").toHttpUrl()
    private val api = Retrofit.Builder()
        .baseUrl(origin)
        .client(baseHttpClient())
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()
        .create(CatalogDeviceAuthApi::class.java)

    suspend fun enroll(inviteCode: String): Unit {
        val normalizedInvite = inviteCode.trim()
        require(normalizedInvite.isNotEmpty()) { "邀请码不能为空" }
        val health = api.health()
        if (!health.isSuccessful) throw IOException("服务器健康检查失败（${health.code()}）")
        val challenge = api.enrollmentChallenge(InviteChallengeRequest(normalizedInvite)).bodyOrThrow()
        val publicKey = signer.publicKeySpki()
        val applicationId = BuildConfig.APPLICATION_ID
        val signingCertificateSha256 = credentials.applicationSigningCertificateSha256()
        val signature = signer.sign(
            CatalogDeviceCanonical.enrollment(
                challenge.nonce,
                normalizedInvite,
                CatalogDeviceCanonical.publicKeyThumbprint(publicKey),
                applicationId,
                signingCertificateSha256,
            ),
        )
        val enrolled = api.enroll(
            EnrollRequest(
                inviteCode = normalizedInvite,
                nonce = challenge.nonce,
                publicKeySpki = publicKey,
                signature = signature,
                displayName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android device" },
                applicationId = applicationId,
                signingCertificateSha256 = signingCertificateSha256,
            ),
        ).bodyOrThrow()
        credentials.saveDevice(enrolled.toCredentials(origin.toString().trimEnd('/')))
    }

    suspend fun issueInvite(
        username: String,
        password: String,
        userId: String,
        displayName: String?,
        replaceExistingDevice: Boolean,
    ): CatalogIssuedInvite {
        val session = api.adminSession(
            AdminSessionRequest(username.trim(), password),
        ).adminBodyOrThrow()
        return api.createInvite(
            "Bearer ${session.accessToken}",
            InviteRequest(
                userId.trim(),
                displayName?.trim()?.takeIf { it.isNotEmpty() },
                replaceExistingDevice,
            ),
        ).adminBodyOrThrow().toIssuedInvite()
    }

    suspend fun authenticateAdministrator(
        username: String,
        password: String,
    ): PasswordAdminSession {
        val session = api.adminSession(
            AdminSessionRequest(username.trim(), password),
        ).adminBodyOrThrow()
        val authorization = "Bearer ${session.accessToken}"
        return PasswordAdminSession(
            accessToken = session.accessToken,
            devices = api.adminDevices(authorization).adminBodyOrThrow(),
        )
    }

    suspend fun setAdministrator(
        accessToken: String,
        deviceId: String,
        enabled: Boolean,
    ): AdminDeviceListDto {
        val authorization = "Bearer $accessToken"
        val response = if (enabled) {
            api.grantAdministrator(authorization, deviceId)
        } else {
            api.revokeAdministrator(authorization, deviceId)
        }
        response.adminBodyOrThrow()
        return api.adminDevices(authorization).adminBodyOrThrow()
    }

    @Synchronized
    fun proof(request: Request, precomputedContentSha256: String? = null): Map<String, String> {
        val isRead = request.method == "GET" || request.method == "HEAD"
        val isLyricWrite = request.method == "PUT" &&
            request.url.encodedPath.matches(Regex("^/v2/renditions/[^/]+/lyrics/[^/]+$"))
        val isChorusWrite = request.url.encodedPath.startsWith("/v2/chorus-") &&
            request.method in setOf("POST", "PUT", "PATCH", "DELETE")
        val isOwnedImageWrite = (
            request.url.encodedPath.startsWith("/v2/labs/image-upload") ||
                request.url.encodedPath.startsWith("/v2/labs/images")
            ) && request.method in setOf("POST", "PATCH", "DELETE")
        val isAdminWrite = request.url.encodedPath.startsWith("/v2/admin/") &&
            request.method in setOf("POST", "PATCH", "DELETE")
        require(isRead || isLyricWrite || isChorusWrite || isOwnedImageWrite || isAdminWrite) {
            "public Catalog only signs reads and narrow owned writes"
        }
        if (credentials.isReenrollmentRequired()) throw CatalogFailure.InvalidCredentials()
        try {
            val current = accessCredentials()
            val nonce = api.nonce(
                "Device ${current.accessToken}",
                DeviceNonceRequest(current.deviceId),
            ).execute().bodyOrThrow().nonce
            val timestamp = Instant.now().epochSecond
            val contentSha256 = precomputedContentSha256?.lowercase()?.also {
                require(it.matches(Regex("^[0-9a-f]{64}$"))) {
                    "precomputed request body SHA-256 is invalid"
                }
            } ?: request.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    CatalogDeviceCanonical.contentSha256(buffer.readByteArray())
                } ?: CatalogDeviceCanonical.emptySha256
            val canonical = CatalogDeviceCanonical.request(
                request.method,
                request.url.encodedPath,
                request.url.encodedQuery.orEmpty(),
                contentSha256,
                current.deviceId,
                timestamp,
                nonce,
            )
            return mapOf(
                "Authorization" to "Device ${current.accessToken}",
                "X-Rhythm-Device-ID" to current.deviceId,
                "X-Rhythm-Timestamp" to timestamp.toString(),
                "X-Rhythm-Nonce" to nonce,
                "X-Rhythm-Content-SHA256" to contentSha256,
                "X-Rhythm-Signature" to signer.sign(canonical),
            )
        } catch (error: CatalogFailure.InvalidCredentials) {
            // Keep the Keystore key and cached media, but stop all use of this invalid session.
            // The synchronized boundary makes concurrent callers observe one durable result.
            credentials.markReenrollmentRequired()
            throw error
        }
    }

    fun proofForGet(url: String): Map<String, String> = proof(
        Request.Builder().url(url).get().build(),
    )

    private fun accessCredentials(): CatalogDeviceCredentials {
        var current = credentials.loadDevice() ?: throw IOException("设备尚未登记")
        if (current.accessTokenExpiresAtEpochSeconds > Instant.now().epochSecond + 30) return current
        val challenge = api.refreshChallenge(
            current.sessionId,
            DeviceNonceRequest(current.deviceId),
        ).execute().bodyOrThrow()
        val timestamp = Instant.now().epochSecond
        val refreshed = api.refresh(
            RefreshRequest(
                current.deviceId,
                current.sessionId,
                timestamp,
                challenge.nonce,
                signer.sign(
                    CatalogDeviceCanonical.refresh(
                        current.deviceId,
                        current.sessionId,
                        timestamp,
                        challenge.nonce,
                    ),
                ),
            ),
        ).execute().bodyOrThrow()
        current = refreshed.toCredentials(current.serverUrl)
        credentials.saveDevice(current)
        return current
    }

    private fun DeviceSessionDto.toCredentials(serverUrl: String) = CatalogDeviceCredentials(
        serverUrl = serverUrl,
        userId = userId,
        deviceId = deviceId,
        sessionId = sessionId,
        accessToken = accessToken,
        accessTokenExpiresAtEpochSeconds = Instant.now().epochSecond + accessTokenExpiresIn,
        sessionExpiresAt = sessionExpiresAt,
    )

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) {
            throw catalogHttpFailure(code())
        }
        return body() ?: throw IOException("服务器返回空响应")
    }

    private fun <T> Response<T>.adminBodyOrThrow(): T {
        if (code() == 401) throw CatalogFailure.AdminInvalidCredentials()
        return bodyOrThrow()
    }

    private fun baseHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

internal fun catalogHttpFailure(statusCode: Int): IOException = when (statusCode) {
    401 -> CatalogFailure.InvalidCredentials()
    409 -> CatalogFailure.InvalidData("该用户已有登记设备，请生成‘替换已有设备’邀请码")
    422 -> CatalogFailure.InvalidData("客户端与服务器协议不兼容（422）")
    429 -> CatalogFailure.InvalidData("请求过于频繁，请稍后再试")
    else -> IOException("服务器拒绝请求（$statusCode）")
}
