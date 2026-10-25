package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtwork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "LyricSourceShare"

/** A file ready to be handed to a share target. */
internal data class LyricSourceShareFile(val file: File, val mimeType: String)

/**
 * issue 123: turns a [LyricSourceShareRequest] into files under cacheDir and a chooser intent.
 *
 * - Every share first empties [LyricSourceSharePolicy.DIRECTORY], so at most one share's files
 *   are ever kept on disk (a share target may still be reading the previous ones — acceptable,
 *   that share has already been handed over).
 * - All pages are downloaded before anything is written: a failed page fails the whole share
 *   instead of sending an incomplete song.
 * - Pages come from the Catalog Asset delivery (hash-checked by the repository); nothing else
 *   (caption, rights note) is added unless [LyricSourceShareRequest.caption] carries it.
 */
internal class LyricSourceSharer(
    private val context: Context,
    private val repository: CatalogRepository = CatalogModule.repository(context),
) {
    suspend fun prepare(request: LyricSourceShareRequest): Result<List<LyricSourceShareFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.cacheDir, LyricSourceSharePolicy.DIRECTORY)
                directory.deleteRecursively()
                if (!directory.mkdirs() && !directory.isDirectory) throw IOException("share directory unavailable")
                val artworks = request.pages.map { page ->
                    page to repository.downloadArtwork(page.imageAssetId).getOrElse { error ->
                        Log.w(TAG, "source page download failed: asset=${page.imageAssetId} (${error.javaClass.simpleName})")
                        throw error
                    }
                }
                val base = LyricSourceSharePolicy.fileBaseName(request.title)
                when (request.format) {
                    LyricSourceShareFormat.IMAGES -> artworks.map { (page, artwork) ->
                        val file = File(
                            directory,
                            LyricSourceSharePolicy.imageFileName(base, page.physicalPageNumber, artwork.mediaType),
                        )
                        file.writeBytes(artwork.bytes)
                        LyricSourceShareFile(file, artwork.mediaType)
                    }
                    LyricSourceShareFormat.PDF -> listOf(
                        LyricSourceShareFile(
                            writePdf(File(directory, LyricSourceSharePolicy.pdfFileName(base)), artworks),
                            "application/pdf",
                        ),
                    )
                }
            }.onFailure {
                if (it !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "share preparation failed: format=${request.format} pages=${request.pages.size} (${it.javaClass.simpleName})")
                }
            }
        }

    /** Opens the system share sheet for [files]; the target gets read access to exactly these URIs. */
    fun launch(files: List<LyricSourceShareFile>, caption: String?, chooserTitle: String) {
        val uris: List<Uri> = files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", it.file)
        }
        val spec = LyricSourceSharePolicy.intentSpec(files.map(LyricSourceShareFile::mimeType), caption)
        val send = Intent(spec.action).apply {
            type = spec.mimeType
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            // ClipData carries the read grant to the target through the chooser.
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            spec.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        Log.i(TAG, "share sheet opened: files=${files.size} type=${spec.mimeType}")
    }

    /** One PDF page per source page, sized from its render DPI so it prints at scan size. */
    private fun writePdf(target: File, artworks: List<Pair<CatalogLyricSourceImage, CatalogArtwork>>): File {
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            artworks.forEachIndexed { index, (page, artwork) ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artwork.bytes, 0, artwork.bytes.size, bounds)
                val bitmap = BitmapFactory.decodeByteArray(
                    artwork.bytes,
                    0,
                    artwork.bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = LyricSourceSharePolicy.decodeSampleSize(bounds.outWidth.coerceAtLeast(1))
                    },
                ) ?: throw IOException("source page is not a decodable image")
                try {
                    val (width, height) = LyricSourceSharePolicy.pdfPageSizePoints(
                        page.widthPx,
                        page.heightPx,
                        page.renderDpi,
                    )
                    val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    pdfPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), paint)
                    document.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
            }
            target.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return target
    }
}
