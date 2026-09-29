/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.local.data.device.DeviceManualMetadataKind
import io.github.cluno1.sonorus.features.local.presentation.screens.DeviceManualMetadataScreen
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import io.github.cluno1.sonorus.features.streaming.presentation.viewmodel.LanSongTagsError
import io.github.cluno1.sonorus.features.streaming.presentation.viewmodel.LanSongTagsViewModel
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding

@Composable
fun LanSongTagsScreen(
    song: Song?,
    viewModel: LanSongTagsViewModel,
    appSettings: AppSettings,
    onSaved: (LanSongMetadata) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    var onlineSearch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(song?.id) { song?.let(viewModel::load) }
    LaunchedEffect(state.saved) { state.saved?.let(onSaved) }
    BackHandler(enabled = state.saving) { }
    BackHandler(enabled = onlineSearch) { onlineSearch = false }
    if (onlineSearch) {
        DeviceManualMetadataScreen(
            song = viewModel.querySong(), initialKind = DeviceManualMetadataKind.DETAILS,
            initialArtistName = null, state = searchState, appSettings = appSettings,
            onStart = { _, kind, _ -> viewModel.startSearch(kind) },
            onSearch = { _, kind, request, providers -> viewModel.search(kind, request, providers) },
            onApplyLyrics = { _, candidate -> viewModel.chooseLyrics(candidate) },
            onApplyArtwork = { _, candidate, _, _ -> viewModel.chooseArtwork(candidate) },
            onApplyArtistArtwork = { _, _ -> },
            onApplyDetails = { _, candidate, fields -> viewModel.chooseDetails(candidate, fields) },
            onClear = viewModel::clearSearch, onBack = { onlineSearch = false }, draftOnly = true,
        )
        return
    }
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    CollapsibleHeaderScreen(
        title = stringResource(R.string.lan_tags_edit), showBackButton = true,
        onBackClick = { if (!state.saving) onBack() },
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(state.song?.title ?: song?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.lan_tags_shared_desc), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.loading && song != null) {
                item { CircularProgressIndicator() }
            }
            if (song == null || state.error == LanSongTagsError.LOAD_FAILED) {
                item {
                    Text(stringResource(R.string.lan_tags_load_failed), color = MaterialTheme.colorScheme.error)
                    if (song != null) TextButton(onClick = { viewModel.load(song) }) {
                        Text(stringResource(R.string.lan_tags_retry))
                    }
                }
            }
            if (state.loading || state.song == null || state.error == LanSongTagsError.LOAD_FAILED) return@LazyColumn
            item {
                OutlinedButton(onClick = { onlineSearch = true }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lan_tags_online))
                }
            }
            items(LanSongMetadata.editableFields, key = { it }) { name ->
                OutlinedTextField(
                    value = state.fields[name].orEmpty(), onValueChange = { viewModel.setField(name, it) },
                    label = { Text(stringResource(tagLabel(name))) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = if (name in LanSongMetadata.numberFields) KeyboardType.Number else KeyboardType.Text),
                )
            }
            item {
                Text(stringResource(R.string.device_manual_metadata_artwork), style = MaterialTheme.typography.titleMedium)
                val preview = if (state.removeArtwork) null else state.selectedArtwork?.imageUrl ?: state.artworkUrl
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = stringResource(R.string.device_manual_metadata_artwork_preview),
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(220.dp))
                    TextButton(onClick = viewModel::removeArtwork, enabled = !state.saving) {
                        Text(stringResource(R.string.lan_tags_remove_cover))
                    }
                }
            }
            item {
                OutlinedTextField(value = state.plainLyrics, onValueChange = { viewModel.setLyrics(plain = it) },
                    label = { Text(stringResource(R.string.device_manual_metadata_plain_lyrics)) },
                    enabled = !state.saving, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = state.syncedLyrics, onValueChange = { viewModel.setLyrics(synced = it) },
                    label = { Text(stringResource(R.string.lan_tags_lrc)) },
                    enabled = !state.saving, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                item {
                    Text(stringResource(if (error == LanSongTagsError.INVALID_FIELDS) R.string.lan_tags_invalid_fields else R.string.lan_tags_save_failed),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    if (state.saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.lan_tags_save))
                }
            }
        }
    }
}

private fun tagLabel(name: String): Int = when (name) {
    "title" -> R.string.device_manual_metadata_detail_title
    "artist" -> R.string.device_manual_metadata_detail_artist
    "album" -> R.string.device_manual_metadata_detail_album
    "albumArtist" -> R.string.device_manual_metadata_detail_album_artist
    "genre" -> R.string.device_manual_metadata_detail_genre
    "year" -> R.string.device_manual_metadata_detail_year
    "trackNumber" -> R.string.device_manual_metadata_detail_track
    "discNumber" -> R.string.device_manual_metadata_detail_disc
    "composer" -> R.string.lan_tags_composer
    "language" -> R.string.lan_tags_language
    else -> R.string.lan_tags_version
}
