@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package io.github.cluno1.sonorus.features.scores.presentation

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.ScrollMode
import alphaTab.Settings
import alphaTab.collections.List as AlphaTabList
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.model.Score
import alphaTab.model.Track
import alphaTab.model.NoteStyle
import alphaTab.model.NoteSubElement
import alphaTab.model.Color as AlphaTabColor
import alphaTab.synth.IExternalMediaHandler
import alphaTab.synth.IExternalMediaSynthOutput
import alphaTab.synth.PlayerState
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.view.View
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.scores.data.BundledScoreLoader
import io.github.cluno1.sonorus.features.scores.data.BundledScoreVariant
import io.github.cluno1.sonorus.features.scores.data.LoadedScore
import io.github.cluno1.sonorus.features.scores.data.ScoreLyricScript
import io.github.cluno1.sonorus.features.scores.data.MergedDisplayScore
import io.github.cluno1.sonorus.features.scores.data.ScoreEditSession
import io.github.cluno1.sonorus.features.scores.data.ScoreGlobalSettings
import io.github.cluno1.sonorus.features.scores.data.ScoreNoteRef
import io.github.cluno1.sonorus.features.scores.data.ScoreScopedSettings
import io.github.cluno1.sonorus.features.scores.data.ScoreSettingsStore
import io.github.cluno1.sonorus.features.scores.data.ScoreSourceMap
import io.github.cluno1.sonorus.infrastructure.service.MediaPlaybackService
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cluno1.sonorus.shared.data.repository.PlaybackActivityKind
import io.github.cluno1.sonorus.shared.data.repository.PlaybackDurationAccumulator
import io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository
import io.github.cluno1.sonorus.shared.data.repository.PlaybackSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ScoreViewMode {
    OCR,
    MIDI,
    COMPARE
}

private enum class ScorePlaybackStatus {
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    ERROR
}

enum class ScorePlaybackCommandAction { PLAY, PAUSE, STOP }

data class ScorePlaybackCommand(
    val sequence: Long,
    val action: ScorePlaybackCommandAction,
)

internal enum class ScorePlaybackIndicatorMode {
    LINE,
    PULSE,
}

private enum class ScorePlaybackEndBehavior { PAUSE_AT_END, LOOP_CURRENT }

private data class MergedDisplayProjection(
    val trackIndexes: Set<Int>,
    val scores: Map<BundledScoreVariant, MergedDisplayScore>
)

private class ScoreUsageRecorder(
    private val repository: PlaybackStatsRepository,
    private val subject: PlaybackSubject,
) {
    private val viewingDuration = PlaybackDurationAccumulator()
    private val listeningDuration = PlaybackDurationAccumulator()
    private var closed = false

    fun onViewVisible() {
        if (!closed) viewingDuration.resume(SystemClock.elapsedRealtime())
    }

    fun onViewHidden() {
        if (!closed) viewingDuration.pause(SystemClock.elapsedRealtime())
    }

    fun onPlaybackStarted() {
        if (!closed) listeningDuration.resume(SystemClock.elapsedRealtime())
    }

    fun onPlaybackPaused() {
        if (!closed) listeningDuration.pause(SystemClock.elapsedRealtime())
    }

    fun close() {
        if (closed) return
        closed = true
        val now = SystemClock.elapsedRealtime()
        val viewingMs = viewingDuration.consume(now)
        val listeningMs = listeningDuration.consume(now)
        if (viewingMs >= SCORE_STATS_MIN_DURATION_MS) {
            repository.recordPlayback(subject, PlaybackActivityKind.SCORE_VIEW, viewingMs)
        }
        if (listeningMs >= SCORE_STATS_MIN_DURATION_MS) {
            repository.recordPlayback(subject, PlaybackActivityKind.SCORE_LISTEN, listeningMs)
        }
    }
}

private class ScorePlaybackController(
    private val context: Context,
    private val onPlaybackStarted: () -> Unit = {},
    private val onPlaybackPaused: () -> Unit = {},
    private val onPositionChanged: (tick: Long, timeMs: Long) -> Unit = { _, _ -> },
) {
    private var view: AlphaTabView? = null
    private var score: Score? = null
    private var playerIsReady = false
    private var mutedTrackIndexes: Set<Int> = emptySet()
    private val completionTracker = ScorePlaybackCompletionTracker()
    private val displayBindings = mutableMapOf<AlphaTabView, ScorePlaybackDisplayBinding>()
    private var currentTick = 0.0
    private var currentTime = 0.0
    private var endTime = 0.0
    private var activePositions: List<ScorePlaybackBeatPosition> = emptyList()
    private var playbackSpeed = 1.0
    private var metronomeEnabled = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false
    private var resumeAfterTransientFocusLoss = false
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterTransientFocusLoss = false
                pauseForAudioFocusLoss()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                resumeAfterTransientFocusLoss = isPlaying
                pauseForAudioFocusLoss()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeAfterTransientFocusLoss) {
                    resumeAfterTransientFocusLoss = false
                    view?.post {
                        if (!isPlaying) view?.api?.playPause()
                    }
                }
            }
        }
    }

    fun attach(view: AlphaTabView, score: Score) {
        this.view = view
        this.score = score
        playerIsReady = false
        endTime = 0.0
        completionTracker.reset()
        resetDisplayPosition()
    }

    fun detach(view: AlphaTabView) {
        if (this.view === view) {
            this.view = null
            score = null
            playerIsReady = false
            isPlaying = false
            onPlaybackPaused()
            completionTracker.reset()
            abandonAudioFocus()
        }
    }

    fun onPlayerReady(view: AlphaTabView) {
        if (this.view === view) {
            playerIsReady = true
            view.api.playbackSpeed = playbackSpeed
            applyMetronome()
            applyMutedTracks()
        }
    }

    fun setMutedTrackIndexes(indexes: Set<Int>) {
        mutedTrackIndexes = indexes
        applyMutedTracks()
    }

    fun setPlaybackSpeed(speed: Double) {
        val safeSpeed = speed.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        if (safeSpeed == playbackSpeed) return
        playbackSpeed = safeSpeed
        val currentView = view
        currentView?.post {
            if (view === currentView && playerIsReady) {
                currentView.api.playbackSpeed = playbackSpeed
            }
        }
        displayBindings.forEach { (displayView, binding) ->
            displayView.post {
                if (displayBindings[displayView] === binding) {
                    applyPlaybackSpeedToDisplay(displayView, binding)
                }
            }
        }
        Log.i(SCORE_PLAYBACK_TAG, "playback speed=${"%.4f".format(safeSpeed)}x")
    }

    fun setMetronomeEnabled(enabled: Boolean) {
        if (enabled == metronomeEnabled) return
        metronomeEnabled = enabled
        applyMetronome()
        Log.i(SCORE_PLAYBACK_TAG, "metronome=${if (enabled) "on" else "off"}")
    }

    fun playPause() {
        val currentView = view ?: return
        if (!isPlaying) {
            pauseMainPlayer()
            if (!requestAudioFocus()) return
        }
        if (completionTracker.consumeFinished()) {
            // alphaTab's Android player runs commands on a FIFO worker queue. Queueing stop
            // before playPause guarantees that a naturally completed score returns to tick 0
            // before playback starts again.
            currentView.api.stop()
            currentView.api.playPause()
            Log.i(SCORE_PLAYBACK_TAG, "replay after finish: reset to start")
        } else {
            currentView.api.playPause()
        }
    }

    fun play() {
        if (!isPlaying) playPause()
    }

    fun pause() {
        if (!isPlaying) return
        val currentView = view ?: return
        isPlaying = false
        onPlaybackPaused()
        currentView.api.playPause()
    }

    fun stop() {
        completionTracker.reset()
        isPlaying = false
        onPlaybackPaused()
        resumeAfterTransientFocusLoss = false
        view?.api?.stop()
        abandonAudioFocus()
        resetDisplayPosition()
    }

    fun onPlayerFinished(view: AlphaTabView) {
        if (this.view === view) {
            completionTracker.markFinished()
            isPlaying = false
            onPlaybackPaused()
            resumeAfterTransientFocusLoss = false
            abandonAudioFocus()
            resetDisplayPosition()
        }
    }

    fun onPlayerStarted(view: AlphaTabView) {
        if (this.view === view) {
            completionTracker.reset()
            isPlaying = true
            onPlaybackStarted()
            updateDisplayPlaybackState(playing = true)
        }
    }

    fun onPlayerPaused(view: AlphaTabView) {
        if (this.view === view) {
            isPlaying = false
            onPlaybackPaused()
            updateDisplayPlaybackState(playing = false)
            if (!resumeAfterTransientFocusLoss) abandonAudioFocus()
        }
    }

    fun onPlayerPositionChanged(
        view: AlphaTabView,
        tick: Double,
        time: Double,
        totalTime: Double,
    ) {
        if (this.view !== view) return
        currentTick = tick
        currentTime = time
        endTime = totalTime
        onPositionChanged(tick.toLong(), time.toLong())
        displayBindings.forEach { (displayView, binding) ->
            displayView.post {
                if (displayBindings[displayView] === binding) {
                    updateDisplayPosition(displayView, binding)
                }
            }
        }
    }

    fun onActiveBeatsChanged(view: AlphaTabView, positions: List<ScorePlaybackBeatPosition>) {
        if (this.view !== view || positions == activePositions) return
        activePositions = positions
        displayBindings.forEach { (displayView, binding) ->
            displayView.post {
                if (displayBindings[displayView] !== binding) return@post
                when (binding.mode) {
                    ScorePlaybackIndicatorMode.LINE -> Unit
                    ScorePlaybackIndicatorMode.PULSE -> binding.onPulsePositions(positions)
                }
            }
        }
    }

    fun attachDisplay(
        view: AlphaTabView,
        mode: ScorePlaybackIndicatorMode,
        onPulsePositions: (List<ScorePlaybackBeatPosition>) -> Unit,
    ) {
        val binding = ScorePlaybackDisplayBinding(mode, onPulsePositions)
        displayBindings[view] = binding
        view.post {
            if (displayBindings[view] !== binding) return@post
            applyPlaybackSpeedToDisplay(view, binding)
            configureDisplayOutput(view, binding)
            updateDisplayPosition(view, binding)
            if (isPlaying) view.api.play() else view.api.pause()
            if (mode == ScorePlaybackIndicatorMode.PULSE) {
                onPulsePositions(activePositions)
            }
        }
    }

    fun onDisplayPlayerReady(view: AlphaTabView) {
        val binding = displayBindings[view] ?: return
        view.post {
            if (displayBindings[view] !== binding) return@post
            applyPlaybackSpeedToDisplay(view, binding)
            configureDisplayOutput(view, binding)
            updateDisplayPosition(view, binding)
            if (isPlaying) view.api.play() else view.api.pause()
        }
    }

    fun detachDisplay(view: AlphaTabView) {
        displayBindings.remove(view)
    }

    private fun resetDisplayPosition() {
        currentTick = 0.0
        currentTime = 0.0
        activePositions = emptyList()
        displayBindings.forEach { (displayView, binding) ->
            displayView.post {
                if (displayBindings[displayView] !== binding) return@post
                displayView.api.pause()
                configureDisplayOutput(displayView, binding)
                updateDisplayPosition(displayView, binding)
                if (binding.mode == ScorePlaybackIndicatorMode.PULSE) {
                    binding.onPulsePositions(emptyList())
                }
            }
        }
    }

    private fun updateDisplayPlaybackState(playing: Boolean) {
        displayBindings.forEach { (displayView, binding) ->
            displayView.post {
                if (displayBindings[displayView] !== binding) return@post
                configureDisplayOutput(displayView, binding)
                if (playing) displayView.api.play() else displayView.api.pause()
            }
        }
    }

    private fun configureDisplayOutput(
        displayView: AlphaTabView,
        binding: ScorePlaybackDisplayBinding,
    ): IExternalMediaSynthOutput? {
        val output = displayView.api.player?.output as? IExternalMediaSynthOutput ?: return null
        binding.externalMediaHandler.duration = endTime
        binding.externalMediaHandler.playbackRate = playbackSpeed
        if (output.handler !== binding.externalMediaHandler) {
            output.handler = binding.externalMediaHandler
        }
        return output
    }

    private fun applyPlaybackSpeedToDisplay(
        displayView: AlphaTabView,
        binding: ScorePlaybackDisplayBinding,
    ) {
        binding.externalMediaHandler.playbackRate = playbackSpeed
        runCatching { displayView.api.playbackSpeed = playbackSpeed }
    }

    private fun updateDisplayPosition(
        displayView: AlphaTabView,
        binding: ScorePlaybackDisplayBinding,
    ) {
        val output = configureDisplayOutput(displayView, binding)
        if (output != null && endTime > 0.0) {
            output.updatePosition(currentTime)
        } else {
            // The display player might not be ready yet. Keep the cursor correct until its
            // external-media output becomes available, then switch to time-based updates.
            displayView.api.tickPosition = currentTick
        }
    }

    private fun applyMutedTracks() {
        val currentView = view ?: return
        val currentScore = score ?: return
        if (!playerIsReady) return

        val mutedTracks = AlphaTabList<Track>()
        val audibleTracks = AlphaTabList<Track>()
        currentScore.tracks.forEach { track ->
            if (track.index.toInt() in mutedTrackIndexes) {
                mutedTracks.push(track)
            } else {
                audibleTracks.push(track)
            }
        }
        currentView.api.changeTrackMute(mutedTracks, true)
        currentView.api.changeTrackMute(audibleTracks, false)
        Log.i(SCORE_PLAYBACK_TAG, "muted tracks=${mutedTrackIndexes.sorted()}")
    }

    private fun applyMetronome() {
        val currentView = view ?: return
        currentView.post {
            if (view === currentView && playerIsReady) {
                currentView.api.metronomeVolume = if (metronomeEnabled) {
                    SCORE_METRONOME_VOLUME
                } else {
                    0.0
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioFocusRequest != null) return true
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        return if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
            Log.i(SCORE_PLAYBACK_TAG, "audio focus granted")
            true
        } else {
            Log.w(SCORE_PLAYBACK_TAG, "audio focus request denied")
            false
        }
    }

    private fun pauseMainPlayer() {
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            action = MediaPlaybackService.ACTION_PAUSE_FOR_IN_APP_AUDIO
        }
        runCatching { context.startService(intent) }
            .onFailure { Log.w(SCORE_PLAYBACK_TAG, "could not pause main player", it) }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
    }

    private fun pauseForAudioFocusLoss() {
        val currentView = view ?: return
        if (!isPlaying) return
        isPlaying = false
        onPlaybackPaused()
        currentView.post {
            if (view === currentView) currentView.api.playPause()
        }
    }
}

