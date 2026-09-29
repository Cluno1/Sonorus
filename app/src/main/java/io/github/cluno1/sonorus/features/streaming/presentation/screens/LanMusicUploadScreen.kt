/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.presentation.screens

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.streaming.domain.model.LanMusicUploadError
import io.github.cluno1.sonorus.features.streaming.presentation.viewmodel.LanMusicUploadViewModel
import io.github.cluno1.sonorus.features.streaming.presentation.viewmodel.LocalMusicUploadStatus
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import io.github.cluno1.sonorus.util.findActivity
import java.util.Locale

private class LocalAudioDocuments : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable
fun LanMusicUploadScreen(
    viewModel: LanMusicUploadViewModel,
    onLibraryChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val latestState by rememberUpdatedState(state)
    val refreshLibrary by rememberUpdatedState(onLibraryChanged)
    val context = LocalContext.current
    val activity = context.findActivity()
    val picker = rememberLauncherForActivityResult(remember { LocalAudioDocuments() }, viewModel::addFiles)
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.libraryVersion) { if (state.libraryVersion > 0) onLibraryChanged() }
    DisposableEffect(viewModel, activity) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                if (latestState.running) refreshLibrary()
                viewModel.cancel()
            }
        }
    }
    val leave = {
        // Refresh after cancelling as well: a response may be lost after the gateway saved a file.
        if (state.running || state.libraryVersion > 0) onLibraryChanged()
        viewModel.cancel()
        onBack()
    }
    BackHandler(onBack = leave)
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    CollapsibleHeaderScreen(
        title = stringResource(R.string.lan_upload_title), showBackButton = true, onBackClick = leave,
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.lan_upload_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.gatewayUrl.isNotBlank()) {
                    Text(stringResource(R.string.lan_upload_gateway, state.gatewayUrl), style = MaterialTheme.typography.bodySmall)
                }
                state.capabilities?.let { capabilities ->
                    Text(stringResource(R.string.lan_upload_formats, capabilities.extensions.joinToString(" / ") { it.uppercase(Locale.ROOT) },
                        (capabilities.maxBytes / (1024 * 1024)).toInt()), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.loading || state.selecting) item { CircularProgressIndicator() }
            state.error?.let { error ->
                item {
                    Text(stringResource(uploadErrorLabel(error)), color = MaterialTheme.colorScheme.error)
                    if (state.capabilities == null && !state.loading) TextButton(onClick = viewModel::load) {
                        Text(stringResource(R.string.lan_tags_retry))
                    }
                }
            }
            if (state.capabilities != null) {
                item {
                    OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "application/octet-stream")) },
                        enabled = !state.running && !state.selecting, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lan_upload_select))
                    }
                    Text(stringResource(R.string.lan_upload_selected, state.files.size), style = MaterialTheme.typography.bodySmall)
                }
                if (state.files.isEmpty()) item {
                    Text(stringResource(R.string.lan_upload_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.files, key = { it.file.uri.toString() }) { item ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.file.name, style = MaterialTheme.typography.titleSmall)
                            item.file.size?.let { size ->
                                Text(Formatter.formatShortFileSize(context, size), style = MaterialTheme.typography.bodySmall)
                            }
                            val statusLabel = when (item.status) {
                                LocalMusicUploadStatus.READY -> R.string.lan_upload_ready
                                LocalMusicUploadStatus.PREPARING -> R.string.lan_upload_preparing
                                LocalMusicUploadStatus.UPLOADING -> if (item.progress >= 1f) R.string.lan_upload_importing else R.string.lan_upload_uploading
                                LocalMusicUploadStatus.UPLOADED -> R.string.lan_upload_success
                                LocalMusicUploadStatus.DUPLICATE -> R.string.lan_upload_duplicate
                                LocalMusicUploadStatus.FAILED -> R.string.lan_upload_failed
                                LocalMusicUploadStatus.CANCELLED -> R.string.lan_upload_cancelled
                            }
                            Text(stringResource(statusLabel), style = MaterialTheme.typography.bodyMedium)
                            when (item.status) {
                                LocalMusicUploadStatus.PREPARING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                LocalMusicUploadStatus.UPLOADING -> {
                                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                                    Text(stringResource(R.string.lan_upload_progress, (item.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                                }
                                else -> Unit
                            }
                            item.error?.let { error -> Text(stringResource(uploadErrorLabel(error)), color = MaterialTheme.colorScheme.error) }
                            if (!state.running) TextButton(onClick = { viewModel.remove(item.file.uri) }) {
                                Text(stringResource(R.string.lan_upload_remove))
                            }
                        }
                    }
                }
                item {
                    if (state.running) {
                        OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.lan_upload_cancel))
                        }
                    } else {
                        val pending = state.files.any { it.status in setOf(LocalMusicUploadStatus.READY, LocalMusicUploadStatus.FAILED, LocalMusicUploadStatus.CANCELLED) }
                        Button(onClick = viewModel::start, enabled = pending && !state.selecting, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.files.any { it.status == LocalMusicUploadStatus.FAILED || it.status == LocalMusicUploadStatus.CANCELLED })
                                R.string.lan_upload_retry else R.string.lan_upload_start))
                        }
                    }
                }
            }
        }
    }
}

private fun uploadErrorLabel(error: LanMusicUploadError): Int = when (error) {
    LanMusicUploadError.UNAVAILABLE -> R.string.lan_upload_unavailable
    LanMusicUploadError.READ_FILE -> R.string.lan_upload_read_failed
    LanMusicUploadError.UNSUPPORTED_FORMAT -> R.string.lan_upload_unsupported
    LanMusicUploadError.INVALID_AUDIO -> R.string.lan_upload_invalid
    LanMusicUploadError.INVALID_REQUEST -> R.string.lan_upload_invalid_request
    LanMusicUploadError.TOO_LARGE -> R.string.lan_upload_too_large
    LanMusicUploadError.CONNECTION_CHANGED -> R.string.lan_upload_connection_changed
    LanMusicUploadError.LIBRARY_FULL -> R.string.lan_upload_library_full
    LanMusicUploadError.BUSY -> R.string.lan_upload_busy
    LanMusicUploadError.STORAGE -> R.string.lan_upload_storage_failed
    LanMusicUploadError.REQUEST_FAILED -> R.string.lan_upload_request_failed
}
