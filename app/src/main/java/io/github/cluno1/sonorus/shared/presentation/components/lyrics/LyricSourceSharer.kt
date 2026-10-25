package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.core.content.FileProvider
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtwork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "LyricSourceShare"

/** The one file handed to a share target. */
internal data class LyricSourceShareFile(val file: File, val mimeType: String)

/**
 * issue 123: turns a [LyricSourceShareRequest] into one image under cacheDir and a chooser intent.
 *
 * - Every share first empties [LyricSourceSharePolicy.DIRECTORY], so at most one share's file is
 *   ever kept on disk (a share target may still be reading the previous one — acceptable, that
 *   share has already been handed over).
 * - All pages are downloaded before anything is written: a failed page fails the whole share
 *   instead of sending an incomplete song.
 * - One page is shared byte for byte as delivered (hash-checked by the repository); several are
 *   drawn into one PNG by [LyricSourceSharePolicy.combinedLayout].
 */
internal class LyricSourceSharer(
    private val context: Context,
    private val repository: CatalogRepository = CatalogModule.repository(context),
) {
    suspend fun prepare(request: LyricSourceShareRequest): Result<LyricSourceShareFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.cacheDir, LyricSourceSharePolicy.DIRECTORY)
                directory.deleteRecursively()
                if (!directory.mkdirs() && !directory.isDirectory) throw IOException("share directory unavailable")
                val artworks = request.pages.map { page ->
                    repository.downloadArtwork(page.imageAssetId).getOrElse { error ->
                        Log.w(TAG, "source page download failed: asset=${page.imageAssetId} (${error.javaClass.simpleName})")
                        throw error
                    }
                }
                val base = LyricSourceSharePolicy.fileBaseName(request.title)
                val single = artworks.singleOrNull()
                if (single != null) {
                    val file = File(directory, LyricSourceSharePolicy.fileName(base, single.mediaType))
                    file.writeBytes(single.bytes)
                    LyricSourceShareFile(file, single.mediaType)
                } else {
                    LyricSourceShareFile(
                        writeCombined(File(directory, LyricSourceSharePolicy.fileName(base, "image/png")), artworks),
                        "image/png",
                    )
                }
            }.onFailure {
                if (it !is CancellationException) {
                    Log.w(TAG, "share preparation failed: pages=${request.pages.size} (${it.javaClass.simpleName})")
                }
            }
        }

    /** Opens the system share sheet; the target gets read access to exactly this one URI. */
    fun launch(shared: LyricSourceShareFile, caption: String?, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", shared.file)
        val spec = LyricSourceSharePolicy.intentSpec(shared.mimeType, caption)
        val send = Intent(spec.action).apply {
            type = spec.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData carries the read grant to the target through the chooser.
            clipData = ClipData.newRawUri(null, uri)
            spec.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        Log.i(TAG, "share sheet opened: type=${spec.mimeType} bytes=${shared.file.length()}")
    }

    /** Draws the pages into one white PNG, one page decoded at a time to bound memory. */
    private fun writeCombined(target: File, artworks: List<CatalogArtwork>): File {
        val sizes = artworks.map { artwork ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artwork.bytes, 0, artwork.bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("source page is not a decodable image")
            bounds.outWidth to bounds.outHeight
        }
        val layout = LyricSourceSharePolicy.combinedLayout(sizes)
        // Scanned pages are black on white: 565 halves the canvas memory with no visible loss.
        val combined = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(combined)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            artworks.forEachIndexed { index, artwork ->
                val cell = layout.cells[index]
                val page = BitmapFactory.decodeByteArray(
                    artwork.bytes,
                    0,
                    artwork.bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = LyricSourceSharePolicy.decodeSampleSize(sizes[index].first, cell.width)
                    },
                ) ?: throw IOException("source page is not a decodable image")
                try {
                    canvas.drawBitmap(
                        page,
                        null,
                        Rect(cell.left, cell.top, cell.left + cell.width, cell.top + cell.height),
                        paint,
                    )
                } finally {
                    page.recycle()
                }
            }
            target.outputStream().use { out ->
                if (!combined.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("combined image not written")
            }
        } finally {
            combined.recycle()
        }
        return target
    }
}
