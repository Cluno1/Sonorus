/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.cluno1.sonorus.shared.presentation.screens.settings



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
import android.widget.Toast
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
import io.github.cluno1.sonorus.core.ProductCapabilities
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


@Composable
fun ExperimentalFeaturesScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    onNavigateToGoSettings: (() -> Unit)? = null
) {
    LabsSettingsScreen(
        onBackClick = onBackClick,
        onNavigateTo = onNavigateTo,
        onNavigateToGoSettings = onNavigateToGoSettings
    )
}

@Composable
fun LabsSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    onNavigateToGoSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val appMode by appSettings.appMode.collectAsState()
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    val enableAlbumEditing by appSettings.enableAlbumEditing.collectAsState()
    val lyricsEditorEnabled by appSettings.lyricsEditorEnabled.collectAsState()
    val scoreChorusLabEnabled by appSettings.scoreChorusLabEnabled.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    val forcePlayerCompactMode by appSettings.forcePlayerCompactMode.collectAsState()
    
    val updaterViewModel: AppUpdaterViewModel = viewModel()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()

    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_labs),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = buildList {
            add(
                SettingGroup(
                    title = context.getString(R.string.settings_metadata_editing),
                    items = listOf(
                        SettingItem(
                            MaterialSymbolIcon("edit"),
                            context.getString(R.string.settings_enable_album_editing),
                            context.getString(R.string.settings_enable_album_editing_desc),
                            toggleState = enableAlbumEditing,
                            onToggleChange = { appSettings.setEnableAlbumEditing(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("lyrics", filled = true),
                            context.getString(R.string.labs_lyrics_editor),
                            context.getString(R.string.labs_lyrics_editor_desc),
                            toggleState = lyricsEditorEnabled,
                            onToggleChange = { appSettings.setLyricsEditorEnabled(it) }
                        )
                    )
                )
            )

            add(
                SettingGroup(
                    title = context.getString(R.string.labs_music_score_experiments),
                    items = listOf(
                        SettingItem(
                            MaterialSymbolIcon("groups", filled = true),
                            context.getString(R.string.labs_score_chorus),
                            context.getString(R.string.labs_score_chorus_desc),
                            toggleState = scoreChorusLabEnabled,
                            onToggleChange = { appSettings.setScoreChorusLabEnabled(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("photo_library", filled = true),
                            context.getString(R.string.labs_client_images),
                            context.getString(R.string.labs_client_images_desc),
                            onClick = { onNavigateTo(SettingsRoutes.CLIENT_IMAGES) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("admin_panel_settings", filled = true),
                            context.getString(R.string.chorus_admin_title),
                            context.getString(R.string.labs_chorus_admin_desc),
                            onClick = { onNavigateTo(SettingsRoutes.CHORUS_ADMIN) }
                        )
                    )
                )
            )
            
            // Developer/Debugging features group
            add(
                SettingGroup(
                    title = context.getString(R.string.exp_developer_debugging),
                    items = buildList {
                        add(
                        SettingItem(
                            MaterialSymbolIcon("running_with_errors"),
                            context.getString(R.string.exp_track_error_checker),
                            context.getString(R.string.exp_track_error_checker_desc),
                            toggleState = appSettings.trackErrorCheckerEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setTrackErrorCheckerEnabled(it) }
                        ))
                        add(
                        SettingItem(
                            RhythmIcons.Code,
                            context.getString(R.string.exp_codec_monitoring),
                            context.getString(R.string.exp_codec_monitoring_desc),
                            toggleState = appSettings.codecMonitoringEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setCodecMonitoringEnabled(it) }
                        ))
                        add(
                        SettingItem(
                            RhythmIcons.Headphones,
                            context.getString(R.string.exp_audio_device_logging),
                            context.getString(R.string.exp_audio_device_logging_desc),
                            toggleState = appSettings.audioDeviceLoggingEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setAudioDeviceLoggingEnabled(it) }
                        ))
                        add(
                        SettingItem(
                            RhythmIcons.BugReport,
                            context.getString(R.string.exp_test_crash),
                            context.getString(R.string.exp_test_crash_desc),
                            onClick = { io.github.cluno1.sonorus.util.CrashReporter.testCrash() }
                        ))
                        add(
                        SettingItem(
                            MaterialSymbolIcon("smartphone"),
                            context.getString(R.string.exp_force_player_compact_mode),
                            context.getString(R.string.exp_force_player_compact_mode_desc),
                            toggleState = forcePlayerCompactMode,
                            onToggleChange = { appSettings.setForcePlayerCompactMode(it) }
                        ))
                        if (!ProductCapabilities.catalogOnly) {
                            add(
                                SettingItem(
                                    MaterialSymbolIcon("cloud_queue"),
                                    context.getString(R.string.exp_go_mode),
                                    context.getString(R.string.exp_go_mode_desc),
                                    toggleState = appMode == "STREAMING",
                                    onToggleChange = { enabled ->
                                        appSettings.setAppMode(if (enabled) "STREAMING" else "LOCAL")
                                    },
                                    onClick = { onNavigateToGoSettings?.invoke() }
                                )
                            )
                        }
                    }
                )
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            items(settingGroups, key = { "setting_${it.title}_${settingGroups.indexOf(it)}" }) { group ->
                Spacer(modifier = Modifier.height(24.dp))

                val materialItems = group.items.map { item ->
                    toMaterial3SettingsItem(context = context, item = item)
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }




            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.updates_experimental_coming),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                io.github.cluno1.sonorus.util.AppRestarter.restartApp(context)
            },
            onContinue = {
                showRestartDialog = false
            },
            message = restartDialogMessage
        )
    }

    // Show update bottomsheet - removed, now handled globally in LocalNavigation
}



fun getUpdateSourceLabel(context: Context, source: String): String {
    return when (source.lowercase()) {
        "github" -> context.getString(R.string.updates_source_github_label)
        "fdroid" -> context.getString(R.string.updates_source_fdroid_label)
        else -> context.getString(R.string.updates_source_installed_label)
    }
}



/**
 * Toggle card for individual decoration elements
 */
@Composable
fun DecorationToggleCard(
    title: String,
    description: String,
    icon: MaterialSymbolIcon,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon with background
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Toggle switch
            TunerAnimatedSwitch(
                checked = isEnabled,
                onCheckedChange = {
                    onToggle(it)
                }
            )
        }
    }
}



fun getFestivalDisplayName(festivalType: String): String {
    return when (festivalType) {
        "CHRISTMAS" -> "Christmas"
        "NEW_YEAR" -> "New Year"
        "NONE" -> "None"
        "CUSTOM" -> "Custom"
        else -> "Not selected"
    }
}
