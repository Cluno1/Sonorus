package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import kotlin.math.abs

/**
 * issue 123: geometry of the lyric source canvas — every page of one source document stacked as a
 * single bounded canvas that the reader pinches and drags as a whole, like a PDF reader.
 *
 * Pure on purpose (no Compose, no Android): bounds, pivot zoom and the reset rules are where this
 * kind of viewer silently goes wrong, so they are unit-tested here and the composable only applies
 * the results.
 *
 * Coordinates: canvas pixels at scale 1 ("fit width": the canvas is exactly as wide as the
 * viewport); a canvas point p is drawn at `p * scale + offset` in the viewport.
 */

/** One page on the canvas at scale 1: its caption row, then the image. */
internal data class LyricSourcePageSlot(
    val labelTop: Float,
    val imageTop: Float,
    val imageHeight: Float,
) {
    val bottom: Float get() = imageTop + imageHeight
}

internal data class LyricSourceCanvasLayout(
    val contentWidth: Float,
    val contentHeight: Float,
    val imageLeft: Float,
    val imageWidth: Float,
    val slots: List<LyricSourcePageSlot>,
) {
    /** Indices of pages that intersect the canvas band [top, bottom]; empty when none do. */
    fun pagesIn(top: Float, bottom: Float): IntRange {
        val first = slots.indexOfFirst { it.bottom >= top }
        if (first < 0) return IntRange.EMPTY
        val last = slots.indexOfLast { it.labelTop <= bottom }
        return if (last < first) IntRange.EMPTY else first..last
    }
}

/**
 * Stacks pages of the given height/width ratios under each other, each with a caption row of
 * [labelHeight] above it, [gap] between pages and [bottomPadding] after the last one.
 */
internal fun layoutLyricSourcePages(
    heightToWidthRatios: List<Float>,
    viewportWidth: Float,
    horizontalPadding: Float,
    labelHeight: Float,
    gap: Float,
    bottomPadding: Float,
): LyricSourceCanvasLayout {
    val imageWidth = (viewportWidth - 2 * horizontalPadding).coerceAtLeast(1f)
    var y = 0f
    val slots = heightToWidthRatios.mapIndexed { index, ratio ->
        if (index > 0) y += gap
        val labelTop = y
        val imageTop = labelTop + labelHeight
        val imageHeight = imageWidth * ratio
        y = imageTop + imageHeight
        LyricSourcePageSlot(labelTop = labelTop, imageTop = imageTop, imageHeight = imageHeight)
    }
    return LyricSourceCanvasLayout(
        contentWidth = viewportWidth,
        contentHeight = y + bottomPadding,
        imageLeft = horizontalPadding,
        imageWidth = imageWidth,
        slots = slots,
    )
}

internal data class LyricSourceCanvasTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/** Double tap at fit width zooms to this scale around the tapped point. */
internal const val LYRIC_SOURCE_DOUBLE_TAP_SCALE = 2f

/** While pinching, the canvas may shrink this far below fit width before snapping back. */
internal const val LYRIC_SOURCE_MIN_ELASTIC_SCALE = 0.85f

/** Fraction of a drag that still moves the canvas once it is past its bounds (rubber band). */
internal const val LYRIC_SOURCE_RUBBER_BAND = 0.35f

/**
 * Highest zoom for pages [maxPageWidthPx] wide drawn [imageWidthOnCanvas] wide at fit width:
 * up to 1.5 source pixels per screen pixel, at least [LYRIC_SOURCE_DOUBLE_TAP_SCALE], at most 4x.
 */
internal fun lyricSourceMaxScale(maxPageWidthPx: Int, imageWidthOnCanvas: Float): Float {
    if (imageWidthOnCanvas <= 0f) return LYRIC_SOURCE_DOUBLE_TAP_SCALE
    return (maxPageWidthPx * 1.5f / imageWidthOnCanvas).coerceIn(LYRIC_SOURCE_DOUBLE_TAP_SCALE, 4f)
}

/**
 * The fixed range the canvas lives in. Along an axis where the scaled canvas is larger than the
 * viewport its edges may never come inside the viewport; where it is smaller it is centred
 * horizontally and pinned to the top vertically, and cannot be dragged.
 */
