package io.github.cluno1.sonorus.features.clientimages.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class PreparedClientImage(
    val file: File,
    val mediaType: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val contentMd5: String,
    val sha256: String,
    val thumbnailFile: File,
    val thumbnailMediaType: String,
    val thumbnailByteSize: Long,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val thumbnailContentMd5: String,
    val thumbnailSha256: String,
)

/**
 * Copies one URI into app-private storage, verifies its real signature/dimensions and removes
 * privacy-sensitive EXIF/XMP fields without decoding the full bitmap. Work is bounded to one file
 * per upload coroutine and rejects animated WebP/PNG.
 */
class ClientImagePreparer(private val context: Context) {
    suspend fun prepare(
        itemId: String,
        source: Uri,
        maxBytes: Long,
        maxPixels: Long,
    ): PreparedClientImage = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "client_image_uploads").apply { mkdirs() }
        val staging = File(directory, "$itemId.staging")
        var preparedTarget: File? = null
        var thumbnailTarget: File? = null
        try {
            val buffer = ByteArray(64 * 1024)
            var copied = 0L
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(staging).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > maxBytes) throw InvalidClientImage("图片超过大小限制")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw InvalidClientImage("无法读取所选图片")

            val detected = detect(staging)
            if (detected.animated) throw InvalidClientImage("首版暂不支持动态图片")
            val target = File(directory, "$itemId.${detected.extension}")
            preparedTarget = target
            if (target.exists() && !target.delete()) throw IOException("cannot replace prepared image")
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }

            sanitizeMetadata(target)
            val byteSize = target.length()
            if (byteSize <= 0L || byteSize > maxBytes) throw InvalidClientImage("图片超过大小限制")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) throw InvalidClientImage("无法识别图片尺寸")
            if (width.toLong() * height.toLong() > maxPixels) {
                throw InvalidClientImage("图片像素尺寸超过限制")
            }
            val (md5, sha256) = hashes(target)
            val thumbnail = createThumbnail(
                source = target,
                sourceMediaType = detected.mediaType,
                sourceWidth = width,
                sourceHeight = height,
                directory = directory,
                itemId = itemId,
            )
            thumbnailTarget = thumbnail.file
            PreparedClientImage(
                file = target,
                mediaType = detected.mediaType,
                byteSize = byteSize,
                width = width,
                height = height,
                contentMd5 = Base64.encodeToString(md5, Base64.NO_WRAP),
                sha256 = sha256.joinToString("") { "%02x".format(it) },
                thumbnailFile = thumbnail.file,
                thumbnailMediaType = thumbnail.mediaType,
                thumbnailByteSize = thumbnail.byteSize,
                thumbnailWidth = thumbnail.width,
                thumbnailHeight = thumbnail.height,
                thumbnailContentMd5 = thumbnail.contentMd5,
                thumbnailSha256 = thumbnail.sha256,
            )
        } catch (error: Throwable) {
            staging.delete()
            preparedTarget?.delete()
            thumbnailTarget?.delete()
            throw error
        }
    }

    private fun createThumbnail(
        source: File,
        sourceMediaType: String,
        sourceWidth: Int,
        sourceHeight: Int,
        directory: File,
        itemId: String,
    ): PreparedThumbnail {
        var sampleSize = 1
        while (sourceWidth / sampleSize > THUMBNAIL_DECODE_EDGE ||
            sourceHeight / sampleSize > THUMBNAIL_DECODE_EDGE
        ) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw InvalidClientImage("无法生成图片预览")
        val oriented = applyOrientation(decoded, ExifInterface(source).rotationDegrees)
        if (oriented !== decoded) decoded.recycle()
        val scale = minOf(1f, THUMBNAIL_EDGE.toFloat() / maxOf(oriented.width, oriented.height))
        val targetWidth = maxOf(1, (oriented.width * scale).toInt())
        val targetHeight = maxOf(1, (oriented.height * scale).toInt())
        val scaled = if (targetWidth == oriented.width && targetHeight == oriented.height) {
            oriented
        } else {
            Bitmap.createScaledBitmap(oriented, targetWidth, targetHeight, true)
        }
        if (scaled !== oriented) oriented.recycle()

        val jpeg = sourceMediaType == "image/jpeg"
        val mediaType = if (jpeg) "image/jpeg" else "image/png"
        val extension = if (jpeg) "jpg" else "png"
        val target = File(directory, "$itemId.thumbnail_512.$extension")
        if (target.exists() && !target.delete()) throw IOException("cannot replace thumbnail")
        try {
            FileOutputStream(target).use { output ->
                val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                if (!scaled.compress(format, if (jpeg) 90 else 100, output)) {
                    throw InvalidClientImage("无法生成图片预览")
                }
            }
        } finally {
            scaled.recycle()
        }
        val (md5, sha256) = hashes(target)
        return PreparedThumbnail(
            file = target,
            mediaType = mediaType,
            byteSize = target.length(),
            width = targetWidth,
            height = targetHeight,
            contentMd5 = Base64.encodeToString(md5, Base64.NO_WRAP),
            sha256 = sha256.joinToString("") { "%02x".format(it) },
        )
    }

    private fun applyOrientation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        )
    }

    private fun sanitizeMetadata(file: File) {
        runCatching {
            ExifInterface(file).apply {
                SENSITIVE_EXIF_TAGS.forEach { setAttribute(it, null) }
                saveAttributes()
            }
        }.getOrElse { throw InvalidClientImage("无法安全清理图片元数据", it) }
    }

    private fun detect(file: File): DetectedImage {
        FileInputStream(file).use { input ->
            val header = ByteArray(12)
            if (input.read(header) < header.size) throw InvalidClientImage("图片文件不完整")
            if (header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
                return DetectedImage("image/png", "png", pngAnimated(file))
            }
            if (header[0] == 0xff.toByte() && header[1] == 0xd8.toByte()) {
                return DetectedImage("image/jpeg", "jpg", false)
            }
            val riff = header.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val webp = header.copyOfRange(8, 12).toString(Charsets.US_ASCII)
            if (riff == "RIFF" && webp == "WEBP") {
                return DetectedImage("image/webp", "webp", webpAnimated(file))
            }
        }
        throw InvalidClientImage("仅支持 PNG、JPEG 和静态 WebP")
    }

    private fun pngAnimated(file: File): Boolean = FileInputStream(file).use { input ->
        input.skip(8)
        val header = ByteArray(8)
        while (input.read(header) == 8) {
            val length = ((header[0].toLong() and 0xff) shl 24) or
                ((header[1].toLong() and 0xff) shl 16) or
                ((header[2].toLong() and 0xff) shl 8) or
                (header[3].toLong() and 0xff)
            val type = header.copyOfRange(4, 8).toString(Charsets.US_ASCII)
            if (type == "acTL") return@use true
            if (type == "IEND") return@use false
            skipFully(input, length + 4L)
        }
        false
    }

    private fun webpAnimated(file: File): Boolean = FileInputStream(file).use { input ->
        skipFully(input, 12)
        val header = ByteArray(8)
        while (input.read(header) == 8) {
            val type = header.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val length = (header[4].toLong() and 0xff) or
                ((header[5].toLong() and 0xff) shl 8) or
                ((header[6].toLong() and 0xff) shl 16) or
                ((header[7].toLong() and 0xff) shl 24)
            if (type == "ANIM" || type == "ANMF") return@use true
            if (type == "VP8X") {
                val flags = input.read()
                if (flags < 0) return@use false
                if (flags and 0x02 != 0) return@use true
                skipFully(input, length - 1L + (length and 1L))
            } else {
                skipFully(input, length + (length and 1L))
            }
        }
        false
    }

    private fun hashes(file: File): Pair<ByteArray, ByteArray> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha256 = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                md5.update(buffer, 0, count)
                sha256.update(buffer, 0, count)
            }
        }
        return md5.digest() to sha256.digest()
    }

    private data class DetectedImage(
        val mediaType: String,
        val extension: String,
        val animated: Boolean,
    )

    private data class PreparedThumbnail(
        val file: File,
        val mediaType: String,
        val byteSize: Long,
        val width: Int,
        val height: Int,
        val contentMd5: String,
        val sha256: String,
    )

    companion object {
        private const val THUMBNAIL_EDGE = 512
        private const val THUMBNAIL_DECODE_EDGE = 1024
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )

        private val SENSITIVE_EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_XMP,
        )

        private fun skipFully(input: FileInputStream, count: Long) {
            if (count < 0) throw InvalidClientImage("图片容器损坏")
            var remaining = count
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) {
                    if (input.read() < 0) throw InvalidClientImage("图片容器不完整")
                    remaining--
                } else {
                    remaining -= skipped
                }
            }
        }
    }
}

class InvalidClientImage(message: String, cause: Throwable? = null) : IOException(message, cause)
