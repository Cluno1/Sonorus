package io.github.cluno1.sonorus.features.catalog.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.PlaybackDescriptor
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevision
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import io.github.cluno1.sonorus.features.catalog.domain.RhythmQueueEntry
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryAlbum
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryScoreWork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyrics
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchType
import io.github.cluno1.sonorus.features.catalog.domain.CatalogTagWorksPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogIssuedInvite
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDashboard
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDevice
import io.github.cluno1.sonorus.features.catalog.domain.CatalogSmartEnrollmentError
import io.github.cluno1.sonorus.features.catalog.domain.CatalogSmartEnrollmentException
import io.github.cluno1.sonorus.features.catalog.domain.CatalogSmartEnrollmentText
import io.github.cluno1.sonorus.features.catalog.domain.ChorusCatalog
import io.github.cluno1.sonorus.features.catalog.domain.ChorusMix
import io.github.cluno1.sonorus.features.catalog.domain.ChorusProject
import io.github.cluno1.sonorus.features.catalog.domain.ChorusSyncAnchor
import io.github.cluno1.sonorus.features.catalog.domain.ChorusTrack
import io.github.cluno1.sonorus.features.catalog.domain.ChorusTrackUpload
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.shared.data.model.Song
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val configured: Boolean = false,
    val deviceRegistered: Boolean = false,
    val reenrollmentRequired: Boolean = false,
    val serverUrl: String = "",
    val works: List<WorkSummary> = emptyList(),
    val songs: List<CatalogLibrarySong> = emptyList(),
    val albums: List<CatalogLibraryAlbum> = emptyList(),
    val scoreWorks: List<CatalogLibraryScoreWork> = emptyList(),
    val selectedBundle: WorkBundle? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val offlineSnapshot: Boolean = false,
    val issuedInvite: CatalogIssuedInvite? = null,
    val currentDeviceId: String? = null,
    val administratorDevices: List<CatalogAdminDevice> = emptyList(),
    val administratorDashboard: CatalogAdminDashboard? = null,
    val administratorLoginRequired: Boolean = false,
    val administratorSessionActive: Boolean = false,
    val administratorLoading: Boolean = false,
    val error: String? = null,
    val adminError: String? = null,
)

data class RestoredUnifiedQueue(
    val songs: List<Song>,
    val catalogEntries: List<RhythmQueueEntry>,
    val currentIndex: Int,
    val positionMs: Long,
)

