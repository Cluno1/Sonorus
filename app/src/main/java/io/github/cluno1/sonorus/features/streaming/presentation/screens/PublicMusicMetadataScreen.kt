package io.github.cluno1.sonorus.features.streaming.presentation.screens

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
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.features.streaming.data.repository.StreamingMusicRepositoryImpl
import io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import kotlinx.coroutines.launch

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
        } catch (failure: Exception) {
            error = failure.message
        } finally {
            busy = false
        }
    }

    CollapsibleHeaderScreen(
        title = "公共音乐元数据", showBackButton = true, onBackClick = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("优先显示后端已保存的信息。可以重新从公共来源获取，或手动修改后推送到后端。")
            if (song == null) {
                Text("歌曲不可用", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (busy) CircularProgressIndicator()
            OutlinedTextField(title, { title = it }, label = { Text("歌名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, label = { Text("歌手") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(album, { album = it }, label = { Text("专辑") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artwork, { artwork = it }, label = { Text("封面地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(avatar, { avatar = it }, label = { Text("歌手头像地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lyrics, { lyrics = it }, label = { Text("歌词 / LRC") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            repository.publicMusicMetadata(song, manual = true)?.let { metadata ->
                                title = metadata.fields["title"].orEmpty()
                                artist = metadata.fields["artist"].orEmpty()
                                album = metadata.fields["album"].orEmpty()
                                lyrics = metadata.syncedLyrics.ifBlank { metadata.plainLyrics }
                                artwork = metadata.artworkUrl.orEmpty()
                                onSaved(metadata)
                            }
                        } catch (failure: Exception) { error = failure.message }
                        finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("从公共来源手动重新获取并保存") }
            Button(
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val providerId = requireNotNull(LanSubsonicPlaybackPolicy.trackIdFromMediaId(song.id))
                            val saved = repository.publicMusicClient().saveManual(
                                providerId, title, artist, album, lyrics,
                                artwork.takeIf(String::isNotBlank), avatar.takeIf(String::isNotBlank),
                            )
                            repository.applyLanMetadata(saved)
                            onSaved(saved)
                        } catch (failure: Exception) { error = failure.message }
                        finally { busy = false }
                    }
                },
                enabled = !busy && title.isNotBlank() && artist.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存手动元数据") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
