package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.content.Intent
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSourceSharePolicyTest {
    private fun page(
        documentId: String,
        physicalPage: Int,
        displayOrder: Int,
    ) = CatalogLyricSourceImage(
        linkId = "link-$documentId-$physicalPage",
        sourcePageId = "page-$documentId-$physicalPage",
        imageAssetId = "00000000-0000-4000-8000-00000000000$physicalPage",
        documentId = documentId,
        documentTitle = "歌本 $documentId",
        sourceKind = "pdf",
        sourceRef = null,
        physicalPageNumber = physicalPage,
        displayLabel = null,
        displayOrder = displayOrder,
        widthPx = 1240,
        heightPx = 1754,
        renderDpi = 150,
        ownerType = "work",
        ownerId = "work-1",
        languageRelations = emptyList(),
        note = null,
    )

    @Test
    fun `只分享所选文档的页并按显示顺序再按页码排列`() {
        val images = listOf(
            page("a", physicalPage = 12, displayOrder = 2),
            page("b", physicalPage = 1, displayOrder = 1),
            page("a", physicalPage = 11, displayOrder = 2),
            page("a", physicalPage = 30, displayOrder = 1),
        )
        val pages = LyricSourceSharePolicy.pagesOf(images, "a")
        assertEquals(listOf(30, 11, 12), pages.map(CatalogLyricSourceImage::physicalPageNumber))
    }

    @Test
    fun `文件名去掉非法字符并有兜底`() {
        assertEquals("祂的慈爱 _永远_ 长存", LyricSourceSharePolicy.fileBaseName("  祂的慈爱 /永远:\n长存. "))
        assertEquals("Heaven Song 103", LyricSourceSharePolicy.fileBaseName("Heaven   Song\t103"))
        assertEquals("lyrics-source", LyricSourceSharePolicy.fileBaseName(" ?* "))
        assertEquals(60, LyricSourceSharePolicy.fileBaseName("长".repeat(200)).length)
        assertEquals("歌.png", LyricSourceSharePolicy.fileName("歌", "image/png"))
        assertEquals("歌.jpg", LyricSourceSharePolicy.fileName("歌", "image/jpeg; charset=binary"))
    }

    @Test
    fun `当前不附带任何说明文字，未来的说明会进入 EXTRA_TEXT`() {
        // issue 123 decision: share the files only.
        assertNull(LyricSourceSharePolicy.caption("祂的慈爱永远长存", listOf(page("a", 1, 1))))
        val spec = LyricSourceSharePolicy.intentSpec("image/png", caption = null)
        assertEquals(Intent.ACTION_SEND, spec.action)
        assertEquals("image/png", spec.mimeType)
        assertNull(spec.text)
        assertNull(LyricSourceSharePolicy.intentSpec("image/png", caption = "  ").text)
        assertEquals("来源：某歌本", LyricSourceSharePolicy.intentSpec("image/png", caption = "来源：某歌本").text)
    }

    @Test
    fun `两页合成一张时左右并排，宽度统一为较窄页`() {
        val gap = LyricSourceSharePolicy.COMBINED_GAP_PX
        val layout = LyricSourceSharePolicy.combinedLayout(listOf(1240 to 1754, 1500 to 1500))
        assertEquals(
            listOf(
                LyricSourceShareCell(left = gap, top = gap, width = 1240, height = 1754),
                LyricSourceShareCell(left = gap + 1240 + gap, top = gap, width = 1240, height = 1240),
            ),
            layout.cells,
        )
        assertEquals(gap + 2 * (1240 + gap), layout.width)
        // The row is as tall as its tallest page.
        assertEquals(gap + 1754 + gap, layout.height)
    }

    @Test
    fun `三页按每行两页排列，第三页换行靠左`() {
        val gap = LyricSourceSharePolicy.COMBINED_GAP_PX
        val layout = LyricSourceSharePolicy.combinedLayout(List(3) { 1000 to 1400 })
        assertEquals(gap, layout.cells[2].left)
        assertEquals(gap + 1400 + gap, layout.cells[2].top)
        assertEquals(gap + 2 * (1400 + gap), layout.height)
        // Close to square, so a messenger shrinking the long edge keeps each page readable.
        assertTrue(layout.height.toFloat() / layout.width < 1.5f)
    }

    @Test
    fun `高分辨率页图的单元宽度不超过上限`() {
        val layout = LyricSourceSharePolicy.combinedLayout(listOf(4000 to 5000, 4000 to 5000))
        assertTrue(layout.cells.all { it.width == LyricSourceSharePolicy.COMBINED_MAX_CELL_WIDTH_PX })
    }

    @Test
    fun `页数很多时整体缩小以守住像素上限`() {
        val layout = LyricSourceSharePolicy.combinedLayout(List(12) { 1600 to 2263 })
        assertTrue(layout.width.toLong() * layout.height <= LyricSourceSharePolicy.COMBINED_MAX_PIXELS)
        assertTrue(layout.cells.first().width < LyricSourceSharePolicy.COMBINED_MAX_CELL_WIDTH_PX)
        assertEquals(12, layout.cells.size)
        // Cells never overlap within a row.
        assertTrue(layout.cells[1].left >= layout.cells[0].left + layout.cells[0].width)
    }

    @Test
    fun `解码采样不会把页图缩到单元宽度以下`() {
        assertEquals(1, LyricSourceSharePolicy.decodeSampleSize(1240, 1240))
        assertEquals(2, LyricSourceSharePolicy.decodeSampleSize(5000, 1600))
        assertEquals(4, LyricSourceSharePolicy.decodeSampleSize(6400, 1600))
    }

    @Test
    fun `连点只发起一次分享，完成后才能再次分享`() {
        val gate = LyricSourceShareGate()
        val started = (1..3).count { gate.tryStart() }
        assertEquals(1, started)
        assertTrue(gate.running)

        gate.finish()
        assertFalse(gate.running)
        assertTrue(gate.tryStart())
    }
}
