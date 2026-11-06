package io.github.cluno1.sonorus.features.local.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** The heart button is called 收藏 in Chinese: no "Liked", no 点赞 / 按讚, no 喜欢的歌曲. */
class FavoriteWordingTest {
    private val keys = listOf(
        "player_favorite_add_description",
        "player_favorite_remove_description",
        "player_favorite_added_message",
        "player_favorite_removed_message",
        "settings_tab_liked",
        "library_no_liked_songs",
        "notification_streaming_liked_title",
        "notification_streaming_liked_added",
        "onboarding_gesture_doubletap_desc",
        "player_chip_favorite",
        "library_category_favorites",
        "default_playlist_liked",
    )
    private val retired = listOf("liked", "点赞", "按讚", "喜欢的歌曲", "喜歡的歌曲", "最爱", "最愛", "最喜欢", "最喜歡")

    private fun strings(dir: String): Map<String, String> =
        File("src/main/res/$dir").listFiles { f -> f.extension == "xml" }.orEmpty()
            .flatMap { Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(it.readText()).toList() }
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun `简体和繁体的收藏文案都写成收藏且不再出现 Liked`() {
        listOf("values-zh", "values-zh-rTW").forEach { dir ->
            val values = strings(dir)
            val wrong = keys.filter { key ->
                val v = values[key]
                v == null || "收藏" !in v || retired.any { it in v.lowercase() }
            }
            assertEquals(dir, emptyList<String>(), wrong)
        }
    }

    @Test
    fun `英文仍然叫 Liked`() {
        assertEquals("Added to Liked", strings("values")["player_favorite_added_message"])
    }
}