internal class LyricSourceCanvasBounds(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val maxScale: Float,
    val minScale: Float = 1f,
) {
    fun offsetRangeX(scale: Float): ClosedFloatingPointRange<Float> {
        val width = contentWidth * scale
        return if (width <= viewportWidth) {
            val centred = (viewportWidth - width) / 2f
            centred..centred
        } else {
            (viewportWidth - width)..0f
        }
    }

    fun offsetRangeY(scale: Float): ClosedFloatingPointRange<Float> {
        val height = contentHeight * scale
        return if (height <= viewportHeight) 0f..0f else (viewportHeight - height)..0f
    }

    /** False while the canvas is no wider than the viewport, i.e. a sideways drag cannot move it. */
    fun canPanHorizontally(scale: Float): Boolean = offsetRangeX(scale).let { it.endInclusive - it.start > OFFSET_EPSILON }

    /** The reset state: fit width, top of the first page. */
    fun fit(): LyricSourceCanvasTransform = clamp(LyricSourceCanvasTransform(scale = minScale))

    /** Scale limited to [minScale, maxScale] and offsets pulled back inside the range. */
    fun clamp(transform: LyricSourceCanvasTransform): LyricSourceCanvasTransform {
        val scale = transform.scale.coerceIn(minScale, maxScale)
        return LyricSourceCanvasTransform(
            scale = scale,
            offsetX = transform.offsetX.coerceIn(offsetRangeX(scale)),
            offsetY = transform.offsetY.coerceIn(offsetRangeY(scale)),
        )
    }

    fun isWithinBounds(transform: LyricSourceCanvasTransform): Boolean = clamp(transform).nearlyEquals(transform)

    /** True at fit width and not moved sideways; vertical position is free (it is just reading). */
    fun isFit(transform: LyricSourceCanvasTransform): Boolean =
        abs(transform.scale - minScale) < SCALE_EPSILON &&
            abs(transform.offsetX - offsetRangeX(minScale).start) < OFFSET_EPSILON

    /**
     * Scales by [factor] keeping the canvas point under ([pivotX], [pivotY]) where it is. With
     * [elastic] the scale may dip to [LYRIC_SOURCE_MIN_ELASTIC_SCALE] × [minScale] mid-pinch.
     */
    fun zoom(
        transform: LyricSourceCanvasTransform,
        factor: Float,
        pivotX: Float,
        pivotY: Float,
        elastic: Boolean,
    ): LyricSourceCanvasTransform {
        val lowest = if (elastic) minScale * LYRIC_SOURCE_MIN_ELASTIC_SCALE else minScale
        val scale = (transform.scale * factor).coerceIn(lowest, maxScale)
        val ratio = scale / transform.scale
        return LyricSourceCanvasTransform(
            scale = scale,
            offsetX = pivotX - (pivotX - transform.offsetX) * ratio,
            offsetY = pivotY - (pivotY - transform.offsetY) * ratio,
        )
    }

    /**
     * Moves by ([dx], [dy]). With [rubberBand] a move that goes further past the range only
     * travels [LYRIC_SOURCE_RUBBER_BAND] of the finger; without it the result is clamped.
     */
    fun pan(
        transform: LyricSourceCanvasTransform,
        dx: Float,
        dy: Float,
        rubberBand: Boolean,
    ): LyricSourceCanvasTransform {
        val moved = transform.copy(
            offsetX = panAxis(transform.offsetX, dx, offsetRangeX(transform.scale), rubberBand),
            offsetY = panAxis(transform.offsetY, dy, offsetRangeY(transform.scale), rubberBand),
        )
        return if (rubberBand) moved else clamp(moved)
    }

    /** Where a released gesture comes to rest: back to fit width if pinched below it, else clamped. */
    fun settle(transform: LyricSourceCanvasTransform, pivotX: Float, pivotY: Float): LyricSourceCanvasTransform =
        if (transform.scale < minScale) {
            clamp(zoom(transform, minScale / transform.scale, pivotX, pivotY, elastic = false))
        } else {
            clamp(transform)
        }

    /**
     * Double tap at ([x], [y]): zoomed in → back to fit width around that point, which keeps the
     * page being read in view instead of jumping to the first page; at fit width → zoom in there.
     */
    fun doubleTap(transform: LyricSourceCanvasTransform, x: Float, y: Float): LyricSourceCanvasTransform {
        val target = if (transform.scale > minScale + SCALE_EPSILON) minScale else LYRIC_SOURCE_DOUBLE_TAP_SCALE
        return clamp(zoom(transform, target / transform.scale, x, y, elastic = false))
    }

    /** The canvas band [top, bottom] (scale-1 canvas pixels) the viewport currently shows. */
    fun visibleContentTop(transform: LyricSourceCanvasTransform): Float = -transform.offsetY / transform.scale

    fun visibleContentBottom(transform: LyricSourceCanvasTransform): Float =
        (viewportHeight - transform.offsetY) / transform.scale

    private fun panAxis(
        offset: Float,
        delta: Float,
        range: ClosedFloatingPointRange<Float>,
        rubberBand: Boolean,
    ): Float {
        val next = offset + delta
        if (!rubberBand) return next
        val goingFurtherOut = (next > range.endInclusive && delta > 0f) || (next < range.start && delta < 0f)
        return if (goingFurtherOut) offset + delta * LYRIC_SOURCE_RUBBER_BAND else next
    }

    private fun LyricSourceCanvasTransform.nearlyEquals(other: LyricSourceCanvasTransform): Boolean =
        abs(scale - other.scale) < SCALE_EPSILON &&
            abs(offsetX - other.offsetX) < OFFSET_EPSILON &&
            abs(offsetY - other.offsetY) < OFFSET_EPSILON

    private companion object {
        const val SCALE_EPSILON = 0.01f
        const val OFFSET_EPSILON = 1f
    }
}
