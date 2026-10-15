/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.shared.presentation.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.activities.MainActivity
import io.github.cluno1.sonorus.network.SonorusUpdateClient
import io.github.cluno1.sonorus.network.SonorusUpdateFetchResult
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * App version data model
 */
data class AppVersion(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val whatsNew: List<String>,
    val knownIssues: List<String>,
    val downloadUrl: String,
    val apkAssetName: String = "",
    val apkSize: Long = 0,
    val releaseNotes: String = "",
    val isPreRelease: Boolean = false,
    val buildNumber: Int = 0,
    val apkSha256: String = "",
    val minimumAndroidSdk: Int = 1,
)

/**
 * Download state for tracking download progress and resumption
 */
data class DownloadState(
    val fileName: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val etag: String?,
    val lastModified: String?,
    val resumePosition: Long,
    val checksum: String? = null, // SHA-256 checksum if available
    val retryCount: Int = 0, // Track retry attempts
)

private data class PendingMismatchedDownload(
    val filePath: String,
    val downloadUrl: String,
    val fileName: String,
    val retryAttempt: Int,
)

/** Local epoch day on which the app-open update prompt was last shown. */
private const val KEY_PROMPT_LAST_SHOWN_DAY = "last_shown_epoch_day"

/**
 * ViewModel for handling app updates
 */
class AppUpdaterViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val tag = "AppUpdaterViewModel"
    private val updateDownloadChannelId = "update_download_progress"
    private val updateDownloadNotificationId = 1401
    private val updateDownloadCompletionAutoDismissMs = 7000L

    // Update check interval (6 hours)
    private val updateCheckInterval = TimeUnit.HOURS.toMillis(6)

    private val updateClient by lazy { SonorusUpdateClient(application.applicationContext) }

    // Last update check timestamp
    private var lastUpdateCheck = 0L

    // AppSettings instance
    private val _appSettings = AppSettings.getInstance(application.applicationContext)
    val appSettings: AppSettings = _appSettings // Expose AppSettings publicly

    // SharedPreferences for download state persistence
    private val downloadPrefs: SharedPreferences = application.getSharedPreferences("app_updater_downloads", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Active download state
    private var activeDownload: DownloadState? = null
    private var activeCall: Call? = null
    private var lastNotifiedProgressPercent: Int = -1
    private var completionNotificationDismissJob: kotlinx.coroutines.Job? = null

    // Mutex to prevent concurrent downloads
    private val downloadMutex = Mutex()

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Maximum retry attempts for downloads
    private val maxRetryAttempts = 2

    // Current app version info
    private val _currentVersion =
        MutableStateFlow(
            AppVersion(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                releaseDate = BuildConfig.RELEASE_DATE,
                whatsNew = emptyList(),
                knownIssues = emptyList(),
                downloadUrl = "",
                isPreRelease = BuildConfig.VERSION_NAME.contains("Beta", ignoreCase = true),
                buildNumber = extractBuildNumber(BuildConfig.VERSION_NAME),
            ),
        )
    val currentVersion: StateFlow<AppVersion> = _currentVersion.asStateFlow()

    // Latest version info
    private val _latestVersion = MutableStateFlow<AppVersion?>(null)
    val latestVersion: StateFlow<AppVersion?> = _latestVersion.asStateFlow()

    // Update check state
    private val _isCheckingForUpdates = MutableStateFlow(false)
    val isCheckingForUpdates: StateFlow<Boolean> = _isCheckingForUpdates.asStateFlow()

    // Update available state
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    // Force update trigger for testing
    private val _forceUpdateTrigger = MutableStateFlow(0L)
    val forceUpdateTrigger: StateFlow<Long> = _forceUpdateTrigger.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _canProceedWithMismatchedDownload = MutableStateFlow(false)
    val canProceedWithMismatchedDownload: StateFlow<Boolean> = _canProceedWithMismatchedDownload.asStateFlow()

    private val _isVersionCodeDowngrade = MutableStateFlow(false)
    val isVersionCodeDowngrade: StateFlow<Boolean> = _isVersionCodeDowngrade.asStateFlow()

    // Download state - true when actively downloading
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    // Download progress (0-100)
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    // Downloaded file
    private val _downloadedFile = MutableStateFlow<File?>(null)
    val downloadedFile: StateFlow<File?> = _downloadedFile.asStateFlow()

    // Download state for tracking download progress and resumption
    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState.asStateFlow()
    private var pendingMismatchedDownload: PendingMismatchedDownload? = null

    // Serializes manifest fetches so the app-open check and periodic checks share one request.
    private val checkMutex = Mutex()

    // App-open update prompt (shown at most once per local day)
    private val startupPromptPrefs: SharedPreferences =
        application.getSharedPreferences("app_update_prompt", Context.MODE_PRIVATE)
    private val _startupPrompt = MutableStateFlow<AppVersion?>(null)
    val startupPrompt: StateFlow<AppVersion?> = _startupPrompt.asStateFlow()
    private val _startupInstallPending = MutableStateFlow(false)
    val startupInstallPending: StateFlow<Boolean> = _startupInstallPending.asStateFlow()
    private val _startupUpgradeError = MutableStateFlow<String?>(null)
    val startupUpgradeError: StateFlow<String?> = _startupUpgradeError.asStateFlow()
    private var startupUpgradeJob: kotlinx.coroutines.Job? = null

    init {
        if (!ProductCapabilities.inAppUpdates) {
            notificationManager.cancel(updateDownloadNotificationId)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notificationManager.deleteNotificationChannel(updateDownloadChannelId)
            }
        } else {
            ensureDownloadNotificationChannel()

            // Load any persisted download state
            loadDownloadState()

            // Start periodic update checks
            startPeriodicUpdateChecks()
        }
    }

    /**
     * Load download state from SharedPreferences
     */
    private fun loadDownloadState() {
        try {
            val downloadStateJson = downloadPrefs.getString("active_download", null)
            val downloadProgress = downloadPrefs.getFloat("download_progress", 0f)
            val isDownloading = downloadPrefs.getBoolean("is_downloading", false)
            val downloadedFilePath = downloadPrefs.getString("downloaded_file", null)

            if (downloadStateJson != null) {
                activeDownload = gson.fromJson(downloadStateJson, DownloadState::class.java)
                // Validate the download state
                if (validateDownloadState(activeDownload)) {
                    _downloadState.value = activeDownload
                    Log.d(tag, "Loaded download state: ${activeDownload?.fileName}")
                } else {
                    Log.w(tag, "Download state validation failed, clearing")
                    clearDownloadState()
                    return
                }
            }

            if (downloadProgress > 0f && downloadProgress <= 100f) {
                _downloadProgress.value = downloadProgress
                Log.d(tag, "Loaded download progress: $downloadProgress%")
            }

            if (downloadedFilePath != null) {
                val file = File(downloadedFilePath)
                if (file.exists() && file.length() > 0) {
                    // Verify file integrity if checksum available
                    val isValid =
                        activeDownload?.checksum?.takeIf { it.isNotBlank() }?.let { checksum ->
                            verifyFileChecksum(file, checksum)
                        } ?: false

                    if (isValid) {
                        _downloadedFile.value = file
                        _downloadProgress.value = 100f
                        Log.d(tag, "Found completed download: ${file.absolutePath}")
                    } else {
                        Log.w(tag, "Downloaded file checksum mismatch, deleting")
                        file.delete()
                        clearDownloadState()
                    }
                } else {
                    Log.w(tag, "Downloaded file not found or empty, clearing state")
                    clearDownloadState()
                }
            }

            // Don't restore isDownloading state - always start fresh to avoid stuck downloads
            _isDownloading.value = false
        } catch (e: Exception) {
            Log.e(tag, "Failed to load download state", e)
            clearDownloadState()
        }
    }

    /**
     * Validate download state data
     */
    private fun validateDownloadState(state: DownloadState?): Boolean {
        if (state == null) return false
        return state.fileName.matches(Regex("^[A-Za-z0-9._-]+\\.apk$")) &&
            state.url.isNotBlank() &&
            state.totalBytes > 0 &&
            state.downloadedBytes >= 0 &&
            state.downloadedBytes <= state.totalBytes &&
            state.checksum?.matches(Regex("^[0-9a-fA-F]{64}$")) == true &&
            state.retryCount >= 0 &&
            state.retryCount < maxRetryAttempts
    }

    /**
     * Save download state to SharedPreferences
     */
    private fun saveDownloadState() {
        try {
            downloadPrefs.edit {
                if (activeDownload != null) {
                    putString("active_download", gson.toJson(activeDownload))
                } else {
                    remove("active_download")
                }

                putFloat("download_progress", _downloadProgress.value)
                putBoolean("is_downloading", _isDownloading.value)

                _downloadedFile.value?.let { file ->
                    putString("downloaded_file", file.absolutePath)
                } ?: remove("downloaded_file")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to save download state", e)
        }
    }

    /**
     * Clear persisted download state
     */
    private fun clearDownloadState() {
        downloadPrefs.edit { clear() }
        activeDownload = null
        pendingMismatchedDownload = null
        _canProceedWithMismatchedDownload.value = false
        _downloadState.value = null
    }

    /** Check the device-authenticated first-party update service. */
    fun checkForUpdates(force: Boolean = false) {
        if (!ProductCapabilities.inAppUpdates) return
        viewModelScope.launch {
            val updatesEnabled = _appSettings.updatesEnabled.first()
            val autoCheckEnabled = _appSettings.autoCheckForUpdates.first()
            // Master check: if updates are completely disabled, don't check at all
            if (!updatesEnabled) {
                Log.d(tag, "Skipping update check - updates are completely disabled.")
                _isCheckingForUpdates.value = false
                return@launch
            }

            // Auto-check setting: only applies to automatic checks, not forced checks
            if (!force && !autoCheckEnabled) {
                Log.d(tag, "Skipping update check - auto-check is disabled and not forced.")
                _isCheckingForUpdates.value = false
                return@launch
            }

            checkMutex.withLock {
                // Skip check if within update interval unless forced. Evaluated under the lock so
                // a concurrent app-open check that just finished is reused instead of repeated.
                if (!force && System.currentTimeMillis() - lastUpdateCheck < updateCheckInterval) {
                    Log.d(tag, "Skipping update check - within interval")
                    return@launch
                }

                _isCheckingForUpdates.value = true
                _error.value = null

                try {
                    fetchLatestCandidate()
                } catch (e: Exception) {
                    Log.e(tag, "Error checking for updates", e)
                    _error.value = "Update check failed: ${e.message ?: "Unknown error"}"
                } finally {
                    _isCheckingForUpdates.value = false
                    lastUpdateCheck = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * Fetches the signed manifest and publishes it through [processCandidate].
     * Callers must hold [checkMutex]. Throws on network, enrollment or validation failures.
     */
    private suspend fun fetchLatestCandidate() {
        _latestVersion.value = null // Clear any previous version data
        when (val result = updateClient.latest()) {
            SonorusUpdateFetchResult.NotModified -> Unit
            is SonorusUpdateFetchResult.Available -> {
                val update = result.update
                val manifest = update.manifest
                processCandidate(
                    AppVersion(
                        versionName = manifest.versionName,
                        versionCode = manifest.versionCode,
                        releaseDate = manifest.publishedAt.substringBefore('T'),
                        whatsNew = manifest.releaseNotes,
                        knownIssues = emptyList(),
                        downloadUrl = update.downloadUrl,
                        apkAssetName = update.asset.fileName,
                        apkSize = update.asset.sizeBytes,
                        releaseNotes = manifest.releaseNotes.joinToString("\n"),
                        isPreRelease = manifest.channel == "debug",
                        buildNumber = manifest.versionCode,
                        apkSha256 = update.asset.sha256,
                        minimumAndroidSdk = manifest.minimumAndroidSdk,
                    ),
                )
            }
        }
    }

    /**
     * App-open update check. Runs on cold start and whenever the app returns to the foreground,
     * and publishes [startupPrompt] at most once per local calendar day. Failures (not enrolled,
     * offline, invalid manifest) are logged only: an app-open check must never surface errors.
     */
    fun checkForUpdatesOnAppOpen() {
        if (!ProductCapabilities.inAppUpdates) return
        viewModelScope.launch {
            if (!_appSettings.updatesEnabled.first() || !_appSettings.autoCheckForUpdates.first()) return@launch
            if (_startupPrompt.value != null) return@launch
            val today = java.time.LocalDate.now().toEpochDay()
            val lastShownDay = startupPromptPrefs.getLong(KEY_PROMPT_LAST_SHOWN_DAY, StartupUpdatePromptPolicy.NEVER_SHOWN)
            if (!StartupUpdatePromptPolicy.canPromptToday(lastShownDay, today)) return@launch

            val candidate = checkMutex.withLock {
                val sinceLastCheck = System.currentTimeMillis() - lastUpdateCheck
                if (lastUpdateCheck == 0L || sinceLastCheck >= StartupUpdatePromptPolicy.RESUME_RECHECK_INTERVAL_MS) {
                    _isCheckingForUpdates.value = true
                    try {
                        fetchLatestCandidate()
                    } catch (e: Exception) {
                        Log.w(tag, "App-open update check failed silently", e)
                        return@launch
                    } finally {
                        _isCheckingForUpdates.value = false
                        lastUpdateCheck = System.currentTimeMillis()
                    }
                }
                _latestVersion.value
            } ?: return@launch

            val currentDay = java.time.LocalDate.now().toEpochDay()
            val shouldPrompt = StartupUpdatePromptPolicy.shouldPrompt(
                lastShownEpochDay = startupPromptPrefs.getLong(KEY_PROMPT_LAST_SHOWN_DAY, StartupUpdatePromptPolicy.NEVER_SHOWN),
                todayEpochDay = currentDay,
                candidateVersionCode = candidate.versionCode,
                installedVersionCode = BuildConfig.VERSION_CODE,
                candidateMinimumSdk = candidate.minimumAndroidSdk,
                deviceSdk = Build.VERSION.SDK_INT,
            )
            if (!shouldPrompt || _startupPrompt.value != null) return@launch
            // Record the day before showing so a crash or kill still counts as today's prompt.
            startupPromptPrefs.edit { putLong(KEY_PROMPT_LAST_SHOWN_DAY, currentDay) }
            _startupUpgradeError.value = null
            _startupPrompt.value = candidate
        }
    }

    /** User chose "skip": hide the prompt; it may appear again tomorrow. */
    fun skipStartupPrompt() {
        _startupPrompt.value = null
        _startupInstallPending.value = false
        _startupUpgradeError.value = null
    }

    /**
     * One-tap upgrade from the app-open prompt: download (or reuse a verified download), then
     * hand the APK to the system installer automatically.
     */
    fun startStartupUpgrade() {
        val target = _startupPrompt.value ?: return
        _startupUpgradeError.value = null
        _startupInstallPending.value = true
        val alreadyDownloaded = _downloadedFile.value?.takeIf { it.exists() }
        if (alreadyDownloaded != null) {
            installForStartupPrompt()
            return
        }
        if (_latestVersion.value?.versionCode != target.versionCode) {
            _latestVersion.value = target
        }
        _error.value = null
        downloadUpdate()
        startupUpgradeJob?.cancel()
        startupUpgradeJob = viewModelScope.launch {
            // Wait for either a verified APK or a terminal download error.
            kotlinx.coroutines.flow.combine(_downloadedFile, _isDownloading, _error) { file, downloading, error ->
                Triple(file, downloading, error)
            }.first { (file, downloading, error) -> file != null || (!downloading && error != null) }
            if (!_startupInstallPending.value) return@launch
            if (_downloadedFile.value != null) {
                installForStartupPrompt()
            } else {
                _startupInstallPending.value = false
                _startupUpgradeError.value = _error.value
            }
        }
    }

    /** Cancel an in-flight one-tap upgrade and close the prompt. */
    fun cancelStartupUpgrade() {
        startupUpgradeJob?.cancel()
        if (_isDownloading.value) cancelDownload()
        skipStartupPrompt()
    }

    /**
     * Called when the app returns to the foreground. If the user was sent to the
     * "install unknown apps" settings page, continue the pending install automatically.
     */
    fun onAppForegroundForStartupUpgrade() {
        if (!_startupInstallPending.value || _downloadedFile.value == null) return
        if (getApplication<Application>().packageManager.canRequestPackageInstalls()) {
            installForStartupPrompt()
        }
    }

    private fun installForStartupPrompt() {
        installDownloadedApk()
        if (_downloadedFile.value == null) {
            // The system installer was launched successfully.
            _startupInstallPending.value = false
            _startupPrompt.value = null
        } else if (getApplication<Application>().packageManager.canRequestPackageInstalls()) {
            // Permission was granted but the install still failed: report it in the prompt.
            _startupInstallPending.value = false
            _startupUpgradeError.value = _error.value
        }
        // Otherwise the unknown-sources settings page was opened; keep pending until resume.
    }

    private fun processCandidate(candidate: AppVersion) {
        _latestVersion.value = candidate
        val isNewer = candidate.versionCode > BuildConfig.VERSION_CODE &&
            candidate.minimumAndroidSdk <= Build.VERSION.SDK_INT
        val hasVersionCodeDowngrade = candidate.versionCode <= BuildConfig.VERSION_CODE
        _isVersionCodeDowngrade.value = hasVersionCodeDowngrade

        Log.d(
            tag,
            "Version comparison: current=${_currentVersion.value.versionName} (code=${BuildConfig.VERSION_CODE}) vs latest=${candidate.versionName} (code=${candidate.versionCode}) -> isNewer=$isNewer, isDowngrade=$hasVersionCodeDowngrade"
        )
        _updateAvailable.value = isNewer
    }

    private fun extractBuildNumber(
        versionString: String,
        versionParts: List<String>? = null,
    ): Int {
        val cleaned = versionString.trim().removePrefix("v").removePrefix("V")
        val buildRegex = Regex("(?:b|build)-(\\d+)", RegexOption.IGNORE_CASE)
        val explicitBuildNumber =
            buildRegex
                .find(cleaned)
                ?.groupValues
                ?.get(1)
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
        if (explicitBuildNumber != null) {
            return explicitBuildNumber
        }

        val parts =
            versionParts ?: cleaned
                .split(" ")[0]
                .split("-")[0]
                .split("_")[0]
                .split(".")
        return parts.getOrNull(3)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    /**
     * Clear any error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Force update available state for testing/debugging purposes
     * This is useful for testing update UI without waiting for actual updates
     */
    fun forceUpdateAvailable(available: Boolean) {
        _updateAvailable.value = available
        _forceUpdateTrigger.value = System.currentTimeMillis() // Trigger change
        if (available) {
            // Create a mock latest version for testing
            _latestVersion.value =
                AppVersion(
                    versionName = "2.0.0",
                    versionCode = 200,
                    releaseDate = System.currentTimeMillis().toString(),
                    whatsNew =
                        listOf(
                            "New update system with bottom sheet",
                            "Improved UI and performance",
                            "Bug fixes and optimizations",
                        ),
                    knownIssues = emptyList(),
                    downloadUrl = "",
                    apkAssetName = "Sonorus-release.apk",
                    apkSize = 0,
                    releaseNotes = "Test update",
                    isPreRelease = false,
                    buildNumber = 200,
                )
        } else {
            _latestVersion.value = null
        }
    }

    /**
     * Calculate readable file size
     */
    fun getReadableFileSize(size: Long): String {
        if (size <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()

        return String.format(
            Locale.ROOT,
            "%.1f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups],
        )
    }

    /**
     * Download the update by opening the browser to the download URL or starting in-app download
     */
    fun downloadUpdate() {
        viewModelScope.launch {
            val updatesEnabled = _appSettings.updatesEnabled.first()

            if (!updatesEnabled) {
                _error.value = "Updates are disabled in settings"
                return@launch
            }

            val latestVersion =
                _latestVersion.value ?: run {
                    _error.value = "No update information available"
                    return@launch
                }
            val downloadUrl = latestVersion.downloadUrl

            if (downloadUrl.isBlank()) {
                _error.value = "No download URL available"
                return@launch
            }

            // Clear any previous errors
            _error.value = null

            if (latestVersion.apkAssetName.isEmpty()) {
                _error.value = "The signed update manifest contains no compatible APK."
                return@launch
            }

            // Check if we have an active download
            if (_isDownloading.value) {
                Log.d(tag, "Download already in progress")
                return@launch
            }

            // Start or resume download
            if (latestVersion.minimumAndroidSdk > Build.VERSION.SDK_INT) {
                _error.value = "This update requires Android API ${latestVersion.minimumAndroidSdk} or newer."
                return@launch
            }
            downloadApkInApp(
                downloadUrl,
                latestVersion.apkAssetName,
                expectedSize = latestVersion.apkSize,
                expectedChecksum = latestVersion.apkSha256,
            )
        }
    }

    /**
     * Download an APK file in-app with progress tracking and resume support
     * @param expectedSize The signed manifest's exact APK size.
     */
    private fun downloadApkInApp(
        downloadUrl: String,
        fileName: String,
        expectedSize: Long = 0,
        expectedChecksum: String = "",
        retryAttempt: Int = 0,
    ) {
        // Use mutex to prevent concurrent downloads
        viewModelScope.launch {
            if (!downloadMutex.tryLock()) {
                Log.w(tag, "Download already in progress")
                return@launch
            }

            try {
                downloadApkInAppInternal(downloadUrl, fileName, expectedSize, expectedChecksum, retryAttempt)
            } finally {
                downloadMutex.unlock()
            }
        }
    }

    /**
     * Internal download implementation with mutex protection
     * @param expectedSize The signed manifest's exact APK size.
     */
    private fun downloadApkInAppInternal(
        downloadUrl: String,
        fileName: String,
        expectedSize: Long = 0,
        expectedChecksum: String = "",
        retryAttempt: Int = 0,
    ) {
        if (_isDownloading.value) {
            return // Already downloading
        }
        if (
            expectedSize <= 0L ||
            !expectedChecksum.matches(Regex("^[0-9a-fA-F]{64}$")) ||
            !fileName.matches(Regex("^[A-Za-z0-9._-]+\\.apk$"))
        ) {
            _error.value = "The signed update manifest does not contain valid APK integrity metadata."
            return
        }

        // Cancel any stale notification from a previous session
        cancelDownloadNotification()

        _downloadProgress.value = 0f
        _error.value = null

        val shouldResumeDownload =
            activeDownload != null &&
                activeDownload?.url == downloadUrl

        if (!shouldResumeDownload) {
            // Starting fresh download - clear previous state and delete partial files
            _downloadedFile.value = null

            // Delete any existing partial download file
            val context = getApplication<Application>()
            val downloadDir =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }
            downloadDir?.let { dir ->
                val existingFile = File(dir, fileName)
                if (existingFile.exists()) {
                    Log.d(tag, "Deleting partial download file: ${existingFile.absolutePath}")
                    existingFile.delete()
                }
            }

            activeDownload = null
        }

        _isDownloading.value = true
        lastNotifiedProgressPercent = -1
        showDownloadProgressNotification(0)

        // Use viewModelScope with IO dispatcher for background work
        // The download continues in background even if user navigates away
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use app-specific external storage instead of public Downloads
                val context = getApplication<Application>()
                val downloadDir =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // For Android 10 and above, use app-specific directory
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    } else {
                        // For older versions, check permission first
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModelScope.launch {
                                _error.value = "Storage permission required to download updates"
                                _isDownloading.value = false
                            }
                            return@launch
                        }
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    }

                if (downloadDir == null) {
                    viewModelScope.launch {
                        _error.value = "Could not access storage"
                        _isDownloading.value = false
                    }
                    return@launch
                }

                // Ensure download directory exists
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                // Create or get existing file
                val file = File(downloadDir, fileName)
                var existingLength = 0L

                // Validate existing file before resume
                if (file.exists() && activeDownload != null && shouldResumeDownload) {
                    existingLength = file.length()

                    // Validate file size matches expected resume position
                    if (existingLength != activeDownload?.resumePosition) {
                        Log.w(tag, "File size mismatch (expected: ${activeDownload?.resumePosition}, actual: $existingLength), deleting")
                        file.delete()
                        existingLength = 0L
                        activeDownload = null
                    }
                } else if (file.exists()) {
                    // File exists but we're not resuming - delete it
                    Log.d(tag, "Deleting existing file for fresh download")
                    file.delete()
                }

                // Create request with resume support
                val requestBuilder =
                    Request
                        .Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "Sonorus/${BuildConfig.VERSION_NAME} (Android)")

                // Add range header if resuming
                if (existingLength > 0 && activeDownload != null) {
                    Log.d(tag, "Resuming download from byte $existingLength")
                    requestBuilder.header("Range", "bytes=$existingLength-")
                    requestBuilder.header("If-Match", "\"${expectedChecksum.lowercase(Locale.ROOT)}\"")
                }

                val request = requestBuilder.build()

                // Execute request
                activeCall = updateClient.newDownloadCall(request, expectedChecksum)
                activeCall?.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            viewModelScope.launch {
                                if (!call.isCanceled()) {
                                    Log.e(tag, "Download failed", e)
                                    handleDownloadFailure(downloadUrl, fileName, retryAttempt, e.message ?: "Unknown error")
                                }
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            // Handle HTTP 412 Precondition Failed - file changed on server
                            if (response.code == 412) {
                                Log.w(tag, "Server file changed (HTTP 412), restarting download")
                                response.close()
                                viewModelScope.launch {
                                    _isDownloading.value = false
                                    activeDownload = null
                                    activeCall = null
                                    // Delete partial file and restart
                                    file.delete()
                                    handleDownloadFailure(downloadUrl, fileName, retryAttempt, "File changed on server", forceRetry = true)
                                }
                                return
                            }

                            if (!response.isSuccessful && response.code != 206) {
                                response.close()
                                viewModelScope.launch {
                                    handleDownloadFailure(
                                        downloadUrl,
                                        fileName,
                                        retryAttempt,
                                        "HTTP ${response.code} - ${response.message}",
                                    )
                                }
                                return
                            }

                            try {
                                // Get content length and resume info
                                val contentLength = response.body.contentLength()
                                val totalLength =
                                    if (response.code == 206) {
                                        val range = response.header("Content-Range")
                                        range?.substringAfter("/")?.toLongOrNull() ?: 0L
                                    } else {
                                        contentLength
                                    }

                                if (totalLength > 0L && totalLength != expectedSize) {
                                    response.close()
                                    file.delete()
                                    viewModelScope.launch {
                                        handleDownloadFailure(
                                            downloadUrl,
                                            fileName,
                                            retryAttempt,
                                            "Server APK size does not match signed manifest",
                                        )
                                    }
                                    return
                                }

                                var resumePosition = existingLength
                                if (resumePosition > 0 && response.code != 206) {
                                    Log.w(tag, "Server ignored range request with HTTP ${response.code}; restarting download from scratch")
                                    resumePosition = 0L
                                    file.delete()
                                }

                                // Store download state
                                activeDownload =
                                    DownloadState(
                                        fileName = fileName,
                                        url = downloadUrl,
                                        totalBytes = expectedSize,
                                        downloadedBytes = resumePosition,
                                        etag = "\"${expectedChecksum.lowercase(Locale.ROOT)}\"",
                                        lastModified = response.header("Last-Modified"),
                                        resumePosition = resumePosition,
                                        checksum = expectedChecksum,
                                        retryCount = retryAttempt,
                                    )
                                viewModelScope.launch {
                                    _downloadState.value = activeDownload
                                }

                                // Create output stream
                                val outputStream = FileOutputStream(file, resumePosition > 0)

                                // Get input stream
                                val inputStream = response.body.byteStream()

                                // Create buffer
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytesRead = resumePosition

                                // Read input stream
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    if (!_isDownloading.value) {
                                        // Download was cancelled
                                        break
                                    }

                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead

                                    // Update progress
                                    val totalBytes = expectedSize
                                    if (totalBytes > 0) {
                                        val progress = (totalBytesRead.toFloat() / totalBytes.toFloat()) * 100f
                                        viewModelScope.launch {
                                            _downloadProgress.value = progress.coerceIn(0f, 100f)
                                            maybeUpdateDownloadProgressNotification(_downloadProgress.value)
                                            activeDownload = activeDownload?.copy(downloadedBytes = totalBytesRead)
                                            _downloadState.value = activeDownload

                                            // Save state periodically (every 5% progress)
                                            if (progress % 5f < 1f) {
                                                saveDownloadState()
                                            }
                                        }
                                    }
                                }

                                // Close streams
                                outputStream.flush()
                                outputStream.close()
                                inputStream.close()

                                if (!_isDownloading.value) {
                                    Log.d(tag, "Download cancelled/paused by user. Keeping partial file.")
                                    return
                                }

                                // Verify file integrity
                                val fileSize = file.length()
                                if (fileSize != expectedSize) {
                                    viewModelScope.launch {
                                        file.delete()
                                        handleDownloadFailure(downloadUrl, fileName, retryAttempt, "Downloaded APK size does not match signed manifest")
                                    }
                                    return
                                } else {
                                    Log.d(tag, "File size verification passed: $fileSize bytes (expected: $expectedSize)")
                                }

                                // Verify checksum if available
                                val checksumValid =
                                    activeDownload?.checksum?.takeIf { it.isNotBlank() }?.let { expectedChecksum ->
                                        val actualChecksum = calculateFileChecksum(file)
                                        val isValid = verifyChecksum(actualChecksum, expectedChecksum)
                                        if (!isValid) {
                                            Log.e(tag, "Checksum verification failed. Expected: $expectedChecksum, Actual: $actualChecksum")
                                        }
                                        isValid
                                    } ?: false

                                if (!checksumValid) {
                                    viewModelScope.launch {
                                        file.delete() // Delete corrupted file
                                        handleDownloadFailure(downloadUrl, fileName, retryAttempt, "Checksum verification failed")
                                    }
                                    return
                                }

                                // Download complete and verified
                                viewModelScope.launch(Dispatchers.IO) {
                                    if (_isDownloading.value) {
                                        val finalFile = file

                                        _isDownloading.value = false
                                        _downloadProgress.value = 100f
                                        _downloadedFile.value = finalFile
                                        showDownloadCompletedNotification(finalFile.name)

                                        // Calculate and store final checksum
                                        val finalChecksum = calculateFileChecksum(finalFile)
                                        activeDownload = activeDownload?.copy(checksum = finalChecksum)
                                        saveDownloadState() // Save final state with checksum
                                        // Clear active download but keep downloaded file info
                                        activeDownload = null
                                        activeCall = null
                                        _downloadState.value = null
                                        Log.d(
                                            tag,
                                            "Download complete: ${finalFile.absolutePath} (${finalFile.length()} bytes, checksum: $finalChecksum)",
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                viewModelScope.launch {
                                    if (_isDownloading.value) {
                                        Log.e(tag, "Download failed during write", e)
                                        handleDownloadFailure(downloadUrl, fileName, retryAttempt, e.message ?: "Unknown error")
                                    }
                                }
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                Log.e(tag, "Download setup failed", e)
                _isDownloading.value = false
                _error.value = "Download failed: ${e.message ?: "Unknown error"}"
                activeDownload = null
                activeCall = null
                _downloadState.value = null
                cancelDownloadNotification()
            }
        }
    }

    private fun handleDownloadFailure(
        downloadUrl: String,
        fileName: String,
        retryAttempt: Int,
        errorMessage: String,
        forceRetry: Boolean = false,
    ) {
        _isDownloading.value = false
        activeCall = null

        // Cancel old notification to avoid duplicate download notifications before retry
        cancelDownloadNotification()

        val nextRetryAttempt = retryAttempt + 1

        // Update activeDownload's resumePosition and retryCount so we can resume during retry
        val context = getApplication<Application>()
        val downloadDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        val file = downloadDir?.let { File(it, fileName) }
        val currentSize = file?.length() ?: 0L

        if (activeDownload != null && activeDownload?.url == downloadUrl) {
            activeDownload =
                activeDownload?.copy(
                    resumePosition = currentSize,
                    downloadedBytes = currentSize,
                    retryCount = nextRetryAttempt,
                )
            saveDownloadState()
        }

        if (forceRetry || nextRetryAttempt < maxRetryAttempts) {
            val delayMs = if (forceRetry) 1000L else (1L shl retryAttempt) * 1000L

            Log.w(tag, "Download attempt ${retryAttempt + 1} failed: $errorMessage. Retrying in ${delayMs}ms...")
            _error.value = "Download failed: $errorMessage. Retrying ($nextRetryAttempt/$maxRetryAttempts)..."

            viewModelScope.launch {
                delay(delayMs)
                if (!_isDownloading.value) {
                    Log.d(tag, "Retrying download (attempt $nextRetryAttempt)")
                    val expectedSize = _latestVersion.value?.apkSize ?: 0
                    val expectedChecksum = _latestVersion.value?.apkSha256
                        ?: activeDownload?.checksum.orEmpty()
                    downloadApkInApp(downloadUrl, fileName, expectedSize, expectedChecksum, nextRetryAttempt)
                }
            }
        } else {
            Log.e(tag, "Download failed after $maxRetryAttempts attempts: $errorMessage")
            _error.value = "Download failed after $maxRetryAttempts attempts."
            activeDownload = null
            _downloadState.value = null
            clearDownloadState()
            cancelDownloadNotification()
        }
    }

    fun proceedWithMismatchedDownload() {
        val pending = pendingMismatchedDownload
        if (pending == null) {
            _error.value = "No mismatched download is available to proceed with."
            return
        }
        cleanupUpdaterDownloadAfterFailure(
            "Size-mismatched APKs cannot bypass signed update verification.",
            File(pending.filePath),
        )
    }

    private fun cleanupUpdaterDownloadAfterFailure(
        message: String,
        fileToDelete: File? = null,
    ) {
        val context = getApplication<Application>()
        val downloadDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        val activeFile =
            activeDownload?.fileName?.let { fileName ->
                downloadDir?.let { File(it, fileName) }
            }
        val pendingFile = pendingMismatchedDownload?.filePath?.let { File(it) }
        val downloadedFile = _downloadedFile.value

        listOfNotNull(fileToDelete, activeFile, pendingFile, downloadedFile)
            .distinctBy { it.absolutePath }
            .forEach { file ->
                runCatching {
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(tag, "Deleted updater file after failure: ${file.absolutePath}, success: $deleted")
                    }
                }.onFailure { e ->
                    Log.e(tag, "Failed to delete updater file: ${file.absolutePath}", e)
                }
            }

        resetDownloadState()
        _error.value = "$message Download state was reset. Please try again."
    }

    /**
     * Calculate SHA-256 checksum of a file
     */
    private fun calculateFileChecksum(file: File): String =
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int

            FileInputStream(file).use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error calculating checksum", e)
            ""
        }

    /**
     * Verify file checksum matches expected value
     */
    private fun verifyFileChecksum(
        file: File,
        expectedChecksum: String,
    ): Boolean {
        if (expectedChecksum.isBlank()) return false
        val actualChecksum = calculateFileChecksum(file)
        return verifyChecksum(actualChecksum, expectedChecksum)
    }

    /**
     * Verify checksum with various format support
     */
    private fun verifyChecksum(
        actual: String,
        expected: String,
    ): Boolean {
        if (actual.isBlank() || expected.isBlank()) return false

        // Handle different checksum formats (sha-256=xxx, sha256:xxx, etc.)
        val cleanExpected =
            expected
                .substringAfter("sha-256=", "")
                .substringAfter("sha256:", "")
                .substringAfter("SHA-256=", "")
                .substringAfter("SHA256:", "")
                .ifBlank { expected }
                .lowercase()
                .trim()

        return actual.lowercase() == cleanExpected
    }

    /**
     * Install the downloaded APK with improved error handling
     */
    fun installDownloadedApk() {
        val file = _downloadedFile.value
        if (file == null || !file.exists()) {
            _error.value = "No downloaded file found"
            return
        }

        try {
            val context = getApplication<Application>()

            // Check if the file is valid
            if (file.length() == 0L) {
                _downloadedFile.value = null
                _error.value = "Downloaded file is corrupted. Please try downloading again."
                clearDownloadState()
                return
            }

            validateDownloadedApkIdentity(context, file)?.let { error ->
                _error.value = error
                return
            }

            // For Android 8.0 and later, check if install from unknown sources is allowed
            if (!context.packageManager.canRequestPackageInstalls()) {
                // Automatically open settings to allow install from unknown sources
                try {
                    val intent =
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            "package:${context.packageName}".toUri(),
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    _error.value = "Could not open settings. Please enable 'Install unknown apps' manually in Settings."
                    Log.e(tag, "Error opening unknown sources settings", e)
                }
                return
            }

            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                // IMPORTANT: Clear download state after successful launch of installer
                // This prevents the app from thinking the update is still downloaded
                // and showing "Install Update" again after the user installs it.
                _downloadedFile.value = null
                activeDownload = null
                _downloadState.value = null
                clearDownloadState() // Clear persisted state as well
            } else {
                // Keep _downloadedFile so user can retry from Settings
                _error.value =
                    "No app available to install APK files. The APK file is still saved — go to your file manager to install it manually."
                clearDownloadState()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error installing APK", e)
            // Keep _downloadedFile so user can retry install
            _error.value = "Could not install APK: ${e.message ?: "Unknown error"}. Tap Install to try again."
            clearDownloadState()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApkIdentity(context: Application, file: File): String? {
        val packageManager = context.packageManager
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        val archiveInfo = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "Downloaded file is not a valid Android package."
        if (archiveInfo.packageName != BuildConfig.APPLICATION_ID) {
            return "Downloaded update belongs to a different app (${archiveInfo.packageName})."
        }

        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            archiveInfo.versionCode.toLong()
        }
        if (archiveVersionCode <= BuildConfig.VERSION_CODE.toLong()) {
            return "Downloaded update is not newer than the installed Sonorus version."
        }

        val installedInfo = packageManager.getPackageInfo(context.packageName, flags)
        val archiveDigests = currentSignerDigests(archiveInfo)
        val installedDigests = currentSignerDigests(installedInfo)
        if (archiveDigests.isEmpty() || archiveDigests != installedDigests) {
            return "Downloaded update is not signed with this Sonorus installation's certificate."
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun currentSignerDigests(packageInfo: PackageInfo): Set<List<Byte>> {
        val signers =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                packageInfo.signatures.orEmpty()
            }
        return signers.map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).toList()
        }.toSet()
    }

    /**
     * Cancel the current download with proper cleanup
     */
    fun cancelDownload() {
        Log.d(tag, "Cancelling download")
        activeCall?.cancel()
        activeCall = null
        _isDownloading.value = false
        _error.value = null

        // Delete partial download file
        try {
            val context = getApplication<Application>()
            val downloadDir =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }

            activeDownload?.let { downloadState ->
                downloadDir?.let { dir ->
                    val file = File(dir, downloadState.fileName)
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(tag, "Deleted partial download file: ${file.absolutePath}, success: $deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error deleting partial download file", e)
        }

        // Clear download state
        activeDownload = null
        _downloadState.value = null
        _downloadProgress.value = 0f
        clearDownloadState()
        cancelDownloadNotification()
    }

    /**
     * Reset all download states - useful for retry scenarios
     */
    fun resetDownloadState() {
        Log.d(tag, "Resetting download state")
        activeCall?.cancel()
        activeCall = null
        activeDownload = null
        _isDownloading.value = false
        _downloadProgress.value = 0f
        _downloadState.value = null
        _downloadedFile.value = null
        _error.value = null

        // Clear persisted state
        clearDownloadState()
        cancelDownloadNotification()
    }

    /**
     * Resume a previously paused/interrupted download
     */
    fun resumeDownload() {
        if (activeDownload == null) {
            _error.value = "No download to resume"
            return
        }

        if (_isDownloading.value) {
            Log.d(tag, "Download already in progress")
            return
        }

        val downloadState = activeDownload!!
        Log.d(
            tag,
            "Resuming download: ${downloadState.fileName} from ${downloadState.downloadedBytes} bytes (retry: ${downloadState.retryCount})",
        )

        downloadApkInApp(
            downloadState.url,
            downloadState.fileName,
            downloadState.totalBytes,
            downloadState.checksum.orEmpty(),
            downloadState.retryCount,
        )
    }

    /**
     * Check if there's a download that can be resumed
     */
    fun canResumeDownload(): Boolean = activeDownload != null && !_isDownloading.value && _downloadedFile.value == null

    /**
     * Start periodic update checks if auto-check is enabled and updates are enabled
     */
    private fun startPeriodicUpdateChecks() {
        viewModelScope.launch {
            // Combine both update settings
            _appSettings.updatesEnabled.collectLatest { updatesEnabled ->
                if (updatesEnabled) {
                    _appSettings.autoCheckForUpdates.collectLatest { autoCheckEnabled ->
                        if (autoCheckEnabled) {
                            // Check immediately if it's been more than the interval
                            val timeSinceLastCheck = System.currentTimeMillis() - lastUpdateCheck
                            if (timeSinceLastCheck > updateCheckInterval) {
                                checkForUpdates(force = false)
                            }

                            // Schedule periodic checks
                            while (autoCheckEnabled && _appSettings.updatesEnabled.first()) {
                                delay(updateCheckInterval)
                                if (_appSettings.autoCheckForUpdates.first() && _appSettings.updatesEnabled.first()) {
                                    checkForUpdates(force = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        Log.d(tag, "ViewModel cleared")
        // Note: Active downloads will be cancelled when ViewModel is cleared
        // Download state is persisted and can be resumed later
        cancelDownload()
    }

    private fun ensureDownloadNotificationChannel() {
        val context = getApplication<Application>()

        val channel =
            NotificationChannel(
                updateDownloadChannelId,
                context.getString(R.string.notification_updater_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_updater_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun maybeUpdateDownloadProgressNotification(progress: Float) {
        val progressPercent = progress.toInt().coerceIn(0, 100)
        if (progressPercent == lastNotifiedProgressPercent) {
            return
        }
        lastNotifiedProgressPercent = progressPercent
        showDownloadProgressNotification(progressPercent)
    }

    private fun showDownloadProgressNotification(progressPercent: Int) {
        val context = getApplication<Application>()
        val openUpdatesIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "updates")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                4201,
                openUpdatesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = context.getString(R.string.notification_updater_downloading, progressPercent)
        val notification =
            NotificationCompat
                .Builder(context, updateDownloadChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_updater_title))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(100, progressPercent, false)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(updateDownloadNotificationId, notification)
    }

    private fun showDownloadCompletedNotification(
        @Suppress("UNUSED_PARAMETER") fileName: String,
    ) {
        val context = getApplication<Application>()
        val openUpdatesIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "updates")
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                4202,
                openUpdatesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentText = context.getString(R.string.notification_updater_download_complete)
        val notification =
            NotificationCompat
                .Builder(context, updateDownloadChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_updater_title))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager.notify(updateDownloadNotificationId, notification)

        completionNotificationDismissJob?.cancel()
        completionNotificationDismissJob =
            viewModelScope.launch {
                delay(updateDownloadCompletionAutoDismissMs)
                notificationManager.cancel(updateDownloadNotificationId)
                completionNotificationDismissJob = null
            }
    }

    private fun cancelDownloadNotification() {
        completionNotificationDismissJob?.cancel()
        completionNotificationDismissJob = null
        notificationManager.cancel(updateDownloadNotificationId)
        lastNotifiedProgressPercent = -1
    }
}
