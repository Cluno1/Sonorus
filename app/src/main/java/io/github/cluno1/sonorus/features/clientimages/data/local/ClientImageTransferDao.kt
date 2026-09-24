package io.github.cluno1.sonorus.features.clientimages.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientImageTransferDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ClientImageTransferBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<ClientImageTransferItemEntity>)

    @Transaction
    suspend fun insertBatchWithItems(
        batch: ClientImageTransferBatchEntity,
        items: List<ClientImageTransferItemEntity>,
    ) {
        insertBatch(batch)
        insertItems(items)
    }

    @Query(
        """
        SELECT * FROM client_image_transfer_batches
        WHERE ownerUserId = :ownerUserId AND serverOrigin = :serverOrigin
        ORDER BY updatedAtEpochMs DESC
        """,
    )
    fun observeBatches(ownerUserId: String, serverOrigin: String): Flow<List<ClientImageTransferBatchEntity>>

    @Query(
        """
        SELECT * FROM client_image_transfer_items
        WHERE batchLocalId = :batchLocalId
        ORDER BY createdAtEpochMs, localId
        """,
    )
    fun observeItems(batchLocalId: String): Flow<List<ClientImageTransferItemEntity>>

    @Query("SELECT * FROM client_image_transfer_batches WHERE localId = :batchLocalId")
    suspend fun batch(batchLocalId: String): ClientImageTransferBatchEntity?

    @Query("SELECT * FROM client_image_transfer_items WHERE localId = :itemId")
    suspend fun item(itemId: String): ClientImageTransferItemEntity?

    @Query("SELECT * FROM client_image_transfer_items WHERE batchLocalId = :batchLocalId")
    suspend fun items(batchLocalId: String): List<ClientImageTransferItemEntity>

    @Query(
        """
        SELECT * FROM client_image_transfer_items
        WHERE batchLocalId = :batchLocalId
          AND state IN ('queued', 'ready', 'retryable', 'creating', 'uploading', 'verifying')
        ORDER BY createdAtEpochMs, localId
        """,
    )
    suspend fun runnableItems(batchLocalId: String): List<ClientImageTransferItemEntity>

    @Query(
        """
        UPDATE client_image_transfer_batches
        SET serverBatchId = :serverBatchId, state = :state, updatedAtEpochMs = :now
        WHERE localId = :batchLocalId
        """,
    )
    suspend fun bindServerBatch(batchLocalId: String, serverBatchId: String, state: String, now: Long)

    @Query(
        """
        UPDATE client_image_transfer_batches
        SET state = :state, updatedAtEpochMs = :now
        WHERE localId = :batchLocalId
        """,
    )
    suspend fun setBatchState(batchLocalId: String, state: String, now: Long)

    @Query(
        """
        UPDATE client_image_transfer_items
        SET state = :state, errorCode = :errorCode, retryCount = retryCount + :retryIncrement,
            updatedAtEpochMs = :now
        WHERE localId = :itemId
        """,
    )
    suspend fun setItemState(
        itemId: String,
        state: String,
        errorCode: String?,
        retryIncrement: Int,
        now: Long,
    )

    @Query(
        """
        UPDATE client_image_transfer_items
        SET preparedPath = :preparedPath, mediaType = :mediaType, byteSize = :byteSize,
            width = :width, height = :height, contentMd5 = :contentMd5, sha256 = :sha256,
            thumbnailPreparedPath = :thumbnailPreparedPath,
            thumbnailMediaType = :thumbnailMediaType,
            thumbnailByteSize = :thumbnailByteSize,
            thumbnailWidth = :thumbnailWidth,
            thumbnailHeight = :thumbnailHeight,
            thumbnailContentMd5 = :thumbnailContentMd5,
            thumbnailSha256 = :thumbnailSha256,
            transferredBytes = 0, state = 'ready', errorCode = NULL, updatedAtEpochMs = :now
        WHERE localId = :itemId
        """,
    )
    suspend fun setPrepared(
        itemId: String,
        preparedPath: String,
        mediaType: String,
        byteSize: Long,
        width: Int,
        height: Int,
        contentMd5: String,
        sha256: String,
        thumbnailPreparedPath: String,
        thumbnailMediaType: String,
        thumbnailByteSize: Long,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        thumbnailContentMd5: String,
        thumbnailSha256: String,
        now: Long,
    )

    @Query(
        """
        UPDATE client_image_transfer_items
        SET serverUploadId = :uploadId, imageId = :imageId, state = :state,
            errorCode = NULL, updatedAtEpochMs = :now
        WHERE localId = :itemId
        """,
    )
    suspend fun bindUpload(
        itemId: String,
        uploadId: String,
        imageId: String,
        state: String,
        now: Long,
    )

    @Query(
        """
        UPDATE client_image_transfer_items
        SET transferredBytes = :transferredBytes, state = 'uploading', updatedAtEpochMs = :now
        WHERE localId = :itemId
          AND state IN ('ready', 'creating', 'uploading')
        """,
    )
    suspend fun setProgress(itemId: String, transferredBytes: Long, now: Long)

    @Query(
        """
        UPDATE client_image_transfer_items
        SET assetId = :assetId, imageId = :imageId, transferredBytes = COALESCE(byteSize, transferredBytes),
            state = 'completed', errorCode = NULL, updatedAtEpochMs = :now
        WHERE localId = :itemId
        """,
    )
    suspend fun complete(itemId: String, imageId: String, assetId: String, now: Long)

    @Query(
        """
        SELECT :batchLocalId AS batchLocalId,
               COUNT(*) AS totalCount,
               SUM(CASE WHEN state = 'completed' THEN 1 ELSE 0 END) AS succeededCount,
               SUM(CASE WHEN state IN ('failed', 'cancelled') THEN 1 ELSE 0 END) AS failedCount,
               COALESCE(SUM(transferredBytes), 0) AS transferredBytes,
               COALESCE(SUM(COALESCE(byteSize, sourceByteSize, 0)), 0) AS totalBytes
        FROM client_image_transfer_items
        WHERE batchLocalId = :batchLocalId
        """,
    )
    suspend fun progress(batchLocalId: String): ClientImageBatchProgress

    @Query(
        """
        UPDATE client_image_transfer_batches
        SET succeededCount = :succeeded, failedCount = :failed, state = :state,
            updatedAtEpochMs = :now
        WHERE localId = :batchLocalId
        """,
    )
    suspend fun updateBatchProgress(
        batchLocalId: String,
        succeeded: Int,
        failed: Int,
        state: String,
        now: Long,
    )

    @Query(
        """
        UPDATE client_image_transfer_items
        SET state = 'paused', updatedAtEpochMs = :now
        WHERE batchLocalId = :batchLocalId
          AND state NOT IN ('completed', 'failed', 'cancelled')
        """,
    )
    suspend fun pauseItems(batchLocalId: String, now: Long)

    @Query(
        """
        UPDATE client_image_transfer_items
        SET state = CASE WHEN preparedPath IS NULL THEN 'queued' ELSE 'retryable' END,
            errorCode = NULL, updatedAtEpochMs = :now
        WHERE batchLocalId = :batchLocalId AND state IN ('paused', 'failed')
        """,
    )
    suspend fun resumeItems(batchLocalId: String, now: Long)

    @Query(
        """
        UPDATE client_image_transfer_items
        SET state = 'cancelled', updatedAtEpochMs = :now
        WHERE batchLocalId = :batchLocalId AND state != 'completed'
        """,
    )
    suspend fun cancelItems(batchLocalId: String, now: Long)
}
