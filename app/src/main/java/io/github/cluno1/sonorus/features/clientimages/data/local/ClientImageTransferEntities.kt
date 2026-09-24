package io.github.cluno1.sonorus.features.clientimages.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable user-visible batch. Signed object-store URLs are intentionally not persisted. */
@Entity(
    tableName = "client_image_transfer_batches",
    indices = [Index(value = ["ownerUserId", "updatedAtEpochMs"])],
)
data class ClientImageTransferBatchEntity(
    @PrimaryKey val localId: String,
    val ownerUserId: String,
    val serverOrigin: String,
    val serverBatchId: String?,
    val state: String,
    val wifiOnly: Boolean,
    val totalCount: Int,
    val totalBytes: Long,
    val succeededCount: Int,
    val failedCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "client_image_transfer_items",
    foreignKeys = [
        ForeignKey(
            entity = ClientImageTransferBatchEntity::class,
            parentColumns = ["localId"],
            childColumns = ["batchLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["batchLocalId", "state"]),
        Index(value = ["ownerUserId", "updatedAtEpochMs"]),
        Index(value = ["imageId"], unique = false),
    ],
)
data class ClientImageTransferItemEntity(
    @PrimaryKey val localId: String,
    val batchLocalId: String,
    val ownerUserId: String,
    val sourceUri: String,
    val displayName: String,
    val sourceMediaType: String?,
    val sourceByteSize: Long?,
    val preparedPath: String?,
    val mediaType: String?,
    val byteSize: Long?,
    val width: Int?,
    val height: Int?,
    val contentMd5: String?,
    val sha256: String?,
    val thumbnailPreparedPath: String?,
    val thumbnailMediaType: String?,
    val thumbnailByteSize: Long?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val thumbnailContentMd5: String?,
    val thumbnailSha256: String?,
    val serverUploadId: String?,
    val imageId: String?,
    val assetId: String?,
    val transferredBytes: Long,
    val state: String,
    val retryCount: Int,
    val errorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class ClientImageBatchProgress(
    val batchLocalId: String,
    val totalCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val transferredBytes: Long,
    val totalBytes: Long,
)

object ClientImageTransferState {
    const val QUEUED = "queued"
    const val PREPARING = "preparing"
    const val READY = "ready"
    const val CREATING = "creating"
    const val UPLOADING = "uploading"
    const val VERIFYING = "verifying"
    const val COMPLETED = "completed"
    const val RETRYABLE = "retryable"
    const val FAILED = "failed"
    const val PAUSED = "paused"
    const val CANCELLED = "cancelled"

    val runnable = listOf(QUEUED, READY, RETRYABLE, CREATING, UPLOADING, VERIFYING)
}
