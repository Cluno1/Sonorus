package io.github.cluno1.sonorus.shared.presentation.components.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.shared.data.model.Playlist

/**
 * Sonorus: what the user sees as a local playlist's name.
 *
 * The three built-in playlists are stored with fixed English names ("Liked", "Recently Added",
 * "Most Played"), and MediaPlaybackService / MusicViewModel find them by id AND that stored name.
 * So the stored name must never change; only the displayed name is translated here.
 *
 * Both id and name must match: a streaming playlist whose server id happens to be "1", or a
 * default playlist the user renamed, keeps its own name.
 */
@StringRes
fun defaultPlaylistNameRes(id: String, name: String): Int? = when {
    id == "1" && name == "Liked" -> R.string.default_playlist_liked
    id == "2" && name == "Recently Added" -> R.string.default_playlist_recently_added
    id == "3" && name == "Most Played" -> R.string.default_playlist_most_played
    else -> null
}

fun Playlist.displayName(context: Context): String =
    defaultPlaylistNameRes(id, name)?.let(context::getString) ?: name

@Composable
fun Playlist.displayName(): String =
    defaultPlaylistNameRes(id, name)?.let { stringResource(it) } ?: name