private data class ScorePlaybackDisplayBinding(
    val mode: ScorePlaybackIndicatorMode,
    val onPulsePositions: (List<ScorePlaybackBeatPosition>) -> Unit,
    val externalMediaHandler: ScoreDisplayExternalMediaHandler = ScoreDisplayExternalMediaHandler(),
)

private class ScoreDisplayExternalMediaHandler : IExternalMediaHandler {
    var duration: Double = 0.0
    override val backingTrackDuration: Double
        get() = duration
    override var playbackRate: Double = 1.0
    override var masterVolume: Double = 1.0

    override fun seekTo(time: Double) = Unit

    override fun play() = Unit

    override fun pause() = Unit
}

/** Read-only projection of an immutable server ScoreRevision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScoreScreen(
    title: String,
    canonicalMusicXml: ByteArray,
    onBackClick: () -> Unit,
    onChorusClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    onPlaybackPositionChanged: (tick: Long, timeMs: Long) -> Unit = { _, _ -> },
    playbackCommand: ScorePlaybackCommand? = null,
    playbackInteractionEnabled: Boolean = true,
    scoreLabel: String? = null,
    revisionLabel: String? = null,
    revisionTimeLabel: String? = null,
    revisionLockedMessage: String? = null,
    canOpenNewerRevision: Boolean = false,
    canOpenOlderRevision: Boolean = false,
    onOpenNewerRevision: () -> Unit = {},
    onOpenOlderRevision: () -> Unit = {},
    scoreSettingsContent: @Composable (stopPlayback: () -> Unit) -> Unit = {},
    topContent: @Composable () -> Unit = {},
    overlayContent: @Composable () -> Unit = {},
    expectedPartCount: Int? = null,
    playbackSubject: PlaybackSubject? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val loader = remember(context) { BundledScoreLoader(context) }
    val miniPlayerPadding = LocalMiniPlayerPadding.current
    var loaded by remember(canonicalMusicXml) { mutableStateOf<LoadedScore?>(null) }
    var soundFont by remember(canonicalMusicXml) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(canonicalMusicXml) { mutableStateOf(false) }
    var retryKey by remember(canonicalMusicXml) { mutableIntStateOf(0) }

    LaunchedEffect(loader, canonicalMusicXml, retryKey) {
        runCatching {
            loader.loadVariant(BundledScoreVariant.OCR, canonicalMusicXml) to loader.loadSoundFont()
        }.fold(
            onSuccess = {
                loaded = it.first
                soundFont = it.second
                failed = false
            },
            onFailure = { failed = true },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (loaded == null || soundFont == null || failed) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            scoreLabel?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 12.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Icon(
                                RhythmIcons.Back,
                                contentDescription = stringResource(R.string.score_back),
                            )
                        }
                    },
                    actions = {
                        if (onHelpClick != null) {
                            FilledTonalIconButton(
                                onClick = onHelpClick,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    MaterialSymbolIcon("help", filled = true),
                                    contentDescription = stringResource(R.string.chorus_recording_help_open),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(miniPlayerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                failed -> ScoreError(onRetry = {
                    loaded = null
                    soundFont = null
                    failed = false
                    retryKey++
                })
                loaded == null || soundFont == null -> ScoreLoading(Modifier.fillMaxSize())
                else -> Column(Modifier.fillMaxSize()) {
                    val actualTrackCount = checkNotNull(loaded).displayScore.tracks.length.toInt()
                    if (expectedPartCount != null && expectedPartCount != actualTrackCount) {
                        Text(
                            "提示：编配定义 $expectedPartCount 个声部，此修订包含 $actualTrackCount 条谱轨；将按谱面原始结构显示。",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    ScoreReadyContent(
                        loader = loader,
                        scores = mapOf(
                            BundledScoreVariant.OCR to checkNotNull(loaded),
                            BundledScoreVariant.MIDI to checkNotNull(loaded),
                        ),
                        soundFont = checkNotNull(soundFont),
                        viewMode = ScoreViewMode.OCR,
                        onEditingChange = {},
                        allowEditing = false,
                        title = title,
                        subtitle = scoreLabel,
                        onBackClick = onBackClick,
                        onChorusClick = onChorusClick,
                        onHelpClick = onHelpClick,
                        onPlaybackPositionChanged = onPlaybackPositionChanged,
                        playbackCommand = playbackCommand,
                        playbackInteractionEnabled = playbackInteractionEnabled,
                        scoreSettingsContent = { stopPlayback ->
                            scoreSettingsContent(stopPlayback)
                            if (revisionLabel != null) {
                                ScoreSettingsCard(
                                    icon = RhythmIcons.Score,
                                    title = stringResource(R.string.score_version),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            revisionLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        revisionTimeLabel?.let { time ->
                                            Text(
                                                time,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (revisionLockedMessage == null) {
                                            FilterChip(
                                                selected = !canOpenNewerRevision,
                                                onClick = onOpenNewerRevision,
                                                enabled = canOpenNewerRevision,
                                                label = { Text(stringResource(R.string.score_revision_newest)) },
                                            )
                                            FilterChip(
                                                selected = canOpenNewerRevision,
                                                onClick = onOpenOlderRevision,
                                                enabled = canOpenOlderRevision,
                                                label = { Text(stringResource(R.string.score_revision_older)) },
                                            )
                                        }
                                    }
                                    if (revisionLockedMessage != null) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Icon(
                                                    MaterialSymbolIcon("lock", filled = true),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Text(
                                                    revisionLockedMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        topContent = topContent,
                        overlayContent = overlayContent,
                        playbackSubject = playbackSubject,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun ScoreReadyContent(
    loader: BundledScoreLoader,
    scores: Map<BundledScoreVariant, LoadedScore>,
    soundFont: ByteArray,
    viewMode: ScoreViewMode,
    onEditingChange: (Boolean) -> Unit,
    allowEditing: Boolean = true,
    title: String? = null,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    onChorusClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    onPlaybackPositionChanged: (tick: Long, timeMs: Long) -> Unit = { _, _ -> },
    playbackCommand: ScorePlaybackCommand? = null,
    playbackInteractionEnabled: Boolean = true,
    scoreSettingsContent: @Composable (stopPlayback: () -> Unit) -> Unit = {},
    topContent: @Composable () -> Unit = {},
    overlayContent: @Composable () -> Unit = {},
    playbackSubject: PlaybackSubject? = null,
    modifier: Modifier = Modifier
) {
    var activeScores by remember(scores) { mutableStateOf(scores) }
    val ocrScore = checkNotNull(activeScores[BundledScoreVariant.OCR])
    val midiScore = checkNotNull(activeScores[BundledScoreVariant.MIDI])
    val context = LocalContext.current
    val scoreSettingsStore = remember(context) { ScoreSettingsStore(context) }
    val scoreSettingsId = playbackSubject?.scoreId
    val initialGlobalSettings = remember(scoreSettingsStore) {
        scoreSettingsStore.loadGlobal()
    }
    val initialScopedSettings = remember(scoreSettingsStore, scoreSettingsId) {
        scoreSettingsId?.let(scoreSettingsStore::loadScore) ?: ScoreScopedSettings()
    }
    var playbackVariant by rememberSaveable { mutableStateOf(BundledScoreVariant.OCR) }
    var playbackStatus by remember { mutableStateOf(ScorePlaybackStatus.PREPARING) }
    var playbackIndicatorMode by remember {
        mutableStateOf(
            runCatching {
                ScorePlaybackIndicatorMode.valueOf(initialGlobalSettings.playbackIndicatorMode)
            }.getOrDefault(ScorePlaybackIndicatorMode.LINE)
        )
    }
    var followScrollEnabled by remember {
        mutableStateOf(initialGlobalSettings.followScrollEnabled)
    }
    var metronomeEnabled by remember {
        mutableStateOf(initialGlobalSettings.metronomeEnabled)
    }
    var playbackEndBehavior by remember {
        mutableStateOf(
            runCatching {
                ScorePlaybackEndBehavior.valueOf(initialGlobalSettings.playbackEndBehavior)
            }.getOrDefault(ScorePlaybackEndBehavior.PAUSE_AT_END)
        )
    }
    var staffMode by remember(scoreSettingsId) {
        mutableStateOf(
            runCatching { ScoreStaffMode.valueOf(initialScopedSettings.staffMode) }
                .getOrDefault(ScoreStaffMode.ALL_STAVES)
        )
    }
    var notationLayout by remember {
        mutableStateOf(
            runCatching { ScoreNotationLayout.valueOf(initialGlobalSettings.notationLayout) }
                .getOrDefault(ScoreNotationLayout.SEPARATE_PARTS)
        )
    }
    var partColorMode by remember {
        mutableStateOf(
            runCatching { ScorePartColorMode.valueOf(initialGlobalSettings.partColorMode) }
                .getOrDefault(ScorePartColorMode.DEFAULT)
        )
    }
    var selectedTrackMask by remember(scoreSettingsId) {
        mutableIntStateOf(initialScopedSettings.selectedTrackMask)
    }
    var lyricScriptSetting by remember {
        mutableStateOf(initialGlobalSettings.lyricScript)
    }
    val uiLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val effectiveLyricScript = ScoreLyricScript.fromSetting(lyricScriptSetting)
        ?: ScoreLyricScript.forLocale(uiLocale)
    var trackControlsExpanded by rememberSaveable { mutableStateOf(false) }
    var mutedTracksByVariant by remember(scoreSettingsId) {
        mutableStateOf(
            initialScopedSettings.mutedTrackIndexesByVariant.mapNotNull { (variant, indexes) ->
                runCatching { BundledScoreVariant.valueOf(variant) }.getOrNull()
                    ?.let { it to indexes.toSet() }
            }.toMap()
        )
    }
    val scoreUsageRecorder = remember(context, playbackSubject) {
        playbackSubject?.let {
            ScoreUsageRecorder(
                repository = PlaybackStatsRepository.getInstance(context.applicationContext),
                subject = it,
            )
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, scoreUsageRecorder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> scoreUsageRecorder?.onViewVisible()
                Lifecycle.Event.ON_STOP -> scoreUsageRecorder?.onViewHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            scoreUsageRecorder?.onViewVisible()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scoreUsageRecorder?.onViewHidden()
            scoreUsageRecorder?.close()
        }
    }
    val currentPositionCallback by rememberUpdatedState(onPlaybackPositionChanged)
    val playbackController = remember(context, scoreUsageRecorder) {
        ScorePlaybackController(
            context = context.applicationContext,
            onPlaybackStarted = { scoreUsageRecorder?.onPlaybackStarted() },
            onPlaybackPaused = { scoreUsageRecorder?.onPlaybackPaused() },
            onPositionChanged = { tick, timeMs -> currentPositionCallback(tick, timeMs) },
        )
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(playbackCommand) {
        when (playbackCommand?.action) {
            ScorePlaybackCommandAction.PLAY -> playbackController.play()
            ScorePlaybackCommandAction.PAUSE -> playbackController.pause()
            ScorePlaybackCommandAction.STOP -> playbackController.stop()
            null -> Unit
        }
    }
    var editSession by remember { mutableStateOf<ScoreEditSession?>(null) }
    var editVariant by remember { mutableStateOf<BundledScoreVariant?>(null) }
    var editBaseScore by remember { mutableStateOf<LoadedScore?>(null) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var editRevision by remember { mutableIntStateOf(0) }
    var editBusy by remember { mutableStateOf(false) }
    var editMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewMode) {
        when (viewMode) {
            ScoreViewMode.OCR -> playbackVariant = BundledScoreVariant.OCR
            ScoreViewMode.MIDI -> playbackVariant = BundledScoreVariant.MIDI
            ScoreViewMode.COMPARE -> Unit
        }
    }
    LaunchedEffect(playbackVariant) {
        playbackStatus = ScorePlaybackStatus.PREPARING
    }

    val playbackScore = checkNotNull(activeScores[playbackVariant])
    val playbackSourceBpm = remember(playbackScore.playbackScore) {
        normalizedScoreSourceBpm(playbackScore.playbackScore.tempo)
    }
    val displayedSourceBpm = remember(playbackSourceBpm) {
        displayedScoreSourceBpm(playbackSourceBpm)
    }
    var customPlaybackBpm by remember(scoreSettingsId) {
        mutableStateOf(initialScopedSettings.customPlaybackBpm)
    }

    fun persistGlobalSettings(
        indicatorMode: ScorePlaybackIndicatorMode = playbackIndicatorMode,
        followScroll: Boolean = followScrollEnabled,
        metronome: Boolean = metronomeEnabled,
        endBehavior: ScorePlaybackEndBehavior = playbackEndBehavior,
        layout: ScoreNotationLayout = notationLayout,
        colorMode: ScorePartColorMode = partColorMode,
        lyricScript: String = lyricScriptSetting,
    ) {
        scoreSettingsStore.saveGlobal(
            ScoreGlobalSettings(
                playbackIndicatorMode = indicatorMode.name,
                followScrollEnabled = followScroll,
                metronomeEnabled = metronome,
                playbackEndBehavior = endBehavior.name,
                notationLayout = layout.name,
                partColorMode = colorMode.name,
                lyricScript = lyricScript,
            )
        )
    }

    fun persistScopedSettings(
        playbackBpm: Int? = customPlaybackBpm,
        displayedStaffMode: ScoreStaffMode = staffMode,
        trackMask: Int = selectedTrackMask,
        mutedTracks: Map<BundledScoreVariant, Set<Int>> = mutedTracksByVariant,
    ) {
        val id = scoreSettingsId ?: return
        scoreSettingsStore.saveScore(
            id,
            ScoreScopedSettings(
                customPlaybackBpm = playbackBpm,
                staffMode = displayedStaffMode.name,
                selectedTrackMask = trackMask,
                mutedTrackIndexesByVariant = mutedTracks.mapKeys { it.key.name }
                    .mapValues { it.value.sorted() },
            )
        )
    }

    val targetPlaybackBpm = customPlaybackBpm ?: displayedSourceBpm
    val playbackSpeed = remember(playbackSourceBpm, customPlaybackBpm) {
        customPlaybackBpm?.let { targetBpm ->
            scorePlaybackSpeedForTargetBpm(playbackSourceBpm, targetBpm)
        } ?: 1.0
    }
    val trackOptions = remember(playbackScore.displayScore, playbackScore.displayPartLabels) {
        buildScoreTrackOptions(
            trackNames = playbackScore.displayScore.tracks.toList().map { it.name },
            inferredLabels = playbackScore.displayPartLabels,
        )
    }
    val effectiveSelectedTrackMask = trackOptions.takeIf { it.isNotEmpty() }?.let {
        normalizeScoreTrackSelectionMask(selectedTrackMask, it.size)
    } ?: selectedTrackMask
    val selectedTrackIndexes = trackOptions
        .mapNotNullTo(mutableSetOf()) { option ->
            option.index.takeIf { index ->
                effectiveSelectedTrackMask and (1 shl index) != 0
            }
        }
        .ifEmpty { trackOptions.firstOrNull()?.let { setOf(it.index) }.orEmpty() }
    val visibleTrackIndexes = when (staffMode) {
        ScoreStaffMode.ALL_STAVES -> trackOptions.mapTo(mutableSetOf()) { it.index }
        ScoreStaffMode.SELECTED_PARTS -> selectedTrackIndexes
    }
    val mutedTrackIndexes = mutedTracksByVariant[playbackVariant]
        .orEmpty()
        .filterTo(mutableSetOf()) { it in trackOptions.indices }
    var mergedDisplayProjection by remember { mutableStateOf<MergedDisplayProjection?>(null) }
    val availableLyricScripts = remember(activeScores) {
        activeScores.values.flatMapTo(linkedSetOf()) { it.availableLyricScripts }
    }

    // Show only the selected Chinese script when a score carries both. Display-only: the shared
    // playback score is untouched, and editing works on the full canonical lyrics.
    LaunchedEffect(effectiveLyricScript, activeScores, editSession == null) {
        if (editSession != null || availableLyricScripts.isEmpty()) return@LaunchedEffect
        val projected = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                activeScores.mapValues { (_, score) -> score.withLyricScript(effectiveLyricScript) }
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.e(SCORE_DISPLAY_TAG, "lyric script projection failed; showing all lyrics", error)
            return@LaunchedEffect
        }
        if (projected != activeScores) activeScores = projected
    }

    LaunchedEffect(notationLayout, staffMode, visibleTrackIndexes, activeScores) {
        if (notationLayout == ScoreNotationLayout.SEPARATE_PARTS) {
            mergedDisplayProjection = null
            if (staffMode != ScoreStaffMode.SELECTED_PARTS) return@LaunchedEffect
            // Only some parts shown: if the lyric part is hidden, show its words on a visible part.
            val carried = runCatching {
                activeScores.mapValues { (_, score) -> score.loadLyricCarriedDisplayScore(visibleTrackIndexes) }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Log.e(SCORE_DISPLAY_TAG, "lyric carrier projection failed; showing parts as-is", error)
                return@LaunchedEffect
            }
            if (carried.values.all { it == null }) return@LaunchedEffect
            mergedDisplayProjection = MergedDisplayProjection(
                trackIndexes = visibleTrackIndexes,
                scores = activeScores.mapValues { (variant, score) ->
                    carried[variant] ?: score.fallbackMergedDisplayScore
                },
            )
            return@LaunchedEffect
        }
        mergedDisplayProjection = null
        val projectedScores = runCatching {
            mutableMapOf<BundledScoreVariant, MergedDisplayScore>().apply {
                activeScores.forEach { (variant, score) ->
                    this[variant] = score.loadMergedDisplayScore(visibleTrackIndexes)
                }
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.e(SCORE_DISPLAY_TAG, "merged score projection failed; using separate tracks", error)
            activeScores.mapValues { it.value.fallbackMergedDisplayScore }
        }
        mergedDisplayProjection = MergedDisplayProjection(
            trackIndexes = visibleTrackIndexes,
            scores = projectedScores
        )
    }

    val currentMergedScores = mergedDisplayProjection
        ?.takeIf { it.trackIndexes == visibleTrackIndexes }
        ?.scores

    LaunchedEffect(playbackVariant, mutedTrackIndexes) {
        playbackController.setMutedTrackIndexes(mutedTrackIndexes)
    }
    LaunchedEffect(playbackController, playbackSpeed) {
        playbackController.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(playbackController, metronomeEnabled) {
        playbackController.setMetronomeEnabled(metronomeEnabled)
    }

    val editSaveSuccess = stringResource(R.string.score_edit_save_success)
    val editApplyError = stringResource(R.string.score_edit_apply_error)
    val editSaveError = stringResource(R.string.score_edit_save_error)
    val editMapError = stringResource(R.string.score_edit_map_error)
    val selectedNote = remember(editSession, editRevision, selectedNoteId) {
        selectedNoteId?.let { editSession?.note(it) }
    }

    fun rebuildEditedScore() {
        val session = editSession ?: return
        val variant = editVariant ?: return
        editBusy = true
        editMessage = null
        coroutineScope.launch {
            runCatching { loader.loadVariant(variant, session.toByteArray()) }
                .onSuccess { loaded ->
                    activeScores = activeScores + (variant to loaded)
                    editRevision++
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(SCORE_EDIT_TAG, "edited score projection failed", error)
                    editMessage = editApplyError
                }
            editBusy = false
        }
    }

    fun runEditCommand(command: ScoreEditSession.() -> Unit) {
        val session = editSession ?: return
        runCatching { session.command() }
            .onSuccess { rebuildEditedScore() }
            .onFailure { error ->
                Log.e(SCORE_EDIT_TAG, "score edit command failed", error)
                editMessage = editApplyError
            }
    }

    fun closeEditSession() {
        editSession = null
        editVariant = null
        editBaseScore = null
        selectedNoteId = null
        editMessage = null
        editBusy = false
        onEditingChange(false)
    }

    fun cancelEditing() {
        val variant = editVariant
        val base = editBaseScore
        if (variant != null && base != null) activeScores = activeScores + (variant to base)
        closeEditSession()
    }

    fun startEditing() {
        val variant = when (viewMode) {
            ScoreViewMode.OCR -> BundledScoreVariant.OCR
            ScoreViewMode.MIDI -> BundledScoreVariant.MIDI
            ScoreViewMode.COMPARE -> return
        }
        val base = checkNotNull(activeScores[variant])
        runCatching { ScoreEditSession.create(base.canonicalMusicXml) }
            .onSuccess { session ->
                playbackController.stop()
                playbackStatus = ScorePlaybackStatus.READY
                playbackVariant = variant
                editSession = session
                editVariant = variant
                editBaseScore = base
                selectedNoteId = null
                editMessage = null
                val trackIndex = selectedTrackIndexes.minOrNull() ?: 0
                selectedTrackMask = 1 shl trackIndex
                staffMode = ScoreStaffMode.SELECTED_PARTS
                notationLayout = ScoreNotationLayout.SEPARATE_PARTS
                onEditingChange(true)
            }
            .onFailure { error ->
                Log.e(SCORE_EDIT_TAG, "could not start score edit session", error)
                editMessage = editApplyError
            }
    }

    fun saveEditing() {
        val session = editSession ?: return
        val variant = editVariant ?: return
        editBusy = true
        coroutineScope.launch {
            runCatching { loader.saveWorkingCopy(variant, session.toByteArray()) }
                .onSuccess {
                    closeEditSession()
                    Log.i(SCORE_EDIT_TAG, editSaveSuccess)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.e(SCORE_EDIT_TAG, "could not save working MusicXML revision", error)
                    editMessage = editSaveError
                    editBusy = false
                }
        }
    }

    Column(modifier = modifier) {
        ScorePlaybackControls(
            title = title,
            subtitle = subtitle,
            onBackClick = onBackClick,
            onChorusClick = onChorusClick,
            onHelpClick = onHelpClick,
            viewMode = viewMode,
            playbackVariant = playbackVariant,
            status = playbackStatus,
            indicatorMode = playbackIndicatorMode,
            followScrollEnabled = followScrollEnabled,
            metronomeEnabled = metronomeEnabled,
            sourceBpm = displayedSourceBpm,
            targetBpm = targetPlaybackBpm,
            hasCustomBpm = customPlaybackBpm != null,
            onPlaybackVariantChange = { playbackVariant = it },
            onIndicatorModeChange = {
                playbackIndicatorMode = it
                persistGlobalSettings(indicatorMode = it)
            },
            onFollowScrollChange = {
                followScrollEnabled = it
                persistGlobalSettings(followScroll = it)
            },
            onMetronomeEnabledChange = {
                metronomeEnabled = it
                persistGlobalSettings(metronome = it)
            },
            onTargetBpmChange = { bpm ->
                val nextBpm = bpm.coerceIn(
                    MIN_SCORE_PLAYBACK_BPM,
                    MAX_SCORE_PLAYBACK_BPM,
                )
                customPlaybackBpm = nextBpm
                persistScopedSettings(playbackBpm = nextBpm)
            },
            onResetBpm = {
                customPlaybackBpm = null
                persistScopedSettings(playbackBpm = null)
            },
            endBehavior = playbackEndBehavior,
            onEndBehaviorChange = {
                playbackEndBehavior = it
                persistGlobalSettings(endBehavior = it)
            },
            onPlayPause = { playbackController.playPause() },
            onStop = {
                playbackController.stop()
                playbackStatus = ScorePlaybackStatus.READY
            },
            interactionEnabled = editSession == null && playbackInteractionEnabled,
            settingsContent = {
                scoreSettingsContent {
                    playbackController.stop()
                    playbackStatus = ScorePlaybackStatus.READY
                }
                if (editSession == null && availableLyricScripts.size >= 2) {
                    ScoreSettingsCard(
                        icon = RhythmIcons.Player.Lyrics,
                        title = stringResource(R.string.score_lyrics_script),
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                ScoreLyricScript.ZH_HANT to R.string.score_lyrics_script_hant,
                                ScoreLyricScript.ZH_HANS to R.string.score_lyrics_script_hans,
                            ).filter { (script, _) -> script in availableLyricScripts }
                                .forEach { (script, label) ->
                                    ScoreModeChip(
                                        selected = effectiveLyricScript == script,
                                        onClick = {
                                            lyricScriptSetting = script.name
                                            persistGlobalSettings(lyricScript = script.name)
                                        },
                                        label = stringResource(label),
                                    )
                                }
                        }
                    }
                }
            },
            settingsFooterContent = {
                if (editSession == null) {
                    ScoreTrackControls(
                        trackOptions = trackOptions,
                        expanded = trackControlsExpanded,
                        staffMode = staffMode,
                        notationLayout = notationLayout,
                        partColorMode = partColorMode,
                        selectedTrackIndexes = selectedTrackIndexes,
                        mutedTrackIndexes = mutedTrackIndexes,
                        onExpandedChange = { trackControlsExpanded = it },
                        onStaffModeChange = {
                            staffMode = it
                            persistScopedSettings(displayedStaffMode = it)
                        },
                        onNotationLayoutChange = {
                            notationLayout = it
                            persistGlobalSettings(layout = it)
                        },
                        onPartColorModeChange = {
                            partColorMode = it
                            persistGlobalSettings(colorMode = it)
                        },
                        onTrackVisibilityToggle = { trackIndex ->
                            val nextMask = toggleScoreTrackSelectionMask(
                                effectiveSelectedTrackMask, trackIndex, trackOptions.size
                            )
                            selectedTrackMask = nextMask
                            staffMode = ScoreStaffMode.SELECTED_PARTS
                            persistScopedSettings(
                                displayedStaffMode = ScoreStaffMode.SELECTED_PARTS,
                                trackMask = nextMask,
                            )
                        },
                        onTrackSoundToggle = { trackIndex ->
                            val next = mutedTrackIndexes.toMutableSet().apply {
                                if (!add(trackIndex)) remove(trackIndex)
                            }
                            val nextMutedTracks = mutedTracksByVariant + (playbackVariant to next)
                            mutedTracksByVariant = nextMutedTracks
                            persistScopedSettings(mutedTracks = nextMutedTracks)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        topContent()
        if (allowEditing) {
            ScoreEditControls(
                editing = editSession != null,
                canStart = viewMode != ScoreViewMode.COMPARE,
                canUndo = editSession?.canUndo == true,
                canRedo = editSession?.canRedo == true,
                isDirty = editSession?.isDirty == true,
                busy = editBusy,
                message = editMessage,
                onStart = ::startEditing,
                onUndo = {
                    if (editSession?.undo() == true) rebuildEditedScore()
                },
                onRedo = {
                    if (editSession?.redo() == true) rebuildEditedScore()
                },
                onSave = ::saveEditing,
                onCancel = ::cancelEditing,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (editSession != null) {
            ScoreEditStaffControls(
                trackOptions = trackOptions,
                staffMode = staffMode,
                selectedTrackIndexes = selectedTrackIndexes,
                onStaffModeChange = { mode ->
                    staffMode = mode
                    notationLayout = ScoreNotationLayout.SEPARATE_PARTS
                    selectedNoteId = null
                },
                onTrackChange = { trackIndex ->
                    selectedTrackMask = 1 shl trackIndex
                    staffMode = ScoreStaffMode.SELECTED_PARTS
                    notationLayout = ScoreNotationLayout.SEPARATE_PARTS
                    selectedNoteId = null
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (viewMode) {
                ScoreViewMode.OCR -> key(
                    ocrScore.displayScore,
                    staffMode,
                    notationLayout,
                    partColorMode,
                    currentMergedScores?.get(BundledScoreVariant.OCR),
                    selectedTrackIndexes,
                    editVariant == BundledScoreVariant.OCR,
                    selectedNoteId
                ) {
                    AlphaTabScore(
                        loadedScore = ocrScore,
                        staffMode = staffMode,
                        notationLayout = notationLayout,
                        partColorMode = partColorMode,
                        playbackIndicatorMode = playbackIndicatorMode,
                        followScrollEnabled = followScrollEnabled,
                        playbackController = playbackController,
                        mergedDisplayScore = currentMergedScores?.get(BundledScoreVariant.OCR),
                        selectedTrackIndexes = selectedTrackIndexes,
                        editMode = editVariant == BundledScoreVariant.OCR,
                        canonicalNotes = editSession?.notes.orEmpty(),
                        selectedNoteId = selectedNoteId,
                        onNoteSelected = { noteId ->
                            if (noteId == null) editMessage = editMapError
                            else {
                                selectedNoteId = noteId
                                editMessage = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScoreViewMode.MIDI -> key(
                    midiScore.displayScore,
                    staffMode,
                    notationLayout,
                    partColorMode,
                    currentMergedScores?.get(BundledScoreVariant.MIDI),
                    selectedTrackIndexes,
                    editVariant == BundledScoreVariant.MIDI,
                    selectedNoteId
                ) {
                    AlphaTabScore(
                        loadedScore = midiScore,
                        staffMode = staffMode,
                        notationLayout = notationLayout,
                        partColorMode = partColorMode,
                        playbackIndicatorMode = playbackIndicatorMode,
                        followScrollEnabled = followScrollEnabled,
                        playbackController = playbackController,
                        mergedDisplayScore = currentMergedScores?.get(BundledScoreVariant.MIDI),
                        selectedTrackIndexes = selectedTrackIndexes,
                        editMode = editVariant == BundledScoreVariant.MIDI,
                        canonicalNotes = editSession?.notes.orEmpty(),
                        selectedNoteId = selectedNoteId,
                        onNoteSelected = { noteId ->
                            if (noteId == null) editMessage = editMapError
                            else {
                                selectedNoteId = noteId
                                editMessage = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScoreViewMode.COMPARE -> Column(modifier = Modifier.fillMaxSize()) {
                    ScoreComparePane(
                        label = stringResource(R.string.score_source_ocr),
                        loadedScore = ocrScore,
                        staffMode = staffMode,
                        notationLayout = notationLayout,
                        partColorMode = partColorMode,
                        playbackIndicatorMode = playbackIndicatorMode,
                        followScrollEnabled = followScrollEnabled,
                        playbackController = playbackController,
                        mergedDisplayScore = currentMergedScores?.get(BundledScoreVariant.OCR),
                        selectedTrackIndexes = selectedTrackIndexes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    ScoreComparePane(
                        label = stringResource(R.string.score_source_midi),
                        loadedScore = midiScore,
                        staffMode = staffMode,
                        notationLayout = notationLayout,
                        partColorMode = partColorMode,
                        playbackIndicatorMode = playbackIndicatorMode,
                        followScrollEnabled = followScrollEnabled,
                        playbackController = playbackController,
                        mergedDisplayScore = currentMergedScores?.get(BundledScoreVariant.MIDI),
                        selectedTrackIndexes = selectedTrackIndexes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            key(playbackVariant, playbackScore.playbackScore) {
                ScorePlaybackEngine(
                    loadedScore = playbackScore,
                    soundFont = soundFont,
                    controller = playbackController,
                    onStatusChange = { playbackStatus = it },
                    endBehavior = playbackEndBehavior,
                    modifier = Modifier.size(1.dp)
                )
            }

            overlayContent()

        }
        if (editSession != null) {
            ScoreNoteEditor(
                note = selectedNote,
                enabled = !editBusy,
                onLowerPitch = {
                    selectedNoteId?.let { noteId ->
                        runEditCommand { changePitch(noteId, -1) }
                    }
                },
                onRaisePitch = {
                    selectedNoteId?.let { noteId ->
                        runEditCommand { changePitch(noteId, 1) }
                    }
                },
                onApplyLyric = { lyric ->
                    selectedNoteId?.let { noteId ->
                        runEditCommand { setLyrics(noteId, lyric) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScoreEditControls(
    editing: Boolean,
    canStart: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isDirty: Boolean,
    busy: Boolean,
    message: String?,
    onStart: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!editing) {
                    Button(onClick = onStart, enabled = canStart) {
                        Text(stringResource(R.string.score_edit))
                    }
                    Text(
                        text = stringResource(
                            if (canStart) R.string.score_edit_start_hint
                            else R.string.score_edit_compare_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.score_edit_voice_hint),
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedButton(onClick = onUndo, enabled = canUndo && !busy) {
                        Text(stringResource(R.string.score_edit_undo))
                    }
                    OutlinedButton(onClick = onRedo, enabled = canRedo && !busy) {
                        Text(stringResource(R.string.score_edit_redo))
                    }
                    Button(onClick = onSave, enabled = isDirty && !busy) {
                        Text(stringResource(R.string.score_edit_save))
                    }
                    OutlinedButton(onClick = onCancel, enabled = !busy) {
                        Text(stringResource(R.string.score_edit_cancel))
                    }
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreNoteEditor(
    note: ScoreNoteRef?,
    enabled: Boolean,
    onLowerPitch: () -> Unit,
    onRaisePitch: () -> Unit,
    onApplyLyric: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (note == null) {
            Text(
                text = stringResource(R.string.score_edit_select_note),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            return@Surface
        }

        var lyric by remember(note.id, note.lyric) { mutableStateOf(note.lyric.orEmpty()) }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.score_edit_selected_note,
                    scorePitchName(note.midiPitch),
                    note.measureIndex + 1
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onLowerPitch, enabled = enabled) {
                    Text(stringResource(R.string.score_edit_pitch_down))
                }
                OutlinedButton(onClick = onRaisePitch, enabled = enabled) {
                    Text(stringResource(R.string.score_edit_pitch_up))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = lyric,
                    onValueChange = { lyric = it },
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.score_edit_lyric)) },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { onApplyLyric(lyric) }, enabled = enabled) {
                    Text(stringResource(R.string.score_edit_apply_lyric))
                }
            }
        }
    }
}

@Composable
private fun ScoreEditStaffControls(
    trackOptions: List<ScoreTrackOption>,
    staffMode: ScoreStaffMode,
    selectedTrackIndexes: Set<Int>,
    onStaffModeChange: (ScoreStaffMode) -> Unit,
    onTrackChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trackOptions.size <= 1) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_edit_staff_display),
                    style = MaterialTheme.typography.labelLarge
                )
                ScoreModeChip(
                    selected = staffMode == ScoreStaffMode.SELECTED_PARTS,
                    onClick = { onStaffModeChange(ScoreStaffMode.SELECTED_PARTS) },
                    label = stringResource(R.string.score_layout_separate)
                )
                ScoreModeChip(
                    selected = staffMode == ScoreStaffMode.ALL_STAVES,
                    onClick = { onStaffModeChange(ScoreStaffMode.ALL_STAVES) },
                    label = stringResource(R.string.score_staff_all)
                )
            }
            if (staffMode == ScoreStaffMode.SELECTED_PARTS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.score_edit_part_select),
                        style = MaterialTheme.typography.labelLarge
                    )
                    trackOptions.forEach { option ->
                        ScoreModeChip(
                            selected = option.index in selectedTrackIndexes,
                            onClick = { onTrackChange(option.index) },
                            label = option.label
                        )
                    }
                }
            }
        }
    }
}

private fun scorePitchName(midiPitch: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[Math.floorMod(midiPitch, 12)] + (Math.floorDiv(midiPitch, 12) - 1)
}

@Composable
private fun ScoreTrackControls(
    trackOptions: List<ScoreTrackOption>,
    expanded: Boolean,
    staffMode: ScoreStaffMode,
    notationLayout: ScoreNotationLayout,
    partColorMode: ScorePartColorMode,
    selectedTrackIndexes: Set<Int>,
    mutedTrackIndexes: Set<Int>,
    onExpandedChange: (Boolean) -> Unit,
    onStaffModeChange: (ScoreStaffMode) -> Unit,
    onNotationLayoutChange: (ScoreNotationLayout) -> Unit,
    onPartColorModeChange: (ScorePartColorMode) -> Unit,
    onTrackVisibilityToggle: (Int) -> Unit,
    onTrackSoundToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (trackOptions.size <= 1) return

    ScoreSettingsCard(
        icon = RhythmIcons.Equalizer,
        title = stringResource(R.string.score_part_settings),
        modifier = modifier,
        trailingContent = {
            OutlinedButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) RhythmIcons.ExpandLess else RhythmIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(
                        if (expanded) R.string.score_controls_collapse
                        else R.string.score_controls_expand
                    ),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        },
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_notation_layout),
                    style = MaterialTheme.typography.labelLarge
                )
                ScoreModeChip(
                    selected = notationLayout == ScoreNotationLayout.SEPARATE_PARTS,
                    onClick = { onNotationLayoutChange(ScoreNotationLayout.SEPARATE_PARTS) },
                    label = stringResource(R.string.score_layout_separate)
                )
                ScoreModeChip(
                    selected = notationLayout == ScoreNotationLayout.MERGED_STAVES,
                    onClick = { onNotationLayoutChange(ScoreNotationLayout.MERGED_STAVES) },
                    label = stringResource(R.string.score_layout_merged)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_part_color),
                    style = MaterialTheme.typography.labelLarge
                )
                ScoreModeChip(
                    selected = partColorMode == ScorePartColorMode.DEFAULT,
                    onClick = { onPartColorModeChange(ScorePartColorMode.DEFAULT) },
                    label = stringResource(R.string.score_part_color_default)
                )
                ScoreModeChip(
                    selected = partColorMode == ScorePartColorMode.ENHANCED,
                    onClick = { onPartColorModeChange(ScorePartColorMode.ENHANCED) },
                    label = stringResource(R.string.score_part_color_enhanced)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_staff_display),
                    style = MaterialTheme.typography.labelLarge
                )
                ScoreModeChip(
                    selected = staffMode == ScoreStaffMode.ALL_STAVES,
                    onClick = { onStaffModeChange(ScoreStaffMode.ALL_STAVES) },
                    label = stringResource(R.string.score_staff_all)
                )
                ScoreModeChip(
                    selected = staffMode == ScoreStaffMode.SELECTED_PARTS,
                    onClick = { onStaffModeChange(ScoreStaffMode.SELECTED_PARTS) },
                    label = stringResource(R.string.score_staff_current)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_part_select),
                    style = MaterialTheme.typography.labelLarge
                )
                trackOptions.forEach { option ->
                    ScoreModeChip(
                        selected = option.index in selectedTrackIndexes,
                        onClick = { onTrackVisibilityToggle(option.index) },
                        label = option.label
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.score_part_sound),
                    style = MaterialTheme.typography.labelLarge
                )
                trackOptions.forEach { option ->
                    val isAudible = option.index !in mutedTrackIndexes
                    FilterChip(
                        selected = isAudible,
                        onClick = { onTrackSoundToggle(option.index) },
                        label = {
                            Text(
                                stringResource(
                                    if (isAudible) {
                                        R.string.score_part_sound_on
                                    } else {
                                        R.string.score_part_sound_muted
                                    },
                                    option.label
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScorePlaybackControls(
    title: String?,
    subtitle: String?,
    onBackClick: (() -> Unit)?,
    onChorusClick: (() -> Unit)?,
    onHelpClick: (() -> Unit)?,
    viewMode: ScoreViewMode,
    playbackVariant: BundledScoreVariant,
    status: ScorePlaybackStatus,
    indicatorMode: ScorePlaybackIndicatorMode,
    followScrollEnabled: Boolean,
    metronomeEnabled: Boolean,
    sourceBpm: Int,
    targetBpm: Int,
    hasCustomBpm: Boolean,
    onPlaybackVariantChange: (BundledScoreVariant) -> Unit,
    onIndicatorModeChange: (ScorePlaybackIndicatorMode) -> Unit,
    onFollowScrollChange: (Boolean) -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onTargetBpmChange: (Int) -> Unit,
    onResetBpm: () -> Unit,
    endBehavior: ScorePlaybackEndBehavior,
    onEndBehaviorChange: (ScorePlaybackEndBehavior) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    interactionEnabled: Boolean,
    settingsContent: @Composable () -> Unit,
    settingsFooterContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val statusLabel = stringResource(
        when (status) {
            ScorePlaybackStatus.PREPARING -> R.string.score_playback_preparing
            ScorePlaybackStatus.READY -> R.string.score_playback_ready
            ScorePlaybackStatus.PLAYING -> R.string.score_playback_playing
            ScorePlaybackStatus.PAUSED -> R.string.score_playback_paused
            ScorePlaybackStatus.ERROR -> R.string.score_playback_error
        }
    )
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackClick != null) {
                FilledTonalIconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        RhythmIcons.Back,
                        contentDescription = stringResource(R.string.score_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title ?: stringResource(R.string.catalog_scores),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle ?: statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status == ScorePlaybackStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onChorusClick != null) {
                FilledTonalIconButton(
                    onClick = onChorusClick,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Icon(
                        RhythmIcons.MusicNote,
                        contentDescription = stringResource(R.string.score_chorus),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (onHelpClick != null) {
                FilledTonalIconButton(
                    onClick = onHelpClick,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Icon(
                        MaterialSymbolIcon("help", filled = true),
                        contentDescription = stringResource(R.string.chorus_recording_help_open),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            FilledIconButton(
                onClick = onPlayPause,
                enabled = interactionEnabled && status in setOf(
                    ScorePlaybackStatus.READY, ScorePlaybackStatus.PLAYING, ScorePlaybackStatus.PAUSED
                ),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (status == ScorePlaybackStatus.PLAYING) RhythmIcons.Pause else RhythmIcons.Play,
                    contentDescription = stringResource(
                        if (status == ScorePlaybackStatus.PLAYING) R.string.score_pause else R.string.score_play
                    ),
                    modifier = Modifier.size(25.dp),
                )
            }
            FilledTonalIconButton(
                onClick = onStop,
                enabled = interactionEnabled && status in setOf(
                    ScorePlaybackStatus.PLAYING, ScorePlaybackStatus.PAUSED
                ),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    RhythmIcons.Stop,
                    contentDescription = stringResource(R.string.score_stop),
                    modifier = Modifier.size(23.dp),
                )
            }
            FilledTonalIconButton(
                onClick = { showSettings = true },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(
                    RhythmIcons.Settings,
                    contentDescription = stringResource(R.string.score_settings),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                RhythmIcons.SettingsFilled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column {
                        Text(
                            stringResource(R.string.score_settings),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        title?.let {
                            Text(
                                text = listOfNotNull(it, subtitle).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                settingsContent()
                if (viewMode == ScoreViewMode.COMPARE) {
                    ScoreSettingsCard(
                        icon = RhythmIcons.MusicNote,
                        title = stringResource(R.string.score_playback_source),
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ScoreModeChip(playbackVariant == BundledScoreVariant.OCR, { onPlaybackVariantChange(BundledScoreVariant.OCR) }, stringResource(R.string.score_source_ocr), interactionEnabled)
                            ScoreModeChip(playbackVariant == BundledScoreVariant.MIDI, { onPlaybackVariantChange(BundledScoreVariant.MIDI) }, stringResource(R.string.score_source_midi), interactionEnabled)
                        }
                    }
                }
                ScoreSettingsCard(
                    icon = RhythmIcons.Tune,
                    title = stringResource(R.string.score_playback_indicator),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScoreModeChip(indicatorMode == ScorePlaybackIndicatorMode.LINE, { onIndicatorModeChange(ScorePlaybackIndicatorMode.LINE) }, stringResource(R.string.score_playback_indicator_default), interactionEnabled)
                        ScoreModeChip(indicatorMode == ScorePlaybackIndicatorMode.PULSE, { onIndicatorModeChange(ScorePlaybackIndicatorMode.PULSE) }, stringResource(R.string.score_playback_indicator_pulse), interactionEnabled)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Text(
                        stringResource(R.string.score_playback_end),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScoreModeChip(endBehavior == ScorePlaybackEndBehavior.PAUSE_AT_END, { onEndBehaviorChange(ScorePlaybackEndBehavior.PAUSE_AT_END) }, stringResource(R.string.score_pause_at_end), interactionEnabled)
                        ScoreModeChip(endBehavior == ScorePlaybackEndBehavior.LOOP_CURRENT, { onEndBehaviorChange(ScorePlaybackEndBehavior.LOOP_CURRENT) }, stringResource(R.string.score_loop_current), interactionEnabled)
                    }
                }
                ScoreSettingsCard(
                    icon = RhythmIcons.Actions.SwapVert,
                    title = stringResource(R.string.score_playback_scroll),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScoreModeChip(
                            selected = followScrollEnabled,
                            onClick = { onFollowScrollChange(true) },
                            label = stringResource(R.string.score_playback_scroll_follow),
                            enabled = interactionEnabled,
                        )
                        ScoreModeChip(
                            selected = !followScrollEnabled,
                            onClick = { onFollowScrollChange(false) },
                            label = stringResource(R.string.score_playback_scroll_off),
                            enabled = interactionEnabled,
                        )
                    }
                }
                ScorePlaybackTempoCard(
                    sourceBpm = sourceBpm,
                    targetBpm = targetBpm,
                    hasCustomBpm = hasCustomBpm,
                    enabled = interactionEnabled,
                    onTargetBpmChange = onTargetBpmChange,
                    onResetBpm = onResetBpm,
                )
                ScoreSettingsCard(
                    icon = RhythmIcons.Player.Timer,
                    title = stringResource(R.string.score_metronome),
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScoreModeChip(
                            selected = metronomeEnabled,
                            onClick = { onMetronomeEnabledChange(true) },
                            label = stringResource(R.string.score_metronome_on),
                            enabled = interactionEnabled,
                        )
                        ScoreModeChip(
                            selected = !metronomeEnabled,
                            onClick = { onMetronomeEnabledChange(false) },
                            label = stringResource(R.string.score_metronome_off),
                            enabled = interactionEnabled,
                        )
                    }
                }
                settingsFooterContent()
            }
        }
    }
}

@Composable
private fun ScorePlaybackTempoCard(
    sourceBpm: Int,
    targetBpm: Int,
    hasCustomBpm: Boolean,
    enabled: Boolean,
    onTargetBpmChange: (Int) -> Unit,
    onResetBpm: () -> Unit,
) {
    var input by remember(targetBpm) { mutableStateOf(targetBpm.toString()) }
    val parsedInput = input.toIntOrNull()
    val inputIsValid = parsedInput != null &&
        parsedInput in MIN_SCORE_PLAYBACK_BPM..MAX_SCORE_PLAYBACK_BPM

    ScoreSettingsCard(
        icon = RhythmIcons.Player.Speed,
        title = stringResource(R.string.score_playback_speed),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        input = value
                        value.toIntOrNull()
                            ?.takeIf { it in MIN_SCORE_PLAYBACK_BPM..MAX_SCORE_PLAYBACK_BPM }
                            ?.let(onTargetBpmChange)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                isError = input.isNotEmpty() && !inputIsValid,
                label = { Text(stringResource(R.string.score_playback_speed_target)) },
                suffix = { Text("BPM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            TextButton(
                onClick = onResetBpm,
                enabled = enabled && hasCustomBpm,
            ) {
                Text(stringResource(R.string.score_playback_speed_reset))
            }
        }
        Text(
            text = stringResource(R.string.score_playback_speed_original, sourceBpm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(-5, -1, 1, 5).forEach { delta ->
                OutlinedButton(
                    onClick = {
                        onTargetBpmChange(
                            (targetBpm + delta).coerceIn(
                                MIN_SCORE_PLAYBACK_BPM,
                                MAX_SCORE_PLAYBACK_BPM,
                            )
                        )
                    },
                    enabled = enabled,
                ) {
                    Text(if (delta > 0) "+$delta" else delta.toString())
                }
            }
        }
        if (input.isNotEmpty() && !inputIsValid) {
            Text(
                text = stringResource(
                    R.string.score_playback_speed_range,
                    MIN_SCORE_PLAYBACK_BPM,
                    MAX_SCORE_PLAYBACK_BPM,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ScoreSettingsCard(
    icon: io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon,
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                trailingContent()
            }
            content()
        }
    }
}

@Composable
private fun ScoreComparePane(
    label: String,
    loadedScore: LoadedScore,
    staffMode: ScoreStaffMode,
    notationLayout: ScoreNotationLayout,
    partColorMode: ScorePartColorMode,
    playbackIndicatorMode: ScorePlaybackIndicatorMode,
    followScrollEnabled: Boolean,
    playbackController: ScorePlaybackController,
    mergedDisplayScore: MergedDisplayScore?,
    selectedTrackIndexes: Set<Int>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        key(
            loadedScore.displayScore,
            staffMode,
            notationLayout,
            partColorMode,
            mergedDisplayScore,
            selectedTrackIndexes
        ) {
            AlphaTabScore(
                loadedScore = loadedScore,
                staffMode = staffMode,
                notationLayout = notationLayout,
                partColorMode = partColorMode,
                playbackIndicatorMode = playbackIndicatorMode,
                followScrollEnabled = followScrollEnabled,
                playbackController = playbackController,
                mergedDisplayScore = mergedDisplayScore,
                selectedTrackIndexes = selectedTrackIndexes,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun ScoreLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.score_loading),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ScoreError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(PaddingValues(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.score_load_error_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.score_load_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.score_retry))
        }
    }
}

internal fun isAlphaTabRenderSizeReady(
    displayAttached: Boolean,
    outerScrollAttached: Boolean,
    outerScrollWidth: Int,
    outerScrollHeight: Int,
): Boolean = displayAttached &&
    outerScrollAttached &&
    outerScrollWidth > 0 &&
    outerScrollHeight > 0

private class AlphaTabRenderReadiness(
    private val displayView: AlphaTabView,
    private val outerScroll: View,
    private val tracksToRender: List<Track>,
    private val onReady: (viewportHeightPx: Int) -> Unit = {},
) {
    private var completed = false
    private var released = false
    private var renderPosted = false

    private val renderRunnable = Runnable {
        renderPosted = false
        if (released || completed || !isReady()) return@Runnable

        completed = true
        removeListeners()
        if (BuildConfig.DEBUG) {
            Log.d(
                SCORE_DISPLAY_TAG,
                "visible AlphaTab ready outer=${outerScroll.measuredWidth}x${outerScroll.measuredHeight} " +
                    "tracks=${tracksToRender.size}",
            )
        }
        onReady(outerScroll.measuredHeight)
        displayView.tracks = tracksToRender
    }

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestRenderIfReady()
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            requestRenderIfReady()
        }

        override fun onViewDetachedFromWindow(view: View) {
            release()
        }
    }

    fun start() {
        outerScroll.addOnLayoutChangeListener(layoutListener)
        outerScroll.addOnAttachStateChangeListener(attachListener)
        if (!requestRenderIfReady() && BuildConfig.DEBUG) {
            Log.d(
                SCORE_DISPLAY_TAG,
                "waiting for visible AlphaTab outer=${outerScroll.measuredWidth}x${outerScroll.measuredHeight} " +
                    "tracks=${tracksToRender.size}",
            )
        }
    }

    fun release() {
        if (released) return
        released = true
        displayView.removeCallbacks(renderRunnable)
        removeListeners()
    }

    private fun requestRenderIfReady(): Boolean {
        if (released || completed || renderPosted || !isReady()) return false
        renderPosted = displayView.post(renderRunnable)
        return renderPosted
    }

    private fun isReady(): Boolean = isAlphaTabRenderSizeReady(
        displayAttached = displayView.isAttachedToWindow,
        outerScrollAttached = outerScroll.isAttachedToWindow,
        outerScrollWidth = outerScroll.measuredWidth,
        outerScrollHeight = outerScroll.measuredHeight,
    )

    private fun removeListeners() {
        outerScroll.removeOnLayoutChangeListener(layoutListener)
        outerScroll.removeOnAttachStateChangeListener(attachListener)
    }
}

private data class AlphaTabScoreViewState(
    val playbackOverlay: ScorePlaybackOverlayView,
    val renderReadiness: AlphaTabRenderReadiness,
    val playbackScrollHandler: ScorePlaybackScrollHandler?,
    val scrollRenderRecovery: ScoreScrollRenderRecovery,
    val renderSurface: View,
    val renderSurfaceLayoutListener: View.OnLayoutChangeListener?,
)

@Composable
private fun AlphaTabScore(
    loadedScore: LoadedScore,
    staffMode: ScoreStaffMode,
    notationLayout: ScoreNotationLayout,
    partColorMode: ScorePartColorMode,
    playbackIndicatorMode: ScorePlaybackIndicatorMode,
    followScrollEnabled: Boolean,
    playbackController: ScorePlaybackController,
    mergedDisplayScore: MergedDisplayScore?,
    selectedTrackIndexes: Set<Int>,
    editMode: Boolean = false,
    canonicalNotes: List<ScoreNoteRef> = emptyList(),
    selectedNoteId: String? = null,
    onNoteSelected: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (notationLayout == ScoreNotationLayout.MERGED_STAVES && mergedDisplayScore == null) {
        ScoreLoading(modifier)
        return
    }
    val displayScore = mergedDisplayScore?.score ?: loadedScore.displayScore
    val allTracks = displayScore.tracks.toList()
    val tracksToRender = when (notationLayout) {
        ScoreNotationLayout.MERGED_STAVES -> allTracks
        ScoreNotationLayout.SEPARATE_PARTS -> when (staffMode) {
            ScoreStaffMode.ALL_STAVES -> allTracks
            ScoreStaffMode.SELECTED_PARTS -> allTracks
                .filter { it.index.toInt() in selectedTrackIndexes }
                .ifEmpty { allTracks.take(1) }
        }
    }
    applyScorePartColors(
        tracks = tracksToRender,
        notationLayout = notationLayout,
        colorMode = partColorMode,
        voiceColorIndexesByTrack = mergedDisplayScore?.colorPartIndexesByTrack
            ?: loadedScore.displayPartColorIndexesByTrack,
    )
    if (editMode && selectedNoteId != null) {
        val selectedColor = checkNotNull(AlphaTabColor.fromJson("#4f6bff"))
        tracksToRender.forEach { track ->
            track.staves.forEach { staff ->
                staff.bars.forEach { bar ->
                    bar.voices.forEach { voice ->
                        voice.beats.forEach { beat ->
                            beat.notes.forEach { note ->
                                if (ScoreSourceMap.findNoteId(note, canonicalNotes) == selectedNoteId) {
                                    note.style = (note.style ?: NoteStyle()).apply {
                                        NoteSubElement.values().forEach { element ->
                                            colors.set(element, selectedColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Surface(
        modifier = modifier,
        // A score is paper, not a themed surface. alphaTab's glyph palette is authored for
        // dark ink and becomes unreadable when a dark app surface shows through its canvas.
        color = androidx.compose.ui.graphics.Color.White,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                AlphaTabView(context, null).apply {
                    setBackgroundColor(AndroidColor.WHITE)
                    val displayView = this
                    val playbackOverlay = ScorePlaybackOverlayView(context)
                    api.settings.player.playerMode = if (editMode) {
                        PlayerMode.Disabled
                    } else {
                        PlayerMode.EnabledExternalMedia
                    }
                    api.settings.player.enableCursor =
                        !editMode
                    api.settings.player.enableAnimatedBeatCursor = !editMode
                    // A custom handler below performs smooth vertical following. Continuous keeps
                    // alphaTab's cursor callbacks enabled without installing its faulty Android
                    // Smooth overflow calculation.
                    api.settings.player.scrollMode = if (editMode) ScrollMode.Off else ScrollMode.Continuous
                    api.settings.player.enableElementHighlighting = false
                    api.settings.player.enableUserInteraction = editMode
                    // Playback pulse markers and edit hit-testing both need note-head bounds.
                    api.settings.core.includeNoteBounds = true
                    barCursorFillColor = AndroidColor.TRANSPARENT
                    // The overlay owns the playback line; alphaTab's Android View Animation can
                    // stop drawing near the end of a long vertically rendered score.
                    beatCursorFillColor = AndroidColor.TRANSPARENT
                    if (!editMode) {
                        api.playerReady.on {
                            playbackController.onDisplayPlayerReady(displayView)
                        }
                    }
                    // alphaTab renders secondary voices with 100/255 alpha by default.
                    // Explicit per-voice styles carry enhanced colors; this fallback keeps
                    // any unstyled secondary glyph black instead of gray.
                    val scoreInk = AlphaTabColor(0.0, 0.0, 0.0, 255.0)
                    api.settings.display.resources.staffLineColor = scoreInk
                    api.settings.display.resources.barSeparatorColor = scoreInk
                    api.settings.display.resources.mainGlyphColor = scoreInk
                    api.settings.display.resources.secondaryGlyphColor = scoreInk
                    api.settings.display.resources.scoreInfoColor = scoreInk
                    if (editMode) {
                        // On touch screens alphaTab only emits noteMouseDown when the finger
                        // lands inside the small note-head bounds. Treat the wider beat hitbox
                        // as a fallback so a normal finger tap can still select its note. For a
                        // chord, the exact noteMouseDown callback below runs afterwards and wins.
                        api.beatMouseDown.on { beat ->
                            val noteId = beat.notes.toList().firstNotNullOfOrNull { note ->
                                ScoreSourceMap.findNoteId(note, canonicalNotes)
                            }
                            Log.i(
                                SCORE_EDIT_TAG,
                                "beat hit track=${beat.voice.bar.staff.track.index.toInt()} " +
                                    "bar=${beat.voice.bar.index.toInt()} " +
                                    "voice=${beat.voice.index.toInt()} beat=${beat.index.toInt()} " +
                                    "canonical=$noteId"
                            )
                            if (noteId != null) post { onNoteSelected(noteId) }
                        }
                        api.noteMouseDown.on { note ->
                            val noteId = ScoreSourceMap.findNoteId(note, canonicalNotes)
                            Log.i(
                                SCORE_EDIT_TAG,
                                "note hit track=${note.beat.voice.bar.staff.track.index.toInt()} " +
                                    "bar=${note.beat.voice.bar.index.toInt()} " +
                                    "voice=${note.beat.voice.index.toInt()} " +
                                    "beat=${note.beat.index.toInt()} note=${note.index.toInt()} " +
                                    "canonical=$noteId"
                            )
                            post { onNoteSelected(noteId) }
                        }
                    }
                    val outerScroll = findViewById<View>(net.alphatab.R.id.outerScroll)
                    val innerScroll = findViewById<ScrollView>(net.alphatab.R.id.innerScroll)
                    val renderWrapper =
                        findViewById<RelativeLayout>(net.alphatab.R.id.renderWrapper)
                    val renderSurface = findViewById<View>(net.alphatab.R.id.renderSurface)
                    val scrollRenderRecovery = ScoreScrollRenderRecovery(
                        renderSurface = renderSurface,
                        scrollView = innerScroll,
                    )
                    val playbackScrollHandler = if (!editMode) {
                        ScorePlaybackScrollHandler(
                            displayView = displayView,
                            scrollView = innerScroll,
                            initiallyEnabled = followScrollEnabled,
                        ).also { handler ->
                            api.customCursorHandler = ScorePlaybackCursorHandler(playbackOverlay)
                            api.customScrollHandler = handler
                        }
                    } else {
                        null
                    }
                    api.updateSettings()
                    var renderSurfaceLayoutListener: View.OnLayoutChangeListener? = null
                    if (!editMode) {
                        renderSurfaceLayoutListener = View.OnLayoutChangeListener {
                                _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                            if (bottom - top != oldBottom - oldTop) {
                                updateScorePlaybackCanvasLayout(
                                    renderSurface = renderSurface,
                                    renderWrapper = renderWrapper,
                                    playbackOverlay = playbackOverlay,
                                    innerScroll = innerScroll,
                                )
                            }
                        }
                        outerScroll.setBackgroundColor(AndroidColor.WHITE)
                        innerScroll.setBackgroundColor(AndroidColor.WHITE)
                        renderWrapper.setBackgroundColor(AndroidColor.WHITE)
                        renderSurface.setBackgroundColor(AndroidColor.WHITE)
                        api.postRenderFinished.on {
                            // Adding a sibling while alphaTab creates its first render
                            // surface can invalidate the lazy bitmap placeholders. Wait
                            // until that surface is complete before installing the overlay.
                            renderSurface.post {
                                val overlayLayout = RelativeLayout.LayoutParams(
                                    renderSurface.width,
                                    renderSurface.height,
                                )
                                if (playbackOverlay.parent == null) {
                                    renderWrapper.addView(playbackOverlay, overlayLayout)
                                    renderSurface.addOnLayoutChangeListener(
                                        checkNotNull(renderSurfaceLayoutListener),
                                    )
                                } else {
                                    playbackOverlay.layoutParams = overlayLayout
                                }
                                updateScorePlaybackCanvasLayout(
                                    renderSurface = renderSurface,
                                    renderWrapper = renderWrapper,
                                    playbackOverlay = playbackOverlay,
                                    innerScroll = innerScroll,
                                )
                                playbackOverlay.refresh(displayView.api.renderer.boundsLookup)
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        SCORE_DISPLAY_TAG,
                                        "visible AlphaTab rendered surface=${renderSurface.width}x${renderSurface.height}",
                                    )
                                }
                            }
                        }
                    }
                    val renderReadiness = AlphaTabRenderReadiness(
                        displayView = displayView,
                        outerScroll = outerScroll,
                        tracksToRender = tracksToRender,
                        onReady = { viewportHeightPx ->
                            if (!editMode) {
                                api.settings.player.scrollOffsetY = scoreFollowScrollOffset(
                                    viewportHeightPx = viewportHeightPx,
                                    density = resources.displayMetrics.density,
                                )
                                api.updateSettings()
                            }
                        },
                    )
                    tag = AlphaTabScoreViewState(
                        playbackOverlay = playbackOverlay,
                        renderReadiness = renderReadiness,
                        playbackScrollHandler = playbackScrollHandler,
                        scrollRenderRecovery = scrollRenderRecovery,
                        renderSurface = renderSurface,
                        renderSurfaceLayoutListener = renderSurfaceLayoutListener,
                    )
                    scrollRenderRecovery.start()
                    renderReadiness.start()
                }
            },
            update = { displayView ->
                if (!editMode) {
                    val viewState = displayView.tag as AlphaTabScoreViewState
                    val playbackOverlay = viewState.playbackOverlay
                    viewState.playbackScrollHandler?.setEnabled(followScrollEnabled)
                    playbackOverlay.setMode(playbackIndicatorMode)
                    if (playbackIndicatorMode == ScorePlaybackIndicatorMode.LINE) {
                        playbackOverlay.showBeats(
                            emptyList(),
                            displayView.api.renderer.boundsLookup,
                        )
                    }
                    playbackController.attachDisplay(
                        view = displayView,
                        mode = playbackIndicatorMode,
                        onPulsePositions = { positions ->
                            val pulseBeats = findScorePlaybackHighlightBeats(
                                tracks = tracksToRender,
                                notationLayout = notationLayout,
                                activePositions = positions,
                                voiceSourcePartIndexesByTrack =
                                    mergedDisplayScore?.sourcePartIndexesByTrack.orEmpty(),
                            )
                            playbackOverlay.showBeats(
                                pulseBeats,
                                displayView.api.renderer.boundsLookup,
                            )
                        }
                    )
                }
            },
            onRelease = { view ->
                (view.tag as? AlphaTabScoreViewState)?.let { state ->
                    state.renderReadiness.release()
                    state.playbackScrollHandler?.close()
                    state.scrollRenderRecovery.close()
                    if (state.renderSurfaceLayoutListener != null) {
                        state.renderSurface.removeOnLayoutChangeListener(
                            state.renderSurfaceLayoutListener,
                        )
                    }
                }
                playbackController.detachDisplay(view)
                view.api.destroy()
            }
        )
    }
}

private fun updateScorePlaybackCanvasLayout(
    renderSurface: View,
    renderWrapper: RelativeLayout,
    playbackOverlay: ScorePlaybackOverlayView,
    innerScroll: ScrollView,
) {
    val width = renderSurface.width
    val height = renderSurface.height
    if (width <= 0 || height <= 0) return
    playbackOverlay.layoutParams = RelativeLayout.LayoutParams(width, height)
    renderWrapper.minimumHeight = scoreFollowContentMinHeight(
        scoreHeightPx = height,
        viewportHeightPx = innerScroll.height,
    )
}

@Composable
private fun ScorePlaybackEngine(
    loadedScore: LoadedScore,
    soundFont: ByteArray,
    controller: ScorePlaybackController,
    onStatusChange: (ScorePlaybackStatus) -> Unit,
    endBehavior: ScorePlaybackEndBehavior,
    modifier: Modifier = Modifier
) {
    val currentEndBehavior by rememberUpdatedState(endBehavior)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AlphaTabView(context, null).apply {
                val playerView = this
                var playerIsReady = false
                controller.attach(playerView, loadedScore.playbackScore)
                val tempoAutomationCount = loadedScore.playbackScore.masterBars
                    .toList()
                    .sumOf { it.tempoAutomations.length.toInt() }
                Log.i(
                    SCORE_PLAYBACK_TAG,
                    "playback score loaded: tempoAutomations=$tempoAutomationCount"
                )
                val playbackSettings = Settings().apply {
                    player.playerMode = PlayerMode.EnabledAutomatic
                    player.enableCursor = false
                    player.enableUserInteraction = false
                    // alphaTab's 500 ms default only primes about 90 ms of stereo PCM on
                    // Android. Bluetooth routes on real devices can need several times that,
                    // otherwise the worker immediately underruns and the score is silent.
                    player.bufferTimeInMilliseconds = SCORE_PLAYBACK_BUFFER_MS
                }
                settings = playbackSettings
                api.playerReady.on {
                    playerIsReady = true
                    api.masterVolume = 1.0
                    controller.onPlayerReady(playerView)
                    val trackMix = loadedScore.playbackScore.tracks.toList().joinToString {
                        "${it.index.toInt()}:volume=${it.playbackInfo.volume},mute=${it.playbackInfo.isMute}"
                    }
                    Log.i(
                        SCORE_PLAYBACK_TAG,
                        "player ready: masterVolume=${api.masterVolume}, tracks=[$trackMix]"
                    )
                    post { onStatusChange(ScorePlaybackStatus.READY) }
                }
                api.soundFontLoaded.on {
                    Log.i(SCORE_PLAYBACK_TAG, "soundfont loaded (${soundFont.size} bytes)")
                }
                api.playerStateChanged.on { event ->
                    val nextStatus = when (event.state) {
                        PlayerState.Playing -> {
                            controller.onPlayerStarted(playerView)
                            ScorePlaybackStatus.PLAYING
                        }
                        PlayerState.Paused -> if (playerIsReady) {
                            controller.onPlayerPaused(playerView)
                            ScorePlaybackStatus.PAUSED
                        } else {
                            ScorePlaybackStatus.PREPARING
                        }
                    }
                    Log.i(SCORE_PLAYBACK_TAG, "state=${event.state}")
                    post { onStatusChange(nextStatus) }
                }
                api.playerPositionChanged.on { event ->
                    controller.onPlayerPositionChanged(
                        view = playerView,
                        tick = event.currentTick,
                        time = event.currentTime,
                        totalTime = event.endTime,
                    )
                }
                api.activeBeatsChanged.on { event ->
                    controller.onActiveBeatsChanged(
                        playerView,
                        activeScorePlaybackPositions(event.activeBeats.toList())
                    )
                }
                api.playerFinished.on {
                    controller.onPlayerFinished(playerView)
                    Log.i(
                        SCORE_PLAYBACK_TAG,
                        "player finished: tick=${api.tickPosition}, time=${api.timePosition}"
                    )
                    if (currentEndBehavior == ScorePlaybackEndBehavior.LOOP_CURRENT) {
                        post {
                            api.stop()
                            controller.playPause()
                            onStatusChange(ScorePlaybackStatus.PLAYING)
                        }
                    } else {
                        post { onStatusChange(ScorePlaybackStatus.READY) }
                    }
                }
                api.updateSettings()
                post {
                    tracks = loadedScore.playbackScore.tracks.toList()
                    loadSoundFontWhenPlayerExists(
                        soundFont = soundFont,
                        onFailure = {
                            Log.e(SCORE_PLAYBACK_TAG, "soundfont player initialization timed out")
                            onStatusChange(ScorePlaybackStatus.ERROR)
                        }
                    )
                }
            }
        },
        onRelease = { view ->
            controller.detach(view)
            runCatching { view.api.stop() }
            view.api.destroy()
        }
    )
}

private fun AlphaTabView.loadSoundFontWhenPlayerExists(
    soundFont: ByteArray,
    attempt: Int = 0,
    onFailure: () -> Unit
) {
    val player = api.player
    if (player != null) {
        runCatching {
            player.loadSoundFont(
                Uint8Array(soundFont.asUByteArray()),
                false
            )
        }.onFailure {
            Log.e(SCORE_PLAYBACK_TAG, "soundfont load failed", it)
            onFailure()
        }
        return
    }
    if (attempt < SOUND_FONT_PLAYER_MAX_ATTEMPTS) {
        postDelayed(
            {
                loadSoundFontWhenPlayerExists(
                    soundFont = soundFont,
                    attempt = attempt + 1,
                    onFailure = onFailure
                )
            },
            SOUND_FONT_PLAYER_RETRY_MS
        )
    } else {
        onFailure()
    }
}

private const val SCORE_PLAYBACK_TAG = "ScorePlayback"
private const val SCORE_DISPLAY_TAG = "ScoreDisplay"
private const val SCORE_EDIT_TAG = "ScoreEdit"
private const val SCORE_STATS_MIN_DURATION_MS = 3_000L
private const val SOUND_FONT_PLAYER_MAX_ATTEMPTS = 60
private const val SOUND_FONT_PLAYER_RETRY_MS = 500L
private const val SCORE_PLAYBACK_BUFFER_MS = 1_500.0
private const val SCORE_METRONOME_VOLUME = 1.0
