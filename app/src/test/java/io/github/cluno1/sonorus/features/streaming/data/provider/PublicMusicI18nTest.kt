package io.github.cluno1.sonorus.features.streaming.data.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicMusicI18nTest {
    private val sourceRoot = "src/main/java/io/github/cluno1/sonorus/features/streaming"
    private val guardedFiles = listOf(
        "data/provider/PublicMusicApiClient.kt",
        "data/provider/PublicMusicException.kt",
        "data/provider/PublicMusicMetadataSync.kt",
        "presentation/screens/PublicMusicMetadataScreen.kt",
        "presentation/screens/StreamingServiceSetupScreen.kt",
        "presentation/viewmodel/StreamingMusicViewModel.kt",
    )

    private fun strings(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue(file.path, file.exists())
        return Regex("<string name=\"(public_music_[a-z_]+)\">(.*?)</string>").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun publicMusicSourcesContainNoChineseStringLiterals() {
        val literal = Regex("\"[^\"\\n]*\\p{IsHan}[^\"\\n]*\"")
        guardedFiles.forEach { path ->
            val file = File("$sourceRoot/$path")
            assertTrue(file.path, file.exists())
            assertEquals(path, emptyList<String>(), literal.findAll(file.readText()).map { it.value }.toList())
        }
    }

    @Test
    fun everyLanguageHasTheSameKeysAndFormatArguments() {
        val format = Regex("%\\d+\\$[sd]")
        val english = strings("values")
        assertTrue(english.isNotEmpty())
        listOf("values-zh", "values-zh-rTW").forEach { dir ->
            val translated = strings(dir)
            assertEquals(dir, english.keys, translated.keys)
            english.forEach { (key, value) ->
                assertEquals("$dir/$key", format.findAll(value).map { it.value }.toList(), format.findAll(translated.getValue(key)).map { it.value }.toList())
            }
        }
    }

    @Test
    fun everyErrorKindHasItsOwnMessage() {
        val kinds = PublicMusicException.Kind.entries
        assertEquals(kinds.size, kinds.map { it.messageRes }.toSet().size)
    }

    @Test
    fun exceptionMessageDoesNotTriggerConnectionHeuristics() {
        PublicMusicException.Kind.entries.forEach { kind ->
            val message = PublicMusicException(kind).message.orEmpty().lowercase()
            assertTrue(message, "connect" !in message && "timeout" !in message)
        }
    }
}
