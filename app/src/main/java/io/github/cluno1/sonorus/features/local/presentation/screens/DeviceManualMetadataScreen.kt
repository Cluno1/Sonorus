/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtworkCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtistArtworkCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceDetailsCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceDetailsField
import io.github.cluno1.sonorus.features.local.data.device.DeviceEditorialCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceEditorialSubject
import io.github.cluno1.sonorus.features.local.data.device.DeviceArtworkSaveTarget
import io.github.cluno1.sonorus.features.local.data.device.DeviceLyricsCandidate
import io.github.cluno1.sonorus.features.local.data.device.DeviceManualMetadataKind
import io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataRequest
import io.github.cluno1.sonorus.features.local.data.device.DevicePublicMetadataProvider
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualMetadataError
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualProviderStatus
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.DeviceManualMetadataUiState
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManualMetadataScreen(
    song: Song?,
    initialKind: DeviceManualMetadataKind,
    initialArtistName: String?,
    state: DeviceManualMetadataUiState,
    appSettings: AppSettings,
    onStart: (String, DeviceManualMetadataKind, String?) -> Unit,
    onSearch: (
        String,
        DeviceManualMetadataKind,
        DeviceMetadataRequest,
        Set<DevicePublicMetadataProvider>,
    ) -> Unit,
    onApplyLyrics: (String, DeviceLyricsCandidate) -> Unit,
    onApplyArtwork: (String, DeviceArtworkCandidate, DeviceArtworkSaveTarget, Uri?) -> Unit,
    onApplyArtistArtwork: (String, DeviceArtistArtworkCandidate) -> Unit,
    onApplyDetails: (String, DeviceDetailsCandidate, Set<DeviceDetailsField>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    draftOnly: Boolean = false,
) {
    val publicMetadataEnabled by appSettings.devicePublicMetadataEnabled.collectAsState()
    var kind by rememberSaveable(song?.id, initialKind) { mutableStateOf(initialKind) }
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title.orEmpty()) }
    var artist by rememberSaveable(song?.id, initialArtistName) {
        mutableStateOf(initialArtistName?.takeIf(String::isNotBlank) ?: song?.artist.orEmpty())
    }
    var album by rememberSaveable(song?.id) { mutableStateOf(song?.album.orEmpty()) }
    var duration by rememberSaveable(song?.id) {
        mutableStateOf(song?.duration?.takeIf { it > 0 }?.let(::formatDurationInput).orEmpty())
    }
    var lrclibSelected by rememberSaveable(song?.id) { mutableStateOf(true) }
    var musicBrainzSelected by rememberSaveable(song?.id) { mutableStateOf(true) }
    var deezerSelected by rememberSaveable(song?.id) { mutableStateOf(true) }
    var itunesSelected by rememberSaveable(song?.id) { mutableStateOf(true) }
    var wikipediaSelected by rememberSaveable(song?.id) { mutableStateOf(true) }
    var previewLyrics by remember { mutableStateOf<String?>(null) }
    var selectedLyrics by remember { mutableStateOf<DeviceLyricsCandidate?>(null) }
    var selectedArtwork by remember { mutableStateOf<DeviceArtworkCandidate?>(null) }
    var selectedArtistArtwork by remember { mutableStateOf<DeviceArtistArtworkCandidate?>(null) }
    var selectedDetails by remember { mutableStateOf<DeviceDetailsCandidate?>(null) }
    var selectedDetailFields by remember { mutableStateOf<Set<DeviceDetailsField>>(emptySet()) }
    var pendingFolderArtwork by remember { mutableStateOf<DeviceArtworkCandidate?>(null) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val candidate = pendingFolderArtwork
        pendingFolderArtwork = null
        if (uri != null && candidate != null && song != null) {
            onApplyArtwork(song.id, candidate, DeviceArtworkSaveTarget.MUSIC_FOLDER, uri)
        }
    }
    val miniPlayerBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val durationSeconds = parseDurationSeconds(duration)
    val durationValid = duration.isBlank() || durationSeconds != null

    LaunchedEffect(song?.id, kind, initialArtistName) {
        song?.let { onStart(it.id, kind, initialArtistName) }
    }
    LaunchedEffect(state.applied) {
        if (state.applied) onBack()
    }
    DisposableEffect(Unit) {
        onDispose(onClear)
    }

    CollapsibleHeaderScreen(
        title = stringResource(if (draftOnly) R.string.lan_tags_online else R.string.device_manual_metadata_title),
        showBackButton = true,
        onBackClick = onBack,
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier
                .then(modifier)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 24.dp + miniPlayerBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (song == null || state.error == DeviceManualMetadataError.SONG_UNAVAILABLE) {
                item {
                    MetadataCard {
                        Text(
                            text = stringResource(R.string.device_manual_metadata_song_unavailable),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                MetadataCard {
                    Text(
                        text = stringResource(R.string.device_manual_metadata_current_song),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == DeviceManualMetadataKind.LYRICS,
                        onClick = { kind = DeviceManualMetadataKind.LYRICS },
                        label = { Text(stringResource(R.string.device_manual_metadata_lyrics)) },
                        leadingIcon = {
                            Icon(
                                icon = RhythmIcons.Player.Lyrics,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = kind == DeviceManualMetadataKind.ARTWORK,
                        onClick = { kind = DeviceManualMetadataKind.ARTWORK },
                        label = { Text(stringResource(R.string.device_manual_metadata_artwork)) },
                        leadingIcon = {
                            Icon(
                                icon = RhythmIcons.Album,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    if (!draftOnly) FilterChip(
                        selected = kind == DeviceManualMetadataKind.ARTIST_ARTWORK,
                        onClick = { kind = DeviceManualMetadataKind.ARTIST_ARTWORK },
                        label = { Text(stringResource(R.string.device_manual_metadata_artist_artwork)) },
                        leadingIcon = {
                            Icon(
                                icon = RhythmIcons.Artist,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    FilterChip(
                        selected = kind == DeviceManualMetadataKind.DETAILS,
                        onClick = { kind = DeviceManualMetadataKind.DETAILS },
                        label = { Text(stringResource(R.string.device_manual_metadata_details)) },
                        leadingIcon = {
                            Icon(
                                icon = RhythmIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            if (!publicMetadataEnabled) {
                item {
                    MetadataCard {
                        Text(
                            text = stringResource(R.string.settings_device_public_metadata),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_device_public_metadata_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { appSettings.setDevicePublicMetadataEnabled(true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.device_manual_metadata_enable_public))
                        }
                    }
                }
            }

            item {
                MetadataCard {
                    Text(
                        text = stringResource(R.string.device_manual_metadata_sources),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    when (kind) {
                        DeviceManualMetadataKind.LYRICS -> ProviderChip(
                            selected = lrclibSelected,
                            onClick = { lrclibSelected = !lrclibSelected },
                            title = "LRCLIB",
                            description = stringResource(R.string.device_manual_metadata_lrclib_desc),
                            status = state.providerStatuses[DevicePublicMetadataProvider.LRCLIB],
                            resultCount = state.lyricsCandidates.size,
                        )
                        DeviceManualMetadataKind.ARTWORK -> {
                            ProviderChip(
                                selected = musicBrainzSelected,
                                onClick = { musicBrainzSelected = !musicBrainzSelected },
                                title = "MusicBrainz + Cover Art Archive",
                                description = stringResource(R.string.device_manual_metadata_caa_desc),
                                status = state.providerStatuses[
                                    DevicePublicMetadataProvider.MUSICBRAINZ_CAA
                                ],
                                resultCount = state.artworkCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.MUSICBRAINZ_CAA
                                },
                            )
                            ProviderChip(
                                selected = deezerSelected,
                                onClick = { deezerSelected = !deezerSelected },
                                title = "Deezer",
                                description = stringResource(R.string.device_manual_metadata_deezer_desc),
                                status = state.providerStatuses[DevicePublicMetadataProvider.DEEZER],
                                resultCount = state.artworkCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.DEEZER
                                },
                            )
                            ProviderChip(
                                selected = itunesSelected,
                                onClick = { itunesSelected = !itunesSelected },
                                title = "iTunes Search",
                                description = stringResource(
                                    R.string.device_manual_metadata_itunes_artwork_desc,
                                ),
                                status = state.providerStatuses[DevicePublicMetadataProvider.ITUNES],
                                resultCount = state.artworkCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.ITUNES
                                },
                            )
                        }
                        DeviceManualMetadataKind.ARTIST_ARTWORK -> ProviderChip(
                            selected = deezerSelected,
                            onClick = { deezerSelected = !deezerSelected },
                            title = "Deezer",
                            description = stringResource(
                                R.string.device_manual_metadata_deezer_artist_desc,
                            ),
                            status = state.providerStatuses[DevicePublicMetadataProvider.DEEZER],
                            resultCount = state.artistArtworkCandidates.size,
                        )
                        DeviceManualMetadataKind.DETAILS -> {
                            ProviderChip(
                                selected = musicBrainzSelected,
                                onClick = { musicBrainzSelected = !musicBrainzSelected },
                                title = "MusicBrainz",
                                description = stringResource(
                                    R.string.device_manual_metadata_musicbrainz_details_desc,
                                ),
                                status = state.providerStatuses[
                                    DevicePublicMetadataProvider.MUSICBRAINZ_CAA
                                ],
                                resultCount = state.detailsCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.MUSICBRAINZ_CAA
                                },
                            )
                            ProviderChip(
                                selected = deezerSelected,
                                onClick = { deezerSelected = !deezerSelected },
                                title = "Deezer",
                                description = stringResource(
                                    R.string.device_manual_metadata_deezer_details_desc,
                                ),
                                status = state.providerStatuses[DevicePublicMetadataProvider.DEEZER],
                                resultCount = state.detailsCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.DEEZER
                                },
                            )
                            ProviderChip(
                                selected = itunesSelected,
                                onClick = { itunesSelected = !itunesSelected },
                                title = "iTunes Search",
                                description = stringResource(
                                    R.string.device_manual_metadata_itunes_details_desc,
                                ),
                                status = state.providerStatuses[DevicePublicMetadataProvider.ITUNES],
                                resultCount = state.detailsCandidates.count {
                                    it.provider == DevicePublicMetadataProvider.ITUNES
                                },
                            )
                            if (!draftOnly) ProviderChip(
                                selected = wikipediaSelected,
                                onClick = { wikipediaSelected = !wikipediaSelected },
                                title = "Wikipedia",
                                description = stringResource(
                                    R.string.device_manual_metadata_wikipedia_desc,
                                ),
                                status = state.providerStatuses[
                                    DevicePublicMetadataProvider.WIKIPEDIA
                                ],
                                resultCount = state.editorialCandidates.size,
                            )
                        }
                    }
                }
            }

            item {
                MetadataCard {
                    Text(
                        text = stringResource(R.string.device_manual_metadata_query),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (kind != DeviceManualMetadataKind.ARTIST_ARTWORK) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.device_manual_metadata_field_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = {
                            Text(
                                stringResource(
                                    if (kind == DeviceManualMetadataKind.ARTIST_ARTWORK) {
                                        R.string.device_manual_metadata_field_artist_required
                                    } else {
                                        R.string.device_manual_metadata_field_artist
                                    },
                                ),
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (kind != DeviceManualMetadataKind.ARTIST_ARTWORK) {
                        OutlinedTextField(
                            value = album,
                            onValueChange = { album = it },
                            label = { Text(stringResource(R.string.device_manual_metadata_field_album)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text(stringResource(R.string.device_manual_metadata_field_duration)) },
                            supportingText = if (!durationValid) {
                                { Text(stringResource(R.string.device_manual_metadata_duration_error)) }
                            } else {
                                null
                            },
                            isError = !durationValid,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            title = song.title
                            artist = initialArtistName?.takeIf(String::isNotBlank) ?: song.artist
                            album = song.album
                            duration = song.duration.takeIf { it > 0 }?.let(::formatDurationInput).orEmpty()
                        },
                        enabled = !state.isSearching && !state.isApplying,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (kind == DeviceManualMetadataKind.ARTIST_ARTWORK) {
                                    R.string.device_manual_metadata_restore_artist
                                } else {
                                    R.string.device_manual_metadata_restore_defaults
                                },
                            ),
                        )
                    }
                }
            }

            item {
                val providers = buildSet {
                    if (kind == DeviceManualMetadataKind.LYRICS && lrclibSelected) {
                        add(DevicePublicMetadataProvider.LRCLIB)
                    }
                    if (kind == DeviceManualMetadataKind.ARTWORK && musicBrainzSelected) {
                        add(DevicePublicMetadataProvider.MUSICBRAINZ_CAA)
                    }
                    if (kind == DeviceManualMetadataKind.ARTWORK && deezerSelected) {
                        add(DevicePublicMetadataProvider.DEEZER)
                    }
                    if (kind == DeviceManualMetadataKind.ARTWORK && itunesSelected) {
                        add(DevicePublicMetadataProvider.ITUNES)
                    }
                    if (kind == DeviceManualMetadataKind.ARTIST_ARTWORK && deezerSelected) {
                        add(DevicePublicMetadataProvider.DEEZER)
                    }
                    if (kind == DeviceManualMetadataKind.DETAILS && musicBrainzSelected) {
                        add(DevicePublicMetadataProvider.MUSICBRAINZ_CAA)
                    }
                    if (kind == DeviceManualMetadataKind.DETAILS && deezerSelected) {
                        add(DevicePublicMetadataProvider.DEEZER)
                    }
                    if (kind == DeviceManualMetadataKind.DETAILS && itunesSelected) {
                        add(DevicePublicMetadataProvider.ITUNES)
                    }
                    if (kind == DeviceManualMetadataKind.DETAILS && wikipediaSelected && !draftOnly) {
                        add(DevicePublicMetadataProvider.WIKIPEDIA)
                    }
                }
                Button(
                    onClick = {
                        onSearch(
                            song.id,
                            kind,
                            DeviceMetadataRequest(
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = durationSeconds,
                            ),
                            providers,
                        )
                    },
                    enabled = publicMetadataEnabled &&
                        (kind == DeviceManualMetadataKind.ARTIST_ARTWORK || durationValid) &&
                        !state.isSearching && !state.isApplying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            icon = RhythmIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.device_manual_metadata_search))
                    }
                }
            }

            state.error?.takeUnless { it == DeviceManualMetadataError.SONG_UNAVAILABLE }?.let { error ->
                item {
                    Text(
                        text = stringResource(error.stringResource()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.hasSearched && !state.isSearching &&
                state.lyricsCandidates.isEmpty() && state.artworkCandidates.isEmpty()
                    && state.artistArtworkCandidates.isEmpty() && state.detailsCandidates.isEmpty()
                    && state.editorialCandidates.isEmpty()
            ) {
                item {
                    MetadataCard {
                        Text(
                            text = stringResource(R.string.device_manual_metadata_no_results),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.device_manual_metadata_no_results_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (
                state.lyricsCandidates.isNotEmpty() ||
                state.artworkCandidates.isNotEmpty() ||
                state.artistArtworkCandidates.isNotEmpty() ||
                state.detailsCandidates.isNotEmpty() ||
                state.editorialCandidates.isNotEmpty()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.device_manual_metadata_results),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            items(state.lyricsCandidates, key = { "lyrics:${it.externalId}" }) { candidate ->
                LyricsCandidateCard(
                    candidate = candidate,
                    queryDurationSeconds = durationSeconds,
                    onClick = {
                        selectedLyrics = candidate
                        previewLyrics = candidate.lyrics.syncedLyrics ?: candidate.lyrics.plainLyrics
                    },
                )
            }
            items(
                state.artworkCandidates,
                key = { "artwork:${it.provider}:${it.externalId}" },
            ) { candidate ->
                ArtworkCandidateCard(
                    candidate = candidate,
                    queryDurationSeconds = durationSeconds,
                    onClick = { selectedArtwork = candidate },
                )
            }
            items(
                state.artistArtworkCandidates,
                key = { "artist-artwork:${it.provider}:${it.externalId}" },
            ) { candidate ->
                ArtistArtworkCandidateCard(
                    candidate = candidate,
                    onClick = { selectedArtistArtwork = candidate },
                )
            }
            items(
                state.detailsCandidates,
                key = { "details:${it.provider}:${it.externalId}" },
            ) { candidate ->
                DetailsCandidateCard(
                    candidate = candidate,
                    queryDurationSeconds = durationSeconds,
                    onClick = {
                        selectedDetails = candidate
                        selectedDetailFields = defaultDetailsFields(song, candidate)
                    },
                )
            }
            items(
                state.editorialCandidates,
                key = { "editorial:${it.externalId}" },
            ) { candidate ->
                EditorialCandidateCard(candidate)
            }
        }
    }

    selectedDetails?.let { candidate ->
        AlertDialog(
            onDismissRequest = {
                selectedDetails = null
                selectedDetailFields = emptySet()
            },
            title = { Text(candidate.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.artist, style = MaterialTheme.typography.titleSmall)
                    Text(candidate.album, style = MaterialTheme.typography.bodyMedium)
                    DetailsLines(candidate)
                    Text(
                        text = stringResource(R.string.device_manual_metadata_select_detail_fields),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDetailsFields(candidate).forEach { field ->
                            FilterChip(
                                selected = field in selectedDetailFields,
                                onClick = {
                                    selectedDetailFields = if (field in selectedDetailFields) {
                                        selectedDetailFields - field
                                    } else {
                                        selectedDetailFields + field
                                    }
                                },
                                label = { Text(stringResource(field.labelResource())) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(if (draftOnly) R.string.lan_tags_candidate_desc else R.string.device_manual_metadata_details_app_only_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onApplyDetails(song!!.id, candidate, selectedDetailFields)
                        selectedDetails = null
                        selectedDetailFields = emptySet()
                    },
                    enabled = !state.isApplying && selectedDetailFields.isNotEmpty(),
                ) {
                    Text(stringResource(if (draftOnly) R.string.lan_tags_use_candidate else R.string.device_manual_metadata_use_details_app_only))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedDetails = null
                    selectedDetailFields = emptySet()
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    selectedLyrics?.let { candidate ->
        AlertDialog(
            onDismissRequest = {
                selectedLyrics = null
                previewLyrics = null
            },
            title = { Text(candidate.title) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = previewLyrics.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedLyrics = null
                        previewLyrics = null
                        song?.let { onApplyLyrics(it.id, candidate) }
                    },
                    enabled = !state.isApplying,
                ) {
                    Text(stringResource(if (draftOnly) R.string.lan_tags_use_candidate else R.string.device_manual_metadata_use_lyrics))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedLyrics = null
                        previewLyrics = null
                    },
                ) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    selectedArtwork?.let { candidate ->
        AlertDialog(
            onDismissRequest = { selectedArtwork = null },
            title = { Text(candidate.album.ifBlank { candidate.title }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(candidate.imageUrl).build(),
                        contentDescription = stringResource(R.string.device_manual_metadata_artwork_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                    )
                    song?.let { targetSong ->
                        Text(
                            text = stringResource(
                                R.string.device_manual_metadata_artwork_target,
                                targetSong.title,
                                targetSong.album,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(if (draftOnly) R.string.lan_tags_candidate_desc else R.string.device_manual_metadata_artwork_save_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            selectedArtwork = null
                            song?.let {
                                onApplyArtwork(
                                    it.id,
                                    candidate,
                                    DeviceArtworkSaveTarget.APP_ONLY,
                                    null,
                                )
                            }
                        },
                        enabled = !state.isApplying,
                    ) {
                        Text(stringResource(if (draftOnly) R.string.lan_tags_use_candidate else R.string.device_manual_metadata_artwork_app_only))
                    }
                    if (!draftOnly) TextButton(
                        onClick = {
                            selectedArtwork = null
                            pendingFolderArtwork = candidate
                            folderLauncher.launch(null)
                        },
                        enabled = !state.isApplying,
                    ) {
                        Text(stringResource(R.string.device_manual_metadata_artwork_music_folder))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedArtwork = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    selectedArtistArtwork?.let { candidate ->
        AlertDialog(
            onDismissRequest = { selectedArtistArtwork = null },
            title = { Text(candidate.artistName) },
            text = {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(candidate.imageUrl).build(),
                    contentDescription = stringResource(
                        R.string.device_manual_metadata_artist_artwork_preview,
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedArtistArtwork = null
                        song?.let { onApplyArtistArtwork(it.id, candidate) }
                    },
                    enabled = !state.isApplying,
                ) {
                    Text(stringResource(R.string.device_manual_metadata_use_artist_artwork))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedArtistArtwork = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun MetadataCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ProviderChip(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String,
    status: DeviceManualProviderStatus?,
    resultCount: Int,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                status?.let {
                    Text(
                        text = when (it) {
                            DeviceManualProviderStatus.LOADING -> stringResource(
                                R.string.device_manual_metadata_provider_loading,
                            )
                            DeviceManualProviderStatus.SUCCESS -> stringResource(
                                R.string.device_manual_metadata_provider_success,
                                resultCount,
                            )
                            DeviceManualProviderStatus.EMPTY -> stringResource(
                                R.string.device_manual_metadata_provider_empty,
                            )
                            DeviceManualProviderStatus.FAILED -> stringResource(
                                R.string.device_manual_metadata_provider_failed,
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (it == DeviceManualProviderStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LyricsCandidateCard(
    candidate: DeviceLyricsCandidate,
    queryDurationSeconds: Int?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LRCLIB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(
                        R.string.device_manual_metadata_confidence,
                        (candidate.confidence * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(candidate.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(candidate.artist, candidate.album).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CandidateDuration(
                candidateSeconds = candidate.durationSeconds,
                queryDurationSeconds = queryDurationSeconds,
            )
            Text(
                if (candidate.lyrics.syncedLyrics.isNullOrBlank()) {
                    stringResource(R.string.device_manual_metadata_plain_lyrics)
                } else {
                    stringResource(R.string.device_manual_metadata_synced_lyrics)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            val preview = (candidate.lyrics.syncedLyrics ?: candidate.lyrics.plainLyrics).orEmpty()
                .lineSequence()
                .filter(String::isNotBlank)
                .take(3)
                .joinToString("\n")
            if (preview.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtworkCandidateCard(
    candidate: DeviceArtworkCandidate,
    queryDurationSeconds: Int?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(candidate.imageUrl).build(),
                contentDescription = stringResource(R.string.device_manual_metadata_artwork_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = candidate.provider.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = candidate.album.ifBlank { candidate.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CandidateDuration(
                    candidateSeconds = candidate.durationSeconds,
                    queryDurationSeconds = queryDurationSeconds,
                )
                Text(
                    stringResource(
                        R.string.device_manual_metadata_confidence,
                        (candidate.confidence * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ArtistArtworkCandidateCard(
    candidate: DeviceArtistArtworkCandidate,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(candidate.imageUrl).build(),
                contentDescription = stringResource(
                    R.string.device_manual_metadata_artist_artwork_preview,
                ),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(88.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Deezer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = candidate.artistName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.device_manual_metadata_artist_stats,
                        candidate.albumCount,
                        candidate.fanCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.device_manual_metadata_confidence,
                        (candidate.confidence * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun DetailsCandidateCard(
    candidate: DeviceDetailsCandidate,
    queryDurationSeconds: Int?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            candidate.artworkUrl?.let { artworkUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artworkUrl).build(),
                    contentDescription = stringResource(R.string.device_manual_metadata_artwork_preview),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        candidate.provider.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            R.string.device_manual_metadata_confidence,
                            (candidate.confidence * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(candidate.artist, candidate.album)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CandidateDuration(candidate.durationSeconds, queryDurationSeconds)
                DetailsLines(candidate)
            }
        }
    }
}

@Composable
private fun DetailsLines(candidate: DeviceDetailsCandidate) {
    val lines = buildList {
        candidate.releaseDate?.takeIf(String::isNotBlank)?.let {
            add(stringResource(R.string.device_manual_metadata_details_release_date, it))
        }
        candidate.trackNumber?.takeIf { it > 0 }?.let { track ->
            val count = candidate.trackCount?.takeIf { it > 0 }
            add(
                if (count != null) {
                    stringResource(
                        R.string.device_manual_metadata_details_track_number,
                        track,
                        count,
                    )
                } else {
                    stringResource(R.string.device_manual_metadata_details_track_number_only, track)
                }
            )
        }
        candidate.discNumber?.takeIf { it > 0 }?.let {
            add(stringResource(R.string.device_manual_metadata_details_disc_number, it))
        }
        candidate.albumType?.takeIf(String::isNotBlank)?.let {
            add(stringResource(R.string.device_manual_metadata_details_album_type, it))
        }
        candidate.genre?.takeIf(String::isNotBlank)?.let {
            add(stringResource(R.string.device_manual_metadata_details_genre, it))
        }
        candidate.country?.takeIf(String::isNotBlank)?.let {
            add(stringResource(R.string.device_manual_metadata_details_country, it))
        }
        candidate.label?.takeIf(String::isNotBlank)?.let {
            add(stringResource(R.string.device_manual_metadata_details_label, it))
        }
        if (candidate.artistAlbumCount != null || candidate.artistFanCount != null) {
            add(
                stringResource(
                    R.string.device_manual_metadata_artist_stats,
                    candidate.artistAlbumCount ?: 0,
                    candidate.artistFanCount ?: 0,
                )
            )
        }
    }
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun availableDetailsFields(candidate: DeviceDetailsCandidate): List<DeviceDetailsField> = buildList {
    if (candidate.title.isNotBlank()) add(DeviceDetailsField.TITLE)
    if (candidate.artist.isNotBlank()) add(DeviceDetailsField.ARTIST)
    if (candidate.album.isNotBlank()) add(DeviceDetailsField.ALBUM)
    if (!candidate.albumArtist.isNullOrBlank()) add(DeviceDetailsField.ALBUM_ARTIST)
    if (candidate.year != null) add(DeviceDetailsField.YEAR)
    if ((candidate.trackNumber ?: 0) > 0) add(DeviceDetailsField.TRACK_NUMBER)
    if ((candidate.discNumber ?: 0) > 0) add(DeviceDetailsField.DISC_NUMBER)
    if (!candidate.genre.isNullOrBlank()) add(DeviceDetailsField.GENRE)
}

private fun defaultDetailsFields(
    song: Song,
    candidate: DeviceDetailsCandidate,
): Set<DeviceDetailsField> {
    val available = availableDetailsFields(candidate).toSet()
    if (candidate.confidence >= 0.98) return available
    return buildSet {
        if (DeviceDetailsField.TITLE in available && song.title.isUnknownMetadata()) {
            add(DeviceDetailsField.TITLE)
        }
        if (DeviceDetailsField.ARTIST in available && song.artist.isUnknownMetadata()) {
            add(DeviceDetailsField.ARTIST)
        }
        if (DeviceDetailsField.ALBUM in available && song.album.isUnknownMetadata()) {
            add(DeviceDetailsField.ALBUM)
        }
        if (DeviceDetailsField.ALBUM_ARTIST in available && song.albumArtist.isNullOrBlank()) {
            add(DeviceDetailsField.ALBUM_ARTIST)
        }
        if (DeviceDetailsField.YEAR in available && song.year <= 0) add(DeviceDetailsField.YEAR)
        if (DeviceDetailsField.TRACK_NUMBER in available && song.trackNumber <= 0) {
            add(DeviceDetailsField.TRACK_NUMBER)
        }
        if (
            DeviceDetailsField.DISC_NUMBER in available &&
            (candidate.discNumber ?: 1) > 1 && song.discNumber <= 1
        ) {
            add(DeviceDetailsField.DISC_NUMBER)
        }
        if (DeviceDetailsField.GENRE in available && song.genre.isNullOrBlank()) {
            add(DeviceDetailsField.GENRE)
        }
    }
}

private fun String.isUnknownMetadata(): Boolean =
    isBlank() || equals("unknown", ignoreCase = true) || startsWith("unknown ", ignoreCase = true) ||
        (startsWith("<") && endsWith(">"))

private fun DeviceDetailsField.labelResource(): Int = when (this) {
    DeviceDetailsField.TITLE -> R.string.device_manual_metadata_detail_title
    DeviceDetailsField.ARTIST -> R.string.device_manual_metadata_detail_artist
    DeviceDetailsField.ALBUM -> R.string.device_manual_metadata_detail_album
    DeviceDetailsField.ALBUM_ARTIST -> R.string.device_manual_metadata_detail_album_artist
    DeviceDetailsField.YEAR -> R.string.device_manual_metadata_detail_year
    DeviceDetailsField.TRACK_NUMBER -> R.string.device_manual_metadata_detail_track
    DeviceDetailsField.DISC_NUMBER -> R.string.device_manual_metadata_detail_disc
    DeviceDetailsField.GENRE -> R.string.device_manual_metadata_detail_genre
}

@Composable
private fun EditorialCandidateCard(candidate: DeviceEditorialCandidate) {
    MetadataCard {
        Text(
            text = stringResource(
                if (candidate.subject == DeviceEditorialSubject.ALBUM) {
                    R.string.device_manual_metadata_album_description
                } else {
                    R.string.device_manual_metadata_artist_description
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = candidate.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = candidate.description, style = MaterialTheme.typography.bodyMedium)
        Text("Wikipedia", style = MaterialTheme.typography.labelSmall)
    }
}

private fun DevicePublicMetadataProvider.displayName(): String = when (this) {
    DevicePublicMetadataProvider.LRCLIB -> "LRCLIB"
    DevicePublicMetadataProvider.MUSICBRAINZ_CAA -> "MusicBrainz + CAA"
    DevicePublicMetadataProvider.DEEZER -> "Deezer"
    DevicePublicMetadataProvider.ITUNES -> "iTunes Search"
    DevicePublicMetadataProvider.WIKIPEDIA -> "Wikipedia"
}

@Composable
private fun CandidateDuration(candidateSeconds: Double?, queryDurationSeconds: Int?) {
    val rounded = candidateSeconds?.takeIf { it > 0 }?.roundToInt() ?: return
    val durationText = formatDurationInput(rounded * 1000L)
    val difference = queryDurationSeconds?.let { abs(rounded - it) }
    Text(
        text = if (difference == null) {
            stringResource(R.string.device_manual_metadata_candidate_duration, durationText)
        } else {
            stringResource(
                R.string.device_manual_metadata_candidate_duration_difference,
                durationText,
                difference,
            )
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun parseDurationSeconds(raw: String): Int? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val seconds = if (':' in value) {
        val parts = value.split(':')
        if (parts.size !in 2..3 || parts.any { it.toIntOrNull() == null }) return null
        val numbers = parts.map(String::toInt)
        if (numbers.drop(1).any { it !in 0..59 }) return null
        if (numbers.size == 2) numbers[0] * 60L + numbers[1]
        else numbers[0] * 3600L + numbers[1] * 60L + numbers[2]
    } else {
        value.toLongOrNull() ?: return null
    }
    return seconds.takeIf { it in 1..86_400 }?.toInt()
}

private fun formatDurationInput(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = totalSeconds % 3600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun DeviceManualMetadataError.stringResource(): Int = when (this) {
    DeviceManualMetadataError.SONG_UNAVAILABLE -> R.string.device_manual_metadata_song_unavailable
    DeviceManualMetadataError.TITLE_REQUIRED -> R.string.device_manual_metadata_title_required
    DeviceManualMetadataError.ARTIST_REQUIRED -> R.string.device_manual_metadata_artist_required
    DeviceManualMetadataError.PROVIDER_REQUIRED -> R.string.device_manual_metadata_provider_required
    DeviceManualMetadataError.REQUEST_FAILED -> R.string.device_manual_metadata_request_failed
    DeviceManualMetadataError.APPLY_FAILED -> R.string.device_manual_metadata_apply_failed
}
