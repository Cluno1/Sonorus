package io.github.cluno1.sonorus.features.catalog.presentation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRoomsCopyTest {
    private val retiredTerms = listOf("聚会", "聚會", "诗歌", "詩歌", "敬拜", "教会", "教會", "赞美", "讚美", "gathering", "worship", "church", "hymn")

    @Test
    fun roomCopyAvoidsRetiredReligiousTermsInEveryLanguage() {
        listOf("values", "values-zh", "values-zh-rTW").forEach { dir ->
            val file = File("src/main/res/$dir/catalog_rooms.xml")
            assertTrue(file.path, file.exists())
            val text = file.readText().lowercase()
            assertEquals(dir, emptyList<String>(), retiredTerms.filter { it in text })
        }
    }
}
