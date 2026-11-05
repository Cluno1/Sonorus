package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementImagePolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementSummary
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/** issue 154: one quiet line under the home header, only for the newest important announcement. */
@Composable
internal fun CatalogAnnouncementBanner(
    announcement: CatalogAnnouncementSummary,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    MaterialSymbolIcon("close"),
                    contentDescription = stringResource(R.string.announcements_dismiss),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** issue 154: every published announcement, newest first. Unread titles are bold, nothing more. */
@Composable
fun CatalogAnnouncementsScreen(
    onBack: () -> Unit,
    onOpen: (id: String) -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { CatalogModule.announcements(context) }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val serverUrl = remember { CatalogModule.repository(context).connection().serverUrl }
    val listState = rememberLazyListState()
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()

    LaunchedEffect(Unit) { controller.refresh(force = true) }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) {
        if (nearEnd) controller.loadMore()
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.announcements_title),
        showBackButton = true,
        onBackClick = onBack,
    ) { modifier ->
        when {
            state.items.isEmpty() && state.loading -> Box(modifier, contentAlignment = Alignment.Center) {
                M3CircularLoader()
            }
            state.items.isEmpty() && state.loadFailed -> AnnouncementMessage(
                message = stringResource(R.string.announcements_load_failed),
                onRetry = { scope.launch { controller.refresh(force = true) } },
                modifier = modifier,
            )
            state.items.isEmpty() -> AnnouncementMessage(
                message = stringResource(R.string.announcements_empty),
                onRetry = null,
                modifier = modifier,
            )
            else -> LazyColumn(
                state = listState,
                modifier = modifier,
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    AnnouncementRow(
                        item = item,
                        unread = state.read.isUnread(item),
                        coverUrl = CatalogAnnouncementImagePolicy.url(serverUrl, item.coverImagePath),
                        onClick = { onOpen(item.id) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnouncementRow(
    item: CatalogAnnouncementSummary,
    unread: Boolean,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = announcementDate(item.publishedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
internal fun AnnouncementMessage(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.announcements_retry)) }
        }
    }
}

internal fun announcementDate(instant: Instant): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .format(instant.atZone(ZoneId.systemDefault()))
