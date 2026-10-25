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
        assertEquals("祂的慈爱 _永远_ 长存",LyricSourceSharePolicy.fileBaseName("  祂的慈爱 /永远:\n长存. "))
        assertEquals("Heaven Song 103", LyricSourceSharePolicy.fileBaseName("Heaven   Song\t103"))
        assertEquals("lyrics-source", LyricSourceSharePolicy.fileBaseName(" ?* "))
        assertEquals(60, LyricSourceSharePolicy.fileBaseName("长".repeat(200)).length)
        assertEquals("歌-p12.png", LyricSourceSharePolicy.imageFileName("歌", 12, "image/png"))
        assertEquals("歌-p3.jpg", LyricSourceSharePolicy.imageFileName("歌", 3, "image/jpeg; charset=binary"))
        assertEquals("歌.pdf", LyricSourceSharePolicy.pdfFileName("歌"))
    }

    @Test
    fun `一个文件用 SEND，多个文件用 SEND_MULTIPLE 和最窄的公共类型`() {
        val single = LyricSourceSharePolicy.intentSpec(listOf("application/pdf"), caption = null)
        assertEquals(Intent.ACTION_SEND, single.action)
        assertEquals("application/pdf", single.mimeType)

        val samePng = LyricSourceSharePolicy.intentSpec(listOf("image/png", "image/png"), caption = null)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, samePng.action)
        assertEquals("image/png", samePng.mimeType)

        val mixedImages = LyricSourceSharePolicy.intentSpec(listOf("image/png", "image/jpeg"), caption = null)
        assertEquals("image/*", mixedImages.mimeType)
    }

    @Test
    fun `当前不附带任何说明文字，未来的说明会进入 EXTRA_TEXT`() {
        // issue 123 decision: share the files only.
        assertNull(LyricSourceSharePolicy.caption("祂的慈爱永远长存", listOf(page("a", 1, 1))))
        assertNull(LyricSourceSharePolicy.intentSpec(listOf("image/png"), caption = null).text)
        assertNull(LyricSourceSharePolicy.intentSpec(listOf("image/png"), caption = "  ").text)
        assertEquals("来源：某歌本", LyricSourceSharePolicy.intentSpec(listOf("image/png"), caption = "来源：某歌本").text)
    }

    @Test
    fun `PDF 页按扫描 DPI 换算成实际尺寸`() {
        // 1240 x 1754 px at 150 dpi is an A4 page: 595 x 842 pt.
        assertEquals(595 to 842, LyricSourceSharePolicy.pdfPageSizePoints(1240, 1754, 150))
    }

    @Test
    fun `大图按二的幂降采样到宽度上限以内`() {
        assertEquals(1, LyricSourceSharePolicy.decodeSampleSize(2480))
        assertEquals(2, LyricSourceSharePolicy.decodeSampleSize(2481))
        assertEquals(4, LyricSourceSharePolicy.decodeSampleSize(9000))
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