private data class RestoredQueueItem(
    val sourceIndex: Int,
    val song: Song,
    val catalogEntry: RhythmQueueEntry?,
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CatalogModule.repository(application)
    private val queueStore = CatalogQueueStore(application)
    private val _state = MutableStateFlow(
        repository.connection().let {
            CatalogUiState(
                configured = it.configured,
                deviceRegistered = it.deviceRegistered,
                reenrollmentRequired = it.reenrollmentRequired,
                serverUrl = it.serverUrl,
                currentDeviceId = it.deviceId,
                works = repository.cachedWorks(),
                songs = repository.cachedLibrary()?.songs.orEmpty(),
                albums = repository.cachedLibrary()?.albums.orEmpty(),
                scoreWorks = repository.cachedLibrary()?.scoreWorks.orEmpty(),
                error = if (it.reenrollmentRequired) "音乐库登记已失效，请重新登记" else null,
            )
        },
    )
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var libraryRefreshJob: Job? = null

    init {
        if (_state.value.configured) refreshLibrary()
    }

    fun enrollDevice(serverUrl: String, inviteCode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.enrollDevice(serverUrl, inviteCode).fold(
                onSuccess = {
                    val connection = repository.connection()
                    _state.value = _state.value.copy(
                        configured = true,
                        deviceRegistered = connection.deviceRegistered,
                        reenrollmentRequired = connection.reenrollmentRequired,
                        serverUrl = connection.serverUrl,
                        currentDeviceId = connection.deviceId,
                        loading = false,
                    )
                    refreshLibrary()
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = message(it)) },
            )
        }
    }

    fun enrollDeviceFromSmartText(text: String) {
        val payload = try {
            CatalogSmartEnrollmentText.decodeFromText(text).let { decoded ->
                decoded.copy(serverUrl = CatalogEndpoint.normalize(decoded.serverUrl))
            }
        } catch (error: CatalogSmartEnrollmentException) {
            _state.value = _state.value.copy(error = smartEnrollmentMessage(error.reason))
            return
        } catch (_: IllegalArgumentException) {
            _state.value = _state.value.copy(
                error = getApplication<Application>().getString(R.string.catalog_smart_enrollment_invalid),
            )
            return
        }
        enrollDevice(payload.serverUrl, payload.inviteCode)
    }

    fun issueInvite(
        serverUrl: String,
        username: String,
        password: String,
        userId: String,
        displayName: String,
        replaceExistingDevice: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, issuedInvite = null, adminError = null)
            repository.issueInvite(
                serverUrl,
                username,
                password,
                userId,
                displayName,
                replaceExistingDevice,
            ).fold(
                onSuccess = { invite ->
                    _state.value = _state.value.copy(
                        loading = false,
                        issuedInvite = invite,
                        adminError = null,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        loading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun clearConnection() {
        repository.clearConnection()
        queueStore.clear()
        _state.value = CatalogUiState()
    }

    fun clearInviteUiState() {
        _state.value = _state.value.copy(issuedInvite = null, adminError = null)
    }

    fun refreshAdministrator() {
        viewModelScope.launch {
            _state.value = _state.value.copy(administratorLoading = true, adminError = null)
            repository.getAdminDashboard().fold(
                onSuccess = { dashboard ->
                    _state.value = _state.value.copy(
                        administratorDashboard = dashboard,
                        administratorDevices = dashboard.devices,
                        administratorLoginRequired = false,
                        administratorLoading = false,
                        adminError = null,
                    )
                },
                onFailure = { error ->
                    if (error is CatalogFailure.Forbidden) {
                        _state.value = _state.value.copy(
                            administratorDashboard = null,
                            administratorLoginRequired = true,
                            administratorLoading = false,
                            adminError = null,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            administratorDashboard = null,
                            administratorLoading = false,
                            adminError = message(error),
                        )
                    }
                },
            )
        }
    }

    fun authenticateAdministrator(username: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(administratorLoading = true, adminError = null)
            repository.authenticateAdministrator(username, password).fold(
                onSuccess = { devices ->
                    _state.value = _state.value.copy(
                        administratorDevices = devices,
                        administratorSessionActive = true,
                        administratorLoginRequired = true,
                        administratorLoading = false,
                        adminError = null,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        administratorSessionActive = false,
                        administratorLoading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun setDeviceAdministrator(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(administratorLoading = true, adminError = null)
            repository.setDeviceAdministrator(deviceId, enabled).fold(
                onSuccess = { devices ->
                    _state.value = _state.value.copy(
                        administratorDevices = devices,
                        administratorLoading = false,
                    )
                    if (deviceId == _state.value.currentDeviceId) refreshAdministrator()
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        administratorLoading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun issueInviteAsAdministrator(
        userId: String,
        displayName: String,
        replaceExistingDevice: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                administratorLoading = true,
                issuedInvite = null,
                adminError = null,
            )
            repository.issueInviteAsAdministrator(
                userId,
                displayName,
                replaceExistingDevice,
            ).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        issuedInvite = it,
                        administratorLoading = false,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        administratorLoading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun setChorusAutomaticApproval(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(administratorLoading = true, adminError = null)
            repository.setChorusAutomaticApproval(enabled).fold(
                onSuccess = { settings ->
                    val dashboard = _state.value.administratorDashboard
                    _state.value = _state.value.copy(
                        administratorDashboard = dashboard?.copy(settings = settings),
                        administratorLoading = false,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        administratorLoading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun moderateChorusTrack(trackId: String, revision: Int, publish: Boolean, reason: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(administratorLoading = true, adminError = null)
            repository.moderateChorusTrack(trackId, revision, publish, reason).fold(
                onSuccess = { refreshAdministrator() },
                onFailure = {
                    _state.value = _state.value.copy(
                        administratorLoading = false,
                        adminError = message(it),
                    )
                },
            )
        }
    }

    fun refreshWorks(query: String? = null) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!query.isNullOrBlank()) delay(300)
            val hadItems = _state.value.works.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hadItems,
                refreshing = hadItems,
                error = null,
            )
            repository.listWorks(query).fold(
                onSuccess = { page ->
                    _state.value = _state.value.copy(
                        works = page.items,
                        loading = false,
                        refreshing = false,
                        offlineSnapshot = page.fromCache,
                    )
                    if (!page.fromCache && query.isNullOrBlank()) repository.syncChanges()
                },
                onFailure = { error ->
                    val reenrollmentRequired = error is CatalogFailure.InvalidCredentials
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        offlineSnapshot = hadItems && error is CatalogFailure.Unreachable,
                        reenrollmentRequired = reenrollmentRequired || _state.value.reenrollmentRequired,
                        deviceRegistered = if (reenrollmentRequired) false else _state.value.deviceRegistered,
                        error = message(error),
                    )
                },
            )
        }
    }

    fun openWork(workId: String) {
        viewModelScope.launch {
            val cached = repository.cachedBundle(workId)
            _state.value = _state.value.copy(selectedBundle = cached, loading = cached == null, error = null)
            repository.getWorkBundle(workId).fold(
                onSuccess = { _state.value = _state.value.copy(selectedBundle = it, loading = false) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = message(it)) },
            )
        }
    }

    suspend fun loadWork(workId: String): Result<WorkBundle> {
        val cached = repository.cachedBundle(workId)
        if (cached != null) _state.value = _state.value.copy(selectedBundle = cached)
        return repository.getWorkBundle(workId).onSuccess {
            _state.value = _state.value.copy(selectedBundle = it, error = null)
        }
    }

    fun refreshLibrary() {
        libraryRefreshJob?.cancel()
        libraryRefreshJob = viewModelScope.launch {
            val hadItems = _state.value.songs.isNotEmpty() || _state.value.albums.isNotEmpty() ||
                _state.value.scoreWorks.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hadItems,
                refreshing = hadItems,
                error = null,
            )
            repository.getLibrary(forceRefresh = true).fold(
                onSuccess = { snapshot ->
                    _state.value = _state.value.copy(
                        songs = snapshot.songs,
                        albums = snapshot.albums,
                        scoreWorks = snapshot.scoreWorks,
                        loading = false,
                        refreshing = false,
                        offlineSnapshot = snapshot.fromCache,
                    )
                },
                onFailure = { error ->
                    val reenrollmentRequired = error is CatalogFailure.InvalidCredentials
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        offlineSnapshot = hadItems && error is CatalogFailure.Unreachable,
                        reenrollmentRequired = reenrollmentRequired || _state.value.reenrollmentRequired,
                        deviceRegistered = if (reenrollmentRequired) false else _state.value.deviceRegistered,
                        error = message(error),
                    )
                },
            )
        }
    }

    /** issue 107: server-side lyrics / hymnal-number search used by universal search. */
    suspend fun searchLibrary(
        query: String,
        type: CatalogLibrarySearchType,
    ): Result<CatalogLibrarySearchResult> = repository.searchLibrary(query, type)

    /** issue 127: public-platform Chinese lyrics shown under the lyrics search category. */
    suspend fun searchExternalLyrics(query: String): Result<CatalogExternalLyricsSearchResult> =
        repository.searchExternalLyrics(query)

    suspend fun getExternalLyrics(
        source: CatalogExternalLyricsSource,
        externalId: String,
        title: String?,
    ): Result<CatalogExternalLyrics> = repository.getExternalLyrics(source, externalId, title)

    suspend fun listTagWorks(tagKey: String, cursor: String? = null): Result<CatalogTagWorksPage> =
        repository.listTagWorks(tagKey, cursor)

    fun cachedWorkBundle(workId: String): WorkBundle? = repository.cachedBundle(workId)

    /**
     * Bypasses the ETag so bundles cached before work lyrics/number_meta existed are replaced;
     * the repository still falls back to the cached bundle when the server is unreachable.
     */
    suspend fun loadWorkLyrics(workId: String): Result<WorkBundle> =
        repository.getWorkBundle(workId, forceRefresh = true)

    fun errorMessage(error: Throwable): String = message(error)

    fun closeWork() {
        _state.value = _state.value.copy(selectedBundle = null, error = null)
    }

    suspend fun playback(renditionId: String): Result<PlaybackDescriptor> = repository.getPlayback(renditionId)

    suspend fun scoreRevision(revisionId: String): Result<ScoreRevision> = repository.getScoreRevision(revisionId)

    suspend fun scoreHistory(headRevisionId: String): Result<List<ScoreRevision>> = runCatching {
        val revisions = mutableListOf<ScoreRevision>()
        val seen = mutableSetOf<String>()
        var next: String? = headRevisionId
        while (next != null) {
            check(seen.add(next)) { "谱面修订链出现循环" }
            val revision = repository.getScoreRevision(next).getOrElse { error ->
                if (revisions.isNotEmpty()) return@runCatching revisions
                throw error
            }
            revisions += revision
            next = revision.basedOnRevisionId
        }
        revisions
    }

    suspend fun scoreBytes(revision: ScoreRevision): Result<ByteArray> {
        val asset = revision.primaryMusicXml
            ?: return Result.failure(CatalogFailure.InvalidData("谱面修订没有 primary_musicxml"))
        return repository.downloadAsset(asset.assetId, asset.sha256, asset.byteSize)
    }

    suspend fun chorus(workId: String): Result<ChorusCatalog> = repository.getChorus(workId)

    suspend fun chorusProject(projectId: String): Result<ChorusProject> =
        repository.getChorusProject(projectId)

    suspend fun uploadChorusTrack(projectId: String, upload: ChorusTrackUpload): Result<ChorusTrack> =
        repository.uploadChorusTrack(projectId, upload)

    suspend fun alignChorusTrack(
        trackId: String,
        revision: Int,
        offsetMs: Long,
        anchors: List<ChorusSyncAnchor>,
    ): Result<ChorusTrack> = repository.updateChorusTrackAlignment(trackId, revision, offsetMs, anchors)

    suspend fun submitChorusTrack(trackId: String): Result<ChorusTrack> =
        repository.submitChorusTrack(trackId)

    suspend fun withdrawChorusTrack(trackId: String): Result<ChorusTrack> =
        repository.withdrawChorusTrack(trackId)

    suspend fun resolveChorusMix(
        projectId: String,
        chorusTimelineId: String,
        trackIds: List<String>,
    ): Result<ChorusMix> = repository.resolveChorusMix(projectId, chorusTimelineId, trackIds)

    suspend fun chorusMix(mixId: String): Result<ChorusMix> = repository.getChorusMix(mixId)

    suspend fun restoreQueue(deviceSongs: List<Song> = emptyList()): Result<RestoredUnifiedQueue?> {
        val record = queueStore.load() ?: return Result.success(null)
        if (!repository.connection().configured) return Result.success(null)
        return runCatching {
            val deviceSongsById = deviceSongs.associateBy(Song::id)
            if (deviceSongsById.isEmpty() && record.entries.any { it.isDevice() }) {
                // Most commonly the runtime audio permission has not been granted yet. Keep the
                // complete record intact and retry after MediaStore initialization.
                return@runCatching null
            }
            val restored = record.entries.mapIndexedNotNull { sourceIndex, saved ->
                if (saved.isDevice()) {
                    saved.deviceSongId?.let(deviceSongsById::get)?.let {
                        RestoredQueueItem(sourceIndex, it, null)
                    }
                } else {
                    val nowPlaying = saved.nowPlaying ?: return@mapIndexedNotNull null
                    val playback = CatalogPlaybackItem(
                        renditionId = nowPlaying.renditionId,
                        assetId = null,
                        title = saved.title ?: nowPlaying.title,
                        artist = saved.artist ?: nowPlaying.subtitle,
                        arrangementName = saved.arrangementName.orEmpty(),
                        playbackUrl = CatalogPlaybackPolicy.deferredUri(nowPlaying.renditionId),
                        cacheKey = null,
                        mediaType = "audio/mpeg",
                        durationMs = saved.durationMs,
                        albumId = saved.albumId,
                        artworkUrl = CatalogPlaybackPolicy.resolveAutomaticArtworkUrl(
                            saved.artworkUrl,
                            repository.connection().serverUrl,
                        ),
                    )
                    val entry = RhythmQueueEntry(
                        nowPlaying = nowPlaying.copy(assetId = null),
                        playback = playback,
                    )
                    val song = Song(
                        id = playback.toMediaItem().mediaId,
                        title = nowPlaying.title,
                        artist = nowPlaying.subtitle,
                        album = playback.arrangementName,
                        albumId = playback.albumId.ifBlank { nowPlaying.arrangementId },
                        duration = playback.durationMs,
                        uri = Uri.parse(playback.playbackUrl),
                        artworkUri = playback.artworkUrl?.let(Uri::parse),
                        codec = playback.mediaType,
                    )
                    RestoredQueueItem(sourceIndex, song, entry)
                }
            }
            if (restored.isEmpty()) return@runCatching null
            val currentIndex = restored.indexOfFirst { it.sourceIndex == record.currentIndex }
                .takeIf { it >= 0 }
                ?: restored.indexOfLast { it.sourceIndex < record.currentIndex }.coerceAtLeast(0)
            RestoredUnifiedQueue(
                songs = restored.map { it.song },
                catalogEntries = restored.mapNotNull { it.catalogEntry },
                currentIndex = currentIndex,
                positionMs = record.positionMs,
            )
        }
    }

    private fun message(error: Throwable): String = when (error) {
        is CatalogFailure.InvalidCredentials -> "音乐库登记已失效，请重新登记"
        is CatalogFailure.AdminInvalidCredentials -> getApplication<Application>()
            .getString(R.string.catalog_admin_invalid_credentials)
        else -> (error.message ?: "发生未知错误")
            .replace(Regex("\\bCatalog\\b", RegexOption.IGNORE_CASE), "音乐库")
    }

    private fun smartEnrollmentMessage(reason: CatalogSmartEnrollmentError): String =
        getApplication<Application>().getString(
            when (reason) {
                CatalogSmartEnrollmentError.INPUT_TOO_LONG -> R.string.catalog_smart_enrollment_input_too_long
                CatalogSmartEnrollmentError.MULTIPLE_TOKENS -> R.string.catalog_smart_enrollment_multiple
                CatalogSmartEnrollmentError.TOKEN_TOO_LONG -> R.string.catalog_smart_enrollment_token_too_long
                CatalogSmartEnrollmentError.TOKEN_MISSING,
                CatalogSmartEnrollmentError.INVALID_TOKEN,
                CatalogSmartEnrollmentError.UNSUPPORTED_ADDRESS,
                -> R.string.catalog_smart_enrollment_invalid
            },
        )
}
