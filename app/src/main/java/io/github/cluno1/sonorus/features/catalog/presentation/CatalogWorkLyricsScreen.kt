package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsVariant
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
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
 *
 * issue 117: on a tablet the page is the same split as the player lyrics — the left pane is empty
 * (a Work read from search never borrows the background player) and the source images cover only
 * that pane, so the lyrics on the right stay readable. Phones keep the single column.
 * issue 116: the Work's tags and the "Manage tags" entry sit under the title.
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
    // issue 112: opens the Work's default score; null when the Work has no deliverable score,
    // in which case the entry is not composed at all (same "no fake entry" rule as onPlaySong).
    onOpenScore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state = rememberCatalogWorkLyricsState(workId, viewModel)
    val work = state.bundle?.work
    val variants = remember(work) { work?.lyricsVariants().orEmpty() }
    val sourceImages = remember(work) { work?.lyricsSourceImages.orEmpty() }
    var selectedLanguage by rememberSelectedLyricsLanguage(variants)
    val selectedVariant = variants.firstOrNull { it.language.equals(selectedLanguage, ignoreCase = true) }
        ?: variants.firstOrNull()
    var showSourceImages by remember(workId) { mutableStateOf(false) }
    val displayingImages = showSourceImages && sourceImages.isNotEmpty()
    val bottomPadding = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
    val tablet = isTabletLyricsLayout()
    val phoneNumbersLine = remember(work) { formatWorkNumbers(work?.numberMeta.orEmpty()) }

    // Tablet only: back first hides the left-pane images, then leaves. Phones keep their old back.
    BackHandler(enabled = tablet && displayingImages) { showSourceImages = false }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (tablet) {
                        Text(
                            text = stringResource(R.string.work_lyrics_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // issue 121: on a phone the Work title and hymnal numbers live in the bar,
                        // so the body has no separate title block.
                        Column {
                            Text(
                                text = work?.canonicalTitle ?: initialTitle,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (phoneNumbersLine.isNotEmpty()) {
                                Text(
                                    text = phoneNumbersLine,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
        val header: @Composable () -> Unit = {
            CatalogWorkLyricsHeader(
                state = state,
                initialTitle = initialTitle,
                actions = {
                    // Playback/score rule: unavailable entries are not composed at all, never shown disabled.
                    val playSong = playableSongs.firstOrNull()
                    if (playSong != null || onOpenScore != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            playSong?.let { song ->
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
                            onOpenScore?.let { openScore ->
                                FilledTonalButton(onClick = openScore) {
                                    Icon(
                                        RhythmIcons.ScoreFilled,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.work_lyrics_open_score))
                                }
                            }
                        }
                    }
                },
            )
        }

        if (tablet) {
            CatalogLyricsTabletSplit(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                leftOverlay = if (displayingImages) {
                    { CatalogLyricSourceImagesView(images = sourceImages, modifier = Modifier.fillMaxSize()) }
                } else {
                    null
                },
                left = {},
                right = {
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                    ) {
                        header()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            CatalogWorkLyricsBody(
                                state = state,
                                selectedVariant = selectedVariant,
                                bottomPadding = bottomPadding,
                                onBack = onBack,
                            )
                        }
                    }
                },
            )
        } else {
            // issue 121: phone — nothing fixed above the lyrics except the top bar. The tag row
            // scrolls away with the lyrics; play/score are small floating icon buttons.
            val playSong = playableSongs.firstOrNull()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (state.bundle != null && displayingImages) {
                    CatalogLyricSourceImagesView(
                        images = sourceImages,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CatalogWorkLyricsBody(
                        state = state,
                        selectedVariant = selectedVariant,
                        // Room so the last lines can scroll clear of the floating buttons.
                        bottomPadding = bottomPadding + if (playSong != null || onOpenScore != null) 112.dp else 0.dp,
                        onBack = onBack,
                        leading = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 12.dp),
                            ) {
                                CatalogWorkTagsSection(work = work, onWorkUpdated = state::updateWork)
                                if (state.error != null && state.bundle != null) {
                                    Text(
                                        text = stringResource(R.string.work_lyrics_offline_cached),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
                // Playback/score rule: unavailable entries are not composed at all.
                if (playSong != null || onOpenScore != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = bottomPadding - 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        onOpenScore?.let { openScore ->
                            SmallFloatingActionButton(
                                onClick = openScore,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Icon(
                                    RhythmIcons.ScoreFilled,
                                    contentDescription = stringResource(R.string.work_lyrics_open_score),
                                )
                            }
                        }
                        playSong?.let { song ->
                            FloatingActionButton(
                                onClick = { onPlaySong(song) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(
                                    RhythmIcons.Play,
                                    contentDescription = stringResource(R.string.work_lyrics_play_song),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * issue 118: Work lyrics as a side pane next to a playing score. Independent of any player: it
 * reads the Work [workId] the score belongs to, never the background song, and loading, errors or
 * tag edits here never touch score playback. Source images are not offered (they would cover the
 * score). [onClose] hides the pane.
 */
@Composable
fun CatalogWorkLyricsPane(
    workId: String,
    initialTitle: String,
    viewModel: CatalogViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberCatalogWorkLyricsState(workId, viewModel)
    val work = state.bundle?.work
    val variants = remember(work) { work?.lyricsVariants().orEmpty() }
    var selectedLanguage by rememberSelectedLyricsLanguage(variants)
    val selectedVariant = variants.firstOrNull { it.language.equals(selectedLanguage, ignoreCase = true) }
        ?: variants.firstOrNull()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.score_lyrics_pane_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                CatalogLyricsLanguageMenu(
                    languages = variants.map { it.language },
                    selectedLanguage = selectedVariant?.language,
                    onLanguageSelect = { selectedLanguage = it },
                )
                IconButton(onClick = onClose) {
                    Icon(RhythmIcons.Close, contentDescription = stringResource(R.string.score_lyrics_pane_close))
                }
            }
            CatalogWorkLyricsHeader(state = state, initialTitle = initialTitle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                CatalogWorkLyricsBody(
                    state = state,
                    selectedVariant = selectedVariant,
                    bottomPadding = 24.dp,
                    onBack = onClose,
                )
            }
        }
    }
}

/** Load state of one Work's lyrics; [updateWork] applies a saved tag edit to the shown bundle. */
@Stable
class CatalogWorkLyricsState internal constructor(
    val workId: String,
    initialBundle: WorkBundle?,
) {
    var bundle by mutableStateOf(initialBundle)
        internal set
    var loading by mutableStateOf(true)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    internal var retryKey by mutableIntStateOf(0)

    fun retry() {
        retryKey++
    }

    /** Ignores a Work other than [workId]: a late save for a previous page must not leak in. */
    fun updateWork(work: WorkSummary) {
        val current = bundle ?: return
        if (work.id == workId) bundle = current.copy(work = work)
    }
}

@Composable
fun rememberCatalogWorkLyricsState(workId: String, viewModel: CatalogViewModel): CatalogWorkLyricsState {
    val state = remember(workId) { CatalogWorkLyricsState(workId, viewModel.cachedWorkBundle(workId)) }
    LaunchedEffect(state, state.retryKey) {
        state.loading = true
        viewModel.loadWorkLyrics(workId).fold(
            onSuccess = {
                state.bundle = it
                state.error = null
            },
            onFailure = { state.error = viewModel.errorMessage(it) },
        )
        state.loading = false
    }
    return state
}

/** Selected lyrics language, defaulting to the best match for the device locales. */
@Composable
private fun rememberSelectedLyricsLanguage(variants: List<CatalogLyricsVariant>) = run {
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
    remember(variants) {
        mutableStateOf(selectCatalogLyricsVariant(variants, preferredLanguageTags)?.language)
    }
}

/** Title, hymnal numbers, tags (issue 116), offline note and optional entry [actions]. */
@Composable
fun CatalogWorkLyricsHeader(
    state: CatalogWorkLyricsState,
    initialTitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val work = state.bundle?.work
    val numbersLine = remember(work) { formatWorkNumbers(work?.numberMeta.orEmpty()) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = work?.canonicalTitle ?: initialTitle,
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
        CatalogWorkTagsSection(work = work, onWorkUpdated = state::updateWork)
        if (state.error != null && state.bundle != null) {
            Text(
                text = stringResource(R.string.work_lyrics_offline_cached),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}

/** Loading, error, plain lyrics text or empty state of the selected variant. */
@Composable
fun CatalogWorkLyricsBody(
    state: CatalogWorkLyricsState,
    selectedVariant: CatalogLyricsVariant?,
    bottomPadding: Dp,
    onBack: () -> Unit,
    // issue 121: content that scrolls with the lyrics (the phone tag row).
    leading: @Composable () -> Unit = {},
) {
    when {
        state.bundle == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            M3CircularLoader()
        }
        state.bundle == null -> WorkLyricsError(
            message = state.error ?: stringResource(R.string.work_lyrics_load_failed),
            onRetry = state::retry,
            onBack = onBack,
        )
        selectedVariant != null -> {
            val text = remember(selectedVariant) { workLyricsDisplayText(selectedVariant.lyrics) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                leading()
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.5f,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = bottomPadding)),
                )
            }
        }
        else -> Column(Modifier.fillMaxWidth()) {
            leading()
            Text(
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
