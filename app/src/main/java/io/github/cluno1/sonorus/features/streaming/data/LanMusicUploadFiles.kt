/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadError
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.InputStream

data class LocalMusicUploadFile(val uri: Uri, val name: String, val size: Long?)

object LanMusicUploadFiles {
    fun describe(context: Context, uri: Uri): LocalMusicUploadFile {
        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        var size: Long? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                }
            }
        } catch (_: Exception) {
            // Some local document providers expose only a file descriptor.
        }
        if (size == null) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    size = descriptor.statSize.takeIf { it >= 0 }
                }
            } catch (_: Exception) { }
        }
        return LocalMusicUploadFile(uri, name.trim(), size)
    }

    /** Unknown-length providers are staged to disk; audio is never held in memory. */
    suspend fun <T> withBody(
        context: Context,
        file: LocalMusicUploadFile,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
        upload: suspend (RequestBody, Long) -> T,
    ): T {
        var temporary: File? = null
        try {
            val body = withContext(Dispatchers.IO) {
                if (file.size != null) {
                    checkSize(file.size, maxBytes)
                    AudioRequestBody(file.size, { open(context, file.uri) }, onProgress)
                } else {
                    val directory = File(context.cacheDir, "lan-music-uploads").apply { mkdirs() }
                    val staged = File.createTempFile("audio-", ".tmp", directory)
                    temporary = staged
                    open(context, file.uri).use { input ->
                        staged.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var size = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count
                                if (size > maxBytes) throw LanMusicUploadException(LanMusicUploadError.TOO_LARGE)
                                output.write(buffer, 0, count)
                            }
                            checkSize(size, maxBytes)
                        }
                    }
                    AudioRequestBody(staged.length(), { staged.inputStream() }, onProgress)
                }
            }
            return upload(body, body.contentLength())
        } catch (e: CancellationException) {
            throw e
        } catch (e: LanMusicUploadException) {
            throw e
        } catch (_: java.io.IOException) {
            throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
        } catch (_: SecurityException) {
            throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
        } finally {
            temporary?.delete()
        }
    }

    private fun checkSize(size: Long, maxBytes: Long) {
        if (size <= 0) throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
        if (size > maxBytes) throw LanMusicUploadException(LanMusicUploadError.TOO_LARGE)
    }

    private fun open(context: Context, uri: Uri): InputStream = try {
        context.contentResolver.openInputStream(uri) ?: throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
    } catch (_: Exception) {
        throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
    }
}

private class AudioRequestBody(
    private val size: Long,
    private val open: () -> InputStream,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = size
    override fun isOneShot() = true

    override fun writeTo(sink: BufferedSink) {
        val input = try { open() } catch (_: Exception) {
            throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
        }
        input.use {
            val buffer = ByteArray(64 * 1024)
            var sent = 0L
            var lastPercent = -1
            while (sent < size) {
                val count = try { input.read(buffer, 0, minOf(buffer.size.toLong(), size - sent).toInt()) }
                    catch (_: Exception) { throw LanMusicUploadException(LanMusicUploadError.READ_FILE) }
                if (count < 0) throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
                sink.write(buffer, 0, count)
                sent += count
                val percent = (sent * 100 / size).toInt()
                if (percent != lastPercent) {
                    onProgress(sent, size)
                    lastPercent = percent
                }
            }
            if (input.read() != -1) throw LanMusicUploadException(LanMusicUploadError.READ_FILE)
        }
    }
}
