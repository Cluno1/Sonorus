/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmGlassMenuItem
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmGlassMenu
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmSideToggleButton
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmSideIconButton
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmSideButtonDefaults
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmSideButtonColors
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.shared.data.model.LyricsContribution
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.nestedScroll

import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.network.CanvasArtwork
import io.github.cluno1.sonorus.shared.presentation.components.player.CanvasArtworkPlayer
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import androidx.compose.material3.ContainedLoadingIndicator
import io.github.cluno1.sonorus.shared.presentation.components.common.M3PlaceholderType
import io.github.cluno1.sonorus.shared.presentation.components.common.rememberExpressiveShapeFor
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressiveShapeTarget
import io.github.cluno1.sonorus.shared.presentation.components.common.ExpressivePlayerControlGroup
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmGroupedButton
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonWeighted
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonSize
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.util.HapticUtils
import io.github.cluno1.sonorus.util.HapticType
import io.github.cluno1.sonorus.util.M3ImageUtils
import io.github.cluno1.sonorus.util.LrcUtils
import io.github.cluno1.sonorus.util.SemanticLyrics
import io.github.cluno1.sonorus.util.LyricsTranslationMode
import io.github.cluno1.sonorus.util.lyricsHaveTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import io.github.cluno1.sonorus.R
import androidx.compose.ui.res.stringResource
import java.util.Locale
import io.github.cluno1.sonorus.util.windowScreenWidthDp
import io.github.cluno1.sonorus.util.windowScreenHeightDp
import coil.compose.AsyncImage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtworkPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import io.github.cluno1.sonorus.features.catalog.presentation.isTabletLyricsLayout

