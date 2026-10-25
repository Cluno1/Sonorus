package io.github.cluno1.sonorus.features.catalog.presentation

import android.widget.Toast
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoom
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomOrder
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomRow
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomWriteGate
import io.github.cluno1.sonorus.shared.presentation.components.common.DragDropLazyColumn
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.screens.CatalogWorkEntryKind
import io.github.cluno1.sonorus.shared.presentation.screens.CatalogWorkEntryRow
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import kotlinx.coroutines.launch

/**
 * issue 124: one room's songs in order, with ordinals and who added each, editable by everyone.
 *
 * Long-press drag (committed once on drop) or the up/down menu reorder; every reorder carries the
 * room revision, so when someone else changed the room first the server answers 412 and the list
 * reloads instead of silently overwriting their change. One write at a time ([CatalogRoomWriteGate]).
 * Named sections (room templates, a later phase) show as headers; today the list is plain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRoomDetailScreen(
    roomId: String,
    initialName: String,
    onBack: () -> Unit,
    onOpenLyrics: (CatalogLibrarySearchItem) -> Unit,
    onOpenScore: (CatalogLibrarySearchItem) -> Unit,
    onOpenSong: (CatalogLibrarySearchSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CatalogModule.rooms(context) }
    val gate = remember { CatalogRoomWriteGate() }
    var room by remember { mutableStateOf<CatalogRoom?>(null) }
    var rows by remember { mutableStateOf<List<CatalogRoomRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val listState = rememberLazyListState()

    fun show(updated: CatalogRoom) {
        room = updated
        rows = CatalogRoomOrder.rows(updated)
        error = null
    }

    fun fail(failure: Throwable) {
        if (failure is CatalogFailure.RoomUnavailable) gone = true
        Toast.makeText(context, roomErrorMessage(context, failure), Toast.LENGTH_SHORT).show()
        // A stale revision or any rejected write: show what the server has now.
        reloadKey++
    }

    /** Runs one room write; ignored while another is in flight. */
    fun write(block: suspend () -> Result<CatalogRoom>) {
        if (!gate.tryStart()) return
        writing = true
        scope.launch {
            try {
                block().fold(onSuccess = ::show, onFailure = ::fail)
            } finally {
                writing = false
                gate.finish()
            }
        }
    }

    fun submitOrder(newRows: List<CatalogRoomRow>) {
        val current = room ?: return
        rows = newRows
        write { repository.reorderRoom(current.id, CatalogRoomOrder.toOrder(current, newRows), current.revision) }
    }

    LaunchedEffect(reloadKey) {
        if (reloadKey == 0) return@LaunchedEffect
        repository.room(roomId).fold(
            onSuccess = ::show,
            onFailure = {
                if (it is CatalogFailure.RoomUnavailable) gone = true
                error = roomErrorMessage(context, it)
            },
        )
    }
    LifecycleResumeEffect(roomId) {
        reloadKey++
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = room?.name ?: initialName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        room?.let {
                            Text(
                                text = roomDateLabel(it.serviceDate) + " · " +
                                    stringResource(R.string.rooms_song_count, it.itemCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(RhythmIcons.Back, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(MaterialSymbolIcon("refresh"), contentDescription = stringResource(R.string.rooms_refresh))
                    }
                    if (room?.canDelete == true) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(MaterialSymbolIcon("more_vert"), contentDescription = stringResource(R.string.rooms_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rooms_delete)) },
                                    leadingIcon = { Icon(MaterialSymbolIcon("delete"), contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
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
            val current = room
            when {
                gone -> RoomsMessage(
                    title = stringResource(R.string.rooms_room_gone),
                    body = "",
                    action = stringResource(R.string.cd_back),
                    onAction = onBack,
                )
                current == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    M3CircularLoader()
                }
                current == null -> RoomsMessage(
                    title = stringResource(R.string.rooms_load_failed),
                    body = error.orEmpty(),
                    action = stringResource(R.string.rooms_retry),
                    onAction = { reloadKey++ },
                )
                current.itemCount == 0 -> RoomsMessage(
                    title = stringResource(R.string.rooms_detail_empty),
                    body = "",
                    action = null,
                    onAction = {},
                )
                else -> {
                    val ordinals = remember(rows) { CatalogRoomOrder.ordinals(rows) }
                    Column(Modifier.fillMaxSize()) {
                        if (writing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.rooms_reorder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                        DragDropLazyColumn(
                            items = rows,
                            modifier = Modifier.fillMaxSize(),
                            lazyListState = listState,
                            onMove = { from, to -> rows = CatalogRoomOrder.move(rows, from, to) },
                            onDragFinished = { submitOrder(rows) },
                            itemKey = CatalogRoomRow::key,
                            isReorderableItem = { it is CatalogRoomRow.Song && !writing },
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
                            itemSpacing = 10.dp,
                            animateItemPlacement = true,
                        ) { row, dragging, _ ->
                            when (row) {
                                is CatalogRoomRow.Header -> RoomSectionHeader(row)
                                is CatalogRoomRow.Song -> RoomSongRow(
                                    row = row,
                                    ordinal = ordinals[row.item.id] ?: row.item.position,
                                    dragging = dragging,
                                    canUp = CatalogRoomOrder.canStep(rows, row.item.id, up = true),
                                    canDown = CatalogRoomOrder.canStep(rows, row.item.id, up = false),
                                    enabled = !writing,
                                    onUp = { submitOrder(CatalogRoomOrder.step(rows, row.item.id, up = true)) },
                                    onDown = { submitOrder(CatalogRoomOrder.step(rows, row.item.id, up = false)) },
                                    onRemove = { write { repository.removeRoomSong(current.id, row.item.id) } },
                                    onOpenLyrics = { onOpenLyrics(row.item.work) },
                                    onOpenScore = { onOpenScore(row.item.work) },
                                    onOpenSong = onOpenSong,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val current = room
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.rooms_delete_confirm_title, current?.name ?: initialName)) },
            text = { Text(stringResource(R.string.rooms_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (current == null || !gate.tryStart()) return@TextButton
                    writing = true
                    scope.launch {
                        try {
                            repository.deleteRoom(current.id).fold(
                                onSuccess = { onBack() },
                                onFailure = ::fail,
                            )
                        } finally {
                            writing = false
                            gate.finish()
                        }
                    }
                }) {
                    Text(stringResource(R.string.rooms_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.rooms_cancel)) }
            },
        )
    }
}

@Composable
private fun RoomSectionHeader(row: CatalogRoomRow.Header) {
    val section = row.section
    val name = section.name ?: stringResource(R.string.rooms_section_untitled, section.position)
    Text(
        text = section.assignee?.let { stringResource(R.string.rooms_section_assignee, name, roomUserName(it)) } ?: name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun RoomSongRow(
    row: CatalogRoomRow.Song,
    ordinal: Int,
    dragging: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenScore: () -> Unit,
    onOpenSong: (CatalogLibrarySearchSong) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (dragging) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            // Large ordinal so people can follow "第几首" at a glance.
            Text(
                text = ordinal.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 36.dp),
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                CatalogWorkEntryRow(
                    item = row.item.work,
                    onOpenLyrics = onOpenLyrics,
                    onOpenScore = onOpenScore,
                    onOpenSong = onOpenSong,
                    kind = if (row.item.work.numbers.isNotEmpty()) CatalogWorkEntryKind.HYMNAL_NUMBER else CatalogWorkEntryKind.WORK,
                )
                Text(
                    text = stringResource(R.string.rooms_added_by, roomUserName(row.item.addedBy)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(
                        MaterialSymbolIcon("drag_indicator"),
                        contentDescription = stringResource(R.string.rooms_drag_handle),
                        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rooms_move_up)) },
                        leadingIcon = { Icon(MaterialSymbolIcon("arrow_upward"), contentDescription = null) },
                        enabled = canUp,
                        onClick = {
                            menuOpen = false
                            onUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rooms_move_down)) },
                        leadingIcon = { Icon(MaterialSymbolIcon("arrow_downward"), contentDescription = null) },
                        enabled = canDown,
                        onClick = {
                            menuOpen = false
                            onDown()
                        },
                    )
                    if (row.item.canRemove) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rooms_remove)) },
                            leadingIcon = { Icon(MaterialSymbolIcon("remove_circle"), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}
