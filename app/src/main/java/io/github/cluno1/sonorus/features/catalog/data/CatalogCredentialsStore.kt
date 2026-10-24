package io.github.cluno1.sonorus.features.catalog.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CatalogDeviceCredentials(
    val serverUrl: String,
    val userId: String,
    val deviceId: String,
    val sessionId: String,
    val accessToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
    val sessionExpiresAt: String,
)

/**
 * Stores the short-lived access token encrypted under a non-exportable Android Keystore key.
 *
 * Invariant: the token ciphertext, its IV, its expiry and the device identity are one snapshot.
 * Every write goes into a single SharedPreferences commit and every multi-key read or write holds
 * the process-wide [lock], shared by all instances of this class.
 *
 * Why: callers (update check, playback resolver, repository, image uploads) each build their own
 * store. Issue 115: with two separate commits, one reader got the old expired token together with
 * the expiry just written by another refresher, skipped the refresh, received 401 and persisted
 * "re-enrollment required" although the server-side device and session were valid.
 *
 * Silent failure if broken: a mixed token/expiry pair produces a sporadic 401 on app start; a mixed
 * ciphertext/IV pair fails decryption and [loadToken] wipes the whole registration and device key.
 *
 * Rejected: `@Synchronized` on callers only locks one instance and did not prevent issue 115.
 */
class CatalogCredentialsStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /** Kept so an existing WireGuard/private-server configuration works after the upgrade. */
    fun save(serverUrl: String, token: String) {
        val normalizedUrl = CatalogEndpoint.normalize(serverUrl)
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty())
        val encrypted = encryptToken(normalizedToken)
        synchronized(lock) {
            preferences.edit(commit = true) {
                putEncryptedToken(encrypted)
                putString(KEY_SERVER_URL, normalizedUrl)
                remove(KEY_USER_ID)
                remove(KEY_DEVICE_ID)
                remove(KEY_SESSION_ID)
                remove(KEY_ACCESS_EXPIRES_AT)
                remove(KEY_SESSION_EXPIRES_AT)
                remove(KEY_REENROLLMENT_REQUIRED)
            }
        }
    }

    fun saveDevice(credentials: CatalogDeviceCredentials) {
        val encrypted = encryptToken(credentials.accessToken)
        synchronized(lock) {
            preferences.edit(commit = true) {
                putEncryptedToken(encrypted)
                putString(KEY_SERVER_URL, CatalogEndpoint.normalize(credentials.serverUrl))
                putString(KEY_USER_ID, credentials.userId)
                putString(KEY_DEVICE_ID, credentials.deviceId)
                putString(KEY_SESSION_ID, credentials.sessionId)
                putLong(KEY_ACCESS_EXPIRES_AT, credentials.accessTokenExpiresAtEpochSeconds)
                putString(KEY_SESSION_EXPIRES_AT, credentials.sessionExpiresAt)
                putBoolean(KEY_REENROLLMENT_REQUIRED, false)
            }
        }
    }

    fun markReenrollmentRequired() {
        synchronized(lock) {
            preferences.edit(commit = true) {
                putBoolean(KEY_REENROLLMENT_REQUIRED, true)
                remove(KEY_ACCESS_EXPIRES_AT)
            }
        }
    }

    fun isReenrollmentRequired(): Boolean =
        preferences.getBoolean(KEY_REENROLLMENT_REQUIRED, false)

    fun updateAccessToken(token: String, expiresAtEpochSeconds: Long) {
        check(loadDeviceId() != null) { "device credentials are not configured" }
        val encrypted = encryptToken(token)
        synchronized(lock) {
            preferences.edit(commit = true) {
                putEncryptedToken(encrypted)
                putLong(KEY_ACCESS_EXPIRES_AT, expiresAtEpochSeconds)
            }
        }
    }

    fun loadServerUrl(): String? = preferences.getString(KEY_SERVER_URL, null)
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotEmpty() }

    fun loadDeviceId(): String? = preferences.getString(KEY_DEVICE_ID, null)
        ?.takeIf { it.isNotBlank() }

    fun loadDevice(): CatalogDeviceCredentials? = synchronized(lock) { loadDeviceSnapshot() }

    private fun loadDeviceSnapshot(): CatalogDeviceCredentials? {
        val serverUrl = loadServerUrl() ?: return null
        val token = loadTokenSnapshot() ?: return null
        return CatalogDeviceCredentials(
            serverUrl = serverUrl,
            userId = preferences.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
                ?: return null,
            deviceId = loadDeviceId() ?: return null,
            sessionId = preferences.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() }
                ?: return null,
            accessToken = token,
            accessTokenExpiresAtEpochSeconds = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            sessionExpiresAt = preferences.getString(KEY_SESSION_EXPIRES_AT, null)
                ?.takeIf { it.isNotBlank() }
                ?: return null,
        )
    }

    fun loadToken(): String? = synchronized(lock) { loadTokenSnapshot() }

    private fun loadTokenSnapshot(): String? {
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateEncryptionKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
                )
            }
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        }.getOrElse {
            clear()
            null
        }
    }

    /** SHA-256 of the sole current APK signer, used to bind server registration to this app line. */
    @Suppress("DEPRECATION")
    fun applicationSigningCertificateSha256(): String {
        val packageManager = applicationContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageInfo(applicationContext.packageName, flags)
        val signers = currentSigners(info)
        require(signers.size == 1) { "Sonorus requires exactly one current APK signer" }
        return MessageDigest.getInstance("SHA-256").digest(signers.single())
            .joinToString("") { "%02x".format(it) }
    }

    fun clear() {
        synchronized(lock) {
            preferences.edit(commit = true) { clear() }
            CatalogDeviceKey.delete()
        }
    }

    private class EncryptedToken(val ciphertext: String, val iv: String)

    private fun encryptToken(token: String): EncryptedToken {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return EncryptedToken(
            ciphertext = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    private fun SharedPreferences.Editor.putEncryptedToken(token: EncryptedToken) {
        putString(KEY_CIPHERTEXT, token.ciphertext)
        putString(KEY_IV, token.iv)
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    ENCRYPTION_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(packageInfo: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        } else {
            packageInfo.signatures.orEmpty().map { it.toByteArray() }
        }

    private companion object {
        /** Process-wide: every store instance reads and writes the same preferences file. */
        val lock = Any()
        const val PREFERENCES_NAME = "rhythm_catalog_credentials"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_CIPHERTEXT = "token_ciphertext"
        const val KEY_IV = "token_iv"
        const val KEY_USER_ID = "device_user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SESSION_ID = "device_session_id"
        const val KEY_ACCESS_EXPIRES_AT = "access_token_expires_at"
        const val KEY_SESSION_EXPIRES_AT = "device_session_expires_at"
        const val KEY_REENROLLMENT_REQUIRED = "reenrollment_required"
        const val ENCRYPTION_KEY_ALIAS = "rhythm_catalog_bearer_token_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
