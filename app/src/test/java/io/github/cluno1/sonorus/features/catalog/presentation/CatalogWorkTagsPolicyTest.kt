package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.presentation.CatalogWorkTagsPolicy.Check
import io.github.cluno1.sonorus.features.catalog.presentation.CatalogWorkTagsPolicy.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogWorkTagsPolicyTest {
    @Test
    fun `首尾空白和中间连续空白被规范化`() {
        assertEquals(Check.Valid(listOf("庄严 赞美", "多声部")), CatalogWorkTagsPolicy.check(listOf("  庄严   赞美 ", "多声部")))
    }

    @Test
    fun `空列表合法，用于清空标签`() {
        assertEquals(Check.Valid(emptyList()), CatalogWorkTagsPolicy.check(emptyList()))
    }

    @Test
    fun `忽略大小写的重复标在第二个位置`() {
        val result = CatalogWorkTagsPolicy.check(listOf("Hymn", "hymn "))
        assertEquals(Check.Invalid(mapOf(1 to Problem.DUPLICATE), tooMany = false), result)
    }

    @Test
    fun `空白标签标为空`() {
        val result = CatalogWorkTagsPolicy.check(listOf("ok", "   "))
        assertEquals(Check.Invalid(mapOf(1 to Problem.EMPTY), tooMany = false), result)
    }

    @Test
    fun `按码点计长度，40 个汉字合法、41 个超长`() {
        assertEquals(Check.Valid(listOf("字".repeat(40))), CatalogWorkTagsPolicy.check(listOf("字".repeat(40))))
        val emoji = "😀".repeat(40) // 40 code points, 80 UTF-16 units
        assertEquals(Check.Valid(listOf(emoji)), CatalogWorkTagsPolicy.check(listOf(emoji)))
        assertEquals(
            Check.Invalid(mapOf(0 to Problem.TOO_LONG), tooMany = false),
            CatalogWorkTagsPolicy.check(listOf("字".repeat(41))),
        )
    }

    @Test
    fun `超过 50 个标签判为过多`() {
        val result = CatalogWorkTagsPolicy.check((1..51).map { "t$it" })
        assertTrue(result is Check.Invalid && result.tooMany)
    }
}

class CatalogWorkTagsPickerTest {
    private val catalog = listOf(
        io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag("赞美", "赞美", 42),
        io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag("duet", "Duet", 7),
        io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag("多声部", "多声部", 3),
    )

    @Test
    fun `空关键字列出全部且保持常用顺序`() {
        assertEquals(listOf("赞美", "Duet", "多声部"), CatalogWorkTagsPolicy.filterCatalog(catalog, " ").map { it.label })
    }

    @Test
    fun `关键字忽略大小写做子串筛选`() {
        assertEquals(listOf("Duet"), CatalogWorkTagsPolicy.filterCatalog(catalog, "UE").map { it.label })
        assertEquals(listOf("多声部"), CatalogWorkTagsPolicy.filterCatalog(catalog, "声").map { it.label })
    }

    @Test
    fun `输入与已有标签只差大小写时复用已有写法，不新建近似重复`() {
        assertEquals("Duet", CatalogWorkTagsPolicy.resolveEntry(" duet ", catalog, selected = emptyList()))
        org.junit.Assert.assertFalse(CatalogWorkTagsPolicy.isNewTag("DUET", catalog))
    }

    @Test
    fun `全新标签按规范化后的写法新增`() {
        assertEquals("安静 敬拜", CatalogWorkTagsPolicy.resolveEntry("  安静   敬拜 ", catalog, emptyList()))
        assertTrue(CatalogWorkTagsPolicy.isNewTag("安静 敬拜", catalog))
    }

    @Test
    fun `已选、空白或超长时不产生新条目`() {
        assertEquals(null, CatalogWorkTagsPolicy.resolveEntry("赞美", catalog, selected = listOf("赞美")))
        assertEquals(null, CatalogWorkTagsPolicy.resolveEntry("   ", catalog, emptyList()))
        assertEquals(null, CatalogWorkTagsPolicy.resolveEntry("字".repeat(41), catalog, emptyList()))
    }
}