@Composable
fun FullScreenLyricsView(
    song: Song?,
    isPlaying: Boolean,
    currentTimeMs: Long,
    lyrics: LyricsData?,
    isLoadingLyrics: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onLyricsSeek: ((Long) -> Unit)?,
    onRetryLyrics: () -> Unit,
    onManualLyricsSearch: (() -> Unit)? = null,
    onClose: () -> Unit,
    onShowLyricsEditor: () -> Unit,
    onNavigateToLyricsSettings: () -> Unit,
    catalogLyricsLanguages: List<String> = emptyList(),
    selectedCatalogLyricsLanguage: String? = null,
    onCatalogLyricsLanguageSelect: (String) -> Unit = {},
    catalogLyricSourceImages: List<CatalogLyricSourceImage> = emptyList(),
    // issue 165: songbook numbers of the playing catalog Work, e.g. "ihope 114 · Heaven Song 20".
    catalogWorkNumbers: String? = null,
    // issue 116: tag row of the catalog Work being played (null when the song has no Work).
    catalogWorkHeader: (@Composable () -> Unit)? = null,
    // issue 124: "add to room" for the playing catalog Work; null for songs without a Work.
    onAddToRoom: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    canvasArtwork: CanvasArtwork? = null,
    canvasLoading: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val playerLyricsTextSize by appSettings.playerLyricsTextSize.collectAsState()
    val autoHideLyricsControls by appSettings.autoHideLyricsControls.collectAsState()
    val keepScreenOnLyrics by appSettings.keepScreenOnLyrics.collectAsState()
    val playerLyricsAlignment by appSettings.playerLyricsAlignment.collectAsState()
    val lyricsEditorEnabled by appSettings.lyricsEditorEnabled.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCatalogSourceImages by remember(song?.id) { mutableStateOf(false) }

    BackHandler(enabled = showCatalogSourceImages) {
        showCatalogSourceImages = false
    }

    LaunchedEffect(catalogLyricSourceImages) {
        if (catalogLyricSourceImages.isEmpty()) showCatalogSourceImages = false
    }

    fun showControls() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    fun touchDetected() {
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(controlsVisible, lastInteractionTime, autoHideLyricsControls) {
        if (autoHideLyricsControls && controlsVisible) {
            delay(5000L) // Auto hide after 5 seconds of inactivity
            controlsVisible = false
        }
    }

    // Keep screen awake while full screen lyrics is visible
    val activity = context as? android.app.Activity
    DisposableEffect(keepScreenOnLyrics) {
        if (keepScreenOnLyrics && activity != null) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val lyricsTextAlign = when (playerLyricsAlignment) {
        "START" -> TextAlign.Start
        "END" -> TextAlign.End
        else -> TextAlign.Center
    }

    // issue 164: same plain app-theme background as the Work lyrics page (no blurred artwork,
    // moving gradients or dim layer), following the current light/dark scheme and palette.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseBgColor = MaterialTheme.colorScheme.background
    val textPrimaryColor = MaterialTheme.colorScheme.onBackground
    val textSecondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val glassBgColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f)
    val glassBorderColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)

    // Global settings for translation and romanization
    val showTranslation by appSettings.showLyricsTranslation.collectAsState()
    val showRomanization by appSettings.showLyricsRomanization.collectAsState()
    val translationOnly by appSettings.lyricsTranslationOnly.collectAsState()
    // issue 163: 原 / 译 / 原译, shared with the player page through the same settings.
    val translationMode = LyricsTranslationMode.fromSettings(showTranslation, translationOnly)
    val hasLineTranslation by produceState(initialValue = false, lyrics) {
        value = withContext(Dispatchers.Default) { lyricsHaveTranslation(lyrics) }
    }

    // issue 163: the real-time sync adjustment dock is hidden for every song; the offset stays
    // wired into the lyric views at zero so their timing code is unchanged.
    val manualSyncOffsetMs = 0L

    val artworkScaleState by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ArtworkThumbnailScale"
    )

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                showControls()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
            .nestedScroll(nestedScrollConnection)
            .pointerInput(autoHideLyricsControls) {
                if (autoHideLyricsControls) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            touchDetected()
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (autoHideLyricsControls) {
                    showControls()
                }
            }
    ) {
        // 1. Background: the Box above paints the plain app-theme colour (issue 164).

        // 2. MAIN LAYOUT CONTAINER
        // 2. MAIN LAYOUT CONTAINER (Adaptive Tablet Split or Phone Stack)
        val isTablet = windowScreenWidthDp() >= 600
        // issue 117: only a real tablet (both window axes ≥ 600dp) gets the left-pane image cover;
        // a landscape phone keeps the original split where the images replace the lyrics.
        val sourceImagesCoverLeftPane = isTablet && isTabletLyricsLayout()

        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // LEFT SIDE (40% width) - Metadata, controls, offset adjuster
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(end = 24.dp),
                ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Close button aligned to top-left (no Now Playing text)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onClose()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.onboarding_dismiss),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Centered content Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Large Artwork
                        val artShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.SONG_ART)
                        Surface(
                            shape = artShape,
                            color = glassBgColor,
                            border = BorderStroke(1.dp, glassBorderColor),
                            modifier = Modifier
                                .size(240.dp)
                                .graphicsLayer {
                                    scaleX = artworkScaleState
                                    scaleY = artworkScaleState
                                }
                        ) {
                            M3ImageUtils.M3MediaImage(
                                data = song?.artworkUri,
                                contentDescription = stringResource(R.string.fullscreenlyricsview_cover_artwork),
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(16.dp),
                                type = M3PlaceholderType.TRACK,
                                name = song?.title,
                                expressiveShape = artShape
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Song Title & Artist
                        Text(
                            text = song?.title ?: stringResource(R.string.unknown_song),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (!catalogWorkNumbers.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = catalogWorkNumbers,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = song?.artist ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = textSecondaryColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (!sourceImagesCoverLeftPane && catalogWorkHeader != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            catalogWorkHeader()
                        }
                    }

                    // Glassmorphic control dock
                    AnimatedVisibility(
                        visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        ExpressivePlayerControlGroup(
                            isPlaying = isPlaying,
                            showSeekButtons = false,
                            onPrevious = onSkipPrevious,
                            onPlayPause = onPlayPause,
                            onNext = onSkipNext,
                            onSeekBack = {},
                            onSeekForward = {},
                            useGlassEffect = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // issue 117: the source images cover the whole left pane, player included; the player
                // stays composed underneath so playback is untouched, the opaque surface blocks touches.
                if (sourceImagesCoverLeftPane && showCatalogSourceImages) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = baseBgColor,
                    ) {
                        CatalogLyricSourceImagesView(
                            images = catalogLyricSourceImages,
                            shareTitle = song?.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                }

                // RIGHT SIDE (60% width) - Lyrics scrolling area and settings/editor floating actions
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                ) {
                // issue 117/165: on a real tablet the Work tags sit at the top of the lyrics pane (the
                // images cover the left pane); language and source-image buttons are in the side bar.
                if (sourceImagesCoverLeftPane && catalogWorkHeader != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        catalogWorkHeader()
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val hasLyrics = lyrics?.hasLyrics() == true && lyrics.isErrorMessage().not()

                    when {
                        showCatalogSourceImages && !sourceImagesCoverLeftPane -> {
                            CatalogLyricSourceImagesView(
                                images = catalogLyricSourceImages,
                                shareTitle = song?.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        isLoadingLyrics -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ContainedLoadingIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.fullscreenlyricsview_loading_synced_lyrics),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textSecondaryColor
                                    )
                                }
                            }
                        }

                        !hasLyrics -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.MusicNote,
                                        contentDescription = null,
                                        tint = textSecondaryColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = stringResource(R.string.fullscreenlyricsview_lyrics_arent_available_for),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = textPrimaryColor,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = onRetryLyrics,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(stringResource(R.string.fullscreenlyricsview_retry_fetching))
                                    }
                                    onManualLyricsSearch?.let { manualSearch ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        RhythmGroupedButton(size = RhythmButtonSize.Small) { // issue 175
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    manualSearch()
                                                },
                                                weight = 1f,
                                                isFirst = true,
                                                isLast = true,
                                                icon = RhythmIcons.Search,
                                                text = stringResource(R.string.device_manual_metadata_find_lyrics)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val appSettings = remember { AppSettings.getInstance(context) }
                            val translationAutoWord by appSettings.translationAutoWord.collectAsState()
                            val wordByWordLyrics = remember(lyrics, translationAutoWord) {
                                lyrics.getWordByWordLyricsOrNull() ?: run {
                                    if (translationAutoWord && lyrics.syncedLyrics != null) {
                                        try {
                                            val options = LrcUtils.LrcParserOptions(
                                                trim = true, multiLine = true, errorText = null, autoWordSync = true
                                            )
                                            val parsed = LrcUtils.parseLyrics(
                                                lyrics.syncedLyrics, audioMimeType = null,
                                                parserOptions = options, format = LrcUtils.LyricFormat.LRC
                                            )
                                            if (parsed is SemanticLyrics.SyncedLyrics) {
                                                LrcUtils.convertSemanticLyricsToWordByWord(parsed)
                                            } else null
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }
                            }

                            if (wordByWordLyrics != null) {
                                WordByWordLyricsView(
                                    wordByWordLyrics = wordByWordLyrics,
                                    currentPlaybackTime = currentTimeMs,
                                    syncOffset = manualSyncOffsetMs,
                                    modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                    onSeek = onLyricsSeek,
                                    lyricsSource = lyrics.source,
                                    contributions = lyrics.contributions,
                                    textSizeMultiplier = playerLyricsTextSize,
                                    textAlignment = lyricsTextAlign,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization,
                                    translationOnly = translationOnly
                                )
                            } else {
                                val lyricsText = remember(lyrics) { lyrics.getBestLyrics() ?: "" }
                                val likelySynced = remember(lyricsText) {
                                    Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{0,3})?]")
                                        .containsMatchIn(lyricsText)
                                }
                                if (likelySynced) {
                                    SyncedLyricsView(
                                        lyrics = lyricsText,
                                        currentPlaybackTime = currentTimeMs,
                                        syncOffset = manualSyncOffsetMs,
                                        modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                        onSeek = onLyricsSeek,
                                        showTranslation = showTranslation,
                                        showRomanization = showRomanization,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign,
                                        translationOnly = translationOnly
                                    )
                                } else {
                                    FullScreenPlainLyricsView(
                                        lyrics = lyricsText,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign,
                                        textColor = textPrimaryColor,
                                        sourceColor = textSecondaryColor,
                                        modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                    )
                                }
                            }
                        }
                    }

                    // issue 124: every floating action in one column at the bottom right — add to
                    // room, romanization/translation (non-catalog lyrics), lyrics editor, settings —
                    // the same corner the Work lyrics page uses.
                    LyricsFloatingActions(
                        visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                        languageMenu = {
                            LyricsLanguageSideButton(
                                catalogLanguages = catalogLyricsLanguages,
                                selectedCatalogLanguage = selectedCatalogLyricsLanguage,
                                onCatalogLanguageSelect = onCatalogLyricsLanguageSelect,
                                hasTranslation = hasLineTranslation,
                                translationMode = translationMode,
                                onTranslationModeSelect = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setLyricsTranslationMode(it)
                                },
                            )
                        },
                        showSourceImagesToggle = catalogLyricSourceImages.isNotEmpty(),
                        sourceImagesShown = showCatalogSourceImages,
                        onSourceImagesShownChange = { showCatalogSourceImages = it },
                        showRomanizationToggle = catalogLyricsLanguages.isEmpty(),
                        showRomanization = showRomanization,
                        onRomanizationChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setShowLyricsRomanization(it)
                        },
                        lyricsEditorEnabled = lyricsEditorEnabled,
                        onEditLyrics = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onShowLyricsEditor()
                        },
                        onLyricsSettings = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onNavigateToLyricsSettings()
                        },
                        onAddToRoom = onAddToRoom?.let { addToRoom ->
                            {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                addToRoom()
                            }
                        },
                    )
                }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                // A. TOP BAR: Horizontal Artwork, Metadata and close button (always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Expressive-shaped Cover Art
                    val artShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.SONG_ART)
                    Surface(
                        shape = artShape,
                        color = glassBgColor,
                        border = BorderStroke(1.dp, glassBorderColor),
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                scaleX = artworkScaleState
                                scaleY = artworkScaleState
                            }
                    ) {
                        M3ImageUtils.M3MediaImage(
                            data = song?.artworkUri,
                            contentDescription = stringResource(R.string.fullscreenlyricsview_cover_artwork),
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            type = M3PlaceholderType.TRACK,
                            name = song?.title,
                            expressiveShape = artShape
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // issue 165: row 1 is only cover, title / number / artist and close; the language
                    // menu and source-image toggle moved to the side bar so the title is never squeezed.
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song?.title ?: stringResource(R.string.unknown_song),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimaryColor,
                                letterSpacing = 0.1.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!catalogWorkNumbers.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = catalogWorkNumbers,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song?.artist ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = textSecondaryColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Close button: Material 3 FilledTonalIconButton
                    FilledTonalIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onClose()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.onboarding_dismiss),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // issue 116: tags of the catalog Work, shown and hidden with the other controls.
                if (catalogWorkHeader != null) {
                    AnimatedVisibility(visible = !autoHideLyricsControls || controlsVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            catalogWorkHeader()
                        }
                    }
                }

                // B. SCROLLING LYRICS AREA (with floating controls)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val hasLyrics = lyrics?.hasLyrics() == true && lyrics.isErrorMessage().not()

                    when {
                        showCatalogSourceImages -> {
                            CatalogLyricSourceImagesView(
                                images = catalogLyricSourceImages,
                                shareTitle = song?.title,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        isLoadingLyrics -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ContainedLoadingIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.fullscreenlyricsview_loading_synced_lyrics),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textSecondaryColor
                                    )
                                }
                            }
                        }

                        !hasLyrics -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.MusicNote,
                                        contentDescription = null,
                                        tint = textSecondaryColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = stringResource(R.string.fullscreenlyricsview_lyrics_arent_available_for),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = textPrimaryColor,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RhythmGroupedButton(
                                        size = RhythmButtonSize.Small
                                    ) {
                                        RhythmButtonWeighted(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onRetryLyrics()
                                            },
                                            weight = 1f,
                                            isFirst = true,
                                            isLast = false,
                                            icon = RhythmIcons.Refresh,
                                            text = stringResource(R.string.updates_retry)
                                        )
                                        if (lyricsEditorEnabled) {
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onShowLyricsEditor()
                                                },
                                                weight = 1f,
                                                isFirst = false,
                                                isLast = false,
                                                icon = RhythmIcons.Player.Lyrics,
                                                text = stringResource(R.string.lyrics_editor_short)
                                            )
                                        }
                                        RhythmButtonWeighted(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                onNavigateToLyricsSettings()
                                            },
                                            weight = 1f,
                                            isFirst = false,
                                            isLast = true,
                                            icon = MaterialSymbolIcon("settings", filled = true),
                                            text = stringResource(R.string.lyrics_settings_short)
                                        )
                                    }
                                    onManualLyricsSearch?.let { manualSearch ->
                                        Spacer(modifier = Modifier.height(10.dp))
                                        RhythmGroupedButton(size = RhythmButtonSize.Small) { // issue 175: Editor style
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    manualSearch()
                                                },
                                                weight = 1f,
                                                isFirst = true,
                                                isLast = true,
                                                icon = RhythmIcons.Search,
                                                text = stringResource(R.string.device_manual_metadata_find_lyrics)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val appSettingsInst = remember { AppSettings.getInstance(context) }
                            val translationAutoWord2 by appSettingsInst.translationAutoWord.collectAsState()
                            val wordByWordLyrics = remember(lyrics, translationAutoWord2) {
                                lyrics.getWordByWordLyricsOrNull() ?: run {
                                    if (translationAutoWord2 && lyrics.syncedLyrics != null) {
                                        try {
                                            val options = LrcUtils.LrcParserOptions(
                                                trim = true, multiLine = true, errorText = null, autoWordSync = true
                                            )
                                            val parsed = LrcUtils.parseLyrics(
                                                lyrics.syncedLyrics, audioMimeType = null,
                                                parserOptions = options, format = LrcUtils.LyricFormat.LRC
                                            )
                                            if (parsed is SemanticLyrics.SyncedLyrics) {
                                                LrcUtils.convertSemanticLyricsToWordByWord(parsed)
                                            } else null
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } else null
                                }
                            }

                            if (wordByWordLyrics != null) {
                                WordByWordLyricsView(
                                    wordByWordLyrics = wordByWordLyrics,
                                    currentPlaybackTime = currentTimeMs,
                                    syncOffset = manualSyncOffsetMs,
                                    modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                    onSeek = onLyricsSeek,
                                    lyricsSource = lyrics.source,
                                    contributions = lyrics.contributions,
                                    textSizeMultiplier = playerLyricsTextSize,
                                    textAlignment = lyricsTextAlign,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization,
                                    translationOnly = translationOnly
                                )
                            } else {
                                val lyricsText = remember(lyrics) { lyrics.getBestLyrics() ?: "" }
                                val likelySynced = remember(lyricsText) {
                                    Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{0,3})?]")
                                        .containsMatchIn(lyricsText)
                                }
                                if (likelySynced) {
                                    SyncedLyricsView(
                                        lyrics = lyricsText,
                                        currentPlaybackTime = currentTimeMs,
                                        syncOffset = manualSyncOffsetMs,
                                        modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                        onSeek = onLyricsSeek,
                                        showTranslation = showTranslation,
                                        showRomanization = showRomanization,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign,
                                        translationOnly = translationOnly
                                    )
                                } else {
                                    FullScreenPlainLyricsView(
                                        lyrics = lyricsText,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign,
                                        textColor = textPrimaryColor,
                                        sourceColor = textSecondaryColor,
                                        modifier = Modifier.fillMaxSize().padding(end = LyricsSideBarWidth),
                                    )
                                }
                            }
                        }
                    }

                    // issue 124: every floating action in one column at the bottom right — add to
                    // room, romanization/translation (non-catalog lyrics), lyrics editor, settings —
                    // the same corner the Work lyrics page uses.
                    LyricsFloatingActions(
                        visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                        languageMenu = {
                            LyricsLanguageSideButton(
                                catalogLanguages = catalogLyricsLanguages,
                                selectedCatalogLanguage = selectedCatalogLyricsLanguage,
                                onCatalogLanguageSelect = onCatalogLyricsLanguageSelect,
                                hasTranslation = hasLineTranslation,
                                translationMode = translationMode,
                                onTranslationModeSelect = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setLyricsTranslationMode(it)
                                },
                            )
                        },
                        showSourceImagesToggle = catalogLyricSourceImages.isNotEmpty(),
                        sourceImagesShown = showCatalogSourceImages,
                        onSourceImagesShownChange = { showCatalogSourceImages = it },
                        showRomanizationToggle = catalogLyricsLanguages.isEmpty(),
                        showRomanization = showRomanization,
                        onRomanizationChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setShowLyricsRomanization(it)
                        },
                        lyricsEditorEnabled = lyricsEditorEnabled,
                        onEditLyrics = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onShowLyricsEditor()
                        },
                        onLyricsSettings = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onNavigateToLyricsSettings()
                        },
                        onAddToRoom = onAddToRoom?.let { addToRoom ->
                            {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                addToRoom()
                            }
                        },
                    )
                }

                // D. GLASSMORPHIC CONTROL DOCK: Integrated with the official material ExpressivePlayerControlGroup
                AnimatedVisibility(
                    visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        ) {
                            ExpressivePlayerControlGroup(
                                isPlaying = isPlaying,
                                showSeekButtons = false,
                                onPrevious = onSkipPrevious,
                                onPlayPause = onPlayPause,
                                onNext = onSkipNext,
                                onSeekBack = {},
                                onSeekForward = {},
                                useGlassEffect = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun FullScreenPlainLyricsView(
    lyrics: String,
    lyricsSource: String?,
    contributions: List<LyricsContribution>,
    textSizeMultiplier: Float,
    textAlignment: TextAlign,
    textColor: Color,
    sourceColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = when (textAlignment) {
            TextAlign.Start -> Alignment.Start
            TextAlign.End -> Alignment.End
            else -> Alignment.CenterHorizontally
        },
    ) {
        val visibleLyrics = remember(lyrics) {
            io.github.cluno1.sonorus.util.LyricsContributionAttribution.visibleLyrics(lyrics, "plain")
        }
        Text(
            text = visibleLyrics,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * textSizeMultiplier,
                lineHeight = MaterialTheme.typography.titleLarge.lineHeight * 1.5f * textSizeMultiplier,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
            ),
            color = textColor,
            textAlign = textAlignment,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!lyricsSource.isNullOrBlank() || contributions.isNotEmpty()) {
            LyricsAttributionFooter(
                lyricsSource = lyricsSource,
                contributions = contributions,
                color = sourceColor.copy(alpha = 0.7f),
                textStyle = MaterialTheme.typography.labelMedium,
                textAlign = textAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            )
        }
    }
}


