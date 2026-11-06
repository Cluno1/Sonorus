/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.cluno1.sonorus.shared.presentation.screens.settings



import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.cluno1.sonorus.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.data.model.Playlist
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository
import io.github.cluno1.sonorus.shared.data.repository.StatsTimeRange
import io.github.cluno1.sonorus.util.GsonUtils
import io.github.cluno1.sonorus.util.HapticUtils
import io.github.cluno1.sonorus.util.HapticType
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.system.exitProcess
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.common.ButtonGroupStyle
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveScrollBar
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveButtonGroup
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveGroupButton
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import io.github.cluno1.sonorus.shared.presentation.components.common.StyledProgressBar
import io.github.cluno1.sonorus.shared.presentation.components.common.ProgressStyle
import io.github.cluno1.sonorus.shared.presentation.components.common.ThumbStyle
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.LicensesBottomSheet
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.UpdateBottomSheet
import io.github.cluno1.sonorus.ui.utils.LazyListStateSaver
import io.github.cluno1.sonorus.features.local.presentation.viewmodel.MusicViewModel
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveShapeProvider
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveShapes
import io.github.cluno1.sonorus.shared.presentation.components.common.buildSplashBackdropShapes
import io.github.cluno1.sonorus.shared.presentation.components.common.SplashBackgroundOrbs
import io.github.cluno1.sonorus.shared.presentation.viewmodel.AppUpdaterViewModel
import io.github.cluno1.sonorus.shared.presentation.viewmodel.AppVersion
import io.github.cluno1.sonorus.ui.theme.getFontPreviewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import io.github.cluno1.sonorus.utils.FontLoader
import io.github.cluno1.sonorus.ui.theme.parseCustomColorScheme
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.text.HtmlCompat
import io.github.cluno1.sonorus.shared.presentation.components.common.M3FourColorCircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.player.PlayingEqIcon
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.CreatePlaylistDialog
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.BulkPlaylistExportDialog
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.PlaylistImportDialog
import io.github.cluno1.sonorus.shared.presentation.components.common.rememberExpressiveShape
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import io.github.cluno1.sonorus.shared.presentation.components.dialogs.AppRestartDialog
import io.github.cluno1.sonorus.shared.presentation.components.player.PlayerChipOrderBottomSheet
import io.github.cluno1.sonorus.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import io.github.cluno1.sonorus.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import io.github.cluno1.sonorus.shared.presentation.components.Material3SettingsGroup
import io.github.cluno1.sonorus.shared.presentation.components.Material3SettingsItem

import io.github.cluno1.sonorus.shared.presentation.screens.settings.TunerSettingRow
import io.github.cluno1.sonorus.shared.presentation.screens.settings.TunerAnimatedSwitch
import io.github.cluno1.sonorus.shared.presentation.screens.settings.TunerSettingCard
import io.github.cluno1.sonorus.shared.presentation.screens.settings.SettingItem
import io.github.cluno1.sonorus.shared.presentation.screens.settings.SettingGroup


// Library Tab Order Screen (merged from LibraryTabOrderBottomSheet)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTabOrderSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val tabOrder by appSettings.libraryTabOrder.collectAsState()
    var reorderableList by remember { mutableStateOf(tabOrder.toList()) }

    // Helper function to get display name and icon for tab
    fun getTabInfo(tabId: String): Pair<String, MaterialSymbolIcon> {
        return when (tabId) {
            "SONGS" -> Pair(context.getString(R.string.settings_tab_songs), RhythmIcons.HeadphonesFilled)
            "LIKED" -> Pair(context.getString(R.string.settings_tab_liked), RhythmIcons.FavoriteFilled)
            "PLAYLISTS" -> Pair(context.getString(R.string.settings_tab_playlists), RhythmIcons.PlaylistFilled)
            "ALBUMS" -> Pair(context.getString(R.string.settings_tab_albums), RhythmIcons.Music.Album)
            "ARTISTS" -> Pair(context.getString(R.string.settings_tab_artists), RhythmIcons.Artist)
            "SCORES" -> Pair(context.getString(R.string.catalog_scores), RhythmIcons.Score)
            "ALBUM_ARTISTS" -> Pair(context.getString(R.string.settings_tab_album_artists), MaterialSymbolIcon("person_pin"))
            "EXPLORER" -> Pair(context.getString(R.string.settings_tab_explorer), RhythmIcons.Folder)
            else -> Pair(tabId, RhythmIcons.Music.Song)
        }
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_tab_order),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = context.getString(R.string.library_reorder_tabs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Reorderable list
            itemsIndexed(
                items = reorderableList,
                key = { _, item -> item }
            ) { index, tabId ->
                val (tabName, tabIcon) = getTabInfo(tabId)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .animateItem(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
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

                            // Tab icon
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )

                            // Tab name
                            Text(
                                text = tabName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Reorder buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Move up button
                            FilledIconButton(
                                onClick = {
                                    if (index > 0) {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        val newList = reorderableList.toMutableList()
                                        val item = newList.removeAt(index)
                                        newList.add(index - 1, item)
                                        reorderableList = newList
                                    }
                                },
                                enabled = index > 0,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.ArrowUpward,
                                    contentDescription = context.getString(R.string.settings_move_up),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Move down button
                            FilledIconButton(
                                onClick = {
                                    if (index < reorderableList.size - 1) {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        val newList = reorderableList.toMutableList()
                                        val item = newList.removeAt(index)
                                        newList.add(index + 1, item)
                                        reorderableList = newList
                                    }
                                },
                                enabled = index < reorderableList.size - 1,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.ArrowDownward,
                                    contentDescription = context.getString(R.string.settings_move_down),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reset button
                    OutlinedButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            appSettings.resetLibraryTabOrder()
                            reorderableList = listOf("SONGS", "LIKED", "PLAYLISTS", "ALBUMS", "ARTISTS", "EXPLORER")
                            RhythmMessages.success(context, context.getString(R.string.settings_tab_order_reset))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("restart_alt"),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.ui_reset))
                    }

                    // Save button
                    Button(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            appSettings.setLibraryTabOrder(reorderableList)
                            RhythmMessages.success(context, context.getString(R.string.settings_tab_order_saved))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
