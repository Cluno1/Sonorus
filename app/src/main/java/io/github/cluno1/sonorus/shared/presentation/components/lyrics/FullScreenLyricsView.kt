/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.cluno1.sonorus.shared.presentation.components.lyrics

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
    // issue 116: tag row of the catalog Work being played (null when the song has no Work).
    catalogWorkHeader: (@Composable () -> Unit)? = null,
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
    val showLyricsBackgroundArtwork by appSettings.showLyricsBackgroundArtwork.collectAsState()
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

    // Detect light or dark theme based on the app's theme background luminance
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseBgColor = if (isDark) Color.Black else Color.White
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.65f)
    val glassBgColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f)
    val glassBorderColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)

    // Global settings for translation and romanization
    val showTranslation by appSettings.showLyricsTranslation.collectAsState()
    val showRomanization by appSettings.showLyricsRomanization.collectAsState()
    
    // Determine if lyrics have timestamps (synced LRC or word-by-word JSON)
    val hasSyncedLyrics = remember(lyrics) {
        lyrics?.syncedLyrics?.isNotBlank() == true || lyrics?.getWordByWordLyricsOrNull() != null
    }

    // Local manual sync offset in milliseconds (real-time offset tuning!)
    var manualSyncOffsetMs by remember { mutableLongStateOf(0L) }

    // Infinite transition for organic background morphing
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundAura")
    
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1Scale"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb2Scale"
    )

    val translationX1 by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1X"
    )

    val translationY1 by infiniteTransition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Orb1Y"
    )

    // Beautiful slow rotation for a premium rotating cosmic aura background
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(38000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CosmicRotation"
    )

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
        // 1. DYNAMIC BACKGROUND: Blurred scaled album art with animated moving orbs (if enabled)
        if (showLyricsBackgroundArtwork) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Blurred base cover art + canvas player + moving gradient auras (drawn underneath)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp)
                        .alpha(0.68f)
                ) {
                    // Blurred base cover art
                    M3ImageUtils.M3MediaImage(
                        data = song?.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                        type = M3PlaceholderType.TRACK,
                        name = song?.title,
                        expressiveShape = RoundedCornerShape(0.dp)
                    )

                    // Animated canvas player (blurred along with backdrop)
                    if (canvasArtwork?.preferredAnimationUrl != null) {
                        CanvasArtworkPlayer(
                            primaryUrl = canvasArtwork.animated,
                            fallbackUrl = canvasArtwork.videoUrl,
                            isPlaying = isPlaying,
                            alwaysPlay = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Dynamic Gradient Overlay 1 (Golden-accented Warm Aura)
                    Box(
                        modifier = Modifier
                            .size(340.dp)
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                translationX = translationX1
                                translationY = translationY1
                                scaleX = pulseScale1
                                scaleY = pulseScale1
                                rotationZ = rotationAngle
                            }
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Dynamic Gradient Overlay 2 (Cooler Toned Aurora Accent)
                    Box(
                        modifier = Modifier
                            .size(420.dp)
                            .align(Alignment.BottomEnd)
                            .graphicsLayer {
                                translationX = -translationX1 * 0.8f
                                translationY = -translationY1 * 0.9f
                                scaleX = pulseScale2
                                scaleY = pulseScale2
                                rotationZ = -rotationAngle * 1.2f
                            }
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // Dynamic dim layer to boost readability of text overlay based on theme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.52f),
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.72f),
                                    Color.White.copy(alpha = 0.52f),
                                    Color.White.copy(alpha = 0.78f)
                                )
                            }
                        )
                    )
            )
        }

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
                        if (!sourceImagesCoverLeftPane) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatalogLyricsLanguageMenu(
                                languages = catalogLyricsLanguages,
                                selectedLanguage = selectedCatalogLyricsLanguage,
                                onLanguageSelect = onCatalogLyricsLanguageSelect,
                            )
                            CatalogLyricSourceToggleButton(
                                visible = catalogLyricSourceImages.isNotEmpty(),
                                checked = showCatalogSourceImages,
                                onCheckedChange = { showCatalogSourceImages = it },
                            )
                        }
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
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

                    // Sync tuner dock (only visible for synced lyrics and when controls are visible)
                    AnimatedVisibility(
                        visible = (isLoadingLyrics || hasSyncedLyrics) && (!autoHideLyricsControls || controlsVisible),
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = glassBgColor,
                            border = BorderStroke(1.dp, glassBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.fullscreenlyricsview_realtime_sync_adjustment),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondaryColor
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Nudge Backwards
                                    val nudgeBackInteractionSource = remember { MutableInteractionSource() }
                                    val nudgeBackPressed by nudgeBackInteractionSource.collectIsPressedAsState()
                                    val nudgeBackScale by animateFloatAsState(
                                        targetValue = if (nudgeBackPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "tablet_nudge_back_bounce"
                                    )
                                    Surface(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            manualSyncOffsetMs -= 500L
                                        },
                                        shape = CircleShape,
                                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer {
                                                scaleX = nudgeBackScale
                                                scaleY = nudgeBackScale
                                            },
                                        interactionSource = nudgeBackInteractionSource
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("chevron_left"),
                                                contentDescription = stringResource(R.string.lyrics_nudge_backward),
                                                tint = textPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Display Current Offset
                                    val offsetSeconds = manualSyncOffsetMs / 1000f
                                    Text(
                                        text = String.format(Locale.ROOT, "%+.1fs", offsetSeconds),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )

                                    // Nudge Forwards
                                    val nudgeForwardInteractionSource = remember { MutableInteractionSource() }
                                    val nudgeForwardPressed by nudgeForwardInteractionSource.collectIsPressedAsState()
                                    val nudgeForwardScale by animateFloatAsState(
                                        targetValue = if (nudgeForwardPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "tablet_nudge_forward_bounce"
                                    )
                                    Surface(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            manualSyncOffsetMs += 500L
                                        },
                                        shape = CircleShape,
                                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer {
                                                scaleX = nudgeForwardScale
                                                scaleY = nudgeForwardScale
                                            },
                                        interactionSource = nudgeForwardInteractionSource
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("chevron_right"),
                                                contentDescription = stringResource(R.string.lyrics_nudge_forward),
                                                tint = textPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Reset Button
                                    if (manualSyncOffsetMs != 0L) {
                                        Icon(
                                            imageVector = RhythmIcons.Restore,
                                            contentDescription = stringResource(R.string.fullscreenlyricsview_reset_offset),
                                            tint = textSecondaryColor,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(20.dp)
                                                .bouncyClickable {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    manualSyncOffsetMs = 0L
                                                }
                                        )
                                    }
                                }
                            }
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
                // issue 117: on a real tablet the language menu and the source-image toggle sit at the
                // top right of the lyrics pane, because the images now cover the left pane instead.
                if (sourceImagesCoverLeftPane) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (catalogWorkHeader != null) {
                            Box(modifier = Modifier.weight(1f)) { catalogWorkHeader() }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        CatalogLyricsLanguageMenu(
                            languages = catalogLyricsLanguages,
                            selectedLanguage = selectedCatalogLyricsLanguage,
                            onLanguageSelect = onCatalogLyricsLanguageSelect,
                        )
                        CatalogLyricSourceToggleButton(
                            visible = catalogLyricSourceImages.isNotEmpty(),
                            checked = showCatalogSourceImages,
                            onCheckedChange = { showCatalogSourceImages = it },
                        )
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
                                        OutlinedButton(onClick = manualSearch) {
                                            Icon(
                                                icon = RhythmIcons.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.device_manual_metadata_find_lyrics))
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
                                    modifier = Modifier.fillMaxSize(),
                                    onSeek = onLyricsSeek,
                                    lyricsSource = lyrics.source,
                                    contributions = lyrics.contributions,
                                    textSizeMultiplier = playerLyricsTextSize,
                                    textAlignment = lyricsTextAlign,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization
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
                                        modifier = Modifier.fillMaxSize(),
                                        onSeek = onLyricsSeek,
                                        showTranslation = showTranslation,
                                        showRomanization = showRomanization,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign
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
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    // Floating Romanization and Translation Stack (on the right)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = catalogLyricsLanguages.isEmpty() &&
                            (isLoadingLyrics || !autoHideLyricsControls || controlsVisible),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 4.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quick Toggle Romanization
                            FilledTonalIconToggleButton(
                                checked = showRomanization,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setShowLyricsRomanization(it)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("translate"),
                                    contentDescription = stringResource(R.string.fullscreenlyricsview_romanization),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Quick Toggle Translation
                            FilledTonalIconToggleButton(
                                checked = showTranslation,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setShowLyricsTranslation(it)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("subtitles"),
                                    contentDescription = stringResource(R.string.fullscreenlyricsview_translation),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Floating Lyrics Editor and Lyrics Settings Stack (on the left)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 16.dp, start = 4.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (lyricsEditorEnabled) {
                                // Quick Open Lyrics Editor
                                FilledTonalIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        onShowLyricsEditor()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("edit"),
                                        contentDescription = stringResource(R.string.edit_lyrics),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Quick Open Lyrics Settings
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    onNavigateToLyricsSettings()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("settings"),
                                    contentDescription = stringResource(R.string.lyrics_settings),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
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

                    // Song details (Title + Artist)
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

                    CatalogLyricsLanguageMenu(
                        languages = catalogLyricsLanguages,
                        selectedLanguage = selectedCatalogLyricsLanguage,
                        onLanguageSelect = onCatalogLyricsLanguageSelect,
                    )

                    if (catalogLyricsLanguages.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    CatalogLyricSourceToggleButton(
                        visible = catalogLyricSourceImages.isNotEmpty(),
                        checked = showCatalogSourceImages,
                        onCheckedChange = { showCatalogSourceImages = it },
                    )

                    if (catalogLyricSourceImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

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
                                        OutlinedButton(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                manualSearch()
                                            },
                                        ) {
                                            Icon(
                                                icon = RhythmIcons.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.device_manual_metadata_find_lyrics))
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
                                    modifier = Modifier.fillMaxSize(),
                                    onSeek = onLyricsSeek,
                                    lyricsSource = lyrics.source,
                                    contributions = lyrics.contributions,
                                    textSizeMultiplier = playerLyricsTextSize,
                                    textAlignment = lyricsTextAlign,
                                    showTranslation = showTranslation,
                                    showRomanization = showRomanization
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
                                        modifier = Modifier.fillMaxSize(),
                                        onSeek = onLyricsSeek,
                                        showTranslation = showTranslation,
                                        showRomanization = showRomanization,
                                        lyricsSource = lyrics.source,
                                        contributions = lyrics.contributions,
                                        textSizeMultiplier = playerLyricsTextSize,
                                        textAlignment = lyricsTextAlign
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
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    // Floating Romanization and Translation Stack (on the right)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = catalogLyricsLanguages.isEmpty() &&
                            (isLoadingLyrics || !autoHideLyricsControls || controlsVisible),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 4.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quick Toggle Romanization
                            FilledTonalIconToggleButton(
                                checked = showRomanization,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setShowLyricsRomanization(it)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("translate"),
                                    contentDescription = stringResource(R.string.fullscreenlyricsview_romanization),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Quick Toggle Translation
                            FilledTonalIconToggleButton(
                                checked = showTranslation,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setShowLyricsTranslation(it)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("subtitles"),
                                    contentDescription = stringResource(R.string.fullscreenlyricsview_translation),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Floating Lyrics Editor and Lyrics Settings Stack (on the left)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLoadingLyrics || !autoHideLyricsControls || controlsVisible,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 16.dp, start = 4.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (lyricsEditorEnabled) {
                                // Quick Open Lyrics Editor
                                FilledTonalIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        onShowLyricsEditor()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("edit"),
                                        contentDescription = stringResource(R.string.edit_lyrics),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Quick Open Lyrics Settings
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    onNavigateToLyricsSettings()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("settings"),
                                    contentDescription = stringResource(R.string.lyrics_settings),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // C. SYNC TUNER DOCK: Translucent slider or quick buttons to tweak sync offset in real time
                AnimatedVisibility(
                    visible = (isLoadingLyrics || hasSyncedLyrics) && (!autoHideLyricsControls || controlsVisible),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = glassBgColor,
                            border = BorderStroke(1.dp, glassBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.fullscreenlyricsview_realtime_sync_adjustment),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondaryColor
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Nudge Backwards
                                    val nudgeBackInteractionSource = remember { MutableInteractionSource() }
                                    val nudgeBackPressed by nudgeBackInteractionSource.collectIsPressedAsState()
                                    val nudgeBackScale by animateFloatAsState(
                                        targetValue = if (nudgeBackPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "nudge_back_bounce"
                                    )
                                    Surface(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            manualSyncOffsetMs -= 500L
                                        },
                                        shape = CircleShape,
                                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer {
                                                scaleX = nudgeBackScale
                                                scaleY = nudgeBackScale
                                            },
                                        interactionSource = nudgeBackInteractionSource
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("chevron_left"),
                                                contentDescription = stringResource(R.string.lyrics_nudge_backward),
                                                tint = textPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Display Current Offset
                                    val offsetSeconds = manualSyncOffsetMs / 1000f
                                    Text(
                                        text = String.format(Locale.ROOT, "%+.1fs", offsetSeconds),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )

                                    // Nudge Forwards
                                    val nudgeForwardInteractionSource = remember { MutableInteractionSource() }
                                    val nudgeForwardPressed by nudgeForwardInteractionSource.collectIsPressedAsState()
                                    val nudgeForwardScale by animateFloatAsState(
                                        targetValue = if (nudgeForwardPressed) 0.90f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "nudge_forward_bounce"
                                    )
                                    Surface(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            manualSyncOffsetMs += 500L
                                        },
                                        shape = CircleShape,
                                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer {
                                                scaleX = nudgeForwardScale
                                                scaleY = nudgeForwardScale
                                            },
                                        interactionSource = nudgeForwardInteractionSource
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("chevron_right"),
                                                contentDescription = stringResource(R.string.lyrics_nudge_forward),
                                                tint = textPrimaryColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Reset Button
                                    if (manualSyncOffsetMs != 0L) {
                                        Icon(
                                            imageVector = RhythmIcons.Restore,
                                            contentDescription = stringResource(R.string.fullscreenlyricsview_reset_offset),
                                            tint = textSecondaryColor,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .size(20.dp)
                                                .bouncyClickable {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    manualSyncOffsetMs = 0L
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
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


@Composable
internal fun CatalogLyricSourceToggleButton(
    visible: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (!visible) return
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = MaterialSymbolIcon(if (checked) "lyrics" else "image"),
            contentDescription = stringResource(
                if (checked) R.string.lyrics_show_text
                else R.string.lyrics_show_source_images,
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun CatalogLyricSourceImagesView(
    images: List<CatalogLyricSourceImage>,
    modifier: Modifier = Modifier,
) {
    val documents = remember(images) {
        images.distinctBy(CatalogLyricSourceImage::documentId)
    }
    var selectedDocumentId by remember(documents) {
        mutableStateOf(documents.firstOrNull()?.documentId)
    }
    val selectedPages = remember(images, selectedDocumentId) {
        images.filter { it.documentId == selectedDocumentId }
            .sortedWith(compareBy(CatalogLyricSourceImage::displayOrder).thenBy {
                it.physicalPageNumber
            })
    }

    Column(modifier = modifier) {
        if (documents.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                FilledTonalButton(
                    onClick = { expanded = true },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("menu_book"),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedPages.firstOrNull()?.documentTitle.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = MaterialSymbolIcon("arrow_drop_down"),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    documents.forEach { document ->
                        DropdownMenuItem(
                            text = { Text(document.documentTitle) },
                            onClick = {
                                expanded = false
                                selectedDocumentId = document.documentId
                            },
                            trailingIcon = if (document.documentId == selectedDocumentId) {
                                {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("check"),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(selectedPages, key = CatalogLyricSourceImage::sourcePageId) { page ->
                val pageLabel = page.displayLabel
                    ?: stringResource(R.string.lyrics_source_pdf_page, page.physicalPageNumber)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = buildString {
                            append(page.documentTitle)
                            append(" · ")
                            append(pageLabel)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    ZoomableLyricSourcePage(page)
                }
            }
        }
    }
}

@Composable
internal fun ZoomableLyricSourcePage(page: CatalogLyricSourceImage) {
    var scale by remember(page.sourcePageId) { mutableFloatStateOf(1f) }
    var offset by remember(page.sourcePageId) { mutableStateOf(Offset.Zero) }
    var loadFailed by remember(page.sourcePageId) { mutableStateOf(false) }
    var retryKey by remember(page.sourcePageId) { mutableIntStateOf(0) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else offset + panChange
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(page.widthPx.toFloat() / page.heightPx.toFloat()),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            key(retryKey) {
                AsyncImage(
                    model = Uri.parse(CatalogArtworkPolicy.uri(page.imageAssetId)),
                    contentDescription = stringResource(
                        R.string.lyrics_source_page_description,
                        page.documentTitle,
                        page.physicalPageNumber,
                    ),
                    contentScale = ContentScale.Fit,
                    onSuccess = { loadFailed = false },
                    onError = { loadFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(
                            state = transformState,
                            canPan = { scale > 1f },
                        )
                        .pointerInput(page.sourcePageId) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2f
                                    }
                                },
                            )
                        },
                )
            }
            if (loadFailed) {
                FilledTonalButton(
                    onClick = {
                        loadFailed = false
                        retryKey += 1
                    },
                ) {
                    Icon(
                        imageVector = RhythmIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.updates_retry))
                }
            }
        }
    }
}

@Composable
internal fun CatalogLyricsLanguageMenu(
    languages: List<String>,
    selectedLanguage: String?,
    onLanguageSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.size <= 1) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            modifier = Modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = MaterialSymbolIcon("translate"),
                contentDescription = stringResource(R.string.cd_change_language),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = catalogLyricsLanguageCompactLabel(selectedLanguage ?: languages.first()),
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = MaterialSymbolIcon("arrow_drop_down"),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = catalogLyricsLanguageLabel(language),
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        expanded = false
                        onLanguageSelect(language)
                    },
                    leadingIcon = {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = if (language.equals(selectedLanguage, ignoreCase = true)) {
                        {
                            Icon(
                                imageVector = MaterialSymbolIcon("check"),
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun catalogLyricsLanguageLabel(languageTag: String): String {
    val locale = Locale.forLanguageTag(languageTag)
    return locale.getDisplayName(Locale.getDefault()).takeIf(String::isNotBlank) ?: languageTag
}

private fun catalogLyricsLanguageCompactLabel(languageTag: String): String {
    val locale = Locale.forLanguageTag(languageTag)
    val displayLocale = Locale.getDefault()
    return locale.getDisplayScript(displayLocale).takeIf(String::isNotBlank)
        ?: locale.getDisplayLanguage(displayLocale).takeIf(String::isNotBlank)
        ?: languageTag
}


@Composable
private fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncy_click"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
