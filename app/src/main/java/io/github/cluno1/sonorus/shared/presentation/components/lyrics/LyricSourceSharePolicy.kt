package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.content.Intent
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import kotlin.math.roundToInt

/**
 * issue 123: sharing the source pages of a song (the pages of its hymnal PDF) to Signal & co.
 *
 * Decisions (user, 2026-10-25): share the song's own pages — never the whole original book — as
 * images or as a small PDF of just those pages, with no caption. The model keeps room for what may
 * come later without touching the UI or the intent code:
 * - a new [LyricSourceShareFormat] (e.g. the original document) only needs its own preparation;
 * - [LyricSourceShareRequest.caption] already flows into the intent as EXTRA_TEXT; today
 *   [LyricSourceSharePolicy.caption] decides it is always null (a source / rights note would go there).
 */
internal enum class LyricSourceShareFormat { IMAGES, PDF }

internal data class LyricSourceShareRequest(
    /** Base of the shared file names: the song / Work title, else the document title. */
    val title: String,
    /** Pages in reading order; never empty. */
    val pages: List<CatalogLyricSourceImage>,
    val format: LyricSourceShareFormat,
    val caption: String? = null,
) {
    init {
        require(pages.isNotEmpty()) { "nothing to share" }
    }
}

/** Android-free description of the share intent, so its rules are unit-tested. */
internal data class LyricSourceShareIntentSpec(
    val action: String,
    val mimeType: String,
    val text: String?,
)

internal object LyricSourceSharePolicy {
    /** Subdirectory of cacheDir; also the FileProvider `cache-path` (res/xml/file_paths.xml). */
    const val DIRECTORY = "shared-lyric-sources"

    /** Long edge cap when decoding pages for the PDF; ~A4 at 300 dpi. */
    const val PDF_MAX_DECODE_WIDTH_PX = 2480

    private const val FALLBACK_NAME = "lyrics-source"
    private const val MAX_NAME_LENGTH = 60
    private val illegalFileNameChars = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val whitespace = Regex("\\s+")

    /** Today no caption is attached (issue 123 decision); the hook for a future source note. */
    @Suppress("UNUSED_PARAMETER")
    fun caption(title: String, pages: List<CatalogLyricSourceImage>): String? = null

    /** The selected document's pages in reading order: display order, then physical page. */
    fun pagesOf(images: List<CatalogLyricSourceImage>, documentId: String?): List<CatalogLyricSourceImage> =
        images.filter { it.documentId == documentId }
            .sortedWith(
                compareBy(CatalogLyricSourceImage::displayOrder)
                    .thenBy(CatalogLyricSourceImage::physicalPageNumber),
            )

    fun fileBaseName(title: String): String {
        // Whitespace first, so newlines and tabs read as spaces rather than becoming "_".
        val cleaned = title
            .replace(whitespace, " ")
            .replace(illegalFileNameChars, "_")
            .trim()
            .trim('.', ' ')
            .take(MAX_NAME_LENGTH)
            .trim()
        // A title of nothing but illegal characters would become "__": use the fallback instead.
        return cleaned.takeIf { name -> name.any(Char::isLetterOrDigit) } ?: FALLBACK_NAME
    }

    fun imageFileName(base: String, pageNumber: Int, mediaType: String): String =
        "$base-p$pageNumber.${extensionOf(mediaType)}"

    fun pdfFileName(base: String): String = "$base.pdf"

    fun extensionOf(mediaType: String): String = when (mediaType.substringBefore(';').trim().lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "application/pdf" -> "pdf"
        else -> "bin"
    }

    /** One file → SEND with its own type; several → SEND_MULTIPLE with the narrowest common type. */
    fun intentSpec(mimeTypes: List<String>, caption: String?): LyricSourceShareIntentSpec {
        require(mimeTypes.isNotEmpty()) { "nothing to share" }
        val types = mimeTypes.map { it.substringBefore(';').trim().lowercase() }
        val mimeType = when {
            types.distinct().size == 1 -> types.first()
            types.all { it.startsWith("image/") } -> "image/*"
            else -> "*/*"
        }
        return LyricSourceShareIntentSpec(
            action = if (types.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
            mimeType = mimeType,
            text = caption?.takeIf(String::isNotBlank),
        )
    }

    /** PDF page size in points (1/72 in), so the page prints at the size it was scanned at. */
    fun pdfPageSizePoints(widthPx: Int, heightPx: Int, renderDpi: Int): Pair<Int, Int> {
        val dpi = renderDpi.coerceAtLeast(1)
        return (widthPx * 72f / dpi).roundToInt().coerceAtLeast(1) to
            (heightPx * 72f / dpi).roundToInt().coerceAtLeast(1)
    }

    /** Power-of-two BitmapFactory sample size that brings [widthPx] down to at most [maxWidthPx]. */
    fun decodeSampleSize(widthPx: Int, maxWidthPx: Int = PDF_MAX_DECODE_WIDTH_PX): Int {
        var sample = 1
        while (widthPx / sample > maxWidthPx) sample *= 2
        return sample
    }
}

/**
 * Lets one tap start one share: further taps are ignored until the running one finishes, so a
 * double tap on a slow network never opens two share sheets or writes the files twice.
 */
internal class LyricSourceShareGate {
    var running: Boolean = false
        private set

    fun tryStart(): Boolean {
        if (running) return false
        running = true
        return true
    }

    fun finish() {
        running = false
    }
}
