/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.shared.presentation.components.bottomsheets
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.SheetAdaptiveType

import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.util.HapticUtils
import io.github.cluno1.sonorus.util.HapticType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.cluno1.sonorus.R
import androidx.compose.ui.res.stringResource

private data class ControlAction(
    val icon: MaterialSymbolIcon,
    val label: String,
    val description: String?,
    val containerColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit
)

private fun getGridItemShape(index: Int, totalItems: Int): RoundedCornerShape {
    if (totalItems <= 1) return RoundedCornerShape(24.dp)
    if (totalItems == 2) {
        return if (index == 0) {
            RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
        } else {
            RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
        }
    }
    
    val totalRows = (totalItems + 1) / 2
    val r = index / 2
    val c = index % 2
    
    return when {
        r == 0 -> {
            if (c == 0) {
                RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            }
        }
        r == totalRows - 1 -> {
            if (index == totalItems - 1 && c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            } else if (c == 0) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            }
        }
        else -> RoundedCornerShape(8.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraControlBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState,
    hiddenChips: Set<String>,
    equalizerEnabled: Boolean,
    sleepTimerActive: Boolean,
    sleepTimerRemainingSeconds: Long,
    lyrics: LyricsData?,
    overflowButtonIds: List<String> = emptyList(),
    isFavorite: Boolean = false,
    onToggleLyrics: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onOpenScore: () -> Unit = {},
    onEditTags: (() -> Unit)? = null,
    onDevice: () -> Unit = {},
    onQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onEditControls: (() -> Unit)? = null,
    onPlaybackSpeed: () -> Unit,
    onPlaybackPitch: () -> Unit = {},
    onEqualizer: () -> Unit,
    onSleepTimer: () -> Unit,
    onLyricsEditor: () -> Unit,
    onAlbum: () -> Unit = {},
    onArtist: () -> Unit = {},
    onSongInfo: () -> Unit,
    onShareFile: () -> Unit = {},
    haptic: HapticFeedback,
    isExtraSmallWidth: Boolean = false,
    isCompactWidth: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showContent by remember { mutableStateOf(true) }

    fun dismissAndDo(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val onSecondary = MaterialTheme.colorScheme.onSecondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val onTertiary = MaterialTheme.colorScheme.onTertiaryContainer
    val overflowIds = overflowButtonIds.filterNot { it == "MORE" }.distinct()
    val sleepLabel = if (sleepTimerActive) {
        val minutes = sleepTimerRemainingSeconds / 60
        val seconds = sleepTimerRemainingSeconds % 60
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    } else {
        context.getString(R.string.status_disabled)
    }

    fun controlAction(
        icon: MaterialSymbolIcon,
        label: String,
        description: String? = null,
        active: Boolean = false,
        hapticType: HapticType = HapticType.HEAVY,
        action: () -> Unit,
    ) = ControlAction(
        icon = icon,
        label = label,
        description = description,
        containerColor = if (active) tertiary else secondary,
        iconColor = if (active) onTertiary else onSecondary,
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, hapticType)
            dismissAndDo(action)
        },
    )

    fun overflowAction(buttonId: String): ControlAction? = when (buttonId) {
        "EDIT_TAGS" -> onEditTags?.let { action -> controlAction(
            icon = MaterialSymbolIcon("edit_note", filled = true),
            label = context.getString(R.string.lan_tags_edit), action = action,
        ) }
        "LYRICS" -> controlAction(
            icon = MaterialSymbolIcon("lyrics", filled = true),
            label = context.getString(R.string.expressiveplayerscreen_lyrics),
            hapticType = HapticType.LIGHT,
            action = onToggleLyrics,
        )
        "FAVORITE" -> controlAction(
            icon = if (isFavorite) RhythmIcons.Actions.Favorite else RhythmIcons.Actions.FavoriteOutlined,
            label = context.getString(R.string.expressiveplayerscreen_favorite),
            description = context.getString(
                if (isFavorite) R.string.player_favorite_remove_description
                else R.string.player_favorite_add_description,
            ),
            active = isFavorite,
            hapticType = HapticType.LIGHT,
            action = onToggleFavorite,
        )
        "SCORE" -> controlAction(
            icon = RhythmIcons.Score,
            label = context.getString(R.string.catalog_scores),
            hapticType = HapticType.LIGHT,
            action = onOpenScore,
        )
        "DEVICE" -> controlAction(
            icon = RhythmIcons.SpeakerFilled,
            label = context.getString(R.string.expressiveplayerscreen_device),
            action = onDevice,
        )
        "QUEUE" -> controlAction(
            icon = RhythmIcons.Queue,
            label = context.getString(R.string.bottomsheet_queue),
            action = onQueue,
        )
        "EQUALIZER" -> controlAction(
            icon = MaterialSymbolIcon("graphic_eq", filled = true),
            label = context.getString(R.string.equalizer),
            description = context.getString(
                if (equalizerEnabled) R.string.status_enabled else R.string.status_disabled,
            ),
            active = equalizerEnabled,
            action = onEqualizer,
        )
        "SPEED" -> controlAction(
            icon = MaterialSymbolIcon("tune", filled = true),
            label = context.getString(R.string.player_speed_and_pitch),
            description = context.getString(R.string.extrasheet_tempo_pitch),
            action = onPlaybackSpeed,
        )
        "SLEEP_TIMER" -> controlAction(
            icon = RhythmIcons.AccessTime,
            label = context.getString(R.string.sleep_timer),
            description = sleepLabel,
            active = sleepTimerActive,
            action = onSleepTimer,
        )
        "ADD_TO_PLAYLIST" -> controlAction(
            icon = RhythmIcons.AddToPlaylist,
            label = context.getString(R.string.bottomsheet_add_to_playlist),
            action = onAddToPlaylist,
        )
        "ALBUM" -> controlAction(
            icon = RhythmIcons.AlbumFilled,
            label = context.getString(R.string.multiselectionbottomsheet_go_to_album),
            action = onAlbum,
        )
        "ARTIST" -> controlAction(
            icon = RhythmIcons.ArtistFilled,
            label = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
            action = onArtist,
        )
        "SONG_INFO" -> controlAction(
            icon = RhythmIcons.Info,
            label = context.getString(R.string.action_song_info),
            action = onSongInfo,
        )
        "SHARE" -> controlAction(
            icon = RhythmIcons.Share,
            label = context.getString(R.string.extrasheet_share_file),
            action = onShareFile,
        )
        else -> null
    }

    val actions = buildList {
        overflowIds.mapNotNull(::overflowAction).forEach { add(it) }

        onEditControls?.let { editControls ->
            add(ControlAction(
                icon = RhythmIcons.Edit,
                label = context.getString(R.string.bottomsheet_edit_controls),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { editControls() }
                }
            ))
        }

        if ("ADD_TO_PLAYLIST" !in overflowIds) {
            add(controlAction(
                icon = RhythmIcons.AddToPlaylist,
                label = context.getString(R.string.bottomsheet_add_to_playlist),
                action = onAddToPlaylist,
            ))
        }

        if ("SPEED" !in overflowIds && ("SPEED" !in hiddenChips || "PITCH" !in hiddenChips)) {
            add(ControlAction(
                icon = MaterialSymbolIcon("tune", filled = true),
                label = context.getString(R.string.player_speed_and_pitch),
                description = context.getString(R.string.extrasheet_tempo_pitch),
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onPlaybackSpeed() }
                }
            ))
        }

        if ("EQUALIZER" !in overflowIds && "EQUALIZER" !in hiddenChips) {
            add(ControlAction(
                icon = MaterialSymbolIcon("graphic_eq", filled = true),
                label = context.getString(R.string.equalizer),
                description = if (equalizerEnabled) context.getString(R.string.status_enabled) else context.getString(R.string.status_disabled),
                containerColor = if (equalizerEnabled) tertiary else secondary,
                iconColor = if (equalizerEnabled) onTertiary else onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onEqualizer() }
                }
            ))
        }

        if ("SLEEP_TIMER" !in overflowIds && "SLEEP_TIMER" !in hiddenChips) {
            add(ControlAction(
                icon = RhythmIcons.AccessTime,
                label = context.getString(R.string.sleep_timer),
                description = sleepLabel,
                containerColor = if (sleepTimerActive) tertiary else secondary,
                iconColor = if (sleepTimerActive) onTertiary else onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onSleepTimer() }
                }
            ))
        }

        if ("LYRICS" !in hiddenChips) {
            val hasLyrics = lyrics != null && lyrics.hasLyrics() && !lyrics.isErrorMessage()
            add(ControlAction(
                icon = if (hasLyrics) RhythmIcons.Edit else MaterialSymbolIcon("lyrics", filled = true),
                label = if (hasLyrics) context.getString(R.string.action_edit_lyrics) else context.getString(R.string.action_add_lyrics),
                description = if (hasLyrics) context.getString(R.string.extrasheet_has_lyrics) else null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onLyricsEditor() }
                }
            ))
        }

        if ("ALBUM" !in overflowIds && "ALBUM" !in hiddenChips) {
            add(ControlAction(
                icon = RhythmIcons.AlbumFilled,
                label = context.getString(R.string.multiselectionbottomsheet_go_to_album),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onAlbum() }
                }
            ))
        }

        if ("ARTIST" !in overflowIds && "ARTIST" !in hiddenChips) {
            add(ControlAction(
                icon = RhythmIcons.ArtistFilled,
                label = context.getString(R.string.multiselectionbottomsheet_go_to_artist),
                description = null,
                containerColor = secondary,
                iconColor = onSecondary,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    dismissAndDo { onArtist() }
                }
            ))
        }

        if ("SONG_INFO" !in overflowIds) {
            add(controlAction(
                icon = RhythmIcons.Info,
                label = context.getString(R.string.action_song_info),
                action = onSongInfo,
            ))
        }

        if ("SHARE" !in overflowIds) {
            add(controlAction(
                icon = RhythmIcons.Share,
                label = context.getString(R.string.extrasheet_share_file),
                action = onShareFile,
            ))
        }
    }

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        scrollState = scrollState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header — matches SongOptionsBottomSheet style
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_shapes_player_controls),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            text = stringResource(R.string.libraryscreen_more_actions),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Grouped status grid layout matching RhythmGuardTrendsRow
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                AdaptiveSheetScrollContainer(
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) { endPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 8.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    actions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowActions.forEachIndexed { colIndex, action ->
                                val overallIndex = rowIndex * 2 + colIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    ControlGridItem(
                                        icon = action.icon,
                                        text = action.label,
                                        description = action.description,
                                        containerColor = action.containerColor,
                                        iconColor = action.iconColor,
                                        shape = getGridItemShape(overallIndex, actions.size),
                                        onClick = action.onClick,
                                        modifier = Modifier.fillMaxHeight()
                                    )
                                }
                            }
                            if (rowActions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
}

@Composable
private fun ControlGridItem(
    icon: MaterialSymbolIcon,
    text: String,
    description: String?,
    containerColor: Color,
    iconColor: Color,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = containerColor.copy(alpha = 0.25f),
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