/** issue 165: width kept free on the right of the lyrics for the side bar. */
private val LyricsSideBarWidth = 48.dp

/**
 * issue 124/163/165: the side bar of the full-screen lyrics, one column at the bottom right (the
 * corner the Work lyrics page also uses). Top to bottom: lyrics language (Catalog language or
 * 原/译/原译), source images (catalog), add to room (catalog Work), romanization (non-catalog
 * lyrics), lyrics editor (when enabled), settings. It scrolls when the pane is too short.
 * issue 170: every button is the shared [RhythmSideIconButton] (40dp glass circle).
 */
@Composable
private fun BoxScope.LyricsFloatingActions(
    visible: Boolean,
    languageMenu: @Composable () -> Unit,
    showSourceImagesToggle: Boolean,
    sourceImagesShown: Boolean,
    onSourceImagesShownChange: (Boolean) -> Unit,
    showRomanizationToggle: Boolean,
    showRomanization: Boolean,
    onRomanizationChange: (Boolean) -> Unit,
    lyricsEditorEnabled: Boolean,
    onEditLyrics: () -> Unit,
    onLyricsSettings: () -> Unit,
    onAddToRoom: (() -> Unit)?,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(top = 8.dp, bottom = 16.dp, end = 4.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RhythmSideButtonDefaults.Spacing),
            horizontalAlignment = Alignment.End,
        ) {
            languageMenu()
            CatalogLyricSourceToggleButton(
                visible = showSourceImagesToggle,
                checked = sourceImagesShown,
                onCheckedChange = onSourceImagesShownChange,
            )
            onAddToRoom?.let { addToRoom ->
                RhythmSideIconButton(
                    icon = MaterialSymbolIcon("playlist_add"),
                    contentDescription = stringResource(R.string.rooms_add_to_room),
                    onClick = addToRoom,
                )
            }
            if (showRomanizationToggle) {
                RhythmSideToggleButton(
                    icon = MaterialSymbolIcon("translate"),
                    contentDescription = stringResource(R.string.fullscreenlyricsview_romanization),
                    checked = showRomanization,
                    onCheckedChange = onRomanizationChange,
                )
            }
            if (lyricsEditorEnabled) {
                RhythmSideIconButton(
                    icon = MaterialSymbolIcon("edit"),
                    contentDescription = stringResource(R.string.edit_lyrics),
                    onClick = onEditLyrics,
                )
            }
            RhythmSideIconButton(
                icon = MaterialSymbolIcon("settings"),
                contentDescription = stringResource(R.string.lyrics_settings),
                onClick = onLyricsSettings,
            )
        }
    }
}

