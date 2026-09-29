/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.cluno1.sonorus.features.streaming.data.LanMusicUploadFiles
import io.github.cluno1.sonorus.features.streaming.data.LocalMusicUploadFile
import io.github.cluno1.sonorus.features.streaming.data.repository.StreamingMusicRepositoryImpl
import io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadCapabilities
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadError
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class LocalMusicUploadStatus { READY, PREPARING, UPLOADING, UPLOADED, DUPLICATE, FAILED, CANCELLED }

data class LocalMusicUploadItem(
    val file: LocalMusicUploadFile,
    val status: LocalMusicUploadStatus = LocalMusicUploadStatus.READY,
    val progress: Float = 0f,
    val error: LanMusicUploadError? = null,
)

data class LanMusicUploadUiState(
    val loading: Boolean = true,
    val selecting: Boolean = false,
    val running: Boolean = false,
    val gatewayUrl: String = "",
    val capabilities: LanMusicUploadCapabilities? = null,
    val files: List<LocalMusicUploadItem> = emptyList(),
    val error: LanMusicUploadError? = null,
    val libraryVersion: Int = 0,
)

class LanMusicUploadViewModel(application: Application) : AndroidViewModel(application) {
    private val client = (StreamingMusicModule.provideStreamingMusicRepository(application) as StreamingMusicRepositoryImpl)
        .lanMetadataClient()
    private val _state = MutableStateFlow(LanMusicUploadUiState())
    val state = _state.asStateFlow()
    private var expectedServer: String? = null
    private var loadJob: Job? = null
    private var uploadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true || _state.value.capabilities != null) return
        val server = expectedServer ?: client.getServerUrl().also { expectedServer = it }
        _state.update { it.copy(loading = true, error = null, gatewayUrl = server) }
        loadJob = viewModelScope.launch {
            try {
                if (server.isBlank() || client.getServerUrl() != server) throw LanMusicUploadException(LanMusicUploadError.CONNECTION_CHANGED)
                val capabilities = client.getLanMusicUploadCapabilities(server).getOrThrow()
                _state.update { it.copy(loading = false, capabilities = capabilities) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = (e as? LanMusicUploadException)?.reason ?: LanMusicUploadError.UNAVAILABLE) }
            }
        }
    }

    fun addFiles(uris: List<Uri>) {
        val capabilities = _state.value.capabilities ?: return
        if (_state.value.running || _state.value.selecting || uris.isEmpty()) return
        _state.update { it.copy(selecting = true) }
        viewModelScope.launch {
            try {
                val selected = withContext(Dispatchers.IO) {
                    uris.distinct().map { uri ->
                        val file = LanMusicUploadFiles.describe(getApplication(), uri)
                        val error = validate(file, capabilities)
                        LocalMusicUploadItem(file, if (error == null) LocalMusicUploadStatus.READY else LocalMusicUploadStatus.FAILED, error = error)
                    }
                }
                _state.update { it.copy(files = (it.files + selected).distinctBy { item -> item.file.uri }, selecting = false) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                _state.update { it.copy(selecting = false, error = LanMusicUploadError.READ_FILE) }
            }
        }
    }

    fun remove(uri: Uri) {
        if (!_state.value.running) _state.update { it.copy(files = it.files.filterNot { item -> item.file.uri == uri }) }
    }

    fun start() {
        val draft = _state.value
        val capabilities = draft.capabilities ?: return
        val server = expectedServer ?: return
        if (draft.running || draft.selecting) return
        val pending = draft.files.filter { it.status in setOf(LocalMusicUploadStatus.READY, LocalMusicUploadStatus.FAILED, LocalMusicUploadStatus.CANCELLED) }
        if (pending.isEmpty()) return
        _state.update { it.copy(running = true, error = null) }
        uploadJob = viewModelScope.launch {
            var published = false
            try {
                if (client.getServerUrl() != server) throw LanMusicUploadException(LanMusicUploadError.CONNECTION_CHANGED)
                val current = client.getLanMusicUploadCapabilities(server).getOrThrow()
                if (current.gatewayId != capabilities.gatewayId) throw LanMusicUploadException(LanMusicUploadError.CONNECTION_CHANGED)
                val batchJob = currentCoroutineContext().job
                for (item in pending) {
                    currentCoroutineContext().ensureActive()
                    val uri = item.file.uri
                    try {
                        validate(item.file, current)?.let { throw LanMusicUploadException(it) }
                        updateFile(uri) { it.copy(status = LocalMusicUploadStatus.PREPARING, progress = 0f, error = null) }
                        val result = LanMusicUploadFiles.withBody(
                            getApplication(), item.file, current.maxBytes,
                            onProgress = { sent, total ->
                                if (batchJob.isActive) updateFile(uri) { it.copy(progress = sent.toFloat() / total) }
                            },
                        ) { body, size ->
                            updateFile(uri) { it.copy(file = it.file.copy(size = size), status = LocalMusicUploadStatus.UPLOADING) }
                            client.uploadLanMusic(server, current.gatewayId, item.file.name, body).getOrThrow()
                        }
                        published = true
                        updateFile(uri) { it.copy(status = if (result.duplicate) LocalMusicUploadStatus.DUPLICATE else LocalMusicUploadStatus.UPLOADED, progress = 1f) }
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) {
                        updateFile(uri) { it.copy(status = LocalMusicUploadStatus.FAILED,
                            error = (e as? LanMusicUploadException)?.reason ?: LanMusicUploadError.REQUEST_FAILED) }
                    }
                }
            } catch (e: CancellationException) {
                _state.update { state -> state.copy(files = state.files.map { item ->
                    if (item.status in setOf(LocalMusicUploadStatus.PREPARING, LocalMusicUploadStatus.UPLOADING)) item.copy(status = LocalMusicUploadStatus.CANCELLED) else item
                }) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = (e as? LanMusicUploadException)?.reason ?: LanMusicUploadError.REQUEST_FAILED) }
            } finally {
                _state.update { it.copy(running = false, libraryVersion = it.libraryVersion + if (published) 1 else 0) }
            }
        }
    }

    fun cancel() { uploadJob?.cancel() }

    private fun updateFile(uri: Uri, transform: (LocalMusicUploadItem) -> LocalMusicUploadItem) {
        _state.update { state -> state.copy(files = state.files.map { if (it.file.uri == uri) transform(it) else it }) }
    }

    private fun validate(file: LocalMusicUploadFile, capabilities: LanMusicUploadCapabilities): LanMusicUploadError? = when {
        file.name.isBlank() || file.name.length > 255 || file.name.any { it.code < 32 } -> LanMusicUploadError.READ_FILE
        file.name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in capabilities.extensions -> LanMusicUploadError.UNSUPPORTED_FORMAT
        file.size != null && file.size <= 0 -> LanMusicUploadError.READ_FILE
        file.size != null && file.size > capabilities.maxBytes -> LanMusicUploadError.TOO_LARGE
        else -> null
    }
}
