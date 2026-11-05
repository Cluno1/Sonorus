package io.github.cluno1.sonorus.features.local.data.model

import io.github.cluno1.sonorus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteChangeTest {
    @Test
    fun `收藏状态没有变化时不提示成功`() {
        assertEquals(FavoriteChange.UNCHANGED, FavoriteChange.between(wasFavorite = false, isFavorite = false))
        assertEquals(FavoriteChange.UNCHANGED, FavoriteChange.between(wasFavorite = true, isFavorite = true))
        assertNull(FavoriteChange.UNCHANGED.messageRes)
    }

    @Test
    fun `实际加入后才提示已添加`() {
        val change = FavoriteChange.between(wasFavorite = false, isFavorite = true)
        assertEquals(R.string.player_favorite_added_message, change.messageRes)
    }

    @Test
    fun `实际移除后才提示已移除`() {
        val change = FavoriteChange.between(wasFavorite = true, isFavorite = false)
        assertEquals(R.string.player_favorite_removed_message, change.messageRes)
    }
}
