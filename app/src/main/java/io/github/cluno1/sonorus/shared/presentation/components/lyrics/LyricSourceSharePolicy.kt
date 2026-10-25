package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.content.Intent
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * issue 123: sharing the source pages of a song (the pages of its hymnal PDF) to Signal & co.
 *
 * Decisions (user, 2026-10-25): always exactly one image. A one-page song shares its page as is;
 * several pages are combined into one image. Never the whole original book, no PDF (dropped: "nobody
 * would use it"), no caption.
 *
 * Why a two-column grid and not one long strip: Signal (and most messengers) shrink the long edge of
 * a sent image to roughly 1600–2048 px. Three pages stacked into 1240 × 5262 would arrive about
 * 380 px wide and unreadable; two columns keep the combined image close to square, so each page
 * survives the compression at a readable size. Pages read left to right, then top to bottom.
 *
 * Room for later without touching the UI or the intent code: [LyricSourceShareRequest.caption]
 * already flows into the intent as EXTRA_TEXT; today [LyricSourceSharePolicy.caption] decides it is
 * always null (a source / rights note would go there).
 */
internal data class LyricSourceShareRequest(
    /** Base of the shared file name: the song / Work title, else the document title. */
    val title: String,
    /** Pages in reading order; never empty. */
    val pages: List<CatalogLyricSourceImage>,
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

/** Where one page lands in the combined image, in output pixels. */
internal data class LyricSourceShareCell(val left: Int, val top: Int, val width: Int, val height: Int)

internal data class LyricSourceShareCombinedLayout(
    val width: Int,
    val height: Int,
    val cells: List<LyricSourceShareCell>,
)

internal object LyricSourceSharePolicy {
    /** Subdirectory of cacheDir; also the FileProvider `cache-path` (res/xml/file_paths.xml). */
    const val DIRECTORY = "shared-lyric-sources"

    /** Pages per row in a combined image. */
    const val COMBINED_COLUMNS = 2

    /** Widest a page is drawn in a combined image; source pages are rarely sharper than this. */
    const val COMBINED_MAX_CELL_WIDTH_PX = 1600

    /** White space between pages and around the combined image. */
    const val COMBINED_GAP_PX = 24

    /** Pixel budget of a combined image (~32 MB as RGB_565); more pages shrink every cell instead. */
    const val COMBINED_MAX_PIXELS = 16_000_000

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

    fun fileName(base: String, mediaType: String): String = "$base.${extensionOf(mediaType)}"

    fun extensionOf(mediaType: String): String = when (mediaType.substringBefore(';').trim().lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "bin"
    }

    fun intentSpec(mimeType: String, caption: String?): LyricSourceShareIntentSpec =
        LyricSourceShareIntentSpec(
            action = Intent.ACTION_SEND,
            mimeType = mimeType.substringBefore(';').trim().lowercase(),
            text = caption?.takeIf(String::isNotBlank),
        )

    /**
     * Grid for combining pages of the given pixel sizes into one image: [COMBINED_COLUMNS] per row,
     * every page scaled to one cell width (the narrowest page, at most [COMBINED_MAX_CELL_WIDTH_PX]),
     * rows as tall as their tallest page, everything top-aligned with [COMBINED_GAP_PX] of white.
     * Shrinks uniformly to stay within [COMBINED_MAX_PIXELS].
     */
    fun combinedLayout(pageSizes: List<Pair<Int, Int>>): LyricSourceShareCombinedLayout {
        require(pageSizes.isNotEmpty()) { "nothing to combine" }
        require(pageSizes.all { (w, h) -> w > 0 && h > 0 }) { "page sizes must be positive" }
        val naturalCell = minOf(pageSizes.minOf { it.first }, COMBINED_MAX_CELL_WIDTH_PX)
        val natural = gridAt(pageSizes, naturalCell)
        val pixels = natural.width.toLong() * natural.height
        if (pixels <= COMBINED_MAX_PIXELS) return natural
        val shrink = sqrt(COMBINED_MAX_PIXELS.toDouble() / pixels)
        // Gaps do not shrink, so step down until the budget really holds.
        var cell = (naturalCell * shrink).toInt().coerceAtLeast(1)
        var layout = gridAt(pageSizes, cell)
        while (layout.width.toLong() * layout.height > COMBINED_MAX_PIXELS && cell > 1) {
            cell = (cell * 0.95).toInt().coerceAtLeast(1)
            layout = gridAt(pageSizes, cell)
        }
        return layout
    }

    private fun gridAt(pageSizes: List<Pair<Int, Int>>, cellWidth: Int): LyricSourceShareCombinedLayout {
        val columns = minOf(COMBINED_COLUMNS, pageSizes.size)
        val heights = pageSizes.map { (w, h) -> (h.toDouble() * cellWidth / w).roundToInt().coerceAtLeast(1) }
        val rows = ceil(pageSizes.size / columns.toDouble()).toInt()
        val cells = ArrayList<LyricSourceShareCell>(pageSizes.size)
        var top = COMBINED_GAP_PX
        for (row in 0 until rows) {
            val indices = (row * columns until minOf((row + 1) * columns, pageSizes.size))
            for ((column, index) in indices.withIndex()) {
                cells += LyricSourceShareCell(
                    left = COMBINED_GAP_PX + column * (cellWidth + COMBINED_GAP_PX),
                    top = top,
                    width = cellWidth,
                    height = heights[index],
                )
            }
            top += indices.maxOf { heights[it] } + COMBINED_GAP_PX
        }
        return LyricSourceShareCombinedLayout(
            width = COMBINED_GAP_PX + columns * (cellWidth + COMBINED_GAP_PX),
            height = top,
            cells = cells,
        )
    }

    /** Power-of-two BitmapFactory sample size that brings [widthPx] down to at most [maxWidthPx]. */
    fun decodeSampleSize(widthPx: Int, maxWidthPx: Int): Int {
        var sample = 1
        while (widthPx / (sample * 2) >= maxWidthPx) sample *= 2
        return sample
    }
}

/**
 * Lets one tap start one share: further taps are ignored until the running one finishes, so a
 * double tap on a slow network never opens two share sheets or writes the file twice.
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
