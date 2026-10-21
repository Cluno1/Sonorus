package io.github.cluno1.sonorus.shared.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogTagWorksPage
import io.github.cluno1.sonorus.features.catalog.presentation.CatalogViewModel
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import kotlinx.coroutines.launch

/** A server-paged list of Works with one exact Catalog tag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogTagWorksScreen(
    tagKey: String,
    initialLabel: String,
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onOpenLyrics: (CatalogLibrarySearchItem) -> Unit,
    onOpenScore: (CatalogLibrarySearchItem) -> Unit,
    onOpenSong: (CatalogLibrarySearchSong) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember(tagKey) { mutableStateOf<CatalogTagWorksPage?>(null) }
    var loading by remember(tagKey) { mutableStateOf(true) }
    var loadingMore by remember(tagKey) { mutableStateOf(false) }
    var error by remember(tagKey) { mutableStateOf<String?>(null) }
    var loadMoreError by remember(tagKey) { mutableStateOf<String?>(null) }
    var refreshVersion by remember(tagKey) { mutableIntStateOf(0) }

    LaunchedEffect(tagKey, refreshVersion) {
        loading = true
        error = null
        page = null
        viewModel.listTagWorks(tagKey).fold(
            onSuccess = { page = it },
            onFailure = { failure ->
                error = if (failure is CatalogFailure.Server && failure.statusCode == 404) {
                    context.getString(R.string.search_tags_unavailable)
                } else {
                    viewModel.errorMessage(failure)
                }
            },
        )
        loading = false
    }

    val currentPage = page
    val label = currentPage?.tag?.label ?: initialLabel
    val miniPlayerPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(RhythmIcons.Back, contentDescription = stringResource(R.string.search_tag_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshVersion++ },
                        enabled = !loading && !loadingMore,
                    ) {
                        Icon(MaterialSymbolIcon("refresh"), contentDescription = stringResource(R.string.search_tag_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + miniPlayerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            currentPage?.let { loaded ->
                item(key = "tag_count") {
                    Text(
                        stringResource(R.string.search_tag_work_count, loaded.tag.workCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (loading) {
                item(key = "tag_loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (currentPage == null) {
                item(key = "tag_error") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(error ?: stringResource(R.string.search_tag_load_failed))
                        TextButton(onClick = { refreshVersion++ }) { Text(stringResource(R.string.work_lyrics_retry)) }
                    }
                }
            } else if (currentPage.items.isEmpty()) {
                item(key = "tag_empty") {
                    Text(
                        stringResource(R.string.search_tag_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(currentPage?.items.orEmpty(), key = { it.workId }) { work ->
                CatalogWorkEntryRow(
                    item = work,
                    tagLabel = label,
                    onOpenLyrics = { onOpenLyrics(work) },
                    onOpenScore = { onOpenScore(work) },
                    onOpenSong = onOpenSong,
                )
            }
            currentPage?.nextCursor?.let { cursor ->
                item(key = "tag_more") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        loadMoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = {
                                if (!loadingMore) {
                                    loadingMore = true
                                    loadMoreError = null
                                    scope.launch {
                                        viewModel.listTagWorks(tagKey, cursor).fold(
                                            onSuccess = { next ->
                                                if (next.tag.key == tagKey && page?.nextCursor == cursor) {
                                                    page = next.copy(
                                                        items = (page?.items.orEmpty() + next.items).distinctBy { it.workId },
                                                        nextCursor = next.nextCursor?.takeIf { it != cursor },
                                                    )
                                                }
                                            },
                                            onFailure = { loadMoreError = viewModel.errorMessage(it) },
                                        )
                                        loadingMore = false
                                    }
                                }
                            },
                            enabled = !loadingMore,
                        ) {
                            if (loadingMore) CircularProgressIndicator()
                            else Text(stringResource(R.string.search_tag_load_more))
                        }
                    }
                }
            }
        }
    }
}
