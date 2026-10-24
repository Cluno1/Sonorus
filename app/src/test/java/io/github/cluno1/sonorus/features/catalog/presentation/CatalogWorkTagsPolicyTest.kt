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
