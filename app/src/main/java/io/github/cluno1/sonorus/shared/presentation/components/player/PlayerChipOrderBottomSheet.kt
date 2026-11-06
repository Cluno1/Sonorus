/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package io.github.cluno1.sonorus.shared.presentation.components.player

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.SheetAdaptiveType
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.StandardBottomSheetHeader

import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.cluno1.sonorus.shared.presentation.components.common.DragDropLazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.presentation.components.common.ButtonGroupStyle
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmGroupedButton
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonWeighted
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonSize
import io.github.cluno1.sonorus.util.HapticUtils
import io.github.cluno1.sonorus.util.HapticType
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer

private fun groupedChipItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 8.dp,
            topEnd = 8.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(8.dp)
    }
}

@Composable
fun PlayerChipOrderBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val chipOrder by appSettings.playerChipOrder.collectAsState()
    val hiddenChips by appSettings.hiddenPlayerChips.collectAsState()
    var reorderableList by remember { mutableStateOf(chipOrder.toList()) }
    var hiddenChipsSet by remember { mutableStateOf(hiddenChips) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    
    // Helper function to get display name and icon for chip
    fun getChipInfo(chipId: String): Pair<String, MaterialSymbolIcon> {
        return when (chipId) {
            "FAVORITE" -> Pair(context.getString(R.string.player_chip_favorite), RhythmIcons.FavoriteFilled)
            "SPEED_PITCH" -> Pair(context.getString(R.string.player_speed_and_pitch), MaterialSymbolIcon("tune", filled = true))
            "SPEED" -> Pair(context.getString(R.string.player_speed_label), MaterialSymbolIcon("speed", filled = true))
            "PITCH" -> Pair(context.getString(R.string.player_pitch_label), MaterialSymbolIcon("graphic_eq", filled = true))
            "EQUALIZER" -> Pair(context.getString(R.string.equalizer), MaterialSymbolIcon("graphic_eq", filled = true))
            "SLEEP_TIMER" -> Pair(context.getString(R.string.sleep_timer), RhythmIcons.AccessTime)
            "LYRICS" -> Pair(context.getString(R.string.player_chip_lyrics), MaterialSymbolIcon("lyrics", filled = true))
            "ALBUM" -> Pair(context.getString(R.string.player_chip_album), RhythmIcons.Music.Album)
            "ARTIST" -> Pair(context.getString(R.string.player_chip_artist), RhythmIcons.Music.Artist)
            "SHARE" -> Pair(context.getString(R.string.action_share), RhythmIcons.Share)
            else -> Pair(chipId, RhythmIcons.Edit)
        }
    }
    
    val lazyListState = rememberLazyListState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        lazyListState = lazyListState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = context.getString(R.string.player_chip_order_title),
            subtitle = context.getString(R.string.player_chip_order_desc),
            visible = true
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            // Reorderable list using DragDropLazyColumn inside AdaptiveSheetScrollContainer
            AdaptiveSheetScrollContainer(
                lazyListState = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { endPadding ->
                DragDropLazyColumn(
                    items = reorderableList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp + endPadding),
                    lazyListState = lazyListState,
                onMove = { fromIndex, toIndex ->
                    val newList = reorderableList.toMutableList()
                    val item = newList.removeAt(fromIndex)
                    newList.add(toIndex, item)
                    reorderableList = newList
                },
                itemKey = { it }
            ) { chip, isDragging, index ->
                val (name, icon) = getChipInfo(chip)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) 
                            MaterialTheme.colorScheme.secondaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = groupedChipItemShape(index, reorderableList.size)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Position indicator
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            // Chip icon
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            
                            // Chip name
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Visibility toggle and drag handle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Visibility toggle button
                            val isHidden = hiddenChipsSet.contains(chip)
                            val visibleChipsCount = reorderableList.count { !hiddenChipsSet.contains(it) }
                            
                            IconButton(
                                onClick = {
                                    // Prevent hiding the last visible chip
                                    if (!isHidden && visibleChipsCount <= 1) {
                                        RhythmMessages.error(context, R.string.player_chip_one_visible)
                                        return@IconButton
                                    }
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    hiddenChipsSet = if (isHidden) {
                                        hiddenChipsSet - chip
                                    } else {
                                        hiddenChipsSet + chip
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHidden) RhythmIcons.VisibilityOff else RhythmIcons.Visibility,
                                    contentDescription = context.getString(
                                        if (isHidden) R.string.player_chip_show else R.string.player_chip_hide
                                    ),
                                    tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Drag Handle Icon
                            Icon(
                                imageVector = RhythmIcons.DragHandle,
                                contentDescription = stringResource(R.string.drag_to_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
            
        // Sticky Footer at the bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                RhythmGroupedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    size = RhythmButtonSize.Large
                ) {
                    // Reset button
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            appSettings.resetPlayerChipOrder()
                            appSettings.setHiddenPlayerChips(emptySet())
                            reorderableList = listOf("FAVORITE", "SPEED", "EQUALIZER", "SLEEP_TIMER", "LYRICS", "ALBUM", "ARTIST", "SHARE")
                            hiddenChipsSet = emptySet()
                            RhythmMessages.success(context, R.string.player_chip_order_reset)
                        },
                        weight = 1f,
                        isFirst = true,
                        icon = MaterialSymbolIcon("restart_alt"),
                        text = context.getString(R.string.bottomsheet_reset)
                    )

                    // Save button
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            appSettings.setPlayerChipOrder(reorderableList)
                            appSettings.setHiddenPlayerChips(hiddenChipsSet)
                            RhythmMessages.success(context, R.string.player_chip_order_saved)
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    onDismiss()
                                }
                            }
                        },
                        weight = 1f,
                        isLast = true,
                        icon = RhythmIcons.Check,
                        text = context.getString(R.string.bottomsheet_save)
                    )
                }
            }
        }
    }
}
