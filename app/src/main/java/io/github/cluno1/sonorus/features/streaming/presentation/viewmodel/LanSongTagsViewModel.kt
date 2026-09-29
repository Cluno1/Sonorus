/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cluno1.sonorus.features.local.data.device.*
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualMetadataError
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualMetadataUiState
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualProviderStatus
import io.github.cluno1.sonorus.features.streaming.data.repository.StreamingMusicRepositoryImpl
import io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.shared.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

enum class LanSongTagsError { LOAD_FAILED, INVALID_FIELDS, SAVE_FAILED, ARTWORK_READ_FAILED, ARTWORK_TOO_LARGE, INVALID_TAG }

data class LanSongTagsUiState(
    val song: Song? = null,
    val fields: Map<String, String> = emptyMap(),
    val plainLyrics: String = "",
    val syncedLyrics: String = "",
    val lyricsSource: String = "",
    val lyricsExternalId: String = "",
    val artworkUrl: String? = null,
    val selectedArtwork: DeviceArtworkCandidate? = null,
    val localArtworkBytes: ByteArray? = null,
    val preparingArtwork: Boolean = false,
    val removeArtwork: Boolean = false,
    val tags: List<String> = emptyList(),
    val tagQuery: String = "",
    val tagSuggestions: List<String> = emptyList(),
    val loadingTagSuggestions: Boolean = false,
    val tagSuggestionsFailed: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: LanSongTagsError? = null,
    val saved: LanSongMetadata? = null,
)

class LanSongTagsViewModel(application: Application) : AndroidViewModel(application) {
    private val client = (StreamingMusicModule.provideStreamingMusicRepository(application) as StreamingMusicRepositoryImpl)
        .lanMetadataClient()
    private val publicMetadata = DeviceMetadataRepository(application, allowLanQueries = true)
    private val _state = MutableStateFlow(LanSongTagsUiState())
    val state = _state.asStateFlow()
    private val _searchState = MutableStateFlow(DeviceManualMetadataUiState())
    val searchState = _searchState.asStateFlow()
    private var original: LanSongMetadata? = null
    private var expectedServer = ""
    private var expectedGateway = ""
    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var artworkJob: Job? = null
    private var tagSuggestionsJob: Job? = null

