package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Narrow, owned image-management surface used by the Labs image client. */
internal interface CatalogImageApi {
    @GET("v2/labs/images/capabilities")
    suspend fun capabilities(): Response<ClientImageCapabilitiesDto>

    @POST("v2/labs/image-upload-batches")
    suspend fun createBatch(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ClientImageBatchCreateDto,
    ): Response<ClientImageBatchDto>

    @GET("v2/labs/image-upload-batches/{id}")
    suspend fun batch(@Path("id") batchId: String): Response<ClientImageBatchDto>

    @POST("v2/labs/image-upload-batches/{id}/cancel")
    suspend fun cancelBatch(
        @Path("id") batchId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ClientImageBatchDto>

    @POST("v2/labs/image-uploads")
    suspend fun createUpload(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ClientImageUploadCreateDto,
    ): Response<ClientImageUploadDto>

    @POST("v2/labs/image-uploads/{id}/refresh")
    suspend fun refreshUpload(
        @Path("id") uploadId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ClientImageUploadDto>

    @POST("v2/labs/image-uploads/{id}/complete")
    suspend fun completeUpload(
        @Path("id") uploadId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ClientImageRecordDto>

    @POST("v2/labs/image-uploads/{id}/cancel")
    suspend fun cancelUpload(
        @Path("id") uploadId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ClientImageUploadDto>

    @GET("v2/labs/images")
    suspend fun images(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30,
    ): Response<ClientImagePageDto>

    @GET("v2/labs/images/{id}")
    suspend fun image(@Path("id") imageId: String): Response<ClientImageRecordDto>

    @GET("v2/labs/images/{id}/delivery")
    suspend fun delivery(
        @Path("id") imageId: String,
        @Query("purpose") purpose: String,
    ): Response<ClientImageDeliveryDto>

    @POST("v2/labs/images/thumbnail-deliveries")
    suspend fun thumbnailDeliveries(
        @Body body: ClientImageThumbnailRequestDto,
    ): Response<ClientImageThumbnailResponseDto>

    @DELETE("v2/labs/images/{id}")
    suspend fun deleteImage(
        @Path("id") imageId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ClientImageDeleteDto>

    @HTTP(method = "DELETE", path = "v2/labs/images:batch-delete", hasBody = true)
    suspend fun deleteImages(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ClientImageIdsDto,
    ): Response<ClientImageDeleteResponseDto>

    @GET("v2/labs/images/admin-visibility")
    suspend fun visibility(): Response<ClientImageVisibilityDto>

    @PATCH("v2/labs/images/admin-visibility")
    suspend fun updateVisibility(
        @Header("If-Match") revision: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ClientImageVisibilityPatchDto,
    ): Response<ClientImageVisibilityDto>

    @GET("v2/admin/shared-images")
    suspend fun sharedImages(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 30,
        @Query("owner_user_id") ownerUserId: String? = null,
        @Query("image_format") imageFormat: String? = null,
    ): Response<ClientImagePageDto>

    @POST("v2/admin/shared-images/thumbnail-deliveries")
    suspend fun sharedThumbnailDeliveries(
        @Body body: ClientImageThumbnailRequestDto,
    ): Response<ClientImageThumbnailResponseDto>

    @GET("v2/admin/shared-images/{id}/delivery")
    suspend fun sharedDelivery(
        @Path("id") imageId: String,
        @Query("variant") variant: String,
    ): Response<ClientImageDeliveryDto>
}

internal data class ClientImageCapabilitiesDto(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("unavailable_reason") val unavailableReason: String?,
    @SerializedName("supported_media_types") val supportedMediaTypes: List<String>,
    @SerializedName("max_image_bytes") val maxImageBytes: Long,
    @SerializedName("max_image_pixels") val maxImagePixels: Long,
    @SerializedName("max_batch_items") val maxBatchItems: Int,
    @SerializedName("max_thumbnail_deliveries") val maxThumbnailDeliveries: Int,
    @SerializedName("max_parallel_uploads") val maxParallelUploads: Int,
    @SerializedName("upload_url_ttl_seconds") val uploadUrlTtlSeconds: Int,
    @SerializedName("shared_preview_ttl_seconds") val sharedPreviewTtlSeconds: Int,
)

internal data class ClientImageBatchCreateDto(
    @SerializedName("client_batch_id") val clientBatchId: String,
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("total_bytes") val totalBytes: Long,
)

internal data class ClientImageBatchDto(
    @SerializedName("id") val id: String,
    @SerializedName("client_batch_id") val clientBatchId: String,
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("total_bytes") val totalBytes: Long,
    @SerializedName("succeeded_count") val succeededCount: Int,
    @SerializedName("failed_count") val failedCount: Int,
    @SerializedName("state") val state: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

internal data class ClientImageUploadCreateDto(
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("client_item_id") val clientItemId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("byte_size") val byteSize: Long,
    @SerializedName("content_md5") val contentMd5: String,
    @SerializedName("client_sha256") val clientSha256: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("metadata_sanitized") val metadataSanitized: Boolean = true,
    @SerializedName("thumbnail_512") val thumbnail512: ClientImageObjectDeclarationDto,
)

internal data class ClientImageObjectDeclarationDto(
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("byte_size") val byteSize: Long,
    @SerializedName("content_md5") val contentMd5: String,
    @SerializedName("client_sha256") val clientSha256: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
)

internal data class ClientImageUploadTargetDto(
    @SerializedName("method") val method: String,
    @SerializedName("url") val url: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("required_headers") val requiredHeaders: Map<String, String>,
)

internal data class ClientImageUploadDto(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("image_id") val imageId: String,
    @SerializedName("state") val state: String,
    @SerializedName("upload") val upload: ClientImageUploadTargetDto?,
    @SerializedName("thumbnail_upload") val thumbnailUpload: ClientImageUploadTargetDto?,
    @SerializedName("asset_id") val assetId: String?,
)

internal data class ClientImageRecordDto(
    @SerializedName("id") val id: String,
    @SerializedName("owner_user_id") val ownerUserId: String,
    @SerializedName("batch_id") val batchId: String,
    @SerializedName("client_item_id") val clientItemId: String,
    @SerializedName("asset_id") val assetId: String,
    @SerializedName("asset_sha256") val assetSha256: String,
    @SerializedName("uploader_device_id") val uploaderDeviceId: String?,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("image_format") val imageFormat: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("byte_size") val byteSize: Long,
    @SerializedName("state") val state: String,
    @SerializedName("revision") val revision: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("ready_at") val readyAt: String,
)

internal data class ClientImagePageDto(
    @SerializedName("items") val items: List<ClientImageRecordDto>,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class ClientImageDeliveryDto(
    @SerializedName("image_id") val imageId: String,
    @SerializedName("asset_id") val assetId: String,
    @SerializedName("variant") val variant: String,
    @SerializedName("signed_url") val signedUrl: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("stable_cache_key") val stableCacheKey: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("byte_size") val byteSize: Long?,
    @SerializedName("suggested_filename") val suggestedFilename: String?,
)

internal data class ClientImageThumbnailRequestDto(
    @SerializedName("image_ids") val imageIds: List<String>,
    @SerializedName("variant") val variant: String,
)

internal data class ClientImageThumbnailItemDto(
    @SerializedName("image_id") val imageId: String,
    @SerializedName("status") val status: String,
    @SerializedName("delivery") val delivery: ClientImageDeliveryDto?,
)

internal data class ClientImageThumbnailResponseDto(
    @SerializedName("items") val items: List<ClientImageThumbnailItemDto>,
)

internal data class ClientImageIdsDto(
    @SerializedName("image_ids") val imageIds: List<String>,
)

internal data class ClientImageDeleteDto(
    @SerializedName("image_id") val imageId: String,
    @SerializedName("result") val result: String,
)

internal data class ClientImageDeleteResponseDto(
    @SerializedName("items") val items: List<ClientImageDeleteDto>,
)

internal data class ClientImageVisibilityPatchDto(
    @SerializedName("enabled") val enabled: Boolean,
)

internal data class ClientImageVisibilityDto(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("revision") val revision: Int,
    @SerializedName("enabled_at") val enabledAt: String?,
    @SerializedName("updated_at") val updatedAt: String,
)
