package io.github.cluno1.sonorus.features.clientimages.presentation

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import io.github.cluno1.sonorus.features.clientimages.background.ClientImageTransferScheduler
import io.github.cluno1.sonorus.features.clientimages.data.ClientImageRepository
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferBatchEntity
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageCapabilities
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageDelivery
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageRecord
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageVisibility
import io.github.cluno1.sonorus.features.clientimages.domain.SelectedClientImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientImageLabsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientImageRepository.get(application)

    private val _selected = MutableStateFlow<List<SelectedClientImage>>(emptyList())
    val selected = _selected.asStateFlow()

    private val _capabilities = MutableStateFlow<ClientImageCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()

    private val _visibility = MutableStateFlow<ClientImageVisibility?>(null)
    val visibility = _visibility.asStateFlow()

    private val _administratorViewAvailable = MutableStateFlow(false)
    val administratorViewAvailable = _administratorViewAvailable.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _deliveries = MutableStateFlow<Map<DeliveryKey, ClientImageDelivery>>(emptyMap())
    val deliveries = _deliveries.asStateFlow()

    private val _preview = MutableStateFlow<ClientImagePreview?>(null)
    val preview = _preview.asStateFlow()

    private val _legacyDownload = MutableStateFlow<ClientImageRecord?>(null)
    val legacyDownload = _legacyDownload.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val pendingDeliveries = linkedSetOf<DeliveryKey>()
    private var deliveryJob: Job? = null

    val batches = flow {
        emitAll(repository.observeBatches())
    }.catch {
        emit(emptyList<ClientImageTransferBatchEntity>())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownImages = repository.ownPager().flow.cachedIn(viewModelScope)
    val sharedImages = repository.sharedPager().flow.cachedIn(viewModelScope)

    init {
        refreshConfiguration()
    }

    fun refreshConfiguration() {
        viewModelScope.launch {
            runCatching {
                _capabilities.value = repository.capabilities()
                _visibility.value = repository.visibility()
                _administratorViewAvailable.value = repository.canReadSharedImages()
            }.onFailure { _messages.tryEmit(it.userMessage()) }
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val known = _selected.value.mapTo(mutableSetOf()) { it.uri }
                val additions = uris.distinct().filterNot(known::contains).map { uri ->
                    takeReadPermission(uri)
                    repository.inspectSelection(uri)
                }
                setSelection(_selected.value + additions)
            } catch (error: Throwable) {
                _messages.emit(error.userMessage())
            } finally {
                _busy.value = false
            }
        }
    }

    fun addTree(treeUri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                takeReadPermission(treeUri)
                val additions = repository.inspectTree(treeUri)
                val known = _selected.value.mapTo(mutableSetOf()) { it.uri }
                setSelection(_selected.value + additions.filterNot { it.uri in known })
            } catch (error: Throwable) {
                _messages.emit(error.userMessage())
            } finally {
                _busy.value = false
            }
        }
    }

    fun removeSelected(uri: Uri) {
        _selected.value = _selected.value.filterNot { it.uri == uri }
    }

    fun clearSelection() {
        _selected.value = emptyList()
    }

    fun startUpload(wifiOnly: Boolean) {
        val selection = _selected.value
        if (selection.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val batchId = repository.enqueue(selection, wifiOnly)
                _selected.value = emptyList()
                ClientImageTransferScheduler.start(getApplication(), batchId)
                _messages.emit("已加入后台上传队列")
            } catch (error: Throwable) {
                _messages.emit(error.userMessage())
            } finally {
                _busy.value = false
            }
        }
    }

    fun pauseBatch(batchId: String) {
        viewModelScope.launch {
            repository.pause(batchId)
            ClientImageTransferScheduler.pause(getApplication(), batchId)
        }
    }

    fun resumeBatch(batchId: String) {
        viewModelScope.launch {
            repository.resume(batchId)
            ClientImageTransferScheduler.start(getApplication(), batchId)
        }
    }

    fun cancelBatch(batchId: String) {
        viewModelScope.launch {
            ClientImageTransferScheduler.pause(getApplication(), batchId)
            repository.cancel(batchId)
        }
    }

    fun updateVisibility(enabled: Boolean) {
        val current = _visibility.value ?: return
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.updateVisibility(enabled, current.revision) }
                .onSuccess { _visibility.value = it }
                .onFailure { _messages.emit(it.userMessage()) }
            _busy.value = false
        }
    }

    fun requestThumbnail(imageId: String, shared: Boolean, force: Boolean = false) {
        val key = DeliveryKey(imageId, "thumbnail_512", shared)
        if (!force && _deliveries.value.containsKey(key)) return
        if (force) _deliveries.value -= key
        pendingDeliveries += key
        if (deliveryJob?.isActive == true) return
        deliveryJob = viewModelScope.launch {
            delay(100)
            while (pendingDeliveries.isNotEmpty()) {
                val requested = pendingDeliveries.toList()
                pendingDeliveries.clear()
                requested.groupBy { it.shared to it.variant }.forEach { (group, keys) ->
                    val limit = _capabilities.value?.maxThumbnailDeliveries ?: 100
                    keys.chunked(limit.coerceAtLeast(1)).forEach { chunk ->
                        runCatching {
                            repository.thumbnailDeliveries(
                                chunk.map(DeliveryKey::imageId),
                                group.second,
                                group.first,
                            )
                        }.onSuccess { descriptors ->
                            val updates = descriptors.associateBy { DeliveryKey(it.imageId, it.variant, group.first) }
                            _deliveries.value += updates
                        }
                    }
                }
                if (pendingDeliveries.isNotEmpty()) delay(100)
            }
        }
    }

    fun revalidateSharedAccess(onStillAvailable: () -> Unit) {
        viewModelScope.launch {
            val available = runCatching { repository.canReadSharedImages() }
                .onFailure { _messages.emit(it.userMessage()) }
                .getOrDefault(false)
            _administratorViewAvailable.value = available
            _deliveries.value = _deliveries.value.filterKeys { !it.shared }
            if (_preview.value?.shared == true) _preview.value = null
            if (available) onStillAvailable()
        }
    }

    fun openPreview(record: ClientImageRecord, shared: Boolean) {
        viewModelScope.launch {
            runCatching { repository.delivery(record.id, "preview", shared) }
                .onSuccess { _preview.value = ClientImagePreview(record, it, shared) }
                .onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun closePreview() {
        _preview.value = null
    }

    fun delete(record: ClientImageRecord, onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(record.id) }
                .onSuccess {
                    _preview.value = null
                    _messages.emit("图片已删除")
                    onComplete()
                }
                .onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun download(record: ClientImageRecord) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _legacyDownload.value = record
            return
        }
        viewModelScope.launch {
            runCatching { repository.downloadToPictures(record) }
                .onSuccess { _messages.emit("已保存到 Pictures/Sonorus") }
                .onFailure { _messages.emit(it.userMessage()) }
        }
    }

    fun completeLegacyDownload(target: Uri?) {
        val record = _legacyDownload.value ?: return
        _legacyDownload.value = null
        if (target == null) return
        viewModelScope.launch {
            runCatching { repository.downloadToUri(record, target) }
                .onSuccess { _messages.emit("图片已下载") }
                .onFailure { _messages.emit(it.userMessage()) }
        }
    }

    private suspend fun setSelection(items: List<SelectedClientImage>) {
        val limit = _capabilities.value?.maxBatchItems ?: 5000
        if (items.size > limit) {
            _selected.value = items.take(limit)
            _messages.emit("单批最多选择 $limit 张，已保留前 $limit 张")
        } else {
            _selected.value = items
        }
    }

    private fun takeReadPermission(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

data class DeliveryKey(
    val imageId: String,
    val variant: String,
    val shared: Boolean,
)

data class ClientImagePreview(
    val record: ClientImageRecord,
    val delivery: ClientImageDelivery,
    val shared: Boolean,
)

private fun Throwable.userMessage(): String =
    message?.takeIf(String::isNotBlank) ?: "图片操作失败，请稍后重试"
