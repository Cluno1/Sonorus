package io.github.cluno1.sonorus.features.clientimages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogApiClient
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageBatchCreateDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageCapabilitiesDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageDeliveryDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageObjectDeclarationDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageRecordDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageSettingsPatchDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageThumbnailRequestDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageUploadCreateDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ClientImageVisibilityPatchDto
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferBatchEntity
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferDatabase
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferItemEntity
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferState
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageBatchProgress
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageApiException
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageCapabilities
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageDelivery
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageRecord
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageVisibility
import io.github.cluno1.sonorus.features.clientimages.domain.SelectedClientImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

class ClientImageRepository private constructor(private val context: Context) {
    private val credentials = CatalogCredentialsStore(context)
    private val dao = ClientImageTransferDatabase.get(context).transfers()
    private val preparer = ClientImagePreparer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val batchLocks = ConcurrentHashMap<String, Mutex>()

    fun currentIdentity(): Pair<String, String>? = credentials.loadDevice()?.let {
        it.userId to it.serverUrl
    }

    fun observeBatches(): Flow<List<ClientImageTransferBatchEntity>> {
        val (owner, origin) = requireIdentity()
        return dao.observeBatches(owner, origin)
    }

    fun observeItems(batchLocalId: String): Flow<List<ClientImageTransferItemEntity>> =
        dao.observeItems(batchLocalId)

    suspend fun capabilities(): ClientImageCapabilities = api().imageApi.capabilities()
        .bodyOrThrow("读取图片功能配置失败")
        .toDomain()

