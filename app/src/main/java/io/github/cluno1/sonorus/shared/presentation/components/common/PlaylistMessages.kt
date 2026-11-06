package io.github.cluno1.sonorus.shared.presentation.components.common

import android.content.Context
import io.github.cluno1.sonorus.R

/** issue 169: the one message after adding several songs to a playlist; the name is the display name (Liked → 收藏). */
fun playlistBatchAddMessage(
    context: Context,
    successCount: Int,
    total: Int,
    playlistId: String,
    playlistName: String,
): String {
    val name = defaultPlaylistNameRes(playlistId, playlistName)?.let(context::getString) ?: playlistName
    return when {
        successCount <= 0 -> context.getString(R.string.msg_playlist_none_added)
        successCount >= total -> context.getString(R.string.msg_songs_added_to_playlist, successCount, name)
        else -> context.getString(R.string.msg_playlist_added_partial, successCount, total, name)
    }
}