    fun load(song: Song) {
        if (_state.value.song?.id == song.id && original != null) return
        if (loadJob?.isActive == true) return
        // Capture the song and gateway once. Advancing the playback queue never changes this target.
        expectedServer = client.getServerUrl()
        _state.value = LanSongTagsUiState(song = song)
        loadJob = viewModelScope.launch {
            try {
                val trackId = LanSubsonicPlaybackPolicy.trackIdFromMediaId(song.id) ?: error("Invalid LAN song")
                expectedGateway = client.getLanMetadataCapabilities(expectedServer).getOrThrow()
                val metadata = client.getLanSongMetadata(trackId, expectedServer, expectedGateway).getOrThrow()
                original = metadata
                _state.value = LanSongTagsUiState(
                    song = metadata.applyTo(song), fields = metadata.fields,
                    plainLyrics = metadata.plainLyrics, syncedLyrics = metadata.syncedLyrics,
                    lyricsSource = metadata.lyricsSource, lyricsExternalId = metadata.lyricsExternalId,
                    artworkUrl = metadata.artworkUrl, tags = metadata.tags, loading = false,
                )
                refreshTagSuggestions()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = LanSongTagsError.LOAD_FAILED)
            }
        }
    }

    fun setField(name: String, value: String) {
        if (_state.value.saving || name !in LanSongMetadata.editableFields) return
        _state.value = _state.value.copy(fields = _state.value.fields + (name to value), error = null)
    }

    fun setLyrics(plain: String? = null, synced: String? = null) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(
            plainLyrics = plain ?: _state.value.plainLyrics,
            syncedLyrics = synced ?: _state.value.syncedLyrics,
            lyricsSource = "Manual", lyricsExternalId = "", error = null,
        )
    }

    fun removeArtwork() {
        if (_state.value.saving) return
        artworkJob?.cancel()
        _state.value = _state.value.copy(selectedArtwork = null, localArtworkBytes = null,
            preparingArtwork = false, removeArtwork = true, error = null)
    }

    fun chooseLocalArtwork(uri: Uri) {
        if (_state.value.saving || _state.value.loading) return
        artworkJob?.cancel()
        _state.value = _state.value.copy(preparingArtwork = true, error = null)
        artworkJob = viewModelScope.launch {
            try {
                val bytes = publicMetadata.gatewayArtworkBytes(uri)
                _state.value = _state.value.copy(localArtworkBytes = bytes, selectedArtwork = null,
                    removeArtwork = false, preparingArtwork = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(preparingArtwork = false,
                    error = if (e is GatewayArtworkTooLargeException) LanSongTagsError.ARTWORK_TOO_LARGE
                        else LanSongTagsError.ARTWORK_READ_FAILED)
            }
        }
    }

    fun setTagQuery(value: String) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(tagQuery = value, error = null)
        refreshTagSuggestions()
    }

    fun addTag(value: String = _state.value.tagQuery) {
        if (_state.value.saving) return
        val name = Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        if (name.isBlank()) return
        val draft = _state.value
        val duplicate = draft.tags.any { it.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT) }
        if (name.length > 128 || name.any { it.code < 32 } || (!duplicate && draft.tags.size >= 64)) {
            _state.value = draft.copy(error = LanSongTagsError.INVALID_TAG)
            return
        }
        _state.value = draft.copy(tags = if (duplicate) draft.tags else draft.tags + name, tagQuery = "", error = null)
        refreshTagSuggestions()
    }

    fun removeTag(name: String) {
        if (_state.value.saving) return
        _state.value = _state.value.copy(tags = _state.value.tags.filterNot { it == name }, error = null)
    }

    fun refreshTagSuggestions() {
        tagSuggestionsJob?.cancel()
        if (_state.value.loading || expectedGateway.isBlank()) return
        val query = _state.value.tagQuery.trim()
        if (query.length > 128) {
            _state.value = _state.value.copy(loadingTagSuggestions = false, tagSuggestions = emptyList())
            return
        }
        _state.value = _state.value.copy(loadingTagSuggestions = true, tagSuggestionsFailed = false)
        tagSuggestionsJob = viewModelScope.launch {
            try {
                delay(250)
                val suggestions = client.getLanTagSuggestions(query, expectedServer, expectedGateway).getOrThrow()
                _state.value = _state.value.copy(tagSuggestions = suggestions, loadingTagSuggestions = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(tagSuggestions = emptyList(), loadingTagSuggestions = false,
                    tagSuggestionsFailed = true)
            }
        }
    }

    fun querySong(): Song? = _state.value.song?.copy(
        title = _state.value.fields["title"].orEmpty(),
        artist = _state.value.fields["artist"].orEmpty(),
        album = _state.value.fields["album"].orEmpty(),
    )

    fun startSearch(kind: DeviceManualMetadataKind) {
        searchJob?.cancel()
        _searchState.value = DeviceManualMetadataUiState(songId = _state.value.song?.id, kind = kind)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchState.value = DeviceManualMetadataUiState()
    }

    fun search(kind: DeviceManualMetadataKind, request: DeviceMetadataRequest, providers: Set<DevicePublicMetadataProvider>) {
        val song = querySong() ?: return
        searchJob?.cancel()
        val supported = when (kind) {
            DeviceManualMetadataKind.LYRICS -> setOf(DevicePublicMetadataProvider.LRCLIB)
            DeviceManualMetadataKind.ARTWORK, DeviceManualMetadataKind.DETAILS -> setOf(
                DevicePublicMetadataProvider.MUSICBRAINZ_CAA, DevicePublicMetadataProvider.DEEZER,
                DevicePublicMetadataProvider.ITUNES,
            )
            DeviceManualMetadataKind.ARTIST_ARTWORK -> emptySet()
        }
        val selected = providers.intersect(supported)
        if (request.title.isBlank() || selected.isEmpty()) {
            _searchState.value = DeviceManualMetadataUiState(songId = song.id, kind = kind,
                error = if (request.title.isBlank()) DeviceManualMetadataError.TITLE_REQUIRED else DeviceManualMetadataError.PROVIDER_REQUIRED)
            return
        }
        _searchState.value = DeviceManualMetadataUiState(songId = song.id, kind = kind, isSearching = true,
            providerStatuses = selected.associateWith { DeviceManualProviderStatus.LOADING })
        searchJob = viewModelScope.launch {
            try {
                coroutineScope {
                    when (kind) {
                        DeviceManualMetadataKind.LYRICS -> {
                            val result = publicMetadata.searchLyricsResult(song, request)
                            _searchState.value = _searchState.value.copy(lyricsCandidates = result.candidates,
                                providerStatuses = mapOf(result.provider to status(result)))
                        }
                        DeviceManualMetadataKind.ARTWORK -> {
                            val results = selected.map { provider -> async { publicMetadata.searchArtworkResult(song, request, provider) } }.awaitAll()
                            _searchState.value = _searchState.value.copy(
                                artworkCandidates = results.flatMap { it.candidates }.sortedByDescending { it.confidence },
                                providerStatuses = results.associate { it.provider to status(it) })
                        }
                        DeviceManualMetadataKind.DETAILS -> {
                            val results = selected.map { provider -> async { publicMetadata.searchDetailsResult(song, request, provider) } }.awaitAll()
                            _searchState.value = _searchState.value.copy(
                                detailsCandidates = results.flatMap { it.candidates }.sortedByDescending { it.confidence },
                                providerStatuses = results.associate { it.provider to status(it) })
                        }
                        DeviceManualMetadataKind.ARTIST_ARTWORK -> Unit
                    }
                }
                _searchState.value = _searchState.value.copy(isSearching = false, hasSearched = true)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _searchState.value = _searchState.value.copy(isSearching = false, hasSearched = true,
                    providerStatuses = selected.associateWith { DeviceManualProviderStatus.FAILED },
                    error = DeviceManualMetadataError.REQUEST_FAILED)
            }
        }
    }

    private fun status(result: DeviceProviderSearchResult<*>) = when {
        result.failed -> DeviceManualProviderStatus.FAILED
        result.candidates.isEmpty() -> DeviceManualProviderStatus.EMPTY
        else -> DeviceManualProviderStatus.SUCCESS
    }

    fun chooseLyrics(candidate: DeviceLyricsCandidate) {
        _state.value = _state.value.copy(plainLyrics = candidate.lyrics.plainLyrics.orEmpty(),
            syncedLyrics = candidate.lyrics.syncedLyrics.orEmpty(), lyricsSource = candidate.lyrics.source ?: "LRCLIB",
            lyricsExternalId = candidate.externalId, error = null)
        _searchState.value = _searchState.value.copy(applied = true)
    }

    fun chooseArtwork(candidate: DeviceArtworkCandidate) {
        if (_state.value.saving) return
        artworkJob?.cancel()
        _state.value = _state.value.copy(selectedArtwork = candidate, localArtworkBytes = null,
            preparingArtwork = false, removeArtwork = false, error = null)
        _searchState.value = _searchState.value.copy(applied = true)
    }

    fun chooseDetails(candidate: DeviceDetailsCandidate, selected: Set<DeviceDetailsField>) {
        val fields = buildMap<String, String> {
            if (DeviceDetailsField.TITLE in selected) put("title", candidate.title)
            if (DeviceDetailsField.ARTIST in selected) put("artist", candidate.artist)
            if (DeviceDetailsField.ALBUM in selected) put("album", candidate.album)
            if (DeviceDetailsField.ALBUM_ARTIST in selected) put("albumArtist", candidate.albumArtist.orEmpty())
            if (DeviceDetailsField.GENRE in selected) put("genre", candidate.genre.orEmpty())
            if (DeviceDetailsField.YEAR in selected) put("year", candidate.year?.toString().orEmpty())
            if (DeviceDetailsField.TRACK_NUMBER in selected) put("trackNumber", candidate.trackNumber?.toString().orEmpty())
            if (DeviceDetailsField.DISC_NUMBER in selected) put("discNumber", candidate.discNumber?.toString().orEmpty())
        }
        _state.value = _state.value.copy(fields = _state.value.fields + fields, error = null)
        _searchState.value = _searchState.value.copy(applied = true)
    }

    fun save() {
        val baseline = original ?: return
        val draft = _state.value
        if (draft.loading || draft.saving || draft.preparingArtwork) return
        val payload = try {
            val fields = JSONObject()
            draft.fields.forEach { (name, raw) ->
                if (name in LanSongMetadata.numberFields) {
                    val number = if (raw.isBlank()) 0 else raw.trim().toInt()
                    require(number in 0..(if (name == "discNumber") 999 else 9999))
                    if (number != baseline.fields[name]?.toIntOrNull()) fields.put(name, number)
                } else {
                    val value = raw.trim()
                    val limit = when (name) { "genre" -> 256; "language", "version" -> 64; else -> 1024 }
                    require(value.length <= limit && '\u0000' !in value && (name != "title" || value.isNotEmpty()))
                    if (value != baseline.fields[name]) fields.put(name, value)
                }
            }
            JSONObject().put("fields", fields).apply {
                if (draft.plainLyrics != baseline.plainLyrics || draft.syncedLyrics != baseline.syncedLyrics ||
                    draft.lyricsExternalId != baseline.lyricsExternalId) {
                    val lyrics = JSONObject().put("plain", draft.plainLyrics).put("synced", draft.syncedLyrics)
                        .put("source", draft.lyricsSource).put("externalId", draft.lyricsExternalId)
                    require(lyrics.toString().toByteArray(Charsets.UTF_8).size <= 512 * 1024)
                    put("lyrics", lyrics)
                }
                if (draft.removeArtwork) put("removeArtwork", true)
                if (draft.tags.toSet() != baseline.tags.toSet()) put("tags", JSONArray(draft.tags))
            }
        } catch (e: Exception) {
            _state.value = draft.copy(error = LanSongTagsError.INVALID_FIELDS)
            return
        }
        _state.value = draft.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                check(client.getLanMetadataCapabilities(expectedServer).getOrThrow() == expectedGateway)
                val artwork = draft.localArtworkBytes ?: draft.selectedArtwork?.let { publicMetadata.gatewayArtworkBytes(it) }
                val saved = client.saveLanSongMetadata(baseline.trackId, expectedServer, payload, artwork, expectedGateway).getOrThrow()
                _state.value = _state.value.copy(saving = false, saved = saved)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = LanSongTagsError.SAVE_FAILED)
            }
        }
    }
}
