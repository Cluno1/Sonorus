package io.github.cluno1.sonorus.features.streaming.presentation.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.streaming.data.provider.PublicMusicException
import io.github.cluno1.sonorus.features.streaming.data.provider.publicMusicErrorMessage
import io.github.cluno1.sonorus.features.streaming.data.repository.StreamingMusicRepositoryImpl
import io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "PublicMusicMetadata"

/** issue 169: fixed text on screen; anything that is not a classified public-music failure goes to the log only. */
private fun Context.metadataFailureText(failure: Throwable): String {
    Log.w(TAG, "Public music metadata request failed: ${failure.javaClass.simpleName}", failure)
    return if (failure is PublicMusicException) publicMusicErrorMessage(failure)
    else getString(R.string.public_music_error_unknown)
}

@Composable
fun PublicMusicMetadataScreen(
    song: Song?,
    onSaved: (LanSongMetadata) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) {
        StreamingMusicModule.provideStreamingMusicRepository(context) as StreamingMusicRepositoryImpl
    }
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title.orEmpty()) }
    var artist by rememberSaveable(song?.id) { mutableStateOf(song?.artist.orEmpty()) }
    var album by rememberSaveable(song?.id) { mutableStateOf(song?.album.orEmpty()) }
    var lyrics by rememberSaveable(song?.id) { mutableStateOf("") }
    var artwork by rememberSaveable(song?.id) { mutableStateOf(song?.artworkUri?.toString().orEmpty()) }
    var avatar by rememberSaveable(song?.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song?.id) {
        if (song == null) return@LaunchedEffect
        busy = true
        try {
            repository.publicMusicMetadata(song)?.let { metadata ->
                title = metadata.fields["title"].orEmpty()
                artist = metadata.fields["artist"].orEmpty()
                album = metadata.fields["album"].orEmpty()
                lyrics = metadata.syncedLyrics.ifBlank { metadata.plainLyrics }
                artwork = metadata.artworkUrl.orEmpty()
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = context.metadataFailureText(failure)
        } finally {
            busy = false
        }
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.public_music_metadata_title), showBackButton = true, onBackClick = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.public_music_metadata_desc))
            if (song == null) {
                Text(stringResource(R.string.public_music_song_unavailable), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (busy) CircularProgressIndicator()
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.public_music_field_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.public_music_field_artist)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.public_music_field_album)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artwork, { artwork = it }, label = { Text(stringResource(R.string.public_music_field_artwork)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(avatar, { avatar = it }, label = { Text(stringResource(R.string.public_music_field_artist_avatar)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lyrics, { lyrics = it }, label = { Text(stringResource(R.string.public_music_field_lyrics)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val metadata = repository.publicMusicMetadata(song, manual = true)
                            if (metadata == null) {
                                // issue 169: previously nothing happened at all when nothing was found.
                                RhythmMessages.info(context, R.string.msg_streaming_metadata_not_found)
                            } else {
                                title = metadata.fields["title"].orEmpty()
                                artist = metadata.fields["artist"].orEmpty()
                                album = metadata.fields["album"].orEmpty()
                                lyrics = metadata.syncedLyrics.ifBlank { metadata.plainLyrics }
                                artwork = metadata.artworkUrl.orEmpty()
                                Log.i(TAG, "Public music metadata refetched and saved")
                                // Posted before onSaved: the screen navigates back right after.
                                RhythmMessages.success(context, R.string.msg_streaming_metadata_saved)
                                onSaved(metadata)
                            }
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) { error = context.metadataFailureText(failure) }
                        finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.public_music_refetch_and_save)) }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val providerId = LanSubsonicPlaybackPolicy.trackIdFromMediaId(song.id)
                                ?: throw PublicMusicException(PublicMusicException.Kind.INVALID_SONG_ID)
                            val saved = repository.publicMusicClient().saveManual(
                                providerId, title, artist, album, lyrics,
                                artwork.takeIf(String::isNotBlank), avatar.takeIf(String::isNotBlank),
                            )
                            repository.applyLanMetadata(saved)
                            Log.i(TAG, "Public music manual metadata saved")
                            // Posted before onSaved: the screen navigates back right after.
                            RhythmMessages.success(context, R.string.msg_streaming_metadata_saved)
                            onSaved(saved)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) { error = context.metadataFailureText(failure) }
                        finally { busy = false }
                    }
                },
                enabled = !busy && title.isNotBlank() && artist.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.public_music_save_manual)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