@Composable
internal fun CatalogLyricSourceToggleButton(
    visible: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
) {
    if (!visible) return
    RhythmSideToggleButton(
        icon = MaterialSymbolIcon(if (checked) "lyrics" else "image"),
        contentDescription = stringResource(
            if (checked) R.string.lyrics_show_text
            else R.string.lyrics_show_source_images,
        ),
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = colors,
    )
}

// issue 123: CatalogLyricSourceImagesView (bounded canvas + share) lives in CatalogLyricSourceCanvas.kt.

/**
 * issue 163/165/170: the one lyrics language entry of the player, the full-screen lyrics, the Work
 * lyrics page and the score's lyrics pane. Catalog songs / Works with several effective lyrics
 * languages pick a language (the backend already resolved Song → preferred Score → Work per
 * language, so no source choice is offered). Other songs whose lyrics carry line translations pick
 * 原/译/原译. What is shown is decided by [lyricsLanguageButtonKind]; never both menus at once.
 *
 * issue 170: the button is only the translate icon (no short label, no arrow); the current choice
 * is the check in the glass menu and is read out by the content description.
 */
@Composable
internal fun LyricsLanguageSideButton(
    catalogLanguages: List<String>,
    selectedCatalogLanguage: String?,
    onCatalogLanguageSelect: (String) -> Unit,
    hasTranslation: Boolean,
    translationMode: LyricsTranslationMode,
    onTranslationModeSelect: (LyricsTranslationMode) -> Unit,
    modifier: Modifier = Modifier,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
) {
    val kind = lyricsLanguageButtonKind(catalogLanguages.size, hasTranslation)
    if (kind == LyricsLanguageButtonKind.NONE) return
    var expanded by remember { mutableStateOf(false) }
    val menuName = stringResource(
        if (kind == LyricsLanguageButtonKind.CATALOG) R.string.cd_change_language
        else R.string.lyrics_translation_mode_menu
    )
    val fullLabels = mapOf(
        LyricsTranslationMode.ORIGINAL to stringResource(R.string.lyrics_translation_mode_original),
        LyricsTranslationMode.TRANSLATION to stringResource(R.string.lyrics_translation_mode_translation),
        LyricsTranslationMode.BOTH to stringResource(R.string.lyrics_translation_mode_both),
    )
    val selectedLanguage = catalogLanguages.firstOrNull { it.equals(selectedCatalogLanguage, ignoreCase = true) }
        ?: catalogLanguages.firstOrNull()
    val currentLabel = if (kind == LyricsLanguageButtonKind.CATALOG) {
        selectedLanguage?.let(::catalogLyricsLanguageLabel).orEmpty()
    } else {
        fullLabels.getValue(translationMode)
    }
    Box(modifier = modifier) {
        RhythmSideIconButton(
            icon = MaterialSymbolIcon("translate"),
            contentDescription = stringResource(R.string.lyrics_language_button_cd, menuName, currentLabel),
            onClick = { expanded = true },
            colors = colors,
        )
        RhythmGlassMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            colors = colors,
        ) {
            if (kind == LyricsLanguageButtonKind.CATALOG) {
                catalogLanguages.forEach { language ->
                    RhythmGlassMenuItem(
                        label = catalogLyricsLanguageLabel(language),
                        tag = language,
                        selected = language == selectedLanguage,
                        onClick = {
                            expanded = false
                            onCatalogLanguageSelect(language)
                        },
                        colors = colors,
                    )
                }
            } else {
                LyricsTranslationMode.entries.forEach { mode ->
                    RhythmGlassMenuItem(
                        label = fullLabels.getValue(mode),
                        selected = mode == translationMode,
                        onClick = {
                            expanded = false
                            onTranslationModeSelect(mode)
                        },
                        colors = colors,
                    )
                }
            }
        }
    }
}

private fun catalogLyricsLanguageLabel(languageTag: String): String {
    val locale = Locale.forLanguageTag(languageTag)
    return locale.getDisplayName(Locale.getDefault()).takeIf(String::isNotBlank) ?: languageTag
}
