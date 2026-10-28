package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyrics
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding

/** Display name of a platform, for search rows and the attribution line. */
@StringRes
fun CatalogExternalLyricsSource.labelRes(): Int = when (this) {
    CatalogExternalLyricsSource.NETEASE -> R.string.external_lyrics_source_netease
    CatalogExternalLyricsSource.TENCENT -> R.string.external_lyrics_source_tencent
    CatalogExternalLyricsSource.KUGOU -> R.string.external_lyrics_source_kugou
}

/**
 * issue 127: read-only lyrics of a song found on a public platform (universal search, "online
 * lyrics" group). Nothing here is saved to a Work and there is no playback: the backend never
 * hands out third-party audio. The source platform is always named at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogExternalLyricsScreen(
    source: CatalogExternalLyricsSource,
    externalId: String,
    title: String,
    artist: String,
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyrics by remember(source, externalId) { mutableStateOf<CatalogExternalLyrics?>(null) }
    var loading by remember(source, externalId) { mutableStateOf(true) }
    var failure by remember(source, externalId) { mutableStateOf<Throwable?>(null) }
    var retryKey by remember(source, externalId) { mutableIntStateOf(0) }
    var showTranslation by remember(source, externalId) { mutableStateOf(false) }
    LaunchedEffect(source, externalId, retryKey) {
        loading = true
        viewModel.getExternalLyrics(source, externalId, title).fold(
            onSuccess = {
                lyrics = it
                failure = null
            },
            onFailure = { failure = it },
        )
        loading = false
    }
    val bottomPadding = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artist.isNotBlank()) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(RhythmIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val loaded = lyrics
            when {
                loaded == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3CircularLoader()
                }
                loaded == null -> ExternalLyricsError(
                    message = stringResource(
                        if ((failure as? CatalogFailure.Server)?.statusCode == 404) R.string.external_lyrics_not_found
                        else R.string.work_lyrics_load_failed,
                    ),
                    onRetry = { retryKey++ },
                    onBack = onBack,
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (loaded.translation != null) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !showTranslation,
                                onClick = { showTranslation = false },
                                label = { Text(stringResource(R.string.external_lyrics_original)) },
                            )
                            FilterChip(
                                selected = showTranslation,
                                onClick = { showTranslation = true },
                                label = { Text(stringResource(R.string.external_lyrics_translation)) },
                            )
                        }
                    }
                    Text(
                        text = if (showTranslation) loaded.translation.orEmpty() else loaded.lyrics,
                        style = MaterialTheme.typography.titleMedium.copy(
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.5f,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp)),
                    )
                    Text(
                        text = stringResource(R.string.external_lyrics_attribution, stringResource(source.labelRes())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = bottomPadding)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalLyricsError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    RhythmIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.work_lyrics_retry))
                    }
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cd_back))
                    }
                }
            }
        }
    }
}
