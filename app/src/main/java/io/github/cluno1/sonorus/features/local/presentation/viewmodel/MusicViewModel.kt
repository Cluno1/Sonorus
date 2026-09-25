/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import io.github.cluno1.sonorus.shared.data.model.AutoEQDatabase
import io.github.cluno1.sonorus.shared.data.model.AutoEQProfile
import io.github.cluno1.sonorus.util.AutoEQManager
import android.util.LruCache
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import coil.Coil
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.activities.MainActivity
import io.github.cluno1.sonorus.activities.RhythmGuardTimeoutActivity
import io.github.cluno1.sonorus.shared.data.model.Album
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.data.model.Artist
import io.github.cluno1.sonorus.shared.data.model.LyricsSourcePreference
import io.github.cluno1.sonorus.shared.data.model.MediaScanMode
import io.github.cluno1.sonorus.shared.data.model.ScanPhase
import io.github.cluno1.sonorus.features.local.data.repository.MusicRepository
import io.github.cluno1.sonorus.features.local.data.device.DeviceLyricsCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtworkCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtistArtworkCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceDetailsCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceDetailsField
import io.github.cluno1.sonorus.features.local.data.device.DeviceEditorialCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtworkSaveTarget
import io.github.cluno1.sonorus.features.local.data.device.DeviceDocumentPolicy
import io.github.cluno1.sonorus.features.local.data.device.DeviceManualMetadataKind
import io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataRequest
import io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataPolicy
import io.github.cluno1.sonorus.features.local.data.device.DevicePrivateMetadataFileNames
import io.github.cluno1.sonorus.features.local.data.device.DevicePublicMetadataProvider
import io.github.cluno1.sonorus.network.NetworkClient
import io.github.cluno1.sonorus.features.catalog.domain.CATALOG_SONG_ID_PREFIX
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.RhythmNowPlayingItem
import io.github.cluno1.sonorus.features.catalog.domain.RhythmQueueEntry
import io.github.cluno1.sonorus.features.catalog.domain.isCatalogLibrarySong
import io.github.cluno1.sonorus.features.catalog.domain.lyricsVariants
import io.github.cluno1.sonorus.features.catalog.domain.selectCatalogLyricsVariant
import io.github.cluno1.sonorus.features.catalog.domain.toStableCatalogSongId
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogLyricsDraftStore
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricLanguageFormat
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsDraft
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsSyncState
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsSyncStatus
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsTranslation
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.normalizeCatalogLyricsLanguageTag
import io.github.cluno1.sonorus.features.local.presentation.player.PlaybackControlStateMachine
import io.github.cluno1.sonorus.features.local.presentation.player.PlaybackControlUiState
import io.github.cluno1.sonorus.features.local.presentation.player.PlaybackReadiness
import io.github.cluno1.sonorus.features.local.data.database.entity.toSong
import io.github.cluno1.sonorus.shared.data.model.PlaybackLocation
import io.github.cluno1.sonorus.shared.data.model.Playlist
import io.github.cluno1.sonorus.shared.data.model.Queue
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.data.model.FolderNode
import io.github.cluno1.sonorus.infrastructure.service.MediaPlaybackService
import io.github.cluno1.sonorus.infrastructure.widget.WidgetUpdater
import io.github.cluno1.sonorus.util.AudioDeviceManager
import io.github.cluno1.sonorus.util.EqualizerUtils
import io.github.cluno1.sonorus.util.GsonUtils
import io.github.cluno1.sonorus.util.MediaUtils
import io.github.cluno1.sonorus.util.PlaylistImportExportUtils
import io.github.cluno1.sonorus.util.RhythmBackupDetectedException
import io.github.cluno1.sonorus.util.PlaybackCommandSerializer
import io.github.cluno1.sonorus.util.RhythmLyricsParser
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeoutOrNull
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.cluno1.sonorus.features.local.data.database.RhythmDatabase
import io.github.cluno1.sonorus.features.local.data.database.entity.PlaylistEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.PlaylistSongEntity
import java.time.Duration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.util.PendingWriteRequest
import io.github.cluno1.sonorus.util.PendingBatchWriteRequest
import io.github.cluno1.sonorus.util.PendingLyricsWriteRequest
import io.github.cluno1.sonorus.util.PendingDeleteRequest
import io.github.cluno1.sonorus.util.QueueUtils
import io.github.cluno1.sonorus.util.GenreUtils
import io.github.cluno1.sonorus.util.NaturalSortComparator
import io.github.cluno1.sonorus.util.LyricLine
import io.github.cluno1.sonorus.util.LyricsParser
import io.github.cluno1.sonorus.util.LyricsContributionAttribution
import io.github.cluno1.sonorus.util.ServiceStartUtils
import io.github.cluno1.sonorus.utils.StatusBroadcaster
import io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository
import io.github.cluno1.sonorus.shared.data.repository.PlaybackActivityKind
import io.github.cluno1.sonorus.shared.data.repository.PlaybackDurationAccumulator
import io.github.cluno1.sonorus.shared.data.repository.PlaybackMediaKind
import io.github.cluno1.sonorus.shared.data.repository.PlaybackSubject

enum class DeviceManualMetadataError {
    SONG_UNAVAILABLE,
    TITLE_REQUIRED,
    ARTIST_REQUIRED,
    PROVIDER_REQUIRED,
    REQUEST_FAILED,
    APPLY_FAILED,
}

enum class DeviceManualProviderStatus { LOADING, SUCCESS, EMPTY, FAILED }

data class DeviceManualMetadataUiState(
    val songId: String? = null,
    val targetArtistName: String? = null,
    val kind: DeviceManualMetadataKind = DeviceManualMetadataKind.LYRICS,
    val isSearching: Boolean = false,
    val isApplying: Boolean = false,
    val hasSearched: Boolean = false,
    val lyricsCandidates: List<DeviceLyricsCandidate> = emptyList(),
    val artworkCandidates: List<DeviceArtworkCandidate> = emptyList(),
    val artistArtworkCandidates: List<DeviceArtistArtworkCandidate> = emptyList(),
    val detailsCandidates: List<DeviceDetailsCandidate> = emptyList(),
    val editorialCandidates: List<DeviceEditorialCandidate> = emptyList(),
    val providerStatuses: Map<DevicePublicMetadataProvider, DeviceManualProviderStatus> = emptyMap(),
    val error: DeviceManualMetadataError? = null,
    val applied: Boolean = false,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    sealed class RestoreResult {
        object Idle : RestoreResult()
        object Queued : RestoreResult()
        data class Success(val wasQueued: Boolean) : RestoreResult()
        data class Failure(val errorMessage: String) : RestoreResult()
    }

    data class QueuedRestore(
        val backupJson: String,
        val sections: AppSettings.BackupRestoreSections,
        val onResult: (Boolean) -> Unit
    )

    private val _restoreResult = MutableStateFlow<RestoreResult>(RestoreResult.Idle)
    val restoreResult: StateFlow<RestoreResult> = _restoreResult.asStateFlow()

    private var pendingRestore: QueuedRestore? = null

    fun clearRestoreResult() {
        _restoreResult.value = RestoreResult.Idle
    }

    companion object {
        private const val TAG = "MusicViewModel"
        private const val COLOR_SOURCE_ALBUM_ART = "ALBUM_ART"
        /**
         * Threshold above which we skip per-item moveMediaItem calls and use
         * a single setMediaItems call instead. moveMediaItem triggers an IPC
         * round-trip for each call, which freezes the UI on large queues.
         */
        private const val BULK_REPLACE_THRESHOLD = 80

        // SharedPreferences keys
        private const val PREF_GAPLESS_PLAYBACK = "gapless_playback"
        private const val PREF_CROSSFADE = "crossfade"
        private const val PREF_CROSSFADE_DURATION = "crossfade_duration"
        private const val PREF_AUDIO_NORMALIZATION = "audio_normalization"
        private const val PREF_REPLAY_GAIN = "replay_gain"
        private const val PREF_SHOW_LYRICS = "show_lyrics"
        private const val PREF_ONLINE_ONLY_LYRICS = "online_only_lyrics"
        private const val PREF_SONG_PLAY_COUNTS = "song_play_counts"
        
        // Player control constants
        private const val REWIND_THRESHOLD_MS = 3000L // 3 seconds

        // Keep manually imported lyrics bounded to avoid UI stalls and oversized cache files.
        private const val MAX_EDITABLE_LYRICS_CHARS = 200_000

        // Avoid rapid controller rebuild storms during cold start/service reconnect windows.
        private const val CONTROLLER_CONNECT_MIN_INTERVAL_MS = 750L

        private const val MEDIA_SCAN_NOTIFICATION_ID = 1501
        private const val PLAYLIST_IMPORT_NOTIFICATION_ID = 1502
        private const val PLAYLIST_EXPORT_NOTIFICATION_ID = 1503
        private const val OPERATION_NOTIFICATION_AUTO_DISMISS_MS = 6000L

        // Phase two is a private-catalog-only product. Keep the legacy ViewModel as the
        // Media3 controller adapter, but never activate its MediaStore library pipeline.
        private val deviceLibraryEnabled: Boolean
            get() = CatalogPlaybackPolicy.DEVICE_LIBRARY_ENABLED

        private const val DEFAULT_BLUETOOTH_LYRIC_LINE = "No lyrics"
        private const val METADATA_EXTRA_ORIGINAL_TITLE = "io.github.cluno1.sonorus.extra.original_title"
        private const val METADATA_EXTRA_ORIGINAL_ARTIST = "io.github.cluno1.sonorus.extra.original_artist"
        private const val METADATA_EXTRA_ORIGINAL_ALBUM = "io.github.cluno1.sonorus.extra.original_album"
    }

    private val repository = MusicRepository(application)
    private val catalogRepository = CatalogModule.repository(application)
    private val catalogLyricsDraftStore = CatalogLyricsDraftStore(application)
    private val notificationManagerHelper = LibraryNotificationManager(application)
    private val metadataManagerHelper = LibraryMetadataManager(
        context = application,
        scope = viewModelScope,
        getCurrentSong = { _currentSong.value },
        updateCurrentSongMetadata = { updatedSong -> updateCurrentSongMetadata(updatedSong) },
        bulkUpdateSongs = { updatedSongsMap ->
            val newSongs = _songs.value.map { song ->
                updatedSongsMap[song.id] ?: song
            }
            _songs.value = newSongs
            repository.updateAndPersistSongs(newSongs)
            viewModelScope.launch {
                _albums.value = repository.loadAlbums()
                _artists.value = repository.loadArtists()
            }
        }
    )
    private var mediaScanNotificationSequence: Long = 0L
    
    // Job for debouncing ContentObserver-triggered refreshes
    private var mediaStoreRefreshJob: kotlinx.coroutines.Job? = null
    
    // Playback stats repository for enhanced tracking
    private val playbackStatsRepository = PlaybackStatsRepository.getInstance(application)
    
    // Audio device manager (exposed for UI components that need device detection)
    val audioDeviceManager = AudioDeviceManager(application)
    
    // Settings manager
    val appSettings = AppSettings.getInstance(application)

    private val statusBroadcaster = StatusBroadcaster(application)
    
    // AutoEQ manager
    private val autoEQManager = AutoEQManager(application)
    
    // Queue state manager
    private val queueStateHolder = QueueStateHolder()
    
    // Playback command serializer for deterministic queue operations
    private val commandSerializer = PlaybackCommandSerializer()
    
    // Settings
    val showLyrics = appSettings.showLyrics
    val showOnlineOnlyLyrics = appSettings.onlineOnlyLyrics
    val useSystemTheme = appSettings.useSystemTheme
    val darkMode = appSettings.darkMode
    val autoConnectDevice = appSettings.autoConnectDevice
    val maxCacheSize = appSettings.maxCacheSize

    
    // Playback settings
    val enableGaplessPlayback = appSettings.gaplessPlayback
    val enableCrossfade = appSettings.crossfade
    val crossfadeDuration = appSettings.crossfadeDuration
    val enableCrossfadeOnSkip = appSettings.crossfadeOnSkip
    val enableAudioNormalization = appSettings.audioNormalization
    val enableReplayGain = appSettings.replayGain
    val skipSilenceEnabled = appSettings.skipSilenceEnabled
    
    // Queue & Shuffle behavior settings
    val shuffleUsesExoplayer = appSettings.shuffleUsesExoplayer
    val autoAddToQueue = appSettings.autoAddToQueue
    val clearQueueOnNewSong = appSettings.clearQueueOnNewSong
    val shuffleModePersistence = appSettings.shuffleModePersistence
    val repeatModePersistence = appSettings.repeatModePersistence
    val playbackSpeed = appSettings.playbackSpeed
    val defaultPlaybackSpeed = appSettings.defaultPlaybackSpeed
    val useDefaultPlaybackSpeed = appSettings.useDefaultPlaybackSpeed
    val playbackPitch = appSettings.playbackPitch
    
    // Equalizer settings
    val equalizerEnabled = appSettings.equalizerEnabled
    val equalizerPreset = appSettings.equalizerPreset
    val equalizerBandLevels = appSettings.equalizerBandLevels
    val bassBoostEnabled = appSettings.bassBoostEnabled
    val bassBoostStrength = appSettings.bassBoostStrength
    val virtualizerEnabled = appSettings.virtualizerEnabled
    val virtualizerStrength = appSettings.virtualizerStrength
    val monoAudioEnabled = appSettings.monoAudioEnabled
    
    // Spatialization status
    private val _spatializationStatus = MutableStateFlow("Unknown")
    val spatializationStatus: StateFlow<String> = _spatializationStatus.asStateFlow()
    
    private val _isSpatializationAvailable = MutableStateFlow(false)
    val isSpatializationAvailable: StateFlow<Boolean> = _isSpatializationAvailable.asStateFlow()
    
    // Bass boost availability (based on device support)
    private val _isBassBoostAvailable = MutableStateFlow(appSettings.isBassBoostAvailable())
    val isBassBoostAvailable: StateFlow<Boolean> = _isBassBoostAvailable.asStateFlow()
    
    // Media scanning progress
    val scanProgress = repository.scanProgress
    val scanDiagnostics = repository.scanDiagnostics
    val lastScanTimestamp = appSettings.lastScanTimestamp
    val lastScanDuration = appSettings.lastScanDuration
    
    // Media scanning filters
    val allowedFormats = appSettings.allowedFormats
    val minimumBitrate = appSettings.minimumBitrate
    val minimumDuration = appSettings.minimumDuration
    
    // Search history
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    
    // Lyrics
    private val _currentLyrics = MutableStateFlow<LyricsData?>(null)
    val currentLyrics: StateFlow<LyricsData?> = _currentLyrics.asStateFlow()
    private val _catalogLyricsLanguages = MutableStateFlow<List<String>>(emptyList())
    val catalogLyricsLanguages: StateFlow<List<String>> = _catalogLyricsLanguages.asStateFlow()
    private val _catalogLyricsLanguage = MutableStateFlow<String?>(null)
    val catalogLyricsLanguage: StateFlow<String?> = _catalogLyricsLanguage.asStateFlow()
    private val _deviceLyricsCandidates = MutableStateFlow<List<DeviceLyricsCandidate>>(emptyList())
    val deviceLyricsCandidates: StateFlow<List<DeviceLyricsCandidate>> = _deviceLyricsCandidates.asStateFlow()
    private var deviceLyricsCandidateIndex = -1
    private var deviceLyricsCandidatesSongId: String? = null
    private val _deviceManualMetadataState = MutableStateFlow(DeviceManualMetadataUiState())
    val deviceManualMetadataState: StateFlow<DeviceManualMetadataUiState> =
        _deviceManualMetadataState.asStateFlow()
    private var deviceManualMetadataJob: Job? = null
    private var deviceManualMetadataRequestId = 0L

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()
    
    // Track lyrics adjustment offset
    private val _lyricsTimeOffset = MutableStateFlow(0)
    val lyricsTimeOffset: StateFlow<Int> = _lyricsTimeOffset.asStateFlow()

    // New helper methods
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    // Lyrics fetch job tracking to prevent race conditions
    private var lyricsFetchJob: Job? = null

    private var cachedSyncedLyricsRaw: String? = null
    private var cachedParsedSyncedLyrics: List<LyricLine> = emptyList()
    private var lastBroadcastLyricSongId: String? = null
    private var lastBroadcastLyricLine: String? = null
    private var lastAppliedBluetoothLyricSongId: String? = null
    private var lastAppliedBluetoothLyricLine: String? = null
    private var pendingQueueRestore: Pair<List<String>, Int>? = null
    
    // Scan job for cancellation support
    private var scanJob: Job? = null

    private var mediaStoreObserverRegisteredTimeMs = 0L

    private fun isContentUriReadable(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme != "content") return true
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    // Playback session tracking for accurate stats
    private var currentPlaybackSubject: PlaybackSubject? = null
    private var currentPlaybackRecentSong: Song? = null
    private var currentPlaybackAddedToRecent = false
    private val currentPlaybackDuration = PlaybackDurationAccumulator()

    // Broadcast receiver for service/widget state changes
    private val favoriteChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "io.github.cluno1.sonorus.action.FAVORITE_CHANGED" -> {
                    Log.d(TAG, "Received favorite change notification from service")
                    // Refresh favorite songs and playlists from settings
                    refreshFavoriteSongs()
                    loadSavedPlaylists()
                }
                "io.github.cluno1.sonorus.action.WIDGET_TOGGLE_FAVORITE" -> {
                    Log.d(TAG, "Received favorite toggle from widget")
                    // Toggle favorite for current song
                    _currentSong.value?.let { song ->
                        viewModelScope.launch {
                            toggleFavorite(song)
                        }
                    }
                }
                MediaPlaybackService.ACTION_SHUFFLE_STATE_CHANGED -> {
                    val shuffleEnabled = intent.getBooleanExtra(
                        MediaPlaybackService.EXTRA_SHUFFLE_ENABLED,
                        _isShuffleEnabled.value
                    )
                    Log.d(TAG, "Received shuffle state notification from service: $shuffleEnabled")

                    if (appSettings.shuffleUsesExoplayer.value) {
                        if (_isShuffleEnabled.value != shuffleEnabled) {
                            _isShuffleEnabled.value = shuffleEnabled
                        }

                        if (shuffleEnabled && !queueStateHolder.hasOriginalQueue() && _currentQueue.value.songs.isNotEmpty()) {
                            queueStateHolder.saveOriginalQueueState(
                                _currentQueue.value.songs,
                                queueStateHolder.currentQueueSourceName.value
                            )
                        }

                        viewModelScope.launch {
                            delay(75)
                            syncQueueWithMediaController()
                        }
                    }
                }

                MediaPlaybackService.BROADCAST_SLEEP_TIMER_STATUS -> {
                    val timerActive = intent.getBooleanExtra(
                        MediaPlaybackService.EXTRA_TIMER_ACTIVE,
                        false
                    )
                    val remainingMs = intent.getLongExtra(
                        MediaPlaybackService.EXTRA_REMAINING_TIME,
                        0L
                    ).coerceAtLeast(0L)
                    val totalMs = intent.getLongExtra(
                        MediaPlaybackService.EXTRA_TOTAL_TIME,
                        0L
                    ).coerceAtLeast(0L)

                    // Ignore stale "timer stopped" broadcasts that arrive during
                    // the service's internal timer-rewrite transition.  This covers
                    // BOTH the synchronous stopSleepTimer() inside startSleepTimer()
                    // AND the old coroutine's CancellationException handler which
                    // can fire up to ~1s later (at the next delay suspension point).
                    val gracePeriod = 3_000L
                    if (!timerActive && remainingMs == 0L) {
                        val elapsed = SystemClock.elapsedRealtime() - sleepTimerStartRealtimeMs
                        if (elapsed < gracePeriod) {
                            return@onReceive
                        }
                    }

                    if (timerActive) {
                        val remainingSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(1L)
                        val totalSeconds = ((totalMs + 999L) / 1000L).coerceAtLeast(1L)
                        _sleepTimerActive.value = true
                        _sleepTimerRemainingSeconds.value = remainingSeconds
                        _sleepTimerTotalSeconds.value = totalSeconds
                        sleepTimerJob?.cancel()
                        sleepTimerJob = null
                    } else {
                        _sleepTimerActive.value = false
                        _sleepTimerRemainingSeconds.value = 0L
                        _sleepTimerTotalSeconds.value = 0L
                        sleepTimerJob?.cancel()
                        sleepTimerJob = null
                    }
                }
            }
        }
    }

    // Refresh favorite songs from app settings
    private fun refreshFavoriteSongs() {
        viewModelScope.launch {
            try {
                // Add a small delay to ensure AppSettings StateFlow has updated
                delay(50)
                
                val favoriteSongsJson = appSettings.favoriteSongs.value
                if (favoriteSongsJson != null) {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val newFavorites = GsonUtils.gson.fromJson<Set<String>>(favoriteSongsJson, type)
                    _favoriteSongs.value = newFavorites
                    Log.d(TAG, "Refreshed favorite songs: ${newFavorites.size} favorites")
                    
                    // Update current song favorite status if needed
                    currentSong.value?.let { song ->
                        val newIsFavorite = newFavorites.contains(song.id.toStableCatalogSongId())
                        if (_isFavorite.value != newIsFavorite) {
                            _isFavorite.value = newIsFavorite
                            Log.d(TAG, "Updated current song favorite status to: $newIsFavorite")
                        }
                    }
                    
                    // Sync the Liked playlist with the favorite IDs
                    // This is needed because the service can't add songs to the playlist (only has IDs, not Song objects)
                    syncLikedPlaylistWithFavorites(newFavorites)
                    
                    // Also refresh playlists from AppSettings to sync the Liked playlist
                    refreshPlaylistsFromSettings()
                } else {
                    _favoriteSongs.value = emptySet()
                    _isFavorite.value = false
                    refreshPlaylistsFromSettings()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh favorite songs", e)
            }
        }
    }
    
    // Sync the Liked playlist with the current favorite song IDs
    private suspend fun syncLikedPlaylistWithFavorites(favoriteIds: Set<String>) {
        try {
            if (!isPlaylistsLoaded) {
                Log.w(TAG, "Skipping syncLikedPlaylistWithFavorites — playlists have not finished loading")
                return
            }

            // Sync direction is favorites -> playlist only. Songs that were unliked (disliked)
            // must never be re-added to favorites just because they are still listed in the
            // Liked playlist — that would make the like reappear right after disliking.
            val finalFavoriteIds = favoriteIds

            // Update the Liked playlist directory without losing existing custom song order
            _playlists.value = _playlists.value.map { playlist ->
                if (playlist.id == "1") {
                    // 1. Keep existing songs that are still favorites (preserves custom order)
                    val existingSongsToKeep = playlist.songs.filter {
                        it.id.toStableCatalogSongId() in favoriteIds
                    }
                    
                    // 2. Find any new favorites that are NOT in the playlist yet
                    val existingIds = existingSongsToKeep.map { it.id.toStableCatalogSongId() }.toSet()
                    val newFavoriteIds = finalFavoriteIds.filter { !existingIds.contains(it) }
                    
                    // 3. Find the Song objects for these new favorites from _songs list OR room DB
                    val allSongsMap = _songs.value.associateBy { it.id }
                    val newSongs = newFavoriteIds.mapNotNull { favoriteId ->
                        allSongsMap[favoriteId] ?: run {
                            repository.songDao.getSongById(favoriteId)?.toSong()
                        }
                    }
                    
                    // 4. Append them
                    val finalSongsList = existingSongsToKeep + newSongs
                    
                    Log.d(TAG, "Syncing Liked playlist: kept ${existingSongsToKeep.size}, added ${newSongs.size} new songs")
                    
                    playlist.copy(
                        songs = finalSongsList,
                        dateModified = System.currentTimeMillis()
                    )
                } else {
                    playlist
                }
            }
            
            // Save updated playlists to appSettings
            savePlaylists()
            
            Log.d(TAG, "Liked playlist synced successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Liked playlist with favorites", e)
        }
    }
    
    // Helper to refresh playlists from Room database
    private suspend fun refreshPlaylistsFromSettings() {
        try {
            loadSavedPlaylists()
            Log.d(TAG, "Refreshed playlists from database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh playlists from database", e)
        }
    }

    // Main music data
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    
    // LRU cache for file paths to avoid repeated ContentResolver queries
    private val pathCache = LruCache<String, String>(1000)
    
    // Filter cache to optimize repeated filtering operations
    private val filterCache = mutableMapOf<String, Boolean>()
    private var lastFilterSettings = ""
    
    private data class FilterSettings(
        val mode: MediaScanMode,
        val blacklistedIds: List<String>,
        val blacklistedFolders: List<String>,
        val whitelistedIds: List<String>,
        val whitelistedFolders: List<String>
    )

    private val filterSettingsFlow = combine(
        appSettings.mediaScanMode,
        appSettings.blacklistedSongs,
        appSettings.blacklistedFolders,
        appSettings.whitelistedSongs,
        appSettings.whitelistedFolders
    ) { mode, blacklistedIds, blacklistedFolders, whitelistedIds, whitelistedFolders ->
        FilterSettings(mode, blacklistedIds, blacklistedFolders, whitelistedIds, whitelistedFolders)
    }.distinctUntilChanged()

    // Filtered songs excluding blacklisted ones and including only whitelisted ones (both songs and folders)
    val filteredSongs: StateFlow<List<Song>> = combine(
        _songs,
        filterSettingsFlow
    ) { songs: List<Song>, settings: FilterSettings ->
        filterSongsAsync(songs, settings.mode, settings.blacklistedIds, settings.blacklistedFolders, settings.whitelistedIds, settings.whitelistedFolders)
    }.distinctUntilChanged().conflate().flowOn(Dispatchers.IO).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    // Paginated streams for Paging 3 Compose views
    val paginatedSongs: kotlinx.coroutines.flow.Flow<PagingData<Song>> = repository.getPaginatedSongs()
        .cachedIn(viewModelScope)

    val paginatedArtists: kotlinx.coroutines.flow.Flow<PagingData<Artist>> = repository.getPaginatedArtists(appSettings.groupByAlbumArtist.value)
        .cachedIn(viewModelScope)

    fun playPaginatedSong(song: Song, fallbackIndex: Int = 0) {
        val allCurrentSongs = filteredSongs.value.ifEmpty { _songs.value }
        if (allCurrentSongs.isNotEmpty()) {
            val idx = allCurrentSongs.indexOfFirst { it.id == song.id }
            val playIdx = if (idx >= 0) idx else fallbackIndex.coerceIn(0, allCurrentSongs.size - 1)
            playSong(allCurrentSongs[playIdx])
        } else {
            playSong(song)
        }
    }
    
    private suspend fun filterSongsAsync(
        songs: List<Song>,
        mediaScanMode: MediaScanMode,
        blacklistedIds: List<String>, 
        blacklistedFolders: List<String>,
        whitelistedIds: List<String>,
        whitelistedFolders: List<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val hasBlacklist = blacklistedIds.isNotEmpty() || blacklistedFolders.isNotEmpty()
        val hasWhitelist = whitelistedIds.isNotEmpty() || whitelistedFolders.isNotEmpty()
        
        // Determine which filtering mode to use based on mediaScanMode setting
        val useBlacklistMode = mediaScanMode == MediaScanMode.BLACKLIST
        val useWhitelistMode = mediaScanMode == MediaScanMode.WHITELIST
        
        // If no filters are active for the current mode, return all songs
        if (useBlacklistMode && !hasBlacklist) {
            return@withContext songs
        }
        if (useWhitelistMode && !hasWhitelist) {
            return@withContext songs
        }
        
        // Check if filter settings changed (to clear cache)
        val currentSettings = buildString {
            append(mediaScanMode.value)
            append('|')
            append(blacklistedIds.sorted().joinToString(","))
            append('|')
            append(blacklistedFolders.sorted().joinToString(","))
            append('|')
            append(whitelistedIds.sorted().joinToString(","))
            append('|')
            append(whitelistedFolders.sorted().joinToString(","))
        }
        if (currentSettings != lastFilterSettings) {
            filterCache.clear()
            lastFilterSettings = currentSettings
        }
        
        val startTime = System.currentTimeMillis()
        val result = ArrayList<Song>(songs.size)

        // Pre-normalize target folders once for O(N+M) efficiency instead of O(N*M)
        val normBlacklistedFolders = if (useBlacklistMode && blacklistedFolders.isNotEmpty()) {
            blacklistedFolders.map { normalizeStoragePath(it) }
        } else {
            emptyList()
        }
        val normWhitelistedFolders = if (useWhitelistMode && whitelistedFolders.isNotEmpty()) {
            whitelistedFolders.map { normalizeStoragePath(it) }
        } else {
            emptyList()
        }

        val blacklistedIdsSet = if (useBlacklistMode && blacklistedIds.isNotEmpty()) blacklistedIds.toSet() else emptySet()
        val whitelistedIdsSet = if (useWhitelistMode && whitelistedIds.isNotEmpty()) whitelistedIds.toSet() else emptySet()
        
        // Process in batches to allow yielding
        val batchSize = 100
        var processed = 0
        
        for (song in songs) {
            val cacheKey = "${song.id}_${song.path}_${mediaScanMode.value}"
            val cachedResult = filterCache[cacheKey]
            if (cachedResult != null) {
                if (cachedResult) result.add(song)
                processed++
                continue
            }
            
            var shouldInclude = false
            
            // In BLACKLIST mode: exclude blacklisted songs/folders, include everything else
            if (useBlacklistMode && hasBlacklist) {
                // Check if song is individually blacklisted
                if (blacklistedIdsSet.contains(song.id)) {
                    shouldInclude = false
                } else if (normBlacklistedFolders.isNotEmpty()) {
                    // Check if song is in a blacklisted folder
                    val songPath = song.path ?: if (song.uri.scheme == "file") song.uri.path else getPathFromUriCached(song.uri)
                    shouldInclude = songPath == null || !isNormalizedPathInFolders(normalizeStoragePath(songPath), normBlacklistedFolders)
                } else {
                    shouldInclude = true
                }
            }
            // In WHITELIST mode: include ONLY whitelisted songs/folders, exclude everything else
            else if (useWhitelistMode && hasWhitelist) {
                // Check if song ID is individually whitelisted
                if (whitelistedIdsSet.contains(song.id)) {
                    shouldInclude = true
                } else if (normWhitelistedFolders.isNotEmpty()) {
                    // Check if song is in a whitelisted folder
                    val songPath = song.path ?: if (song.uri.scheme == "file") song.uri.path else getPathFromUriCached(song.uri)
                    if (songPath == null) {
                        Log.w(TAG, "Whitelist: could not resolve path for song '${song.title}' (${song.uri}), excluding it")
                    }
                    shouldInclude = songPath != null && isNormalizedPathInFolders(normalizeStoragePath(songPath), normWhitelistedFolders)
                }
            }
            
            // Cache result
            filterCache[cacheKey] = shouldInclude
            if (shouldInclude) {
                result.add(song)
            }
            
            processed++
            
            // Yield control periodically to prevent ANR
            if (processed % batchSize == 0) {
                yield()
            }
            
            // Limit cache size
            if (filterCache.size > 10000) {
                filterCache.clear()
                Log.d(TAG, "Filter cache cleared due to size limit")
            }
        }
        
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Filtered ${songs.size} songs to ${result.size} in ${endTime - startTime}ms (mode: ${mediaScanMode.value}, cached: ${filterCache.size})")
        
        result
    }
    
    private fun normalizeStoragePath(path: String): String {
        var normalized = path.trim().replace('\\', '/')
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/")
        }
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        // Resolve legacy symlink aliases (e.g. /sdcard, /storage/self/primary) to the
        // canonical external storage path using OS-level symlink resolution
        val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
        if (normalized.startsWith("/") && !normalized.startsWith(externalStorage)) {
            normalized = try {
                val candidate = File(normalized).canonicalPath
                if (candidate.startsWith(externalStorage)) candidate else normalized
            } catch (_: java.io.IOException) {
                normalized
            }
        }
        return normalized
    }

    private fun isNormalizedPathInFolders(normChild: String, normFolders: List<String>): Boolean {
        return normFolders.any { normParent ->
            normParent.equals(normChild, ignoreCase = true) ||
                normChild.startsWith(if (normParent.endsWith('/')) normParent else "$normParent/", ignoreCase = true)
        }
    }

    private fun isFolderSubdirectoryOrEqual(parent: String, child: String): Boolean {
        val normParent = normalizeStoragePath(parent)
        val normChild = normalizeStoragePath(child)
        return isNormalizedPathInFolders(normChild, listOf(normParent))
    }

    private fun isPathBlacklisted(songPath: String, blacklistedFolders: List<String>): Boolean {
        val normFolders = blacklistedFolders.map { normalizeStoragePath(it) }
        return isNormalizedPathInFolders(normalizeStoragePath(songPath), normFolders)
    }
    
    private fun isPathWhitelisted(songPath: String, whitelistedFolders: List<String>): Boolean {
        val normFolders = whitelistedFolders.map { normalizeStoragePath(it) }
        return isNormalizedPathInFolders(normalizeStoragePath(songPath), normFolders)
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums
        .map { list -> list.distinctBy { it.id } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _folderTree = MutableStateFlow<FolderNode?>(null)
    val folderTree: StateFlow<FolderNode?> = _folderTree.asStateFlow()
    
    private fun Song.rawAlbumGroupKey(): String =
        (albumArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: artist.trim().takeIf { it.isNotBlank() }
            ?: "Unknown Artist").lowercase(java.util.Locale.ROOT)

    // Filtered albums excluding albums with all songs blacklisted
    val filteredAlbums: StateFlow<List<Album>> = combine(
        _albums,
        filteredSongs
    ) { albums, filteredSongs ->
        if (albums.isEmpty() || filteredSongs.isEmpty()) {
            return@combine emptyList()
        }
        val albumNameToArtistGroups = java.util.HashMap<String, MutableList<Pair<String, List<Song>>>>()
        val rawGroups = filteredSongs.groupBy { song ->
            song.album.trim().lowercase(java.util.Locale.ROOT) to song.rawAlbumGroupKey()
        }
        for ((keyPair, songList) in rawGroups) {
            val (albumNameKey, artistKey) = keyPair
            albumNameToArtistGroups.getOrPut(albumNameKey) { java.util.ArrayList() }.add(artistKey to songList)
        }

        val result = java.util.ArrayList<Album>(albums.size)
        for (album in albums) {
            val albumNameKey = album.title.trim().lowercase(java.util.Locale.ROOT)
            val groupsForAlbum = albumNameToArtistGroups[albumNameKey] ?: continue
            val albumSongs = if (groupsForAlbum.size == 1) {
                groupsForAlbum[0].second
            } else {
                val artistKey = album.artist.trim().lowercase(java.util.Locale.ROOT)
                groupsForAlbum.firstOrNull { it.first == artistKey }?.second
                    ?: groupsForAlbum.flatMap { it.second }
            }
            if (albumSongs.isNotEmpty()) {
                val sortedSongs = albumSongs.sortedWith(
                    compareBy<Song> { it.discNumber }
                        .thenBy { it.trackNumber }
                        .thenBy { it.title.lowercase(java.util.Locale.ROOT) }
                )
                result.add(
                    album.copy(
                        songs = sortedSongs,
                        numberOfSongs = sortedSongs.size
                    )
                )
            }
        }
        result.distinctBy { it.id }
    }.distinctUntilChanged().conflate().flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()
    
    // Filtered artists excluding artists with all songs blacklisted
    val filteredArtists: StateFlow<List<Artist>> = combine(
        _artists,
        filteredSongs,
        appSettings.groupByAlbumArtist,
        appSettings.artistSeparatorEnabled,
        appSettings.artistSeparatorDelimiters
    ) { artists, filteredSongs, groupByAlbumArtist, artistSeparatorEnabled, artistSeparatorDelimiters ->
        val charDelimiters = if (artistSeparatorEnabled) {
            io.github.cluno1.sonorus.util.ArtistSeparator.parseDelimiters(artistSeparatorDelimiters)
        } else {
            emptyList()
        }

        // Map artist name (lowercase) -> songs of that artist in the filtered set
        val artistSongsMap = java.util.HashMap<String, MutableList<Song>>()
        val artistAlbumsMap = java.util.HashMap<String, java.util.HashSet<String>>()

        for (song in filteredSongs) {
            val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
            val artistsList = if (groupByAlbumArtist) {
                if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                    repository.splitArtistNames(explicitAlbumArtist, charDelimiters)
                } else {
                    repository.splitArtistNames(song.artist, charDelimiters)
                }
            } else {
                repository.splitArtistNames(song.artist, charDelimiters)
            }
            for (artistName in artistsList) {
                val key = artistName.lowercase()
                artistSongsMap.getOrPut(key) { mutableListOf() }.add(song)
                
                val albumKey = song.album.trim().lowercase()
                artistAlbumsMap.getOrPut(key) { java.util.HashSet() }.add(albumKey)
            }
        }

        artists.mapNotNull { artist ->
            val key = artist.name.lowercase()
            val songsOfArtist = artistSongsMap[key]
            if (songsOfArtist != null) {
                val albumsOfArtist = artistAlbumsMap[key]?.size ?: 0
                artist.copy(
                    songs = songsOfArtist,
                    numberOfTracks = songsOfArtist.size,
                    numberOfAlbums = albumsOfArtist
                )
            } else {
                null
            }
        }
    }.distinctUntilChanged().conflate().flowOn(Dispatchers.Default).stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    @Volatile
    private var isPlaylistsLoaded = false
    private val playlistLock = Mutex()

    // Use audioDeviceManager for locations instead of the mock data
    val locations = audioDeviceManager.availableDevices
    val currentDevice = audioDeviceManager.currentDevice

    // Recently played songs
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // Player state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val playbackControlStateMachine = PlaybackControlStateMachine(
        scope = viewModelScope,
        minimumLoadingDurationMs = 400L,
        elapsedRealtimeMs = SystemClock::elapsedRealtime,
    )
    val playbackControlUiState: StateFlow<PlaybackControlUiState> = playbackControlStateMachine.state

    private val _showCorruptionDialog = MutableStateFlow(false)
    val showCorruptionDialog: StateFlow<Boolean> = _showCorruptionDialog.asStateFlow()

    private val _corruptedTrackName = MutableStateFlow("")
    val corruptedTrackName: StateFlow<String> = _corruptedTrackName.asStateFlow()

    private val _corruptedTrackMessage = MutableStateFlow("")
    val corruptedTrackMessage: StateFlow<String> = _corruptedTrackMessage.asStateFlow()

    fun dismissCorruptionDialog() {
        _showCorruptionDialog.value = false
        _corruptedTrackMessage.value = ""
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    private val _catalogNowPlaying = MutableStateFlow<RhythmNowPlayingItem?>(null)
    val catalogNowPlaying: StateFlow<RhythmNowPlayingItem?> = _catalogNowPlaying.asStateFlow()
    private val _catalogLyricsSyncState = MutableStateFlow(CatalogLyricsSyncState())
    val catalogLyricsSyncState: StateFlow<CatalogLyricsSyncState> =
        _catalogLyricsSyncState.asStateFlow()
    private val _catalogQueue = MutableStateFlow<List<RhythmQueueEntry>>(emptyList())
    val catalogQueue: StateFlow<List<RhythmQueueEntry>> = _catalogQueue.asStateFlow()

    private val _currentQueue = MutableStateFlow(Queue(emptyList(), -1))
    val currentQueue: StateFlow<Queue> = _currentQueue.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Media loading and seeking states
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    // Volume control
    private val _volume = MutableStateFlow(if (appSettings.useSystemVolume.value) 1.0f else appSettings.appVolume.value)
    val volume: StateFlow<Float> = _volume.asStateFlow()
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    
    private var _previousVolume = if (appSettings.useSystemVolume.value) 1.0f else appSettings.appVolume.value

    private val _showZeroVolumePauseDialog = MutableStateFlow(false)
    val showZeroVolumePauseDialog = _showZeroVolumePauseDialog.asStateFlow()
    private var pausedByZeroVolume: Boolean = false

    fun triggerZeroVolumePauseDialog() {
        _showZeroVolumePauseDialog.value = true
    }

    fun dismissZeroVolumePauseDialog() {
        _showZeroVolumePauseDialog.value = false
        pausedByZeroVolume = false
    }

    // New player state for additional functionality
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongs: StateFlow<Set<String>> = _favoriteSongs.asStateFlow()
    
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val controllerConnectInFlight = AtomicBoolean(false)
    private var lastControllerConnectAttemptMs = 0L
    
    // Audio session ID for equalizer integration
    val audioSessionId: Int
        get() = mediaController?.audioSessionId ?: AudioEffect.ERROR_BAD_VALUE
    
    // For tracking progress 
    private var progressUpdateJob: Job? = null
    private var playbackWatchdogJob: Job? = null

    // Selected song for adding to playlist
    private val _selectedSongForPlaylist = MutableStateFlow<Song?>(null)
    val selectedSongForPlaylist: StateFlow<Song?> = _selectedSongForPlaylist.asStateFlow()

    // Target playlist for adding songs
    private val _targetPlaylistId = MutableStateFlow<String?>(null)
    val targetPlaylistId: StateFlow<String?> = _targetPlaylistId.asStateFlow()
    
    // Pending lyrics write request for Android 11+ permission flow
    val pendingLyricsWriteRequest: StateFlow<PendingLyricsWriteRequest?>
        get() = metadataManagerHelper.pendingLyricsWriteRequest

    // Pending write request for metadata editing (Android 11+ permission flow)
    val pendingWriteRequest: StateFlow<PendingWriteRequest?>
        get() = metadataManagerHelper.pendingWriteRequest

    // Pending batch write request for metadata editing (Android 11+ permission flow)
    val pendingBatchWriteRequest: StateFlow<PendingBatchWriteRequest?>
        get() = metadataManagerHelper.pendingBatchWriteRequest

    // Pending delete request for Android 10/11+ permission flow
    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest: StateFlow<PendingDeleteRequest?> = _pendingDeleteRequest.asStateFlow()

    // Sort library functionality - Load saved sort order from AppSettings
    private val _sortOrder = MutableStateFlow(
        try {
            SortOrder.valueOf(appSettings.songsSortOrder.value)
        } catch (e: Exception) {
            SortOrder.TITLE_ASC
        }
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // User preferences and statistics
    private val _listeningTime = MutableStateFlow(appSettings.listeningTime.value)
    val listeningTime: StateFlow<Long> = _listeningTime.asStateFlow()
    
    private val _songsPlayed = MutableStateFlow(appSettings.songsPlayed.value)
    val songsPlayed: StateFlow<Int> = _songsPlayed.asStateFlow()
    
    private val _uniqueArtists = MutableStateFlow(appSettings.uniqueArtists.value)
    val uniqueArtists: StateFlow<Int> = _uniqueArtists.asStateFlow()
    
    private val _genrePreferences = MutableStateFlow<Map<String, Int>>(appSettings.genrePreferences.value)
    val genrePreferences: StateFlow<Map<String, Int>> = _genrePreferences.asStateFlow()
    
    private val _timeBasedPreferences = MutableStateFlow<Map<Int, List<String>>>(appSettings.timeBasedPreferences.value)
    val timeBasedPreferences: StateFlow<Map<Int, List<String>>> = _timeBasedPreferences.asStateFlow()

    // Song play counts
    private val _songPlayCounts = MutableStateFlow<Map<String, Int>>(appSettings.songPlayCounts.value)
    val songPlayCounts: StateFlow<Map<String, Int>> = _songPlayCounts.asStateFlow()

    // Add initialization state tracking
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // Media scan loader state for refresh operations
    private val _isMediaScanning = MutableStateFlow(false)
    val isMediaScanning: StateFlow<Boolean> = _isMediaScanning.asStateFlow()

    // Pull-to-refresh state (always set during refreshLibrary, independent of full-screen loader)
    private val _isLibraryRefreshing = MutableStateFlow(false)
    val isLibraryRefreshing: StateFlow<Boolean> = _isLibraryRefreshing.asStateFlow()

    // Genre detection state
    private val _isGenreDetectionComplete = MutableStateFlow(false)
    val isGenreDetectionComplete: StateFlow<Boolean> = _isGenreDetectionComplete.asStateFlow()
    private val _isGenreDetectionRunning = MutableStateFlow(false)
    val isGenreDetectionRunning: StateFlow<Boolean> = _isGenreDetectionRunning.asStateFlow()
    
    // Artwork fetching state
    private val _isFetchingArtwork = MutableStateFlow(false)
    val isFetchingArtwork: StateFlow<Boolean> = _isFetchingArtwork.asStateFlow()
    
    // Audio metadata extraction state
    private val _isExtractingMetadata = MutableStateFlow(false)
    val isExtractingMetadata: StateFlow<Boolean> = _isExtractingMetadata.asStateFlow()

    // Combined background processing state - true if ANY background task is running
    val isBackgroundProcessing: StateFlow<Boolean> = combine(
        isMediaScanning,
        _isGenreDetectionRunning,
        _isFetchingArtwork,
        _isExtractingMetadata
    ) { mediaScanning, genreDetection, artworkFetching, metadataExtraction ->
        mediaScanning || genreDetection || artworkFetching || metadataExtraction
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Queue operation state
    private val _queueOperationError = MutableStateFlow<String?>(null)
    val queueOperationError: StateFlow<String?> = _queueOperationError.asStateFlow()
    
    // Queue action dialog state
    data class QueueActionRequest(
        val song: Song,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val _queueActionRequest = MutableStateFlow<QueueActionRequest?>(null)
    val queueActionRequest: StateFlow<QueueActionRequest?> = _queueActionRequest.asStateFlow()

    // List queue action dialog state (for Play All / context lists)
    data class QueueListActionRequest(
        val songs: List<Song>,
        val startIndex: Int,
        val sourceLabel: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val _queueListActionRequest = MutableStateFlow<QueueListActionRequest?>(null)
    val queueListActionRequest: StateFlow<QueueListActionRequest?> = _queueListActionRequest.asStateFlow()
    
    // Clear queue operation error
    fun clearQueueOperationError() {
        _queueOperationError.value = null
    }
    
    // Dismiss queue action dialog
    fun dismissQueueActionDialog() {
        _queueActionRequest.value = null
    }

    // Dismiss list queue action dialog
    fun dismissQueueListActionDialog() {
        _queueListActionRequest.value = null
    }
    
    // Handle queue action choice from dialog
    fun handleQueueActionChoice(song: Song, clearQueue: Boolean) {
        _queueActionRequest.value = null
        if (clearQueue) {
            // Replace queue with contextual queue or single song
            val shouldAutoAddToQueue = autoAddToQueue.value
            if (shouldAutoAddToQueue) {
                val contextualQueue = createContextualQueue(song)
                if (contextualQueue.size > 1) {
                    playQueue(contextualQueue, enableShuffle = false)
                    return
                }
            }
            playQueue(listOf(song), enableShuffle = false)
        } else {
            // Add to existing queue and play it
            val currentQueueSongs = _currentQueue.value.songs.toMutableList()
            mediaController?.let { controller ->
                val controllerCurrentIndex = controller.currentMediaItemIndex
                val insertIndex = if (
                    controller.mediaItemCount <= 0 ||
                    controllerCurrentIndex == C.INDEX_UNSET
                ) {
                    0
                } else {
                    (controllerCurrentIndex + 1).coerceAtMost(controller.mediaItemCount)
                }

                currentQueueSongs.add(insertIndex, song)

                val mediaItem = song.toMediaItem()
                controller.addMediaItem(insertIndex, mediaItem)

                if (controller.shuffleModeEnabled) {
                    viewModelScope.launch {
                        delay(50)
                        syncQueueWithMediaController()
                    }
                } else {
                    _currentQueue.value = Queue(currentQueueSongs, insertIndex)
                }

                controller.seekToDefaultPosition(insertIndex)
                controller.prepare()
                if (!canStartPlayback("handleQueueActionChoice")) return@let
                controller.play()
                
                _currentSong.value = song
                _isPlaying.value = true
                _isFavorite.value = _favoriteSongs.value.contains(song.id)
                startProgressUpdates()
                
                Log.d(TAG, "Added song to queue at position $insertIndex and started playing")
            }
        }
    }

    fun handleQueueListActionChoice(action: String) {
        val request = _queueListActionRequest.value ?: return
        _queueListActionRequest.value = null
        applyListQueueAction(
            songs = request.songs,
            startIndex = request.startIndex,
            action = action
        )
    }

    enum class SortOrder {
        TITLE_ASC,
        TITLE_DESC,
        ARTIST_ASC,
        ARTIST_DESC,
        ALBUM_ASC,
        ALBUM_DESC,
        YEAR_ASC,
        YEAR_DESC,
        DATE_ADDED_ASC,
        DATE_ADDED_DESC,
        DATE_MODIFIED_ASC,
        DATE_MODIFIED_DESC
    }
    
    enum class SleepAction {
        PAUSE, STOP, FADE_OUT
    }

    init {
        Log.d(TAG, "Initializing MusicViewModel")
        if (deviceLibraryEnabled) startLibrarySetupCompletionMonitor()
        
        // Single coroutine for main initialization to ensure proper ordering
        viewModelScope.launch {
            try {
                initializeViewModelSafely()
            } catch (e: Exception) {
                Log.e(TAG, "Critical error during ViewModel initialization", e)
                handleInitializationFailure(e)
            }
        }

        // Keep artwork-derived colors tied to the actual current song rather than to a single
        // Media3 transition callback. This also refreshes immediately when ALBUM_ART is selected
        // while a song is already active, and collectLatest prevents a slow previous artwork
        // request from overwriting the palette after a quick song change.
        viewModelScope.launch {
            combine(
                appSettings.colorSource,
                currentSong,
            ) { colorSource, song ->
                AlbumArtColorRequest(
                    enabled = colorSource == COLOR_SOURCE_ALBUM_ART,
                    songId = song?.id,
                    songTitle = song?.title,
                    artworkUri = song?.artworkUri,
                )
            }
                .distinctUntilChanged()
                .collectLatest { request ->
                    if (request.enabled) {
                        refreshAlbumArtColors(request)
                    }
                }
        }
        
        // Dynamically update library artwork when preferSongArtwork or isLosslessArtworkActive changes.
        viewModelScope.launch {
            var previousState = Pair(
                appSettings.preferSongArtwork.value,
                appSettings.isLosslessArtworkActive.value
            )

            combine(
                appSettings.preferSongArtwork,
                appSettings.isLosslessArtworkActive
            ) { preferSongArtwork, losslessArtwork ->
                Pair(preferSongArtwork, losslessArtwork)
            }.collect { newState ->
                if (!deviceLibraryEnabled || !_isInitialized.value || newState == previousState) {
                    return@collect
                }
                
                val preferSongArtworkChanged = newState.first != previousState.first
                val losslessChanged = newState.second != previousState.second
                previousState = newState

                Log.d(TAG, "Artwork settings changed dynamically: preferSongArtwork=${newState.first}, losslessArtwork=${newState.second}")
                
                try {
                    // Reset extraction completed flag to allow background extraction for the new format
                    if (preferSongArtworkChanged || (newState.first && losslessChanged)) {
                        appSettings.setEmbeddedArtworkExtractionCompleted(false)
                    }

                    // Invalidate caches and reload
                    withContext(Dispatchers.IO) {
                        repository.clearInMemoryCaches()
                        val freshSongs = repository.loadSongs()
                        withContext(Dispatchers.Main) {
                            _songs.value = freshSongs
                        }
                        val freshAlbums = repository.loadAlbums()
                        val freshArtists = repository.loadArtists()
                        withContext(Dispatchers.Main) {
                            _albums.value = freshAlbums
                            _artists.value = freshArtists
                        }
                    }

                    // Trigger background extraction if preferSongArtwork is enabled
                    val preferSongArtwork = newState.first
                    val losslessArtwork = newState.second
                    if (preferSongArtwork) {
                        launch(Dispatchers.IO) {
                            try {
                                val currentSongs = _songs.value
                                val songsNeedingExtraction = currentSongs.count { song ->
                                    song.artworkUri == null ||
                                    !repository.isEmbeddedArtworkCacheUri(song.artworkUri) ||
                                    !repository.hasArtworkMatchingLossless(song, losslessArtwork)
                                }
                                if (songsNeedingExtraction > 0) {
                                    Log.d(TAG, "Starting dynamic background embedded artwork extraction for $songsNeedingExtraction songs")
                                    val updated = repository.extractEmbeddedArtworkForSongs(currentSongs, losslessArtwork)
                                    withContext(Dispatchers.Main) {
                                        _songs.value = updated
                                    }
                                    repository.updateAndPersistSongs(updated)
                                }
                                appSettings.setEmbeddedArtworkExtractionLosslessStatus(losslessArtwork)
                                appSettings.setEmbeddedArtworkExtractionCompleted(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in dynamic background embedded artwork extraction", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating library artwork on settings change", e)
                }
            }
        }

        // Listen for blacklist/whitelist or scan mode changes and refresh library & playlists accordingly
        // This runs independently but only acts after initialization completes
        viewModelScope.launch {
            combine(
                appSettings.blacklistedSongs,
                appSettings.blacklistedFolders,
                appSettings.whitelistedSongs,
                appSettings.whitelistedFolders,
                appSettings.mediaScanMode
            ) { blacklistedSongs, blacklistedFolders, whitelistedSongs, whitelistedFolders, mediaScanMode ->
                // Trigger when any filter changes
                Unit
            }.collect {
                // Wait for initialization to complete before refreshing
                if (deviceLibraryEnabled && _isInitialized.value) {
                    Log.d(TAG, "Blacklist/Whitelist or scan mode changed, refreshing library & playlists")
                    refreshLibrary(showMediaScanLoader = false)
                    removeBlacklistedSongsFromQueue()
                }
            }
        }
        
        // Reload artists when grouping mode or track-artist separator settings change.
        viewModelScope.launch {
            var previousState = Triple(
                appSettings.groupByAlbumArtist.value,
                appSettings.artistSeparatorEnabled.value,
                appSettings.artistSeparatorDelimiters.value
            )

            combine(
                appSettings.groupByAlbumArtist,
                appSettings.artistSeparatorEnabled,
                appSettings.artistSeparatorDelimiters
            ) { groupByAlbumArtist, artistSeparatorEnabled, artistSeparatorDelimiters ->
                Triple(groupByAlbumArtist, artistSeparatorEnabled, artistSeparatorDelimiters)
            }.collect { newState ->
                if (!deviceLibraryEnabled || !_isInitialized.value || newState == previousState) {
                    return@collect
                }

                val groupChanged = newState.first != previousState.first
                val separatorChanged =
                    newState.second != previousState.second || newState.third != previousState.third
                previousState = newState

                val shouldReloadArtists = groupChanged || separatorChanged
                if (!shouldReloadArtists) {
                    return@collect
                }

                val reason = if (groupChanged) {
                    "groupByAlbumArtist changed to ${newState.first}"
                } else {
                    "artist separator settings changed (enabled=${newState.second}, delimiters='${newState.third}')"
                }

                Log.d(TAG, "$reason, reloading artists")
                try {
                    val freshArtists = withContext(Dispatchers.IO) {
                        repository.loadArtists()
                    }
                    _artists.value = freshArtists
                    Log.d(TAG, "Reloaded ${freshArtists.size} artists after artist settings update")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reloading artists after artist settings update", e)
                }
            }
        }
        
        // Register broadcast receiver for favorite changes from service and widget
        val filter = IntentFilter().apply {
            addAction("io.github.cluno1.sonorus.action.FAVORITE_CHANGED")
            addAction("io.github.cluno1.sonorus.action.WIDGET_TOGGLE_FAVORITE")
            addAction(MediaPlaybackService.ACTION_SHUFFLE_STATE_CHANGED)
            addAction(MediaPlaybackService.BROADCAST_SLEEP_TIMER_STATUS)
        }
        
        // Resume playback on audio device reconnection (e.g., Bluetooth headphones reconnected)
        viewModelScope.launch {
            audioDeviceManager.deviceReconnected.collect { deviceName ->
                if (
                    appSettings.resumeOnDeviceReconnect.value &&
                    !_isPlaying.value &&
                    _currentSong.value != null &&
                    !isRhythmGuardTimeoutActive()
                ) {
                    Log.d(TAG, "Audio device reconnected ($deviceName), resuming playback")
                    mediaController?.let { controller ->
                        if (!canStartPlayback("deviceReconnected.resume")) return@let
                        controller.play()
                        _isPlaying.value = true
                        startProgressUpdates()
                    }
                }
            }
        }

        viewModelScope.launch {
            appSettings.rhythmGuardTimeoutUntilMs.collect { timeoutUntilMs ->
                val timeoutActive = timeoutUntilMs > System.currentTimeMillis()
                if (timeoutActive) {
                    wasRhythmGuardTimeoutActive = true
                    enforceRhythmGuardTimeout(reason = "timeout lock updated")
                } else {
                    if (wasRhythmGuardTimeoutActive) {
                        wasRhythmGuardTimeoutActive = false
                    }
                    resumePlaybackAfterRhythmGuardTimeoutIfNeeded(source = "timeout flow")
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            getApplication<Application>(),
            favoriteChangeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private suspend fun initializeViewModelSafely() {
        Log.d(TAG, "Starting safe data initialization")
        val initStartTime = System.currentTimeMillis()
        
        // Step 1: Load core music data with PARALLEL loading for better performance
        val initializationResults = if (!deviceLibraryEnabled) {
            _songs.value = emptyList()
            _albums.value = emptyList()
            _artists.value = emptyList()
            _folderTree.value = null
            InitializationResult(true)
        } else {
            initializeCoreDataParallel()
        }
        if (!initializationResults.success) {
            throw Exception("Failed to load core music data: ${initializationResults.error}")
        }
        
        Log.d(TAG, "Loaded ${_songs.value.size} songs, ${_albums.value.size} albums, ${_artists.value.size} artists in ${System.currentTimeMillis() - initStartTime}ms")

        // Step 2: Load settings and persisted data (can run in parallel with some tasks)
        val settingsLoaded = loadAllSettings()
        if (!settingsLoaded) {
            Log.w(TAG, "Some settings failed to load, continuing with defaults")
        }

        // Step 3: Initialize media controller (non-blocking, will connect async)
        // Playback/session setup must happen on every launch, even when metadata work is already complete.
        try {
            initializeController()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media controller", e)
        }

        // Step 4: Initialize from persistence (needs songs to be loaded)
        if (deviceLibraryEnabled) {
            try {
                initializeFromPersistence()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing from persistence", e)
            }
        }

        // Step 5: Start progress updates
        try {
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting progress updates", e)
        }
        
        // Step 6: Wait for filteredSongs to be ready and populate playlists
        if (deviceLibraryEnabled) populateDefaultPlaylistsSafely()

        // Step 7: Initialize queue state
        if (deviceLibraryEnabled) initializeQueueState()

        // Step 8: Mark as initialized BEFORE starting background tasks
        // This allows UI to render immediately
        _isInitialized.value = true
        val initTime = System.currentTimeMillis() - initStartTime
        Log.d(TAG, "Core initialization complete in ${initTime}ms")
        
        // Step 9: Start non-critical background tasks AFTER marking initialized
        // This defers heavy work until after UI is responsive
        if (deviceLibraryEnabled) startBackgroundTasksDeferred()
    }
    
    private data class InitializationResult(val success: Boolean, val error: String? = null)
    
    /**
     * Load core data efficiently. Songs are loaded first as they populate the 
     * Room database relationships required for Albums and Artists on a cold start.
     */
    private suspend fun initializeCoreDataParallel(): InitializationResult {
        return try {
            // 1. Load songs first. This takes the longest on cold start as it scans
            // MediaStore and populates the Room database.
            val songs = repository.loadSongs(
                allowedFormats = allowedFormats.value,
                minimumBitrate = minimumBitrate.value,
                minimumDuration = minimumDuration.value
            )
            
            // 2. Load albums and artists concurrently, now that the Room database 
            // has been populated by the songs load.
            val albumsDeferred = viewModelScope.async(Dispatchers.IO) {
                repository.loadAlbums()
            }
            val artistsDeferred = viewModelScope.async(Dispatchers.IO) {
                repository.loadArtists()
            }
            
            // Await all results
            val albums = albumsDeferred.await()
            val artists = artistsDeferred.await()
            
            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                Log.w(TAG, "No media found, but this might be normal on first run")
            }
            
            // Update state atomically
            _songs.value = songs
            _albums.value = albums
            _artists.value = artists
            viewModelScope.launch(Dispatchers.IO) {
                _folderTree.value = repository.buildFolderTree(songs)
            }
            
            InitializationResult(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading core data", e)
            InitializationResult(false, e.message)
        }
    }
    
    private suspend fun loadAllSettings(): Boolean {
        var allSuccess = true
        
        try {
            loadSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app settings", e)
            allSuccess = false
        }
        
        try {
            loadSearchHistory()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading search history", e)
            allSuccess = false
        }
        
        try {
            loadSavedPlaylistsInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved playlists", e)
            allSuccess = false
        }
        
        return allSuccess
    }
    
    private suspend fun populateDefaultPlaylistsSafely() {
        // Ensure playlists are loaded from Room DB first before populating defaults
        loadSavedPlaylistsInternal()

        // Only populate if default playlists are enabled
        if (!appSettings.defaultPlaylistsEnabled.value) {
            Log.d(TAG, "Default playlists are disabled, skipping population")
            return
        }
        
        // If no songs loaded, nothing to populate
        if (_songs.value.isEmpty()) {
            Log.d(TAG, "No songs loaded, skipping playlist population")
            return
        }
        
        // Wait for filteredSongs flow to emit a value with data
        // Since we use Eagerly, this should happen quickly after _songs.value is set
        Log.d(TAG, "Waiting for filteredSongs to be ready (songs: ${_songs.value.size})")
        
        // Poll with small delays until filteredSongs has data or timeout
        val startTime = System.currentTimeMillis()
        val timeoutMs = 5000L
        while (filteredSongs.value.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            delay(50) // Small delay to allow flow to compute
        }
        
        val filteredCount = filteredSongs.value.size
        if (filteredCount == 0 && _songs.value.isNotEmpty()) {
            Log.w(TAG, "Timeout waiting for filteredSongs (${System.currentTimeMillis() - startTime}ms), using songs directly")
        } else {
            Log.d(TAG, "filteredSongs ready with $filteredCount songs in ${System.currentTimeMillis() - startTime}ms")
        }
        
        try {
            syncLikedPlaylistWithFavorites(_favoriteSongs.value)
            populateRecentlyAddedPlaylist()
            populateMostPlayedPlaylist()
        } catch (e: Exception) {
            Log.e(TAG, "Error populating default playlists", e)
        }
    }
    
    private fun initializeQueueState() {
        try {
            // Queue will be restored after MediaController is ready
            // Just initialize with empty state for now
            if (_currentQueue.value.songs.isEmpty()) {
                Log.d(TAG, "Initializing queue with empty state (will restore after controller is ready)")
                _currentQueue.value = Queue(emptyList(), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing queue state", e)
            _currentQueue.value = Queue(emptyList(), -1)
        }
    }
    
    /**
     * Restore the saved queue from persistence
     */
    private fun restoreSavedQueue(songIds: List<String>, savedIndex: Int) {
        viewModelScope.launch {
            try {
                // Wait for the ViewModel to be fully initialized (songs loaded from database)
                _isInitialized.first { it }
                
                // Map saved song IDs to actual song objects
                val allSongs = _songs.value
                val restoredSongs = songIds.mapNotNull { songId ->
                    allSongs.find { it.id == songId }
                }
                
                // Remove songs that no longer exist from the queue
                if (restoredSongs.size != songIds.size) {
                    val missingCount = songIds.size - restoredSongs.size
                    Log.w(TAG, "Queue restoration: $missingCount song(s) no longer available and were removed from queue")
                }
                
                if (restoredSongs.isNotEmpty()) {
                    // Validate the saved index
                    val validIndex = savedIndex.coerceIn(0, restoredSongs.size - 1)
                    val savedPosition = appSettings.savedPlaybackPosition.value
                    val restoredSongIds = restoredSongs.map { it.id }
                    
                    Log.d(TAG, "Successfully restored queue with ${restoredSongs.size} songs at index $validIndex, position: ${savedPosition}ms")
                    
                    // Restore the queue to MediaController
                    withContext(Dispatchers.Main) {
                        mediaController?.let { controller ->
                            pendingQueueRestore = null

                            // Clear existing queue
                            controller.clearMediaItems()
                            
                            // Add all restored songs to MediaController
                            val mediaItems = restoredSongs.map { song -> song.toMediaItem() }
                            controller.addMediaItems(mediaItems)
                            
                            // Prepare the player first
                            controller.prepare()
                            
                            // Set the queue in view model
                            _currentQueue.value = Queue(restoredSongs, validIndex)
                            
                            // Seek to the saved position in the queue
                            controller.seekTo(validIndex, savedPosition)
                            
                            // Update current song and UI state
                            val currentSong = restoredSongs.getOrNull(validIndex)
                            _currentSong.value = currentSong
                            _isFavorite.value = currentSong?.let { song -> 
                                _favoriteSongs.value.contains(song.id) 
                            } ?: false
                            
                            // Update progress immediately to reflect restored position
                            val playbackDuration = resolvePlaybackDuration(controller)
                            if (playbackDuration > 0) {
                                _progress.value = savedPosition.toFloat() / playbackDuration.toFloat()
                            }
                            
                            Log.d(TAG, "Queue restored successfully, ready to continue playback from ${savedPosition}ms")
                        } ?: run {
                            Log.w(TAG, "MediaController not available yet, queue will be restored when controller is ready")
                            // Store for later restoration when controller becomes available.
                            // Keep UI state populated so the player screen does not fall back to empty content.
                            pendingQueueRestore = restoredSongIds to validIndex
                            _currentQueue.value = Queue(restoredSongs, validIndex)
                            _currentSong.value = restoredSongs.getOrNull(validIndex)
                            _isFavorite.value = _currentSong.value?.let { song ->
                                _favoriteSongs.value.contains(song.id)
                            } ?: false
                        }
                    }
                } else {
                    Log.w(TAG, "No valid songs found in saved queue, starting with empty queue")
                    pendingQueueRestore = null
                    _currentQueue.value = Queue(emptyList(), -1)
                    appSettings.clearSavedQueue()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring saved queue", e)
                _currentQueue.value = Queue(emptyList(), -1)
            }
        }
    }
    
    /**
     * Restore queue after MediaController is ready
     * Called from connectToMediaService() after controller is initialized
     */
    private fun restoreQueueAfterControllerReady() {
        try {
            // Check if queue persistence is enabled
            if (!appSettings.queuePersistenceEnabled.value) {
                Log.d(TAG, "Queue persistence is disabled, skipping restoration")
                return
            }
            
            // Prefer a pending restore that was deferred because the controller was not ready yet.
            val pendingRestore = pendingQueueRestore
            val savedQueueIds = pendingRestore?.first ?: appSettings.savedQueue.value
            val savedIndex = pendingRestore?.second ?: appSettings.savedQueueIndex.value
            val hasActiveQueue = _currentQueue.value.songs.isNotEmpty() && _currentQueue.value.currentIndex >= 0

            if (pendingRestore == null && hasActiveQueue) {
                Log.d(TAG, "Active queue already present, skipping persisted queue restore")
                return
            }
            
            if (savedQueueIds.isNotEmpty() && savedIndex >= 0) {
                Log.d(TAG, "Restoring saved queue after controller ready: ${savedQueueIds.size} songs, index: $savedIndex")
                pendingQueueRestore = null
                restoreSavedQueue(savedQueueIds, savedIndex)
            } else {
                Log.d(TAG, "No saved queue to restore")
                pendingQueueRestore = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in restoreQueueAfterControllerReady", e)
        }
    }
    
    /**
     * Save the current queue to persistence
     */
    private fun saveQueueToPersistence() {
        try {
            if (_catalogQueue.value.isNotEmpty()) {
                val controller = mediaController
                val catalogEntriesByMediaId = _catalogQueue.value.associateBy {
                    it.playback.toMediaItem().mediaId
                }
                io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore(getApplication())
                    .saveUnified(
                        _currentQueue.value.songs,
                        catalogEntriesByMediaId,
                        controller?.currentMediaItemIndex?.takeIf { it >= 0 }
                            ?: _currentQueue.value.currentIndex.coerceAtLeast(0),
                        controller?.currentPosition ?: 0L,
                    )
                appSettings.clearSavedQueue()
                return
            }
            // Check if queue persistence is enabled
            if (!appSettings.queuePersistenceEnabled.value) {
                return
            }
            
            val currentQueue = _currentQueue.value
            if (currentQueue.songs.isNotEmpty()) {
                val controller = mediaController
                val useExoPlayerShuffle = appSettings.shuffleUsesExoplayer.value
                val isNativeShuffleActive = controller != null && controller.shuffleModeEnabled && useExoPlayerShuffle

                val songIds = if (isNativeShuffleActive) {
                    // Save original unshuffled timeline order to prevent double-shuffling on restore
                    (0 until controller.mediaItemCount).mapNotNull { index ->
                        controller.getMediaItemAt(index).mediaId
                    }
                } else {
                    currentQueue.songs.map { it.id }
                }

                val savedIndex = if (isNativeShuffleActive) {
                    // Save index in the unshuffled timeline
                    controller.currentMediaItemIndex
                } else {
                    currentQueue.currentIndex
                }

                appSettings.setSavedQueue(songIds)
                appSettings.setSavedQueueIndex(savedIndex)
                
                // Save current playback position
                val currentPosition = controller?.currentPosition ?: 0L
                appSettings.setSavedPlaybackPosition(currentPosition)
                
                Log.d(TAG, "Saved queue: ${songIds.size} songs, index: $savedIndex (nativeShuffleActive=$isNativeShuffleActive), position: ${currentPosition}ms")
            } else {
                // Clear saved queue if current queue is empty
                appSettings.clearSavedQueue()
                Log.d(TAG, "Cleared saved queue (current queue is empty)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue to persistence", e)
        }
    }
    
    /**
     * Start background tasks with appropriate delays to allow UI to settle first.
     * This is called AFTER _isInitialized is set to true, so UI is already responsive.
     */
    private fun startBackgroundTasksDeferred() {
        armLibrarySetupCompletionNotification()

        // Note: The redundant full MediaStore sync on startup has been removed.
        // It used to call loadSongs(forceRefresh=true) 500ms after app launch,
        // causing severe UI lag and DB writes.
        // We now rely on the MediaStoreObserver to detect actual changes and trigger updates.

        // Start listening time tracking (lightweight, can start immediately)
        viewModelScope.launch {
            try {
                startListeningTimeTracking()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting listening time tracking", e)
            }
        }
        
        // Startup staleness check: If MediaStore has changes, run background differential sync
        viewModelScope.launch(Dispatchers.IO) {
            delay(1200) // Allow UI transition to complete smoothly
            try {
                val lastScan = appSettings.lastScanTimestamp.value
                val cachedCount = _songs.value.size
                if (cachedCount == 0 || repository.isLibraryStale(lastScan, cachedCount)) {
                    Log.d(TAG, "Library is empty or stale on startup (count=$cachedCount), triggering background refresh")
                    performMediaStoreRefresh()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during startup staleness check", e)
            }
        }
        
        // Register ContentObserver for automatic MediaStore updates
        // Use a full refresh instead of incremental scan to detect removed/re-added songs.
        // Cancel any pending refresh job before scheduling a new one (debounce pattern).
        mediaStoreObserverRegisteredTimeMs = SystemClock.elapsedRealtime()
        repository.registerMediaStoreObserver {
            val timeSinceRegistration = SystemClock.elapsedRealtime() - mediaStoreObserverRegisteredTimeMs
            if (timeSinceRegistration < 5000) {
                Log.d(TAG, "Ignoring false-positive startup MediaStore observer callback ($timeSinceRegistration ms since registration)")
                return@registerMediaStoreObserver
            }
            Log.d(TAG, "MediaStore changed, scheduling debounce check for refresh")
            mediaStoreRefreshJob?.cancel()
            mediaStoreRefreshJob = viewModelScope.launch {
                delay(2000) // Debounce window
                val lastScan = appSettings.lastScanTimestamp.value
                val cachedCount = _songs.value.size
                if (repository.isLibraryStale(lastScan, cachedCount)) {
                    Log.d(TAG, "MediaStore changed and library is stale. Performing full refresh.")
                    performMediaStoreRefresh()
                } else {
                    Log.d(TAG, "MediaStore callback received, but library is up-to-date. Skipping refresh.")
                }
            }
        }
        
        // Start artwork fetching in background after a delay
        viewModelScope.launch {
            delay(1500) // Wait 1.5 seconds for UI to fully settle
            try {
                val context = getApplication<Application>()
                val hasMissingArtists = _artists.value.any { it.artworkUri == null }
                val hasMissingAlbums = _albums.value.any { it.artworkUri == null }
                val hasMissingSongs = _songs.value.any { it.artworkUri == null } ||
                    (io.github.cluno1.sonorus.core.ProductCapabilities.devicePublicMetadata && _songs.value.isNotEmpty())
                
                val artistSource1 = appSettings.artistArtworkSource.value
                val shouldFetchArtists = hasMissingArtists &&
                    artistSource1 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED &&
                    (artistSource1 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.API_ONLY ||
                        NetworkClient.isDeezerArtistArtworkEnabled())
                val shouldFetchAlbumsAndSongs = (hasMissingAlbums || hasMissingSongs) && appSettings.isAutoFetchArtworkActive.value &&
                    (io.github.cluno1.sonorus.core.ProductCapabilities.devicePublicMetadata || appSettings.ytMusicApiEnabled.value || appSettings.deezerApiEnabled.value)

                if (shouldFetchArtists || shouldFetchAlbumsAndSongs) {
                    _isFetchingArtwork.value = true
                    fetchArtworkFromInternet(
                        fetchArtists = shouldFetchArtists,
                        fetchAlbumsAndSongs = shouldFetchAlbumsAndSongs
                    )
                } else {
                    Log.d(TAG, "All artist/album/song artworks are present or auto-fetch is disabled, skipping startup internet fetches")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching artwork from internet", e)
            } finally {
                _isFetchingArtwork.value = false
            }
        }

        // Start background genre detection after a longer delay
        viewModelScope.launch {
            delay(3000) // Wait 3 seconds after app load before starting genre detection
            try {
                if (!appSettings.genreDetectionCompleted.value) {
                    Log.d(TAG, "Starting genre detection (completed: ${appSettings.genreDetectionCompleted.value})")
                    detectGenresInBackground()
                } else {
                    Log.d(TAG, "Genre detection already completed, skipping")
                    _isGenreDetectionComplete.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background genre detection", e)
                // Mark as complete on error to prevent infinite loading
                _isGenreDetectionComplete.value = true
            }
        }
        
        // Start background audio metadata extraction after an even longer delay
        viewModelScope.launch {
            delay(5000) // Wait 5 seconds after app load before starting metadata extraction
            try {
                val songsWithMetadata = songs.value.count {
                    it.bitrate != null && it.sampleRate != null && it.channels != null && it.codec != null
                }
                val hasMissingMetadata = songsWithMetadata < songs.value.size

                if (!appSettings.audioMetadataExtractionCompleted.value || hasMissingMetadata) {
                    _isExtractingMetadata.value = true
                    extractAudioMetadataInBackground()
                } else {
                    Log.d(TAG, "Audio metadata already extracted for current library, skipping startup pass")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background audio metadata extraction", e)
            } finally {
                _isExtractingMetadata.value = false
                }
            }

        // Extract embedded album art in background when preferSongArtwork is enabled.
        // This runs after the UI is fully settled so it doesn't affect splash screen load time.
        //
        // KEY LOGIC: We only extract if there are songs with NO artwork at all (artworkUri == null).
        // Songs that already have any artwork URI (including MediaStore content:// album art) are
        // considered covered — extractEmbeddedArtworkForSongs() will skip them individually anyway.
        // This prevents the extraction from running on every launch just because songs have
        // non-embedded-cache artwork URIs (which used to incorrectly count as "missing").
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferSongArtwork = appSettings.preferSongArtwork.value
                val losslessArtwork = appSettings.isLosslessArtworkActive.value
                val isCompleted = appSettings.embeddedArtworkExtractionCompleted.value
                val lastLosslessStatus = appSettings.embeddedArtworkExtractionLosslessStatus.value
                // Self-heal: if extraction is complete but the cache was wiped outside the
                // normal flow, re-run extraction once (gated to once per 24h to avoid loops).
                // Missing art is otherwise restored by the rescan scheduled after "Clear all cache".
                val songsExpectingEmbeddedArt = repository.countSongsWithEmbeddedArtworkUri()
                val needsSelfHeal = isCompleted &&
                    appSettings.shouldRunEmbeddedArtSelfHeal() &&
                    repository.isEmbeddedArtworkCacheEmpty() &&
                    songsExpectingEmbeddedArt > 0

                val needsRun = !isCompleted || lastLosslessStatus != losslessArtwork || needsSelfHeal

                if (preferSongArtwork && needsSelfHeal) {
                    Log.d(TAG, "Embedded artwork cache empty with $songsExpectingEmbeddedArt songs referencing it, running one-time self-heal re-extraction")
                    appSettings.markEmbeddedArtSelfHealDone()
                }

                if (preferSongArtwork && needsRun) {
                    val initialSongs = _songs.value
                    val songsNeedingExtraction = initialSongs.count { song ->
                        song.artworkUri == null ||
                        !repository.isEmbeddedArtworkCacheUri(song.artworkUri) ||
                        !repository.hasArtworkMatchingLossless(song, losslessArtwork)
                    }

                    if (songsNeedingExtraction == 0) {
                        Log.d(TAG, "All ${initialSongs.size} songs already have cached embedded artwork, skipping extraction")
                        appSettings.setEmbeddedArtworkExtractionLosslessStatus(losslessArtwork)
                        appSettings.setEmbeddedArtworkExtractionCompleted(true)
                        return@launch
                    }

                    Log.d(TAG, "$songsNeedingExtraction/${initialSongs.size} songs need embedded artwork extraction, starting after delay")
                    delay(1500L) // Allow UI to fully settle before heavy IO

                    Log.d(TAG, "Starting background embedded album art extraction (preferSongArtwork=$preferSongArtwork, lossless=$losslessArtwork)")
                    val currentSongs = _songs.value
                    if (currentSongs.isNotEmpty()) {
                        val updatedSongs = repository.extractEmbeddedArtworkForSongs(currentSongs, losslessArtwork)
                        repository.updateAndPersistSongs(updatedSongs)
                        val freshAlbums = repository.loadAlbums()
                        val freshArtists = repository.loadArtists()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _songs.value = updatedSongs
                            _albums.value = freshAlbums
                            _artists.value = freshArtists
                        }
                        appSettings.setEmbeddedArtworkExtractionLosslessStatus(losslessArtwork)
                        appSettings.setEmbeddedArtworkExtractionCompleted(true)
                        Log.d(TAG, "Background embedded art extraction complete for ${currentSongs.size} songs")
                    }
                } else {
                    Log.d(TAG, "Embedded artwork extraction already completed or disabled (preferSongArtwork=$preferSongArtwork, completed=$isCompleted, statusMatched=${lastLosslessStatus == losslessArtwork}), skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background embedded art extraction", e)
            }
        }
    }
    
    // Keep old method for library refresh which needs immediate execution
    private suspend fun startBackgroundTasks() {
        armLibrarySetupCompletionNotification()

        // Register ContentObserver for automatic MediaStore updates
        mediaStoreObserverRegisteredTimeMs = SystemClock.elapsedRealtime()
        repository.registerMediaStoreObserver {
            val timeSinceRegistration = SystemClock.elapsedRealtime() - mediaStoreObserverRegisteredTimeMs
            if (timeSinceRegistration < 5000) {
                Log.d(TAG, "Ignoring false-positive startup MediaStore observer callback ($timeSinceRegistration ms since registration)")
                return@registerMediaStoreObserver
            }
            Log.d(TAG, "MediaStore changed, scheduling incremental scan")
            mediaStoreRefreshJob?.cancel()
            mediaStoreRefreshJob = viewModelScope.launch {
                var lastChangeMs = SystemClock.elapsedRealtime()
                while (true) {
                    delay(2000)
                    val elapsedSinceLastChange = SystemClock.elapsedRealtime() - lastChangeMs
                    if (elapsedSinceLastChange >= 1900) {
                        break
                    }
                    lastChangeMs = SystemClock.elapsedRealtime()
                }
                performIncrementalScan()
            }
        }
        
        // Start artwork fetching in background without blocking initialization
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val hasMissingArtists = _artists.value.any { it.artworkUri == null }
                val hasMissingAlbums = _albums.value.any { it.artworkUri == null }
                val hasMissingSongs = _songs.value.any { it.artworkUri == null } ||
                    (io.github.cluno1.sonorus.core.ProductCapabilities.devicePublicMetadata && _songs.value.isNotEmpty())
                
                val artistSource2 = appSettings.artistArtworkSource.value
                val shouldFetchArtists = hasMissingArtists &&
                    artistSource2 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED &&
                    (artistSource2 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.API_ONLY ||
                        NetworkClient.isDeezerArtistArtworkEnabled())
                val shouldFetchAlbumsAndSongs = (hasMissingAlbums || hasMissingSongs) && appSettings.isAutoFetchArtworkActive.value &&
                    (io.github.cluno1.sonorus.core.ProductCapabilities.devicePublicMetadata || appSettings.ytMusicApiEnabled.value || appSettings.deezerApiEnabled.value)

                if (shouldFetchArtists || shouldFetchAlbumsAndSongs) {
                    _isFetchingArtwork.value = true
                    fetchArtworkFromInternet(
                        fetchArtists = shouldFetchArtists,
                        fetchAlbumsAndSongs = shouldFetchAlbumsAndSongs
                    )
                } else {
                    Log.d(TAG, "All artist/album/song artworks are present or auto-fetch is disabled, skipping internet fetches")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching artwork from internet", e)
            } finally {
                _isFetchingArtwork.value = false
            }
        }

        // Start background genre detection after a short delay to allow UI to settle
        viewModelScope.launch {
            try {
                delay(2000) // Wait 2 seconds after app load before starting genre detection
                if (!appSettings.genreDetectionCompleted.value) {
                    Log.d(TAG, "Starting genre detection (completed: ${appSettings.genreDetectionCompleted.value})")
                    detectGenresInBackground()
                } else {
                    Log.d(TAG, "Genre detection already completed, skipping")
                    _isGenreDetectionComplete.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background genre detection", e)
                // Mark as complete on error to prevent infinite loading
                _isGenreDetectionComplete.value = true
            }
        }
        
        // Start background audio metadata extraction after a short delay
        viewModelScope.launch {
            try {
                delay(3000) // Wait 3 seconds after app load before starting metadata extraction
                _isExtractingMetadata.value = true
                extractAudioMetadataInBackground()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting background audio metadata extraction", e)
            } finally {
                _isExtractingMetadata.value = false
            }
        }
    }
    
    /**
     * Perform incremental scan for newly added songs
     */
    private suspend fun performIncrementalScan() {
        Log.d(TAG, "Performing incremental scan...")
        val lastScanTime = appSettings.lastScanTimestamp.value
        
        try {
            val newSongs = repository.performIncrementalScan(
                lastScanTimestamp = lastScanTime,
                allowedFormats = allowedFormats.value,
                minimumBitrate = minimumBitrate.value,
                minimumDuration = minimumDuration.value
            )
            if (newSongs.isNotEmpty()) {
                Log.d(TAG, "Found ${newSongs.size} new songs, updating library")
                
                // Extract embedded artwork for new songs if needed
                val losslessArtwork = appSettings.isLosslessArtworkActive.value
                val updatedNewSongs = repository.extractEmbeddedArtworkForSongs(newSongs, losslessArtwork)
                
                val mergedSongs = _songs.value + updatedNewSongs
                _songs.value = mergedSongs
                _albums.value = repository.loadAlbums()
                _artists.value = repository.loadArtists()
                appSettings.setAudioMetadataExtractionCompleted(false)
                
                // Keep the repository's in-memory cache aligned before persisting to Room.
                repository.updateAndPersistSongs(mergedSongs)
                
                // Update last scan time
                appSettings.setLastScanTimestamp(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during incremental scan", e)
        }
    }
    
    /**
     * Perform a full library refresh triggered by MediaStore ContentObserver.
     * Unlike incremental scan, this detects removed, re-added, and new songs properly.
     * Runs on IO dispatcher to avoid blocking the main thread.
     */
    private suspend fun performMediaStoreRefresh() {
        Log.d(TAG, "Performing full MediaStore refresh...")
        try {
            val currentCount = _songs.value.size
            val cachedSongMap = _songs.value.associateBy { it.id }
            // Run all heavy IO work off the main thread
            val (freshSongs, freshAlbums, freshArtists) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.refreshMusicData(
                    allowedFormats = allowedFormats.value,
                    minimumBitrate = minimumBitrate.value,
                    minimumDuration = minimumDuration.value
                )
            }
            // Merge cached metadata into fresh songs to preserve post-scan processing data
            val mergedSongs = freshSongs.map { fresh ->
                val cached = cachedSongMap[fresh.id]
                if (cached != null) {
                    val keepEmbedded = repository.isEmbeddedArtworkCacheUri(cached.artworkUri) &&
                        cached.artworkUri?.path?.let { File(it).exists() } == true
                    fresh.copy(
                        genre = fresh.genre ?: cached.genre,
                        bitrate = fresh.bitrate ?: cached.bitrate,
                        sampleRate = fresh.sampleRate ?: cached.sampleRate,
                        channels = fresh.channels ?: cached.channels,
                        codec = fresh.codec ?: cached.codec,
                        artworkUri = if (keepEmbedded) cached.artworkUri else (fresh.artworkUri ?: cached.artworkUri)
                    )
                } else {
                    fresh
                }
            }
            val projectedSongs = repository.projectDeviceArtwork(mergedSongs)
            _songs.value = projectedSongs
            _albums.value = freshAlbums
            _artists.value = freshArtists
            _folderTree.value = repository.buildFolderTree(projectedSongs)
            repository.updateAndPersistSongs(projectedSongs)
            appSettings.setLastScanTimestamp(System.currentTimeMillis())

            if (appSettings.defaultPlaylistsEnabled.value) {
                try {
                    populateRecentlyAddedPlaylist()
                    populateMostPlayedPlaylist()
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating default playlists after MediaStore refresh", e)
                }
            }
            try {
                refreshPlaylists(preserveMissingSongs = true)
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing playlists after MediaStore refresh", e)
            }

            // Only invalidate the embedded artwork extraction flag when new songs have appeared.
            // Resetting it unconditionally caused extraction to re-run on every launch because the
            // MediaStore observer fires on startup, triggering this refresh and clearing the flag.
            if (projectedSongs.size > currentCount) {
                Log.d(TAG, "New songs detected (${projectedSongs.size - currentCount} added), resetting embedded artwork extraction flag")
                appSettings.setEmbeddedArtworkExtractionCompleted(false)
            }
            Log.d(TAG, "MediaStore refresh complete: $currentCount -> ${mergedSongs.size} songs")
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore refresh", e)
        }
    }

    /**
     * Detects genres for songs in background and updates the UI dynamically
     */
    private suspend fun detectGenresInBackground() {
        // Prevent running if already complete or currently running
        if (_isGenreDetectionComplete.value || _isGenreDetectionRunning.value) {
            Log.d(TAG, "Genre detection already complete or running, skipping")
            return
        }

        Log.d(TAG, "Starting background genre detection for ${songs.value.size} songs")
        _isGenreDetectionRunning.value = true

        try {
            repository.detectGenresInBackground(
                songs = songs.value,
                onProgress = { current, total ->
                    // Optional: Could emit progress updates to UI if needed
                    Log.d(TAG, "Genre detection progress: $current/$total")
                },
                onComplete = { updatedSongs ->
                    // Update the songs state with the new genre information FIRST
                    _songs.value = updatedSongs
                    // Persist genre data with the updated snapshot so future launches reuse it.
                    repository.updateAndPersistSongs(updatedSongs)
                    // Then mark detection as complete AFTER songs are updated to prevent race condition
                    viewModelScope.launch {
                        delay(100) // Small delay to ensure songs state propagates
                        _isGenreDetectionComplete.value = true
                        _isGenreDetectionRunning.value = false
                        appSettings.setGenreDetectionCompleted(true)
                        Log.d(TAG, "Background genre detection completed, updated ${updatedSongs.count { it.genre != null }} songs with genres")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during background genre detection", e)
            // Mark as complete even on error to prevent infinite loading
            _isGenreDetectionComplete.value = true
            _isGenreDetectionRunning.value = false
        }
    }
    
    /**
     * Extracts audio metadata for songs in background and updates the UI dynamically
     */
    private suspend fun extractAudioMetadataInBackground() {
        Log.d(TAG, "Starting background audio metadata extraction for ${songs.value.size} songs")

        repository.extractAudioMetadataInBackground(
            songs = songs.value,
            onProgress = { current, total ->
                Log.d(TAG, "Audio metadata extraction progress: $current/$total")
            },
            onBatchComplete = { batchSongs ->
                val batchMap = batchSongs.associateBy { it.id }
                _songs.value = _songs.value.map { song -> batchMap[song.id] ?: song }
            },
            onComplete = { updatedSongs ->
                // Update the songs state with the new audio metadata information
                _songs.value = updatedSongs
                // Update repository cache and persist metadata to disk cache
                Log.d(TAG, "Background metadata extraction completed, updating repository cache and persisting ${updatedSongs.size} songs to cache")
                repository.updateAndPersistSongs(updatedSongs)
                appSettings.setAudioMetadataExtractionCompleted(true)
                val songsWithMetadata = updatedSongs.count { 
                    it.bitrate != null && it.sampleRate != null && it.channels != null && it.codec != null 
                }
                Log.d(TAG, "Background audio metadata extraction completed, updated $songsWithMetadata songs")
            }
        )
    }
    
    private fun startListeningTimeTracking() {
        viewModelScope.launch {
            try {
                // Only track listening time while actually playing
                isPlaying.collectLatest { playing ->
                    if (playing && _currentSong.value != null) {
                        while (isActive) {
                            delay(60000) // 1 minute
                            // Double-check still playing after delay
                            if (isPlaying.value && _currentSong.value != null) {
                                val newTime = _listeningTime.value + 60000
                                _listeningTime.value = newTime
                                appSettings.setListeningTime(newTime)
                            }
                        }
                    }
                    // When not playing, collectLatest suspends until next change — no polling needed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in listening time tracking", e)
            }
        }
    }
    
    private fun handleInitializationFailure(error: Exception) {
        Log.e(TAG, "Handling initialization failure", error)
        
        // Set safe default values
        _songs.value = emptyList()
        _albums.value = emptyList()
        _artists.value = emptyList()
        _currentQueue.value = Queue(emptyList(), -1)
        _isInitialized.value = true // Mark as initialized even with empty data
        
        // Could potentially show error message to user here
    }

    /**
     * Triggers a refresh of all music data by rescanning the device's MediaStore.
     * This will update the songs, albums, and artists in the ViewModel.
     */
    fun refreshLibrary(showMediaScanLoader: Boolean = true) {
        mediaScanNotificationSequence += 1
        val notificationSequence = mediaScanNotificationSequence
        armLibrarySetupCompletionNotification()

        // Cancel any existing scan
        scanJob?.cancel()
        
        scanJob = viewModelScope.launch {
            Log.d(TAG, "Starting library refresh...")
            val previousSongCount = _songs.value.size
            var refreshCompletedSuccessfully = false
            var refreshCancelled = false

            _isMediaScanning.value = showMediaScanLoader // Only show full-screen loader when requested
            _isLibraryRefreshing.value = true // Always set for pull-to-refresh tracking
            if (showMediaScanLoader) {
                // Only a full-screen scan should hide the app behind the initialization
                // loader; pull-to-refresh and background rescans keep the app visible.
                _isInitialized.value = false
            }
            _isGenreDetectionComplete.value = false // Reset genre detection state
            appSettings.setEmbeddedArtworkExtractionCompleted(false)
            // Don't reset _isGenreDetectionRunning to allow proper concurrency check

            startMediaScanProgressNotifications(notificationSequence)
            
            val startTime = System.currentTimeMillis()

            try {
                // Trigger the refresh in the repository and get the fresh data directly
                // to avoid resolving entire media store twice
                val (freshSongs, freshAlbums, freshArtists) = repository.refreshMusicData(
                    allowedFormats = allowedFormats.value,
                    minimumBitrate = minimumBitrate.value,
                    minimumDuration = minimumDuration.value
                )

                // Update StateFlows with the fresh data
                _songs.value = freshSongs
                _albums.value = freshAlbums
                _artists.value = freshArtists

                // Re-populate dynamic playlists (if enabled)
                if (appSettings.defaultPlaylistsEnabled.value) {
                    populateRecentlyAddedPlaylist()
                    populateMostPlayedPlaylist()
                }
                
                // When the scanned library drops sharply (for example removable storage unmounted),
                // keep unresolved playlist entries temporarily so ordering survives remount.
                val preserveMissingSongs =
                    previousSongCount > 0 && freshSongs.size < (previousSongCount * 0.7f).toInt()
                if (preserveMissingSongs) {
                    Log.d(
                        TAG,
                        "Detected large library drop ($previousSongCount -> ${freshSongs.size}); preserving unresolved playlist entries"
                    )
                }

                refreshPlaylists(preserveMissingSongs = preserveMissingSongs)

                // Re-fetch artwork from internet for newly added/updated items (but don't block completion)
                launch { 
                    try {
                        val context = getApplication<Application>()
                        val hasMissingArtists = _artists.value.any { it.artworkUri == null }
                        val hasMissingAlbums = withContext(Dispatchers.IO) {
                            _albums.value.any { album ->
                                val uri = album.artworkUri
                                uri == null || (uri.toString().startsWith("content://media/external/audio/albumart") && !isContentUriReadable(context, uri))
                            }
                        }
                        val hasMissingSongs = _songs.value.any { it.artworkUri == null }
                        
                        val artistSource3 = appSettings.artistArtworkSource.value
                        val shouldFetchArtists = hasMissingArtists &&
                            artistSource3 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED &&
                            (artistSource3 != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.API_ONLY ||
                                NetworkClient.isDeezerArtistArtworkEnabled())
                        val shouldFetchAlbumsAndSongs = (hasMissingAlbums || hasMissingSongs) && appSettings.isAutoFetchArtworkActive.value && (appSettings.ytMusicApiEnabled.value || appSettings.deezerApiEnabled.value)

                        if (shouldFetchArtists || shouldFetchAlbumsAndSongs) {
                            _isFetchingArtwork.value = true
                            fetchArtworkFromInternet(
                                fetchArtists = shouldFetchArtists,
                                fetchAlbumsAndSongs = shouldFetchAlbumsAndSongs
                            )
                        } else {
                            Log.d(TAG, "All artist/album/song artworks are present or auto-fetch is disabled, skipping internet fetches")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Artwork fetching failed but continuing with library refresh", e)
                    } finally {
                        _isFetchingArtwork.value = false
                    }
                }
                
                // Note: Genre detection is handled by the initial load, no need to restart here
                
                // Restart background audio metadata extraction
                launch {
                    _isExtractingMetadata.value = true
                    try {
                        delay(3000) // Wait 3 seconds before starting metadata extraction
                        extractAudioMetadataInBackground()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restarting background audio metadata extraction", e)
                    } finally {
                        _isExtractingMetadata.value = false
                    }
                }

                // Trigger background embedded album art extraction for new/updated songs
                if (appSettings.preferSongArtwork.value) {
                    launch(Dispatchers.IO) {
                        try {
                            delay(1200)
                            val currentSongs = _songs.value
                            val losslessArtwork = appSettings.isLosslessArtworkActive.value
                            val songsNeedingExtraction = currentSongs.count { song ->
                                song.artworkUri == null ||
                                !repository.isEmbeddedArtworkCacheUri(song.artworkUri) ||
                                !repository.hasArtworkMatchingLossless(song, losslessArtwork)
                            }
                            if (songsNeedingExtraction > 0) {
                                Log.d(TAG, "Starting post-refresh background embedded artwork extraction for $songsNeedingExtraction songs")
                                val updatedSongs = repository.extractEmbeddedArtworkForSongs(currentSongs, losslessArtwork)
                                repository.updateAndPersistSongs(updatedSongs)
                                val freshAlbums = repository.loadAlbums()
                                val freshArtists = repository.loadArtists()
                                withContext(Dispatchers.Main) {
                                    _songs.value = updatedSongs
                                    _albums.value = freshAlbums
                                    _artists.value = freshArtists
                                }
                                appSettings.setEmbeddedArtworkExtractionLosslessStatus(losslessArtwork)
                                appSettings.setEmbeddedArtworkExtractionCompleted(true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in post-refresh embedded artwork extraction", e)
                        }
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val scanFailed = repository.scanDiagnostics.value.failed ||
                    repository.scanProgress.value.stage == ScanPhase.PermissionDenied
                if (scanFailed) {
                    Log.w(
                        TAG,
                        "Library refresh kept the previous library because the device scan failed",
                    )
                } else {
                    appSettings.setLastScanTimestamp(System.currentTimeMillis())
                    appSettings.setLastScanDuration(duration)
                    Log.d(TAG, "Library refresh complete. Loaded ${_songs.value.size} songs, ${_albums.value.size} albums, ${_artists.value.size} artists in ${duration}ms")
                    refreshCompletedSuccessfully = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Library refresh cancelled by user")
                refreshCancelled = true
                throw e // Re-throw to allow proper cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error during library refresh", e)
                // Ensure we still have some data even if refresh fails
                if (_songs.value.isEmpty()) {
                    try {
                        _songs.value = repository.loadSongs(
                            forceRefresh = true,
                            allowedFormats = allowedFormats.value,
                            minimumBitrate = minimumBitrate.value,
                            minimumDuration = minimumDuration.value
                        )
                        _albums.value = repository.loadAlbums()
                        _artists.value = repository.loadArtists()
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Fallback loading also failed", fallbackError)
                    }
                }
            } finally {
                stopMediaScanProgressNotifications()
                _isInitialized.value = true // Mark as initialized again
                _isMediaScanning.value = false // Hide media scan loader
                _isLibraryRefreshing.value = false // Reset pull-to-refresh state
                
                // Process pending restore if queued
                val queued = pendingRestore
                if (queued != null) {
                    pendingRestore = null
                    Log.d(TAG, "Executing queued restore operation...")
                    val success = repository.backupRestoreManager.restoreFromBackupPayload(queued.backupJson, queued.sections)
                    if (success) {
                        reloadPlaylistsFromSettings()
                    }
                    queued.onResult(success)
                }
                
                // Ensure MediaScanLoader doesn't get stuck by dispatching a completion event
                // This is a safety measure for cases where the StateFlow updates might not trigger UI properly
                try {
                    delay(1000) // Give UI time to process the state changes
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Keep cleanup deterministic even when the refresh coroutine is cancelled.
                }
                Log.d(TAG, "Media scanning state cleared - final state: ${_songs.value.size} songs, ${_albums.value.size} albums, ${_artists.value.size} artists")

                if (!refreshCompletedSuccessfully) {
                    disarmLibrarySetupCompletionNotification()
                }

                if (notificationSequence == mediaScanNotificationSequence) {
                    val context = getApplication<Application>()
                    when {
                        refreshCompletedSuccessfully -> {
                            showOperationResultNotification(
                                notificationId = MEDIA_SCAN_NOTIFICATION_ID,
                                title = context.getString(R.string.notification_media_scan_title),
                                content = context.getString(R.string.notification_media_scan_complete_pending_setup),
                                isError = false
                            )
                        }

                        refreshCancelled -> {
                            showOperationResultNotification(
                                notificationId = MEDIA_SCAN_NOTIFICATION_ID,
                                title = context.getString(R.string.notification_media_scan_title),
                                content = context.getString(R.string.notification_media_scan_cancelled),
                                isError = false,
                                autoDismissMs = 3000L
                            )
                        }

                        else -> {
                            showOperationResultNotification(
                                notificationId = MEDIA_SCAN_NOTIFICATION_ID,
                                title = context.getString(R.string.notification_media_scan_title),
                                content = context.getString(R.string.notification_media_scan_failed),
                                isError = true,
                                autoDismissMs = 8000L
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Cancel ongoing scan operation
     */
    fun cancelScan() {
        Log.d(TAG, "Cancelling scan...")
        scanJob?.cancel()
        disarmLibrarySetupCompletionNotification()
        stopMediaScanProgressNotifications()
        _isMediaScanning.value = false
        _isLibraryRefreshing.value = false
        _isInitialized.value = true
    }

    private fun armLibrarySetupCompletionNotification() {
        notificationManagerHelper.armLibrarySetupCompletionNotification()
    }

    private fun disarmLibrarySetupCompletionNotification() {
        notificationManagerHelper.disarmLibrarySetupCompletionNotification()
    }

    private fun startLibrarySetupCompletionMonitor() {
        notificationManagerHelper.startLibrarySetupCompletionMonitor(
            scope = viewModelScope,
            isBackgroundProcessing = isBackgroundProcessing,
            isMediaScanning = isMediaScanning,
            isGenreDetectionRunning = isGenreDetectionRunning,
            isFetchingArtwork = isFetchingArtwork,
            isExtractingMetadata = isExtractingMetadata,
            filteredSongsSize = { filteredSongs.value.size },
            filteredAlbumsSize = { filteredAlbums.value.size },
            filteredArtistsSize = { filteredArtists.value.size }
        )
    }

    fun showOperationProgressNotification(
        notificationId: Int,
        title: String,
        content: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
        requestCode: Int = notificationId
    ) {
        notificationManagerHelper.showOperationProgressNotification(
            notificationId = notificationId,
            title = title,
            content = content,
            progress = progress,
            max = max,
            indeterminate = indeterminate,
            requestCode = requestCode
        )
    }

    fun showOperationResultNotification(
        notificationId: Int,
        title: String,
        content: String,
        isError: Boolean,
        autoDismissMs: Long = OPERATION_NOTIFICATION_AUTO_DISMISS_MS,
        requestCode: Int = notificationId
    ) {
        notificationManagerHelper.showOperationResultNotification(
            scope = viewModelScope,
            notificationId = notificationId,
            title = title,
            content = content,
            isError = isError,
            autoDismissMs = autoDismissMs,
            requestCode = requestCode
        )
    }

    private fun startMediaScanProgressNotifications(sequence: Long) {
        notificationManagerHelper.startMediaScanProgressNotifications(
            scope = viewModelScope,
            repository = repository,
            sequence = sequence
        )
    }

    private fun stopMediaScanProgressNotifications() {
        notificationManagerHelper.stopMediaScanProgressNotifications()
    }

    private fun clearOperationNotifications() {
        notificationManagerHelper.clearOperationNotifications()
    }

    /**
     * Refreshes all playlists by re-validating their songs against the currently available songs.
     * This removes songs from playlists if they no longer exist on the device OR are blacklisted.
     */
    private fun refreshPlaylists(preserveMissingSongs: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Refreshing playlists...")
                val currentSongs = _songs.value
        
                if (currentSongs.isEmpty()) {
                    Log.w(TAG, "Skipping refreshPlaylists() — songs list is empty (library not yet loaded or scan in progress)")
                    return@launch
                }
        
                val currentSongsMap = currentSongs.associateBy { it.id }
                val currentSongsByStableKey = lazy(LazyThreadSafetyMode.NONE) { currentSongs.associateBy { playlistSongStableKey(it) } }
                val currentSongsByStableKeyMed = lazy(LazyThreadSafetyMode.NONE) { currentSongs.associateBy { playlistSongStableKeyMed(it) } }
                val currentSongsByStableKeyLight = lazy(LazyThreadSafetyMode.NONE) { currentSongs.associateBy { playlistSongStableKeyLight(it) } }
                val currentSongsByStableKeyBasic = lazy(LazyThreadSafetyMode.NONE) { currentSongs.associateBy { playlistSongStableKeyBasic(it) } }
        
                val filteredSongsValue = filteredSongs.value
                val filteredSongsSet: Set<String> = if (filteredSongsValue.isEmpty()) {
                    Log.w(TAG, "filteredSongs is empty while songs list has ${currentSongs.size} entries — using full songs list as fallback to prevent playlist wipe")
                    currentSongs.map { it.id }.toSet()
                } else {
                    filteredSongsValue.map { it.id }.toSet()
                }
                
                _playlists.value = _playlists.value.map { playlist ->
                    var remappedByStableKey = 0
                    var preservedMissing = 0
        
                    val updatedSongs = playlist.songs.mapNotNull { playlistSong ->
                        val isStreaming = playlistSong.uri.toString().startsWith("http://") || 
                                          playlistSong.uri.toString().startsWith("https://") || 
                                          playlistSong.uri.toString().startsWith("streaming://")
                        
                        if (isStreaming) {
                            playlistSong
                        } else {
                            val directMatch = currentSongsMap[playlistSong.id]
                            val resolvedSong = directMatch ?: resolveSongByStableKeys(
                                playlistSong,
                                currentSongsByStableKey,
                                currentSongsByStableKeyMed,
                                currentSongsByStableKeyLight,
                                currentSongsByStableKeyBasic
                            )
        
                            when {
                                resolvedSong != null && filteredSongsSet.contains(resolvedSong.id) -> {
                                    if (directMatch == null && resolvedSong.id != playlistSong.id) {
                                        remappedByStableKey++
                                    }
                                    resolvedSong
                                }
                                preserveMissingSongs -> {
                                    preservedMissing++
                                    playlistSong
                                }
                                else -> null
                            }
                        }
                    }
        
                    if (updatedSongs != playlist.songs) {
                        val removedCount = playlist.songs.size - updatedSongs.size
                        if (remappedByStableKey > 0) {
                            Log.d(
                                TAG,
                                "Remapped $remappedByStableKey songs by stable key in playlist: ${playlist.name}"
                            )
                        }
                        if (preservedMissing > 0) {
                            Log.d(
                                TAG,
                                "Preserved $preservedMissing unresolved songs in playlist: ${playlist.name}"
                            )
                        }
                        if (removedCount > 0) {
                            Log.d(
                                TAG,
                                "Removed $removedCount missing/filtered songs from playlist: ${playlist.name}"
                            )
                        }
                        playlist.copy(songs = updatedSongs, dateModified = System.currentTimeMillis())
                    } else {
                        playlist
                    }
                }
                savePlaylists()
                Log.d(TAG, "Playlists refreshed.")
        }
    }

    private fun playlistSongStableKey(song: Song): String {
        return listOf(
            song.title.trim().lowercase(),
            song.artist.trim().lowercase(),
            song.album.trim().lowercase(),
            (song.duration / 1000L).toString(),
            song.trackNumber.toString(),
            song.discNumber.toString()
        ).joinToString("|")
    }

    private fun playlistSongStableKeyMed(song: Song): String {
        return listOf(
            song.title.trim().lowercase(),
            song.artist.trim().lowercase(),
            song.album.trim().lowercase(),
            (song.duration / 1000L).toString()
        ).joinToString("|")
    }

    private fun playlistSongStableKeyLight(song: Song): String {
        return listOf(
            song.title.trim().lowercase(),
            song.artist.trim().lowercase(),
            (song.duration / 1000L).toString()
        ).joinToString("|")
    }

    private fun playlistSongStableKeyBasic(song: Song): String {
        return listOf(
            song.title.trim().lowercase(),
            song.artist.trim().lowercase()
        ).joinToString("|")
    }

    private fun resolveSongByStableKeys(
        song: Song,
        fullMap: Lazy<Map<String, Song>>,
        medMap: Lazy<Map<String, Song>>,
        lightMap: Lazy<Map<String, Song>>,
        basicMap: Lazy<Map<String, Song>>
    ): Song? {
        val title = song.title.trim()
        val artist = song.artist.trim()
        if (title.isEmpty()) return null
        
        return fullMap.value[playlistSongStableKey(song)]
            ?: medMap.value[playlistSongStableKeyMed(song)]
            ?: lightMap.value[playlistSongStableKeyLight(song)]
            ?: if (artist.isNotEmpty() && artist.lowercase() != "unknown artist") {
                basicMap.value[playlistSongStableKeyBasic(song)]
            } else null
    }
    
    /**
     * Updates the current song metadata in the UI and library
     * @param updatedSong The song with updated metadata
     */
    fun updateCurrentSongMetadata(updatedSong: Song) {
        viewModelScope.launch {
            // Update the song in the main songs list
            val updatedSongs = _songs.value.map { song ->
                if (song.id == updatedSong.id) updatedSong else song
            }
            _songs.value = updatedSongs
            
            // Update current song if it matches
            if (_currentSong.value?.id == updatedSong.id) {
                _currentSong.value = updatedSong
            }
            
            // Update in any playlists
            _playlists.value = _playlists.value.map { playlist ->
                playlist.copy(
                    songs = playlist.songs.map { song ->
                        if (song.id == updatedSong.id) updatedSong else song
                    }
                )
            }
            
            // Update albums and artists to reflect the new metadata
            _albums.value = repository.loadAlbums()
            _artists.value = repository.loadArtists()
            
            // Persist the updated songs list so metadata/artwork edits survive restarts.
            repository.updateAndPersistSongs(updatedSongs)
            
            Log.d(TAG, "Updated song metadata: ${updatedSong.title} by ${updatedSong.artist}")
        }
    }

    /**
     * Updates an artist's custom image/artwork
     */
    fun updateArtistArtwork(artist: Artist, newUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedUri = repository.saveArtistArtwork(artist.name, newUri)
            
            // Now, update our in-memory list _artists
            _artists.value = _artists.value.map { currentArtist ->
                if (currentArtist.name == artist.name) {
                    currentArtist.copy(artworkUri = updatedUri)
                } else {
                    currentArtist
                }
            }
            onComplete()
        }
    }

    /**
     * Updates a playlist's custom image/artwork
     */
    fun updatePlaylistArtwork(playlistId: String, newUri: Uri?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedUri = repository.savePlaylistArtwork(playlistId, newUri)
            
            // Now, update our in-memory list _playlists
            _playlists.value = _playlists.value.map { currentPlaylist ->
                if (currentPlaylist.id == playlistId) {
                    currentPlaylist.copy(artworkUri = updatedUri)
                } else {
                    currentPlaylist
                }
            }
            onComplete()
        }
    }
    
    /**
     * Saves metadata changes to the audio file and updates the UI
     * On Android 11+, if permission is needed, it will trigger a permission request flow
     */
    fun saveMetadataChanges(
        song: Song,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        albumArtist: String? = null,
        composer: String? = null,
        discNumber: Int = 1,
        onSuccess: (fileWriteSucceeded: Boolean) -> Unit,
        onError: (String) -> Unit,
        onPermissionRequired: ((PendingWriteRequest) -> Unit)? = null
    ) {
        if (song.id.startsWith("rhythm-catalog:")) {
            onError("远程歌曲不支持修改本地文件元数据")
            return
        }
        metadataManagerHelper.saveMetadataChanges(
            song = song,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            artworkUri = artworkUri,
            removeArtwork = removeArtwork,
            albumArtist = albumArtist,
            composer = composer,
            discNumber = discNumber,
            onSuccess = onSuccess,
            onError = onError,
            onPermissionRequired = onPermissionRequired
        )
    }
    
    /**
     * Called after user grants permission via the system dialog triggered by createWriteRequest
     * Completes the pending metadata write operation
     */
    fun completeMetadataWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        metadataManagerHelper.completeMetadataWriteAfterPermission(onSuccess, onError)
    }
    
    /**
     * Called when user denies the permission request
     * Cleans up the pending request
     */
    fun cancelPendingMetadataWrite() {
        metadataManagerHelper.cancelPendingMetadataWrite()
    }

    /**
     * Attempts to delete a song physically and from local databases.
     * Uses MediaStore.createDeleteRequest on Android 11+ and RecoverableSecurityException handling on Android 10.
     */
    fun deleteSong(song: Song) {
        if (song.id.startsWith("rhythm-catalog:")) {
            Log.w(TAG, "Ignoring file deletion for catalog song")
            return
        }
        viewModelScope.launch {
            // First remove the song from active playback queue
            removeFromQueue(song)
            
            val context = getApplication<Application>()
            try {
                val deletedStatus = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // On Android 11+, we must request delete permission via MediaStore
                        try {
                            val resolvedUri = MediaUtils.getSpecificVolumeUri(context, song.id.toLongOrNull() ?: 0L) ?: song.uri
                            val pendingIntent = android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(resolvedUri))
                            _pendingDeleteRequest.value = PendingDeleteRequest(pendingIntent.intentSender, song)
                            -1 // Signifies pending permission
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to create delete request for Android 11+", e)
                            0
                        }
                    } else {
                        // On Android 10 and below, try direct deletion first.
                        var success = false
                        try {
                            val count = context.contentResolver.delete(song.uri, null, null)
                            success = count > 0
                        } catch (e: Exception) {
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                                _pendingDeleteRequest.value = PendingDeleteRequest(e.userAction.actionIntent.intentSender, song)
                                return@withContext -1
                            }
                        }
                        
                        if (!success && song.path != null) {
                            val file = File(song.path)
                            if (file.exists()) {
                                success = file.delete()
                            }
                        }
                        
                        if (success) 1 else 0
                    }
                }
                
                if (deletedStatus == 1) {
                    repository.deleteSong(song)
                    refreshLibrary(showMediaScanLoader = false)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, context.getString(R.string.song_deleted_successfully), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else if (deletedStatus == 0) {
                    Log.e(TAG, "Failed to delete song: ${song.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteSong", e)
            }
        }
    }

    /**
     * Completes song deletion after user grants permission via dialog.
     */
    fun completeSongDeletion(song: Song) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        try {
                            getApplication<Application>().contentResolver.delete(song.uri, null, null)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete file after Android 10 permission granted", e)
                        }
                    }
                }
                repository.deleteSong(song)
                refreshLibrary(showMediaScanLoader = false)
                _pendingDeleteRequest.value = null
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.song_deleted_successfully), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing song deletion", e)
                _pendingDeleteRequest.value = null
            }
        }
    }

    /**
     * Cancels the pending delete request.
     */
    fun cancelPendingDelete() {
        _pendingDeleteRequest.value = null
        Log.d(TAG, "Cancelled pending delete request")
    }

    /**
     * Batch-edits metadata for multiple songs at once.
     * Only enabled fields are applied; disabled fields are left untouched.
     */
    fun batchEditMetadata(
        songs: List<Song>,
        artist: String?,
        album: String?,
        genre: String?,
        year: Int?,
        artworkUri: Uri? = null,
        removeArtwork: Boolean = false,
        onProgress: (Int, Int) -> Unit,
        onComplete: (successCount: Int, failCount: Int) -> Unit,
        onPermissionRequired: ((PendingBatchWriteRequest) -> Unit)? = null
    ) {
        if (songs.any { it.id.startsWith("rhythm-catalog:") }) {
            onComplete(0, songs.size)
            return
        }
        metadataManagerHelper.batchEditMetadata(
            songs = songs,
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            artworkUri = artworkUri,
            removeArtwork = removeArtwork,
            onProgress = onProgress,
            onComplete = onComplete,
            onPermissionRequired = onPermissionRequired
        )
    }

    /**
     * Completes pending batch metadata write after user grants permission.
     */
    fun completeBatchMetadataWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        metadataManagerHelper.completeBatchMetadataWriteAfterPermission(onSuccess, onError)
    }

    /**
     * Cancels pending batch metadata write request.
     */
    fun cancelPendingBatchMetadataWrite() {
        metadataManagerHelper.cancelPendingBatchMetadataWrite()
    }
    
    /**
     * Restores app data from a backup JSON string. If a media scan is active,
     * the restore is queued until the scan finishes.
     */
    fun restoreFromBackup(
        backupJson: String,
        sections: AppSettings.BackupRestoreSections = AppSettings.BackupRestoreSections()
    ) {
        if (_isLibraryRefreshing.value || _isMediaScanning.value) {
            Log.d(TAG, "Media scan is active. Queueing backup restore.")
            pendingRestore = QueuedRestore(backupJson, sections) { success ->
                if (success) {
                    _restoreResult.value = RestoreResult.Success(wasQueued = true)
                } else {
                    _restoreResult.value = RestoreResult.Failure("Invalid backup format or corrupted data")
                }
            }
            _restoreResult.value = RestoreResult.Queued
        } else {
            Log.d(TAG, "No active scan. Applying non-blocking atomic backup restore.")
            viewModelScope.launch(Dispatchers.IO) {
                val success = repository.backupRestoreManager.restoreFromBackupPayload(backupJson, sections)
                withContext(Dispatchers.Main) {
                    if (success) {
                        reloadPlaylistsFromSettings()
                        _restoreResult.value = RestoreResult.Success(wasQueued = false)
                    } else {
                        _restoreResult.value = RestoreResult.Failure("Invalid backup format or corrupted data")
                    }
                }
            }
        }
    }

    /**
     * Reloads playlists and favorite songs from settings after a backup restore
     */
    fun reloadPlaylistsFromSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Reloading playlists from settings after restore...")
                loadSavedPlaylistsInternal()
                val favoriteSongsJson = appSettings.favoriteSongs.value
                val favoritesSet: Set<String> = if (!favoriteSongsJson.isNullOrBlank()) {
                    val type = object : TypeToken<Set<String>>() {}.type
                    GsonUtils.gson.fromJson(favoriteSongsJson, type) ?: emptySet()
                } else {
                    emptySet()
                }
                withContext(Dispatchers.Main) {
                    _favoriteSongs.value = favoritesSet
                }
                syncLikedPlaylistWithFavorites(favoritesSet)
                Log.d(TAG, "Playlists successfully reloaded and synced from settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading playlists from settings", e)
            }
        }
    }

    /**
     * Enables or disables default playlists dynamically, updating Room storage and UI.
     */
    fun setDefaultPlaylistsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Setting default playlists enabled: $enabled")
                appSettings.setDefaultPlaylistsEnabled(enabled)
                val playlistDao = repository.playlistDao
                if (enabled) {
                    val currentDb = playlistDao.getAllPlaylists()
                    if (currentDb.none { it.id == "1" }) {
                        playlistDao.insertPlaylist(PlaylistEntity("1", "Liked", System.currentTimeMillis(), System.currentTimeMillis(), null))
                    }
                    if (currentDb.none { it.id == "2" }) {
                        playlistDao.insertPlaylist(PlaylistEntity("2", "Recently Added", System.currentTimeMillis(), System.currentTimeMillis(), null))
                    }
                    if (currentDb.none { it.id == "3" }) {
                        playlistDao.insertPlaylist(PlaylistEntity("3", "Most Played", System.currentTimeMillis(), System.currentTimeMillis(), null))
                    }
                } else {
                    playlistDao.deletePlaylistById("2")
                    playlistDao.deleteSongsFromPlaylist("2")
                    playlistDao.deletePlaylistById("3")
                    playlistDao.deleteSongsFromPlaylist("3")
                }
                loadSavedPlaylists()
                if (enabled) {
                    populateDefaultPlaylistsSafely()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling default playlists", e)
            }
        }
    }
    
    /**
     * Ensures current playlists and favorite songs are saved to persistent storage
     * Useful before creating a backup to ensure all data is included
     */
    fun ensurePlaylistsSaved() {
        try {
            if (!isPlaylistsLoaded) {
                Log.w(TAG, "ensurePlaylistsSaved skipped because playlists are not loaded")
                return
            }
            savePlaylistsJob?.cancel()
            runBlocking(Dispatchers.IO) {
                savePlaylistsToRoom(_playlists.value)
            }
            saveFavoriteSongs()
            Log.d(TAG, "Playlists and favorites explicitly saved to storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring playlists are saved", e)
        }
    }
    
    private fun loadSavedPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSavedPlaylistsInternal()
        }
    }

    private suspend fun loadSavedPlaylistsInternal() {
        playlistLock.withLock {
            try {
                val playlistDao = repository.playlistDao
                val defaultPlaylistsEnabled = appSettings.defaultPlaylistsEnabled.value
                var dbPlaylists = playlistDao.getAllPlaylists()
                
                // Ensure default playlists exist if enabled
                val currentIds = dbPlaylists.map { it.id }.toSet()
                var needsReload = false
                if (!currentIds.contains("1")) {
                    playlistDao.insertPlaylist(PlaylistEntity("1", "Liked", System.currentTimeMillis(), System.currentTimeMillis(), null))
                    needsReload = true
                }
                if (defaultPlaylistsEnabled) {
                    if (!currentIds.contains("2")) {
                        playlistDao.insertPlaylist(PlaylistEntity("2", "Recently Added", System.currentTimeMillis(), System.currentTimeMillis(), null))
                        needsReload = true
                    }
                    if (!currentIds.contains("3")) {
                        playlistDao.insertPlaylist(PlaylistEntity("3", "Most Played", System.currentTimeMillis(), System.currentTimeMillis(), null))
                        needsReload = true
                    }
                }
                if (needsReload) {
                    dbPlaylists = playlistDao.getAllPlaylists()
                }
                
                val playlists = if (dbPlaylists.isNotEmpty()) {
                    val songMap = _songs.value.associateBy { it.id }
                    val songStableKeyMap = lazy(LazyThreadSafetyMode.NONE) { _songs.value.associateBy { playlistSongStableKey(it) } }
                    val songStableKeyMedMap = lazy(LazyThreadSafetyMode.NONE) { _songs.value.associateBy { playlistSongStableKeyMed(it) } }
                    val songStableKeyLightMap = lazy(LazyThreadSafetyMode.NONE) { _songs.value.associateBy { playlistSongStableKeyLight(it) } }
                    val songStableKeyBasicMap = lazy(LazyThreadSafetyMode.NONE) { _songs.value.associateBy { playlistSongStableKeyBasic(it) } }
                    dbPlaylists.map { entity ->
                        val songIds = playlistDao.getSongIdsForPlaylist(entity.id)
                        val playlistSongs = songIds.map { songId ->
                            songMap[songId] ?: run {
                                val songEntity = repository.songDao.getSongById(songId)
                                if (songEntity != null) {
                                    val dbSong = Song(
                                        id = songEntity.id,
                                        title = songEntity.title,
                                        artist = songEntity.artist,
                                        album = songEntity.album,
                                        albumId = songEntity.albumId,
                                        duration = songEntity.duration,
                                        uri = (songEntity.uri).toUri(),
                                        artworkUri = songEntity.artworkUri?.let { (it).toUri() },
                                        trackNumber = songEntity.trackNumber,
                                        year = songEntity.year,
                                        genre = songEntity.genre,
                                        dateAdded = songEntity.dateAdded,
                                        dateModified = songEntity.dateModified.takeIf { it > 0L } ?: songEntity.dateAdded,
                                        albumArtist = songEntity.albumArtist,
                                        bitrate = songEntity.bitrate,
                                        sampleRate = songEntity.sampleRate,
                                        channels = songEntity.channels,
                                        codec = songEntity.codec,
                                        discNumber = songEntity.discNumber,
                                        path = songEntity.path
                                    )
                                    // Try to match the DB song (e.g. restored from backup) to a local scanned song by stable key
                                    resolveSongByStableKeys(
                                        dbSong,
                                        songStableKeyMap,
                                        songStableKeyMedMap,
                                        songStableKeyLightMap,
                                        songStableKeyBasicMap
                                    ) ?: dbSong
                                } else {
                                    // Stub song to preserve unresolved entries temporarily (e.g. unmounted SD card)
                                    Song(
                                        id = songId,
                                        title = getApplication<Application>().getString(R.string.unresolved_song),
                                        artist = getApplication<Application>().getString(R.string.unknown_artist_name),
                                        album = getApplication<Application>().getString(R.string.unknown_album_name),
                                        albumId = "",
                                        duration = 0L,
                                        uri = Uri.EMPTY,
                                        artworkUri = null,
                                        trackNumber = 0,
                                        year = 0,
                                        genre = null,
                                        dateAdded = System.currentTimeMillis(),
                                        dateModified = System.currentTimeMillis(),
                                        albumArtist = null,
                                        bitrate = null,
                                        sampleRate = null,
                                        channels = null,
                                        codec = null,
                                        discNumber = 1,
                                        path = null
                                    )
                                }
                            }
                        }
                        Playlist(
                            id = entity.id,
                            name = entity.name,
                            songs = playlistSongs,
                            dateCreated = entity.dateCreated,
                            dateModified = entity.dateModified,
                            artworkUri = entity.artworkUri?.let { (it).toUri() }
                        )
                    }
                } else {
                    val playlistsJson = appSettings.playlists.value
                    if (!playlistsJson.isNullOrBlank()) {
                        Log.i(TAG, "Legacy playlists found in SharedPreferences; starting migration to Room")
                        try {
                            val type = object : TypeToken<List<Playlist>>() {}.type
                            val legacyPlaylists: List<Playlist> = GsonUtils.gson.fromJson(playlistsJson, type)
                            
                            legacyPlaylists.forEach { playlist ->
                                val playlistEntity = PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    dateCreated = playlist.dateCreated,
                                    dateModified = playlist.dateModified,
                                    artworkUri = playlist.artworkUri?.toString()
                                )
                                playlistDao.insertPlaylist(playlistEntity)
                                
                                val songEntities = playlist.songs.mapIndexed { index, song ->
                                    PlaylistSongEntity(playlist.id, song.id, index)
                                }
                                playlistDao.insertPlaylistSongs(songEntities)
                            }
                            
                            appSettings.setPlaylists(null)
                            Log.i(TAG, "Successfully migrated ${legacyPlaylists.size} legacy playlists to Room database")
                            
                            dbPlaylists = playlistDao.getAllPlaylists()
                            val migrationSongMap = _songs.value.associateBy { it.id }
                            dbPlaylists.map { entity ->
                                val songIds = playlistDao.getSongIdsForPlaylist(entity.id)
                                val playlistSongs = songIds.map { songId ->
                                    migrationSongMap[songId] ?: run {
                                        val songEntity = repository.songDao.getSongById(songId)
                                        if (songEntity != null) {
                                            Song(
                                                id = songEntity.id,
                                                title = songEntity.title,
                                                artist = songEntity.artist,
                                                album = songEntity.album,
                                                albumId = songEntity.albumId,
                                                duration = songEntity.duration,
                                                uri = (songEntity.uri).toUri(),
                                                artworkUri = songEntity.artworkUri?.let { (it).toUri() },
                                                trackNumber = songEntity.trackNumber,
                                                year = songEntity.year,
                                                genre = songEntity.genre,
                                                dateAdded = songEntity.dateAdded,
                                                dateModified = songEntity.dateModified.takeIf { it > 0L } ?: songEntity.dateAdded,
                                                albumArtist = songEntity.albumArtist,
                                                bitrate = songEntity.bitrate,
                                                sampleRate = songEntity.sampleRate,
                                                channels = songEntity.channels,
                                                codec = songEntity.codec,
                                                discNumber = songEntity.discNumber,
                                                path = songEntity.path
                                            )
                                        } else {
                                            Song(
                                                id = songId,
                                                title = getApplication<Application>().getString(R.string.unresolved_song),
                                                artist = getApplication<Application>().getString(R.string.unknown_artist_name),
                                                album = getApplication<Application>().getString(R.string.unknown_album_name),
                                                albumId = "",
                                                duration = 0L,
                                                uri = Uri.EMPTY,
                                                artworkUri = null,
                                                trackNumber = 0,
                                                year = 0,
                                                genre = null,
                                                dateAdded = System.currentTimeMillis(),
                                                dateModified = System.currentTimeMillis(),
                                                albumArtist = null,
                                                bitrate = null,
                                                sampleRate = null,
                                                channels = null,
                                                codec = null,
                                                discNumber = 1,
                                                path = null
                                            )
                                        }
                                    }
                                }
                                Playlist(
                                    id = entity.id,
                                    name = entity.name,
                                    songs = playlistSongs,
                                    dateCreated = entity.dateCreated,
                                    dateModified = entity.dateModified,
                                    artworkUri = entity.artworkUri?.let { (it).toUri() }
                                )
                            }
                        } catch (migrationError: Exception) {
                            Log.e(TAG, "Error migrating legacy playlists to Room", migrationError)
                            emptyList()
                        }
                    } else {
                        val defaultPlaylistsEnabled = appSettings.defaultPlaylistsEnabled.value
                        val initialPlaylists = if (defaultPlaylistsEnabled) {
                            listOf(
                                Playlist("1", "Liked"),
                                Playlist("2", "Recently Added"),
                                Playlist("3", "Most Played")
                            )
                        } else {
                            listOf(
                                Playlist("1", "Liked")
                            )
                        }
                        
                        initialPlaylists.forEach { playlist ->
                            playlistDao.insertPlaylist(
                                PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    dateCreated = playlist.dateCreated,
                                    dateModified = playlist.dateModified,
                                    artworkUri = playlist.artworkUri?.toString()
                                )
                            )
                        }
                        initialPlaylists
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _playlists.value = playlists
                    isPlaylistsLoaded = true
                    
                    val favoriteSongsJson = appSettings.favoriteSongs.value
                    if (favoriteSongsJson != null) {
                        val type = object : TypeToken<Set<String>>() {}.type
                        _favoriteSongs.value = GsonUtils.gson.fromJson(favoriteSongsJson, type)
                    }
                    
                }
                
                refreshPlaylistSongsMetadata()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved playlists from Room", e)
                withContext(Dispatchers.Main) {
                    if (_playlists.value.isEmpty()) {
                        val defaultPlaylistsEnabled = appSettings.defaultPlaylistsEnabled.value
                        _playlists.value = if (defaultPlaylistsEnabled) {
                            listOf(
                                Playlist("1", "Liked"),
                                Playlist("2", "Recently Added"),
                                Playlist("3", "Most Played")
                            )
                        } else {
                            listOf(
                                Playlist("1", "Liked")
                            )
                        }
                    }
                    isPlaylistsLoaded = true
                    _favoriteSongs.value = emptySet()
                }
            }
        }
    }
    
    /**
     * Refreshes the metadata of songs in playlists by matching them with the current songs list.
     * This fixes the issue where playlist songs have outdated metadata if they were added before
     * the metadata was edited.
     */
    private fun refreshPlaylistSongsMetadata() {
        try {
            val currentSongs = _songs.value
            if (currentSongs.isEmpty()) {
                Log.d(TAG, "No songs loaded yet, skipping playlist metadata refresh")
                return
            }
            
            // Create a map of song ID to Song for fast lookup
            val songMap = currentSongs.associateBy { it.id }
            var updatedCount = 0
            
            // Update each playlist's songs with current metadata
            _playlists.value = _playlists.value.map { playlist ->
                val updatedSongs = playlist.songs.mapNotNull { playlistSong ->
                    // Find the current version of the song by ID
                    val currentSong = songMap[playlistSong.id]
                    if (currentSong != null) {
                        // Check if metadata has changed
                        if (currentSong != playlistSong) {
                            updatedCount++
                        }
                        currentSong
                    } else {
                        // Song no longer exists in library, keep the old version
                        // (user might have deleted the file but want to keep it in playlist)
                        playlistSong
                    }
                }
                
                if (updatedSongs != playlist.songs) {
                    playlist.copy(songs = updatedSongs)
                } else {
                    playlist
                }
            }
            
            if (updatedCount > 0) {
                Log.d(TAG, "Refreshed metadata for $updatedCount songs across ${_playlists.value.size} playlists")
                // Save the updated playlists to persist the metadata changes
                savePlaylists()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing playlist songs metadata", e)
        }
    }

    private var savePlaylistsJob: Job? = null

    private fun savePlaylists() {
        val currentPlaylists = _playlists.value.map { playlist ->
            playlist.copy(songs = playlist.songs.filterNot { it.id.startsWith("rhythm-catalog:") })
        }
        if (currentPlaylists != _playlists.value) _playlists.value = currentPlaylists
        savePlaylistsJob?.cancel()
        savePlaylistsJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200) // Debounce rapid consecutive mutations
            savePlaylistsToRoom(currentPlaylists)
        }
    }

    private suspend fun savePlaylistsToRoom(currentPlaylists: List<Playlist> = _playlists.value) {
        val persistablePlaylists = currentPlaylists.map { playlist ->
            playlist.copy(songs = playlist.songs.filterNot { it.id.startsWith("rhythm-catalog:") })
        }
        if (!isPlaylistsLoaded) {
            Log.w(TAG, "Skipping savePlaylistsToRoom — playlists have not finished loading yet")
            return
        }
        if (persistablePlaylists.isEmpty()) {
            Log.w(TAG, "Skipping savePlaylistsToRoom — currentPlaylists list is empty")
            return
        }
        playlistLock.withLock {
            try {
                val roomDb = RhythmDatabase.getInstance(getApplication())
                val playlistDao = repository.playlistDao
                
                roomDb.withTransaction {
                    val dbPlaylists = playlistDao.getAllPlaylists()
                    val currentPlaylistIds = persistablePlaylists.map { it.id }.toSet()
                    
                    dbPlaylists.forEach { dbPlaylist ->
                        if (!currentPlaylistIds.contains(dbPlaylist.id)) {
                            playlistDao.deletePlaylistById(dbPlaylist.id)
                            playlistDao.deleteSongsFromPlaylist(dbPlaylist.id)
                            Log.d(TAG, "Deleted playlist ID ${dbPlaylist.id} from Room")
                        }
                    }
                    
                    persistablePlaylists.forEach { playlist ->
                        val entity = PlaylistEntity(
                            id = playlist.id,
                            name = playlist.name,
                            dateCreated = playlist.dateCreated,
                            dateModified = playlist.dateModified,
                            artworkUri = playlist.artworkUri?.toString()
                        )
                        playlistDao.insertPlaylist(entity)
                        
                        playlistDao.deleteSongsFromPlaylist(playlist.id)
                        if (playlist.songs.isNotEmpty()) {
                            val songEntities = playlist.songs.mapIndexed { index, song ->
                                PlaylistSongEntity(
                                    playlistId = playlist.id,
                                    songId = song.id,
                                    orderIndex = index
                                )
                            }
                            songEntities.chunked(500).forEach { chunk ->
                                playlistDao.insertPlaylistSongs(chunk)
                            }
                        }
                    }

                    // Persist distinct songs across all playlists in chunks to avoid redundant allocations
                    val distinctDbSongEntities = currentPlaylists.asSequence()
                        .flatMap { it.songs.asSequence() }
                        .distinctBy { it.id }
                        .map { song ->
                            io.github.cluno1.sonorus.features.local.data.database.entity.SongEntity(
                                id = song.id,
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                                albumId = song.albumId,
                                duration = song.duration,
                                uri = song.uri.toString(),
                                artworkUri = song.artworkUri?.toString(),
                                trackNumber = song.trackNumber,
                                year = song.year,
                                genre = song.genre,
                                dateAdded = song.dateAdded,
                                dateModified = song.dateModified,
                                albumArtist = song.albumArtist,
                                bitrate = song.bitrate,
                                sampleRate = song.sampleRate,
                                channels = song.channels,
                                codec = song.codec,
                                discNumber = song.discNumber,
                                path = song.path
                            )
                        }
                        .toList()

                    if (distinctDbSongEntities.isNotEmpty()) {
                        distinctDbSongEntities.chunked(500).forEach { chunk ->
                            repository.songDao.upsertAll(chunk)
                        }
                    }
                }
                Log.d(TAG, "Successfully saved ${persistablePlaylists.size} playlists to Room")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playlists to Room", e)
            }
        }
    }

    private fun saveFavoriteSongs() {
        try {
            val favoriteSongsJson = GsonUtils.gson.toJson(_favoriteSongs.value)
            appSettings.setFavoriteSongs(favoriteSongsJson)
            Log.d(TAG, "Saved ${_favoriteSongs.value.size} favorite songs")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorite songs", e)
        }
    }

    /**
     * Fetches artist images and album artwork from the internet for items that don't have them
     */
    private suspend fun fetchArtworkFromInternet(
        fetchArtists: Boolean = true,
        fetchAlbumsAndSongs: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val artistSource = appSettings.artistArtworkSource.value
        val isAutoFetchActive = appSettings.isAutoFetchArtworkActive.value
        if (!isAutoFetchActive && artistSource == io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED) {
            Log.d(TAG, "isAutoFetchArtworkActive and artist artwork are disabled, skipping internet artwork fetch")
            return@withContext
        }
        try {
            if (fetchArtists && artistSource != io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED) {
                Log.d(TAG, "Fetching artist images")
                val missingArtists = _artists.value.filter { it.artworkUri == null }
                Log.d(TAG, "Found ${missingArtists.size} artists without images out of ${_artists.value.size} total artists")
                val chunkSize = 10
                for (batch in missingArtists.chunked(chunkSize)) {
                    val updatedArtists = repository.fetchArtistImages(batch)
                    if (updatedArtists.isNotEmpty()) {
                        val artistMap: Map<String, Artist> = updatedArtists.associateBy { a: Artist -> a.id }
                        _artists.value = _artists.value.map { artist: Artist -> artistMap[artist.id] ?: artist }
                        Log.d(TAG, "Updated ${updatedArtists.size} artists with images from internet (batch)")
                    }
                    // Throttle between batches to avoid hitting API rate limits
                    delay(1000)
                }
            }
            
            if (fetchAlbumsAndSongs) {
                Log.d(TAG, "Fetching album artwork from internet")
                // Only fetch for a subset of albums to avoid overwhelming the API
                // Check for albums with genuinely missing or unreadable cover art URIs
                val albumsToUpdate = _albums.value.filter { album ->
                    album.artworkUri == null && album.songs.any {
                        DeviceMetadataPolicy.isEligible(it.id, it.uri.scheme)
                    }
                }.take(10)
                
                Log.d(TAG, "Found ${albumsToUpdate.size} albums that genuinely need artwork out of ${_albums.value.size} total albums")
                if (albumsToUpdate.isNotEmpty()) {
                    Log.d(TAG, "Albums to update: ${albumsToUpdate.map { "${it.artist} - ${it.title}" }}")
                    val updatedAlbums = repository.fetchAlbumArtwork(albumsToUpdate)
                    // Update only the albums we fetched, keeping the rest unchanged
                    val albumMap: Map<String, Album> = updatedAlbums.associateBy { a: Album -> a.id }
                    _albums.value = _albums.value.map { album: Album -> albumMap[album.id] ?: album }
                    Log.d(TAG, "Updated ${updatedAlbums.size} albums with artwork from internet")
                } else {
                    Log.d(TAG, "No albums found that need artwork")
                }

                val songsToUpdate = _songs.value.filter { song ->
                    val uri = song.artworkUri
                    if (uri == null || uri == Uri.EMPTY) true
                    else {
                        val str = uri.toString().trim()
                        if (str.isEmpty() || str == "null" || str == "content://media/external/audio/albumart/0") true
                        else if (str.startsWith("http://") || str.startsWith("https://")) false
                        else !repository.isArtworkReadable(uri)
                    }
                }.take(40)
                if (songsToUpdate.isNotEmpty()) {
                    val updatedSongs = repository.fetchTrackArtwork(songsToUpdate)
                    val songMap: Map<String, Song> = updatedSongs.associateBy { song: Song -> song.id }
                    val mergedSongs: List<Song> = _songs.value.map { song: Song ->
                        songMap[song.id] ?: song
                    }
                    _songs.value = mergedSongs
                    Log.d(TAG, "Updated ${updatedSongs.count { s: Song -> s.artworkUri != null }} songs with fallback artwork")
                    // Persist the updated songs list so artwork edits survive restarts.
                    repository.updateAndPersistSongs(mergedSongs)
                } else {
                    Log.d(TAG, "No songs found that need fallback artwork")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artwork from internet", e)
        }
    }

    /**
     * Auto-fetches artwork from online metadata providers for a specific song,
     * updates the song in memory & repository, and invokes onResult callback with success & artwork Uri string.
     */
    fun autoFetchArtworkForSong(song: Song, onResult: (Boolean, String?) -> Unit) {
        if (!DeviceMetadataPolicy.isEligible(song.id, song.uri.scheme)) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isFetchingArtwork.value = true
                val updated = repository.fetchTrackArtwork(listOf(song))
                val resultSong = updated.firstOrNull()
                if (resultSong?.artworkUri != null && resultSong.artworkUri != song.artworkUri) {
                    val currentList = _songs.value.map { if (it.id == song.id) resultSong else it }
                    val projected = repository.projectDeviceArtwork(currentList)
                    if (projected.any { it.id == song.id }) {
                        _songs.value = projected
                        repository.updateAndPersistSongs(projected)
                        _albums.value = repository.loadAlbums()
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, resultSong.artworkUri.toString())
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in autoFetchArtworkForSong", e)
                withContext(Dispatchers.Main) {
                    onResult(false, null)
                }
            } finally {
                _isFetchingArtwork.value = false
            }
        }
    }
    
    /**
     * Refreshes artwork for a specific artist
     */
    fun refreshArtistImage(artistId: String) {
        viewModelScope.launch {
            if (appSettings.artistArtworkSource.value == io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource.DISABLED) return@launch
            val artist = _artists.value.find { it.id == artistId } ?: return@launch
            try {
                val updatedArtists = repository.fetchArtistImages(listOf<Artist>(artist))
                if (updatedArtists.isNotEmpty()) {
                    val updatedArtist = updatedArtists.first()
                    _artists.value = _artists.value.map { 
                        if (it.id == artistId) updatedArtist else it 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing artist image", e)
            }
        }
    }
    
    /**
     * Refreshes artwork for a specific album
     */
    fun refreshAlbumArtwork(albumId: String) {
        viewModelScope.launch {
            val album = _albums.value.find { it.id == albumId } ?: return@launch
            try {
                val deviceSong = album.songs.firstOrNull {
                    DeviceMetadataPolicy.isEligible(it.id, it.uri.scheme)
                }
                if (deviceSong != null) {
                    val artwork = repository.rematchDeviceAlbumArtwork(deviceSong)
                    if (artwork != null) {
                        val projected = repository.projectDeviceArtwork(_songs.value)
                        _songs.value = projected
                        repository.updateAndPersistSongs(projected)
                        _albums.value = repository.loadAlbums()
                    }
                    return@launch
                }
                val updatedAlbums = repository.fetchAlbumArtwork(listOf<Album>(album))
                if (updatedAlbums.isNotEmpty()) {
                    val updatedAlbum = updatedAlbums.first()
                    _albums.value = _albums.value.map { 
                        if (it.id == albumId) updatedAlbum else it 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing album artwork", e)
            }
        }
    }

    /**
     * Connect to the media service
     */
    fun connectToMediaService(forceReconnect: Boolean = false) {
        if (!forceReconnect && _serviceConnected.value && mediaController != null) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!forceReconnect && now - lastControllerConnectAttemptMs < CONTROLLER_CONNECT_MIN_INTERVAL_MS) {
            return
        }

        if (!controllerConnectInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Media controller connection already in progress")
            return
        }

        lastControllerConnectAttemptMs = now
        Log.d(TAG, "Connecting to media service")
        val context = getApplication<Application>()
        
        // Start the service first to ensure it's running.
        val serviceIntent = Intent(context, MediaPlaybackService::class.java)
        serviceIntent.action = MediaPlaybackService.ACTION_INIT_SERVICE

        val serviceStarted = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = serviceIntent,
            logTag = TAG,
            reason = "connect_to_media_service"
        )
        if (!serviceStarted) {
            Log.w(TAG, "Failed to start media service before controller connection")
            // Continue attempting controller connection; the service may already be alive.
        }

        releaseStaleControllerConnection()
        
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )

        try {
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture?.addListener({
                try {
                    mediaController = controllerFuture?.get()
                    Log.d(TAG, "Media controller initialized: $mediaController")

                    if (mediaController != null) {
                        mediaController?.addListener(playerListener)
                        _serviceConnected.value = true
                        
                        // Check watchdog state initially
                        checkPlaybackWatchdogState()

                        // Update shuffle and repeat mode from controller
                        mediaController?.let { controller ->
                            if (appSettings.shuffleUsesExoplayer.value) {
                                _isShuffleEnabled.value = controller.shuffleModeEnabled
                            } else {
                                _isShuffleEnabled.value = appSettings.savedShuffleState.value
                            }
                            val controllerRepeatMode = controller.repeatMode
                            _repeatMode.value = controllerRepeatMode

                            // Restore saved shuffle and repeat states if not currently playing and persistence is enabled
                            if (!controller.isPlaying) {
                                if (appSettings.shuffleModePersistence.value) {
                                    val savedShuffle = appSettings.savedShuffleState.value
                                    val useExoPlayerShuffle = appSettings.shuffleUsesExoplayer.value
                                    val targetControllerShuffle = savedShuffle && useExoPlayerShuffle

                                    Log.d(
                                        TAG,
                                        "Restoring saved shuffle state: $savedShuffle (useExoPlayerShuffle=$useExoPlayerShuffle, controllerTarget=$targetControllerShuffle)"
                                    )

                                    if (controller.shuffleModeEnabled != targetControllerShuffle) {
                                        controller.shuffleModeEnabled = targetControllerShuffle
                                    }

                                    // In manual shuffle mode, keep UI state from persisted value while controller shuffle stays off.
                                    _isShuffleEnabled.value = if (useExoPlayerShuffle) targetControllerShuffle else savedShuffle
                                }

                                if (appSettings.repeatModePersistence.value) {
                                    val savedRepeat = appSettings.savedRepeatMode.value
                                    Log.d(TAG, "Restoring saved repeat mode: $savedRepeat")

                                    if (controller.repeatMode != savedRepeat) {
                                        controller.repeatMode = savedRepeat
                                        _repeatMode.value = savedRepeat
                                    }
                                }
                            }

                            // Restore saved playback speed and pitch
                            val savedSpeed = appSettings.playbackSpeed.value
                            val savedPitch = appSettings.playbackPitch.value
                            Log.d(TAG, "Restoring saved playback speed: $savedSpeed, pitch: $savedPitch")
                            if (controller.playbackParameters.speed != savedSpeed || controller.playbackParameters.pitch != savedPitch) {
                                controller.playbackParameters = androidx.media3.common.PlaybackParameters(savedSpeed, savedPitch)
                            }

                            Log.d(TAG, "Initial repeat mode from controller: $controllerRepeatMode (${
                            when(controllerRepeatMode) {
                                Player.REPEAT_MODE_OFF -> "OFF"
                                Player.REPEAT_MODE_ONE -> "ONE"
                                Player.REPEAT_MODE_ALL -> "ALL"
                                else -> "UNKNOWN"
                            }
                        })")

                            // Sync initial volume from controller if not using system volume
                            if (!appSettings.useSystemVolume.value) {
                                _volume.value = controller.volume
                                if (controller.volume > 0f) {
                                    _isMuted.value = false
                                }
                            }

                            // Sync playback state with controller when app is reopened
                            val isActuallyPlaying = controller.isPlaying
                            _isPlaying.value = isActuallyPlaying
                            playbackControlStateMachine.update(
                                mediaId = controller.currentMediaItem?.mediaId,
                                readiness = controller.playbackState.toPlaybackReadiness(),
                                playWhenReady = controller.playWhenReady,
                            )
                            Log.d(TAG, "Syncing playback state on controller init: isPlaying=$isActuallyPlaying")

                            // Update duration and start progress updates if playing
                            if (isActuallyPlaying) {
                                val controllerDuration = controller.duration.takeIf { it > 0 }
                                if (controllerDuration != null) {
                                    _duration.value = controllerDuration
                                } else {
                                    _duration.value = resolvePlaybackDuration(controller)
                                }
                                startProgressUpdates()
                            }
                        }

                        // Check if we have a current song after initializing controller
                        updateCurrentSong()

                        // Restore queue if persistence is enabled and queue exists
                        restoreQueueAfterControllerReady()

                        // Debug the queue state after initialization
                        debugQueueState()

                        // Check if we have a pending queue to play
                        pendingQueueToPlay?.let { songs ->
                            Log.d(TAG, "Playing pending queue with ${songs.size} songs")
                            playQueue(songs)
                            pendingQueueToPlay = null
                        }

                        resumePlaybackAfterRhythmGuardTimeoutIfNeeded(source = "controller connected")
                    } else {
                        _serviceConnected.value = false
                        Log.e(TAG, "Failed to get media controller")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing media controller", e)
                    _serviceConnected.value = false
                } finally {
                    controllerConnectInFlight.set(false)
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e(TAG, "Error building media controller future", e)
            _serviceConnected.value = false
            controllerConnectInFlight.set(false)
        }
    }

    private fun releaseStaleControllerConnection() {
        playbackControlStateMachine.clear()
        mediaController?.let { existingController ->
            try {
                existingController.removeListener(playerListener)
                stopPlaybackWatchdog()
            } catch (e: Exception) {
                Log.w(TAG, "Error removing listener from stale controller", e)
            }

            try {
                existingController.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing stale controller", e)
            }
        }
        mediaController = null

        controllerFuture?.let { existingFuture ->
            try {
                MediaController.releaseFuture(existingFuture)
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing stale controller future", e)
            }
        }
        controllerFuture = null
        _serviceConnected.value = false
    }

    private fun checkPlaybackWatchdogState() {
        val controller = mediaController
        if (controller != null && controller.playWhenReady) {
            if (playbackWatchdogJob == null || playbackWatchdogJob?.isActive == false) {
                startPlaybackWatchdog()
            }
        } else {
            stopPlaybackWatchdog()
        }
    }

    private fun startPlaybackWatchdog() {
        playbackWatchdogJob?.cancel()
        playbackWatchdogJob = viewModelScope.launch {
            Log.d(TAG, "Playback watchdog started")
            var lastPosition = -1L
            var lastProgressTime = System.currentTimeMillis()
            
            while (isActive) {
                delay(1000) // check every second
                val controller = mediaController ?: continue
                val state = controller.playbackState
                
                // If ended or idle, reset the progress timers and continue
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    lastPosition = -1L
                    lastProgressTime = System.currentTimeMillis()
                    continue
                }
                
                if (controller.playWhenReady) {
                    val currentPos = controller.currentPosition
                    if (lastPosition == -1L) {
                        lastPosition = currentPos
                        lastProgressTime = System.currentTimeMillis()
                    } else {
                        val progress = currentPos - lastPosition
                        if (progress != 0L) {
                            lastPosition = currentPos
                            lastProgressTime = System.currentTimeMillis()
                        } else {
                            val noProgressDuration = System.currentTimeMillis() - lastProgressTime
                            if (noProgressDuration >= 10000L) { // 10 seconds timeout
                                Log.w(TAG, "Playback watchdog: Stuck/looping at position $currentPos (progress: $progress ms) in state $state for $noProgressDuration ms. Triggering corruption dialog.")
                                
                                // Determine the scenario-specific error message
                                val scenarioMessage = when {
                                    isSeeking.value -> "The player got stuck loading while seeking. The file may have corrupted indexes or metadata."
                                    state == Player.STATE_BUFFERING -> "The song is stuck loading/buffering indefinitely. This usually indicates a corrupted or unreadable audio stream."
                                    else -> "The player is unable to make progress during playback. The file format or encoding might be corrupted."
                                }
                                
                                triggerTrackCorruption(controller, scenarioMessage)
                                break
                            }
                        }
                    }
                } else {
                    lastPosition = -1L
                    lastProgressTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun stopPlaybackWatchdog() {
        playbackWatchdogJob?.cancel()
        playbackWatchdogJob = null
    }

    private fun triggerTrackCorruption(controller: MediaController, message: String) {
        playbackControlStateMachine.update(
            mediaId = controller.currentMediaItem?.mediaId ?: playbackControlUiState.value.mediaId,
            readiness = PlaybackReadiness.ERROR,
            playWhenReady = false,
        )
        viewModelScope.launch {
            try {
                controller.pause()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause controller on corruption detection", e)
            }
            _corruptedTrackName.value = _currentSong.value?.title ?: "Unknown Song"
            _corruptedTrackMessage.value = message
            _showCorruptionDialog.value = true
            stopPlaybackWatchdog()
        }
    }
    
    // Private initialization method (called from init)
    private fun initializeController() {
        connectToMediaService()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player error encountered in ViewModel: ${error.message}", error)
            playbackControlStateMachine.update(
                mediaId = mediaController?.currentMediaItem?.mediaId
                    ?: playbackControlUiState.value.mediaId,
                readiness = PlaybackReadiness.ERROR,
                playWhenReady = false,
            )
            if (appSettings.trackErrorCheckerEnabled.value) {
                _corruptedTrackName.value = _currentSong.value?.title ?: "Unknown Song"
                _corruptedTrackMessage.value = "The player encountered a playback error: ${error.message ?: "Unknown error"}"
                _showCorruptionDialog.value = true
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed: $playbackState")
            
            // Update buffering state
            _isBuffering.value = playbackState == Player.STATE_BUFFERING

            // Check watchdog state when playback state changes
            checkPlaybackWatchdogState()
            
            // Update isPlaying based on both playbackState and controller.isPlaying
            mediaController?.let { controller ->
                // The isPlaying value should be true only when both:
                // 1. The player is in STATE_READY
                // 2. controller.isPlaying is true
                val shouldBePlaying = playbackState == Player.STATE_READY && controller.isPlaying
                
                if (_isPlaying.value != shouldBePlaying) {
                    Log.d(TAG, "Updating isPlaying from ${_isPlaying.value} to $shouldBePlaying")
                    _isPlaying.value = shouldBePlaying
                }
                
                if (playbackState == Player.STATE_READY) {
                    // Always prefer controller.duration; only fallback if unset (for UI robustness)
                    val controllerDuration = controller.duration.takeIf { it > 0 }
                    if (controllerDuration != null) {
                        _duration.value = controllerDuration
                        Log.d(TAG, "Duration updated from controller: $controllerDuration")
                        
                        // Update progress immediately for better UI responsiveness
                        val currentProgress = controller.currentPosition.toFloat() / controllerDuration.toFloat()
                        _progress.value = currentProgress.coerceIn(0f, 1f)
                    } else {
                        // Only use fallback if controller duration is not yet available
                        val fallbackDuration = resolvePlaybackDuration(controller)
                        if (fallbackDuration > 0) {
                            _duration.value = fallbackDuration
                            Log.d(TAG, "Duration fallback: $fallbackDuration (controller duration not ready)")
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    // Handle playback completion - ensure progress is updated to the end
                    _progress.value = 1.0f
                    progressUpdateJob?.cancel()
                    Log.d(TAG, "Playback completed")
                    
                    // If repeat mode is off and we're at the end of the queue, stop playback
                    if (controller.repeatMode == Player.REPEAT_MODE_OFF && 
                        controller.currentMediaItemIndex == controller.mediaItemCount - 1) {
                        controller.pause()
                        _isPlaying.value = false
                    }
                }
            }
            
            // Restart progress updates when playback state changes
            if (_isPlaying.value) {
                startProgressUpdates()
            } else {
                progressUpdateJob?.cancel()
            }
        }

        override fun onVolumeChanged(volume: Float) {
            Log.d(TAG, "onVolumeChanged from controller: $volume")
            if (!appSettings.useSystemVolume.value) {
                if (_volume.value != volume) {
                    _volume.value = volume
                }
                if (volume > 0f) {
                    _isMuted.value = false
                    appSettings.setAppVolume(volume)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "Media item transition: ${mediaItem?.mediaId}, reason: $reason")

            if (mediaItem?.mediaId != playbackControlUiState.value.mediaId) {
                playbackControlStateMachine.beginLoading(
                    mediaId = mediaItem?.mediaId,
                    playWhenReady = mediaController?.playWhenReady == true,
                )
            }

            val transitionSong = mediaItem?.let(::resolveSongFromMediaItem)
            val transitionCatalogEntry = mediaItem?.mediaId?.let { mediaId ->
                val stableMediaId = mediaId.toStableCatalogSongId()
                _catalogQueue.value.firstOrNull {
                    it.playback.toMediaItem().mediaId.toStableCatalogSongId() == stableMediaId
                }
            }
            val transitionSubject = transitionSong?.toPlaybackSubject(transitionCatalogEntry)
            if (transitionSubject == null) {
                finalizePlaybackTracking()
            } else if (currentPlaybackSubject?.subjectId != transitionSubject.subjectId) {
                finalizePlaybackTracking()
                startPlaybackTracking(transitionSubject, transitionSong)
            }

            if (mediaItem?.mediaId != null && mediaItem.mediaId == _currentSong.value?.id) {
                Log.d(TAG, "Ignoring media item transition for same song: ${mediaItem.mediaId}")
                return
            }

            // Restart/reset watchdog for the new song
            if (mediaController?.playWhenReady == true) {
                startPlaybackWatchdog()
            } else {
                stopPlaybackWatchdog()
            }
            
            // Reset progress to 0 for immediate UI feedback
            _progress.value = 0f
            
            // Update current song and queue position
            mediaItem?.let { item ->
                val songId = item.mediaId
                val song = transitionSong ?: resolveSongFromMediaItem(item)
                
                if (song != null) {
                    _currentSong.value = song
                    val catalogEntry = transitionCatalogEntry ?: _catalogQueue.value.firstOrNull {
                        it.playback.toMediaItem().mediaId.toStableCatalogSongId() ==
                            songId.toStableCatalogSongId()
                    }
                    _catalogNowPlaying.value = catalogEntry?.nowPlaying
                    applyCatalogLyrics(catalogEntry?.nowPlaying)
                    if (catalogEntry != null) {
                        if (mediaController?.playWhenReady == true) {
                            enqueueCatalogAutoCache(catalogEntry.nowPlaying)
                        }
                    }
                    // Apply default playback speed if enabled for new songs
                    if (appSettings.useDefaultPlaybackSpeed.value) {
                        val defaultSpeed = appSettings.defaultPlaybackSpeed.value
                        setPlaybackSpeed(defaultSpeed)
                    }
                    
                    // Update favorite status
                    _isFavorite.value = _favoriteSongs.value.contains(song.id.toStableCatalogSongId())
                    
                    // Update queue position - comprehensive logic from both listeners.
                    // Resolve the position against the controller's actual playback order
                    // (shuffle-aware traversal under the shuffle engine, plain timeline order
                    // otherwise) instead of a possibly-stale local list. This keeps the
                    // displayed index linear while shuffling instead of jumping around, and
                    // self-corrects the queue after restores or queue edits.
                    val previousIndex = _currentQueue.value.currentIndex
                    syncQueueWithMediaController()
                    if (_currentQueue.value.currentIndex != previousIndex) {
                        saveQueueToPersistence()
                    }
                    
                    // Fetch lyrics for the new song
                    fetchLyricsForCurrentSong()
                    
                    // Force a duration update - prefer controller.duration
                    mediaController?.let { controller ->
                        val controllerDuration = controller.duration.takeIf { it > 0 }
                        if (controllerDuration != null) {
                            _duration.value = controllerDuration
                        } else {
                            // Fallback if controller duration not yet available
                            _duration.value = resolvePlaybackDuration(controller)
                        }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Is playing changed: $isPlaying")

            if (isPlaying && isRhythmGuardTimeoutActive()) {
                enforceRhythmGuardTimeout(reason = "onIsPlayingChanged")
                return
            }
            
            // Only update if the value is different to avoid unnecessary UI updates
            if (_isPlaying.value != isPlaying) {
                Log.d(TAG, "Updating isPlaying state from ${_isPlaying.value} to $isPlaying")
                _isPlaying.value = isPlaying
            }

            // Check watchdog state when isPlaying changes
            checkPlaybackWatchdogState()
            
            // Track play/pause for accurate stats
            if (isPlaying) {
                _catalogNowPlaying.value?.let { catalogItem ->
                    enqueueCatalogAutoCache(catalogItem)
                }
                resumePlaybackTracking()
                addCurrentPlaybackToRecentIfNeeded()
                startProgressUpdates()
            } else {
                pausePlaybackTracking()
                progressUpdateJob?.cancel()

                // Persist pause position for resume-after-restart, but avoid wiping
                // persisted queue state during early startup callbacks.
                if (mediaController?.playWhenReady == false && _currentQueue.value.songs.isNotEmpty()) {
                    saveQueueToPersistence()
                }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            // Commit the controls from one Media3 snapshot. isPlaying and buffering continue to
            // drive legacy business logic independently, but UI buttons never combine them.
            playbackControlStateMachine.update(
                mediaId = player.currentMediaItem?.mediaId,
                readiness = player.playbackState.toPlaybackReadiness(),
                playWhenReady = player.playWhenReady,
            )
        }
        
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Log.d(TAG, "Shuffle mode changed: $shuffleModeEnabled")

            if (appSettings.shuffleUsesExoplayer.value) {
                // Only update if the state actually changed to avoid unnecessary updates
                if (_isShuffleEnabled.value != shuffleModeEnabled) {
                    _isShuffleEnabled.value = shuffleModeEnabled

                    // Persist controller-driven shuffle only when ExoPlayer shuffle engine is enabled.
                    if (appSettings.shuffleModePersistence.value) {
                        appSettings.setSavedShuffleState(shuffleModeEnabled)
                    }
                }

                if (shuffleModeEnabled && !queueStateHolder.hasOriginalQueue() && _currentQueue.value.songs.isNotEmpty()) {
                    queueStateHolder.saveOriginalQueueState(
                        _currentQueue.value.songs,
                        queueStateHolder.currentQueueSourceName.value
                    )
                }

                viewModelScope.launch {
                    delay(75)
                    syncQueueWithMediaController()
                }
            }
        }
        
        override fun onRepeatModeChanged(repeatMode: Int) {
            Log.d(TAG, "Repeat mode changed: $repeatMode")
            _repeatMode.value = repeatMode
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (appSettings.shuffleUsesExoplayer.value && mediaController?.shuffleModeEnabled == true) {
                syncQueueWithMediaController()
            }
        }
    }

    private fun Int.toPlaybackReadiness(): PlaybackReadiness = when (this) {
        Player.STATE_BUFFERING -> PlaybackReadiness.BUFFERING
        Player.STATE_READY -> PlaybackReadiness.READY
        Player.STATE_ENDED -> PlaybackReadiness.ENDED
        else -> PlaybackReadiness.IDLE
    }
    
    private fun startProgressUpdates() {
        // Cancel existing job if running
        progressUpdateJob?.cancel()
        
        // Reset progress to 0 when starting a new song
        _progress.value = 0f
        
        // Start a new coroutine to update progress based on playing state
        progressUpdateJob = viewModelScope.launch {
            isPlaying.collectLatest { playing ->
                if (playing) {
                    while (isActive) {
                        updateProgress()
                        delay(100) // Update every 100ms for smooth progress
                    }
                } else {
                    // Update one last time when paused/stopped
                    updateProgress()
                }
            }
        }
    }
    
    private fun updateProgress() {
        mediaController?.let { controller ->
            val playbackDuration = resolvePlaybackDuration(controller)
            if (playbackDuration > 0) {
                val currentProgress = controller.currentPosition.toFloat() / playbackDuration.toFloat()
                _progress.value = currentProgress.coerceIn(0f, 1f)
            }

            maybeBroadcastBluetoothLyricsLine(controller)
        }
    }

    private fun resolvePlaybackDuration(controller: MediaController): Long {
        val controllerDuration = controller.duration.takeIf { it > 0 }
        if (controllerDuration != null) {
            return controllerDuration
        }

        val metadataDuration = controller.currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0 }
        if (metadataDuration != null) {
            return metadataDuration
        }

        return _currentSong.value?.duration?.takeIf { it > 0 } ?: 0L
    }

    private fun maybeBroadcastBluetoothLyricsLine(controller: MediaController) {
        val song = _currentSong.value ?: return
        val currentLyricsVal = _currentLyrics.value
        
        // Track raw lyrics key (synced or plain) to send updates to service
        val rawLyricsKey = currentLyricsVal?.syncedLyrics ?: currentLyricsVal?.plainLyrics ?: ""
        if (cachedSyncedLyricsRaw != rawLyricsKey) {
            cachedSyncedLyricsRaw = rawLyricsKey
            val synced = currentLyricsVal?.syncedLyrics
            if (!synced.isNullOrBlank()) {
                cachedParsedSyncedLyrics = LyricsParser.parseLyrics(synced)
            } else {
                cachedParsedSyncedLyrics = emptyList()
            }
            lastBroadcastLyricLine = null
            lastAppliedBluetoothLyricLine = null
            
            // Send lyrics data to service for launcher widget
            try {
                val args = Bundle().apply {
                    putStringArrayList("lyric_texts", ArrayList(cachedParsedSyncedLyrics.map { it.text }))
                    putStringArrayList("lyric_translations", ArrayList(cachedParsedSyncedLyrics.map { it.translation ?: "" }))
                    putStringArrayList("lyric_romanizations", ArrayList(cachedParsedSyncedLyrics.map { it.romanization ?: "" }))
                    putLongArray("lyric_timestamps", cachedParsedSyncedLyrics.map { it.timestamp }.toLongArray())
                    val plain = currentLyricsVal?.plainLyrics
                    if (plain != null) {
                        putString("plain_lyrics", plain)
                    }
                }
                controller.sendCustomCommand(
                    androidx.media3.session.SessionCommand("UPDATE_LYRICS_DATA", Bundle.EMPTY),
                    args
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send UPDATE_LYRICS_DATA command", e)
            }
        }

        val lyricLine = resolveCurrentSyncedLyricLine(
            syncedLyrics = currentLyricsVal?.syncedLyrics,
            currentPositionMs = controller.currentPosition,
            syncOffsetMs = _lyricsTimeOffset.value.toLong()
        )

        // Determine active lyric index
        val effectivePosition = (controller.currentPosition + _lyricsTimeOffset.value.toLong()).coerceAtLeast(0L)
        val activeIndex = if (cachedParsedSyncedLyrics.isNotEmpty()) {
            cachedParsedSyncedLyrics.indexOfLast { it.timestamp <= effectivePosition }
        } else {
            -1
        }

        // Send active lyric line to service for status bar lyrics and widgets
        if (lyricLine != lastBroadcastLyricLine) {
            try {
                val args = Bundle().apply {
                    putString("lyric_line", lyricLine)
                    putInt("lyric_index", activeIndex)
                }
                controller.sendCustomCommand(
                    androidx.media3.session.SessionCommand("UPDATE_ACTIVE_LYRIC", Bundle.EMPTY),
                    args
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send UPDATE_ACTIVE_LYRIC command", e)
            }
        }

        val bluetoothLyricsActive = appSettings.broadcastStatusEnabled.value && appSettings.bluetoothLyricsEnabled.value
        if (!bluetoothLyricsActive) {
            if (lastAppliedBluetoothLyricSongId != null || lastAppliedBluetoothLyricLine != null) {
                restoreStandardNowPlayingMetadata(controller, _currentSong.value)
            }
            lastBroadcastLyricSongId = null
            lastBroadcastLyricLine = lyricLine
            return
        }

        if (song.id == lastBroadcastLyricSongId && lyricLine == lastBroadcastLyricLine) {
            return
        }

        statusBroadcaster.broadcastMetadataChanged(
            song = song,
            position = controller.currentPosition,
            queueSize = controller.mediaItemCount,
            queuePosition = controller.currentMediaItemIndex,
            bluetoothLyricsMode = true,
            currentLyricLine = lyricLine
        )
        applyBluetoothLyricsNowPlayingMetadata(controller, song, lyricLine)
        lastBroadcastLyricSongId = song.id
        lastBroadcastLyricLine = lyricLine
    }

    private fun applyBluetoothLyricsNowPlayingMetadata(
        controller: MediaController,
        song: Song,
        lyricLine: String?
    ) {
        if (song.id == lastAppliedBluetoothLyricSongId && lyricLine == lastAppliedBluetoothLyricLine) {
            return
        }

        try {
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET || currentIndex !in 0 until controller.mediaItemCount) {
                return
            }

            val currentItem = controller.getMediaItemAt(currentIndex)
            if (currentItem.mediaId != song.id) {
                return
            }

            val updatedMetadata = currentItem.mediaMetadata
                .buildUpon()
                .setTitle(lyricLine?.takeIf { it.isNotBlank() } ?: DEFAULT_BLUETOOTH_LYRIC_LINE)
                .setArtist(mergeSongTitleAndArtist(song))
                .setAlbumTitle(song.album)
                .setArtworkUri(song.artworkUri ?: currentItem.mediaMetadata.artworkUri)
                .setExtras(buildCanonicalMetadataExtras(song, currentItem.mediaMetadata.extras))
                .build()

            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(updatedMetadata)
                .build()

            controller.replaceMediaItem(currentIndex, updatedItem)
            lastAppliedBluetoothLyricSongId = song.id
            lastAppliedBluetoothLyricLine = lyricLine
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Bluetooth lyrics metadata to current media item", e)
        }
    }

    private fun restoreStandardNowPlayingMetadata(controller: MediaController, song: Song?) {
        val targetSong = song ?: run {
            lastAppliedBluetoothLyricSongId = null
            lastAppliedBluetoothLyricLine = null
            return
        }

        try {
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET || currentIndex !in 0 until controller.mediaItemCount) {
                return
            }

            val currentItem = controller.getMediaItemAt(currentIndex)
            if (currentItem.mediaId != targetSong.id) {
                return
            }

            val currentMetadata = currentItem.mediaMetadata
            val currentTitle = currentMetadata.title?.toString()
            val currentArtist = currentMetadata.artist?.toString()

            if (currentTitle == targetSong.title && currentArtist == targetSong.artist) {
                return
            }

            val restoredMetadata = currentMetadata
                .buildUpon()
                .setTitle(targetSong.title)
                .setArtist(targetSong.artist)
                .setAlbumTitle(targetSong.album)
                .setArtworkUri(targetSong.artworkUri ?: currentMetadata.artworkUri)
                .setExtras(buildCanonicalMetadataExtras(targetSong, currentMetadata.extras))
                .build()

            val restoredItem = currentItem.buildUpon()
                .setMediaMetadata(restoredMetadata)
                .build()

            controller.replaceMediaItem(currentIndex, restoredItem)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore standard metadata after Bluetooth lyrics", e)
        } finally {
            lastAppliedBluetoothLyricSongId = null
            lastAppliedBluetoothLyricLine = null
        }
    }

    private fun mergeSongTitleAndArtist(song: Song): String {
        return listOf(song.title, song.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { song.title }
    }

    private fun buildCanonicalMetadataExtras(song: Song, existingExtras: Bundle?): Bundle {
        val extras = Bundle(existingExtras ?: Bundle())
        extras.putString(METADATA_EXTRA_ORIGINAL_TITLE, song.title)
        extras.putString(METADATA_EXTRA_ORIGINAL_ARTIST, song.artist)
        extras.putString(METADATA_EXTRA_ORIGINAL_ALBUM, song.album)
        return extras
    }

    private fun resolveCurrentSyncedLyricLine(
        syncedLyrics: String?,
        currentPositionMs: Long,
        syncOffsetMs: Long
    ): String? {
        val rawLyrics = syncedLyrics?.takeIf { it.isNotBlank() } ?: return null

        if (cachedSyncedLyricsRaw != rawLyrics) {
            cachedSyncedLyricsRaw = rawLyrics
            cachedParsedSyncedLyrics = LyricsParser.parseLyrics(rawLyrics)
            lastBroadcastLyricLine = null
            lastAppliedBluetoothLyricLine = null
        }

        if (cachedParsedSyncedLyrics.isEmpty()) {
            return null
        }

        val effectivePosition = (currentPositionMs + syncOffsetMs).coerceAtLeast(0L)
        return cachedParsedSyncedLyrics
            .lastOrNull { it.timestamp <= effectivePosition }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun resetBluetoothLyricsBroadcastState(clearLastBroadcast: Boolean = false) {
        cachedSyncedLyricsRaw = null
        cachedParsedSyncedLyrics = emptyList()
        if (clearLastBroadcast) {
            lastBroadcastLyricSongId = null
            lastBroadcastLyricLine = null
            lastAppliedBluetoothLyricSongId = null
            lastAppliedBluetoothLyricLine = null
        }
    }

    private fun updateCurrentSong() {
        mediaController?.let { controller ->
            val mediaItem = controller.currentMediaItem
            mediaItem?.let {
                val id = it.mediaId
                val queueSong = _currentQueue.value.songs.find { queueEntry -> queueEntry.id == id }
                var song = _songs.value.find { localSong -> localSong.id == id } ?: queueSong
                var isExternalSong = false

                if (song == null) {
                    // Try to create song from mediaItem metadata (for external files)
                    song = mediaItemToTransientSong(it)
                    if (song != null) {
                        isExternalSong = true
                        Log.d(TAG, "Created external song from mediaItem: ${song.title}")
                    }
                }
                
                if (song != null) {
                    song = enrichSongDuration(song, resolvePlaybackDuration(controller))
                    val previousSongId = _currentSong.value?.id
                    if (previousSongId != song.id) {
                        resetBluetoothLyricsBroadcastState(clearLastBroadcast = true)
                    }
                    _currentSong.value = song
                    
                    // Update favorite status
                    _isFavorite.value = _favoriteSongs.value.contains(song.id)
                    
                    // Check if the song is in the current queue
                    val currentQueue = _currentQueue.value
                    val songIndexInQueue = currentQueue.songs.indexOfFirst { queueSong -> queueSong.id == song.id }
                    
                    if (songIndexInQueue != -1) {
                        // Song is in queue - update position if different
                        if (songIndexInQueue != currentQueue.currentIndex) {
                            _currentQueue.value = currentQueue.copy(currentIndex = songIndexInQueue)
                            Log.d(TAG, "Updated queue position to $songIndexInQueue on song restore")
                        }
                    } else {
                        // Song is not in queue - this can happen when resuming from a previous session
                        // or when playing external files.
                        if (isExternalSong) {
                            // For external songs, set queue to just this song
                            _currentQueue.value = Queue(listOf(song), 0)
                            Log.d(TAG, "Set queue for external song: ${song.title}")
                        } else {
                            Log.d(TAG, "Song not in queue, syncing entire queue from MediaController for: ${song.title}")
                            syncQueueWithMediaController()
                        }
                    }
                    
                    // Update duration and progress for the current song
                    val playbackDuration = resolvePlaybackDuration(controller)
                    _duration.value = playbackDuration
                    if (playbackDuration > 0) {
                        val currentProgress = controller.currentPosition.toFloat() / playbackDuration.toFloat()
                        _progress.value = currentProgress.coerceIn(0f, 1f)
                        Log.d(TAG, "Updated progress on song restore: ${_progress.value}, position: ${controller.currentPosition}, duration: $playbackDuration")
                    }
                    
                    // Fetch lyrics for the current song
                    fetchLyricsForCurrentSong()
                } else {
                    Log.w(TAG, "Could not find song with ID: $id")
                }
            } ?: run {
                Log.d(TAG, "No current media item in controller")
                // Clear current song and queue if no media item
                _currentSong.value = null
                resetBluetoothLyricsBroadcastState(clearLastBroadcast = true)
                if (_currentQueue.value.songs.isNotEmpty()) {
                    _currentQueue.value = Queue(emptyList(), -1)
                    Log.d(TAG, "Cleared queue as no media item is active")
                }
            }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val extension = this.path?.substringAfterLast('.', "")?.lowercase() ?: ""
        val mimeType = when (extension) {
            "opus", "opa" -> "audio/ogg"
            "ogg", "oga" -> "audio/ogg"
            "mkv", "mka" -> "audio/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac", "adts" -> "audio/aac"
            "ac3" -> "audio/ac3"
            "eac", "eac3" -> "audio/eac3"
            "ac4" -> "audio/ac4"
            "mhm", "mhm1" -> "audio/mhm1"
            "mid", "midi" -> "audio/midi"
            "ape" -> "audio/x-ape"
            "wv" -> "audio/x-wavpack"
            "tta" -> "audio/x-tta"
            "tak" -> "audio/x-tak"
            "aiff", "aif" -> "audio/aiff"
            "dsf", "dff", "dsd" -> "audio/dsd"
            else -> null
        }
        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(this.uri)
            .apply {
                if (mimeType != null) {
                    setMimeType(mimeType)
                }
                if (LanSubsonicPlaybackPolicy.isLanSong(
                        enabled = ProductCapabilities.lanSubsonicOnly,
                        mediaId = this@toMediaItem.id,
                        uri = this@toMediaItem.uri.toString(),
                    )
                ) {
                    LanSubsonicPlaybackPolicy.cacheKey(this@toMediaItem.id)?.let(::setCustomCacheKey)
                }
            }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setArtist(this.artist)
                    .setAlbumTitle(this.album)
                    .setDurationMs(this.duration)
                    .setArtworkUri(this.artworkUri)
                    .build()
            )
            .build()
    }

    private fun mediaItemToTransientSong(mediaItem: MediaItem): Song? {
        val mediaId = mediaItem.mediaId
        val metadata = mediaItem.mediaMetadata

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" }
        val album = metadata.albumTitle?.toString().orEmpty().ifBlank { "Unknown Album" }

        return Song(
            id = if (mediaId.isNotBlank()) mediaId else "external_${title.hashCode()}",
            title = title,
            artist = artist,
            album = album,
            albumId = "",
            duration = mediaController?.duration?.takeIf { it > 0 }
                ?: metadata.durationMs?.takeIf { it > 0 }
                ?: 0L,
            uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY,
            artworkUri = metadata.artworkUri,
            trackNumber = 0,
            year = 0,
            genre = null,
            dateAdded = System.currentTimeMillis(),
            albumArtist = null,
            bitrate = null,
            sampleRate = null,
            channels = null,
            codec = null,
            discNumber = 1
        )
    }

    private fun enrichSongDuration(song: Song, controllerDuration: Long): Song {
        if (song.duration > 0L || controllerDuration <= 0L) {
            return song
        }

        return song.copy(duration = controllerDuration)
    }

    private fun resolveSongFromMediaItem(mediaItem: MediaItem): Song? {
        val mediaId = mediaItem.mediaId
        val queuedSong = if (mediaId.isNotBlank()) {
            _currentQueue.value.songs.find { it.id == mediaId }
        } else {
            null
        }

        return _songs.value.find { it.id == mediaId }
            ?.let { enrichSongDuration(it, mediaController?.duration ?: 0L) }
            ?: queuedSong?.let { enrichSongDuration(it, mediaController?.duration ?: 0L) }
            ?: mediaItemToTransientSong(mediaItem)
    }

    private fun resolveQueueOccurrenceForSong(songId: String, queueIndex: Int): Int {
        if (queueIndex < 0) return 1

        val queueSongs = _currentQueue.value.songs
        val safeIndex = queueIndex.coerceAtMost(queueSongs.lastIndex)
        val occurrencesUpToIndex = queueSongs
            .subList(0, safeIndex + 1)
            .count { it.id == songId }

        return occurrencesUpToIndex.coerceAtLeast(1)
    }

    private fun resolveQueueOccurrenceInList(queueSongs: List<Song>, songId: String, queueIndex: Int): Int {
        if (queueSongs.isEmpty() || queueIndex < 0) return 1

        val safeIndex = queueIndex.coerceIn(0, queueSongs.lastIndex)
        val occurrencesUpToIndex = queueSongs
            .subList(0, safeIndex + 1)
            .count { it.id == songId }

        return occurrencesUpToIndex.coerceAtLeast(1)
    }

    private fun findControllerIndexForSong(songId: String, occurrence: Int = 1): Int? {
        val controller = mediaController ?: return null
        if (controller.mediaItemCount <= 0) return null

        val targetOccurrence = occurrence.coerceAtLeast(1)
        var seen = 0
        var firstMatch: Int? = null

        for (index in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(index).mediaId == songId) {
                if (firstMatch == null) firstMatch = index
                seen += 1
                if (seen == targetOccurrence) {
                    return index
                }
            }
        }

        return firstMatch
    }

    private fun resolveControllerIndexForQueueSelection(song: Song, queueIndex: Int): Int? {
        val controller = mediaController ?: return null
        if (controller.mediaItemCount <= 0) return null

        if (!controller.shuffleModeEnabled) {
            val occurrence = resolveQueueOccurrenceForSong(song.id, queueIndex)
            return findControllerIndexForSong(song.id, occurrence)
        } else {
            val timeline = controller.currentTimeline
            if (timeline.isEmpty) return null

            var windowIndex = timeline.getFirstWindowIndex(true)
            var count = 0
            val visited = BooleanArray(timeline.windowCount)

            while (windowIndex != C.INDEX_UNSET && windowIndex in visited.indices && !visited[windowIndex]) {
                if (count == queueIndex) {
                    return windowIndex
                }
                visited[windowIndex] = true
                count++
                windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
            }
            return null
        }
    }

    /**
     * Play a song from the queue at a specific index
     * Use this when clicking a song from the queue UI to avoid issues with duplicate songs
     */
    fun playSongAtIndex(index: Int) {
        if (index < 0 || index >= _currentQueue.value.songs.size) {
            Log.e(TAG, "Invalid queue index: $index (queue size: ${_currentQueue.value.songs.size})")
            return
        }

        if (!canStartPlayback("playSongAtIndex")) {
            return
        }
        
        val song = _currentQueue.value.songs[index]
        Log.d(TAG, "Playing song at index $index: ${song.title}")
        
        mediaController?.let { controller ->
            val targetControllerIndex = resolveControllerIndexForQueueSelection(song, index)
                ?: index.coerceIn(0, (controller.mediaItemCount - 1).coerceAtLeast(0))

            controller.seekToDefaultPosition(targetControllerIndex)
            _currentQueue.value = _currentQueue.value.copy(currentIndex = index)
            _currentSong.value = song
            _isFavorite.value = _favoriteSongs.value.contains(song.id)

            Log.d(
                TAG,
                "Queue selection seek mapped queueIndex=$index to controllerIndex=$targetControllerIndex for mediaId=${song.id}"
            )
            
            controller.prepare()
            if (!canStartPlayback("playSongAtIndex.prepare")) return@let
            controller.play()
            _isPlaying.value = true
            startProgressUpdates()
            
            // Track song play for statistics
            updateRecentlyPlayed(song)
            updateListeningStats(song)
        }
    }

    /**
     * Play a song - finds it in the queue or adds it
     */
    fun playSong(song: Song) {
        if (song.id.startsWith("rhythm-catalog:")) {
            Log.w(TAG, "Catalog songs must enter through playCatalogQueue")
            return
        }
        Log.d(TAG, "Playing song: ${song.title}")

        if (!canStartPlayback("playSong")) {
            return
        }

        // Clear current lyrics to prevent showing stale lyrics from previous song
        _currentLyrics.value = null

        updateRecentlyPlayed(song)
        updateListeningStats(song)

        val shouldClearQueue = clearQueueOnNewSong.value
        val shouldAutoAddToQueue = autoAddToQueue.value
        val shouldShowQueueDialog = appSettings.showQueueDialog.value
        val currentQueueSongs = _currentQueue.value.songs.toMutableList()
        val songIndexInQueue = currentQueueSongs.indexOfFirst { it.id == song.id }

        mediaController?.let { controller ->
            if (shouldClearQueue) {
                // Clear queue setting enabled - start fresh
                if (shouldAutoAddToQueue) {
                    val contextualQueue = createContextualQueue(song)
                    if (contextualQueue.size > 1) {
                        Log.d(TAG, "Clearing queue and creating contextual queue with ${contextualQueue.size} songs (clearQueueOnNewSong=true, autoAddToQueue=true)")
                        playQueue(contextualQueue)
                        return
                    }
                }

                Log.d(TAG, "Clearing queue and playing single song (clearQueueOnNewSong=true, autoAddToQueue=$shouldAutoAddToQueue)")
                playQueue(listOf(song))
                return
            }
            
            // Check if queue is not empty and song is not in queue - ask user
            if (currentQueueSongs.isNotEmpty() && songIndexInQueue == -1) {
                if (shouldShowQueueDialog) {
                    // Show dialog to ask user what to do
                    Log.d(TAG, "Queue exists with ${currentQueueSongs.size} songs, requesting user action")
                    _queueActionRequest.value = QueueActionRequest(song)
                    return
                } else {
                    // Default behavior when dialog is disabled - add to queue
                    Log.d(TAG, "Queue exists with ${currentQueueSongs.size} songs, adding to queue (dialog disabled)")
                    handleQueueActionChoice(song, clearQueue = false)
                    return
                }
            }
            
            if (songIndexInQueue != -1) {
                // Song is already in the queue, just play it
                val targetControllerIndex = resolveControllerIndexForQueueSelection(song, songIndexInQueue)
                    ?: songIndexInQueue.coerceIn(0, (controller.mediaItemCount - 1).coerceAtLeast(0))

                controller.seekToDefaultPosition(targetControllerIndex)
                _currentQueue.value = _currentQueue.value.copy(currentIndex = songIndexInQueue)
                Log.d(
                    TAG,
                    "Playing existing song in queue at queueIndex=$songIndexInQueue mappedControllerIndex=$targetControllerIndex"
                )
            } else {
                // Song is not in the queue
                if (shouldAutoAddToQueue) {
                    // Create a contextual queue
                    val contextualQueue = createContextualQueue(song)
                    if (contextualQueue.size > 1) {
                        // If we have a contextual queue, play that instead
                        Log.d(TAG, "Creating contextual queue with ${contextualQueue.size} songs (autoAddToQueue=true)")
                        playQueue(contextualQueue)
                        return
                    }
                }
                
                // Fallback: add single song to current queue
                val controllerCurrentIndex = controller.currentMediaItemIndex
                val insertIndex = if (
                    controller.mediaItemCount <= 0 ||
                    controllerCurrentIndex == C.INDEX_UNSET
                ) {
                    0
                } else {
                    (controllerCurrentIndex + 1).coerceAtMost(controller.mediaItemCount)
                }
                currentQueueSongs.add(insertIndex, song)

                val mediaItem = song.toMediaItem()
                controller.addMediaItem(insertIndex, mediaItem)

                if (controller.shuffleModeEnabled) {
                    viewModelScope.launch {
                        delay(50)
                        syncQueueWithMediaController()
                    }
                } else {
                    _currentQueue.value = Queue(currentQueueSongs, insertIndex)
                }

                controller.seekToDefaultPosition(insertIndex)
                Log.d(
                    TAG,
                    "Added single song to queue at controllerInsertIndex=$insertIndex (autoAddToQueue=$shouldAutoAddToQueue, queue size: ${currentQueueSongs.size})"
                )
            }

            controller.prepare()
            if (!canStartPlayback("playSong.prepare")) return@let
            controller.play()

            _currentSong.value = song
            _isPlaying.value = true
            _isFavorite.value = _favoriteSongs.value.contains(song.id)
            startProgressUpdates()
            
            // Verify queue state after operation
            viewModelScope.launch {
                delay(200)
                if (controller.mediaItemCount != _currentQueue.value.songs.size) {
                    Log.w(TAG, "Queue count mismatch after playSong - MediaController: ${controller.mediaItemCount}, ViewModel: ${_currentQueue.value.songs.size}")
                    syncQueueWithMediaController()
                }
            }
        }
    }

    /**
     * Create a contextual queue based on the song's context (album, artist, recently played, etc.)
     */
    private fun createContextualQueue(song: Song): List<Song> {
        // Build a context-aware queue using user preferences and statistics
        val pref = appSettings.contextQueuePreference.value
        val maxSize = appSettings.contextQueueSize.value.coerceAtLeast(1)

        fun recentlyPlayedQueueOrNull(): List<Song>? {
            if (!_recentlyPlayed.value.any { it.id == song.id }) return null
            val startIndex = _recentlyPlayed.value.indexOfFirst { it.id == song.id }
            if (startIndex == -1) return null
            val recentlyPlayedSongs = _recentlyPlayed.value.take(20)
            val reordered = listOf(song) + recentlyPlayedSongs.filter { it.id != song.id }
            Log.d(TAG, "Created queue from recently played with ${reordered.size} songs")
            return reordered.take(maxSize)
        }

        fun albumQueueOrNull(): List<Song>? {
            val albumSongs = _songs.value.filter { it.album == song.album && it.artist == song.artist }
            if (albumSongs.size <= 1) return null
            val sortedAlbumSongs = albumSongs.sortedWith { a, b -> compareByDiscThenTrack(a, b) }
            val reordered = listOf(song) + sortedAlbumSongs.filter { it.id != song.id }
            Log.d(TAG, "Created queue from album '${song.album}' with ${reordered.size} songs")
            return reordered.take(maxSize)
        }

        if (appSettings.respectAlbumOnPlay.value) {
            albumQueueOrNull()?.let { return it }
            recentlyPlayedQueueOrNull()?.let { return it }
        } else {
            recentlyPlayedQueueOrNull()?.let { return it }
            albumQueueOrNull()?.let { return it }
        }

        // Gather candidate pools
        val artistPool = _songs.value
            .filter { it.artist == song.artist && it.id != song.id }
            .distinctBy { it.id }

        val songGenres = GenreUtils.splitGenres(song.genre ?: "").map { it.lowercase() }.toSet()
        val genrePool = if (songGenres.isNotEmpty()) {
            _songs.value.filter { other ->
                other.id != song.id && GenreUtils.splitGenres(other.genre ?: "").any { it.lowercase() in songGenres }
            }.distinctBy { it.id }
        } else emptyList()

        // Home recommendations / personalized seeds
        val recommended = getRecommendedSongs()

        // Scoring function using per-user play counts, favorites and recency
        val playCounts = _songPlayCounts.value
        val favorites = _favoriteSongs.value
        val recentlyIds = _recentlyPlayed.value.map { it.id }.toSet()
        val recommendedIds = recommended.map { it.id }.toSet()

        fun score(s: Song): Int {
            var sc = (playCounts[s.id] ?: 0) * 2
            if (favorites.contains(s.id)) sc += 50
            if (recentlyIds.contains(s.id)) sc += 10
            if (recommendedIds.contains(s.id)) sc += 20
            return sc
        }

        // Combine pools respecting preference
        val combined = when (pref) {
            "GENRE_FIRST" -> (genrePool + artistPool + recommended)
            "ARTIST_FIRST" -> (artistPool + genrePool + recommended)
            else -> (artistPool + genrePool + recommended) // ARTIST_THEN_GENRE default
        }

        val deduped = combined
            .distinctBy { it.id }
            .filter { it.id != song.id }
            .sortedByDescending { score(it) }
            .take(maxSize)

        val finalQueue = listOf(song) + deduped

        // Persist queue if user requested persistent context queues
        if (appSettings.contextQueuePersistence.value == "PERSISTENT") {
            try {
                appSettings.setSavedQueue(finalQueue.map { it.id })
                appSettings.setSavedQueueIndex(0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist contextual queue: ${e.message}")
            }
        }

        Log.d(TAG, "Created contextual queue (pref=$pref, size=${finalQueue.size})")
        return finalQueue
    }

    private fun compareByDiscThenTrack(a: Song, b: Song): Int {
        val discA = if (a.discNumber > 0) a.discNumber else 1
        val discB = if (b.discNumber > 0) b.discNumber else 1

        return when {
            discA != discB -> discA.compareTo(discB)
            a.trackNumber > 0 && b.trackNumber > 0 -> a.trackNumber.compareTo(b.trackNumber)
            a.trackNumber > 0 -> -1
            b.trackNumber > 0 -> 1
            else -> a.title.compareTo(b.title, ignoreCase = true)
        }
    }

    /**
     * Play a song with options for queue behavior
     */
    fun playSongWithQueueOption(song: Song, replaceQueue: Boolean = false, shuffleQueue: Boolean = false) {
        Log.d(TAG, "Playing song with queue option: ${song.title}, replaceQueue: $replaceQueue")
        
        // Clear current lyrics to prevent showing stale lyrics from previous song
        _currentLyrics.value = null
        
        if (replaceQueue) {
            // Replace the entire queue with this song and context
            val queueSongs = createContextualQueue(song)
            val songIndex = queueSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            playQueue(queueSongs, enableShuffle = shuffleQueue, startIndex = songIndex)
        } else {
            // Add to the current queue without interrupting current playback
            addSongToQueue(song)
        }
    }

    /**
     * Play a song from a specific context (playlist, album, etc.)
     * This ensures the queue reflects the context the song was played from
     */
    fun playSongFromContext(song: Song, contextSongs: List<Song>, contextName: String? = null) {
        Log.d(TAG, "Playing song from context: ${song.title}, context: $contextName, contextSize: ${contextSongs.size}")
        
        // Clear current lyrics to prevent showing stale lyrics from previous song
        _currentLyrics.value = null
        
        if (contextSongs.isEmpty()) {
            // Fallback to regular playSong
            playSong(song)
            return
        }
        
        // Find the position of the song in the context
        val songIndex = contextSongs.indexOfFirst { it.id == song.id }
        if (songIndex != -1) {
            // Respect list queue action behavior for contextual list playback.
            playQueueWithUserRule(
                songs = contextSongs,
                startIndex = songIndex,
                sourceLabel = contextName ?: "List"
            )
        } else {
            // Song not found in context, add it to the front and apply list queue rule.
            val newQueue = listOf(song) + contextSongs
            playQueueWithUserRule(
                songs = newQueue,
                startIndex = 0,
                sourceLabel = contextName ?: "List"
            )
        }
    }

    fun playSongFromSearch(song: Song, searchContextSongs: List<Song>) {
        val contextSongs = if (searchContextSongs.isNotEmpty()) searchContextSongs else _songs.value
        val startIndex = contextSongs.indexOfFirst { it.id == song.id }

        Log.d(
            TAG,
            "Playing song from search with shuffle disabled: ${song.title}, contextSize=${contextSongs.size}, startIndex=$startIndex"
        )

        if (startIndex >= 0) {
            playQueue(contextSongs, enableShuffle = false, startIndex = startIndex)
        } else {
            playQueue(listOf(song), enableShuffle = false)
        }
    }

    /**
     * Add songs from a specific context (album, artist, etc.) to queue
     */
    fun addContextToQueue(contextSongs: List<Song>, shuffled: Boolean = false) {
        val songsToAdd = if (shuffled) contextSongs.shuffled() else contextSongs
        addSongsToQueue(songsToAdd)
    }

    /**
     * Start tracking playback time for a new song
     * The identity snapshot is retained so Catalog items do not need a MediaStore lookup later.
     * to ensure we only track actual playback time, not buffering/loading time
     */
    private fun startPlaybackTracking(subject: PlaybackSubject, recentSong: Song) {
        currentPlaybackDuration.consume(SystemClock.elapsedRealtime())
        currentPlaybackSubject = subject
        currentPlaybackRecentSong = recentSong
        currentPlaybackAddedToRecent = false
        
        // Report playback start for streaming items
        val songId = subject.subjectId
        if (songId.startsWith("streaming://") || songId.contains("::")) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val repository = io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule.provideStreamingMusicRepository(getApplication())
                    repository.reportPlaybackStart(songId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to scrobble playback start for song: $songId", e)
                }
            }
        }
        
        // Check if player is actually playing right now
        val actuallyPlaying = mediaController?.isPlaying == true
        if (actuallyPlaying) {
            currentPlaybackDuration.resume(SystemClock.elapsedRealtime())
            addCurrentPlaybackToRecentIfNeeded()
            Log.d(TAG, "Started playback tracking for song: $songId (playing)")
        } else {
            Log.d(TAG, "Started playback tracking for song: $songId (not playing yet)")
        }
    }
    
    /**
     * Resume playback tracking (after pause or when playback starts)
     */
    private fun resumePlaybackTracking() {
        if (currentPlaybackSubject != null && !currentPlaybackDuration.isRunning) {
            currentPlaybackDuration.resume(SystemClock.elapsedRealtime())
            Log.d(TAG, "Resumed playback tracking")
        }
    }
    
    /**
     * Pause playback tracking
     */
    private fun pausePlaybackTracking() {
        if (currentPlaybackDuration.isRunning) {
            currentPlaybackDuration.pause(SystemClock.elapsedRealtime())
            Log.d(TAG, "Paused playback tracking")
        }
    }
    
    /**
     * Finalize playback tracking and record stats for the previous song
     */
    private fun finalizePlaybackTracking() {
        val subject = currentPlaybackSubject
        if (subject != null) {
            val songId = subject.subjectId
            val actualDuration = currentPlaybackDuration.consume(SystemClock.elapsedRealtime())
            
            // Only record if meaningful playback occurred (more than 3 seconds)
            if (actualDuration >= 3000) {
                Log.d(TAG, "Finalizing playback for '${subject.title}': ${actualDuration}ms actual listening time")
                playbackStatsRepository.recordPlayback(
                    subject = subject,
                    activityKind = PlaybackActivityKind.AUDIO_LISTEN,
                    durationMs = actualDuration,
                )
                
                // Report playback stop/scrobble for streaming items
                if (songId.startsWith("streaming://") || songId.contains("::")) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val repository = io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule.provideStreamingMusicRepository(getApplication())
                            repository.reportPlaybackStop(songId, actualDuration)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to scrobble playback stop for song: $songId", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Skipping playback record for short duration: ${actualDuration}ms")
            }
            
            // Reset tracking
            currentPlaybackSubject = null
            currentPlaybackRecentSong = null
            currentPlaybackAddedToRecent = false
        }
    }

    private fun addCurrentPlaybackToRecentIfNeeded() {
        val song = currentPlaybackRecentSong ?: return
        if (currentPlaybackAddedToRecent) return
        currentPlaybackAddedToRecent = true
        updateRecentlyPlayed(song)
    }

    private fun updateRecentlyPlayed(song: Song) {
        viewModelScope.launch {
            try {
                val recentSong = song.toRecentPlaybackSnapshot()
                val currentList = _recentlyPlayed.value.toMutableList()
                currentList.removeIf {
                    it.id.toStableCatalogSongId() == recentSong.id.toStableCatalogSongId()
                }
                currentList.add(0, recentSong)
                if (currentList.size > 50) {
                    currentList.removeAt(currentList.size - 1)
                }
                
                // Update both the StateFlow and persistence
                _recentlyPlayed.value = currentList
                appSettings.updateRecentlyPlayed(currentList.map { it.id })
                appSettings.updateRecentlyPlayedSongCache(currentList)
                appSettings.updateLastPlayedTimestamp(System.currentTimeMillis())
                
                // Log the update
                Log.d(TAG, "Updated recently played: ${currentList.size} songs, latest: ${recentSong.title}")
                
                // Update various stats (but not playback time stats - those are tracked separately now)
                if (!recentSong.isCatalogLibrarySong()) {
                    updateDailyStats(recentSong)
                    updateListeningStats(recentSong)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating recently played", e)
            }
        }
    }

    private fun Song.toRecentPlaybackSnapshot(): Song {
        val stableId = id.toStableCatalogSongId()
        if (!stableId.startsWith(CATALOG_SONG_ID_PREFIX)) return this
        val renditionId = stableId.removePrefix(CATALOG_SONG_ID_PREFIX)
        return copy(
            id = stableId,
            uri = Uri.parse(CatalogPlaybackPolicy.deferredUri(renditionId)),
            path = null,
        )
    }

    private fun Song.toPlaybackSubject(catalogEntry: RhythmQueueEntry?): PlaybackSubject {
        val stableId = id.toStableCatalogSongId()
        val isCatalog = stableId.startsWith(CATALOG_SONG_ID_PREFIX)
        val renditionId = stableId.takeIf { isCatalog }?.removePrefix(CATALOG_SONG_ID_PREFIX)
        return PlaybackSubject(
            subjectId = stableId,
            mediaKind = if (isCatalog) PlaybackMediaKind.CATALOG_AUDIO else PlaybackMediaKind.LOCAL_AUDIO,
            title = catalogEntry?.nowPlaying?.title ?: title,
            artist = catalogEntry?.nowPlaying?.subtitle ?: artist,
            collection = catalogEntry?.playback?.arrangementName ?: album,
            artworkUri = artworkUri?.toString() ?: catalogEntry?.playback?.artworkUrl,
            genre = genre,
            workId = catalogEntry?.nowPlaying?.workId,
            renditionId = renditionId,
        )
    }

    private fun enqueueCatalogAutoCache(item: RhythmNowPlayingItem) {
        // A recent-playback fallback deliberately persists only rendition identity. It can still
        // use cached audio or refresh playback, but cannot run the Work/Arrangement cache job.
        if (item.workId.isBlank() || item.arrangementId.isBlank()) return
        io.github.cluno1.sonorus.features.catalog.data.CatalogAutoCacheWorker.enqueue(
            getApplication(),
            item,
        )
    }
    
    private data class AlbumArtColorRequest(
        val enabled: Boolean,
        val songId: String?,
        val songTitle: String?,
        val artworkUri: Uri?,
    )

    /** Extracts the active artwork through the same Coil pipeline used by the UI. */
    private suspend fun refreshAlbumArtColors(request: AlbumArtColorRequest) {
        // Keep the last successful artwork palette while no playback item is selected. The next
        // non-null current song will refresh it, preserving the app's existing cold-start look.
        if (request.songId == null) return

        val artworkUri = request.artworkUri
        if (artworkUri == null || artworkUri == Uri.EMPTY) {
            clearAlbumArtColorsIfCurrent(request, "No artwork URI")
            return
        }

        val colorsJson = try {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                val imageRequest = ImageRequest.Builder(context)
                    // Keep Uri typed so CatalogArtworkFetcher and AudioArtworkFetcher participate.
                    .data(artworkUri)
                    .size(512)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(imageRequest)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                val extractedColors = bitmap?.let {
                    io.github.cluno1.sonorus.util.ColorExtractor.extractColorsFromBitmap(it)
                }
                extractedColors?.let(io.github.cluno1.sonorus.util.ColorExtractor::colorsToJson)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artwork colors from $artworkUri", e)
            null
        }

        if (!isCurrentAlbumArtColorRequest(request)) return

        if (colorsJson != null) {
            appSettings.setExtractedAlbumColors(colorsJson)
            Log.d(TAG, "Successfully extracted and saved colors from: ${request.songTitle}")
        } else {
            Log.w(TAG, "Could not extract artwork colors from: ${request.songTitle}")
            appSettings.setExtractedAlbumColors(null)
        }
    }

    private fun clearAlbumArtColorsIfCurrent(request: AlbumArtColorRequest, reason: String) {
        if (!isCurrentAlbumArtColorRequest(request)) return
        appSettings.setExtractedAlbumColors(null)
        Log.d(TAG, "$reason for song: ${request.songTitle}")
    }

    private fun isCurrentAlbumArtColorRequest(request: AlbumArtColorRequest): Boolean =
        appSettings.colorSource.value == COLOR_SOURCE_ALBUM_ART &&
            _currentSong.value?.id == request.songId &&
            _currentSong.value?.artworkUri == request.artworkUri

    private fun updateDailyStats(song: Song) {
        viewModelScope.launch {
            try {
                // Update daily listening stats
                val today = java.time.LocalDate.now().toString()
                val dailyStats = appSettings.dailyListeningStats.value.toMutableMap()
                dailyStats[today] = (dailyStats[today] ?: 0L) + 1
                appSettings.updateDailyListeningStats(dailyStats)
                
                // Update weekly top artists
                val currentArtists = appSettings.weeklyTopArtists.value.toMutableMap()
                val splitArtists = repository.splitArtistNames(song.artist).ifEmpty { listOf(song.artist.trim()) }
                splitArtists.forEach { artistName ->
                    if (artistName.isNotBlank() && !artistName.equals("<unknown>", ignoreCase = true)) {
                        currentArtists[artistName] = (currentArtists[artistName] ?: 0) + 1
                    }
                }
                appSettings.updateWeeklyTopArtists(currentArtists)
                
                // Update favorite genres
                val genres = appSettings.favoriteGenres.value.toMutableMap()
                GenreUtils.splitGenres(song.genre).forEach { genre ->
                    genres[genre] = (genres[genre] ?: 0) + 1
                }
                if (genres != appSettings.favoriteGenres.value) {
                    appSettings.updateFavoriteGenres(genres)
                }
                
                // Update mood preferences
                updateMoodPreferences(song)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating listening stats", e)
            }
        }
    }

    private fun updateMoodPreferences(song: Song) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mood = when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        
        val moodPrefs = appSettings.moodPreferences.value.toMutableMap()
        val songList = moodPrefs.getOrDefault(mood, emptyList()).toMutableList()
        if (songList.size >= 20) songList.removeAt(0)
        songList.add(song.id)
        moodPrefs[mood] = songList
        appSettings.updateMoodPreferences(moodPrefs)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            Log.d(TAG, "Playing album: ${album.title} (ID: ${album.id})")
            // Use album's songs directly if available (they're already loaded)
            if (album.songs.isNotEmpty()) {
                Log.d(TAG, "Using ${album.songs.size} songs from album object")
                // Sort by disc then track to maintain multi-disc album order
                val sortedSongs = album.songs.sortedWith { a, b ->
                    compareByDiscThenTrack(a, b)
                }
                playSongs(sortedSongs)
            } else {
                // Fallback to querying if album.songs is empty
                Log.d(TAG, "Album songs empty, querying repository")
                val songs = repository.getSongsForAlbumLocal(album.id)
                Log.d(TAG, "Found ${songs.size} songs for album")
                if (songs.isNotEmpty()) {
                    // Sort by disc then track to maintain multi-disc album order
                    val sortedSongs = songs.sortedWith { a: Song, b: Song ->
                        compareByDiscThenTrack(a, b)
                    }
                    playSongs(sortedSongs)
                } else {
                    Log.e(TAG, "No songs found for album: ${album.title} (ID: ${album.id})")
                    debugQueueState()
                }
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            Log.d(TAG, "Playing artist: ${artist.name} (ID: ${artist.id})")
            val songs = repository.getSongsForArtist(artist.id)
            Log.d(TAG, "Found ${songs.size} songs for artist")
            if (songs.isNotEmpty()) {
                playSongs(songs)
            } else {
                Log.e(TAG, "No songs found for artist: ${artist.name} (ID: ${artist.id})")
                debugQueueState()
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        Log.d(TAG, "Playing playlist: ${playlist.name}")
        if (playlist.songs.isNotEmpty()) {
            playSongs(playlist.songs)
        }
    }

    fun playQueueWithUserRule(songs: List<Song>, startIndex: Int = 0, sourceLabel: String? = null) {
        if (songs.isEmpty()) {
            Log.w(TAG, "Ignoring queue rule request for empty song list")
            return
        }

        val hasExistingQueue = _currentQueue.value.songs.isNotEmpty() &&
            (mediaController?.mediaItemCount ?: 0) > 0
        val queueRule = appSettings.listQueueActionBehavior.value

        if (!hasExistingQueue || queueRule == "replace") {
            playQueue(songs, enableShuffle = false, startIndex = startIndex)
            return
        }

        if (queueRule == "ask") {
            _queueListActionRequest.value = QueueListActionRequest(
                songs = songs,
                startIndex = startIndex,
                sourceLabel = sourceLabel
            )
            return
        }

        applyListQueueAction(songs = songs, startIndex = startIndex, action = queueRule)
    }

    private fun applyListQueueAction(songs: List<Song>, startIndex: Int, action: String) {
        when (action) {
            "replace" -> playQueue(songs, enableShuffle = false, startIndex = startIndex)
            "play_next" -> insertQueueListAndPlay(songs, startIndex, insertAfterCurrent = true)
            "add_to_end" -> insertQueueListAndPlay(songs, startIndex, insertAfterCurrent = false)
            else -> {
                Log.w(TAG, "Unknown list queue action '$action', falling back to replace")
                playQueue(songs, enableShuffle = false, startIndex = startIndex)
            }
        }
    }

    private fun insertQueueListAndPlay(
        songs: List<Song>,
        startIndex: Int,
        insertAfterCurrent: Boolean
    ) {
        if (songs.isEmpty()) return

        val validStartIndex = startIndex.coerceIn(0, songs.lastIndex)

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val mediaItems = songs.map { it.toMediaItem() }

                withContext(Dispatchers.Main) {
                    mediaController?.let { controller ->
                        val currentQueueSongs = _currentQueue.value.songs.toMutableList()
                        val currentControllerIndex = controller.currentMediaItemIndex

                        val calculatedInsertIndex = if (insertAfterCurrent) {
                            if (controller.mediaItemCount <= 0 || currentControllerIndex == C.INDEX_UNSET) {
                                controller.mediaItemCount
                            } else {
                                (currentControllerIndex + 1).coerceAtMost(controller.mediaItemCount)
                            }
                        } else {
                            controller.mediaItemCount
                        }

                        val insertIndex = calculatedInsertIndex.coerceIn(0, currentQueueSongs.size)
                        currentQueueSongs.addAll(insertIndex, songs)
                        controller.addMediaItems(insertIndex, mediaItems)

                        val isPlayingOrActive = (controller.playbackState == Player.STATE_READY || controller.playbackState == Player.STATE_BUFFERING) &&
                                currentControllerIndex != C.INDEX_UNSET && _currentQueue.value.songs.isNotEmpty()

                        if (isPlayingOrActive) {
                            val activeIndex = currentControllerIndex.coerceIn(0, currentQueueSongs.lastIndex)
                            _currentQueue.value = Queue(currentQueueSongs, activeIndex)
                            saveQueueToPersistence()

                            if (controller.shuffleModeEnabled) {
                                viewModelScope.launch {
                                    delay(50)
                                    syncQueueWithMediaController()
                                }
                            }

                            val toastContext = getApplication<android.app.Application>().applicationContext
                            val msg = if (insertAfterCurrent) {
                                "${songs.size} ${if (songs.size == 1) "song" else "songs"} will play next"
                            } else {
                                "Added ${songs.size} ${if (songs.size == 1) "song" else "songs"} to queue"
                            }
                            android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()

                            Log.d(
                                TAG,
                                "Appended ${songs.size} songs (${if (insertAfterCurrent) "play_next" else "add_to_end"}) without interrupting current playback at activeIndex=$activeIndex"
                            )
                        } else {
                            val targetQueueIndex = (insertIndex + validStartIndex)
                                .coerceIn(0, currentQueueSongs.lastIndex.coerceAtLeast(0))
                            val targetSong = currentQueueSongs[targetQueueIndex]

                            val targetControllerIndex = if (controller.shuffleModeEnabled) {
                                val occurrence = resolveQueueOccurrenceInList(
                                    queueSongs = currentQueueSongs,
                                    songId = targetSong.id,
                                    queueIndex = targetQueueIndex
                                )
                                findControllerIndexForSong(targetSong.id, occurrence)
                                    ?: currentControllerIndex.takeIf { it != C.INDEX_UNSET }
                                    ?: 0
                            } else {
                                targetQueueIndex
                            }

                            _currentQueue.value = Queue(currentQueueSongs, targetQueueIndex)

                            controller.seekToDefaultPosition(targetControllerIndex)
                            controller.prepare()
                            if (!canStartPlayback("insertQueueListAndPlay")) return@let
                            controller.play()

                            _currentSong.value = targetSong
                            _isPlaying.value = true
                            _isFavorite.value = _favoriteSongs.value.contains(targetSong.id)

                            updateRecentlyPlayed(targetSong)
                            updateListeningStats(targetSong)
                            startProgressUpdates()
                            saveQueueToPersistence()

                            if (controller.shuffleModeEnabled) {
                                viewModelScope.launch {
                                    delay(50)
                                    syncQueueWithMediaController()
                                }
                            }

                            Log.d(
                                TAG,
                                "Inserted ${songs.size} songs (${if (insertAfterCurrent) "play_next" else "add_to_end"}) and started at queueIndex=$targetQueueIndex"
                            )
                        }
                    } ?: run {
                        playQueue(songs, enableShuffle = false, startIndex = validStartIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting queue list with action", e)
                withContext(Dispatchers.Main) {
                    playQueue(songs, enableShuffle = false, startIndex = validStartIndex)
                }
            }
        }
    }

    /**
     * Enters Media3 through the managed catalog boundary while keeping Work/Rendition/Asset as
     * the authoritative identity. [Song] values below are display-only adapters for the legacy
     * player shell and are never persisted as library entities.
     */
    fun playCatalogQueue(entries: List<RhythmQueueEntry>, startIndex: Int = 0) {
        applyCatalogQueue(entries, startIndex, 0L, startPlayback = true)
    }

    fun restoreCatalogQueue(entries: List<RhythmQueueEntry>, startIndex: Int, positionMs: Long) {
        if (_currentSong.value != null) return
        applyCatalogQueue(entries, startIndex, positionMs, startPlayback = false)
    }

    /** Plays a queue that may freely interleave device MediaStore and managed Catalog items. */
    fun playUnifiedQueue(
        songs: List<Song>,
        catalogEntries: List<RhythmQueueEntry>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
    ) {
        if (catalogEntries.isEmpty()) {
            playQueue(songs, enableShuffle = shuffle, startIndex = startIndex)
            return
        }
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(songs.indices)
        val orderedSongs = if (shuffle) songs.shuffled() else songs
        val orderedIndex = if (shuffle) 0 else validIndex
        applyUnifiedQueue(orderedSongs, catalogEntries, orderedIndex, 0L, startPlayback = true)
    }

    fun restoreUnifiedQueue(
        songs: List<Song>,
        catalogEntries: List<RhythmQueueEntry>,
        startIndex: Int,
        positionMs: Long,
    ) {
        if (_currentSong.value != null || songs.isEmpty()) return
        applyUnifiedQueue(songs, catalogEntries, startIndex, positionMs, startPlayback = false)
    }

    /** Inserts a managed Catalog song without weakening the deferred-URI playback boundary. */
    fun addUnifiedSongToQueue(song: Song, entry: RhythmQueueEntry, playNext: Boolean) {
        if (!song.isCatalogLibrarySong()) return
        val mediaItem = entry.playback.toMediaItem()
        if (!CatalogPlaybackPolicy.allowsDeferred(
                mediaId = mediaItem.mediaId,
                uri = mediaItem.localConfiguration?.uri?.toString(),
                mediaType = mediaItem.localConfiguration?.mimeType,
            )
        ) {
            Log.w(TAG, "Rejected Catalog queue insertion because the item failed deferred policy")
            return
        }
        mediaController?.let { controller ->
            val queueSongs = _currentQueue.value.songs.toMutableList()
            val insertIndex = if (playNext && controller.currentMediaItemIndex >= 0) {
                controller.currentMediaItemIndex + 1
            } else {
                controller.mediaItemCount
            }.coerceIn(0, queueSongs.size)
            controller.addMediaItem(insertIndex, mediaItem)
            queueSongs.add(insertIndex, song)
            val currentIndex = controller.currentMediaItemIndex.coerceIn(0, queueSongs.lastIndex)
            _currentQueue.value = Queue(queueSongs, currentIndex)
            val catalogEntries = (_catalogQueue.value + entry).distinctBy { it.playback.toMediaItem().mediaId }
            _catalogQueue.value = catalogEntries
            CatalogQueueStore(getApplication()).saveUnified(
                songs = queueSongs,
                catalogEntriesByMediaId = catalogEntries.associateBy { it.playback.toMediaItem().mediaId },
                currentIndex = currentIndex,
                positionMs = controller.currentPosition,
            )
        }
    }

    private fun applyUnifiedQueue(
        songs: List<Song>,
        catalogEntries: List<RhythmQueueEntry>,
        startIndex: Int,
        positionMs: Long,
        startPlayback: Boolean,
    ) {
        if (songs.isEmpty() || !canStartPlayback("playUnifiedQueue")) return
        val serverUrl = io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore(getApplication())
            .loadServerUrl()
            ?: return
        val catalogByMediaId = catalogEntries.associateBy { it.playback.toMediaItem().mediaId }
        val accepted = songs.all { song ->
            val catalog = catalogByMediaId[song.id]
            if (catalog != null) {
                val item = catalog.playback.toMediaItem()
                CatalogPlaybackPolicy.allowsDeferred(
                    mediaId = item.mediaId,
                    uri = item.localConfiguration?.uri?.toString(),
                    mediaType = item.localConfiguration?.mimeType,
                ) || CatalogPlaybackPolicy.allows(
                    mediaId = item.mediaId,
                    uri = item.localConfiguration?.uri?.toString(),
                    customCacheKey = item.localConfiguration?.customCacheKey,
                    mediaType = item.localConfiguration?.mimeType,
                    trustedServerUrl = serverUrl,
                )
            } else {
                CatalogPlaybackPolicy.allowsDeviceMediaStoreItem(song.id, song.uri.toString()) ||
                    DeviceDocumentPolicy.allowsPersistedRead(getApplication(), song.id, song.uri)
            }
        }
        if (!accepted) {
            Log.w(TAG, "Rejected unified queue because one or more entries failed source policy")
            return
        }
        val validIndex = startIndex.coerceIn(songs.indices)
        val mediaItems = songs.map { song ->
            catalogByMediaId[song.id]?.playback?.toMediaItem() ?: song.toMediaItem()
        }
        viewModelScope.launch(Dispatchers.Main) {
            mediaController?.let { controller ->
                controller.stop()
                controller.clearMediaItems()
                controller.shuffleModeEnabled = false
                controller.addMediaItems(mediaItems)
                playbackControlStateMachine.beginLoading(
                    mediaId = mediaItems[validIndex].mediaId,
                    playWhenReady = startPlayback,
                )
                controller.prepare()
                controller.seekTo(validIndex, positionMs.coerceAtLeast(0L))
                if (!canStartPlayback("playUnifiedQueue.prepare")) return@let
                if (startPlayback) controller.play()
                _catalogQueue.value = catalogEntries
                _currentQueue.value = Queue(songs, validIndex)
                _currentSong.value = songs[validIndex]
                val activeCatalog = catalogByMediaId[songs[validIndex].id]
                _catalogNowPlaying.value = activeCatalog?.nowPlaying
                applyCatalogLyrics(activeCatalog?.nowPlaying)
                _isFavorite.value = _favoriteSongs.value.contains(
                    songs[validIndex].id.toStableCatalogSongId()
                )
                _isPlaying.value = startPlayback
                io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore(getApplication())
                    .saveUnified(songs, catalogByMediaId, validIndex, positionMs)
                activeCatalog?.takeIf { startPlayback }?.let {
                    enqueueCatalogAutoCache(it.nowPlaying)
                }
                if (startPlayback) startProgressUpdates()
            } ?: Log.w(TAG, "MediaController unavailable for unified playback")
        }
    }

    private fun applyCatalogQueue(
        entries: List<RhythmQueueEntry>,
        startIndex: Int,
        positionMs: Long,
        startPlayback: Boolean,
    ) {
        if (entries.isEmpty() || !canStartPlayback("playCatalogQueue")) return
        val serverUrl = io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore(getApplication())
            .loadServerUrl()
            ?: return
        val accepted = entries.all { entry ->
            val item = entry.playback.toMediaItem()
            CatalogPlaybackPolicy.allowsDeferred(
                mediaId = item.mediaId,
                uri = item.localConfiguration?.uri?.toString(),
                mediaType = item.localConfiguration?.mimeType,
            ) || CatalogPlaybackPolicy.allows(
                    mediaId = item.mediaId,
                    uri = item.localConfiguration?.uri?.toString(),
                    customCacheKey = item.localConfiguration?.customCacheKey,
                    mediaType = item.localConfiguration?.mimeType,
                    trustedServerUrl = serverUrl,
                )
        }
        if (!accepted) {
            Log.w(TAG, "Rejected catalog queue because one or more entries failed policy")
            return
        }
        val validIndex = startIndex.coerceIn(entries.indices)
        val displaySongs = entries.map { entry ->
            val mediaItem = entry.playback.toMediaItem()
            Song(
                id = mediaItem.mediaId,
                title = entry.nowPlaying.title,
                artist = entry.nowPlaying.subtitle,
                album = entry.playback.arrangementName,
                albumId = entry.playback.albumId.ifBlank { entry.nowPlaying.arrangementId },
                duration = entry.playback.durationMs,
                uri = Uri.parse(entry.playback.playbackUrl),
                codec = entry.playback.mediaType,
                artworkUri = entry.playback.artworkUrl
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?.let(Uri::parse),
                path = null,
            )
        }
        viewModelScope.launch(Dispatchers.Main) {
            mediaController?.let { controller ->
                controller.stop()
                controller.clearMediaItems()
                controller.shuffleModeEnabled = false
                controller.addMediaItems(entries.map { it.playback.toMediaItem() })
                playbackControlStateMachine.beginLoading(
                    mediaId = entries[validIndex].playback.toMediaItem().mediaId,
                    playWhenReady = startPlayback,
                )
                controller.prepare()
                controller.seekTo(validIndex, positionMs.coerceAtLeast(0L))
                if (!canStartPlayback("playCatalogQueue.prepare")) return@let
                if (startPlayback) controller.play()
                _catalogQueue.value = entries
                _catalogNowPlaying.value = entries[validIndex].nowPlaying
                _currentQueue.value = Queue(displaySongs, validIndex)
                _currentSong.value = displaySongs[validIndex]
                applyCatalogLyrics(entries[validIndex].nowPlaying)
                _isFavorite.value = _favoriteSongs.value.contains(
                    displaySongs[validIndex].id.toStableCatalogSongId()
                )
                _isPlaying.value = startPlayback
                io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore(getApplication())
                    .save(entries, validIndex, positionMs)
                if (startPlayback) {
                    enqueueCatalogAutoCache(entries[validIndex].nowPlaying)
                }
                if (startPlayback) startProgressUpdates()
            } ?: Log.w(TAG, "MediaController unavailable for catalog playback")
        }
    }

    fun playQueue(songs: List<Song>, enableShuffle: Boolean? = null, startIndex: Int = 0, pinStartIndex: Boolean = false) {
        if (songs.any { it.id.startsWith("rhythm-catalog:") }) {
            Log.w(TAG, "Catalog songs must enter through playCatalogQueue")
            return
        }
        Log.d(TAG, "Playing queue with ${songs.size} songs, shuffle: $enableShuffle, startIndex: $startIndex, pinStartIndex: $pinStartIndex")

        _catalogQueue.value = emptyList()
        _catalogNowPlaying.value = null
        applyCatalogLyrics(null)
        io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore(getApplication()).clear()

        if (!canStartPlayback("playQueue")) {
            return
        }
        
        // Clear current lyrics to prevent showing stale lyrics from previous song
        _currentLyrics.value = null
        
        if (songs.isEmpty()) {
            Log.e(TAG, "Cannot play empty queue")
            return
        }
        
        // Validate startIndex
        val validStartIndex = startIndex.coerceIn(0, songs.size - 1)
        if (startIndex != validStartIndex) {
            Log.w(TAG, "startIndex $startIndex out of bounds, using $validStartIndex")
        }
        
        // Build media items on a background thread to avoid blocking the main thread
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val useExoPlayerShuffle = appSettings.shuffleUsesExoplayer.value
                val isManualShuffle = enableShuffle == true && !useExoPlayerShuffle
                val shouldPinStart = pinStartIndex || (enableShuffle == true && validStartIndex > 0)

                // If manual shuffle is requested, pre-shuffle the list
                val finalSongs = if (isManualShuffle) {
                    val listToShuffle = songs.toMutableList()
                    if (shouldPinStart) {
                        val startingSong = listToShuffle.getOrNull(validStartIndex)
                        if (startingSong != null) {
                            listToShuffle.removeAt(validStartIndex)
                            listToShuffle.shuffle()
                            listToShuffle.add(0, startingSong)
                        } else {
                            listToShuffle.shuffle()
                        }
                    } else {
                        listToShuffle.shuffle()
                    }
                    listToShuffle
                } else {
                    songs
                }

                // If manual shuffle was applied, the starting index in the final list is 0.
                // If ExoPlayer shuffle is used without a pinned starting song, pick a random start index so ExoPlayer's shuffle order doesn't always start on song 0.
                val finalStartIndex = when {
                    isManualShuffle -> 0
                    enableShuffle == true && !shouldPinStart && songs.size > 1 -> kotlin.random.Random.nextInt(songs.size)
                    else -> validStartIndex
                }

                // Create all media items on background thread
                val mediaItems = finalSongs.map { it.toMediaItem() }
                
                // Switch to main thread to interact with MediaController
                withContext(Dispatchers.Main) {
                    mediaController?.let { controller ->
                        // Stop playback first to prevent issues
                        controller.stop()
                        
                        // Clear existing queue
                        controller.clearMediaItems()
                        
                        // Set shuffle mode BEFORE adding items if specified
                        if (enableShuffle != null) {
                            if (useExoPlayerShuffle) {
                                controller.shuffleModeEnabled = enableShuffle
                                _isShuffleEnabled.value = enableShuffle
                            } else {
                                controller.shuffleModeEnabled = false
                                _isShuffleEnabled.value = enableShuffle
                                if (appSettings.shuffleModePersistence.value) {
                                    appSettings.setSavedShuffleState(enableShuffle)
                                }
                            }
                            Log.d(TAG, "Set shuffle mode to $enableShuffle before building queue (useExoPlayerShuffle=$useExoPlayerShuffle)")
                        }
                        
                        // Add all media items at once
                        controller.addMediaItems(mediaItems)
                        
                        // Prepare BEFORE setting queue state for better sync
                        controller.prepare()
                        
                        // Set the queue in the view model with the correct starting index
                        _currentQueue.value = Queue(finalSongs, finalStartIndex)
                        
                        // Set original queue state if shuffle is enabled
                        if (enableShuffle == true) {
                            queueStateHolder.saveOriginalQueueState(songs, queueStateHolder.currentQueueSourceName.value)
                        } else {
                            queueStateHolder.clearOriginalQueue()
                        }
                        
                        // Save queue to persistence
                        saveQueueToPersistence()
                        
                        // Start playback from the specified index
                        controller.seekToDefaultPosition(finalStartIndex)
                        if (!canStartPlayback("playQueue.prepare")) return@withContext
                        controller.play()
                        
                        // Update current song and state to the song at startIndex
                        val startingSong = finalSongs.getOrNull(finalStartIndex)
                        _currentSong.value = startingSong
                        _isPlaying.value = true
                        
                        // Add starting song to recently played and track play
                        startingSong?.let { 
                            updateRecentlyPlayed(it)
                            updateListeningStats(it)
                        }
                        
                        // Update favorite status
                        _isFavorite.value = startingSong?.let { song -> 
                            _favoriteSongs.value.contains(song.id) 
                        } ?: false
                        
                        startProgressUpdates()
                        
                        Log.d(TAG, "Successfully started playback of queue with ${finalSongs.size} songs from index $finalStartIndex")
                        
                        // Debug queue state
                        debugQueueState()
                        
                        // Immediate sync check for consistency
                        viewModelScope.launch {
                            delay(200) // Short delay to let MediaController settle
                            if (controller.mediaItemCount != finalSongs.size) {
                                Log.w(TAG, "Queue size mismatch detected immediately - expected ${finalSongs.size}, got ${controller.mediaItemCount}")
                                syncQueueWithMediaController()
                            }
                            // Secondary check after slightly longer delay
                            delay(300)
                            if (controller.mediaItemCount != _currentQueue.value.songs.size) {
                                Log.w(TAG, "Queue size mismatch after secondary check - syncing again")
                                syncQueueWithMediaController()
                                debugQueueState()
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing queue", e)
                // Reset queue state on error
                withContext(Dispatchers.Main) {
                    _currentQueue.value = Queue(emptyList(), -1)
                }
            }
        }
    }

    
    // Store pending queue to play when controller becomes available
    private var pendingQueueToPlay: List<Song>? = null
    private var wasRhythmGuardTimeoutActive = false
    private var shouldResumeAfterRhythmGuardTimeout = false

    private fun isRhythmGuardTimeoutActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return appSettings.rhythmGuardTimeoutUntilMs.value > nowMs
    }

    private fun enforceRhythmGuardTimeout(reason: String) {
        val wasPlaying = mediaController?.isPlaying == true || _isPlaying.value
        if (wasPlaying) {
            shouldResumeAfterRhythmGuardTimeout = true
        }
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                Log.d(TAG, "Rhythm Guard timeout active, forcing pause ($reason)")
                controller.pause()
            }
        }
        _isPlaying.value = false
        progressUpdateJob?.cancel()
    }

    fun pauseForRhythmGuardTimeout(reason: String) {
        val wasPlaying = mediaController?.isPlaying == true || _isPlaying.value
        if (wasPlaying) {
            shouldResumeAfterRhythmGuardTimeout = true
        }
        Log.d(TAG, "Pausing playback for Rhythm Guard timeout ($reason), wasPlaying=$wasPlaying")
        pauseMusic()
    }

    fun resumePlaybackAfterRhythmGuardTimeoutIfNeeded(source: String) {
        if (isRhythmGuardTimeoutActive()) {
            Log.d(TAG, "Skipping timeout resume from $source: timeout still active")
            return
        }
        if (!shouldResumeAfterRhythmGuardTimeout) {
            return
        }
        if (mediaController == null) {
            Log.d(TAG, "Deferring timeout resume from $source: media controller not ready")
            return
        }

        Log.d(TAG, "Resuming playback after timeout from $source")
        shouldResumeAfterRhythmGuardTimeout = false
        resumeMusic()
    }

    private fun canStartPlayback(action: String): Boolean {
        if (!isRhythmGuardTimeoutActive()) return true
        Log.d(TAG, "Blocked playback action '$action' due to active Rhythm Guard timeout")
        enforceRhythmGuardTimeout(reason = action)

        RhythmGuardTimeoutActivity.start(
            context = getApplication<Application>(),
            reason = appSettings.rhythmGuardTimeoutReason.value.ifBlank {
                getApplication<Application>().getString(R.string.settings_rhythm_guard_timeout_activity_default_reason)
            },
            timeoutUntilMs = appSettings.rhythmGuardTimeoutUntilMs.value,
            timeoutStartedAtMs = appSettings.rhythmGuardTimeoutStartedAtMs.value
        )
        return false
    }

    fun togglePlayPause() {
        Log.d(TAG, "Toggle play/pause, current state: ${_isPlaying.value}")
        mediaController?.let { controller ->
            // Follow the same atomic state rendered by the button. During BUFFERING this remains
            // Pause while automatic playback is intended; after IDLE/ENDED/ERROR it is Play even
            // if Media3 has not yet cleared a stale playWhenReady value.
            if (playbackControlUiState.value.showPause) {
                controller.pause()
                _isPlaying.value = false
                progressUpdateJob?.cancel()
            } else {
                pausedByZeroVolume = false
                _showZeroVolumePauseDialog.value = false
                if (!canStartPlayback("togglePlayPause")) return
                controller.play()
                _isPlaying.value = true
                startProgressUpdates()
            }
        }
    }
    
    // Sleep Timer Functions
    fun pauseMusic() {
        Log.d(TAG, "Pausing music via sleep timer")
        mediaController?.let { controller ->
            controller.pause()
            _isPlaying.value = false
            progressUpdateJob?.cancel()
        }
    }

    fun resumeMusic() {
        Log.d(TAG, "Resuming music playback")
        mediaController?.let { controller ->
            if (!canStartPlayback("resumeMusic")) return
            controller.play()
            _isPlaying.value = true
            startProgressUpdates()
        }
    }
    
    fun stopMusic() {
        Log.d(TAG, "Stopping music via sleep timer")
        mediaController?.let { controller ->
            controller.stop()
            _isPlaying.value = false
            progressUpdateJob?.cancel()
        }
    }
    
    fun fadeOutAndPause() {
        Log.d(TAG, "Fading out and pausing music via sleep timer")
        mediaController?.let { controller ->
            // Implement fade out effect by gradually reducing volume
            viewModelScope.launch {
                val originalVolume = controller.volume
                var currentVolume = originalVolume
                
                // Fade out over 3 seconds
                repeat(30) {
                    currentVolume -= originalVolume / 30f
                    controller.volume = maxOf(0f, currentVolume)
                    delay(100) // 100ms intervals for smooth fade
                }
                
                // Pause and restore volume for next play
                controller.pause()
                controller.volume = originalVolume
                _isPlaying.value = false
                progressUpdateJob?.cancel()
            }
        }
    }

    private var lastSkipTime = 0L
    private val SKIP_DEBOUNCE_MS = 400L

    fun skipToNext() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) {
            Log.d(TAG, "Ignored skipToNext: debouncing rapid clicks.")
            return
        }
        lastSkipTime = currentTime

        Log.d(TAG, "Skip to next")
        mediaController?.let { controller ->
            // Check if there are more songs in the queue
            if (controller.hasNextMediaItem()) {
                // Get the next song before seeking to update UI immediately
                val nextIndex = (controller.currentMediaItemIndex + 1) % controller.mediaItemCount
                val nextMediaItem = controller.getMediaItemAt(nextIndex)
                val nextSongId = nextMediaItem.mediaId
                val nextSong = _songs.value.find { it.id == nextSongId }

                playbackControlStateMachine.beginLoading(
                    mediaId = nextSongId,
                    playWhenReady = controller.playWhenReady,
                )
                
                // Update the current queue position first for immediate UI feedback
                val currentQueue = _currentQueue.value
                if (currentQueue.songs.isNotEmpty()) {
                    val currentIndex = currentQueue.currentIndex
                    val newIndex = (currentIndex + 1) % currentQueue.songs.size
                    _currentQueue.value = currentQueue.copy(currentIndex = newIndex)
                    
                    // Reset progress to 0 for immediate UI feedback
                    _progress.value = 0f
                    
                    Log.d(TAG, "Updated queue position from $currentIndex to $newIndex")
                }
                
                // Update the current song immediately for better UX
                if (nextSong != null) {
                    _currentSong.value = nextSong
                    // Update recently played
                    updateRecentlyPlayed(nextSong)
                    // Update favorite status
                    _isFavorite.value = _favoriteSongs.value.contains(nextSong.id)
                    // Fetch lyrics for the new song
                    fetchLyricsForCurrentSong()
                }
                
                // Now perform the actual seek operation
                controller.seekToNext()
            } else {
                Log.d(TAG, "No next song available")
            }
        }
    }

    fun skipToPrevious() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < SKIP_DEBOUNCE_MS) {
            Log.d(TAG, "Ignored skipToPrevious: debouncing rapid clicks.")
            return
        }
        lastSkipTime = currentTime

        Log.d(TAG, "Skip to previous")
        mediaController?.let { controller ->
            // If current position is past the threshold, restart current song
            if (controller.currentPosition > REWIND_THRESHOLD_MS) {
                Log.d(TAG, "Current position (${controller.currentPosition}ms) > threshold (${REWIND_THRESHOLD_MS}ms), restarting current song.")
                controller.seekTo(0)
                _progress.value = 0f // Immediately reset progress for UI
            } else {
                // Otherwise, skip to the actual previous song
                if (controller.hasPreviousMediaItem()) {
                    // Get the previous song before seeking to update UI immediately
                    val prevIndex = if (controller.currentMediaItemIndex > 0)
                        controller.currentMediaItemIndex - 1
                    else
                        controller.mediaItemCount - 1

                    val prevMediaItem = controller.getMediaItemAt(prevIndex)
                    val prevSongId = prevMediaItem.mediaId
                    val prevSong = _songs.value.find { it.id == prevSongId }

                    playbackControlStateMachine.beginLoading(
                        mediaId = prevSongId,
                        playWhenReady = controller.playWhenReady,
                    )

                    // Update the current queue position first for immediate UI feedback
                    val currentQueue = _currentQueue.value
                    if (currentQueue.songs.isNotEmpty()) {
                        val currentIndex = currentQueue.currentIndex
                        val newIndex = if (currentIndex > 0)
                            currentIndex - 1
                        else
                            currentQueue.songs.size - 1

                        _currentQueue.value = currentQueue.copy(currentIndex = newIndex)

                        // Reset progress to 0 for immediate UI feedback
                        _progress.value = 0f

                        Log.d(TAG, "Updated queue position from $currentIndex to $newIndex")
                    }

                    // Update the current song immediately for better UX
                    if (prevSong != null) {
                        _currentSong.value = prevSong
                        // Update recently played
                        updateRecentlyPlayed(prevSong)
                        // Update favorite status
                        _isFavorite.value = _favoriteSongs.value.contains(prevSong.id)
                        // Fetch lyrics for the new song
                        fetchLyricsForCurrentSong()
                    }

                    // Now perform the actual seek operation
                    controller.seekToPrevious()
                } else {
                    Log.d(TAG, "No previous song available, restarting current song.")
                    // If no previous song, but still within threshold, restart current song
                    controller.seekTo(0)
                    _progress.value = 0f // Immediately reset progress for UI
                }
            }
        }
    }

    fun seekTo(positionMs: Long, autoPlayIfPaused: Boolean = false) {
        Log.d(TAG, "Seek to position: $positionMs ms (autoPlay=$autoPlayIfPaused)")
        _isSeeking.value = true
        mediaController?.let { controller ->
            controller.seekTo(positionMs)
            if (autoPlayIfPaused && !controller.isPlaying) {
                controller.play()
            }
        }
        updateProgress() // Immediately update progress after seeking
        
        // Reset seeking state after a delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _isSeeking.value = false
        }
    }

    fun seekTo(progress: Float) {
        mediaController?.let { controller ->
            val playbackDuration = resolvePlaybackDuration(controller)
            val positionMs = (progress * playbackDuration).toLong()
            Log.d(TAG, "Seek to progress: $progress (${positionMs}ms)")
            _isSeeking.value = true
            controller.seekTo(positionMs)
            updateProgress() // Immediately update progress after seeking
            
            // Reset seeking state after a delay
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                _isSeeking.value = false
            }
        }
    }

    fun skipBackward() {
        mediaController?.let { controller ->
            val newPosition = (controller.currentPosition - 30_000).coerceAtLeast(0)
            Log.d(TAG, "Skip backward 30s to ${newPosition}ms")
            _isSeeking.value = true
            controller.seekTo(newPosition)
            updateProgress() // Immediately update progress after seeking
            
            // Reset seeking state after a delay
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                _isSeeking.value = false
            }
        }
    }

    fun skipForward() {
        mediaController?.let { controller ->
            val playbackDuration = resolvePlaybackDuration(controller)
            val newPosition = (controller.currentPosition + 30_000).coerceAtMost(playbackDuration)
            Log.d(TAG, "Skip forward 30s to ${newPosition}ms")
            _isSeeking.value = true
            controller.seekTo(newPosition)
            updateProgress() // Immediately update progress after seeking
            
            // Reset seeking state after a delay
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                _isSeeking.value = false
            }
        }
    }

    fun toggleShuffle() {
        commandSerializer.executeCommand {
            mediaController?.let { controller ->
            // Don't allow shuffle toggle if queue is empty
            if (_currentQueue.value.songs.isEmpty()) {
                Log.w(TAG, "Cannot toggle shuffle - queue is empty")
                return@executeCommand
            }

            val currentSongs = _currentQueue.value.songs
            val currentSong = _currentSong.value
            val currentQueueSourceName = queueStateHolder.currentQueueSourceName.value
            val useExoPlayerShuffle = shuffleUsesExoplayer.value
            val newShuffleMode = !_isShuffleEnabled.value

            Log.d(TAG, "Toggle shuffle mode to: $newShuffleMode (useExoPlayerShuffle=$useExoPlayerShuffle)")

            if (newShuffleMode) {
                // Enable Shuffle
                if (!queueStateHolder.hasOriginalQueue()) {
                    queueStateHolder.setOriginalQueueOrder(currentSongs)
                    queueStateHolder.saveOriginalQueueState(currentSongs, currentQueueSourceName)
                }

                val currentMediaId = controller.currentMediaItem?.mediaId ?: currentSong?.id
                val currentPosition = controller.currentPosition
                val wasPlaying = controller.isPlaying

                if (useExoPlayerShuffle) {
                    controller.shuffleModeEnabled = true
                    _isShuffleEnabled.value = true

                    // Sync queue so UI reflects current player timeline after enabling shuffle.
                    syncQueueWithMediaController()
                    saveQueueToPersistence()

                    if (wasPlaying && !controller.isPlaying) {
                        if (!canStartPlayback("toggleShuffle.restoreExo")) return@let
                        controller.play()
                    }
                } else {
                    val currentIndex = currentMediaId
                        ?.let { mediaId -> currentSongs.indexOfFirst { it.id == mediaId }.takeIf { it >= 0 } }
                        ?: controller.currentMediaItemIndex.coerceIn(0, (currentSongs.size - 1).coerceAtLeast(0))

                    // Run heavy shuffle work off main to keep UI and playback responsive.
                    viewModelScope.launch {
                        val shuffledQueue = withContext(Dispatchers.Default) {
                            buildShuffleQueuePreservingPlayed(currentSongs, currentIndex)
                        }

                        withContext(Dispatchers.Main) {
                            // For large queues, use bulk replace (1 IPC call) instead of
                            // per-item moveMediaItem (n IPC calls) which freezes the UI.
                            if (currentSongs.size > BULK_REPLACE_THRESHOLD) {
                                replacePlayerQueue(controller, shuffledQueue, currentMediaId, currentPosition)
                            } else {
                                val reordered = reorderQueueInPlace(controller, shuffledQueue)
                                if (!reordered) {
                                    replacePlayerQueue(controller, shuffledQueue, currentMediaId, currentPosition)
                                }
                            }

                            // Keep ExoPlayer shuffle disabled when using manual queue shuffling.
                            controller.shuffleModeEnabled = false
                            updateQueueState(shuffledQueue)
                            _isShuffleEnabled.value = true

                            if (wasPlaying && !controller.isPlaying) {
                                if (!canStartPlayback("toggleShuffle.restoreManual")) return@withContext
                                controller.play()
                            }
                        }
                    }
                }

                // Save shuffle state preference
                if (shuffleModePersistence.value) {
                    appSettings.setSavedShuffleState(true)
                }
            } else {
                // Disable Shuffle
                // Save shuffle state preference
                if (shuffleModePersistence.value) {
                    appSettings.setSavedShuffleState(false)
                }

                if (useExoPlayerShuffle && !queueStateHolder.hasOriginalQueue()) {
                    controller.shuffleModeEnabled = false
                    _isShuffleEnabled.value = false
                    queueStateHolder.clearOriginalQueue()
                    syncQueueWithMediaController()
                    saveQueueToPersistence()
                    return@executeCommand
                }

                if (!queueStateHolder.hasOriginalQueue()) {
                    controller.shuffleModeEnabled = false
                    _isShuffleEnabled.value = false
                    syncQueueWithMediaController()
                    saveQueueToPersistence()
                    return@executeCommand
                }

                val baseOriginalQueue = queueStateHolder.getFilteredOriginalQueue(currentSongs)
                val originalQueue = buildRestoredQueueWithAdditions(baseOriginalQueue, currentSongs)
                val wasPlaying = controller.isPlaying
                val currentPosition = controller.currentPosition
                val currentSongId = currentSong?.id ?: controller.currentMediaItem?.mediaId
                val originalIndex = originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }

                if (originalQueue.isEmpty() || originalIndex == null) {
                    queueStateHolder.clearOriginalQueue()
                    controller.shuffleModeEnabled = false
                    _isShuffleEnabled.value = false
                    syncQueueWithMediaController()
                    saveQueueToPersistence()
                    return@executeCommand
                }

                // Use bulk replace for large queues to avoid UI freeze
                if (originalQueue.size > BULK_REPLACE_THRESHOLD) {
                    replacePlayerQueue(controller, originalQueue, currentSongId, currentPosition)
                } else {
                    val reordered = reorderQueueInPlace(controller, originalQueue)
                    if (!reordered) {
                        replacePlayerQueue(controller, originalQueue, currentSongId, currentPosition)
                    }
                }

                updateQueueState(originalQueue)
                controller.shuffleModeEnabled = false
                _isShuffleEnabled.value = false
                queueStateHolder.clearOriginalQueue()

                if (wasPlaying && !controller.isPlaying) {
                    if (!canStartPlayback("toggleShuffle.disable")) return@let
                    controller.play()
                }
            }
        }
        }
    }

    /**
     * Preserves the already-played segment and shuffles only upcoming songs.
     * This prevents unplayed tracks from being moved behind the current index and skipped.
     */
    private fun buildShuffleQueuePreservingPlayed(currentSongs: List<Song>, currentIndex: Int): List<Song> {
        if (currentSongs.size <= 1) return currentSongs.toList()

        val safeIndex = currentIndex.coerceIn(0, currentSongs.lastIndex)
        val played = if (safeIndex > 0) currentSongs.subList(0, safeIndex) else emptyList()
        val current = currentSongs[safeIndex]
        val upcoming = if (safeIndex + 1 < currentSongs.size) {
            currentSongs.subList(safeIndex + 1, currentSongs.size)
        } else {
            emptyList()
        }

        val shuffledUpcoming = QueueUtils.buildShuffleQueue(upcoming)
        return buildList(currentSongs.size) {
            addAll(played)
            add(current)
            addAll(shuffledUpcoming)
        }
    }

    /**
     * Restores original queue ordering and preserves songs added while shuffle was enabled.
     */
    private fun buildRestoredQueueWithAdditions(originalQueue: List<Song>, currentSongs: List<Song>): List<Song> {
        if (originalQueue.isEmpty()) return currentSongs.toList()

        val remainingOriginalCounts = originalQueue.groupingBy { it.id }.eachCount().toMutableMap()
        val additions = mutableListOf<Song>()

        currentSongs.forEach { song ->
            val remaining = remainingOriginalCounts[song.id] ?: 0
            if (remaining > 0) {
                remainingOriginalCounts[song.id] = remaining - 1
            } else {
                additions.add(song)
            }
        }

        return originalQueue + additions
    }

    /**
     * Replaces the player timeline with [newQueue] in a single setMediaItems call,
     * preserving the currently playing song and its position. This is O(1) IPC calls
     * versus O(n) for reorderQueueInPlace, making it suitable for large queue shuffles.
     */
    private fun replacePlayerQueue(player: androidx.media3.common.Player, newQueue: List<Song>, currentSongId: String?, currentPosition: Long) {
        val wasPlaying = player.isPlaying
        val targetIndex = if (currentSongId != null) {
            newQueue.indexOfFirst { it.id == currentSongId }.takeIf { it != -1 } ?: 0
        } else 0

        val mediaItems = newQueue.map { it.toMediaItem() }

        player.setMediaItems(mediaItems, targetIndex, currentPosition)
        if (wasPlaying && !player.isPlaying) {
            player.play()
        }
    }

    /**
     * Reorders the player queue in place by moving items to their desired positions.
     * Returns true if successful, false if the reordering failed and a full replacement is needed.
     */
    private fun reorderQueueInPlace(player: androidx.media3.common.Player, desiredQueue: List<Song>): Boolean {
        return try {
            val currentIds = mutableListOf<String>()
            for (i in 0 until player.mediaItemCount) {
                currentIds.add(player.getMediaItemAt(i).mediaId)
            }

            val desiredIds = desiredQueue.map { it.id }
            if (desiredIds.size != currentIds.size) {
                Log.w(TAG, "Cannot reorder queue in place: size mismatch current=${currentIds.size}, desired=${desiredIds.size}")
                return false
            }

            for (targetIndex in desiredIds.indices) {
                val desiredId = desiredIds[targetIndex]
                if (currentIds[targetIndex] == desiredId) continue

                var fromIndex = -1
                for (searchIndex in targetIndex + 1 until currentIds.size) {
                    if (currentIds[searchIndex] == desiredId) {
                        fromIndex = searchIndex
                        break
                    }
                }

                if (fromIndex == -1) {
                    Log.w(TAG, "Cannot reorder queue in place: target mediaId '$desiredId' not found")
                    return false
                }

                player.moveMediaItem(fromIndex, targetIndex)
                val movedId = currentIds.removeAt(fromIndex)
                currentIds.add(targetIndex, movedId)
            }

            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot reorder queue in place: player operation failed", e)
            false
        }
    }

    /**
     * Updates the queue state and persists it.
     */
    private fun updateQueueState(newQueue: List<Song>) {
        val currentSong = _currentSong.value
        val currentIndex = if (currentSong != null) {
            newQueue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
        } else {
            0
        }

        _currentQueue.value = Queue(newQueue, currentIndex)
        saveQueueToPersistence()
    }

    fun toggleRepeatMode() {
        mediaController?.let { controller ->
            val currentMode = controller.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            Log.d(TAG, "Toggle repeat mode from $currentMode to $newMode")
            setRepeatMode(newMode)
        }
    }

    fun setRepeatMode(mode: Int, persist: Boolean = true) {
        val normalizedMode = when (mode) {
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL -> mode
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.let { controller ->
            controller.repeatMode = normalizedMode
            _repeatMode.value = normalizedMode
            if (persist && appSettings.repeatModePersistence.value) {
                appSettings.setSavedRepeatMode(normalizedMode)
            }
            Log.d(TAG, "Repeat mode set to $normalizedMode (persist=$persist)")
        }
    }
    
    fun toggleFavorite() {
        _currentSong.value?.let { song ->
            toggleFavorite(song)
        }
    }

    /**
     * Toggle favorite status for a specific song
     */
    fun toggleFavorite(song: Song) {
        val favoriteSong = song.toFavoriteSnapshot()
        val songId = favoriteSong.id
        val currentFavorites = _favoriteSongs.value.toMutableSet()
        
        if (currentFavorites.contains(songId)) {
            Log.d(TAG, "Removing song from favorites: ${song.title}")
            currentFavorites.remove(songId)
            
            // Update _isFavorite only if this is the current song
            if (_currentSong.value?.id?.toStableCatalogSongId() == songId) {
                _isFavorite.value = false
            }
            
            // Remove from Favorites playlist
            _playlists.value = _playlists.value.map { playlist ->
                if (playlist.id == "1") {
                    playlist.copy(
                        songs = playlist.songs.filter {
                            it.id.toStableCatalogSongId() != songId
                        }
                    )
                } else {
                    playlist
                }
            }
            savePlaylists()
        } else {
            Log.d(TAG, "Adding song to favorites: ${song.title}")
            currentFavorites.add(songId)
            
            // Update _isFavorite only if this is the current song
            if (_currentSong.value?.id?.toStableCatalogSongId() == songId) {
                _isFavorite.value = true
            }
            
            // Add to Favorites playlist
            _playlists.value = _playlists.value.map { playlist ->
                if (playlist.id == "1") {
                    playlist.copy(songs = playlist.songs + favoriteSong)
                } else {
                    playlist
                }
            }
            savePlaylists()
        }
        
        _favoriteSongs.value = currentFavorites
        saveFavoriteSongs()
        
        // Notify MediaPlaybackService about favorite state change
        notifyMediaServiceFavoriteChange()
        
        // Update widget with new favorite state after a brief delay to ensure state is saved
        if (_currentSong.value?.id?.toStableCatalogSongId() == songId) {
            viewModelScope.launch {
                delay(100) // Brief delay to ensure state is saved
                val isFavorite = currentFavorites.contains(songId)
                WidgetUpdater.updateWidget(
                    getApplication(),
                    song,
                    _isPlaying.value,
                    mediaController?.hasPreviousMediaItem() ?: false,
                    mediaController?.hasNextMediaItem() ?: false,
                    isFavorite
                )
            }
        }
    }

    /**
     * Catalog favorites persist only their stable rendition identity. Playback URLs are short-lived
     * descriptors and must be resolved again when the user plays the item from Liked.
     */
    private fun Song.toFavoriteSnapshot(): Song {
        val stableId = id.toStableCatalogSongId()
        if (!stableId.startsWith(CATALOG_SONG_ID_PREFIX)) return this
        val renditionId = stableId.removePrefix(CATALOG_SONG_ID_PREFIX)
        return copy(
            id = stableId,
            uri = Uri.parse(CatalogPlaybackPolicy.deferredUri(renditionId)),
            path = null,
        )
    }
    
    private fun notifyMediaServiceFavoriteChange() {
        // Send broadcast to notify MediaPlaybackService about favorite state change
        val intent = Intent("io.github.cluno1.sonorus.action.FAVORITE_CHANGED").apply {
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    fun clearCurrentSong() {
        Log.d(TAG, "Clearing current song (mini player dismissed)")
        // Clear in-memory state FIRST to prevent listener callbacks from re-saving stale data
        _currentSong.value = null
        resetBluetoothLyricsBroadcastState(clearLastBroadcast = true)
        _isPlaying.value = false
        _progress.value = 0f
        _duration.value = 0L
        _currentQueue.value = Queue(emptyList(), -1)
        progressUpdateJob?.cancel()
        // Clear the persisted queue so it won't be restored on screen navigation or app restart
        appSettings.clearSavedQueue()
        // Stop the player and clear media items AFTER clearing state
        mediaController?.let { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
    }

    /**
     * Start device monitoring when needed (e.g., when player screen is open)
     */
    private var deviceMonitoringJob: Job? = null
    
    fun startDeviceMonitoringOnDemand() {
        if (deviceMonitoringJob?.isActive == true) {
            Log.d(TAG, "Device monitoring already running")
            return
        }
        
        Log.d(TAG, "Starting on-demand device monitoring")
        deviceMonitoringJob = viewModelScope.launch {
            while (isActive) {
                // Refresh devices every 5 seconds when actively monitoring
                // AudioNoisyReceiver handles urgent changes (e.g., headphones unplugged)
                audioDeviceManager.refreshDevices()
                delay(5000)
            }
        }
    }

    /**
     * Stop device monitoring when not needed
     */
    fun stopDeviceMonitoringOnDemand() {
        Log.d(TAG, "Stopping on-demand device monitoring")
        deviceMonitoringJob?.cancel()
        deviceMonitoringJob = null
    }

    /**
     * Refresh audio devices manually
     */
    fun refreshAudioDevices() {
        Log.d(TAG, "Manually refreshing audio devices")
        audioDeviceManager.refreshDevices()
    }

    /**
     * Set the current audio output device
     */
    fun setCurrentDevice(device: PlaybackLocation) {
        Log.d(TAG, "Setting current device: ${device.name}")
        audioDeviceManager.setCurrentDevice(device)
    }

    /**
     * Show the system output switcher dialog
     */
    fun showOutputSwitcherDialog() {
        audioDeviceManager.showOutputSwitcherDialog()
    }

    private fun cleanupResources() {
        Log.d(TAG, "ViewModel being cleared - cleaning up resources")
        
        // Cancel all coroutine jobs
        progressUpdateJob?.cancel()
        deviceMonitoringJob?.cancel()
        lyricsFetchJob?.cancel()
        
        // Release media controller
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        
        // Clean up audio device manager
        audioDeviceManager.cleanup()
        
        // Clean up sleep timer
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        
        // Clean up repository to prevent memory leaks
        try {
            getMusicRepository().cleanup()
            Log.d(TAG, "Repository cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up repository", e)
        }
    }

    private suspend fun populateRecentlyAddedPlaylist() {
        if (!isPlaylistsLoaded) {
            Log.w(TAG, "Skipping populateRecentlyAddedPlaylist — playlists not loaded yet")
            return
        }
        val recentlyAddedPlaylist = _playlists.value.find { it.id == "2" && it.name == "Recently Added" }
        if (recentlyAddedPlaylist == null) {
            Log.e(TAG, "Recently Added playlist not found, cannot populate.")
            return
        }

        // Get all songs, sorted by dateAdded descending
        val currentFilteredSongs = filteredSongs.value
        val topSongs = currentFilteredSongs.sortedByDescending { it.dateAdded }.take(50)

        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == "2") {
                playlist.copy(songs = topSongs, dateModified = System.currentTimeMillis())
            } else {
                playlist
            }
        }
        savePlaylists()
        Log.d(TAG, "Populated Recently Added playlist with ${topSongs.size} songs based on dateAdded.")
    }

    /**
     * Populates the "Most Played" playlist based on song play counts.
     */
    private suspend fun populateMostPlayedPlaylist() {
        if (!isPlaylistsLoaded) {
            Log.w(TAG, "Skipping populateMostPlayedPlaylist — playlists not loaded yet")
            return
        }
        val mostPlayedPlaylist = _playlists.value.find { it.id == "3" && it.name == "Most Played" }
        if (mostPlayedPlaylist == null) {
            Log.e(TAG, "Most Played playlist not found, cannot populate.")
            return
        }

        // Use filteredSongs to respect whitelist/blacklist mode, filtering out unplayed songs
        val sortedSongsByPlayCount = filteredSongs.value
            .filter { song -> (_songPlayCounts.value[song.id] ?: 0) > 0 }
            .sortedByDescending { song -> _songPlayCounts.value[song.id] ?: 0 }

        // Take top 50 most played songs
        val topSongs = sortedSongsByPlayCount.take(50)

        // Add these songs to the playlist, replacing existing ones to keep it fresh
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == "3") {
                playlist.copy(songs = topSongs, dateModified = System.currentTimeMillis())
            } else {
                playlist
            }
        }
        savePlaylists()
        Log.d(TAG, "Populated Most Played playlist with ${topSongs.size} songs.")
    }

    // New functions for playlist management
    fun createPlaylist(name: String, songs: List<Song> = emptyList(), showSnackbar: ((String) -> Unit)? = null) {
        val playlistSongs = songs.map { it.toFavoriteSnapshot() }
        viewModelScope.launch {
            val newPlaylist = repository.createPlaylist(name)
            var updatedPlaylist = newPlaylist
            if (songs.isNotEmpty()) {
                val filteredSongsSet: Set<String> = filteredSongs.value.map { song: Song -> song.id }.toSet()
                val existingSongIds: Set<String> = newPlaylist.songs.map { song: Song -> song.id }.toSet()
                val songsToAdd = playlistSongs.filter { song ->
                    val isStreaming = song.uri.toString().startsWith("http://") || 
                                      song.uri.toString().startsWith("https://") || 
                                      song.uri.toString().startsWith("streaming://") ||
                                      _songs.value.none { it.id == song.id }
                    (isStreaming || filteredSongsSet.contains(song.id)) && !existingSongIds.contains(song.id)
                }
                if (songsToAdd.isNotEmpty()) {
                    updatedPlaylist = newPlaylist.copy(
                        songs = songsToAdd,
                        dateModified = System.currentTimeMillis()
                    )
                }
            }
            _playlists.value = _playlists.value + updatedPlaylist
            savePlaylists()
            Log.d(TAG, "Created new playlist: ${updatedPlaylist.name} with ${updatedPlaylist.songs.size} songs")
            if (showSnackbar != null) {
                if (songs.isNotEmpty()) {
                    showSnackbar("Created playlist '${updatedPlaylist.name}' with ${updatedPlaylist.songs.size} songs")
                } else {
                    showSnackbar("Created playlist '${updatedPlaylist.name}'")
                }
            }
        }
    }

    fun addSongToPlaylist(song: Song, playlistId: String, showSnackbar: (String) -> Unit) {
        val playlistSong = song.toFavoriteSnapshot()
        // Check if song is filtered out (blacklisted or not whitelisted)
        val filteredSongsSet: Set<String> = filteredSongs.value.map { song: Song -> song.id }.toSet()
        val isStreaming = song.uri.toString().startsWith("http://") || 
                          song.uri.toString().startsWith("https://") || 
                          song.uri.toString().startsWith("streaming://") ||
                          _songs.value.none { it.id == song.id }
        if (!isStreaming && !filteredSongsSet.contains(song.id)) {
            showSnackbar("Cannot add ${song.title} - song is filtered out")
            return
        }
        
        var success = false
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                // Check if song is already in the playlist
                if (playlist.songs.any { it.id.toStableCatalogSongId() == playlistSong.id }) {
                    showSnackbar("${song.title} is already in playlist '${playlist.name}'")
                    playlist
                } else {
                    val updatedSongs = playlist.songs + playlistSong
                    success = true
                    showSnackbar("Added ${song.title} to ${playlist.name}")
                    playlist.copy(
                        songs = updatedSongs,
                        dateModified = System.currentTimeMillis()
                    )
                }
            } else {
                playlist
            }
        }
        savePlaylists()
        if (success) {
            if (playlistId == "1") {
                val currentFavorites = _favoriteSongs.value.toMutableSet()
                if (!currentFavorites.contains(song.id)) {
                    currentFavorites.add(song.id)
                    _favoriteSongs.value = currentFavorites
                    if (_currentSong.value?.id == song.id) {
                        _isFavorite.value = true
                    }
                    saveFavoriteSongs()
                    notifyMediaServiceFavoriteChange()
                }
            }
            Log.d(TAG, "Added song to playlist: ${song.title}")
        }
    }

    /**
     * Add multiple songs to a playlist at once
     * Returns a result with success count and playlist name
     */
    fun addSongsToPlaylist(songs: List<Song>, playlistId: String): Pair<Int, String> {
        val localSongs = songs.filterNot { it.id.startsWith("rhythm-catalog:") }
        val filteredSongsSet: Set<String> = filteredSongs.value.map { song: Song -> song.id }.toSet()
        var successCount = 0
        var playlistName = ""
        
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlistName = playlist.name
                val existingSongIds = playlist.songs.map { it.id }.toSet()
                
                // Filter songs that are not filtered out and not already in playlist
                val songsToAdd = localSongs.filter { song ->
                    val isStreaming = song.uri.toString().startsWith("http://") || 
                                      song.uri.toString().startsWith("https://") || 
                                      song.uri.toString().startsWith("streaming://") ||
                                      _songs.value.none { it.id == song.id }
                    (isStreaming || filteredSongsSet.contains(song.id)) && !existingSongIds.contains(song.id)
                }
                
                successCount = songsToAdd.size
                
                if (songsToAdd.isNotEmpty()) {
                    val updatedSongs = playlist.songs + songsToAdd
                    playlist.copy(
                        songs = updatedSongs,
                        dateModified = System.currentTimeMillis()
                    )
                } else {
                    playlist
                }
            } else {
                playlist
            }
        }
        
        if (successCount > 0) {
            savePlaylists()
            if (playlistId == "1") {
                val currentFavorites = _favoriteSongs.value.toMutableSet()
                val targetPlaylist = _playlists.value.find { it.id == "1" }
                if (targetPlaylist != null) {
                    currentFavorites.addAll(targetPlaylist.songs.map { it.id })
                    _favoriteSongs.value = currentFavorites
                    if (_currentSong.value != null && currentFavorites.contains(_currentSong.value?.id)) {
                        _isFavorite.value = true
                    }
                    saveFavoriteSongs()
                    notifyMediaServiceFavoriteChange()
                }
            }
            Log.d(TAG, "Added $successCount songs to playlist: $playlistName")
        }
        
        return Pair(successCount, playlistName)
    }

    fun removeSongFromPlaylist(song: Song, playlistId: String, showSnackbar: (String) -> Unit) {
        var success = false
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val updatedSongs = playlist.songs.filter { it.id != song.id }
                if (updatedSongs.size < playlist.songs.size) {
                    success = true
                    showSnackbar("Removed ${song.title} from ${playlist.name}")
                    playlist.copy(
                        songs = updatedSongs,
                        dateModified = System.currentTimeMillis()
                    )
                } else {
                    playlist // Song not found in playlist, no change
                }
            } else {
                playlist
            }
        }
        savePlaylists()
        if (success) {
            if (playlistId == "1") {
                val currentFavorites = _favoriteSongs.value.toMutableSet()
                currentFavorites.remove(song.id)
                _favoriteSongs.value = currentFavorites
                if (_currentSong.value?.id == song.id) {
                    _isFavorite.value = false
                }
                saveFavoriteSongs()
                notifyMediaServiceFavoriteChange()
            }
            Log.d(TAG, "Removed song from playlist: ${song.title}")
        } else {
            Log.d(TAG, "Song '${song.title}' not found in playlist '$playlistId' for removal.")
        }
    }

    /**
     * Reorder songs in a playlist by moving a song from one position to another
     */
    fun reorderPlaylistSongs(playlistId: String, fromIndex: Int, toIndex: Int) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId && fromIndex != toIndex) {
                val mutableSongs = playlist.songs.toMutableList()
                if (fromIndex in mutableSongs.indices && toIndex in mutableSongs.indices) {
                    val song = mutableSongs.removeAt(fromIndex)
                    mutableSongs.add(toIndex, song)
                    playlist.copy(
                        songs = mutableSongs,
                        dateModified = System.currentTimeMillis()
                    )
                } else {
                    playlist
                }
            } else {
                playlist
            }
        }
        savePlaylists()
        Log.d(TAG, "Reordered song in playlist from $fromIndex to $toIndex")
    }

    /**
     * Update the entire song list for a playlist (for sorting or batch reordering)
     */
    fun updatePlaylistSongs(playlistId: String, newSongList: List<Song>) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    songs = newSongList,
                    dateModified = System.currentTimeMillis()
                )
            } else {
                playlist
            }
        }
        savePlaylists()
        if (playlistId == "1") {
            val newFavoriteIds = newSongList.map { it.id }.toSet()
            _favoriteSongs.value = newFavoriteIds
            if (_currentSong.value != null) {
                _isFavorite.value = newFavoriteIds.contains(_currentSong.value?.id)
            }
            saveFavoriteSongs()
            notifyMediaServiceFavoriteChange()
        }
        Log.d(TAG, "Updated song list for playlist: $playlistId")
    }

    fun deletePlaylist(playlistId: String) {
        // Prevent deleting default playlists
        if (playlistId == "1" || playlistId == "2" || playlistId == "3") {
            Log.d(TAG, "Cannot delete default playlist: $playlistId")
            return
        }
        
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()
        Log.d(TAG, "Deleted playlist: $playlistId")
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    name = newName,
                    dateModified = System.currentTimeMillis()
                )
            } else {
                playlist
            }
        }
        Log.d(TAG, "Renamed playlist to: $newName")
        savePlaylists()
    }

    fun setSelectedSongForPlaylist(song: Song) {
        _selectedSongForPlaylist.value = song
    }

    fun clearSelectedSongForPlaylist() {
        _selectedSongForPlaylist.value = null
    }
    
    fun setTargetPlaylistForAddingSongs(playlistId: String) {
        _targetPlaylistId.value = playlistId
    }
    
    fun clearTargetPlaylistForAddingSongs() {
        _targetPlaylistId.value = null
    }
    
    // Playlist Import/Export Functions
    
    /**
     * Exports a single playlist to the specified format
     */
    fun exportPlaylist(
        playlistId: String,
        format: PlaylistImportExportUtils.PlaylistExportFormat,
        userSelectedDirectoryUri: Uri? = null,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (_isLibraryRefreshing.value || _isMediaScanning.value) {
                val err = Exception("Cannot export playlists while a media scan is in progress. Please wait for the scan to finish.")
                onResult(Result.failure(err))
                showOperationResultNotification(
                    notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_export_title),
                    content = "Media scan in progress. Please wait.",
                    isError = true,
                    autoDismissMs = 5000L
                )
                return@launch
            }
            showOperationProgressNotification(
                notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                title = context.getString(R.string.notification_playlist_export_title),
                content = context.getString(R.string.operation_exporting_playlists),
                indeterminate = true
            )

            try {
                val playlist = _playlists.value.find { it.id == playlistId }
                if (playlist == null) {
                    onResult(Result.failure(IllegalArgumentException("Playlist not found")))
                    showOperationResultNotification(
                        notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_playlist_export_title),
                        content = context.getString(R.string.notification_playlist_not_found),
                        isError = true,
                        autoDismissMs = 8000L
                    )
                    return@launch
                }
                
                val result = withContext(Dispatchers.IO) {
                    PlaylistImportExportUtils.exportPlaylist(
                        context = getApplication<Application>(),
                        playlist = playlist,
                        format = format,
                        userSelectedDirectoryUri = userSelectedDirectoryUri
                    )
                }
                
                result.fold(
                    onSuccess = { file ->
                        val message = "Playlist '${playlist.name}' exported to ${file.absolutePath}"
                        onResult(Result.success(message))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_export_title),
                            content = context.getString(R.string.notification_playlist_export_complete),
                            isError = false
                        )
                        Log.d(TAG, "Successfully exported playlist: ${playlist.name}")
                    },
                    onFailure = { exception ->
                        onResult(Result.failure(exception))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_export_title),
                            content = context.getString(R.string.notification_playlist_export_failed),
                            isError = true,
                            autoDismissMs = 8000L
                        )
                        Log.e(TAG, "Failed to export playlist: ${playlist.name}", exception)
                    }
                )
            } catch (e: Exception) {
                onResult(Result.failure(e))
                showOperationResultNotification(
                    notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_export_title),
                    content = context.getString(R.string.notification_playlist_export_failed),
                    isError = true,
                    autoDismissMs = 8000L
                )
                Log.e(TAG, "Error in exportPlaylist", e)
            }
        }
    }
    
    /**
     * Exports all user-created playlists (excludes default playlists)
     */
    fun exportAllPlaylists(
        format: PlaylistImportExportUtils.PlaylistExportFormat,
        includeDefaultPlaylists: Boolean = false,
        userSelectedDirectoryUri: Uri? = null,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (_isLibraryRefreshing.value || _isMediaScanning.value) {
                val err = Exception("Cannot export playlists while a media scan is in progress. Please wait for the scan to finish.")
                onResult(Result.failure(err))
                showOperationResultNotification(
                    notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_export_title),
                    content = "Media scan in progress. Please wait.",
                    isError = true,
                    autoDismissMs = 5000L
                )
                return@launch
            }
            val operationText = if (userSelectedDirectoryUri != null) {
                context.getString(R.string.operation_exporting_playlists_location)
            } else {
                context.getString(R.string.operation_exporting_playlists)
            }
            showOperationProgressNotification(
                notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                title = context.getString(R.string.notification_playlist_export_title),
                content = operationText,
                indeterminate = true
            )

            try {
                Log.d(TAG, "Starting export all playlists operation")
                
                val playlistsToExport = if (includeDefaultPlaylists) {
                    _playlists.value
                } else {
                    // Exclude default playlists (Favorites, Recently Added, Most Played)
                    _playlists.value.filter { it.id !in listOf("1", "2", "3") }
                }
                
                if (playlistsToExport.isEmpty()) {
                    Log.w(TAG, "No playlists to export")
                    onResult(Result.failure(IllegalStateException("No playlists available to export")))
                    showOperationResultNotification(
                        notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_playlist_export_title),
                        content = context.getString(R.string.notification_no_playlists_to_export),
                        isError = true,
                        autoDismissMs = 8000L
                    )
                    return@launch
                }
                
                Log.d(TAG, "Exporting ${playlistsToExport.size} playlists")
                
                // Add timeout to prevent infinite loading
                val result = withTimeoutOrNull(30000) { // 30 second timeout
                    withContext(Dispatchers.IO) {
                        PlaylistImportExportUtils.exportAllPlaylists(
                            context = getApplication<Application>(),
                            playlists = playlistsToExport,
                            format = format,
                            userSelectedDirectoryUri = userSelectedDirectoryUri
                        )
                    }
                }
                
                if (result == null) {
                    Log.e(TAG, "Export operation timed out")
                    val timeoutError = Exception("Export operation timed out after 30 seconds")
                    onResult(Result.failure(timeoutError))
                    showOperationResultNotification(
                        notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_playlist_export_title),
                        content = context.getString(R.string.notification_operation_timed_out),
                        isError = true,
                        autoDismissMs = 8000L
                    )
                    return@launch
                }
                
                result.fold(
                    onSuccess = { file ->
                        Log.d(TAG, "Successfully exported ${playlistsToExport.size} playlists to ${file.absolutePath}")
                        val message = "Successfully exported ${playlistsToExport.size} playlists to ${file.absolutePath}"
                        onResult(Result.success(message))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_export_title),
                            content = context.resources.getQuantityString(
                                R.plurals.notification_playlist_export_all_complete,
                                playlistsToExport.size,
                                playlistsToExport.size
                            ),
                            isError = false
                        )
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to export all playlists", exception)
                        onResult(Result.failure(exception))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_export_title),
                            content = context.getString(R.string.notification_playlist_export_failed),
                            isError = true,
                            autoDismissMs = 8000L
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in exportAllPlaylists", e)
                onResult(Result.failure(e))
                showOperationResultNotification(
                    notificationId = PLAYLIST_EXPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_export_title),
                    content = context.getString(R.string.notification_playlist_export_failed),
                    isError = true,
                    autoDismissMs = 8000L
                )
            }
        }
    }
    
    /**
     * Imports a playlist from a file URI.
     *
     * Handles three cases:
     *  1. A regular single-playlist file (JSON/M3U/PLS) → single-playlist import as before.
     *  2. A Rhythm app backup JSON → batch import of ALL playlists in the backup.
     *  3. Any other error → failure callback.
     */
    fun importPlaylist(
        uri: Uri,
        onResult: (Result<String>) -> Unit,
        onRestartRequired: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (_isLibraryRefreshing.value || _isMediaScanning.value) {
                val err = Exception("Cannot import playlists while a media scan is in progress. Please wait for the scan to finish.")
                onResult(Result.failure(err))
                showOperationResultNotification(
                    notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_import_title),
                    content = "Media scan in progress. Please wait.",
                    isError = true,
                    autoDismissMs = 5000L
                )
                return@launch
            }
            showOperationProgressNotification(
                notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                title = context.getString(R.string.notification_playlist_import_title),
                content = context.getString(R.string.operation_importing_playlist),
                indeterminate = true
            )

            try {
                Log.d(TAG, "Starting import playlist operation from URI: $uri")
                
                // Add timeout to prevent infinite loading
                val result = withTimeoutOrNull(30000) { // 30 second timeout
                    withContext(Dispatchers.IO) {
                        PlaylistImportExportUtils.importPlaylist(
                            context = getApplication<Application>(),
                            uri = uri,
                            availableSongs = _songs.value
                        )
                    }
                }
                
                if (result == null) {
                    Log.e(TAG, "Import operation timed out")
                    val timeoutError = Exception("Import operation timed out after 30 seconds")
                    onResult(Result.failure(timeoutError))
                    showOperationResultNotification(
                        notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_playlist_import_title),
                        content = context.getString(R.string.notification_operation_timed_out),
                        isError = true,
                        autoDismissMs = 8000L
                    )
                    return@launch
                }
                
                result.fold(
                    onSuccess = { importedPlaylist ->
                        Log.d(TAG, "Successfully imported playlist from utility: ${importedPlaylist.name}")
                        
                        // Check if playlist with same name already exists
                        val existingPlaylist = _playlists.value.find { it.name == importedPlaylist.name }
                        val finalPlaylist = if (existingPlaylist != null) {
                            // Add suffix to avoid name conflict
                            importedPlaylist.copy(name = "${importedPlaylist.name} (Imported)")
                        } else {
                            importedPlaylist
                        }
                        
                        // Add the imported playlist to our list
                        _playlists.value = _playlists.value + finalPlaylist
                        savePlaylists()
                        
                        val matchedCount = finalPlaylist.songs.size
                        Log.d(TAG, "Successfully imported playlist: ${finalPlaylist.name} with $matchedCount songs")
                        val message = "Successfully imported playlist '${finalPlaylist.name}' with $matchedCount songs. App restart recommended for best experience."
                        onResult(Result.success(message))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_import_title),
                            content = context.resources.getQuantityString(
                                R.plurals.notification_playlist_import_complete,
                                matchedCount,
                                finalPlaylist.name,
                                matchedCount
                            ),
                            isError = false
                        )
                        
                        // Recommend app restart for imported playlists to ensure proper UI update
                        onRestartRequired?.invoke()
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to import playlist from $uri", exception)
                        onResult(Result.failure(exception))
                        showOperationResultNotification(
                            notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                            title = context.getString(R.string.notification_playlist_import_title),
                            content = context.getString(R.string.notification_playlist_import_failed),
                            isError = true,
                            autoDismissMs = 8000L
                        )
                    }
                )
            } catch (e: RhythmBackupDetectedException) {
                // The selected file is a full Rhythm app backup — import ALL playlists from it
                Log.d(TAG, "Rhythm backup detected during playlist import; switching to batch import")
                try {
                    val batchResult = withContext(Dispatchers.IO) {
                        PlaylistImportExportUtils.importPlaylistsFromRhythmBackup(
                            context = getApplication<Application>(),
                            backupJson = e.backupJson,
                            availableSongs = _songs.value
                        )
                    }

                    batchResult.fold(
                        onSuccess = { importedPlaylists ->
                            val existingNames = _playlists.value.map { it.name }.toSet()
                            val dedupedPlaylists = importedPlaylists.map { playlist ->
                                if (existingNames.contains(playlist.name)) {
                                    playlist.copy(name = "${playlist.name} (Imported)")
                                } else {
                                    playlist
                                }
                            }

                            _playlists.value = _playlists.value + dedupedPlaylists
                            savePlaylists()

                            val playlistCount = dedupedPlaylists.size
                            val totalSongs = dedupedPlaylists.sumOf { it.songs.size }
                            Log.d(TAG, "Batch import from Rhythm backup: $playlistCount playlists, $totalSongs songs")

                            val message = "Successfully imported $playlistCount playlist(s) with $totalSongs song(s) from a compatible backup."
                            onResult(Result.success(message))
                            showOperationResultNotification(
                                notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                                title = context.getString(R.string.notification_playlist_import_title),
                                content = message,
                                isError = false
                            )
                            onRestartRequired?.invoke()
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "Failed batch import from Rhythm backup", exception)
                            onResult(Result.failure(exception))
                            showOperationResultNotification(
                                notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                                title = context.getString(R.string.notification_playlist_import_title),
                                content = context.getString(R.string.notification_playlist_import_failed),
                                isError = true,
                                autoDismissMs = 8000L
                            )
                        }
                    )
                } catch (batchEx: Exception) {
                    Log.e(TAG, "Error during batch import from Rhythm backup", batchEx)
                    onResult(Result.failure(batchEx))
                    showOperationResultNotification(
                        notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                        title = context.getString(R.string.notification_playlist_import_title),
                        content = context.getString(R.string.notification_playlist_import_failed),
                        isError = true,
                        autoDismissMs = 8000L
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in importPlaylist", e)
                onResult(Result.failure(e))
                showOperationResultNotification(
                    notificationId = PLAYLIST_IMPORT_NOTIFICATION_ID,
                    title = context.getString(R.string.notification_playlist_import_title),
                    content = context.getString(R.string.notification_playlist_import_failed),
                    isError = true,
                    autoDismissMs = 8000L
                )
            }
        }
    }
    
    /**
     * Restarts the application to ensure all changes take effect
     */
    fun restartApp() {
        val context = getApplication<Application>().applicationContext
        
        // Synchronously commit all pending SharedPreferences edits to disk before killing the process
        appSettings.commit()
        
        // Create restart intent
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // Start the activity and exit current process
            context.startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    
    /**
     * Gets all available export formats
     */
    fun getAvailableExportFormats(): List<PlaylistImportExportUtils.PlaylistExportFormat> {
        return PlaylistImportExportUtils.PlaylistExportFormat.values().toList()
    }
    
    // Sort library functionality
    fun sortLibrary() {
        viewModelScope.launch {
            // Cycle through sort orders
            _sortOrder.value = when (_sortOrder.value) {
                SortOrder.TITLE_ASC -> SortOrder.TITLE_DESC
                SortOrder.TITLE_DESC -> SortOrder.ARTIST_ASC
                SortOrder.ARTIST_ASC -> SortOrder.ARTIST_DESC
                SortOrder.ARTIST_DESC -> SortOrder.ALBUM_ASC
                SortOrder.ALBUM_ASC -> SortOrder.ALBUM_DESC
                SortOrder.ALBUM_DESC -> SortOrder.YEAR_ASC
                SortOrder.YEAR_ASC -> SortOrder.YEAR_DESC
                SortOrder.YEAR_DESC -> SortOrder.DATE_ADDED_ASC
                SortOrder.DATE_ADDED_ASC -> SortOrder.DATE_ADDED_DESC
                SortOrder.DATE_ADDED_DESC -> SortOrder.DATE_MODIFIED_ASC
                SortOrder.DATE_MODIFIED_ASC -> SortOrder.DATE_MODIFIED_DESC
                SortOrder.DATE_MODIFIED_DESC -> SortOrder.TITLE_ASC
            }
            
            // Sort songs based on current sort order
            _songs.value = when (_sortOrder.value) {
                SortOrder.TITLE_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.title })
                SortOrder.TITLE_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.title }.reversed())
                SortOrder.ARTIST_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.artist })
                SortOrder.ARTIST_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.artist }.reversed())
                SortOrder.ALBUM_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.album })
                SortOrder.ALBUM_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.album }.reversed())
                SortOrder.YEAR_ASC -> _songs.value.sortedBy { it.year }
                SortOrder.YEAR_DESC -> _songs.value.sortedByDescending { it.year }
                SortOrder.DATE_ADDED_ASC -> _songs.value.sortedBy { it.dateAdded }
                SortOrder.DATE_ADDED_DESC -> _songs.value.sortedByDescending { it.dateAdded }
                SortOrder.DATE_MODIFIED_ASC -> _songs.value.sortedBy { it.dateModified }
                SortOrder.DATE_MODIFIED_DESC -> _songs.value.sortedByDescending { it.dateModified }
            }
            
            // Sort albums based on current sort order
            _albums.value = when (_sortOrder.value) {
                SortOrder.TITLE_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title })
                SortOrder.TITLE_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title }.reversed())
                SortOrder.ARTIST_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.artist })
                SortOrder.ARTIST_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.artist }.reversed())
                SortOrder.ALBUM_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title })
                SortOrder.ALBUM_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title }.reversed())
                SortOrder.YEAR_ASC -> _albums.value.sortedBy { it.year }
                SortOrder.YEAR_DESC -> _albums.value.sortedByDescending { it.year }
                SortOrder.DATE_ADDED_ASC -> _albums.value.sortedBy { it.id.toLongOrNull() ?: 0L } // Placeholder for date added
                SortOrder.DATE_ADDED_DESC -> _albums.value.sortedByDescending { it.id.toLongOrNull() ?: 0L } // Placeholder for date added
                SortOrder.DATE_MODIFIED_ASC -> _albums.value.sortedBy { it.dateModified }
                SortOrder.DATE_MODIFIED_DESC -> _albums.value.sortedByDescending { it.dateModified }
            }
            
            // Sort playlists by name, keeping the default playlists at the top
            _playlists.value = _playlists.value.sortedWith(
                compareBy<Playlist> { 
                    // Put default playlists first
                    when (it.id) {
                        "1" -> 0 // Favorites
                        "2" -> 1 // Recently Added
                        "3" -> 2 // Most Played
                        else -> 3 // User-created playlists
                    }
                }.thenBy { 
                    // Then sort by name according to current sort order
                    when (_sortOrder.value) {
                        SortOrder.TITLE_ASC, SortOrder.ARTIST_ASC, SortOrder.ALBUM_ASC, SortOrder.YEAR_ASC, SortOrder.DATE_ADDED_ASC, SortOrder.DATE_MODIFIED_ASC -> it.name
                        SortOrder.TITLE_DESC, SortOrder.ARTIST_DESC, SortOrder.ALBUM_DESC, SortOrder.YEAR_DESC, SortOrder.DATE_ADDED_DESC, SortOrder.DATE_MODIFIED_DESC -> it.name.reversed()
                    }
                }
            )
            
            Log.d(TAG, "Library sorted: ${_sortOrder.value}")
        }
    }
    
    // Set specific sort order
    fun setSortOrder(newSortOrder: SortOrder) {
        viewModelScope.launch {
            if (_sortOrder.value != newSortOrder) {
                _sortOrder.value = newSortOrder
                // Save sort order to AppSettings for persistence
                appSettings.setSongsSortOrder(newSortOrder.name)
                
                // Sort songs based on new sort order
                _songs.value = when (newSortOrder) {
                    SortOrder.TITLE_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.title })
                    SortOrder.TITLE_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.title }.reversed())
                    SortOrder.ARTIST_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.artist })
                    SortOrder.ARTIST_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.artist }.reversed())
                    SortOrder.ALBUM_ASC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.album })
                    SortOrder.ALBUM_DESC -> _songs.value.sortedWith(NaturalSortComparator.comparator<Song> { it.album }.reversed())
                    SortOrder.YEAR_ASC -> _songs.value.sortedBy { it.year }
                    SortOrder.YEAR_DESC -> _songs.value.sortedByDescending { it.year }
                    SortOrder.DATE_ADDED_ASC -> _songs.value.sortedBy { it.dateAdded }
                    SortOrder.DATE_ADDED_DESC -> _songs.value.sortedByDescending { it.dateAdded }
                    SortOrder.DATE_MODIFIED_ASC -> _songs.value.sortedBy { it.dateModified }
                    SortOrder.DATE_MODIFIED_DESC -> _songs.value.sortedByDescending { it.dateModified }
                }
                
                // Sort albums based on new sort order
                _albums.value = when (newSortOrder) {
                    SortOrder.TITLE_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title })
                    SortOrder.TITLE_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title }.reversed())
                    SortOrder.ARTIST_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.artist })
                    SortOrder.ARTIST_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.artist }.reversed())
                    SortOrder.ALBUM_ASC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title })
                    SortOrder.ALBUM_DESC -> _albums.value.sortedWith(NaturalSortComparator.comparator<Album> { it.title }.reversed())
                    SortOrder.YEAR_ASC -> _albums.value.sortedBy { it.year }
                    SortOrder.YEAR_DESC -> _albums.value.sortedByDescending { it.year }
                    SortOrder.DATE_ADDED_ASC -> _albums.value.sortedBy { it.id.toLongOrNull() ?: 0L } // Placeholder for date added
                    SortOrder.DATE_ADDED_DESC -> _albums.value.sortedByDescending { it.id.toLongOrNull() ?: 0L } // Placeholder for date added
                    SortOrder.DATE_MODIFIED_ASC -> _albums.value.sortedBy { it.dateModified }
                    SortOrder.DATE_MODIFIED_DESC -> _albums.value.sortedByDescending { it.dateModified }
                }
                
                // Sort playlists by name, keeping the default playlists at the top
                _playlists.value = _playlists.value.sortedWith(
                    compareBy<Playlist> { 
                        // Put default playlists first
                        when (it.id) {
                            "1" -> 0 // Favorites
                            "2" -> 1 // Recently Added
                            "3" -> 2 // Most Played
                            else -> 3 // User-created playlists
                        }
                    }.thenBy { 
                        // Then sort by name according to current sort order
                        when (newSortOrder) {
                            SortOrder.TITLE_ASC, SortOrder.ARTIST_ASC, SortOrder.ALBUM_ASC -> it.name
                            SortOrder.TITLE_DESC, SortOrder.ARTIST_DESC, SortOrder.ALBUM_DESC -> it.name.reversed()
                            SortOrder.YEAR_ASC -> it.dateCreated
                            SortOrder.YEAR_DESC -> -it.dateCreated // Descending
                            SortOrder.DATE_ADDED_ASC -> it.dateCreated
                            SortOrder.DATE_ADDED_DESC -> -it.dateCreated // Descending
                            SortOrder.DATE_MODIFIED_ASC -> it.dateModified
                            SortOrder.DATE_MODIFIED_DESC -> -it.dateModified // Descending
                        }
                    }
                )
                
                Log.d(TAG, "Sort order set to: $newSortOrder")
            }
        }
    }

    /**
     * Loads all settings from SharedPreferences
     */
    private fun loadSettings() {
        Log.d(TAG, "Loading settings from SharedPreferences")
        // Settings are now handled by AppSettings, verify they are loaded
        Log.d(TAG, "Loaded settings: " +
                "Gapless=${enableGaplessPlayback.value}, " +
                "Crossfade=${enableCrossfade.value} (${crossfadeDuration.value}s), " +
                "Normalization=${enableAudioNormalization.value}, " +
                "ReplayGain=${enableReplayGain.value}, " +
                "ShowLyrics=${showLyrics.value}, " +
                "OnlineOnlyLyrics=${showOnlineOnlyLyrics.value}, " +
                "UseSystemTheme=${useSystemTheme.value}, " +
                "DarkMode=${darkMode.value}, " +
                "AutoConnectDevice=${autoConnectDevice.value}, " +
                "MaxCacheSize=${maxCacheSize.value}, " +
                "AutoTrimCache=true")

        // Load song play counts
        _songPlayCounts.value = appSettings.songPlayCounts.value
    }

    /**
     * Updates the show lyrics setting
     */
    fun setShowLyrics(show: Boolean) {
        appSettings.setShowLyrics(show)
        if (show && currentSong.value != null) {
            fetchLyricsForCurrentSong()
        } else {
            _currentLyrics.value = null
        }
    }
    
    /**
     * Updates the lyrics source preference setting
     */
    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        appSettings.setLyricsSourcePreference(preference)
        if (showLyrics.value && currentSong.value != null) {
            fetchLyricsForCurrentSong()
        }
    }

    fun selectCatalogLyricsLanguage(language: String) {
        val nowPlaying = _catalogNowPlaying.value ?: return
        applyCatalogLyrics(nowPlaying, language)
    }

    /**
     * Refreshes the active Catalog item's descriptive metadata without rebuilding the
     * MediaItem or interrupting playback. Persisted queues may contain an older library
     * snapshot, so newly added lyric variants must be merged back into the live queue.
     */
    fun refreshCatalogNowPlayingMetadata(refreshed: RhythmNowPlayingItem) {
        val current = _catalogNowPlaying.value ?: return
        if (current.renditionId != refreshed.renditionId) return

        val merged = if (current.renditionRevision > refreshed.renditionRevision) {
            current.copy(assetId = current.assetId ?: refreshed.assetId)
        } else {
            refreshed.copy(assetId = current.assetId ?: refreshed.assetId)
        }
        if (merged == current) return

        _catalogQueue.value = _catalogQueue.value.map { entry ->
            if (entry.nowPlaying.renditionId == merged.renditionId) {
                entry.copy(
                    nowPlaying = merged.copy(
                        assetId = entry.nowPlaying.assetId ?: merged.assetId,
                    ),
                )
            } else {
                entry
            }
        }
        _catalogNowPlaying.value = merged
        applyCatalogLyrics(merged, _catalogLyricsLanguage.value)
        saveQueueToPersistence()
    }

    private fun applyCatalogLyrics(
        nowPlaying: RhythmNowPlayingItem?,
        requestedLanguage: String? = null,
    ) {
        val variants = nowPlaying?.lyricsVariants().orEmpty()
        _catalogLyricsLanguages.value = variants.map { it.language }
        val selected = requestedLanguage?.let { requested ->
            variants.firstOrNull { it.language.equals(requested, ignoreCase = true) }
        } ?: selectCatalogLyricsVariant(variants, preferredCatalogLyricsLanguageTags())
        _catalogLyricsLanguage.value = selected?.language
        val connection = catalogRepository.connection()
        val draft = if (
            nowPlaying != null && selected != null && connection.draftNamespace.isNotBlank()
        ) {
            catalogLyricsDraftStore.load(
                connection.draftNamespace,
                nowPlaying.renditionId,
                selected.language,
            )
        } else {
            null
        }
        _catalogLyricsSyncState.value = when (draft?.status) {
            "pending" -> CatalogLyricsSyncState(CatalogLyricsSyncStatus.PENDING)
            "synced" -> CatalogLyricsSyncState(CatalogLyricsSyncStatus.SYNCED)
            "conflict" -> CatalogLyricsSyncState(CatalogLyricsSyncStatus.CONFLICT)
            "error" -> CatalogLyricsSyncState(CatalogLyricsSyncStatus.ERROR)
            else -> CatalogLyricsSyncState(
                if (nowPlaying == null) CatalogLyricsSyncStatus.UNAVAILABLE
                else CatalogLyricsSyncStatus.CLEAN,
            )
        }
        val unsyncedDraft = draft?.takeIf { it.status != "synced" }
        _currentLyrics.value = selected?.let { variant ->
            catalogLyricsData(
                lyrics = unsyncedDraft?.lyrics ?: variant.lyrics,
                format = unsyncedDraft?.format ?: variant.format,
                language = variant.language,
            )
        }
    }

    private fun catalogLyricsData(lyrics: String, format: String, language: String): LyricsData {
        val source = "RHYTHM_LIBRARY|$language"
        val extracted = LyricsContributionAttribution.extract(lyrics, format)
        val visibleLyrics = extracted.visibleLyrics
        return when (format) {
            "lrc", "enhanced_lrc" -> LyricsData(
                plainLyrics = visibleLyrics.lines().joinToString("\n") { line ->
                    line.replace(Regex("^\\[\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?]"), "")
                        .replace(Regex("<\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?>"), "")
                        .trim()
                },
                syncedLyrics = visibleLyrics,
                source = source,
                isCorrected = true,
                contributions = extracted.contributions,
            )
            "word_by_word_json" -> LyricsData(
                plainLyrics = null,
                syncedLyrics = null,
                wordByWordLyrics = visibleLyrics,
                source = source,
                isCorrected = true,
                contributions = extracted.contributions,
            )
            else -> LyricsData(
                plainLyrics = visibleLyrics,
                syncedLyrics = null,
                source = source,
                isCorrected = true,
                contributions = extracted.contributions,
            )
        }
    }

    private fun backendLyricFormat(lyrics: String, format: String?): String = when {
        format == "WORD_BY_WORD" -> "word_by_word_json"
        RhythmLyricsParser.isTtmlContent(lyrics) -> "ttml"
        format == "LINE_BY_LINE" && LyricsParser.hasWordTimestamps(lyrics) -> "enhanced_lrc"
        format == "LINE_BY_LINE" &&
            io.github.cluno1.sonorus.util.LrcTimingEditor.hasLineTimestamp(lyrics) -> "lrc"
        else -> "plain"
    }

    private fun RhythmNowPlayingItem.withLyricsVariant(
        language: String,
        lyrics: String,
        format: String,
        revision: Int = renditionRevision,
    ): RhythmNowPlayingItem {
        val normalizedLanguage = normalizeCatalogLyricsLanguageTag(language)
        val primaryLanguage = lyricsLanguage?.let(::normalizeCatalogLyricsLanguageTag)
        val updatedFormats = lyricsFormats.orEmpty()
            .filterNot { it.language.equals(normalizedLanguage, ignoreCase = true) }
            .plus(CatalogLyricLanguageFormat(normalizedLanguage, format))
        return if (this.lyrics.isNullOrBlank()) {
            copy(
                renditionRevision = revision,
                lyrics = lyrics,
                lyricsLanguage = normalizedLanguage,
                lyricsTranslations = emptyList(),
                lyricsFormats = updatedFormats,
            )
        } else if (primaryLanguage.equals(normalizedLanguage, ignoreCase = true)) {
            copy(
                renditionRevision = revision,
                lyrics = lyrics,
                lyricsFormats = updatedFormats,
            )
        } else {
            copy(
                renditionRevision = revision,
                lyricsTranslations = lyricsTranslations.orEmpty()
                    .filterNot { it.language.equals(normalizedLanguage, ignoreCase = true) }
                    .plus(CatalogLyricsTranslation(normalizedLanguage, lyrics)),
                lyricsFormats = updatedFormats,
            )
        }
    }

    private fun preferredCatalogLyricsLanguageTags(): List<String> {
        val locales = ConfigurationCompat.getLocales(getApplication<Application>().resources.configuration)
        return buildList {
            for (index in 0 until locales.size()) {
                locales[index]?.toLanguageTag()?.takeIf(String::isNotBlank)?.let(::add)
            }
            if (isEmpty()) add(Locale.getDefault().toLanguageTag())
        }
    }
    
    /**
     * Fetches lyrics for the current song if settings allow, with automatic retry logic
     * Now properly handles race conditions and song changes
     */
    private fun fetchLyricsForCurrentSong(retryCount: Int = 0) {
        val song = currentSong.value ?: return
        if (LanSubsonicPlaybackPolicy.isLanSong(
                enabled = ProductCapabilities.lanSubsonicOnly,
                mediaId = song.id,
                uri = song.uri.toString(),
            )
        ) {
            lyricsFetchJob?.cancel()
            _currentLyrics.value = null
            _isLoadingLyrics.value = false
            return
        }
        if (deviceLyricsCandidatesSongId != song.id) {
            _deviceLyricsCandidates.value = emptyList()
            deviceLyricsCandidatesSongId = null
            deviceLyricsCandidateIndex = -1
        }
        if (song.id.startsWith("rhythm-catalog:")) {
            applyCatalogLyrics(_catalogNowPlaying.value, _catalogLyricsLanguage.value)
            _isLoadingLyrics.value = false
            return
        }
        
        // Cancel any previous lyrics fetch to prevent race conditions
        lyricsFetchJob?.cancel()
        
        // Clear current lyrics first only on initial attempt
        // This prevents showing stale lyrics from previous song
        if (retryCount == 0) {
            _currentLyrics.value = null
        }
        
        // Check if lyrics are enabled
        if (!showLyrics.value) {
            return
        }
        
        // Get the user's lyrics source preference
        val lyricsPreference = appSettings.lyricsSourcePreference.value
        
        // Create a new job for this lyrics fetch
        lyricsFetchJob = viewModelScope.launch {
            _isLoadingLyrics.value = true
            try {
                // Store the song ID to validate it hasn't changed
                val fetchingSongId = song.id
                Log.d(TAG, "Fetching lyrics for: ${song.artist} - ${song.title} (ID: $fetchingSongId) using preference: $lyricsPreference")
                
                val lyricsData = repository.fetchLyrics(
                    artist = song.artist, 
                    title = song.title, 
                    songId = song.id,
                    songUri = song.uri,
                    sourcePreference = lyricsPreference
                )
                
                // Verify the song hasn't changed before updating lyrics
                if (currentSong.value?.id == fetchingSongId && isActive) {
                    _currentLyrics.value = lyricsData
                    if (lyricsData?.hasLyrics() == true) {
                        Log.d(TAG, "Successfully fetched lyrics for: ${song.artist} - ${song.title}")
                    } else {
                        Log.d(TAG, "No lyrics found for: ${song.artist} - ${song.title}")
                    }
                } else {
                    Log.d(TAG, "Song changed during lyrics fetch, discarding results for: ${song.title}")
                }
            } catch (e: Exception) {
                // If the job was cancelled, don't log as error
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Lyrics fetch cancelled for: ${song.title}")
                    throw e // Re-throw to properly handle cancellation
                }
                
                Log.e(TAG, "Error fetching lyrics (attempt ${retryCount + 1})", e)
                
                // Only retry if the song is still the same
                if (currentSong.value?.id != song.id) {
                    Log.d(TAG, "Song changed, not retrying lyrics fetch")
                    return@launch
                }
                
                // Retry logic - try up to 2 more times with exponential backoff
                if (retryCount < 2) {
                    val delayMs = (1000 * (retryCount + 1)).toLong() // 1s, 2s delays
                    Log.d(TAG, "Retrying lyrics fetch in ${delayMs}ms...")
                    delay(delayMs)
                    
                    // Check if the song is still the same before retrying
                    if (currentSong.value?.id == song.id && isActive) {
                        fetchLyricsForCurrentSong(retryCount + 1)
                        return@launch
                    } else {
                        Log.d(TAG, "Song changed during retry, cancelling lyrics fetch")
                    }
                } else {
                    // All retries failed - only show error if song hasn't changed
                    if (currentSong.value?.id == song.id && isActive) {
                        Log.w(TAG, "Failed to fetch lyrics after ${retryCount + 1} attempts")
                        _currentLyrics.value = LyricsData("Unable to load lyrics. Tap to retry.", null)
                    }
                }
            } finally {
                if (isActive) {
                    _isLoadingLyrics.value = false
                }
            }
        }
    }
    
    /**
     * Manually retry fetching lyrics for the current song
     */
    fun retryFetchLyrics() {
        Log.d(TAG, "Manual retry of lyrics fetch requested")
        fetchLyricsForCurrentSong(0)
    }
    
    /**
     * Clear lyrics cache for current song and refetch from sources
     */
    fun clearLyricsCacheAndRefetch() {
        val current = _currentSong.value
        if (LanSubsonicPlaybackPolicy.isLanSong(
                enabled = ProductCapabilities.lanSubsonicOnly,
                mediaId = current?.id,
                uri = current?.uri?.toString(),
            )
        ) {
            lyricsFetchJob?.cancel()
            _currentLyrics.value = null
            _isLoadingLyrics.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val song = _currentSong.value
                if (song != null) {
                    val artist = song.artist
                    val title = song.title
                    
                    // Settings-level action intentionally clears every lyrics cache.
                    repository.clearAllLyricsCaches()
                    Log.d(TAG, "Cleared all lyrics caches before refetching: $title by $artist")
                    
                    // Clear in-memory lyrics
                    _currentLyrics.value = null
                    
                    // Reset time offset
                    _lyricsTimeOffset.value = 0
                    
                    // Refetch from sources with force refresh
                    val lyrics = repository.fetchLyrics(
                        artist = artist,
                        title = title,
                        songId = song.id,
                        songUri = song.uri,
                        sourcePreference = appSettings.lyricsSourcePreference.value,
                        forceRefresh = true,
                        forceOnline = false
                    )
                    
                    withContext(Dispatchers.Main) {
                        _currentLyrics.value = lyrics
                        _isLoadingLyrics.value = false
                    }
                } else {
                    Log.w(TAG, "Cannot clear cache - no current song")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing lyrics cache", e)
            }
        }
    }

    fun startDeviceManualMetadata(
        songId: String,
        kind: DeviceManualMetadataKind,
        targetArtistName: String? = null,
    ) {
        deviceManualMetadataJob?.cancel()
        deviceManualMetadataRequestId++
        val song = findDeviceSong(songId)
        _deviceManualMetadataState.value = DeviceManualMetadataUiState(
            songId = songId,
            targetArtistName = targetArtistName?.trim()?.takeIf(String::isNotEmpty) ?: song?.artist,
            kind = kind,
            error = if (song == null) DeviceManualMetadataError.SONG_UNAVAILABLE else null,
        )
    }

    fun searchDeviceManualMetadata(
        songId: String,
        kind: DeviceManualMetadataKind,
        query: DeviceMetadataRequest,
        providers: Set<DevicePublicMetadataProvider>,
    ) {
        val normalizedTitle = query.title.trim()
        val normalizedArtist = query.artist?.trim().orEmpty()
        if (kind != DeviceManualMetadataKind.ARTIST_ARTWORK && normalizedTitle.isBlank()) {
            _deviceManualMetadataState.value = _deviceManualMetadataState.value.copy(
                error = DeviceManualMetadataError.TITLE_REQUIRED,
            )
            return
        }
        if (kind == DeviceManualMetadataKind.ARTIST_ARTWORK && normalizedArtist.isBlank()) {
            _deviceManualMetadataState.value = _deviceManualMetadataState.value.copy(
                error = DeviceManualMetadataError.ARTIST_REQUIRED,
            )
            return
        }
        val supportedProviders = when (kind) {
            DeviceManualMetadataKind.LYRICS -> providers.intersect(
                setOf(DevicePublicMetadataProvider.LRCLIB),
            )
            DeviceManualMetadataKind.ARTWORK -> providers.intersect(
                setOf(
                    DevicePublicMetadataProvider.MUSICBRAINZ_CAA,
                    DevicePublicMetadataProvider.DEEZER,
                    DevicePublicMetadataProvider.ITUNES,
                ),
            )
            DeviceManualMetadataKind.ARTIST_ARTWORK -> providers.intersect(
                setOf(DevicePublicMetadataProvider.DEEZER),
            )
            DeviceManualMetadataKind.DETAILS -> providers.intersect(
                setOf(
                    DevicePublicMetadataProvider.MUSICBRAINZ_CAA,
                    DevicePublicMetadataProvider.DEEZER,
                    DevicePublicMetadataProvider.ITUNES,
                    DevicePublicMetadataProvider.WIKIPEDIA,
                ),
            )
        }
        if (supportedProviders.isEmpty()) {
            _deviceManualMetadataState.value = _deviceManualMetadataState.value.copy(
                error = DeviceManualMetadataError.PROVIDER_REQUIRED,
            )
            return
        }
        val song = findDeviceSong(songId)
        if (song == null) {
            _deviceManualMetadataState.value = DeviceManualMetadataUiState(
                songId = songId,
                kind = kind,
                error = DeviceManualMetadataError.SONG_UNAVAILABLE,
            )
            return
        }

        deviceManualMetadataJob?.cancel()
        val requestId = ++deviceManualMetadataRequestId
        val targetArtistName = _deviceManualMetadataState.value.targetArtistName ?: song.artist
        deviceManualMetadataJob = viewModelScope.launch {
            _deviceManualMetadataState.value = DeviceManualMetadataUiState(
                songId = songId,
                targetArtistName = targetArtistName,
                kind = kind,
                isSearching = true,
                providerStatuses = supportedProviders.associateWith {
                    DeviceManualProviderStatus.LOADING
                },
            )
            try {
                var lyricsCandidates = emptyList<DeviceLyricsCandidate>()
                var artworkCandidates = emptyList<DeviceArtworkCandidate>()
                var artistArtworkCandidates = emptyList<DeviceArtistArtworkCandidate>()
                var detailsCandidates = emptyList<DeviceDetailsCandidate>()
                var editorialCandidates = emptyList<DeviceEditorialCandidate>()
                val providerStatuses = when (kind) {
                    DeviceManualMetadataKind.LYRICS -> {
                        val result = repository.searchDeviceLyricsCandidatesWithStatus(song, query)
                        lyricsCandidates = result.candidates
                        val status = result.toManualStatus()
                        if (requestId == deviceManualMetadataRequestId) {
                            _deviceManualMetadataState.value = _deviceManualMetadataState.value.copy(
                                lyricsCandidates = result.candidates,
                                providerStatuses = mapOf(result.provider to status),
                            )
                        }
                        mapOf(result.provider to status)
                    }
                    DeviceManualMetadataKind.ARTWORK -> {
                        val searches = supportedProviders.map { provider ->
                            async {
                                val result = repository.searchDeviceArtworkCandidatesWithStatus(
                                    song,
                                    query,
                                    provider,
                                )
                                if (requestId == deviceManualMetadataRequestId) {
                                    val current = _deviceManualMetadataState.value
                                    _deviceManualMetadataState.value = current.copy(
                                        artworkCandidates = (current.artworkCandidates + result.candidates)
                                            .distinctBy { it.provider to it.externalId }
                                            .sortedByDescending(DeviceArtworkCandidate::confidence),
                                        providerStatuses = current.providerStatuses +
                                            (result.provider to result.toManualStatus()),
                                    )
                                }
                                result
                            }
                        }
                        val results = searches.map { it.await() }
                        artworkCandidates = results.flatMap { it.candidates }
                            .distinctBy { it.provider to it.externalId }
                            .sortedByDescending(DeviceArtworkCandidate::confidence)
                        results.associate { it.provider to it.toManualStatus() }
                    }
                    DeviceManualMetadataKind.ARTIST_ARTWORK -> {
                        val result = repository.searchDeviceArtistArtworkCandidatesWithStatus(
                            normalizedArtist,
                        )
                        artistArtworkCandidates = result.candidates
                        val status = result.toManualStatus()
                        if (requestId == deviceManualMetadataRequestId) {
                            _deviceManualMetadataState.value = _deviceManualMetadataState.value.copy(
                                artistArtworkCandidates = result.candidates,
                                providerStatuses = mapOf(result.provider to status),
                            )
                        }
                        mapOf(result.provider to status)
                    }
                    DeviceManualMetadataKind.DETAILS -> {
                        val detailSearches = supportedProviders
                            .filterNot { it == DevicePublicMetadataProvider.WIKIPEDIA }
                            .map { provider ->
                                async {
                                    repository.searchDeviceDetailsCandidatesWithStatus(
                                        song,
                                        query,
                                        provider,
                                    )
                                }
                            }
                        val editorialSearch = if (
                            DevicePublicMetadataProvider.WIKIPEDIA in supportedProviders
                        ) {
                            async { repository.searchDeviceEditorialCandidatesWithStatus(query) }
                        } else {
                            null
                        }
                        val detailResults = detailSearches.map { it.await() }
                        val editorialResult = editorialSearch?.await()
                        detailsCandidates = detailResults.flatMap { it.candidates }
                            .distinctBy { it.provider to it.externalId }
                            .sortedByDescending(DeviceDetailsCandidate::confidence)
                        editorialCandidates = editorialResult?.candidates.orEmpty()
                        detailResults.associate { it.provider to it.toManualStatus() } +
                            listOfNotNull(editorialResult).associate {
                                it.provider to it.toManualStatus()
                            }
                    }
                }
                if (requestId != deviceManualMetadataRequestId) return@launch
                _deviceManualMetadataState.value = DeviceManualMetadataUiState(
                    songId = songId,
                    targetArtistName = targetArtistName,
                    kind = kind,
                    hasSearched = true,
                    lyricsCandidates = lyricsCandidates,
                    artworkCandidates = artworkCandidates,
                    artistArtworkCandidates = artistArtworkCandidates,
                    detailsCandidates = detailsCandidates,
                    editorialCandidates = editorialCandidates,
                    providerStatuses = providerStatuses,
                    error = DeviceManualMetadataError.REQUEST_FAILED.takeIf {
                        providerStatuses.isNotEmpty() &&
                            providerStatuses.values.all { it == DeviceManualProviderStatus.FAILED }
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Manual DEVICE metadata lookup failed", error)
                if (requestId == deviceManualMetadataRequestId) {
                    _deviceManualMetadataState.value = DeviceManualMetadataUiState(
                        songId = songId,
                        targetArtistName = targetArtistName,
                        kind = kind,
                        hasSearched = true,
                        error = DeviceManualMetadataError.REQUEST_FAILED,
                    )
                }
            }
        }
    }

    fun applyDeviceManualLyrics(songId: String, candidate: DeviceLyricsCandidate) {
        val state = _deviceManualMetadataState.value
        val song = findDeviceSong(songId)
        if (song == null || state.songId != songId || candidate !in state.lyricsCandidates) return
        deviceManualMetadataJob?.cancel()
        val requestId = ++deviceManualMetadataRequestId
        deviceManualMetadataJob = viewModelScope.launch {
            _deviceManualMetadataState.value = state.copy(isApplying = true, error = null)
            try {
                val selected = repository.applyDeviceLyricsCandidate(song, candidate)
                if (requestId != deviceManualMetadataRequestId) return@launch
                if (_currentSong.value?.id == songId) _currentLyrics.value = selected
                _deviceManualMetadataState.value = state.copy(applied = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply manual DEVICE lyrics", error)
                if (requestId == deviceManualMetadataRequestId) {
                    _deviceManualMetadataState.value = state.copy(
                        error = DeviceManualMetadataError.APPLY_FAILED,
                    )
                }
            }
        }
    }

    fun applyDeviceManualArtwork(
        songId: String,
        candidate: DeviceArtworkCandidate,
        saveTarget: DeviceArtworkSaveTarget,
        destinationTreeUri: Uri?,
    ) {
        val state = _deviceManualMetadataState.value
        val song = findDeviceSong(songId)
        if (song == null || state.songId != songId || candidate !in state.artworkCandidates) return
        deviceManualMetadataJob?.cancel()
        val requestId = ++deviceManualMetadataRequestId
        deviceManualMetadataJob = viewModelScope.launch {
            _deviceManualMetadataState.value = state.copy(isApplying = true, error = null)
            try {
                val artwork = repository.applyDeviceArtworkCandidate(
                    song,
                    candidate,
                    saveTarget,
                    destinationTreeUri,
                )
                    ?: error("Artwork response was not a valid image")
                if (requestId != deviceManualMetadataRequestId) return@launch
                val targetAlbumKey = io.github.cluno1.sonorus.features.local.data.device
                    .DeviceAlbumIdentity.key(song)
                val withSelection = _songs.value.map { current ->
                    if (
                        targetAlbumKey != null &&
                        io.github.cluno1.sonorus.features.local.data.device.DeviceAlbumIdentity.key(current) == targetAlbumKey
                    ) current.copy(artworkUri = artwork) else current
                }
                val projected = repository.projectDeviceArtwork(withSelection)
                _songs.value = projected
                val projectedById = projected.associateBy(Song::id)
                _currentQueue.value = _currentQueue.value.copy(
                    songs = _currentQueue.value.songs.map { queued ->
                        projectedById[queued.id] ?: queued
                    },
                )
                _recentlyPlayed.value = _recentlyPlayed.value.map { recent ->
                    projectedById[recent.id] ?: recent
                }
                repository.updateAndPersistSongs(projected)
                _albums.value = repository.loadAlbums()
                val currentSong = _currentSong.value
                if (
                    currentSong != null &&
                    io.github.cluno1.sonorus.features.local.data.device.DeviceAlbumIdentity.key(currentSong) == targetAlbumKey
                ) {
                    _currentSong.value = projected.firstOrNull { it.id == currentSong.id }
                        ?: currentSong.copy(artworkUri = artwork)
                }
                _deviceManualMetadataState.value = state.copy(applied = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply manual DEVICE artwork", error)
                if (requestId == deviceManualMetadataRequestId) {
                    _deviceManualMetadataState.value = state.copy(
                        error = DeviceManualMetadataError.APPLY_FAILED,
                    )
                }
            }
        }
    }

    fun applyDeviceManualArtistArtwork(
        songId: String,
        candidate: DeviceArtistArtworkCandidate,
    ) {
        val state = _deviceManualMetadataState.value
        val song = findDeviceSong(songId)
        val targetArtistName = state.targetArtistName?.trim()?.takeIf(String::isNotEmpty)
            ?: song?.artist
            ?: return
        if (
            song == null ||
            state.songId != songId ||
            state.kind != DeviceManualMetadataKind.ARTIST_ARTWORK ||
            candidate !in state.artistArtworkCandidates
        ) return

        deviceManualMetadataJob?.cancel()
        val requestId = ++deviceManualMetadataRequestId
        deviceManualMetadataJob = viewModelScope.launch {
            _deviceManualMetadataState.value = state.copy(isApplying = true, error = null)
            try {
                val artwork = repository.applyDeviceArtistArtworkCandidate(
                    targetArtistName,
                    candidate,
                ) ?: error("Artist artwork response was not a valid image")
                if (requestId != deviceManualMetadataRequestId) return@launch
                _artists.value = _artists.value.map { artist ->
                    if (artist.name.equals(targetArtistName, ignoreCase = true)) {
                        artist.copy(artworkUri = artwork)
                    } else {
                        artist
                    }
                }
                _deviceManualMetadataState.value = state.copy(applied = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply manual DEVICE artist artwork", error)
                if (requestId == deviceManualMetadataRequestId) {
                    _deviceManualMetadataState.value = state.copy(
                        error = DeviceManualMetadataError.APPLY_FAILED,
                    )
                }
            }
        }
    }

    fun applyDeviceManualDetails(
        songId: String,
        candidate: DeviceDetailsCandidate,
        fields: Set<DeviceDetailsField>,
    ) {
        val state = _deviceManualMetadataState.value
        val song = findDeviceSong(songId)
        if (
            song == null || state.songId != songId ||
            state.kind != DeviceManualMetadataKind.DETAILS ||
            candidate !in state.detailsCandidates || fields.isEmpty()
        ) return

        deviceManualMetadataJob?.cancel()
        val requestId = ++deviceManualMetadataRequestId
        deviceManualMetadataJob = viewModelScope.launch {
            _deviceManualMetadataState.value = state.copy(isApplying = true, error = null)
            try {
                val updated = repository.applyDeviceDetailsCandidate(song, candidate, fields)
                if (requestId != deviceManualMetadataRequestId) return@launch
                updateCurrentSongMetadata(updated)
                _deviceManualMetadataState.value = state.copy(applied = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not apply manual DEVICE details", error)
                if (requestId == deviceManualMetadataRequestId) {
                    _deviceManualMetadataState.value = state.copy(
                        error = DeviceManualMetadataError.APPLY_FAILED,
                    )
                }
            }
        }
    }

    fun clearDeviceManualMetadata() {
        deviceManualMetadataJob?.cancel()
        deviceManualMetadataRequestId++
        _deviceManualMetadataState.value = DeviceManualMetadataUiState()
    }

    private fun findDeviceSong(songId: String): Song? =
        (_songs.value.firstOrNull { it.id == songId } ?: _currentSong.value?.takeIf { it.id == songId })
            ?.takeIf { DeviceMetadataPolicy.isEligible(it.id, it.uri.scheme) }

    private fun <T> io.github.cluno1.sonorus.features.local.data.device.DeviceProviderSearchResult<T>
        .toManualStatus(): DeviceManualProviderStatus = when {
            failed -> DeviceManualProviderStatus.FAILED
            candidates.isEmpty() -> DeviceManualProviderStatus.EMPTY
            else -> DeviceManualProviderStatus.SUCCESS
        }

    fun searchAndApplyBestDeviceLyrics() {
        val song = _currentSong.value?.takeUnless { it.id.startsWith("rhythm-catalog:") } ?: return
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            repository.clearLyricsCacheForSong(song)
            if (_currentSong.value?.id != song.id) {
                _isLoadingLyrics.value = false
                return@launch
            }
            _currentLyrics.value = null
            _deviceLyricsCandidates.value = emptyList()
            deviceLyricsCandidatesSongId = null
            val candidates = repository.searchDeviceLyricsCandidates(song)
            if (_currentSong.value?.id != song.id) {
                _isLoadingLyrics.value = false
                return@launch
            }
            _deviceLyricsCandidates.value = candidates
            deviceLyricsCandidatesSongId = song.id
            val automatic = candidates.firstOrNull()?.takeIf {
                io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataMatcher.isAutomaticMatch(
                    it.confidence,
                    candidates.getOrNull(1)?.confidence,
                    io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataRepository.MIN_AUTO_CONFIDENCE,
                    unconditionalThreshold = io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataRepository.EXACT_AUTO_CONFIDENCE,
                )
            }
            deviceLyricsCandidateIndex = automatic?.let(candidates::indexOf) ?: -1
            automatic?.let {
                val selected = repository.applyDeviceLyricsCandidate(song, it)
                if (_currentSong.value?.id == song.id) _currentLyrics.value = selected
            }
            _isLoadingLyrics.value = false
        }
    }

    fun loadDeviceLyricsCandidates() {
        val song = _currentSong.value?.takeUnless { it.id.startsWith("rhythm-catalog:") } ?: return
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _deviceLyricsCandidates.value = emptyList()
            deviceLyricsCandidatesSongId = null
            val candidates = repository.searchDeviceLyricsCandidates(song)
            if (_currentSong.value?.id == song.id) {
                _deviceLyricsCandidates.value = candidates
                deviceLyricsCandidatesSongId = song.id
            }
            _isLoadingLyrics.value = false
        }
    }

    fun selectDeviceLyricsCandidate(candidate: DeviceLyricsCandidate) {
        val song = _currentSong.value?.takeUnless { it.id.startsWith("rhythm-catalog:") } ?: return
        if (deviceLyricsCandidatesSongId != song.id || candidate !in _deviceLyricsCandidates.value) return
        viewModelScope.launch {
            val selected = repository.applyDeviceLyricsCandidate(song, candidate)
            if (_currentSong.value?.id != song.id) return@launch
            _currentLyrics.value = selected
            deviceLyricsCandidateIndex = _deviceLyricsCandidates.value.indexOf(candidate)
        }
    }

    fun restoreCurrentDeviceLyrics() {
        val song = _currentSong.value?.takeUnless { it.id.startsWith("rhythm-catalog:") } ?: return
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _deviceLyricsCandidates.value = emptyList()
            deviceLyricsCandidatesSongId = null
            deviceLyricsCandidateIndex = -1
            val restored = repository.restoreDeviceLocalLyrics(song)
            val restoredArtwork = repository.restoreDeviceLocalArtwork(song)
            if (_currentSong.value?.id == song.id) {
                _currentLyrics.value = restored
                val restoredSongs = _songs.value.map {
                    if (it.id == song.id) song.copy(artworkUri = restoredArtwork) else it
                }
                val projected = repository.projectDeviceArtwork(restoredSongs)
                _songs.value = projected
                _currentSong.value = projected.firstOrNull { it.id == song.id } ?: song.copy(artworkUri = restoredArtwork)
                repository.updateAndPersistSongs(projected)
                _albums.value = repository.loadAlbums()
            }
            _isLoadingLyrics.value = false
        }
    }

    /**
     * Embed lyrics into the current song's audio file metadata
     */
    fun embedLyricsInFile(
        lyrics: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onPermissionRequired: ((PendingLyricsWriteRequest) -> Unit)? = null
    ) {
        metadataManagerHelper.embedLyricsInFile(
            lyrics = lyrics,
            onSuccess = onSuccess,
            onError = onError,
            onPermissionRequired = onPermissionRequired
        )
    }

    /**
     * Complete lyrics embedding after user grants permission via createWriteRequest
     */
    fun completeLyricsWriteAfterPermission(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        metadataManagerHelper.completeLyricsWriteAfterPermission(onSuccess, onError)
    }

    /**
     * Cancel pending lyrics write request
     */
    fun cancelPendingLyricsWrite() {
        metadataManagerHelper.cancelPendingLyricsWrite()
    }

    /**
     * Save edited lyrics for the current song to cache
     */
    fun saveEditedLyrics(editedLyrics: String, timeOffset: Int = 0, format: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val song = _currentSong.value
                if (song != null) {
                    val sanitizedLyrics = editedLyrics
                        .replace("\uFEFF", "")
                        .replace("\r\n", "\n")
                        .replace("\r", "\n")
                        .take(MAX_EDITABLE_LYRICS_CHARS)

                    if (sanitizedLyrics.length < editedLyrics.length) {
                        Log.w(TAG, "Edited lyrics were truncated to $MAX_EDITABLE_LYRICS_CHARS characters")
                    }

                    val catalogNowPlaying = _catalogNowPlaying.value
                    if (song.isCatalogLibrarySong() && catalogNowPlaying != null) {
                        val connection = catalogRepository.connection()
                        val language = normalizeCatalogLyricsLanguageTag(
                            _catalogLyricsLanguage.value ?: catalogNowPlaying.lyricsLanguage ?: "und",
                        )
                        val lyricsWithHistory = LyricsContributionAttribution.preserveHistory(
                            lyrics = sanitizedLyrics,
                            existing = _currentLyrics.value?.contributions.orEmpty(),
                            format = format.orEmpty(),
                        )
                        val backendFormat = backendLyricFormat(lyricsWithHistory, format)
                        val existingDraft = catalogLyricsDraftStore.load(
                            connection.draftNamespace,
                            catalogNowPlaying.renditionId,
                            language,
                        )
                        val draft = CatalogLyricsDraft(
                            namespace = connection.draftNamespace,
                            renditionId = catalogNowPlaying.renditionId,
                            language = language,
                            lyrics = lyricsWithHistory,
                            format = backendFormat,
                            baseRevision = existingDraft
                                ?.takeIf { it.status != "synced" }
                                ?.baseRevision
                                ?: catalogNowPlaying.renditionRevision,
                            status = "pending",
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                        catalogLyricsDraftStore.save(draft)
                        val updated = catalogNowPlaying.withLyricsVariant(
                            language,
                            lyricsWithHistory,
                            backendFormat,
                        )
                        _catalogNowPlaying.value = updated
                        _catalogQueue.value = _catalogQueue.value.map { entry ->
                            if (entry.nowPlaying.renditionId == updated.renditionId) {
                                entry.copy(nowPlaying = updated.copy(assetId = entry.nowPlaying.assetId))
                            } else {
                                entry
                            }
                        }
                        _lyricsTimeOffset.value = timeOffset
                        _catalogLyricsSyncState.value =
                            CatalogLyricsSyncState(CatalogLyricsSyncStatus.PENDING)
                        applyCatalogLyrics(updated, language)
                        saveQueueToPersistence()
                        return@launch
                    }

                    val artist = song.artist
                    val title = song.title
                    
                    // Store the time offset
                    _lyricsTimeOffset.value = timeOffset
                    
                    // Determine what format to treat the lyrics as
                    val isTtml = RhythmLyricsParser.isTtmlContent(sanitizedLyrics)
                    val isWordByWord = format == "WORD_BY_WORD" || 
                        (sanitizedLyrics.trim().startsWith("[") && 
                         (sanitizedLyrics.contains("\"timestamp\"") || sanitizedLyrics.contains("\"words\"")))
                    
                    val hasLineTimestamp =
                        io.github.cluno1.sonorus.util.LrcTimingEditor.hasLineTimestamp(sanitizedLyrics)
                    val isSynced = !isWordByWord && !isTtml && hasLineTimestamp
                    
                    // Load existing cache if exists to preserve other formats
                    val fileName = DevicePrivateMetadataFileNames.lyrics(song.id, artist, title)
                    val lyricsDir = File(getApplication<Application>().filesDir, "lyrics")
                    if (!lyricsDir.exists()) {
                        lyricsDir.mkdirs()
                    }
                    val file = File(lyricsDir, fileName)
                    
                    val existingLyricsData = if (file.exists()) {
                        try {
                            val cachedJson = file.readText()
                            Gson().fromJson(cachedJson, LyricsData::class.java)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    
                    // Normalize LRC if synced
                    val normalizedLyrics = if (isSynced) {
                        normalizePlainLRC(sanitizedLyrics)
                    } else {
                        sanitizedLyrics
                    }
                    
                    val lyricsData = if (isTtml) {
                        val parsed = try {
                            RhythmLyricsParser.parseTtmlLyrics(sanitizedLyrics)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (parsed.isNotEmpty()) {
                            val wordByWordJson = Gson().toJson(parsed)
                            val parsedWordByWordLines = RhythmLyricsParser.parseWordByWordLyrics(wordByWordJson)
                            val lrc = RhythmLyricsParser.toLRCFormat(parsedWordByWordLines)
                            val plain = RhythmLyricsParser.toPlainText(parsedWordByWordLines)
                            val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                            LyricsData(
                                plainLyrics = plain,
                                syncedLyrics = lrc,
                                wordByWordLyrics = if (hasWordTiming) wordByWordJson else null,
                                source = "Local File",
                                isCorrected = true
                            )
                        } else {
                            LyricsData(
                                plainLyrics = sanitizedLyrics,
                                syncedLyrics = existingLyricsData?.syncedLyrics,
                                wordByWordLyrics = existingLyricsData?.wordByWordLyrics
                            )
                        }
                    } else if (isWordByWord) {
                        // Parse JSON to generate synced and plain versions
                        val parsed = try {
                            RhythmLyricsParser.parseWordByWordLyrics(sanitizedLyrics)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        val generatedLrc = if (parsed.isNotEmpty()) {
                            try {
                                RhythmLyricsParser.toLRCFormat(parsed)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                        
                        val generatedPlain = if (parsed.isNotEmpty()) {
                            try {
                                RhythmLyricsParser.toPlainText(parsed)
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                        
                        LyricsData(
                            plainLyrics = generatedPlain ?: existingLyricsData?.plainLyrics,
                            syncedLyrics = generatedLrc ?: existingLyricsData?.syncedLyrics,
                            wordByWordLyrics = sanitizedLyrics
                        )
                    } else if (isSynced) {
                        val hasWordTimestamps = io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(sanitizedLyrics)
                        if (hasWordTimestamps) {
                            val parsedWordByWordLines = try {
                                RhythmLyricsParser.parseEnhancedLRCtoWordByWord(sanitizedLyrics)
                            } catch (e: Exception) {
                                emptyList()
                            }
                            if (parsedWordByWordLines.isNotEmpty()) {
                                val wordByWordJson = Gson().toJson(parsedWordByWordLines)
                                val lrc = RhythmLyricsParser.toLRCFormat(parsedWordByWordLines)
                                val plain = RhythmLyricsParser.toPlainText(parsedWordByWordLines)
                                val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                                LyricsData(
                                    plainLyrics = plain,
                                    syncedLyrics = lrc,
                                    wordByWordLyrics = if (hasWordTiming) wordByWordJson else null,
                                    source = "Local File",
                                    isCorrected = true
                                )
                            } else {
                                val generatedPlain = normalizedLyrics.lines().joinToString("\n") { line ->
                                    line.replace(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}]"), "").trim()
                                }
                                LyricsData(
                                    plainLyrics = generatedPlain,
                                    syncedLyrics = normalizedLyrics,
                                    wordByWordLyrics = existingLyricsData?.wordByWordLyrics
                                )
                            }
                        } else {
                            // Generate plain text by removing LRC timestamps
                            val generatedPlain = normalizedLyrics.lines().joinToString("\n") { line ->
                                line.replace(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}]"), "").trim()
                            }
                            
                            LyricsData(
                                plainLyrics = generatedPlain,
                                syncedLyrics = normalizedLyrics,
                                wordByWordLyrics = existingLyricsData?.wordByWordLyrics
                            )
                        }
                    } else {
                        LyricsData(
                            plainLyrics = sanitizedLyrics,
                            syncedLyrics = if (format == "LINE_BY_LINE") {
                                null
                            } else {
                                existingLyricsData?.syncedLyrics
                            },
                            wordByWordLyrics = existingLyricsData?.wordByWordLyrics
                        )
                    }
                    
                    // Save to cache (internal storage)
                    val json = Gson().toJson(lyricsData)
                    file.writeText(json)
                    
                    // Update in-memory state
                    _currentLyrics.value = lyricsData
                    
                    Log.d(TAG, "Saved edited lyrics for: $title by $artist (format: $format, isSynced: $isSynced, isWordByWord: $isWordByWord)")
                } else {
                    Log.w(TAG, "Cannot save lyrics - no current song")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving edited lyrics", e)
            }
        }
    }

    fun syncCatalogEditedLyrics(
        editedLyrics: String,
        format: String,
        forceOverwrite: Boolean = false,
    ) {
        val nowPlaying = _catalogNowPlaying.value ?: return
        val language = normalizeCatalogLyricsLanguageTag(
            _catalogLyricsLanguage.value ?: nowPlaying.lyricsLanguage ?: "und",
        )
        val sanitizedLyrics = editedLyrics
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .take(MAX_EDITABLE_LYRICS_CHARS)
        val lyricsWithHistory = LyricsContributionAttribution.preserveHistory(
            lyrics = sanitizedLyrics,
            existing = _currentLyrics.value?.contributions.orEmpty(),
            format = format,
        )
        val backendFormat = backendLyricFormat(lyricsWithHistory, format)
        val connection = catalogRepository.connection()
        if (connection.draftNamespace.isBlank()) return

        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                catalogLyricsDraftStore.load(
                    connection.draftNamespace,
                    nowPlaying.renditionId,
                    language,
                )
            }
            val expectedRevision = if (forceOverwrite) {
                _catalogLyricsSyncState.value.currentRevision
                    ?: existing?.baseRevision
                    ?: nowPlaying.renditionRevision
            } else {
                existing?.baseRevision ?: nowPlaying.renditionRevision
            }
            val syncingDraft = CatalogLyricsDraft(
                namespace = connection.draftNamespace,
                renditionId = nowPlaying.renditionId,
                language = language,
                lyrics = lyricsWithHistory,
                format = backendFormat,
                baseRevision = expectedRevision,
                status = "pending",
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) { catalogLyricsDraftStore.save(syncingDraft) }
            _catalogLyricsSyncState.value = CatalogLyricsSyncState(CatalogLyricsSyncStatus.SYNCING)
            catalogRepository.replaceRenditionLyrics(
                renditionId = nowPlaying.renditionId,
                language = language,
                lyrics = lyricsWithHistory,
                format = backendFormat,
                expectedRevision = expectedRevision,
                idempotencyKey = java.util.UUID.randomUUID().toString(),
            ).fold(
                onSuccess = { result ->
                    val current = _catalogNowPlaying.value
                        ?.takeIf { it.renditionId == result.renditionId }
                        ?: nowPlaying
                    val updated = current.withLyricsVariant(
                        result.language,
                        result.lyrics,
                        result.format,
                        result.renditionRevision,
                    )
                    _catalogNowPlaying.value = updated
                    _catalogQueue.value = _catalogQueue.value.map { entry ->
                        if (entry.nowPlaying.renditionId == updated.renditionId) {
                            entry.copy(nowPlaying = updated.copy(assetId = entry.nowPlaying.assetId))
                        } else {
                            entry
                        }
                    }
                    withContext(Dispatchers.IO) {
                        catalogLyricsDraftStore.remove(
                            connection.draftNamespace,
                            result.renditionId,
                            result.language,
                        )
                    }
                    applyCatalogLyrics(updated, result.language)
                    _catalogLyricsSyncState.value =
                        CatalogLyricsSyncState(CatalogLyricsSyncStatus.SYNCED)
                    saveQueueToPersistence()
                },
                onFailure = { error ->
                    val status = if (error is CatalogFailure.StaleRevision) {
                        CatalogLyricsSyncStatus.CONFLICT
                    } else {
                        CatalogLyricsSyncStatus.ERROR
                    }
                    withContext(Dispatchers.IO) {
                        catalogLyricsDraftStore.save(
                            syncingDraft.copy(
                                status = if (status == CatalogLyricsSyncStatus.CONFLICT) {
                                    "conflict"
                                } else {
                                    "error"
                                },
                                updatedAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    }
                    _catalogLyricsSyncState.value = CatalogLyricsSyncState(
                        status = status,
                        currentRevision = (error as? CatalogFailure.StaleRevision)?.currentRevision,
                    )
                },
            )
        }
    }

    fun loadServerCatalogLyrics() {
        val current = _catalogNowPlaying.value ?: return
        val language = _catalogLyricsLanguage.value ?: current.lyricsLanguage ?: "und"
        val connection = catalogRepository.connection()
        if (connection.draftNamespace.isBlank()) return
        viewModelScope.launch {
            _catalogLyricsSyncState.value = CatalogLyricsSyncState(CatalogLyricsSyncStatus.SYNCING)
            catalogRepository.getLibrary(forceRefresh = true).fold(
                onSuccess = { snapshot ->
                    val server = snapshot.songs.firstOrNull { it.renditionId == current.renditionId }
                    if (server == null) {
                        _catalogLyricsSyncState.value = CatalogLyricsSyncState(
                            CatalogLyricsSyncStatus.ERROR,
                        )
                        return@fold
                    }
                    withContext(Dispatchers.IO) {
                        catalogLyricsDraftStore.remove(
                            connection.draftNamespace,
                            current.renditionId,
                            language,
                        )
                    }
                    val updated = current.copy(
                        renditionRevision = server.renditionRevision,
                        lyrics = server.lyrics,
                        lyricsLanguage = server.lyricsLanguage,
                        lyricsTranslations = server.lyricsTranslations,
                        lyricsFormats = server.lyricsFormats,
                    )
                    _catalogNowPlaying.value = updated
                    _catalogQueue.value = _catalogQueue.value.map { entry ->
                        if (entry.nowPlaying.renditionId == updated.renditionId) {
                            entry.copy(nowPlaying = updated.copy(assetId = entry.nowPlaying.assetId))
                        } else {
                            entry
                        }
                    }
                    applyCatalogLyrics(updated, language)
                    saveQueueToPersistence()
                },
                onFailure = { _ ->
                    _catalogLyricsSyncState.value = CatalogLyricsSyncState(
                        CatalogLyricsSyncStatus.ERROR,
                    )
                },
            )
        }
    }

    /**
     * Normalize plain LRC text by fixing fragmented words (e.g., "some thing" -> "something")
     * This handles LRC files where words have been split with spaces.
     */
    private fun normalizePlainLRC(lrcContent: String): String {
        val lrcRegex = Regex("""^\[(\d{1,2}):(\d{2}(?:\.\d{2,3})?)\](.*)$""", RegexOption.MULTILINE)
        return lrcContent.lines().map { line ->
            val matchResult = lrcRegex.matchEntire(line.trim())
            if (matchResult != null) {
                val timestamp = matchResult.groupValues[0].substring(0, matchResult.groupValues[0].indexOf(']') + 1)
                val text = matchResult.groupValues[3]
                val normalizedText = io.github.cluno1.sonorus.util.LyricsParser.normalizeWordFlowText(text)
                "$timestamp$normalizedText"
            } else {
                line
            }
        }.joinToString("\n")
    }

    fun setVolume(newVolume: Float) {
        val clampedVolume = newVolume.coerceIn(0f, 1f)
        Log.d(TAG, "Setting volume to: $clampedVolume")
        mediaController?.let { controller ->
            controller.volume = clampedVolume
            _volume.value = clampedVolume
            if (clampedVolume > 0f) {
                _isMuted.value = false
            }
            if (!appSettings.useSystemVolume.value && clampedVolume > 0f) {
                appSettings.setAppVolume(clampedVolume)
            }
            // Stop playback if volume reaches 0 and setting is enabled
            // Gate on !useSystemVolume: when system volume mode is ON, the system-volume
            // ContentObserver in MaterialPlayerScreen handles pausing and the dialog trigger.
            if (clampedVolume == 0f && !_isMuted.value
                && appSettings.stopPlaybackOnZeroVolume.value
                && !appSettings.useSystemVolume.value
            ) {
                pauseMusic()
                pausedByZeroVolume = true
                _showZeroVolumePauseDialog.value = true
            }
        }
        // Auto-resume when volume increases after a zero-volume pause
        if (clampedVolume > 0f && pausedByZeroVolume && !_isPlaying.value) {
            pausedByZeroVolume = false
            _showZeroVolumePauseDialog.value = false
            resumeMusic()
        }
        // Extend sleep timer when user changes volume during fade-out
        if (_sleepTimerActive.value && clampedVolume > 0f) {
            val context = getApplication<Application>()
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                this.action = MediaPlaybackService.ACTION_EXTEND_SLEEP_TIMER
            }
            ServiceStartUtils.startServiceSafely(
                context = context,
                intent = intent,
                logTag = TAG,
                reason = "extend_sleep_timer_on_volume_change"
            )
        }
    }

    /**
     * Switches the volume control mode.
     * - When enabling system volume mode: pins ExoPlayer volume to 1.0f (silent burst prevention)
     *   and saves current app volume so it can be restored later.
     * - When disabling system volume mode: restores the previously saved app volume.
     */
    fun setUseSystemVolumeMode(enable: Boolean) {
        val prevAppVolume = if (_volume.value > 0f) _volume.value else appSettings.appVolume.value
        appSettings.setUseSystemVolume(enable)
        if (enable) {
            // Save current app volume before pinning to 1.0f
            if (prevAppVolume > 0f) {
                appSettings.setAppVolume(prevAppVolume)
            }
            // Pin app (ExoPlayer) volume to full so system volume is sole audio knob.
            // Use the raw controller call to bypass the zero-volume dialog guard.
            mediaController?.volume = 1f
            _volume.value = 1f
            _isMuted.value = false
            Log.d(TAG, "System volume mode ON — ExoPlayer volume pinned to 1.0f")
        } else {
            // Restore the previously saved app volume
            val restore = appSettings.appVolume.value.coerceIn(0.01f, 1f)
            mediaController?.volume = restore
            _volume.value = restore
            Log.d(TAG, "System volume mode OFF — ExoPlayer volume restored to $restore")
        }
    }
    
    fun toggleMute() {
        Log.d(TAG, "Toggling mute")
        if (_isMuted.value) {
            // Unmute - restore previous volume
            _isMuted.value = false
            setVolume(_previousVolume)
        } else {
            // Mute - save current volume and set to 0
            // Set _isMuted BEFORE calling setVolume so the zero-volume dialog guard
            // correctly suppresses the dialog during intentional muting.
            _previousVolume = _volume.value
            _isMuted.value = true
            setVolume(0f)
        }
    }
    
    fun maxVolume() {
        Log.d(TAG, "Setting max volume")
        setVolume(1.0f)
    }

    /**
     * Opens the system equalizer for the current audio session
     */
    fun openSystemEqualizer(activity: Activity? = null, requestCode: Int = 0) {
        val context = getApplication<Application>()
        // Simply use EqualizerUtils which handles the system equalizer opening properly
        EqualizerUtils.openSystemEqualizer(context, audioSessionId, activity, requestCode)
    }

    // Playback settings functions
    
    fun setGaplessPlayback(enable: Boolean) {
        appSettings.setGaplessPlayback(enable)
        applyPlaybackSettings()
    }
    
    fun setSkipSilenceEnabled(enable: Boolean) {
        appSettings.setSkipSilenceEnabled(enable)
        applyPlaybackSettings()
    }
    
    fun setCrossfade(enable: Boolean) {
        appSettings.setCrossfade(enable)
        applyPlaybackSettings()
    }
    
    fun setCrossfadeDuration(duration: Float) {
        appSettings.setCrossfadeDuration(duration)
        applyPlaybackSettings()
    }

    fun setCrossfadeOnSkip(enable: Boolean) {
        appSettings.setCrossfadeOnSkip(enable)
        applyPlaybackSettings()
    }
    
    fun setAudioNormalization(enable: Boolean) {
        appSettings.setAudioNormalization(enable)
        applyPlaybackSettings()
    }
    
    fun setReplayGain(enable: Boolean) {
        appSettings.setReplayGain(enable)
        applyPlaybackSettings()
    }
    
    private fun applyPlaybackSettings() {
        // Apply settings to the media player
        mediaController?.let { controller ->
            Log.d(TAG, "Applied playback settings: " +
                    "Gapless=${enableGaplessPlayback.value}, " +
                    "Crossfade=${enableCrossfade.value} (${crossfadeDuration.value}s), " +
                    "Normalization=${enableAudioNormalization.value}, " +
                    "ReplayGain=${enableReplayGain.value}")
            
            // Send intent to update service settings.
            val context = getApplication<Application>()
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_UPDATE_SETTINGS
            }
            ServiceStartUtils.startServiceSafely(
                context = context,
                intent = intent,
                logTag = TAG,
                reason = "update_playback_settings"
            )
        }
    }

    /**
     * Add a song to the queue
     */
    fun addSongToQueue(song: Song) {
        if (song.id.startsWith("rhythm-catalog:")) {
            Log.w(TAG, "Catalog songs must enter through playCatalogQueue")
            return
        }
        Log.d(TAG, "Adding song to queue: ${song.title}")
        
        // Clear any previous error
        _queueOperationError.value = null
        
        mediaController?.let { controller ->
            try {
                val mediaItem = song.toMediaItem()
                
                // Add to media controller queue
                controller.addMediaItem(mediaItem)
                
                // If nothing is currently playing, start playback
                if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                    controller.prepare()
                    if (!canStartPlayback("addSongToQueue")) return@let
                    controller.play()
                }
                
                // Update the queue in our state
                if (controller.shuffleModeEnabled) {
                    // When shuffle is enabled, sync with MediaController to get the correct order
                    viewModelScope.launch {
                        delay(50) // Small delay to let MediaController update
                        syncQueueWithMediaController()
                    }
                } else {
                    // When shuffle is disabled, we can safely update the queue manually
                    val currentQueueSongs = _currentQueue.value.songs.toMutableList()
                    currentQueueSongs.add(song)
                    
                    // Make sure current index is valid and matches MediaController
                    val currentIndex = if (_currentQueue.value.currentIndex == -1 && currentQueueSongs.size == 1) {
                        // First song added to empty queue
                        0
                    } else if (controller.currentMediaItemIndex >= 0 && controller.currentMediaItemIndex < currentQueueSongs.size) {
                        // Use MediaController's current index for accuracy
                        controller.currentMediaItemIndex
                    } else {
                        _currentQueue.value.currentIndex
                    }
                    
                    _currentQueue.value = Queue(currentQueueSongs, currentIndex)
                }
                
                // Save queue to persistence
                saveQueueToPersistence()
                
                val toastContext = getApplication<android.app.Application>().applicationContext
                android.widget.Toast.makeText(toastContext, toastContext.getString(R.string.added_to_queue_simple, song.title), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding song to queue", e)
                val errorMsg = "Failed to add '${song.title}' to queue: ${e.message}"
                _queueOperationError.value = errorMsg
                android.widget.Toast.makeText(getApplication<android.app.Application>().applicationContext, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            val errorMsg = "Cannot add song to queue - media controller is null"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
            android.widget.Toast.makeText(getApplication<android.app.Application>().applicationContext, R.string.musicviewmodel_failed_to_add_to, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Add a song to play next (right after the current song in the queue)
     */
    fun playNext(song: Song) {
        if (song.id.startsWith("rhythm-catalog:")) {
            Log.w(TAG, "Catalog songs must enter through playCatalogQueue")
            return
        }
        Log.d(TAG, "Adding song to play next: ${song.title}")
        
        // Clear any previous error
        _queueOperationError.value = null
        
        mediaController?.let { controller ->
            try {
                val mediaItem = song.toMediaItem()
                
                // Calculate the position to insert (right after current song)
                val currentIndex = controller.currentMediaItemIndex
                val insertIndex = if (currentIndex >= 0) currentIndex + 1 else 0
                
                // Add to media controller queue at specific position
                controller.addMediaItem(insertIndex, mediaItem)
                
                // If nothing is currently playing, start playback
                if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                    controller.prepare()
                    if (!canStartPlayback("playNext")) return@let
                    controller.play()
                }
                
                // Update the queue in our state
                if (controller.shuffleModeEnabled) {
                    // When shuffle is enabled, sync with MediaController to get the correct shuffled order
                    viewModelScope.launch {
                        delay(50) // Small delay to let MediaController update
                        syncQueueWithMediaController()
                    }
                } else {
                    // When shuffle is disabled, we can safely update the queue manually
                    val currentQueueSongs = _currentQueue.value.songs.toMutableList()
                    val currentQueueIndex = controller.currentMediaItemIndex.coerceAtLeast(0) // Use MediaController index
                    val queueInsertIndex = if (currentQueueIndex >= 0 && currentQueueIndex < currentQueueSongs.size) {
                        currentQueueIndex + 1
                    } else {
                        0
                    }
                    currentQueueSongs.add(queueInsertIndex, song)
                    
                    // Keep current index pointing to the currently playing song
                    _currentQueue.value = Queue(currentQueueSongs, currentQueueIndex)
                    
                    Log.d(TAG, "Successfully added '${song.title}' to play next at position $queueInsertIndex. Queue now has ${currentQueueSongs.size} songs, current index: $currentQueueIndex")
                }
                
                // Verify queue sync
                if (controller.mediaItemCount != _currentQueue.value.songs.size) {
                    Log.w(TAG, "Queue size mismatch after playNext - MediaController: ${controller.mediaItemCount}, ViewModel: ${_currentQueue.value.songs.size}")
                }
                // Save queue to persistence
                saveQueueToPersistence()

                val toastContext = getApplication<android.app.Application>().applicationContext
                android.widget.Toast.makeText(toastContext, toastContext.getString(R.string.playing_next_simple, song.title), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding song to play next", e)
                val errorMsg = "Failed to add '${song.title}' to play next: ${e.message}"
                _queueOperationError.value = errorMsg
                android.widget.Toast.makeText(getApplication<android.app.Application>().applicationContext, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            val errorMsg = "Cannot add song to play next - media controller is null"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
            android.widget.Toast.makeText(getApplication<android.app.Application>().applicationContext, R.string.musicviewmodel_failed_to_play_next, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Remove a song from the queue
     */
    fun removeFromQueue(song: Song) {
        val currentQueue = _currentQueue.value
        val songIndex = currentQueue.songs.indexOfFirst { it.id == song.id }

        if (songIndex == -1) {
            Log.d(TAG, "Song '${song.title}' not found in queue")
            _queueOperationError.value = "Song '${song.title}' not found in queue"
            return
        }

        removeFromQueueAtIndex(songIndex)
    }

    fun removeFromQueueAtIndex(songIndex: Int) {
        // Clear any previous error
        _queueOperationError.value = null

        val currentQueue = _currentQueue.value

        if (songIndex < 0 || songIndex >= currentQueue.songs.size) {
            val errorMsg = "Cannot remove song - invalid queue index: $songIndex"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
            return
        }

        val song = currentQueue.songs[songIndex]
        Log.d(TAG, "Removing song from queue at index $songIndex: ${song.title}")

        mediaController?.let { controller ->
            try {
                val controllerCurrentIndex = controller.currentMediaItemIndex
                val currentIndexForGuard = if (
                    controllerCurrentIndex != C.INDEX_UNSET &&
                    controllerCurrentIndex < currentQueue.songs.size
                ) {
                    controllerCurrentIndex
                } else {
                    currentQueue.currentIndex
                }

                // Don't remove the currently playing song occurrence.
                if (songIndex == currentIndexForGuard) {
                    Log.d(TAG, "Cannot remove currently playing song at index: $songIndex")
                    _queueOperationError.value = "Cannot remove the currently playing song"
                    return@let
                }

                val controllerIndex = if (controller.shuffleModeEnabled) {
                    val occurrence = resolveQueueOccurrenceInList(
                        queueSongs = currentQueue.songs,
                        songId = song.id,
                        queueIndex = songIndex
                    )
                    findControllerIndexForSong(song.id, occurrence) ?: songIndex
                } else {
                    songIndex
                }

                // Check if the controller has this media item
                if (controllerIndex < controller.mediaItemCount) {
                    // Remove from the media controller
                    Log.d(TAG, "Removing media item at position $controllerIndex (original songIndex=$songIndex)")
                    controller.removeMediaItem(controllerIndex)
                } else {
                    throw IndexOutOfBoundsException("Media item index out of bounds: $controllerIndex, controller has ${controller.mediaItemCount} items")
                }
                
                // Update the local queue state - this ensures UI is still updated even if controller fails
                val updatedSongs = currentQueue.songs.toMutableList().apply {
                    removeAt(songIndex)
                }
                
                // Adjust current index if needed
                val newIndex = when {
                    songIndex < currentQueue.currentIndex -> currentQueue.currentIndex - 1
                    songIndex == currentQueue.currentIndex -> currentQueue.currentIndex // Should not happen (can't remove current)
                    else -> currentQueue.currentIndex
                }.coerceIn(0, updatedSongs.size - 1)
                
                _currentQueue.value = Queue(updatedSongs, newIndex)
                
                // Save queue to persistence
                saveQueueToPersistence()
                
                Log.d(TAG, "Successfully removed '${song.title}'. Queue now has ${updatedSongs.size} songs, current index: $newIndex")
                
                // Verify sync with MediaController
                if (controller.mediaItemCount != updatedSongs.size) {
                    Log.w(TAG, "Queue size mismatch after remove - MediaController: ${controller.mediaItemCount}, ViewModel: ${updatedSongs.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing song from queue", e)
                _queueOperationError.value = "Failed to remove '${song.title}' from queue: ${e.message}"
            }
        } ?: run {
            val errorMsg = "Cannot remove song - media controller is null"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
        }
    }
    
    /**
     * Move a song in the queue from one position to another
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        Log.d(TAG, "Moving queue item from $fromIndex to $toIndex")
        
        // Clear any previous error
        _queueOperationError.value = null

        if (_isShuffleEnabled.value) {
            val errorMsg = "Cannot move queue item while shuffle is enabled"
            Log.w(TAG, errorMsg)
            _queueOperationError.value = errorMsg
            return
        }
        
        val currentQueue = _currentQueue.value
        val songs = currentQueue.songs.toMutableList()
        
        // Input validation to prevent index out of bounds
        if (fromIndex < 0 || fromIndex >= songs.size || toIndex < 0 || toIndex >= songs.size) {
            val errorMsg = "Cannot move queue item - invalid indices: from=$fromIndex, to=$toIndex, size=${songs.size}"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
            return
        }
        
        // Additional validation for edge cases
        if (fromIndex == toIndex) {
            Log.d(TAG, "Moving queue item to same position, ignoring")
            return
        }
        
        // Safely update local state first
        try {
            // Store original state for rollback
            val originalSongs = songs.toList()
            val originalCurrentIndex = currentQueue.currentIndex
            
            // Apply the move in the local queue
            val song = songs.removeAt(fromIndex)
            songs.add(toIndex, song)
            
            // Calculate new current index with better logic
            val newCurrentIndex = when {
                // If we moved the current song
                fromIndex == currentQueue.currentIndex -> toIndex
                // If we moved a song from before the current song to after it
                fromIndex < currentQueue.currentIndex && toIndex >= currentQueue.currentIndex -> 
                    currentQueue.currentIndex - 1
                // If we moved a song from after the current song to before it
                fromIndex > currentQueue.currentIndex && toIndex <= currentQueue.currentIndex -> 
                    currentQueue.currentIndex + 1
                // Otherwise, current index doesn't change
                else -> currentQueue.currentIndex
            }.coerceIn(0, songs.size - 1) // Ensure index is always valid
            
            // Update local state optimistically
            _currentQueue.value = Queue(songs, newCurrentIndex)
            
            // Save queue to persistence
            saveQueueToPersistence()
            
            // Also move it in the MediaController with proper error handling
            mediaController?.let { controller ->
                try {
                    // Validate controller state
                    if (controller.mediaItemCount != originalSongs.size) {
                        Log.w(TAG, "MediaController item count mismatch: expected ${originalSongs.size}, got ${controller.mediaItemCount}")
                    }
                    
                    if (fromIndex < controller.mediaItemCount && toIndex < controller.mediaItemCount) {
                        Log.d(TAG, "Moving media item in controller from $fromIndex to $toIndex")
                        controller.moveMediaItem(fromIndex, toIndex)
                        
                        // If shuffle is enabled, sync the queue to get the correct order
                        if (controller.shuffleModeEnabled) {
                            viewModelScope.launch {
                                delay(50) // Small delay to let MediaController update
                                syncQueueWithMediaController()
                            }
                        }
                        
                        Log.d(TAG, "Successfully moved queue item from $fromIndex to $toIndex, new current index: $newCurrentIndex")
                    } else {
                        throw IndexOutOfBoundsException("Cannot move media item - index out of bounds in controller: from=$fromIndex, to=$toIndex, count=${controller.mediaItemCount}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error moving media item in controller, rolling back UI state", e)
                    // Rollback to original state
                    _currentQueue.value = Queue(originalSongs, originalCurrentIndex)
                    _queueOperationError.value = "Failed to move queue item: ${e.message}"
                    return
                }
            } ?: run {
                Log.e(TAG, "Cannot move queue item - media controller is null, rolling back UI state")
                // Rollback to original state
                _currentQueue.value = Queue(originalSongs, originalCurrentIndex)
                _queueOperationError.value = "Cannot move queue item - media controller is null"
                return
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error moving queue item", e)
            _queueOperationError.value = "Failed to move queue item: ${e.message}"
        }
    }
    
    /**
     * Add multiple songs to the queue
     */
    fun addSongsToQueue(songs: List<Song>) {
        viewModelScope.launch {
            Log.d(TAG, "Adding ${songs.size} songs to queue")
            
            if (songs.isEmpty()) {
                Log.d(TAG, "No songs to add to queue")
                return@launch
            }
            
            // Clear any previous error
            _queueOperationError.value = null
            
            mediaController?.let { controller ->
                try {
                    // Build media items in background to avoid UI freeze
                    val mediaItems = withContext(Dispatchers.Default) {
                        songs.map { it.toMediaItem() }
                    }
                    
                    // Add items in batches to prevent ANR
                    val batchSize = 50
                    mediaItems.chunked(batchSize).forEach { batch ->
                        controller.addMediaItems(batch)
                        // Small delay between batches to keep UI responsive
                        if (mediaItems.size > batchSize) {
                            kotlinx.coroutines.delay(10)
                        }
                    }
                    
                    // If nothing is currently playing, start playback
                    if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                        controller.prepare()
                        if (!canStartPlayback("addSongsToQueue")) return@launch
                        controller.play()
                    }
                    
                    // Update the queue in our state
                    if (controller.shuffleModeEnabled) {
                        // When shuffle is enabled, sync with MediaController to get the correct shuffled order
                        viewModelScope.launch {
                            delay(100) // Small delay to let MediaController update
                            syncQueueWithMediaController()
                        }
                    } else {
                        // When shuffle is disabled, we can safely update the queue manually
                        val currentQueueSongs = _currentQueue.value.songs.toMutableList()
                        currentQueueSongs.addAll(songs)
                        
                        // Use MediaController index for accuracy
                        val currentIndex = if (controller.currentMediaItemIndex >= 0) {
                            controller.currentMediaItemIndex
                        } else {
                            _currentQueue.value.currentIndex
                        }
                        
                        _currentQueue.value = Queue(currentQueueSongs, currentIndex)
                        
                        // Save queue to persistence
                        saveQueueToPersistence()
                        
                        Log.d(TAG, "Successfully added ${songs.size} songs. Queue now has ${currentQueueSongs.size} songs, current index: $currentIndex")
                    }
                    
                    // Verify sync
                    kotlinx.coroutines.delay(200)
                    if (controller.mediaItemCount != _currentQueue.value.songs.size) {
                        Log.w(TAG, "Queue size mismatch after addSongsToQueue - MediaController: ${controller.mediaItemCount}, ViewModel: ${_currentQueue.value.songs.size}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding songs to queue", e)
                    _queueOperationError.value = "Failed to add ${songs.size} songs to queue: ${e.message}"
                }
            } ?: run {
                val errorMsg = "Cannot add songs to queue - media controller is null"
                Log.e(TAG, errorMsg)
                _queueOperationError.value = errorMsg
            }
        }
    }
    
    /**
     * Clear the entire queue except the currently playing song
     */
    fun clearQueue() {
        Log.d(TAG, "Clearing queue")
        
        // Clear any previous error
        _queueOperationError.value = null
        
        mediaController?.let { controller ->
            try {
                val currentQueue = _currentQueue.value
                
                if (currentQueue.songs.isEmpty()) {
                    Log.d(TAG, "Queue is already empty")
                    return
                }
                
                val controllerCurrentIndex = controller.currentMediaItemIndex
                val fallbackIndexFromSong = _currentSong.value
                    ?.let { current -> currentQueue.songs.indexOfFirst { it.id == current.id } }
                    ?.takeIf { it >= 0 }

                val keepIndex = when {
                    controllerCurrentIndex in currentQueue.songs.indices -> controllerCurrentIndex
                    fallbackIndexFromSong != null -> fallbackIndexFromSong
                    else -> 0
                }
                
                if (currentQueue.songs.size == 1) {
                    Log.d(TAG, "Queue has only one song, nothing to clear")
                    return
                }
                
                // Remove all items except the currently playing one
                // Remove in reverse order to maintain indices
                for (i in (controller.mediaItemCount - 1) downTo 0) {
                    if (i != keepIndex) {
                        controller.removeMediaItem(i)
                    }
                }
                
                // Update the queue in our state to contain only the current song
                val currentSong = if (keepIndex in currentQueue.songs.indices) {
                    currentQueue.songs[keepIndex]
                } else {
                    _currentSong.value
                }
                
                val newQueue = if (currentSong != null) {
                    listOf(currentSong)
                } else {
                    emptyList()
                }
                
                _currentQueue.value = Queue(newQueue, if (newQueue.isNotEmpty()) 0 else -1)
                
                // Save queue to persistence
                saveQueueToPersistence()
                
                Log.d(TAG, "Successfully cleared queue. Kept current song: ${currentSong?.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing queue", e)
                _queueOperationError.value = "Failed to clear queue: ${e.message}"
            }
        } ?: run {
            val errorMsg = "Cannot clear queue - media controller is null"
            Log.e(TAG, errorMsg)
            _queueOperationError.value = errorMsg
        }
    }

    /**
     * Add songs to the queue (legacy method for single song - keeping for backward compatibility)
     */
    fun addSongsToQueue() {
        // This method is now a placeholder for UI navigation
        // The actual song addition should use addSongsToQueue(songs: List<Song>)
    }

    // Search history methods
    private fun loadSearchHistory() {
        val searchHistoryJson = appSettings.searchHistory.value
        if (searchHistoryJson != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val history = GsonUtils.gson.fromJson<List<String>>(searchHistoryJson, type)
                _searchHistory.value = history
            } catch (e: Exception) {
                Log.e(TAG, "Error loading search history", e)
                _searchHistory.value = emptyList()
            }
        }
    }
    
    private fun saveSearchHistory() {
        val searchHistoryJson = GsonUtils.gson.toJson(_searchHistory.value)
        appSettings.setSearchHistory(searchHistoryJson)
    }
    
    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        appSettings.setSearchHistory(null)
    }

    /**
     * Checks if the service is connected and ready
     */
    fun isServiceConnected(): Boolean {
        return _serviceConnected.value && mediaController != null
    }
    
    /**
     * Checks if music is currently playing
     */
    fun isPlaying(): Boolean {
        return _isPlaying.value
    }
    
    /**
     * Plays an external audio file that was opened from outside the app
     */
    fun playExternalAudioFile(song: Song) {
        if (!CatalogPlaybackPolicy.EXTERNAL_URI_PLAYBACK_ENABLED) {
            Log.w(TAG, "Rejected external playback by private catalog policy: ${song.uri}")
            return
        }
        Log.d(TAG, "Playing external audio file: ${song.title}, URI: ${song.uri}")
        
        // Add to recently played list
        updateRecentlyPlayed(song)
        
        // Create a media item from the song
        val mediaItem = song.toMediaItem()
        
        // First check if we have a valid controller
        if (mediaController == null) {
            Log.d(TAG, "Media controller is null, reconnecting to service")
            // Try to reconnect to the service
            connectToMediaService()
            
            // Create a delayed job to retry playback once we have a controller
            viewModelScope.launch {
                var attempts = 0
                val maxAttempts = 15  // Increased from 10 to 15 attempts for cold starts
                val delayMs = 800L    // Increased from 500ms to 800ms between attempts
                
                // Add initial delay to give service more time to fully initialize
                delay(1000)
                
                while (mediaController == null && attempts < maxAttempts) {
                    delay(delayMs)
                    attempts++
                    Log.d(TAG, "Waiting for media controller (attempt $attempts)")
                    
                    // Try reconnecting if we're still not connected after half the attempts
                    if (attempts == maxAttempts / 2) {
                        Log.d(TAG, "Still no controller, trying to reconnect...")
                        connectToMediaService()
                    }
                }
                
                if (mediaController != null) {
                    actuallyPlayExternalFile(mediaController!!, mediaItem, song)
                } else {
                    Log.e(TAG, "Failed to obtain media controller after $maxAttempts attempts")
                    // Update UI state to reflect that we have a song but it's not playing
                    _currentSong.value = song
                    _isPlaying.value = false
                    _currentQueue.value = Queue(listOf(song), 0)
                }
            }
        } else {
            // We have a controller, use it directly
            actuallyPlayExternalFile(mediaController!!, mediaItem, song)
        }
    }
    
    /**
     * Helper method to actually play the external file once we have a valid controller
     */
    private fun actuallyPlayExternalFile(controller: MediaController, mediaItem: MediaItem, song: Song) {
        Log.d(TAG, "Using controller to play: ${song.title}")
        
        try {
            // Clear existing queue to avoid conflicts
            controller.clearMediaItems()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            if (!canStartPlayback("playExternalAudioFile")) {
                _isPlaying.value = false
                _currentSong.value = song
                _currentQueue.value = Queue(listOf(song), 0)
                return
            }
            controller.play()
            
            // Update UI state
            _currentSong.value = song
            _isPlaying.value = true
            
            // Create a new queue with just this song
            _currentQueue.value = Queue(listOf(song), 0)
            
            // Update favorite status
            _isFavorite.value = _favoriteSongs.value.contains(song.id)
            
            // Start progress tracking
            startProgressUpdates()
            
            // Mark the service as connected
            _serviceConnected.value = true
            
            // Double-check if playback actually started with multiple retries
            viewModelScope.launch {
                var retryCount = 0
                val maxRetries = 5
                
                while (retryCount < maxRetries) {
                    delay(500)
                    if (!controller.isPlaying) {
                        Log.d(TAG, "Playback didn't start, retry #${retryCount + 1}")
                        if (!canStartPlayback("playExternalAudioFile.retry")) {
                            break
                        }
                        controller.play()
                        retryCount++
                    } else {
                        // Playback started successfully
                        Log.d(TAG, "Playback confirmed as started")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing external file", e)
            // Mark UI as not playing
            _isPlaying.value = false
            // Still update other UI elements so the user sees the song
            _currentSong.value = song
            _currentQueue.value = Queue(listOf(song), 0)
        }
    }

    /**
     * Shuffles all available songs and plays them as a queue
     */
    /**
     * Plays a list of songs as a queue.
     */
    fun playSongs(songs: List<Song>) {
        Log.d(TAG, "Playing list of songs (force replace): ${songs.size} songs")
        // Force replace current queue for explicit list "Play All" actions
        playQueue(songs, enableShuffle = false, startIndex = 0)
    }

    /**
     * Shuffles a list of songs and plays them as a queue.
     * Respects the shuffleUsesExoplayer setting.
     */
    fun playShuffled(songs: List<Song>) {
        Log.d(TAG, "Playing shuffled list of songs: ${songs.size} songs")
        if (songs.isNotEmpty()) {
            playQueue(songs, enableShuffle = true)
        } else {
            Log.e(TAG, "No songs provided to play shuffled")
        }
    }

    fun playShuffledSongs() {
        val allSongs = _songs.value
        if (allSongs.isEmpty()) return
        playQueue(allSongs, enableShuffle = true)
    }

    // Search history methods
    fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            val currentHistory = _searchHistory.value.toMutableList()
            
            // Remove the query if it already exists to avoid duplicates
            currentHistory.remove(query)
            
            // Add the new query at the beginning
            currentHistory.add(0, query)
            
            // Limit history to 10 items
            val limitedHistory = currentHistory.take(10)
            
            // Update the state
            _searchHistory.value = limitedHistory
            
            // Save to SharedPreferences
            saveSearchHistory()
        }
    }

    fun removeSearchQuery(query: String) {
        viewModelScope.launch {
            val currentHistory = _searchHistory.value.toMutableList()
            if (currentHistory.remove(query)) {
                _searchHistory.value = currentHistory
                saveSearchHistory()
            }
        }
    }

    // Theme Settings Methods
    fun setUseSystemTheme(use: Boolean) {
        appSettings.setUseSystemTheme(use)
    }
    
    fun setDarkMode(dark: Boolean) {
        appSettings.setDarkMode(dark)
    }
    
    // Cache Settings Methods
    fun setMaxCacheSize(size: Long) {
        appSettings.setMaxCacheSize(size)
    }
    
    
    // Audio Device Settings Methods
    fun setAutoConnectDevice(enable: Boolean) {
        appSettings.setAutoConnectDevice(enable)
    }
    
    // Playback Speed Control
    fun setPlaybackSpeed(speed: Float, persist: Boolean = true) {
        Log.d(TAG, "Setting playback speed to $speed (persist=$persist)")
        if (persist) {
            appSettings.setPlaybackSpeed(speed)
        }
        val currentPitch = appSettings.playbackPitch.value
        mediaController?.playbackParameters = androidx.media3.common.PlaybackParameters(speed, currentPitch)
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        Log.d(TAG, "Setting default playback speed to $speed")
        appSettings.setDefaultPlaybackSpeed(speed)
    }

    fun setUseDefaultPlaybackSpeed(enabled: Boolean) {
        Log.d(TAG, "Setting use default playback speed to $enabled")
        appSettings.setUseDefaultPlaybackSpeed(enabled)
    }

    // Playback Pitch Control
    fun setPlaybackPitch(pitch: Float) {
        Log.d(TAG, "Setting playback pitch to $pitch")
        appSettings.setPlaybackPitch(pitch)
        val currentSpeed = appSettings.playbackSpeed.value
        mediaController?.playbackParameters = androidx.media3.common.PlaybackParameters(currentSpeed, pitch)
    }

    // Enhanced play tracking with user preferences
    private fun updateListeningStats(song: Song) {
        if (song.id.startsWith("rhythm-catalog:")) return
        viewModelScope.launch {
            // Update songs played count
            val newCount = _songsPlayed.value + 1
            _songsPlayed.value = newCount
            appSettings.setSongsPlayed(newCount)
            
            // Update unique artists
            val currentArtists = _uniqueArtists.value
            if (!_recentlyPlayed.value.any { it.artist == song.artist }) {
                _uniqueArtists.value = currentArtists + 1
                appSettings.setUniqueArtists(currentArtists + 1)
            }
            
            // Update genre preferences
            val currentGenrePrefs = _genrePreferences.value.toMutableMap()
            GenreUtils.splitGenres(song.genre).forEach { genre ->
                val count = currentGenrePrefs.getOrDefault(genre, 0) + 1
                currentGenrePrefs[genre] = count
            }
            if (currentGenrePrefs != _genrePreferences.value) {
                appSettings.setGenrePreferences(currentGenrePrefs)
            }

            // Update song play counts
            val currentSongPlayCounts = _songPlayCounts.value.toMutableMap()
            val playCount = currentSongPlayCounts.getOrDefault(song.id, 0) + 1
            currentSongPlayCounts[song.id] = playCount
            _songPlayCounts.value = currentSongPlayCounts
            appSettings.setSongPlayCounts(currentSongPlayCounts)
            
            try {
                populateMostPlayedPlaylist()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating most played playlist on playback", e)
            }
            
            // Update time-based preferences
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val currentTimePrefs = _timeBasedPreferences.value.toMutableMap()
            val timeSlot = (hour / 3) * 3 // Group into 3-hour slots
            val songs = currentTimePrefs.getOrDefault(timeSlot, emptyList()).toMutableList()
            if (songs.size >= 20) songs.removeAt(0) // Keep last 20 songs
            songs.add(song.id)
            currentTimePrefs[timeSlot] = songs
            _timeBasedPreferences.value = currentTimePrefs
            appSettings.setTimeBasedPreferences(currentTimePrefs)
            
            // Note: Actual playback time recording is now handled by the playback tracking system
            // in onMediaItemTransition and onIsPlayingChanged listeners
        }
    }

    // Enhanced recommendation algorithms
    fun getRecommendedSongs(): List<Song> {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeSlot = (currentHour / 3) * 3
        
        // Get songs frequently played in this time slot
        val timeBasedSongs = _timeBasedPreferences.value[timeSlot]?.mapNotNull { id ->
            _songs.value.find { it.id == id }
        } ?: emptyList()
        
        // Get songs from preferred genres
        val preferredGenres = _genrePreferences.value.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
        val preferredGenresSet = preferredGenres.map { it.lowercase() }.toSet()
        
        val genreBasedSongs = _songs.value.filter { song ->
            GenreUtils.splitGenres(song.genre).any { it.lowercase() in preferredGenresSet }
        }
        
        // Filter out blacklisted songs
        val blacklistedIds = appSettings.blacklistedSongs.value
        
        // Combine and shuffle recommendations
        return (timeBasedSongs + genreBasedSongs)
            .distinct()
            .filter { !blacklistedIds.contains(it.id) } // Exclude blacklisted songs
            .shuffled()
            .take(10)
    }

    // Enhanced mood-based playlists
    fun getMoodBasedPlaylists(): List<Song> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mood = when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        
        val moodPrefs = appSettings.moodPreferences.value
        val songIds = moodPrefs[mood] ?: emptyList()
        
        return songIds.mapNotNull { id ->
            _songs.value.find { it.id == id }
        }
    }

    // Enhanced recommendation methods
    fun getPersonalizedRecommendations(): List<Song> {
        val recentArtists = _recentlyPlayed.value
            .map { it.artist }
            .distinct()
            .take(3)
        
        return _songs.value
            .filter { song -> 
                recentArtists.contains(song.artist) && 
                !_recentlyPlayed.value.contains(song)
            }
            .shuffled()
            .take(10)
    }

    fun getListeningStats(): String {
        val listeningTime = appSettings.listeningTime.value
        val hours = listeningTime / (1000 * 60 * 60)
        return if (hours < 1) "< 1h" else "${hours}h"
    }
    
    // Enhanced stats access
    val playbackStatsSummary = playbackStatsRepository.statsSummary
    
    /**
     * Load detailed playback stats for a given time range
     */
    suspend fun loadPlaybackStats(
        range: io.github.cluno1.sonorus.shared.data.repository.StatsTimeRange = io.github.cluno1.sonorus.shared.data.repository.StatsTimeRange.ALL_TIME
    ): io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository.PlaybackStatsSummary {
        return playbackStatsRepository.loadSummary(
            range = range,
            songs = _songs.value
        )
    }
    
    /**
     * Get the PlaybackStatsRepository for direct access
     */
    fun getPlaybackStatsRepository(): PlaybackStatsRepository = playbackStatsRepository
    
    /**
     * Get playback statistics for a specific song
     */
    suspend fun getSongPlaybackStats(
        songId: String,
        range: io.github.cluno1.sonorus.shared.data.repository.StatsTimeRange = io.github.cluno1.sonorus.shared.data.repository.StatsTimeRange.ALL_TIME
    ): io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository.SongPlaybackSummary? {
        return playbackStatsRepository.getSongPlaybackStats(songId, range)
    }

    // Initialize from persistence
    private suspend fun initializeFromPersistence() {
        Log.d(TAG, "Initializing from persistence. Songs loaded: ${_songs.value.size}")
        try {
            // Restore recently played
            val recentIds = appSettings.recentlyPlayed.value
            val recentSongCache = appSettings.recentlyPlayedSongCache.value
            val recentSongs = recentIds.mapNotNull { id ->
                _songs.value.find { it.id == id } ?: recentSongCache[id]
            }
            _recentlyPlayed.value = recentSongs
            
            // Clean up old daily stats (keep only last 30 days)
            val thirtyDaysAgo = java.time.LocalDate.now().minusDays(30)
            val cleanedDailyStats = appSettings.dailyListeningStats.value.filterKeys { date ->
                try {
                    java.time.LocalDate.parse(date).isAfter(thirtyDaysAgo)
                } catch (e: Exception) {
                    // Handle potential parsing errors for old data
                    false
                }
            }
            appSettings.updateDailyListeningStats(cleanedDailyStats)
            
            // Clean up weekly top artists (reset every week)
            val lastPlayed = appSettings.lastPlayedTimestamp.value
            if (System.currentTimeMillis() - lastPlayed > 7 * 24 * 60 * 60 * 1000) {
                appSettings.updateWeeklyTopArtists(emptyMap())
            }

            // Initialize mood-based preferences if empty
            if (appSettings.moodPreferences.value.isEmpty()) {
                val initialMoodPrefs = mapOf(
                    "morning" to emptyList<String>(),
                    "afternoon" to emptyList(),
                    "evening" to emptyList(),
                    "night" to emptyList()
                )
                appSettings.updateMoodPreferences(initialMoodPrefs)
            }

            // Log initialization status
            Log.d(TAG, "Initialized from persistence: ${recentSongs.size} recent songs loaded")
            Log.d(TAG, "Daily stats entries: ${cleanedDailyStats.size}")
            Log.d(TAG, "Weekly top artists: ${appSettings.weeklyTopArtists.value.size}")

            // Restore song play counts
            _songPlayCounts.value = appSettings.songPlayCounts.value
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing from persistence", e)
        }
    }

    /**
     * Get current queue info for debugging
     */
    fun getQueueInfo(): String {
        val queue = _currentQueue.value
        return "Queue: ${queue.songs.size} songs, current index: ${queue.currentIndex}, current song: ${_currentSong.value?.title}"
    }

    /**
     * Force sync the queue state with MediaController
     * This is useful for debugging queue inconsistencies
     */
    fun syncQueueWithMediaController() {
        mediaController?.let { controller ->
            Log.d(TAG, "Syncing queue with MediaController")
            Log.d(TAG, "MediaController: ${controller.mediaItemCount} items, current index: ${controller.currentMediaItemIndex}")
            Log.d(TAG, "ViewModel queue: ${_currentQueue.value.songs.size} songs, current index: ${_currentQueue.value.currentIndex}")

            if (controller.mediaItemCount > 0) {
                val mediaItems = if (controller.shuffleModeEnabled) {
                    // Build queue in shuffle traversal order from the timeline.
                    val traversal = mutableListOf<MediaItem>()
                    val visited = BooleanArray(controller.mediaItemCount)
                    val timeline = controller.currentTimeline
                    var windowIndex = timeline.getFirstWindowIndex(true)
                    while (windowIndex != C.INDEX_UNSET && windowIndex in visited.indices && !visited[windowIndex]) {
                        traversal.add(controller.getMediaItemAt(windowIndex))
                        visited[windowIndex] = true
                        windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
                    }

                    if (traversal.isEmpty()) {
                        (0 until controller.mediaItemCount).map { index ->
                            controller.getMediaItemAt(index)
                        }
                    } else {
                        traversal
                    }
                } else {
                    (0 until controller.mediaItemCount).map { index ->
                        controller.getMediaItemAt(index)
                    }
                }

                // Resolve songs via an id -> song map (O(n) even for large queues), including
                // the current queue so streaming (Go-mode) songs keep their real duration/artwork.
                val songsById = (_songs.value + _currentQueue.value.songs).associateBy { it.id }
                val mediaItemSongs = mediaItems.mapNotNull { mediaItem ->
                    songsById[mediaItem.mediaId] ?: mediaItemToTransientSong(mediaItem)
                }

                val currentMediaId = controller.currentMediaItem?.mediaId
                val rawCurrentMediaIndex = currentMediaId
                    ?.let { id -> mediaItemSongs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                    ?: controller.currentMediaItemIndex.coerceAtLeast(0)
                val currentMediaIndex = if (mediaItemSongs.isEmpty()) {
                    -1
                } else {
                    rawCurrentMediaIndex.coerceIn(0, mediaItemSongs.lastIndex)
                }
                _currentQueue.value = Queue(mediaItemSongs, currentMediaIndex)

                // Update current song if needed
                if (mediaItemSongs.isNotEmpty() && currentMediaIndex >= 0 && currentMediaIndex < mediaItemSongs.size) {
                    val currentSong = mediaItemSongs[currentMediaIndex]
                    _currentSong.value = currentSong
                    _isFavorite.value = _favoriteSongs.value.contains(currentSong.id)
                } else if (mediaItemSongs.isEmpty()) {
                    _currentSong.value = null
                }

                Log.d(TAG, "Synced queue: ${mediaItemSongs.size} songs, index: $currentMediaIndex, shuffle: ${controller.shuffleModeEnabled}")
            } else {
                // No items in MediaController, clear queue
                _currentQueue.value = Queue(emptyList(), -1)
                _currentSong.value = null
                Log.d(TAG, "Cleared queue - no items in MediaController")
            }
        } ?: run {
            Log.w(TAG, "Cannot sync queue - MediaController is null")
        }
    }

    /**
     * Remove any blacklisted or unwhitelisted songs from the active playback queue.
     */
    fun removeBlacklistedSongsFromQueue() {
        mediaController?.let { controller ->
            val mediaScanMode = appSettings.mediaScanMode.value
            val useBlacklist = mediaScanMode == MediaScanMode.BLACKLIST
            val useWhitelist = mediaScanMode == MediaScanMode.WHITELIST
            val blacklistedIds = appSettings.blacklistedSongs.value
            val blacklistedFolders = appSettings.blacklistedFolders.value
            val whitelistedIds = appSettings.whitelistedSongs.value
            val whitelistedFolders = appSettings.whitelistedFolders.value
            
            val hasBlacklist = blacklistedIds.isNotEmpty() || blacklistedFolders.isNotEmpty()
            val hasWhitelist = whitelistedIds.isNotEmpty() || whitelistedFolders.isNotEmpty()
            
            if (useBlacklist && !hasBlacklist) return@let
            if (useWhitelist && !hasWhitelist) return@let
            
            val count = controller.mediaItemCount
            if (count == 0) return@let
            
            Log.d(TAG, "Checking active queue for blacklisted or unwhitelisted songs. Total items: $count")
            
            // Remove items starting from the end of the queue to keep indices stable
            for (i in count - 1 downTo 0) {
                val mediaItem = controller.getMediaItemAt(i)
                val songId = mediaItem.mediaId
                val song = _songs.value.find { it.id == songId }
                val songPath = song?.path ?: if (song?.uri?.scheme == "file") song.uri.path else getPathFromUriCached(song?.uri ?: android.net.Uri.EMPTY)
                
                var shouldInclude = true
                if (useBlacklist && hasBlacklist) {
                    if (blacklistedIds.contains(songId)) {
                        shouldInclude = false
                    } else if (blacklistedFolders.isNotEmpty() && songPath != null) {
                        shouldInclude = !isPathBlacklisted(songPath, blacklistedFolders)
                    }
                } else if (useWhitelist && hasWhitelist) {
                    if (whitelistedIds.contains(songId)) {
                        shouldInclude = true
                    } else if (whitelistedFolders.isNotEmpty() && songPath != null) {
                        shouldInclude = isPathWhitelisted(songPath, whitelistedFolders)
                    } else {
                        shouldInclude = false
                    }
                }
                
                if (!shouldInclude) {
                    controller.removeMediaItem(i)
                    Log.d(TAG, "Removed blacklisted/unwhitelisted song from active queue: $songId at index $i")
                }
            }
            
            // Re-sync local representation
            syncQueueWithMediaController()
        }
    }

    /**
     * Debug function to print current queue state
     */
    fun debugQueueState() {
        Log.d(TAG, "=== QUEUE DEBUG INFO ===")
        Log.d(TAG, "ViewModel queue: ${_currentQueue.value.songs.size} songs, current index: ${_currentQueue.value.currentIndex}")
        if (_currentQueue.value.songs.isNotEmpty()) {
            Log.d(TAG, "Queue songs:")
            _currentQueue.value.songs.forEachIndexed { index, song ->
                val marker = if (index == _currentQueue.value.currentIndex) " -> " else "    "
                Log.d(TAG, "$marker$index: ${song.title} by ${song.artist}")
            }
        }
        
        mediaController?.let { controller ->
            Log.d(TAG, "MediaController: ${controller.mediaItemCount} items, current index: ${controller.currentMediaItemIndex}")
            Log.d(TAG, "MediaController state: ${controller.playbackState}, isPlaying: ${controller.isPlaying}")
            if (controller.mediaItemCount > 0) {
                Log.d(TAG, "MediaController songs:")
                for (i in 0 until controller.mediaItemCount) {
                    val mediaItem = controller.getMediaItemAt(i)
                    val marker = if (i == controller.currentMediaItemIndex) " -> " else "    "
                    Log.d(TAG, "$marker$i: ${mediaItem.mediaId} (${mediaItem.mediaMetadata.title})")
                }
            }
        } ?: run {
            Log.d(TAG, "MediaController: null")
        }
        
        Log.d(TAG, "Current song: ${_currentSong.value?.title ?: "none"}")
        Log.d(TAG, "Is playing: ${_isPlaying.value}")
        Log.d(TAG, "========================")
    }

    fun playAlbumShuffled(album: Album) {
        viewModelScope.launch {
            Log.d(TAG, "Playing shuffled album: ${album.title} (ID: ${album.id})")
            val songs = repository.getSongsForAlbumLocal(album.id)
            Log.d(TAG, "Found ${songs.size} songs for album")
            if (songs.isNotEmpty()) {
                playQueue(songs, enableShuffle = true)
            } else {
                Log.e(TAG, "No songs found for album: ${album.title} (ID: ${album.id})")
                debugQueueState()
            }
        }
    }

    fun playPlaylistShuffled(playlist: Playlist) {
        Log.d(TAG, "Playing shuffled playlist: ${playlist.name}")
        if (playlist.songs.isNotEmpty()) {
            playQueue(playlist.songs, enableShuffle = true)
        } else {
            Log.e(TAG, "No songs found in playlist: ${playlist.name}")
        }
    }
    
    /**
     * Helper function to get file path from URI
     */
    private fun getPathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    var path: String? = null
                    // Direct content URI query
                    val cursor = getApplication<Application>().contentResolver.query(
                        uri, projection, null, null, null
                    )
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                            if (!c.isNull(dataIndex)) {
                                path = c.getString(dataIndex)
                            }
                        }
                    }
                    // Fallback: query EXTERNAL_CONTENT_URI by _ID when direct query returns null
                    // This works around DATA column deprecation on API 30+
                    if (path == null) {
                        val id = uri.lastPathSegment
                        if (id != null) {
                            val fallbackCursor = getApplication<Application>().contentResolver.query(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                projection,
                                "${MediaStore.Audio.Media._ID} = ?",
                                arrayOf(id),
                                null
                            )
                            fallbackCursor?.use { c ->
                                if (c.moveToFirst()) {
                                    val dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                    if (!c.isNull(dataIndex)) {
                                        path = c.getString(dataIndex)
                                    }
                                }
                            }
                        }
                    }
                    if (path == null) {
                        Log.w(TAG, "Could not resolve file path for content URI: $uri")
                    }
                    path
                }
                "file" -> {
                    uri.path
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path from URI: $uri", e)
            null
        }
    }
    
    /**
     * Cached version of getPathFromUri to avoid repeated ContentResolver queries
     */
    private fun getPathFromUriCached(uri: Uri): String? {
        val uriString = uri.toString()
        val cached = pathCache.get(uriString)
        if (cached != null) {
            return cached
        }
        
        val path = getPathFromUri(uri)
        if (path != null) {
            pathCache.put(uriString, path)
        }
        return path
    }
    
    /**
     * Exposes the MusicRepository instance for cache cleanup operations
     * @return The MusicRepository instance
     */
    fun getMusicRepository(): MusicRepository {
        return repository
    }
    
    // Clean sleep timer functionality
    fun startSleepTimer(minutes: Int, action: SleepAction) {
        // Cancel any local fallback timer — the service handles cleanup internally.
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        val safeMinutes = minutes.coerceAtLeast(1)
        val totalSeconds = safeMinutes * 60L
        val totalDurationMs = totalSeconds * 1000L

        sleepTimerStartRealtimeMs = SystemClock.elapsedRealtime()
        _sleepTimerActive.value = true
        _sleepTimerRemainingSeconds.value = totalSeconds
        _sleepTimerAction.value = action.name

        Log.d(TAG, "Starting sleep timer: ${safeMinutes} minutes, action: ${action.name}")

        val (fadeOut, pauseOnly) = when (action) {
            SleepAction.FADE_OUT -> true to true
            SleepAction.PAUSE -> false to true
            SleepAction.STOP -> false to false
        }

        val context = getApplication<Application>()
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            this.action = MediaPlaybackService.ACTION_START_SLEEP_TIMER
            putExtra("duration", totalDurationMs)
            putExtra("fadeOut", fadeOut)
            putExtra("pauseOnly", pauseOnly)
        }

        val serviceCommandSent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = serviceIntent,
            logTag = TAG,
            reason = "start_sleep_timer"
        )

        if (serviceCommandSent) {
            return  // grace period covers stale broadcasts during the transition
        }

        // Fallback: maintain local timer behavior if service command fails.
        sleepTimerStartRealtimeMs = 0L  // no service → no stale broadcasts to ignore
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds

            while (remaining > 0 && _sleepTimerActive.value) {
                delay(1000) // Wait 1 second
                remaining--
                _sleepTimerRemainingSeconds.value = remaining
            }

            // Timer finished, execute action
            if (_sleepTimerActive.value && remaining <= 0) {
                Log.d(TAG, "Sleep timer finished, executing action: ${action.name}")

                when (action) {
                    SleepAction.FADE_OUT -> fadeOutAndPause()
                    SleepAction.PAUSE -> pauseMusic()
                    SleepAction.STOP -> stopMusic()
                }

                // Reset timer state
                _sleepTimerActive.value = false
                _sleepTimerRemainingSeconds.value = 0L
            }
        }
    }
    
    fun stopSleepTimer() {
        Log.d(TAG, "Stopping sleep timer")
        sleepTimerStartRealtimeMs = 0L
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerActive.value = false
        _sleepTimerRemainingSeconds.value = 0L

        val context = getApplication<Application>()
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            this.action = MediaPlaybackService.ACTION_STOP_SLEEP_TIMER
        }

        val stopSent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = serviceIntent,
            logTag = TAG,
            reason = "stop_sleep_timer"
        )
        if (!stopSent) {
            Log.w(TAG, "Failed to stop service-backed sleep timer")
        }
    }
    
    // Equalizer functionality
    fun isEqualizerSupported(): Boolean {
        // All devices now support equalizer with software implementation
        return true
    }
    
    fun setEqualizerEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        
        // Save to settings
        appSettings.setEqualizerEnabled(enabled)
        
        // Send to service
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_SET_EQUALIZER_ENABLED
            putExtra("enabled", enabled)
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "set_equalizer_enabled"
        )
        if (sent) {
            Log.d(TAG, "Set equalizer enabled: $enabled")
        }
    }
    
    /**
     * Request diagnostic information from the service to debug equalizer issues
     */
    fun logEqualizerDiagnostics() {
        val context = getApplication<Application>()
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_GET_EQUALIZER_DIAGNOSTICS
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "get_equalizer_diagnostics"
        )
        if (sent) {
            Log.d(TAG, "Requested equalizer diagnostics from service")
        }
    }
    
    fun setEqualizerBandLevel(band: Short, level: Short) {
        val context = getApplication<Application>()
        
        // Clear AutoEQ profile and set preset to Custom when user manually adjusts bands
        val currentPreset = appSettings.equalizerPreset.value
        if (currentPreset.startsWith("AutoEQ:") || currentPreset != "Custom") {
            appSettings.setEqualizerPreset("Custom")
            appSettings.setAutoEQProfile("")
            Log.d(TAG, "Switched to Custom preset as user manually adjusted EQ band")
        }
        
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_SET_EQUALIZER_BAND
            putExtra("band", band)
            putExtra("level", level)
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "set_equalizer_band"
        )
        if (sent) {
            Log.d(TAG, "Set equalizer band $band to level $level")
        }
    }
    
    fun setBassBoost(enabled: Boolean, strength: Short = 500) {
        val context = getApplication<Application>()
        
        // Save to settings
        appSettings.setBassBoostEnabled(enabled)
        appSettings.setBassBoostStrength(strength.toInt())
        
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_SET_BASS_BOOST
            putExtra("enabled", enabled)
            putExtra("strength", strength)
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "set_bass_boost"
        )
        if (sent) {
            Log.d(TAG, "Set bass boost enabled: $enabled, strength: $strength")
        }
    }
    
    fun isBassBoostSupported(): Boolean {
        return _isBassBoostAvailable.value
    }
    
    fun updateBassBoostAvailability() {
        _isBassBoostAvailable.value = appSettings.isBassBoostAvailable()
    }
    
    fun setBassBoostAvailable(available: Boolean) {
        _isBassBoostAvailable.value = available
        appSettings.setBassBoostAvailable(available)
    }
    
    fun setVirtualizer(enabled: Boolean, strength: Short = 500) {
        val context = getApplication<Application>()
        
        // Save to settings
        appSettings.setVirtualizerEnabled(enabled)
        appSettings.setVirtualizerStrength(strength.toInt())
        
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_SET_VIRTUALIZER
            putExtra("enabled", enabled)
            putExtra("strength", strength)
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "set_virtualizer"
        )
        if (sent) {
            Log.d(TAG, "Set virtualizer enabled: $enabled, strength: $strength")
        }
        
        // Update spatialization status after a short delay
        viewModelScope.launch {
            delay(200)
            updateSpatializationStatus()
        }
    }

    fun setMonoAudioEnabled(enabled: Boolean) {
        val context = getApplication<Application>()

        appSettings.setMonoAudioEnabled(enabled)

        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_SET_MONO_AUDIO
            putExtra("enabled", enabled)
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "set_mono_audio"
        )
        if (sent) {
            Log.d(TAG, "Set mono audio enabled: $enabled")
        }
    }
    
    fun updateSpatializationStatus() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // Rhythm's custom spatialization works on ALL Android versions
                // It uses M/S stereo widening, so it's always available for stereo audio
                _isSpatializationAvailable.value = true
                _spatializationStatus.value = when {
                    appSettings.virtualizerEnabled.value -> "Active (Sonorus Spatial Audio)"
                    else -> "Available"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating spatialization status", e)
                _isSpatializationAvailable.value = true // Still available even if check fails
                _spatializationStatus.value = "Available"
            }
        }
    }
    
    fun applyEqualizerPreset(preset: String, levels: List<Float>) {
        val context = getApplication<Application>()
        
        // Save to settings
        appSettings.setEqualizerPreset(preset)
        appSettings.setEqualizerBandLevels(levels.joinToString(","))
        
        // Clear AutoEQ profile when applying a different preset (unless it's an AutoEQ preset)
        if (!preset.startsWith("AutoEQ:")) {
            appSettings.setAutoEQProfile("")
            Log.d(TAG, "Cleared AutoEQ profile as user selected preset: $preset")
        }
        
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_APPLY_EQUALIZER_PRESET
            putExtra("preset", preset)
            putExtra("levels", levels.toFloatArray())
        }
        val sent = ServiceStartUtils.startServiceSafely(
            context = context,
            intent = intent,
            logTag = TAG,
            reason = "apply_equalizer_preset"
        )
        if (sent) {
            Log.d(TAG, "Applied equalizer preset: $preset")
        }
    }
    
    // AutoEQ functions
    private val _autoEQProfiles = MutableStateFlow<List<io.github.cluno1.sonorus.shared.data.model.AutoEQProfile>>(emptyList())
    val autoEQProfiles: StateFlow<List<io.github.cluno1.sonorus.shared.data.model.AutoEQProfile>> = _autoEQProfiles.asStateFlow()
    
    private val _autoEQLoading = MutableStateFlow(false)
    val autoEQLoading: StateFlow<Boolean> = _autoEQLoading.asStateFlow()
    
    fun loadAutoEQProfiles() {
        viewModelScope.launch {
            _autoEQLoading.value = true
            try {
                val result: Result<AutoEQDatabase> = autoEQManager.loadProfiles()
                if (result.isSuccess) {
                    val database = result.getOrNull()!!
                    _autoEQProfiles.value = database.profiles
                    Log.d(TAG, "Loaded ${database.profiles.size} AutoEQ profiles")
                } else {
                    Log.e(TAG, "Failed to load AutoEQ profiles", result.exceptionOrNull())
                }
            } finally {
                _autoEQLoading.value = false
            }
        }
    }
    
    fun searchAutoEQProfiles(query: String): List<io.github.cluno1.sonorus.shared.data.model.AutoEQProfile> {
        return autoEQManager.searchProfiles(query)
    }
    
    fun applyAutoEQProfile(profile: io.github.cluno1.sonorus.shared.data.model.AutoEQProfile) {
        if (profile.name.isBlank() || profile.name.equals("None", ignoreCase = true)) {
            Log.d(TAG, "Disabling AutoEQ profile")
            appSettings.setAutoEQProfile("")
            applyEqualizerPreset("Flat", List(10) { 0f })
            return
        }
        
        Log.d(TAG, "Applying AutoEQ profile: ${profile.name}")
        
        // Ensure we have 10 bands
        val levels = profile.bands.take(10)
        if (levels.size != 10) {
            Log.w(TAG, "AutoEQ profile has ${levels.size} bands, expected 10")
            return
        }
        
        // Save profile name to settings
        appSettings.setAutoEQProfile(profile.name)
        
        // Enable equalizer first if it's not already enabled
        val wasEnabled = appSettings.equalizerEnabled.value
        if (!wasEnabled) {
            Log.d(TAG, "Enabling equalizer to apply AutoEQ profile")
            setEqualizerEnabled(true)
            
            // Wait for equalizer to initialize using coroutine
            viewModelScope.launch {
                // Increased delay to ensure service has time to initialize equalizer
                // This is critical because the service needs to:
                // 1. Process the enable intent
                // 2. Check if equalizer object exists (may need to reinitialize)
                // 3. Enable the equalizer
                delay(300) // Increased from 150ms to 300ms for reliability
                
                // Apply the profile as a preset
                applyEqualizerPreset("AutoEQ: ${profile.name}", levels)
                Log.d(TAG, "AutoEQ profile applied successfully (equalizer was enabled automatically)")
            }
        } else {
            // Equalizer already enabled, apply immediately
            applyEqualizerPreset("AutoEQ: ${profile.name}", levels)
            Log.d(TAG, "AutoEQ profile applied successfully (equalizer was already enabled)")
        }
    }
    
    fun getAutoEQRecommendedProfiles(): List<io.github.cluno1.sonorus.shared.data.model.AutoEQProfile> {
        return autoEQManager.getRecommendedProfiles()
    }
    
    // User Audio Device Management
    fun saveUserAudioDevice(device: io.github.cluno1.sonorus.shared.data.model.UserAudioDevice) {
        val currentDevicesJson = appSettings.userAudioDevices.value
        val currentDevices = io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.fromJson(currentDevicesJson).toMutableList()
        
        // Check if device already exists (update) or is new (add)
        val existingIndex = currentDevices.indexOfFirst { it.id == device.id }
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = device
            Log.d(TAG, "Updated audio device: ${device.name}")
        } else {
            currentDevices.add(device)
            Log.d(TAG, "Added new audio device: ${device.name}")
        }
        
        appSettings.setUserAudioDevices(io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.toJson(currentDevices))
    }
    
    fun deleteUserAudioDevice(deviceId: String) {
        val currentDevicesJson = appSettings.userAudioDevices.value
        val currentDevices = io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.fromJson(currentDevicesJson).toMutableList()
        
        currentDevices.removeAll { it.id == deviceId }
        appSettings.setUserAudioDevices(io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.toJson(currentDevices))
        
        // If deleted device was active, clear active device
        if (appSettings.activeAudioDeviceId.value == deviceId) {
            appSettings.setActiveAudioDeviceId(null)
        }
        
        Log.d(TAG, "Deleted audio device: $deviceId")
    }
    
    fun setActiveAudioDevice(device: io.github.cluno1.sonorus.shared.data.model.UserAudioDevice) {
        appSettings.setActiveAudioDeviceId(device.id)
        Log.d(TAG, "Set active audio device: ${device.name}")
        
        // Apply AutoEQ profile if the device has one configured
        if (device.autoEQProfileName != null) {
            val profile: AutoEQProfile? = autoEQManager.findProfileByName(device.autoEQProfileName)
            if (profile != null) {
                applyAutoEQProfile(profile)
                Log.d(TAG, "Applied AutoEQ profile for device: ${device.autoEQProfileName}")
            }
        }

        // Apply device-specific mono audio preference
        setMonoAudioEnabled(device.monoAudioEnabled)
        Log.d(TAG, "Applied mono audio for device: ${device.monoAudioEnabled}")
    }
    
    fun getActiveAudioDevice(): io.github.cluno1.sonorus.shared.data.model.UserAudioDevice? {
        val activeId = appSettings.activeAudioDeviceId.value ?: return null
        val devices = io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.fromJson(appSettings.userAudioDevices.value)
        return devices.find { it.id == activeId }
    }
    
    /**
     * Try to match a connected audio device (from PlaybackLocation) with saved UserAudioDevice
     * Uses fuzzy matching to account for slight name variations
     */
    fun findMatchingUserDevice(deviceName: String): io.github.cluno1.sonorus.shared.data.model.UserAudioDevice? {
        val devices = io.github.cluno1.sonorus.shared.data.model.UserAudioDevice.fromJson(appSettings.userAudioDevices.value)
        if (devices.isEmpty()) return null
        
        val normalizedSearchName = deviceName.lowercase().trim()
        
        // First try exact match
        devices.find { it.name.equals(deviceName, ignoreCase = true) }?.let { return it }
        
        // Try fuzzy matching - device name contains saved name or vice versa
        devices.find { savedDevice ->
            val normalizedSavedName = savedDevice.name.lowercase().trim()
            normalizedSearchName.contains(normalizedSavedName) || 
            normalizedSavedName.contains(normalizedSearchName)
        }?.let { return it }
        
        // Try matching by brand if available
        devices.find { savedDevice ->
            if (savedDevice.brand.isNotEmpty()) {
                normalizedSearchName.contains(savedDevice.brand.lowercase())
            } else false
        }?.let { return it }
        
        return null
    }
    
    /**
     * Check if device detection should show AutoEQ suggestion
     * Returns true if device hasn't been dismissed before
     */
    fun shouldShowAutoEQSuggestion(deviceId: String): Boolean {
        val dismissedDevices = appSettings.dismissedAutoEQSuggestions.value?.split(",") ?: emptyList()
        return !dismissedDevices.contains(deviceId)
    }
    
    /**
     * Mark a device as "don't ask again" for AutoEQ suggestions
     */
    fun dismissAutoEQSuggestion(deviceId: String) {
        val current = appSettings.dismissedAutoEQSuggestions.value ?: ""
        val dismissedList = if (current.isEmpty()) {
            listOf(deviceId)
        } else {
            current.split(",").toMutableList().apply { add(deviceId) }
        }
        appSettings.setDismissedAutoEQSuggestions(dismissedList.joinToString(","))
        Log.d(TAG, "Dismissed AutoEQ suggestion for device: $deviceId")
    }
    
    // Clean sleep timer implementation
    private val _sleepTimerActive = MutableStateFlow(false)
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()
    
    private val _sleepTimerRemainingSeconds = MutableStateFlow(0L)
    val sleepTimerRemainingSeconds: StateFlow<Long> = _sleepTimerRemainingSeconds.asStateFlow()
    
    private val _sleepTimerTotalSeconds = MutableStateFlow(0L)
    val sleepTimerTotalSeconds: StateFlow<Long> = _sleepTimerTotalSeconds.asStateFlow()
    
    private val _sleepTimerAction = MutableStateFlow("FADE_OUT")
    val sleepTimerAction: StateFlow<String> = _sleepTimerAction.asStateFlow()
    
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    
    /**
     * Realtime timestamp (SystemClock.elapsedRealtime()) of the most recent
     * startSleepTimer() call. Used to ignore stale "timer stopped" broadcasts
     * that arrive during the service's internal timer-rewrite transition
     * (including the old coroutine's CancellationException handler which can
     * fire up to ~1s later).
     */
    private var sleepTimerStartRealtimeMs = 0L
    
    // Sleep timer state is synchronized from service status broadcasts.
    
    override fun onCleared() {

        Log.d(TAG, "ViewModel clearing, cleaning up resources")
        playbackControlStateMachine.clear()
        
        // Unregister broadcast receiver
        try {
            getApplication<Application>().unregisterReceiver(favoriteChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister favorite change receiver", e)
        }
        
        // Save current queue state before cleanup
        saveQueueToPersistence()
        
        // Finalize any ongoing playback tracking before cleanup
        finalizePlaybackTracking()
        
        // Unregister ContentObserver
        repository.unregisterMediaStoreObserver()
        
        // Clean up audio device manager to prevent BroadcastReceiver leaks
        audioDeviceManager.cleanup()
        
        // Cancel ongoing scan and lyrics fetch
        scanJob?.cancel()
        lyricsFetchJob?.cancel()
        clearOperationNotifications()
        
        // Cancel progress updates
        progressUpdateJob?.cancel()
        stopPlaybackWatchdog()
        
        // Remove player listener before releasing MediaController
        mediaController?.removeListener(playerListener)
        controllerConnectInFlight.set(false)
        
        // Release MediaController
        mediaController?.release()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
}

}
}
