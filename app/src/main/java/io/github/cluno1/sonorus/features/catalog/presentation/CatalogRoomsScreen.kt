package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSummary
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding

/**
 * issue 124: the "房间" tab — rooms that have not expired, nearest gathering first, and a button
 * to create one. Refreshes on entry, on resume and by pull-to-refresh; expired rooms simply stop
 * coming back from the server (it purges them lazily).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRoomsScreen(
    onOpenRoom: (roomId: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var rooms by remember { mutableStateOf<List<CatalogRoomSummary>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()

    LaunchedEffect(reloadKey) {
        // Key 0 is before the first resume; LifecycleResumeEffect below triggers the first load.
        if (reloadKey == 0) return@LaunchedEffect
        loading = true
        CatalogModule.rooms(context).listRooms().fold(
            onSuccess = {
                rooms = it
                error = null
            },
            onFailure = { error = roomErrorMessage(context, it) },
        )
        loading = false
    }
    LifecycleResumeEffect(Unit) {
        reloadKey++
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rooms_title)) },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(MaterialSymbolIcon("refresh"), contentDescription = stringResource(R.string.rooms_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(MaterialSymbolIcon("add"), contentDescription = null) },
                text = { Text(stringResource(R.string.rooms_create)) },
                modifier = Modifier.padding(bottom = bottomPadding),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = loading && rooms != null,
            onRefresh = { reloadKey++ },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val current = rooms
            when {
                current == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3CircularLoader()
                }
                current == null -> RoomsMessage(
                    title = stringResource(R.string.rooms_load_failed),
                    body = error.orEmpty(),
                    action = stringResource(R.string.rooms_retry),
                    onAction = { reloadKey++ },
                )
                current.isEmpty() -> RoomsMessage(
                    title = stringResource(R.string.rooms_empty),
                    body = stringResource(R.string.rooms_empty_hint),
                    action = stringResource(R.string.rooms_create),
                    onAction = { creating = true },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current, key = CatalogRoomSummary::id) { room ->
                        RoomCard(room = room, onClick = { onOpenRoom(room.id, room.name) })
                    }
                }
            }
        }
    }

    if (creating) {
        CatalogCreateRoomDialog(
            onDismiss = { creating = false },
            onCreated = { room ->
                creating = false
                reloadKey++
                onOpenRoom(room.id, room.name)
            },
        )
    }
}

@Composable
private fun RoomCard(room: CatalogRoomSummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = room.serviceDate.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = roomDateLabel(room.serviceDate) + " · " +
                        stringResource(R.string.rooms_song_count, room.itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.rooms_created_by, roomUserName(room.createdBy)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(MaterialSymbolIcon("chevron_right"), contentDescription = null)
        }
    }
}

@Composable
internal fun RoomsMessage(title: String, body: String, action: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(MaterialSymbolIcon("meeting_room"), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (body.isNotBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            action?.let { FilledTonalButton(onClick = onAction) { Text(it) } }
        }
    }
}
