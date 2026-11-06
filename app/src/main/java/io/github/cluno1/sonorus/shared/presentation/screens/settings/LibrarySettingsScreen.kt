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
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.shared.data.model.ArtistArtworkSource
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.ArtistArtworkSourceBottomSheet
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
import io.github.cluno1.sonorus.features.local.data.device.DeviceFolderAccess
import io.github.cluno1.sonorus.features.local.data.device.DeviceMetadataRepository
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
fun LibrarySettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val deviceFolderAccess = remember { DeviceFolderAccess(context) }
    val deviceMetadataRepository = remember { DeviceMetadataRepository(context) }
    var deviceFolderRoots by remember { mutableStateOf(deviceFolderAccess.roots().toList()) }
    val deviceFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { deviceFolderAccess.add(uri) }
                .onSuccess {
                    deviceFolderRoots = deviceFolderAccess.roots().toList()
                    RhythmMessages.success(context, R.string.settings_device_folder_added)
                }
                .onFailure { RhythmMessages.error(context, context.getString(R.string.settings_device_folder_failed), long = true) }
        }
    }

    val libraryCombineDiscs by appSettings.libraryCombineDiscs.collectAsState()
    val showLibraryBottomBarAlways by appSettings.showLibraryBottomBarAlways.collectAsState()
    val preferSongArtwork by appSettings.preferSongArtwork.collectAsState()
    val losslessArtwork by appSettings.losslessArtwork.collectAsState()
    val albumBottomSheetGradientBlur by appSettings.albumBottomSheetGradientBlur.collectAsState()
    val albumHideAbout by appSettings.albumHideAbout.collectAsState()
    val lyricallyApiEnabled by appSettings.lyricallyApiEnabled.collectAsState()
    val autoFetchArtwork by appSettings.autoFetchArtwork.collectAsState()
    val artistArtworkSource by appSettings.artistArtworkSource.collectAsState()
    val devicePublicMetadataEnabled by appSettings.devicePublicMetadataEnabled.collectAsState()

    var showLibraryTabOrderBottomSheet by remember { mutableStateOf(false) }
    var showArtistArtworkSourceBottomSheet by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartRequiresArtworkRescan by remember { mutableStateOf(false) }
    var restartDialogMessage by remember {
        mutableStateOf(context.getString(R.string.settings_song_artwork_restart_required))
    }

    val artistArtworkSourceSubtitle = when (artistArtworkSource) {
        ArtistArtworkSource.PREFER_LOCAL_THEN_API -> stringResource(R.string.settings_artist_artwork_source_prefer_local)
        ArtistArtworkSource.LOCAL_ONLY -> stringResource(R.string.settings_artist_artwork_source_local_only)
        ArtistArtworkSource.API_ONLY -> stringResource(R.string.settings_artist_artwork_source_api_only)
        ArtistArtworkSource.DISABLED -> stringResource(R.string.settings_artist_artwork_source_disabled)
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_library_settings),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = context.getString(R.string.settings_library_group_organization),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("reorder"),
                        context.getString(R.string.settings_library_tab_order),
                        context.getString(R.string.settings_library_tab_order_desc),
                        onClick = { showLibraryTabOrderBottomSheet = true }
                    ),
                    SettingItem(
                        RhythmIcons.Album,
                        context.getString(R.string.settings_library_combine_discs),
                        context.getString(R.string.settings_library_combine_discs_desc),
                        toggleState = libraryCombineDiscs,
                        onToggleChange = { appSettings.setLibraryCombineDiscs(it) }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("view_agenda"),
                        context.getString(R.string.settings_show_library_bottom_bar_always),
                        context.getString(R.string.settings_show_library_bottom_bar_always_desc),
                        toggleState = showLibraryBottomBarAlways,
                        onToggleChange = { appSettings.setShowLibraryBottomBarAlways(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_device_metadata_group),
                items = buildList {
                    add(SettingItem(
                        MaterialSymbolIcon("public"),
                        context.getString(R.string.settings_device_public_metadata),
                        context.getString(R.string.settings_device_public_metadata_privacy),
                        toggleState = devicePublicMetadataEnabled,
                        onToggleChange = appSettings::setDevicePublicMetadataEnabled
                    ))
                    add(SettingItem(
                        MaterialSymbolIcon("create_new_folder"),
                        context.getString(R.string.settings_device_metadata_add_folder),
                        context.getString(R.string.settings_device_metadata_add_folder_desc, deviceFolderRoots.size),
                        onClick = { deviceFolderLauncher.launch(null) }
                    ))
                    deviceFolderRoots.forEach { root ->
                        add(SettingItem(
                            MaterialSymbolIcon("folder"),
                            root.lastPathSegment ?: root.toString(),
                            context.getString(R.string.settings_device_metadata_remove_folder_desc),
                            onClick = {
                                deviceFolderAccess.remove(root)
                                deviceFolderRoots = deviceFolderAccess.roots().toList()
                            }
                        ))
                    }
                    if (deviceFolderRoots.isNotEmpty()) add(SettingItem(
                        MaterialSymbolIcon("folder_off"),
                        context.getString(R.string.settings_device_metadata_clear_folders),
                        context.getString(R.string.settings_device_metadata_clear_folders_desc),
                        onClick = {
                            deviceFolderAccess.clear()
                            deviceFolderRoots = emptyList()
                            RhythmMessages.success(context, R.string.settings_device_folders_cleared)
                        }
                    ))
                    add(SettingItem(
                        MaterialSymbolIcon("delete_sweep"),
                        context.getString(R.string.settings_device_metadata_clear_cache),
                        context.getString(R.string.settings_device_metadata_clear_cache_desc),
                        onClick = {
                            scope.launch {
                                try {
                                    deviceMetadataRepository.clearAllCachedMetadata()
                                    appSettings.requestFullMediaRescanOnNextLaunch("device_metadata_cache_cleared")
                                    Log.i("LibrarySettings", "Device metadata cache cleared")
                                    RhythmMessages.success(context, R.string.settings_device_metadata_cache_cleared)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // issue 169: this used to fail silently (or crash the scope).
                                    Log.w("LibrarySettings", "Could not clear device metadata cache", e)
                                    RhythmMessages.failure(context, R.string.msg_library_clear_failed, e)
                                }
                            }
                        }
                    ))
                }
            ),
            SettingGroup(
                title = context.getString(R.string.settings_library_group_artwork),
                items = buildList {
                    add(
                        SettingItem(
                            RhythmIcons.Album,
                            context.getString(R.string.settings_ignore_mediastore_covers),
                            context.getString(R.string.settings_ignore_mediastore_covers_desc),
                            toggleState = preferSongArtwork,
                            onToggleChange = {
                                if (it != preferSongArtwork) {
                                    appSettings.setPreferSongArtwork(it)
                                    restartRequiresArtworkRescan = true
                                    restartDialogMessage = context.getString(R.string.settings_song_artwork_restart_required)
                                    showRestartDialog = true
                                }
                            }
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.MusicNote,
                            context.getString(R.string.settings_lossless_artwork),
                            context.getString(R.string.settings_lossless_artwork_desc),
                            toggleState = losslessArtwork,
                            onToggleChange = {
                                if (it != losslessArtwork) {
                                    appSettings.setLosslessArtwork(it)
                                    restartRequiresArtworkRescan = true
                                    restartDialogMessage = context.getString(R.string.settings_song_artwork_restart_required)
                                    showRestartDialog = true
                                }
                            }
                        )
                    )
                    add(
                        SettingItem(
                            MaterialSymbolIcon("lens_blur"),
                            context.getString(R.string.settings_album_bottom_sheet_gradient_blur),
                            context.getString(R.string.settings_album_bottom_sheet_gradient_blur_desc),
                            toggleState = albumBottomSheetGradientBlur,
                            onToggleChange = { appSettings.setAlbumBottomSheetGradientBlur(it) }
                        )
                    )
                    add(
                        SettingItem(
                            MaterialSymbolIcon("info"),
                            context.getString(R.string.settings_album_hide_about),
                            context.getString(R.string.settings_album_hide_about_desc),
                            toggleState = albumHideAbout,
                            onToggleChange = { appSettings.setAlbumHideAbout(it) }
                        )
                    )
                    if (ProductCapabilities.thirdPartyMusicServices || ProductCapabilities.devicePublicMetadata) {
                        add(
                            SettingItem(
                                icon = MaterialSymbolIcon("cloud_download"),
                                title = context.getString(R.string.librarysettingsscreen_autofetch_artwork),
                                description = context.getString(R.string.library_auto_fetch_artwork_desc),
                                toggleState = autoFetchArtwork,
                                onToggleChange = { enabled -> appSettings.setAutoFetchArtwork(enabled) }
                            )
                        )
                    }
                    if (ProductCapabilities.thirdPartyMusicServices) {
                        add(
                            SettingItem(
                                icon = MaterialSymbolIcon("portrait"),
                                title = context.getString(R.string.settings_artist_artwork_source),
                                description = artistArtworkSourceSubtitle,
                                onClick = { showArtistArtworkSourceBottomSheet = true }
                            )
                        )
                    }
                }
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            items(settingGroups, key = { "libsettings_${it.title}" }) { group ->
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
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showLibraryTabOrderBottomSheet) {
        LibraryTabOrderBottomSheet(
            onDismiss = { showLibraryTabOrderBottomSheet = false },
            appSettings = appSettings,
            haptics = haptics
        )
    }

    if (showArtistArtworkSourceBottomSheet) {
        ArtistArtworkSourceBottomSheet(
            onDismiss = { showArtistArtworkSourceBottomSheet = false },
            appSettings = appSettings
        )
    }

    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = {
                showRestartDialog = false
                restartRequiresArtworkRescan = false
            },
            onRestart = {
                val shouldRefreshLibrary = restartRequiresArtworkRescan
                showRestartDialog = false
                restartRequiresArtworkRescan = false
                scope.launch {
                    if (shouldRefreshLibrary) {
                        try {
                            io.github.cluno1.sonorus.util.CacheManager.clearAllCache(context, null)
                            appSettings.requestFullMediaRescanOnNextLaunch(reason = "library_artwork_settings_restart")
                        } catch (e: Exception) {
                            Log.e("CacheManagement", "Error clearing cache before artwork settings restart", e)
                        }
                    }
                    io.github.cluno1.sonorus.util.AppRestarter.restartApp(context)
                }
            },
            onContinue = {
                showRestartDialog = false
                restartRequiresArtworkRescan = false
            },
            message = restartDialogMessage
        )
    }
}
