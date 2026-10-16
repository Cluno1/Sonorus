package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.lyricsVariants
import io.github.cluno1.sonorus.features.catalog.domain.selectCatalogLyricsVariant
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.lyrics.CatalogLyricSourceImagesView
import io.github.cluno1.sonorus.shared.presentation.components.lyrics.CatalogLyricSourceToggleButton
import io.github.cluno1.sonorus.shared.presentation.components.lyrics.CatalogLyricsLanguageMenu
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import java.util.Locale

/**
 * issue 107: read-only lyrics of a catalog Work, opened from universal search.
 *
 * Deliberately not built on FullScreenLyricsView (which is bound to the player): when the Work has
 * no playable song the page contains no playback UI at all; otherwise it only offers a single
 * "play song" button that hands over to the normal catalog playback path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogWorkLyricsScreen(
    workId: String,
    initialTitle: String,
    playableSongs: List<CatalogLibrarySearchSong>,
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onPlaySong: (CatalogLibrarySearchSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bundle by remember(workId) { mutableStateOf<WorkBundle?>(viewModel.cachedWorkBundle(workId)) }
    var loading by remember(workId) { mutableStateOf(true) }
    var error by remember(workId) { mutableStateOf<String?>(null) }
    var retry by remember(workId) { mutableIntStateOf(0) }

    LaunchedEffect(workId, retry) {
        loading = true
        viewModel.loadWorkLyrics(workId).fold(
            onSuccess = {
                bundle = it
                error = null
            },
            onFailure = { error = viewModel.errorMessage(it) },
        )
        loading = false
    }

    val work = bundle?.work
    val title = work?.canonicalTitle ?: initialTitle
    val numbersLine = remember(work) { formatWorkNumbers(work?.numberMeta.orEmpty()) }
    val variants = remember(work) { work?.lyricsVariants().orEmpty() }
    val sourceImages = remember(work) { work?.lyricsSourceImages.orEmpty() }
    val configuration = LocalConfiguration.current
    val preferredLanguageTags = remember(configuration) {
        val locales = ConfigurationCompat.getLocales(configuration)
        buildList {
            for (index in 0 until locales.size()) {
                locales[index]?.toLanguageTag()?.takeIf(String::isNotBlank)?.let(::add)
            }
            if (isEmpty()) add(Locale.getDefault().toLanguageTag())
        }
    }
    var selectedLanguage by remember(variants) {
        mutableStateOf(selectCatalogLyricsVariant(variants, preferredLanguageTags)?.language)
    }
    val selectedVariant = variants.firstOrNull { it.language.equals(selectedLanguage, ignoreCase = true) }
        ?: variants.firstOrNull()
    var showSourceImages by remember(workId) { mutableStateOf(false) }
    val displayingImages = showSourceImages && sourceImages.isNotEmpty()
    val bottomPadding = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.work_lyrics_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(RhythmIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    CatalogLyricsLanguageMenu(
                        languages = variants.map { it.language },
                        selectedLanguage = selectedVariant?.language,
                        onLanguageSelect = { selectedLanguage = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    CatalogLyricSourceToggleButton(
                        visible = sourceImages.isNotEmpty(),
                        checked = showSourceImages,
                        onCheckedChange = { showSourceImages = it },
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (numbersLine.isNotEmpty()) {
                    Text(
                        text = numbersLine,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (error != null && bundle != null) {
                    Text(
                        text = stringResource(R.string.work_lyrics_offline_cached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Playback rule: no playable song -> no playback element is composed at all.
                playableSongs.firstOrNull()?.let { song ->
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onPlaySong(song) }) {
                        Icon(
                            RhythmIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.work_lyrics_play_song))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    bundle == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        M3CircularLoader()
                    }
                    bundle == null -> WorkLyricsError(
                        message = error ?: stringResource(R.string.work_lyrics_load_failed),
                        onRetry = { retry++ },
                        onBack = onBack,
                    )
                    displayingImages -> CatalogLyricSourceImagesView(
                        images = sourceImages,
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedVariant != null -> {
                        val text = remember(selectedVariant) { workLyricsDisplayText(selectedVariant.lyrics) }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium.copy(
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.5f,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = bottomPadding),
                        )
                    }
                    else -> Text(
                        text = stringResource(R.string.work_lyrics_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkLyricsError(
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
