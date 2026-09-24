package io.github.cluno1.sonorus.features.clientimages.domain

import android.net.Uri

data class ClientImageCapabilities(
    val enabled: Boolean,
    val unavailableReason: String?,
    val supportedMediaTypes: Set<String>,
    val maxImageBytes: Long,
    val maxImagePixels: Long,
    val maxBatchItems: Int,
    val maxThumbnailDeliveries: Int,
    val maxParallelUploads: Int,
)

data class SelectedClientImage(
    val uri: Uri,
    val displayName: String,
    val mediaType: String?,
    val byteSize: Long?,
)

data class ClientImageRecord(
    val id: String,
    val ownerUserId: String,
    val assetId: String,
    val assetSha256: String,
    val displayName: String,
    val mediaType: String,
    val imageFormat: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val revision: Int,
    val createdAt: String,
)

data class ClientImageDelivery(
    val imageId: String,
    val assetId: String,
    val variant: String,
    val signedUrl: String,
    val expiresAt: String,
    val stableCacheKey: String,
    val mediaType: String,
    val byteSize: Long?,
    val suggestedFilename: String?,
)

data class ClientImageVisibility(
    val enabled: Boolean,
    val revision: Int,
)

class ClientImageApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)
