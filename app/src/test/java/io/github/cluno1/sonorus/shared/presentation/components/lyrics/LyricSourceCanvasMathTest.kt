package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSourceCanvasMathTest {
    // A 1000 x 2000 viewport showing a canvas 1000 wide and 5000 tall at fit width.
    private val bounds = LyricSourceCanvasBounds(
        viewportWidth = 1000f,
        viewportHeight = 2000f,
        contentWidth = 1000f,
        contentHeight = 5000f,
        maxScale = 4f,
    )

    @Test
    fun `复原状态是适应宽度并停在第一页顶部`() {
        assertEquals(LyricSourceCanvasTransform(scale = 1f, offsetX = 0f, offsetY = 0f), bounds.fit())
        assertTrue(bounds.isFit(bounds.fit()))
    }

    @Test
    fun `放大后内容边缘不能被拖进视口内部`() {
        val zoomed = LyricSourceCanvasTransform(scale = 2f, offsetX = 0f, offsetY = 0f)
        // Canvas is 2000 wide now: x may go from -1000 to 0, never past either edge.
        val pastLeft = bounds.pan(zoomed, dx = 300f, dy = 0f, rubberBand = false)
        assertEquals(0f, pastLeft.offsetX, 0.001f)
        val pastRight = bounds.pan(zoomed, dx = -5000f, dy = 0f, rubberBand = false)
        assertEquals(-1000f, pastRight.offsetX, 0.001f)
        // 10000 tall: y from -8000 to 0.
        val pastBottom = bounds.pan(zoomed, dx = 0f, dy = -99999f, rubberBand = false)
        assertEquals(-8000f, pastBottom.offsetY, 0.001f)
    }

    @Test
    fun `内容比视口窄的方向居中且拖不动`() {
        val wideViewport = LyricSourceCanvasBounds(
            viewportWidth = 1600f,
            viewportHeight = 1000f,
            contentWidth = 1000f,
            contentHeight = 600f,
            maxScale = 4f,
        )
        val dragged = wideViewport.pan(wideViewport.fit(), dx = 250f, dy = 180f, rubberBand = false)
        assertEquals(300f, dragged.offsetX, 0.001f)
        assertEquals(0f, dragged.offsetY, 0.001f)
    }

    @Test
    fun `以双指中心缩放时手指下的内容不动`() {
        val start = LyricSourceCanvasTransform(scale = 1.5f, offsetX = -200f, offsetY = -900f)
        val pivotX = 420f
        val pivotY = 1300f
        val canvasX = (pivotX - start.offsetX) / start.scale
        val canvasY = (pivotY - start.offsetY) / start.scale

        val zoomed = bounds.zoom(start, factor = 1.6f, pivotX = pivotX, pivotY = pivotY, elastic = true)

        assertEquals(2.4f, zoomed.scale, 0.001f)
        assertEquals(pivotX, canvasX * zoomed.scale + zoomed.offsetX, 0.01f)
        assertEquals(pivotY, canvasY * zoomed.scale + zoomed.offsetY, 0.01f)
    }

    @Test
    fun `缩放上限与捏合时的弹性下限`() {
        val atFit = bounds.fit()
        assertEquals(4f, bounds.zoom(atFit, 10f, 0f, 0f, elastic = true).scale, 0.001f)
        assertEquals(1f * LYRIC_SOURCE_MIN_ELASTIC_SCALE, bounds.zoom(atFit, 0.1f, 0f, 0f, elastic = true).scale, 0.001f)
        assertEquals(1f, bounds.zoom(atFit, 0.1f, 0f, 0f, elastic = false).scale, 0.001f)
    }

    @Test
    fun `捏到适应宽度以下松手回到适应宽度`() {
        val pinched = bounds.zoom(bounds.fit(), 0.9f, 500f, 1000f, elastic = true)
        assertFalse(bounds.isWithinBounds(pinched))
        val settled = bounds.settle(pinched, 500f, 1000f)
        assertTrue(bounds.isWithinBounds(settled))
        assertEquals(1f, settled.scale, 0.001f)
    }

    @Test
    fun `拖过边界时只跟随一部分手指位移`() {
        val atTop = bounds.fit()
        val pulled = bounds.pan(atTop, dx = 0f, dy = 100f, rubberBand = true)
        assertEquals(100f * LYRIC_SOURCE_RUBBER_BAND, pulled.offsetY, 0.001f)
        // Moving back towards the range follows the finger fully.
        val back = bounds.pan(pulled, dx = 0f, dy = -20f, rubberBand = true)
        assertEquals(pulled.offsetY - 20f, back.offsetY, 0.001f)
        assertEquals(0f, bounds.settle(pulled, 0f, 0f).offsetY, 0.001f)
    }

    @Test
    fun `适应宽度时双击以点击处放大到两倍`() {
        val doubled = bounds.doubleTap(bounds.fit(), x = 600f, y = 800f)
        assertEquals(LYRIC_SOURCE_DOUBLE_TAP_SCALE, doubled.scale, 0.001f)
        assertEquals(600f, 600f * doubled.scale + doubled.offsetX, 0.01f)
        assertEquals(800f, 800f * doubled.scale + doubled.offsetY, 0.01f)
    }

    @Test
    fun `放大时双击回到适应宽度并停在正在看的页`() {
        // Reading around canvas y = 3000 at 3x.
        val zoomed = LyricSourceCanvasTransform(scale = 3f, offsetX = -900f, offsetY = -8000f)
        val reset = bounds.doubleTap(zoomed, x = 500f, y = 1000f)
        assertEquals(1f, reset.scale, 0.001f)
        assertEquals(0f, reset.offsetX, 0.001f)
        // The tapped canvas point (y = 3000) stays in view instead of jumping to the first page.
        val tappedCanvasY = (1000f - zoomed.offsetY) / zoomed.scale
        val shownTop = bounds.visibleContentTop(reset)
        val shownBottom = bounds.visibleContentBottom(reset)
        assertTrue(tappedCanvasY in shownTop..shownBottom)
        assertTrue(reset.offsetY < 0f)
    }

    @Test
    fun `竖向滚动过不算偏离复原状态，放大或横移才算`() {
        assertTrue(bounds.isFit(LyricSourceCanvasTransform(scale = 1f, offsetX = 0f, offsetY = -1800f)))
        assertFalse(bounds.isFit(LyricSourceCanvasTransform(scale = 1.3f, offsetX = 0f, offsetY = 0f)))
        val narrow = LyricSourceCanvasBounds(1600f, 1000f, 1000f, 600f, maxScale = 4f)
        assertFalse(narrow.isFit(LyricSourceCanvasTransform(scale = 1f, offsetX = 0f, offsetY = 0f)))
    }

    @Test
    fun `适应宽度时横向拖不动，放大后才能横向拖`() {
        assertFalse(bounds.canPanHorizontally(1f))
        assertTrue(bounds.canPanHorizontally(1.5f))
    }

    @Test
    fun `页面竖排并只返回视口附近的页`() {
        val layout = layoutLyricSourcePages(
            heightToWidthRatios = listOf(1.5f, 1f, 2f),
            viewportWidth = 1000f,
            horizontalPadding = 50f,
            labelHeight = 40f,
            gap = 20f,
            bottomPadding = 100f,
        )
        assertEquals(900f, layout.imageWidth, 0.001f)
        assertEquals(LyricSourcePageSlot(labelTop = 0f, imageTop = 40f, imageHeight = 1350f), layout.slots[0])
        assertEquals(LyricSourcePageSlot(labelTop = 1410f, imageTop = 1450f, imageHeight = 900f), layout.slots[1])
        assertEquals(2370f, layout.slots[2].labelTop, 0.001f)
        assertEquals(2370f + 40f + 1800f + 100f, layout.contentHeight, 0.001f)

        assertEquals(0..0, layout.pagesIn(0f, 1000f))
        assertEquals(1..2, layout.pagesIn(2000f, 2500f))
        assertTrue(layout.pagesIn(9000f, 9500f).isEmpty())
    }

    @Test
    fun `放大上限按页图分辨率取值并限制在二到四倍之间`() {
        assertEquals(2f, lyricSourceMaxScale(maxPageWidthPx = 1000, imageWidthOnCanvas = 1000f), 0.001f)
        assertEquals(3f, lyricSourceMaxScale(maxPageWidthPx = 2000, imageWidthOnCanvas = 1000f), 0.001f)
        assertEquals(4f, lyricSourceMaxScale(maxPageWidthPx = 9000, imageWidthOnCanvas = 1000f), 0.001f)
    }
}
