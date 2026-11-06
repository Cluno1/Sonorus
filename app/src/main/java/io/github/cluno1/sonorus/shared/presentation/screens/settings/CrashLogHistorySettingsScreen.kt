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


// Sleep Timer Settings - Removed (now accessed via navigation in player screen)
// Sleep timer launches as bottom sheet from player or via Settings -> Sleep Timer
// No dedicated settings screen needed

// Crash Log History Settings Screen
@Composable
fun CrashLogHistorySettingsScreen(onBackClick: () -> Unit, appSettings: AppSettings) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val crashLogHistory by appSettings.crashLogHistory.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var showLogDetailDialog by remember { mutableStateOf(false) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var selectedLog: String? by remember { mutableStateOf(null) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_crash_log),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
            onBackClick()
        }
    ) { modifier ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {


            if (crashLogHistory.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.CheckCircle,
                                contentDescription = stringResource(R.string.crashloghistorysettingsscreen_no_crashes),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = context.getString(R.string.settings_no_crash_logs),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Crash logs section
                item {
                    val crashLogItems = crashLogHistory.map { entry ->
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("error", filled = true),
                            title = { Text(stringResource(R.string.crash_log_title, dateFormat.format(Date(entry.timestamp)))) },
                            description = {
                                Text(
                                    text = entry.log.lines().firstOrNull() ?: "No details available.",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedLog = entry.log
                                showLogDetailDialog = true
                            }
                        )
                    }

                    Material3SettingsGroup(
                        title = context.getString(R.string.crash_reports),
                        items = crashLogItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }

            // Action buttons
            item {
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("delete_sweep", filled = true),
                                title = context.getString(R.string.settings_clear_all_logs),
                                description = context.getString(R.string.settings_clear_all_logs_desc),
                                enabled = crashLogHistory.isNotEmpty(),
                                onClick = { showClearLogsConfirm = true }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    }

    // issue 169: clearing is irreversible, so confirm first and say when it is done.
    if (showClearLogsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogsConfirm = false },
            title = { Text(stringResource(R.string.msg_library_clear_crash_logs_title)) },
            text = { Text(stringResource(R.string.msg_library_clear_crash_logs_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLogsConfirm = false
                        appSettings.clearCrashLogHistory()
                        Log.i("CrashLogHistory", "Crash log history cleared")
                        RhythmMessages.success(context, R.string.msg_library_cleared)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.ui_clear))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearLogsConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Log detail dialog
    if (showLogDetailDialog) {
        AlertDialog(
            onDismissRequest = { showLogDetailDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(context.getString(R.string.settings_crash_log_details)) },
            text = {
                OutlinedTextField(
                    value = selectedLog ?: "No log details available.",
                    onValueChange = { /* Read-only */ },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.getString(R.string.crashlog_clip_label), selectedLog)
                        clipboard.setPrimaryClip(clip)
                        showLogDetailDialog = false
                        RhythmMessages.success(context, context.getString(R.string.settings_log_copied))
                    }
                ) {
                    Icon(
                        imageVector = RhythmIcons.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_copy_log))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogDetailDialog = false }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_close))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}