    suspend fun inspectSelection(uri: Uri): SelectedClientImage = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "image" }
        var size: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    displayName = cursor.getString(it)?.takeIf(String::isNotBlank) ?: displayName
                }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                    if (!cursor.isNull(it)) size = cursor.getLong(it).takeIf { value -> value >= 0 }
                }
            }
        }
        SelectedClientImage(
            uri = uri,
            displayName = displayName.take(500),
            mediaType = context.contentResolver.getType(uri),
            byteSize = size,
        )
    }

    suspend fun inspectTree(treeUri: Uri, hardLimit: Int = 5000): List<SelectedClientImage> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val pending = ArrayDeque<String>().apply { add(rootId) }
            val result = mutableListOf<SelectedClientImage>()
            while (pending.isNotEmpty() && result.size < hardLimit) {
                val parentId = pending.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val typeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext() && result.size < hardLimit) {
                        val id = cursor.getString(idIndex)
                        val type = cursor.getString(typeIndex)
                        if (type == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(id)
                        } else if (type in SUPPORTED_SELECTION_TYPES || type?.startsWith("image/") == true) {
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            result += SelectedClientImage(
                                uri = uri,
                                displayName = cursor.getString(nameIndex)?.take(500) ?: "image",
                                mediaType = type,
                                byteSize = if (cursor.isNull(sizeIndex)) null else cursor.getLong(sizeIndex),
                            )
                        }
                    }
                }
            }
            result
        }

    suspend fun enqueue(
        selected: List<SelectedClientImage>,
        wifiOnly: Boolean,
    ): String {
        require(selected.isNotEmpty()) { "没有选择图片" }
        val capabilities = capabilities()
        require(capabilities.enabled) { capabilities.unavailableReason ?: "服务器未启用图片上传" }
        require(selected.size <= capabilities.maxBatchItems) {
            "单批最多 ${capabilities.maxBatchItems} 张图片"
        }
        selected.forEach { item ->
            item.byteSize?.let { require(it <= capabilities.maxImageBytes) { "${item.displayName} 超过大小限制" } }
        }
        val (owner, origin) = requireIdentity()
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val batch = ClientImageTransferBatchEntity(
            localId = batchId,
            ownerUserId = owner,
            serverOrigin = origin,
            serverBatchId = null,
            state = ClientImageTransferState.QUEUED,
            wifiOnly = wifiOnly,
            totalCount = selected.size,
            totalBytes = selected.sumOf { it.byteSize ?: 0L },
            succeededCount = 0,
            failedCount = 0,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val items = selected.mapIndexed { index, image ->
            ClientImageTransferItemEntity(
                localId = UUID.randomUUID().toString(),
                batchLocalId = batchId,
                ownerUserId = owner,
                sourceUri = image.uri.toString(),
                displayName = image.displayName,
                sourceMediaType = image.mediaType,
                sourceByteSize = image.byteSize,
                preparedPath = null,
                mediaType = null,
                byteSize = null,
                width = null,
                height = null,
                contentMd5 = null,
                sha256 = null,
                thumbnailPreparedPath = null,
                thumbnailMediaType = null,
                thumbnailByteSize = null,
                thumbnailWidth = null,
                thumbnailHeight = null,
                thumbnailContentMd5 = null,
                thumbnailSha256 = null,
                serverUploadId = null,
                imageId = null,
                assetId = null,
                transferredBytes = 0,
                state = ClientImageTransferState.QUEUED,
                retryCount = 0,
                errorCode = null,
                createdAtEpochMs = now + index,
                updatedAtEpochMs = now,
            )
        }
        dao.insertBatchWithItems(batch, items)
        return batchId
    }

    suspend fun batch(batchLocalId: String): ClientImageTransferBatchEntity? = dao.batch(batchLocalId)

    suspend fun runnableItems(batchLocalId: String): List<ClientImageTransferItemEntity> =
        dao.runnableItems(batchLocalId)

    suspend fun progress(batchLocalId: String): ClientImageBatchProgress = dao.progress(batchLocalId)

    suspend fun uploadItem(
        batchLocalId: String,
        itemId: String,
        capabilities: ClientImageCapabilities,
    ) {
        var item = dao.item(itemId) ?: return
        if (item.state !in ClientImageTransferState.runnable) return
        try {
            val preparedFile = item.preparedPath?.let(::File)?.takeIf(File::isFile)
            val preparedThumbnail = item.thumbnailPreparedPath?.let(::File)?.takeIf(File::isFile)
            if (preparedFile == null || preparedThumbnail == null || listOf(
                    item.mediaType,
                    item.byteSize,
                    item.width,
                    item.height,
                    item.contentMd5,
                    item.sha256,
                    item.thumbnailMediaType,
                    item.thumbnailByteSize,
                    item.thumbnailWidth,
                    item.thumbnailHeight,
                    item.thumbnailContentMd5,
                    item.thumbnailSha256,
                ).any { it == null }
            ) {
                dao.setItemState(itemId, ClientImageTransferState.PREPARING, null, 0, now())
                val prepared = preparer.prepare(
                    itemId,
                    Uri.parse(item.sourceUri),
                    capabilities.maxImageBytes,
                    capabilities.maxImagePixels,
                )
                dao.setPrepared(
                    itemId = itemId,
                    preparedPath = prepared.file.absolutePath,
                    mediaType = prepared.mediaType,
                    byteSize = prepared.byteSize,
                    width = prepared.width,
                    height = prepared.height,
                    contentMd5 = prepared.contentMd5,
                    sha256 = prepared.sha256,
                    thumbnailPreparedPath = prepared.thumbnailFile.absolutePath,
                    thumbnailMediaType = prepared.thumbnailMediaType,
                    thumbnailByteSize = prepared.thumbnailByteSize,
                    thumbnailWidth = prepared.thumbnailWidth,
                    thumbnailHeight = prepared.thumbnailHeight,
                    thumbnailContentMd5 = prepared.thumbnailContentMd5,
                    thumbnailSha256 = prepared.thumbnailSha256,
                    now = now(),
                )
                item = requireNotNull(dao.item(itemId))
            }

            val batch = ensureServerBatch(batchLocalId)
            dao.setItemState(itemId, ClientImageTransferState.CREATING, null, 0, now())
            val upload = if (item.serverUploadId == null) {
                api().imageApi.createUpload(
                    idempotencyKey = "image-create-${item.localId}",
                    body = ClientImageUploadCreateDto(
                        batchId = batch,
                        clientItemId = item.localId,
                        displayName = item.displayName,
                        mediaType = requireNotNull(item.mediaType),
                        byteSize = requireNotNull(item.byteSize),
                        contentMd5 = requireNotNull(item.contentMd5),
                        clientSha256 = requireNotNull(item.sha256),
                        width = requireNotNull(item.width),
                        height = requireNotNull(item.height),
                        thumbnail512 = ClientImageObjectDeclarationDto(
                            mediaType = requireNotNull(item.thumbnailMediaType),
                            byteSize = requireNotNull(item.thumbnailByteSize),
                            contentMd5 = requireNotNull(item.thumbnailContentMd5),
                            clientSha256 = requireNotNull(item.thumbnailSha256),
                            width = requireNotNull(item.thumbnailWidth),
                            height = requireNotNull(item.thumbnailHeight),
                        ),
                    ),
                ).bodyOrThrow("创建图片上传失败")
            } else {
                api().imageApi.refreshUpload(
                    item.serverUploadId,
                    "image-refresh-${item.localId}-${item.retryCount}",
                ).bodyOrThrow("刷新图片上传凭据失败")
            }
            dao.bindUpload(
                itemId,
                upload.uploadId,
                upload.imageId,
                if (upload.state == "completed") ClientImageTransferState.COMPLETED else ClientImageTransferState.UPLOADING,
                now(),
            )
            if (upload.state == "completed" && upload.assetId != null) {
                dao.complete(itemId, upload.imageId, upload.assetId, now())
                cleanupPrepared(item)
                return
            }
            if (upload.state == "cancelled") {
                dao.setItemState(itemId, ClientImageTransferState.CANCELLED, null, 0, now())
                cleanupPrepared(item)
                return
            }
            if (upload.state == "rejected") {
                dao.setItemState(
                    itemId,
                    ClientImageTransferState.FAILED,
                    "服务器已拒绝这张图片",
                    0,
                    now(),
                )
                cleanupPrepared(item)
                return
            }
            val target = upload.upload ?: throw IOException("没有获取到图片上传地址")
            val thumbnailTarget = upload.thumbnailUpload
                ?: throw IOException("没有获取到图片预览地址")
            val file = File(requireNotNull(item.preparedPath))
            val thumbnailFile = File(requireNotNull(item.thumbnailPreparedPath))
            var lastPublished = 0L
            api().uploadImageToSignedUrl(target.url, file, target.requiredHeaders) { sent, total ->
                if (sent == total || sent - lastPublished >= 256 * 1024) {
                    lastPublished = sent
                    scope.launch { dao.setProgress(itemId, sent, now()) }
                }
            }
            api().uploadImageToSignedUrl(
                thumbnailTarget.url,
                thumbnailFile,
                thumbnailTarget.requiredHeaders,
            ) { _, _ -> }
            dao.setItemState(itemId, ClientImageTransferState.VERIFYING, null, 0, now())
            val record = api().imageApi.completeUpload(
                upload.uploadId,
                "image-complete-${item.localId}",
            ).bodyOrThrow("服务器校验图片失败")
            dao.complete(itemId, record.id, record.assetId, now())
            cleanupPrepared(item)
        } catch (error: Throwable) {
            val latest = dao.item(itemId)
            if (error is CancellationException && latest?.state in setOf(
                    ClientImageTransferState.PAUSED,
                    ClientImageTransferState.CANCELLED,
                )
            ) {
                throw error
            }
            val permanent = error is InvalidClientImage ||
                (error is ClientImageApiException && error.statusCode in 400..499 && error.statusCode !in setOf(408, 409, 429))
            val exhausted = (latest?.retryCount ?: item.retryCount) >= MAX_RETRIES
            dao.setItemState(
                itemId = itemId,
                state = if (permanent || exhausted) ClientImageTransferState.FAILED else ClientImageTransferState.RETRYABLE,
                errorCode = error.message?.take(300) ?: error.javaClass.simpleName,
                retryIncrement = if (permanent) 0 else 1,
                now = now(),
            )
            if (permanent || exhausted) cleanupPrepared(latest ?: item)
            throw error
        } finally {
            refreshLocalBatch(batchLocalId)
        }
    }

    suspend fun pause(batchLocalId: String) {
        dao.pauseItems(batchLocalId, now())
        dao.setBatchState(batchLocalId, ClientImageTransferState.PAUSED, now())
    }

    suspend fun resume(batchLocalId: String) {
        dao.resumeItems(batchLocalId, now())
        dao.setBatchState(batchLocalId, ClientImageTransferState.QUEUED, now())
    }

    suspend fun cancel(batchLocalId: String) {
        val batch = dao.batch(batchLocalId) ?: return
        val items = dao.items(batchLocalId)
        batch.serverBatchId?.let { remoteId ->
            runCatching {
                api().imageApi.cancelBatch(remoteId, "image-batch-cancel-$batchLocalId")
                    .bodyOrThrow("取消服务端批次失败")
            }
        }
        dao.cancelItems(batchLocalId, now())
        dao.setBatchState(batchLocalId, ClientImageTransferState.CANCELLED, now())
        items.forEach(::cleanupPrepared)
    }

    fun ownPager(): Pager<String, ClientImageRecord> = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 8, enablePlaceholders = false),
    ) { ClientImagePagingSource(shared = false) }

    fun sharedPager(): Pager<String, ClientImageRecord> = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 8, enablePlaceholders = false),
    ) { ClientImagePagingSource(shared = true) }

    suspend fun canReadSharedImages(): Boolean = try {
        api().imageApi.sharedImages(limit = 1).bodyOrThrow("读取共享图片失败")
        true
    } catch (error: ClientImageApiException) {
        if (error.statusCode == 401 || error.statusCode == 403) false else throw error
    }

    suspend fun updateMaxImageBytes(maxImageBytes: Long): Long {
        require(maxImageBytes in MIN_ADMIN_IMAGE_BYTES..MAX_ADMIN_IMAGE_BYTES) {
            "单张图片上限必须在 1–500 MB 之间"
        }
        return api().imageApi.updateSettings(ClientImageSettingsPatchDto(maxImageBytes))
            .bodyOrThrow("更新图片大小上限失败")
            .maxImageBytes
    }

    suspend fun thumbnailDeliveries(
        imageIds: List<String>,
        variant: String,
        shared: Boolean,
    ): List<ClientImageDelivery> {
        if (imageIds.isEmpty()) return emptyList()
        val request = ClientImageThumbnailRequestDto(imageIds.distinct(), variant)
        val response = if (shared) {
            api().imageApi.sharedThumbnailDeliveries(request)
        } else {
            api().imageApi.thumbnailDeliveries(request)
        }.bodyOrThrow("读取图片预览地址失败")
        return response.items.mapNotNull { it.delivery?.toDomain() }
    }

    suspend fun delivery(imageId: String, purpose: String, shared: Boolean): ClientImageDelivery {
        val response = if (shared) {
            val variant = when (purpose) {
                "thumbnail" -> "thumbnail_512"
                "download" -> "original"
                else -> "preview_2048"
            }
            api().imageApi.sharedDelivery(imageId, variant)
        } else {
            api().imageApi.delivery(imageId, purpose)
        }
        return response.bodyOrThrow("读取图片地址失败").toDomain()
    }

    suspend fun visibility(): ClientImageVisibility = api().imageApi.visibility()
        .bodyOrThrow("读取管理员可见设置失败")
        .let { ClientImageVisibility(it.enabled, it.revision) }

    suspend fun updateVisibility(enabled: Boolean, revision: Int): ClientImageVisibility =
        api().imageApi.updateVisibility(
            revision = "\"rev-$revision\"",
            idempotencyKey = "image-visibility-${UUID.randomUUID()}",
            body = ClientImageVisibilityPatchDto(enabled),
        ).bodyOrThrow("更新管理员可见设置失败")
            .let { ClientImageVisibility(it.enabled, it.revision) }

    suspend fun delete(imageId: String) {
        api().imageApi.deleteImage(imageId, "image-delete-$imageId")
            .bodyOrThrow("删除图片失败")
    }

    suspend fun downloadToUri(
        record: ClientImageRecord,
        target: Uri,
        shared: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val verified = downloadVerifiedToTemporaryFile(record, shared)
        try {
            context.contentResolver.openOutputStream(target, "w")?.use { output ->
                verified.file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入下载位置")
        } finally {
            verified.file.delete()
        }
    }

    suspend fun downloadToPictures(
        record: ClientImageRecord,
        shared: Boolean = false,
    ): Uri = withContext(Dispatchers.IO) {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val verified = downloadVerifiedToTemporaryFile(record, shared)
        try {
            val name = safeFileName(
                verified.suggestedFilename ?: record.displayName,
                record.imageFormat,
            )
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, record.mediaType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sonorus")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IOException("无法创建下载文件")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    verified.file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("无法写入下载文件")
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                uri
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } finally {
            verified.file.delete()
        }
    }

    private suspend fun ensureServerBatch(batchLocalId: String): String =
        batchLocks.getOrPut(batchLocalId) { Mutex() }.withLock {
            val batch = dao.batch(batchLocalId) ?: throw IOException("本地上传批次不存在")
            batch.serverBatchId ?: api().imageApi.createBatch(
                idempotencyKey = "image-batch-${batch.localId}",
                body = ClientImageBatchCreateDto(
                    clientBatchId = batch.localId,
                    totalCount = batch.totalCount,
                    totalBytes = batch.totalBytes,
                ),
            ).bodyOrThrow("创建图片上传批次失败").id.also { remoteId ->
                dao.bindServerBatch(batch.localId, remoteId, "active", now())
            }
        }

    private suspend fun refreshLocalBatch(batchLocalId: String) {
        val batch = dao.batch(batchLocalId) ?: return
        if (batch.state in setOf(ClientImageTransferState.PAUSED, ClientImageTransferState.CANCELLED)) {
            return
        }
        val progress = dao.progress(batchLocalId)
        val finished = progress.succeededCount + progress.failedCount
        val state = when {
            progress.totalCount > 0 && finished >= progress.totalCount && progress.failedCount == 0 -> "completed"
            progress.failedCount > 0 -> "partial"
            else -> "active"
        }
        dao.updateBatchProgress(
            batchLocalId,
            progress.succeededCount,
            progress.failedCount,
            state,
            now(),
        )
    }

    private fun cleanupPrepared(item: ClientImageTransferItemEntity) {
        item.preparedPath?.let(::File)?.takeIf(File::isFile)?.delete()
        item.thumbnailPreparedPath?.let(::File)?.takeIf(File::isFile)?.delete()
    }

    private suspend fun downloadVerifiedToTemporaryFile(
        record: ClientImageRecord,
        shared: Boolean,
    ): VerifiedDownload {
        val directory = File(context.cacheDir, "client_image_downloads").apply { mkdirs() }
        val target = File.createTempFile("client-image-", ".part", directory)
        try {
            var lastFailure: Throwable? = null
            repeat(2) { attempt ->
                try {
                    val descriptor = delivery(record.id, "download", shared = shared)
                    target.outputStream().use { output ->
                        downloadVerified(record, descriptor, output)
                    }
                    return VerifiedDownload(target, descriptor.suggestedFilename)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    lastFailure = error
                    if (attempt == 0) target.writeBytes(byteArrayOf())
                }
            }
            throw lastFailure ?: IOException("下载图片失败")
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun downloadVerified(
        record: ClientImageRecord,
        descriptor: ClientImageDelivery,
        output: java.io.OutputStream,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val verified = object : FilterOutputStream(output) {
            override fun write(value: Int) {
                out.write(value)
                digest.update(value.toByte())
                written++
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                out.write(buffer, offset, length)
                digest.update(buffer, offset, length)
                written += length
            }
        }
        api().downloadImageFromSignedUrl(descriptor.signedUrl, verified)
        verified.flush()
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (written != record.byteSize || actualSha256 != record.assetSha256) {
            throw IOException("下载图片完整性校验失败")
        }
    }

    private fun api(): CatalogApiClient {
        val device = credentials.loadDevice() ?: throw IllegalStateException("请先在目录服务器中登记此设备")
        return CatalogApiClient(device.serverUrl, credentials)
    }

    private fun requireIdentity(): Pair<String, String> = currentIdentity()
        ?: throw IllegalStateException("请先在目录服务器中登记此设备")

    private inner class ClientImagePagingSource(
        private val shared: Boolean,
    ) : PagingSource<String, ClientImageRecord>() {
        override suspend fun load(params: LoadParams<String>): LoadResult<String, ClientImageRecord> = try {
            val response = if (shared) {
                api().imageApi.sharedImages(cursor = params.key, limit = params.loadSize.coerceAtMost(100))
            } else {
                api().imageApi.images(cursor = params.key, limit = params.loadSize.coerceAtMost(100))
            }.bodyOrThrow("读取图片列表失败")
            LoadResult.Page(
                data = response.items.map(ClientImageRecordDto::toDomain),
                prevKey = null,
                nextKey = response.nextCursor,
            )
        } catch (error: Throwable) {
            LoadResult.Error(error)
        }

        override fun getRefreshKey(state: PagingState<String, ClientImageRecord>): String? = null
    }

    companion object {
        private const val MIN_ADMIN_IMAGE_BYTES = 1L * 1024L * 1024L
        private const val MAX_ADMIN_IMAGE_BYTES = 500L * 1024L * 1024L
        private const val MAX_RETRIES = 5
        private val SUPPORTED_SELECTION_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        @Volatile private var instance: ClientImageRepository? = null

        fun get(context: Context): ClientImageRepository = instance ?: synchronized(this) {
            instance ?: ClientImageRepository(context.applicationContext).also { instance = it }
        }

        private fun now(): Long = System.currentTimeMillis()

        private fun safeFileName(name: String, format: String): String {
            var clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .trim('.', ' ')
                .take(180)
            val extension = when (format.lowercase()) {
                "jpeg", "jpg" -> ".jpg"
                "webp" -> ".webp"
                else -> ".png"
            }
            val accepted = if (extension == ".jpg") listOf(".jpg", ".jpeg") else listOf(extension)
            if (accepted.any { clean.lowercase().endsWith(it) }) return clean
            listOf(".png", ".jpg", ".jpeg", ".webp").firstOrNull {
                clean.lowercase().endsWith(it)
            }?.let { clean = clean.dropLast(it.length).trimEnd('.', ' ') }
            return (clean.ifBlank { "image-${UUID.randomUUID()}" }) + extension
        }
    }

    private data class VerifiedDownload(
        val file: File,
        val suggestedFilename: String?,
    )
}

private fun ClientImageCapabilitiesDto.toDomain() = ClientImageCapabilities(
    enabled = enabled,
    unavailableReason = unavailableReason,
    supportedMediaTypes = supportedMediaTypes.toSet(),
    maxImageBytes = maxImageBytes,
    maxImagePixels = maxImagePixels,
    maxBatchItems = maxBatchItems,
    maxThumbnailDeliveries = maxThumbnailDeliveries,
    maxParallelUploads = maxParallelUploads,
)

private fun ClientImageRecordDto.toDomain() = ClientImageRecord(
    id = id,
    ownerUserId = ownerUserId,
    assetId = assetId,
    assetSha256 = assetSha256,
    displayName = displayName,
    mediaType = mediaType,
    imageFormat = imageFormat,
    width = width,
    height = height,
    byteSize = byteSize,
    revision = revision,
    createdAt = createdAt,
)

private fun ClientImageDeliveryDto.toDomain() = ClientImageDelivery(
    imageId = imageId,
    assetId = assetId,
    variant = variant,
    signedUrl = signedUrl,
    expiresAt = expiresAt,
    stableCacheKey = stableCacheKey,
    mediaType = mediaType,
    byteSize = byteSize,
    suggestedFilename = suggestedFilename,
)

private fun <T> Response<T>.bodyOrThrow(action: String): T {
    if (isSuccessful) return body() ?: throw ClientImageApiException(code(), "$action：响应为空")
    val detail = runCatching { errorBody()?.string()?.take(500) }.getOrNull()
    throw ClientImageApiException(code(), "$action（HTTP ${code()}）${detail?.let { "：$it" }.orEmpty()}")
}
