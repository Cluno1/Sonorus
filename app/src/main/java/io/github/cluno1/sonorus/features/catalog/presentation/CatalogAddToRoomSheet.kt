package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoom
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSectionSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomTarget
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomWriteGate
import io.github.cluno1.sonorus.features.catalog.domain.rankRoomTargets
import io.github.cluno1.sonorus.features.catalog.domain.roomUserLabel
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * issue 124: "添加到房间" from a Work's lyrics page. Lists the rooms that have not expired (ranked by
 * [rankRoomTargets]: sections the reader leads first, then nearest gathering), marks
 * rooms that already hold this song with who added it, and adds with one tap — appended at the end.
 * A new room can be created right here; the song is then added to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogAddToRoomSheet(
    workId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CatalogModule.rooms(context) }
    val currentUserId = remember { runCatching { CatalogModule.repository(context).connection().userId }.getOrNull() }
    val gate = remember { CatalogRoomWriteGate() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var targets by remember { mutableStateOf<List<CatalogRoomTarget>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var addingRoomId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var expandedRoomId by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        repository.listRooms(workId).fold(
            onSuccess = {
                targets = rankRoomTargets(it, currentUserId)
                error = null
            },
            onFailure = { error = roomErrorMessage(context, it) },
        )
    }

    fun added(room: CatalogRoom) {
        val position = room.sections.flatMap { it.items }.firstOrNull { it.work.workId == workId }?.position
            ?: room.itemCount
        RhythmMessages.success(context, context.getString(R.string.rooms_added_toast, room.name, position))
        onDismiss()
    }

    fun add(target: CatalogRoomTarget) {
        if (!gate.tryStart()) return
        addingRoomId = target.room.id
        // One key per tap: a retried request replays instead of adding the song twice.
        val key = UUID.randomUUID().toString()
        scope.launch {
            try {
                repository.addRoomSong(target.room.id, workId, target.section?.id, key).fold(
                    onSuccess = ::added,
                    onFailure = { failure ->
                        val message = if (failure is CatalogFailure.RoomSongAlreadyAdded) {
                            context.getString(
                                R.string.rooms_already_in_room,
                                failure.addedBy?.let { roomUserLabel(it, context.getString(R.string.rooms_user_fallback)) }
                                    ?: context.getString(R.string.rooms_user_fallback),
                            )
                        } else {
                            roomErrorMessage(context, failure)
                        }
                        RhythmMessages.info(context, message)
                        reloadKey++
                    },
                )
            } finally {
                addingRoomId = null
                gate.finish()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.rooms_add_to_room),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val current = targets
            when {
                current == null && error == null -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                current == null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                current.isEmpty() -> Text(
                    stringResource(R.string.rooms_add_sheet_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current, key = { "${it.room.id}:${it.section?.id}:${it.assignedToMe}" }) { target ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val expandable = target.section == null && target.room.sections.size > 1
                            RoomTargetRow(
                                target = target,
                                adding = addingRoomId == target.room.id,
                                enabled = addingRoomId == null,
                                onClick = {
                                    if (expandable) {
                                        expandedRoomId = if (expandedRoomId == target.room.id) null else target.room.id
                                    } else {
                                        add(target)
                                    }
                                },
                            )
                            if (expandable && expandedRoomId == target.room.id) {
                                target.room.sections.forEach { section ->
                                    RoomTargetRow(
                                        target = CatalogRoomTarget(target.room, section, assignedToMe = false),
                                        adding = addingRoomId == target.room.id,
                                        enabled = addingRoomId == null,
                                        onClick = { add(CatalogRoomTarget(target.room, section, assignedToMe = false)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(MaterialSymbolIcon("add"), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rooms_create))
            }
        }
    }

    if (creating) {
        CatalogCreateRoomDialog(
            onDismiss = { creating = false },
            onCreated = { room ->
                creating = false
                val newRoom = CatalogRoomSummary(
                    id = room.id,
                    name = room.name,
                    serviceDate = room.serviceDate,
                    createdBy = room.createdBy,
                    itemCount = room.itemCount,
                    revision = room.revision,
                    canDelete = room.canDelete,
                    sections = room.sections.map { section ->
                        CatalogRoomSectionSummary(
                            id = section.id, name = section.name, position = section.position,
                            assignee = section.assignee, itemCount = section.items.size,
                        )
                    },
                    containsWork = null,
                )
                if (newRoom.sections.size == 1) {
                    add(CatalogRoomTarget(newRoom, newRoom.sections.single(), assignedToMe = false))
                } else {
                    targets = listOf(CatalogRoomTarget(newRoom, section = null, assignedToMe = false)) + targets.orEmpty()
                    expandedRoomId = newRoom.id
                }
            },
        )
    }
}

@Composable
private fun RoomTargetRow(
    target: CatalogRoomTarget,
    adding: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val room = target.room
    val contained = room.containsWork
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (target.assignedToMe) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && contained == null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = roomDateLabel(room.serviceDate) + " · " +
                        stringResource(R.string.rooms_song_count, room.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                target.section?.let { section ->
                    Text(
                        text = if (target.assignedToMe) stringResource(
                            R.string.rooms_assigned_to_me,
                            section.name ?: stringResource(R.string.rooms_section_untitled, section.position),
                        ) else section.assignee?.let {
                            stringResource(
                                R.string.rooms_section_assignee,
                                section.name ?: stringResource(R.string.rooms_section_untitled, section.position),
                                roomUserName(it),
                            )
                        } ?: (section.name ?: stringResource(R.string.rooms_section_untitled, section.position)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (target.section == null && room.sections.size > 1) {
                    Text(
                        text = stringResource(R.string.rooms_choose_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                contained?.let {
                    Text(
                        text = stringResource(R.string.rooms_already_added, roomUserName(it.addedBy), it.position),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                adding -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                contained != null -> Icon(MaterialSymbolIcon("check_circle"), contentDescription = null)
                else -> Icon(MaterialSymbolIcon("playlist_add"), contentDescription = null)
            }
        }
    }
}
