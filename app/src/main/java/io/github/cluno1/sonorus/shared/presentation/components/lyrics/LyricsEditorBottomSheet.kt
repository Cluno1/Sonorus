/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import io.github.cluno1.sonorus.shared.presentation.components.bottomsheets.SheetAdaptiveType

import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.Player
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.features.local.data.device.DeviceLyricsCandidate
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsSyncState
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsSyncStatus
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmGroupedButton
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonWeighted
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmButtonSize
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmToggleButtonGroup
import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmToggleOption
import io.github.cluno1.sonorus.util.HapticUtils
import io.github.cluno1.sonorus.util.HapticType
import io.github.cluno1.sonorus.util.LyricsContributionAttribution
import io.github.cluno1.sonorus.util.LyricsFileUtils
import io.github.cluno1.sonorus.util.LrcTimingEditor
import io.github.cluno1.sonorus.util.LrcTimingTarget
import io.github.cluno1.sonorus.util.RhythmLyricsParser
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class LyricFormat {
    SOURCE,
    LINE_BY_LINE,
    WORD_BY_WORD
}

data class SaveLyricsInput(
    val fileName: String,
    val mimeType: String,
    val initialUri: Uri?
)

class CreateDocumentWithInitialFolder : ActivityResultContract<SaveLyricsInput, Uri?>() {
    override fun createIntent(context: Context, input: SaveLyricsInput): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)
        if (input.initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != android.app.Activity.RESULT_OK) null else intent.data
    }
}

private fun getInitialFolderUri(filePath: String?): Uri? {
    if (filePath.isNullOrBlank()) return null
    return try {
        val file = File(filePath)
        val parentFile = file.parentFile ?: return null
        val parentPath = parentFile.absolutePath

        // Check if it's primary external storage
        val primaryPrefix = "/storage/emulated/0"
        if (parentPath.startsWith(primaryPrefix, ignoreCase = true)) {
            val relativePath = parentPath.substring(primaryPrefix.length).trim('/')
            val docId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        } else {
            // Check if it's secondary external storage (e.g. micro SD card /storage/XXXX-XXXX/...)
            val storagePrefix = "/storage/"
            if (parentPath.startsWith(storagePrefix, ignoreCase = true)) {
                val subPath = parentPath.substring(storagePrefix.length)
                val parts = subPath.split('/')
                if (parts.isNotEmpty()) {
                    val volumeId = parts[0]
                    if (volumeId != "emulated") {
                        val relativePath = parts.drop(1).joinToString("/")
                        val docId = if (relativePath.isEmpty()) "$volumeId:" else "$volumeId:$relativePath"
                        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
                    } else null
                } else null
            } else null
        }
    } catch (e: Exception) {
        Log.w("LyricsEditor", "Failed to resolve initial folder URI for path: $filePath", e)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorBottomSheet(
    lyricsData: LyricsData?,
    songTitle: String,
    initialTimeOffset: Int = 0,
    song: Song? = null,
    isStreamingMode: Boolean = false,
    currentPlaybackPositionMs: Long = 0L,
    playbackDurationMs: Long = 0L,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    canSyncCatalog: Boolean = false,
    catalogSyncState: CatalogLyricsSyncState = CatalogLyricsSyncState(),
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit,
    onRefresh: () -> Unit = {},
    onOnlineRematch: () -> Unit = {},
    onChooseOtherVersion: () -> Unit = {},
    deviceLyricsCandidates: List<DeviceLyricsCandidate> = emptyList(),
    onSelectDeviceLyricsCandidate: (DeviceLyricsCandidate) -> Unit = {},
    onRestoreLocal: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onSetRepeatMode: (Int) -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSyncCatalog: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onLoadServerLyrics: () -> Unit = {},
    onEmbedInFile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val scope = rememberCoroutineScope()
    val toolsScrollState = rememberScrollState()
    var toolsExpanded by remember { mutableStateOf(false) }
    var showCandidateDialog by remember { mutableStateOf(false) }
    var showTimingHelp by remember { mutableStateOf(false) }
    val initialRepeatMode = remember { repeatMode }
    val initialPlaybackSpeed = remember { playbackSpeed }
    var repeatChangedByEditor by remember { mutableStateOf(false) }
    var speedChangedByEditor by remember { mutableStateOf(false) }
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }
    var pauseAfterStamp by remember { mutableStateOf(false) }
    var showContributionDialog by remember { mutableStateOf(false) }
    var contributionForceOverwrite by remember { mutableStateOf(false) }
    var contributionUploadInFlight by remember { mutableStateOf(false) }
    var resumePlaybackAfterContribution by remember { mutableStateOf(false) }
    var editorPlaybackSpeed by remember { mutableStateOf(playbackSpeed) }
    val latestRepeatChangedByEditor by rememberUpdatedState(repeatChangedByEditor)
    val latestSpeedChangedByEditor by rememberUpdatedState(speedChangedByEditor)
    val latestOnSetRepeatMode by rememberUpdatedState(onSetRepeatMode)
    val latestOnSetPlaybackSpeed by rememberUpdatedState(onSetPlaybackSpeed)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)
    val latestResumePlaybackAfterContribution by rememberUpdatedState(
        resumePlaybackAfterContribution,
    )

    LaunchedEffect(Unit) {
        if (initialRepeatMode != Player.REPEAT_MODE_ONE) {
            repeatChangedByEditor = true
            onSetRepeatMode(Player.REPEAT_MODE_ONE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (latestRepeatChangedByEditor) {
                latestOnSetRepeatMode(initialRepeatMode)
            }
            if (latestSpeedChangedByEditor) {
                latestOnSetPlaybackSpeed(initialPlaybackSpeed)
            }
            if (latestResumePlaybackAfterContribution) {
                latestOnPlayPause()
            }
        }
    }

    LaunchedEffect(currentPlaybackPositionMs, isPlaying, loopStartMs, loopEndMs) {
        val start = loopStartMs
        val end = loopEndMs
        if (isPlaying && start != null && end != null && currentPlaybackPositionMs >= end) {
            onSeekTo(start)
        }
    }
    
    var selectedFormat by remember(lyricsData) {
        mutableStateOf(
            if (lyricsData?.wordByWordLyrics?.isNotBlank() == true) LyricFormat.WORD_BY_WORD
            else if (lyricsData?.syncedLyrics?.isNotBlank() == true) LyricFormat.LINE_BY_LINE
            else LyricFormat.SOURCE
        )
    }
    
    val sourceForm = remember(lyricsData) {
        lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: ""
    }
    
    val lineByLineForm = remember(lyricsData) {
        lyricsData?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() }?.let {
                try {
                    RhythmLyricsParser.toLRCFormat(RhythmLyricsParser.parseWordByWordLyrics(it))
                } catch (e: Exception) {
                    ""
                }
            }
            ?: lyricsData?.plainLyrics?.takeIf { it.isNotBlank() }
            ?: ""
    }
    
    val wordByWordForm = remember(lyricsData) {
        lyricsData?.wordByWordLyrics?.takeIf { it.isNotBlank() } ?: ""
    }
    
    var editedSource by remember { mutableStateOf(sourceForm) }
    var editedLineByLine by remember { mutableStateOf(lineByLineForm) }
    var editedWordByWord by remember { mutableStateOf(wordByWordForm) }
    
    val editedLyrics = when (selectedFormat) {
        LyricFormat.SOURCE -> editedSource
        LyricFormat.LINE_BY_LINE -> editedLineByLine
        LyricFormat.WORD_BY_WORD -> editedWordByWord
    }
    
    fun updateEditedLyrics(newText: String) {
        when (selectedFormat) {
            LyricFormat.SOURCE -> editedSource = newText
            LyricFormat.LINE_BY_LINE -> editedLineByLine = newText
            LyricFormat.WORD_BY_WORD -> editedWordByWord = newText
        }
    }
    
    var timeOffset by remember { mutableIntStateOf(initialTimeOffset) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    
    // Helper functions for lyrics document handling
    fun getDocumentDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        val name = cursor.getString(nameIndex)?.trim()
                        if (name.isNullOrEmpty()) null else name
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w("LyricsEditor", "Unable to read lyrics document name", e)
            null
        }
    }

    fun maybeRenameLyricsDocument(uri: Uri, expectedFileName: String): Uri {
        // Validate inputs
        if (expectedFileName.isBlank()) return uri
        
        val currentName = getDocumentDisplayName(uri) ?: return uri
        if (currentName.isBlank()) return uri
        
        // Check if provider appended .txt to our .lrc filename
        // E.g., we wanted "song.lrc" but got "song.lrc.txt"
        val currentNameWithoutTxt = if (currentName.endsWith(".txt", ignoreCase = true) && currentName.length > 4) {
            currentName.substring(0, currentName.length - 4)  // Safe because we checked length > 4
        } else {
            currentName
        }
        
        // Check if removing .txt from current name gives us the expected filename
        val shouldRename = currentName.endsWith(".txt", ignoreCase = true) &&
            currentNameWithoutTxt.equals(expectedFileName, ignoreCase = true)
        
        if (!shouldRename) {
            return uri
        }
        
        return try {
            DocumentsContract.renameDocument(context.contentResolver, uri, expectedFileName) ?: uri
        } catch (e: Exception) {
            Log.w("LyricsEditor", "Unable to rename saved lyrics document from '$currentName' to '$expectedFileName'", e)
            uri
        }
    }
    
    // Check if lyrics are synced (contain LRC timestamps or word-by-word JSON)
    val hasSyncedLyrics = remember(editedLyrics, selectedFormat) {
        selectedFormat == LyricFormat.WORD_BY_WORD ||
        editedLyrics.contains(Regex("""\[\d{2}:\d{2}\.\d{2,3}\]"""))
    }
    
    // Update edited states when lyricsData changes
    LaunchedEffect(lyricsData) {
        editedSource = sourceForm
        editedLineByLine = lineByLineForm
        editedWordByWord = wordByWordForm
    }
    
    // Update timeOffset when initialTimeOffset changes
    LaunchedEffect(initialTimeOffset) {
        timeOffset = initialTimeOffset
    }

    // Animation states
    var showContent by remember { mutableStateOf(true) }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) toolsExpanded = false
    }

    // Function to adjust LRC timestamps or word-by-word JSON timestamps
    fun adjustLyricsTimestamps(lyrics: String, offsetMs: Int): String {
        if (offsetMs == 0) return lyrics
        
        if (selectedFormat == LyricFormat.WORD_BY_WORD) {
            return try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(lyrics)
                if (parsed.isEmpty()) return lyrics
                val adjusted = parsed.map { line ->
                    line.copy(
                        lineTimestamp = (line.lineTimestamp + offsetMs).coerceAtLeast(0L),
                        lineEndtime = (line.lineEndtime + offsetMs).coerceAtLeast(0L),
                        words = line.words.map { word ->
                            word.copy(
                                timestamp = (word.timestamp + offsetMs).coerceAtLeast(0L),
                                endtime = (word.endtime + offsetMs).coerceAtLeast(0L)
                            )
                        }
                    )
                }
                RhythmLyricsParser.toWordByWordJson(adjusted)
            } catch (e: Exception) {
                lyrics
            }
        }

        // If lyrics have Enhanced LRC word timestamps, adjust both line and word timestamps
        if (io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(lyrics)) {
            return try {
                val parsed = RhythmLyricsParser.parseEnhancedLRCtoWordByWord(lyrics)
                if (parsed.isNotEmpty()) {
                    val adjusted = parsed.map { line ->
                        line.copy(
                            lineTimestamp = (line.lineTimestamp + offsetMs).coerceAtLeast(0L),
                            lineEndtime = (line.lineEndtime + offsetMs).coerceAtLeast(0L),
                            words = line.words.map { word ->
                                word.copy(
                                    timestamp = (word.timestamp + offsetMs).coerceAtLeast(0L),
                                    endtime = (word.endtime + offsetMs).coerceAtLeast(0L)
                                )
                            }
                        )
                    }
                    RhythmLyricsParser.toEnhancedLRCFormat(adjusted)
                } else lyrics
            } catch (_: Exception) {
                lyrics
            }
        }
        
        val lrcRegex = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""", RegexOption.MULTILINE)
        return lyrics.lines().joinToString("\n") { line ->
            lrcRegex.matchEntire(line)?.let { match ->
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val centiseconds = match.groupValues[3].padEnd(3, '0').take(3).toInt()
                val text = match.groupValues[4]
                
                // Convert to milliseconds
                var totalMs = (minutes * 60 * 1000) + (seconds * 1000) + centiseconds
                totalMs += offsetMs
                
                // Don't allow negative timestamps
                if (totalMs < 0) totalMs = 0
                
                // Convert back to LRC format
                val newMinutes = totalMs / 60000
                val newSeconds = (totalMs % 60000) / 1000
                val newCentiseconds = (totalMs % 1000)
                
                "[%02d:%02d.%03d]%s".format(newMinutes, newSeconds, newCentiseconds, text)
            } ?: line
        }
    }

    val appSettings = remember { AppSettings.getInstance(context) }
    var contributionName by remember {
        mutableStateOf(appSettings.getLyricsContributorName())
    }
    val songLyricsPreferences by appSettings.songLyricsPreferences.collectAsState()
    val songCustomLrcFiles by appSettings.songCustomLrcFiles.collectAsState()
    val lrcRenameBehavior by appSettings.lrcRenameBehavior.collectAsState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFileName by remember { mutableStateOf("") }
    var pendingExpectedName by remember { mutableStateOf("") }
    var pendingLyrics by remember { mutableStateOf("") }
    var rememberChoiceCheckbox by remember { mutableStateOf(false) }

    fun restorePlaybackAfterContribution() {
        val shouldResume = resumePlaybackAfterContribution
        resumePlaybackAfterContribution = false
        if (shouldResume) onPlayPause()
    }

    fun requestCatalogSync(forceOverwrite: Boolean) {
        contributionForceOverwrite = forceOverwrite
        showContributionDialog = true
        if (isPlaying && !resumePlaybackAfterContribution) {
            resumePlaybackAfterContribution = true
            onPlayPause()
        }
    }

    LaunchedEffect(contributionUploadInFlight, catalogSyncState.status) {
        if (
            contributionUploadInFlight &&
            catalogSyncState.status != CatalogLyricsSyncStatus.SYNCING
        ) {
            // Let the ViewModel publish SYNCING first. This also releases playback if the
            // request cannot start because the Catalog connection disappeared.
            delay(400)
            contributionUploadInFlight = false
            restorePlaybackAfterContribution()
        }
    }

    fun applyLoadedLyrics(loadedLyrics: String) {
        val loadedTrimmed = loadedLyrics.trim()
        
        val isWordByWordJson = (loadedTrimmed.startsWith("[") || loadedTrimmed.startsWith("{")) && 
            (loadedTrimmed.contains("\"timestamp\"") || loadedTrimmed.contains("\"words\""))
            
        val isLrc = loadedLyrics.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}]"))

        val isTtml = loadedTrimmed.startsWith("<") && (
            loadedTrimmed.contains("<tt") ||
            loadedTrimmed.contains("http://www.w3.org/ns/ttml") ||
            loadedTrimmed.contains("<p ") ||
            loadedTrimmed.contains("<p>") ||
            loadedTrimmed.contains("<span ")
        )
        
        if (isWordByWordJson) {
            editedWordByWord = loadedLyrics
            editedSource = loadedLyrics
            try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(loadedLyrics)
                editedLineByLine = RhythmLyricsParser.toLRCFormat(parsed)
            } catch (_: Exception) {
                editedLineByLine = ""
            }
            selectedFormat = LyricFormat.WORD_BY_WORD
        } else if (isTtml) {
            val parsedLines = RhythmLyricsParser.parseTtmlLyrics(loadedLyrics)
            if (parsedLines.isNotEmpty()) {
                val wordByWordJson = Gson().toJson(parsedLines)
                val parsedWordByWordLines = RhythmLyricsParser.parseWordByWordLyrics(wordByWordJson)
                val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                editedWordByWord = if (hasWordTiming) wordByWordJson else ""
                editedLineByLine = RhythmLyricsParser.toLRCFormat(parsedWordByWordLines)
                editedSource = loadedLyrics
                selectedFormat = if (hasWordTiming) LyricFormat.WORD_BY_WORD else LyricFormat.LINE_BY_LINE
            } else {
                val semanticLyrics = io.github.cluno1.sonorus.util.parseTtml(null, loadedLyrics)
                val plain = when (semanticLyrics) {
                    is io.github.cluno1.sonorus.util.SemanticLyrics.UnsyncedLyrics ->
                        semanticLyrics.unsyncedText.joinToString("\n") { it.first }
                    is io.github.cluno1.sonorus.util.SemanticLyrics.SyncedLyrics ->
                        semanticLyrics.text.joinToString("\n") { it.text }
                    else -> loadedLyrics
                }
                editedSource = loadedLyrics
                editedLineByLine = plain
                editedWordByWord = ""
                selectedFormat = LyricFormat.LINE_BY_LINE
            }
        } else if (isLrc) {
            val hasWordTimestamps = io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(loadedLyrics)
            if (hasWordTimestamps) {
                val parsedWordByWordLines = try {
                    RhythmLyricsParser.parseEnhancedLRCtoWordByWord(loadedLyrics)
                } catch (_: Exception) {
                    emptyList()
                }
                if (parsedWordByWordLines.isNotEmpty()) {
                    val wordByWordJson = Gson().toJson(parsedWordByWordLines)
                    val hasWordTiming = RhythmLyricsParser.hasWordTiming(parsedWordByWordLines)
                    editedWordByWord = if (hasWordTiming) wordByWordJson else ""
                    editedLineByLine = loadedLyrics
                    editedSource = loadedLyrics
                    selectedFormat = if (hasWordTiming) LyricFormat.WORD_BY_WORD else LyricFormat.LINE_BY_LINE
                } else {
                    editedLineByLine = loadedLyrics
                    editedSource = loadedLyrics
                    editedWordByWord = ""
                    selectedFormat = LyricFormat.LINE_BY_LINE
                }
            } else {
                editedLineByLine = loadedLyrics
                editedSource = loadedLyrics
                editedWordByWord = ""
                selectedFormat = LyricFormat.LINE_BY_LINE
            }
        } else {
            editedSource = loadedLyrics
            editedLineByLine = loadedLyrics
            editedWordByWord = ""
            selectedFormat = LyricFormat.SOURCE
        }
        RhythmMessages.success(context, R.string.lyrics_loaded_success)
    }

    suspend fun performRename(
        context: Context,
        sourceUri: Uri,
        parentDir: File?,
        destFileName: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (parentDir == null || !parentDir.exists()) return@withContext false
        try {
            val destFile = File(parentDir, destFileName)
            destFile.writeText(content)
            true
        } catch (e: Exception) {
            Log.e("LyricsEditor", "Failed to write renamed file", e)
            false
        }
    }

    // File picker launcher for loading .lrc files
    val loadLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        LyricsFileUtils.loadLyricsFromUri(context, selectedUri)
                    }

                    if (result.lyrics != null) {
                        val loadedLyrics = result.lyrics
                        val loadedFileName = getDocumentDisplayName(selectedUri)
                        
                        if (song != null && song.path != null && loadedFileName != null) {
                            val songFile = File(song.path)
                            val songNameWithoutExt = songFile.nameWithoutExtension
                            val loadedExt = File(loadedFileName).extension.lowercase().ifEmpty { "lrc" }
                            val expectedLrcName = "$songNameWithoutExt.$loadedExt"
                            
                            if (!loadedFileName.equals(expectedLrcName, ignoreCase = true)) {
                                when (lrcRenameBehavior) {
                                    "always" -> {
                                        val success = performRename(context, selectedUri, songFile.parentFile, expectedLrcName, loadedLyrics)
                                        if (!success) {
                                            appSettings.setSongCustomLrcFile(song.id, loadedFileName)
                                            RhythmMessages.error(context, context.getString(R.string.lyrics_rename_permission_denied), long = true)
                                        } else {
                                            RhythmMessages.success(context, context.getString(R.string.lyrics_renamed_to_format, expectedLrcName))
                                        }
                                        applyLoadedLyrics(loadedLyrics)
                                    }
                                    "never" -> {
                                        appSettings.setSongCustomLrcFile(song.id, loadedFileName)
                                        applyLoadedLyrics(loadedLyrics)
                                    }
                                    else -> { // "ask"
                                        pendingUri = selectedUri
                                        pendingFileName = loadedFileName
                                        pendingExpectedName = expectedLrcName
                                        pendingLyrics = loadedLyrics
                                        rememberChoiceCheckbox = false
                                        showRenameDialog = true
                                    }
                                }
                            } else {
                                applyLoadedLyrics(loadedLyrics)
                            }
                        } else {
                            applyLoadedLyrics(loadedLyrics)
                        }
                    } else {
                        RhythmMessages.error(context, result.errorMessage ?: context.getString(R.string.lyrics_load_file_error), long = true)
                    }
                } catch (e: Exception) {
                    Log.e("LyricsEditor", "Error loading lyrics file", e)
                    RhythmMessages.failure(context, R.string.msg_lyrics_load_failed, e, long = true)
                }
            }
        }
    }

    val defaultFileName = stringResource(R.string.lyrics_default_file_name)
    val sanitizedTitle = remember(songTitle, defaultFileName) {
        songTitle.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("_+"), "_")  // Collapse multiple underscores
            .trim('_')  // Remove leading/trailing underscores
            .takeIf { it.isNotEmpty() } ?: defaultFileName
    }

    val defaultLyricsFileName = remember(song, sanitizedTitle, selectedFormat, editedSource, editedLineByLine) {
        val baseName = if (song != null && !song.path.isNullOrBlank()) {
            File(song.path).nameWithoutExtension
        } else {
            sanitizedTitle
        }
        when {
            selectedFormat == LyricFormat.WORD_BY_WORD -> "$baseName.json"
            selectedFormat == LyricFormat.LINE_BY_LINE && io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(editedLineByLine) -> "$baseName.elrc"
            selectedFormat == LyricFormat.SOURCE && editedSource.trim().startsWith("<") -> "$baseName.ttml"
            else -> "$baseName.lrc"
        }
    }

    val detectedFormatLabel = when (selectedFormat) {
            LyricFormat.WORD_BY_WORD -> stringResource(R.string.lyrics_format_word_by_word_json)
            LyricFormat.LINE_BY_LINE -> {
                if (io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(editedLineByLine)) {
                    stringResource(R.string.lyrics_format_enhanced_lrc)
                } else {
                    stringResource(R.string.lyrics_format_standard_lrc)
                }
            }
            LyricFormat.SOURCE -> {
                if (editedSource.trim().startsWith("<")) {
                    stringResource(R.string.lyrics_format_ttml)
                } else {
                    stringResource(R.string.lyrics_format_raw_source)
                }
            }
    }

    fun selectFormat(targetFormat: LyricFormat) {
        if (
            targetFormat == LyricFormat.LINE_BY_LINE &&
            editedLineByLine.isBlank() &&
            editedWordByWord.isNotBlank()
        ) {
            try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(editedWordByWord)
                if (parsed.isNotEmpty()) {
                    editedLineByLine = RhythmLyricsParser.toEnhancedLRCFormat(parsed)
                }
            } catch (_: Exception) {}
        } else if (
            targetFormat == LyricFormat.WORD_BY_WORD &&
            editedWordByWord.isBlank() &&
            io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(editedLineByLine)
        ) {
            try {
                val parsed = RhythmLyricsParser.parseEnhancedLRCtoWordByWord(editedLineByLine)
                if (parsed.isNotEmpty()) {
                    editedWordByWord = Gson().toJson(parsed)
                }
            } catch (_: Exception) {}
        } else if (targetFormat == LyricFormat.SOURCE && editedSource.isBlank()) {
            if (editedWordByWord.isNotBlank()) {
                try {
                    val parsed = RhythmLyricsParser.parseWordByWordLyrics(editedWordByWord)
                    if (parsed.isNotEmpty()) {
                        editedSource = RhythmLyricsParser.toTtmlFormat(
                            parsed,
                            song?.title,
                            song?.artist,
                        )
                    }
                } catch (_: Exception) {}
            } else if (editedLineByLine.isNotBlank()) {
                editedSource = editedLineByLine
            }
        }
        selectedFormat = targetFormat
    }

    var editorValue by remember(selectedFormat) {
        mutableStateOf(TextFieldValue(editedLyrics, TextRange(editedLyrics.length)))
    }
    val editorSessionKey = song?.id ?: songTitle
    // Saving a stamp refreshes lyricsData with the just-saved lyrics. That refresh belongs to
    // the same editor session and must not move the timing cursor back to the first line.
    var timingLineIndex by remember(editorSessionKey) { mutableIntStateOf(0) }
    var previousTimingText by remember(editorSessionKey) { mutableStateOf<String?>(null) }
    var catalogEditDirty by remember(editorSessionKey) { mutableStateOf(false) }
    val timingTarget = remember(editedLyrics, timingLineIndex, selectedFormat) {
        if (selectedFormat == LyricFormat.WORD_BY_WORD) {
            null
        } else {
            LrcTimingEditor.timingTarget(editedLyrics, timingLineIndex)
        }
    }

    fun stampCurrentLine() {
        LrcTimingEditor.stampLine(
            editedLyrics,
            timingLineIndex,
            currentPlaybackPositionMs,
            playbackDurationMs.takeIf { it > 0L },
        )?.let { result ->
            previousTimingText = editedLyrics
            editedLineByLine = result.text
            selectedFormat = LyricFormat.LINE_BY_LINE
            timingLineIndex = result.nextLineIndex ?: result.stampedLineIndex
            val cursor = LrcTimingEditor.lineStartOffset(result.text, timingLineIndex)
            editorValue = TextFieldValue(result.text, TextRange(cursor))
            catalogEditDirty = true
            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
            if (pauseAfterStamp && isPlaying) {
                onPlayPause()
            }
        }
    }

    LaunchedEffect(editedLyrics, selectedFormat) {
        if (editorValue.text != editedLyrics) {
            val cursor = editorValue.selection.end.coerceIn(0, editedLyrics.length)
            editorValue = TextFieldValue(editedLyrics, TextRange(cursor))
        }
    }
    LaunchedEffect(editedLyrics, selectedFormat, canSyncCatalog, catalogEditDirty) {
        if (canSyncCatalog && catalogEditDirty && editedLyrics.isNotBlank()) {
            delay(600)
            onSave(editedLyrics, timeOffset, selectedFormat.name)
            catalogEditDirty = false
        }
    }

    // File picker launcher for saving .lrc files
    val saveLyricsLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentWithInitialFolder()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(editedLyrics.toByteArray())
                    outputStream.flush()
                }

                maybeRenameLyricsDocument(it, defaultLyricsFileName)

                RhythmMessages.success(context, R.string.lyrics_saved_success)
                onSave(editedLyrics, timeOffset, selectedFormat.name)
                onDismiss()
            } catch (e: Exception) {
                Log.e("LyricsEditor", "Error saving lyrics file", e)
                RhythmMessages.failure(context, R.string.msg_lyrics_save_failed, e, long = true)
            }
        }
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = {},
        sheetState = sheetState,
        showCloseButton = false,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // Header with animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                LyricsEditorHeader(
                    songTitle = songTitle,
                    formatLabel = detectedFormatLabel,
                    onBack = onDismiss,
                    onHelp = { showTimingHelp = true },
                )
            }

            if (!isImeVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RhythmToggleButtonGroup(
                        options = listOf(
                            RhythmToggleOption(text = stringResource(R.string.lyrics_source)),
                            RhythmToggleOption(text = stringResource(R.string.lyrics_line_by_line)),
                            RhythmToggleOption(text = stringResource(R.string.lyrics_word_by_word)),
                        ),
                        selectedIndices = setOf(
                            when (selectedFormat) {
                                LyricFormat.SOURCE -> 0
                                LyricFormat.LINE_BY_LINE -> 1
                                LyricFormat.WORD_BY_WORD -> 2
                            }
                        ),
                        onToggle = { index ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            selectFormat(
                                when (index) {
                                    0 -> LyricFormat.SOURCE
                                    1 -> LyricFormat.LINE_BY_LINE
                                    else -> LyricFormat.WORD_BY_WORD
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        size = RhythmButtonSize.Small,
                        isShowingCheck = false,
                        isFillMaxWidth = false,
                    )
                    FilledTonalIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            toolsExpanded = !toolsExpanded
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (toolsExpanded) {
                                RhythmIcons.ExpandLess
                            } else {
                                RhythmIcons.Tune
                            },
                            contentDescription = stringResource(R.string.settings_section_advanced),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (!isImeVisible && toolsExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(toolsScrollState)
                        .padding(vertical = 12.dp),
                ) {
            if (song != null && !isImeVisible && !canSyncCatalog) {
                val songId = song.id
                val currentPref = songLyricsPreferences[songId]
                val customLrc = songCustomLrcFiles[songId]
                
                var dropdownExpanded by remember { mutableStateOf(false) }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lyrics_source_preference),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (currentPref) {
                                    "online" -> stringResource(R.string.lyrics_source_pref_online)
                                    "embedded" -> stringResource(R.string.lyrics_source_pref_embedded)
                                    "lrc" -> stringResource(R.string.lyrics_source_pref_local_lrc)
                                    else -> stringResource(R.string.lyrics_source_pref_default)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            FilledTonalButton(
                                onClick = { dropdownExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.lyrics_change))
                                Icon(
                                    imageVector = MaterialSymbolIcon("arrow_drop_down", filled = true),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 220.dp)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                val options = listOf(
                                    Triple(null, stringResource(R.string.lyrics_source_pref_default), "settings"),
                                    Triple("online", stringResource(R.string.lyrics_source_pref_online), "cloud"),
                                    Triple("embedded", stringResource(R.string.lyrics_source_pref_embedded), "music_note"),
                                    Triple("lrc", stringResource(R.string.lyrics_source_pref_local_lrc), "storage")
                                )
                                
                                val outerRadius = 16.dp
                                val innerRadius = 4.dp
                                val itemSpacing = 3.dp

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                                ) {
                                    options.forEachIndexed { index, (prefValue, label, iconName) ->
                                        val itemShape = when {
                                            options.size == 1 -> RoundedCornerShape(outerRadius)
                                            index == 0 -> RoundedCornerShape(
                                                topStart = outerRadius, topEnd = outerRadius,
                                                bottomStart = innerRadius, bottomEnd = innerRadius
                                            )
                                            index == options.size - 1 -> RoundedCornerShape(
                                                topStart = innerRadius, topEnd = innerRadius,
                                                bottomStart = outerRadius, bottomEnd = outerRadius
                                            )
                                            else -> RoundedCornerShape(innerRadius)
                                        }

                                        Surface(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setSongLyricsPreference(songId, prefValue)
                                                dropdownExpanded = false
                                                onRefresh()
                                            },
                                            shape = itemShape,
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    modifier = Modifier.size(28.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Icon(
                                                            imageVector = MaterialSymbolIcon(iconName, filled = true),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (customLrc != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("description", filled = true),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = customLrc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    appSettings.setSongCustomLrcFile(songId, null)
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.lyrics_clear), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (ProductCapabilities.devicePublicMetadata && !song.id.startsWith("rhythm-catalog:")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onOnlineRematch, modifier = Modifier.fillMaxWidth()) {
                                Icon(MaterialSymbolIcon("travel_explore", filled = true), null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.lyrics_online_rematch))
                            }
                            OutlinedButton(onClick = {
                                onChooseOtherVersion()
                                showCandidateDialog = true
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(MaterialSymbolIcon("swap_horiz", filled = true), null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.lyrics_choose_version))
                            }
                            TextButton(onClick = onRestoreLocal, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.lyrics_restore_local))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Timestamp Adjustment Controls
            AnimatedVisibility(
                visible = showContent && selectedFormat != LyricFormat.WORD_BY_WORD,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                previousTimingText = editedLyrics
                                val generated = LrcTimingEditor.generateTemplate(
                                    editedLyrics,
                                    playbackDurationMs.takeIf { it > 0L },
                                    estimateFromDuration = true,
                                )
                                editedLineByLine = generated
                                selectedFormat = LyricFormat.LINE_BY_LINE
                                timingLineIndex = 0
                                editorValue = TextFieldValue(generated, TextRange(0))
                                catalogEditDirty = true
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedFormat != LyricFormat.WORD_BY_WORD &&
                                editedLyrics.isNotBlank() &&
                                playbackDurationMs > 0L,
                        ) {
                            Text(stringResource(R.string.lyrics_generate_timing_template))
                        }
                        OutlinedButton(
                            onClick = {
                                previousTimingText = editedLyrics
                                val generated = LrcTimingEditor.generateTemplate(
                                    editedLyrics,
                                    durationMs = null,
                                    estimateFromDuration = false,
                                )
                                editedLineByLine = generated
                                selectedFormat = LyricFormat.LINE_BY_LINE
                                timingLineIndex = 0
                                editorValue = TextFieldValue(generated, TextRange(0))
                                catalogEditDirty = true
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedFormat != LyricFormat.WORD_BY_WORD && editedLyrics.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.lyrics_generate_zero_template))
                        }
                    }

                    if (hasSyncedLyrics) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = MaterialSymbolIcon("sync", filled = true),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.sync_adjustment),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.lyrics_time_offset_ms,
                                    "${if (timeOffset >= 0) "+" else ""}$timeOffset"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Reset/Refresh button
                            FilledTonalButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    timeOffset = 0
                                    onRefresh()
                                },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(context.getString(R.string.bottomsheet_reset), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Medium
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset -= 500
                                val adjusted = adjustLyricsTimestamps(editedLyrics, -500)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = false,
                            size = RhythmButtonSize.Medium,
                            text = "-${stringResource(R.string.lyricseditorbottomsheet_str_500ms)}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset -= 100
                                val adjusted = adjustLyricsTimestamps(editedLyrics, -100)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = false,
                            size = RhythmButtonSize.Medium,
                            text = "-${stringResource(R.string.lyricseditorbottomsheet_str_100ms)}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset += 100
                                val adjusted = adjustLyricsTimestamps(editedLyrics, 100)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = false,
                            size = RhythmButtonSize.Medium,
                            text = "+${stringResource(R.string.lyricseditorbottomsheet_str_100ms)}",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.secondary,
                        )
                        
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                timeOffset += 500
                                val adjusted = adjustLyricsTimestamps(editedLyrics, 500)
                                updateEditedLyrics(adjusted)
                                onSave(adjusted, timeOffset, selectedFormat.name)
                            },
                            weight = 1f,
                            isFirst = false,
                            isLast = true,
                            size = RhythmButtonSize.Medium,
                            text = "+${stringResource(R.string.lyricseditorbottomsheet_str_500ms)}",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            contentColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    }
                }
            }
                }
            }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lyrics Text Field with animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = editorValue,
                        onValueChange = {
                            val textChanged = it.text != editorValue.text
                            editorValue = it
                            timingLineIndex = LrcTimingEditor.lineIndexAtOffset(
                                it.text,
                                it.selection.end,
                            )
                            updateEditedLyrics(it.text)
                            if (textChanged) catalogEditDirty = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        placeholder = {
                            Text(
                                text = when (selectedFormat) {
                                    LyricFormat.WORD_BY_WORD -> stringResource(R.string.lyrics_placeholder_word_by_word)
                                    LyricFormat.LINE_BY_LINE -> stringResource(R.string.lyrics_placeholder_line_by_line)
                                    LyricFormat.SOURCE -> stringResource(R.string.lyrics_placeholder_source)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            if (timingTarget != null) {
                LyricsTimingWorkbench(
                    target = timingTarget,
                    currentPositionMs = currentPlaybackPositionMs,
                    durationMs = playbackDurationMs,
                    isPlaying = isPlaying,
                    playbackSpeed = editorPlaybackSpeed,
                    isTrackLoopEnabled = repeatMode == Player.REPEAT_MODE_ONE,
                    loopStartMs = loopStartMs,
                    loopEndMs = loopEndMs,
                    pauseAfterStamp = pauseAfterStamp,
                    canUndo = previousTimingText != null,
                    onSeekTo = onSeekTo,
                    onRewind = {
                        onSeekTo((currentPlaybackPositionMs - 3_000L).coerceAtLeast(0L))
                    },
                    onPlayPause = {
                        if (!contributionUploadInFlight) onPlayPause()
                    },
                    onToggleTrackLoop = {
                        loopStartMs = null
                        loopEndMs = null
                        val nextMode = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            if (initialRepeatMode == Player.REPEAT_MODE_ONE) {
                                Player.REPEAT_MODE_OFF
                            } else {
                                initialRepeatMode
                            }
                        } else {
                            Player.REPEAT_MODE_ONE
                        }
                        repeatChangedByEditor = true
                        onSetRepeatMode(nextMode)
                    },
                    onAdvanceAbLoop = {
                        when {
                            loopStartMs == null -> {
                                if (repeatMode == Player.REPEAT_MODE_ONE) {
                                    repeatChangedByEditor = true
                                    onSetRepeatMode(
                                        if (initialRepeatMode == Player.REPEAT_MODE_ONE) {
                                            Player.REPEAT_MODE_OFF
                                        } else {
                                            initialRepeatMode
                                        }
                                    )
                                }
                                loopStartMs = currentPlaybackPositionMs
                                    .coerceAtLeast(0L)
                                    .let { position ->
                                        if (playbackDurationMs >= 500L) {
                                            position.coerceAtMost(playbackDurationMs - 500L)
                                        } else {
                                            position
                                        }
                                    }
                                loopEndMs = null
                            }
                            loopEndMs == null -> {
                                loopEndMs = LrcTimingEditor.loopEnd(
                                    startMs = loopStartMs ?: 0L,
                                    requestedEndMs = currentPlaybackPositionMs,
                                    durationMs = playbackDurationMs.takeIf { it > 0L },
                                )
                            }
                            else -> {
                                loopStartMs = null
                                loopEndMs = null
                            }
                        }
                    },
                    onToggleSpeed = {
                        val nextSpeed = if (abs(editorPlaybackSpeed - 0.75f) < 0.01f) {
                            1f
                        } else {
                            0.75f
                        }
                        speedChangedByEditor = true
                        editorPlaybackSpeed = nextSpeed
                        onSetPlaybackSpeed(nextSpeed)
                    },
                    onTogglePauseAfterStamp = {
                        pauseAfterStamp = !pauseAfterStamp
                    },
                    onPreviousLine = {
                        LrcTimingEditor.previousEditableLine(editedLyrics, timingLineIndex)
                            ?.let { previous ->
                                timingLineIndex = previous
                                val cursor = LrcTimingEditor.lineStartOffset(editedLyrics, previous)
                                editorValue = editorValue.copy(selection = TextRange(cursor))
                            }
                    },
                    onStamp = ::stampCurrentLine,
                    onUndo = {
                        previousTimingText?.let { previous ->
                            val current = editedLyrics
                            editedLineByLine = previous
                            selectedFormat = LyricFormat.LINE_BY_LINE
                            previousTimingText = current
                            val cursor = LrcTimingEditor.lineStartOffset(previous, timingLineIndex)
                            editorValue = TextFieldValue(previous, TextRange(cursor))
                            catalogEditDirty = true
                        }
                    },
                )
            }

            // Sticky Footer with action buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (canSyncCatalog) {
                        val syncStatusText = when (catalogSyncState.status) {
                            CatalogLyricsSyncStatus.CLEAN -> stringResource(R.string.lyrics_sync_clean)
                            CatalogLyricsSyncStatus.PENDING -> stringResource(R.string.lyrics_sync_pending)
                            CatalogLyricsSyncStatus.SYNCING -> stringResource(R.string.lyrics_sync_in_progress)
                            CatalogLyricsSyncStatus.SYNCED -> stringResource(R.string.lyrics_sync_complete)
                            CatalogLyricsSyncStatus.CONFLICT -> stringResource(R.string.lyrics_sync_conflict)
                            CatalogLyricsSyncStatus.ERROR -> stringResource(R.string.lyrics_sync_failed)
                            CatalogLyricsSyncStatus.UNAVAILABLE -> stringResource(R.string.lyrics_sync_unavailable)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = catalogSyncState.message ?: syncStatusText,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (
                                    catalogSyncState.status == CatalogLyricsSyncStatus.ERROR ||
                                    catalogSyncState.status == CatalogLyricsSyncStatus.CONFLICT
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (catalogSyncState.status != CatalogLyricsSyncStatus.CONFLICT) {
                                FilledTonalButton(
                                    onClick = { requestCatalogSync(forceOverwrite = false) },
                                    enabled = editedLyrics.isNotBlank() &&
                                        catalogSyncState.status != CatalogLyricsSyncStatus.SYNCING,
                                ) {
                                    Text(stringResource(R.string.lyrics_sync_to_server))
                                }
                            }
                        }
                        if (catalogSyncState.status == CatalogLyricsSyncStatus.CONFLICT) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onLoadServerLyrics,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.lyrics_load_server_version))
                                }
                                FilledTonalButton(
                                    onClick = { requestCatalogSync(forceOverwrite = true) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.lyrics_overwrite_server_version))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!isImeVisible) {
                        RhythmGroupedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            size = RhythmButtonSize.Medium
                        ) {
                        // Load File Button
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                loadLyricsLauncher.launch(
                                    arrayOf(
                                        "text/plain",
                                        "text/*",
                                        "text/x-lrc",
                                        "application/x-lrc",
                                        "application/json",
                                        "application/xml",
                                        "text/xml",
                                        "application/ttml+xml",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            weight = 1f,
                            isFirst = true,
                            icon = MaterialSymbolIcon("file_open", filled = true),
                            text = context.getString(R.string.bottomsheet_lyrics_load)
                        )

                        // Save File Button
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                if (editedLyrics.isNotBlank()) {
                                    val mimeType = when {
                                        selectedFormat == LyricFormat.WORD_BY_WORD -> "application/json"
                                        selectedFormat == LyricFormat.LINE_BY_LINE && io.github.cluno1.sonorus.util.LyricsParser.hasWordTimestamps(editedLineByLine) -> "text/x-lrc"
                                        selectedFormat == LyricFormat.SOURCE && editedSource.trim().startsWith("<") -> "application/ttml+xml"
                                        else -> "application/octet-stream"
                                    }
                                    val initialUri = getInitialFolderUri(song?.path)
                                    saveLyricsLauncher.launch(
                                        SaveLyricsInput(
                                            fileName = defaultLyricsFileName,
                                            mimeType = mimeType,
                                            initialUri = initialUri
                                        )
                                    )
                                }
                            },
                            weight = 1f,
                            isLast = true,
                            enabled = editedLyrics.isNotBlank(),
                            icon = MaterialSymbolIcon("save", filled = true),
                            text = context.getString(R.string.bottomsheet_lyrics_save)
                        )
                        }
                    }

                    // Embed in File is local-only — streaming songs have no writable file.
                    if (!isStreamingMode && !canSyncCatalog && !isImeVisible) {
                        Spacer(modifier = Modifier.height(8.dp))

                        RhythmGroupedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            size = RhythmButtonSize.Medium
                        ) {
                            RhythmButtonWeighted(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    if (editedLyrics.isNotBlank()) {
                                        onEmbedInFile(editedLyrics)
                                    }
                                },
                                weight = 1f,
                                isFirst = true,
                                isLast = true,
                                enabled = editedLyrics.isNotBlank(),
                                icon = RhythmIcons.MusicNote,
                                text = context.getString(R.string.bottomsheet_lyrics_embed)
                            )
                        }
                    }
            }
        }

        if (showRenameDialog && song != null) {
            AlertDialog(
                onDismissRequest = { 
                    showRenameDialog = false 
                    appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                    applyLoadedLyrics(pendingLyrics)
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("drive_file_rename_outline", filled = true),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.lyrics_rename_lrc_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.lyrics_rename_lrc_body, pendingFileName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberChoiceCheckbox,
                                onCheckedChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    rememberChoiceCheckbox = it
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.lyrics_remember_choice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRenameDialog = false
                            if (rememberChoiceCheckbox) {
                                appSettings.setLrcRenameBehavior("always")
                            }
                            scope.launch {
                                val songFile = File(song.path!!)
                                val success = performRename(context, pendingUri!!, songFile.parentFile, pendingExpectedName, pendingLyrics)
                                if (!success) {
                                    appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                                    RhythmMessages.error(context, context.getString(R.string.lyrics_rename_permission_denied), long = true)
                                } else {
                                    RhythmMessages.success(context, context.getString(R.string.lyrics_renamed_success))
                                }
                                applyLoadedLyrics(pendingLyrics)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("drive_file_rename_outline", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_rename))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showRenameDialog = false
                            if (rememberChoiceCheckbox) {
                                appSettings.setLrcRenameBehavior("never")
                            }
                            appSettings.setSongCustomLrcFile(song.id, pendingFileName)
                            applyLoadedLyrics(pendingLyrics)
                        }
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("label", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_tag_keep_custom))
                    }
                }
            )
        }
    }

    if (showCandidateDialog) {
        AlertDialog(
            onDismissRequest = { showCandidateDialog = false },
            title = { Text(stringResource(R.string.lyrics_choose_version)) },
            text = {
                if (deviceLyricsCandidates.isEmpty()) {
                    Text(stringResource(R.string.lyrics_no_candidates))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(deviceLyricsCandidates, key = { it.externalId }) { candidate ->
                            Surface(
                                onClick = {
                                    onSelectDeviceLyricsCandidate(candidate)
                                    showCandidateDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(candidate.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            R.string.lyrics_candidate_format,
                                            candidate.artist,
                                            candidate.album.ifBlank { "—" },
                                            candidate.durationSeconds?.toInt()?.let { stringResource(R.string.lyrics_duration_seconds, it) } ?: "—",
                                            (candidate.confidence * 100).toInt()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCandidateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showContributionDialog) {
        AlertDialog(
            onDismissRequest = {
                showContributionDialog = false
                restorePlaybackAfterContribution()
            },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("volunteer_activism", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.lyrics_contribution_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.lyrics_contribution_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = contributionName,
                        onValueChange = { contributionName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.lyrics_contribution_name_label)) },
                        supportingText = {
                            Text(stringResource(R.string.lyrics_contribution_name_support))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("history", filled = true),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.lyrics_contribution_history_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val submittedName = contributionName.trim()
                        appSettings.setLyricsContributorName(submittedName)
                        val lyricsWithHistory = LyricsContributionAttribution.preserveHistory(
                            lyrics = editedLyrics,
                            existing = lyricsData?.contributions.orEmpty(),
                            format = selectedFormat.name,
                        )
                        val attributedLyrics = LyricsContributionAttribution.append(
                            lyrics = lyricsWithHistory,
                            contributorName = submittedName,
                            updatedAt = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss XXX",
                                Locale.ROOT,
                            ).format(Date()),
                            format = selectedFormat.name,
                        )
                        showContributionDialog = false
                        contributionUploadInFlight = true
                        catalogEditDirty = false
                        onSyncCatalog(
                            attributedLyrics,
                            selectedFormat.name,
                            contributionForceOverwrite,
                        )
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("cloud_upload", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.lyrics_contribution_upload))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showContributionDialog = false
                        restorePlaybackAfterContribution()
                    },
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showTimingHelp) {
        LyricsTimingHelpSheet(onDismiss = { showTimingHelp = false })
    }
}
}

@Composable
private fun LyricsTimingWorkbench(
    target: LrcTimingTarget,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    isTrackLoopEnabled: Boolean,
    loopStartMs: Long?,
    loopEndMs: Long?,
    pauseAfterStamp: Boolean,
    canUndo: Boolean,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleTrackLoop: () -> Unit,
    onAdvanceAbLoop: () -> Unit,
    onToggleSpeed: () -> Unit,
    onTogglePauseAfterStamp: () -> Unit,
    onPreviousLine: () -> Unit,
    onStamp: () -> Unit,
    onUndo: () -> Unit,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(currentPositionMs) }
    val safeDuration = durationMs.coerceAtLeast(0L)
    val shownPosition = if (isScrubbing) scrubPositionMs else currentPositionMs

    LaunchedEffect(currentPositionMs, safeDuration, isScrubbing) {
        if (!isScrubbing) {
            scrubPositionMs = currentPositionMs.coerceIn(0L, safeDuration.coerceAtLeast(0L))
        }
    }

    val loopActionLabel = when {
        loopStartMs == null -> "A"
        loopEndMs == null -> "B"
        else -> "A–B"
    }
    val loopActionDescription = when {
        loopStartMs == null -> stringResource(R.string.lyrics_timing_set_loop_start)
        loopEndMs == null -> stringResource(R.string.lyrics_timing_set_loop_end)
        else -> stringResource(R.string.lyrics_timing_clear_loop)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatEditorClock(shownPosition),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = if (safeDuration > 0L) {
                        shownPosition.coerceIn(0L, safeDuration).toFloat()
                    } else {
                        0f
                    },
                    onValueChange = { value ->
                        isScrubbing = true
                        scrubPositionMs = value.toLong()
                    },
                    onValueChangeFinished = {
                        onSeekTo(scrubPositionMs.coerceIn(0L, safeDuration))
                        isScrubbing = false
                    },
                    valueRange = 0f..safeDuration.coerceAtLeast(1L).toFloat(),
                    enabled = safeDuration > 0L,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                )
                Text(
                    text = formatEditorClock(safeDuration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onRewind,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("replay", filled = true),
                        contentDescription = stringResource(R.string.lyrics_timing_rewind_three_seconds),
                        modifier = Modifier.size(20.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon(
                            if (isPlaying) "pause" else "play_arrow",
                            filled = true,
                        ),
                        contentDescription = stringResource(R.string.play_pause),
                        modifier = Modifier.size(22.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onToggleTrackLoop,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isTrackLoopEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("repeat_one", filled = isTrackLoopEnabled),
                        contentDescription = stringResource(R.string.lyrics_timing_repeat_track),
                        tint = if (isTrackLoopEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                OutlinedButton(
                    onClick = onAdvanceAbLoop,
                    modifier = Modifier
                        .height(40.dp)
                        .semantics { contentDescription = loopActionDescription },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (loopEndMs != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                ) {
                    Text(loopActionLabel, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onToggleSpeed,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%.2f×", playbackSpeed),
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.lyrics_timing_target_line,
                            target.ordinal,
                            target.total,
                            target.text,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (loopStartMs != null) {
                        Text(
                            text = buildString {
                                append("A ")
                                append(formatEditorClock(loopStartMs))
                                loopEndMs?.let { end ->
                                    append("  ·  B ")
                                    append(formatEditorClock(end))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                TextButton(
                    onClick = onTogglePauseAfterStamp,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon(
                            if (pauseAfterStamp) "pause_circle" else "fast_forward",
                            filled = pauseAfterStamp,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (pauseAfterStamp) {
                                R.string.lyrics_timing_step_mode
                            } else {
                                R.string.lyrics_timing_continuous_mode
                            }
                        ),
                        maxLines = 1,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onPreviousLine,
                    enabled = target.ordinal > 1,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("arrow_upward", filled = true),
                        contentDescription = stringResource(R.string.lyrics_previous_timing_line),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Button(
                    onClick = onStamp,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("timer", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.lyrics_stamp_current_time,
                            LrcTimingEditor.formatTimestamp(currentPositionMs),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("undo", filled = true),
                        contentDescription = stringResource(R.string.action_undo),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun formatEditorClock(positionMs: Long): String {
    val safe = positionMs.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val tenths = (safe % 1_000L) / 100L
    return String.format(java.util.Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsTimingHelpSheet(onDismiss: () -> Unit) {
    val helpSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    RhythmAdaptiveModalSheet(
        onDismissRequest = onDismiss,
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        tabletMaxWidth = 680.dp,
        showCloseButton = false,
        sheetState = helpSheetState,
        sheetGesturesEnabled = true,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MaterialSymbolIcon("help", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyrics_timing_help_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.lyrics_timing_help_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("close", filled = true),
                        contentDescription = stringResource(R.string.lyrics_timing_help_close),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.lyrics_timing_help_overview),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    LyricsTimingHelpSectionTitle(
                        iconName = "route",
                        text = stringResource(R.string.lyrics_timing_help_quick_start),
                    )
                }
                item {
                    LyricsTimingHelpStep(
                        number = 1,
                        title = stringResource(R.string.lyrics_timing_help_step_prepare_title),
                        body = stringResource(R.string.lyrics_timing_help_step_prepare_body),
                    )
                }
                item {
                    LyricsTimingHelpStep(
                        number = 2,
                        title = stringResource(R.string.lyrics_timing_help_step_practice_title),
                        body = stringResource(R.string.lyrics_timing_help_step_practice_body),
                    )
                }
                item {
                    LyricsTimingHelpStep(
                        number = 3,
                        title = stringResource(R.string.lyrics_timing_help_step_stamp_title),
                        body = stringResource(R.string.lyrics_timing_help_step_stamp_body),
                    )
                }
                item {
                    LyricsTimingHelpStep(
                        number = 4,
                        title = stringResource(R.string.lyrics_timing_help_step_finish_title),
                        body = stringResource(R.string.lyrics_timing_help_step_finish_body),
                    )
                }

                item {
                    LyricsTimingHelpSectionTitle(
                        iconName = "cloud_sync",
                        text = stringResource(R.string.lyrics_timing_help_storage_section),
                    )
                }
                item {
                    LyricsTimingHelpNote(
                        iconName = "draft",
                        title = stringResource(R.string.lyrics_timing_help_local_title),
                        body = stringResource(R.string.lyrics_timing_help_local_body),
                    )
                }
                item {
                    LyricsTimingHelpNote(
                        iconName = "save",
                        title = stringResource(R.string.lyrics_timing_help_export_title),
                        body = stringResource(R.string.lyrics_timing_help_export_body),
                    )
                }
                item {
                    LyricsTimingHelpNote(
                        iconName = "cloud_upload",
                        title = stringResource(R.string.lyrics_timing_help_server_title),
                        body = stringResource(R.string.lyrics_timing_help_server_body),
                    )
                }
                item {
                    LyricsTimingHelpNote(
                        iconName = "sync_problem",
                        title = stringResource(R.string.lyrics_timing_help_conflict_title),
                        body = stringResource(R.string.lyrics_timing_help_conflict_body),
                    )
                }

                item {
                    LyricsTimingHelpSectionTitle(
                        iconName = "play_circle",
                        text = stringResource(R.string.lyrics_timing_help_playback_controls),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "replay",
                        title = stringResource(R.string.lyrics_timing_rewind_three_seconds),
                        body = stringResource(R.string.lyrics_timing_help_rewind_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "play_arrow",
                        title = stringResource(R.string.play_pause),
                        body = stringResource(R.string.lyrics_timing_help_play_pause_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "repeat_one",
                        title = stringResource(R.string.lyrics_timing_repeat_track),
                        body = stringResource(R.string.lyrics_timing_help_repeat_default_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "repeat",
                        title = stringResource(R.string.lyrics_timing_help_ab_title),
                        body = stringResource(R.string.lyrics_timing_help_ab_body),
                        badge = "A → B → ×",
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "speed",
                        title = stringResource(R.string.lyrics_timing_help_speed_title),
                        body = stringResource(R.string.lyrics_timing_help_speed_body),
                        badge = "0.75× / 1.00×",
                    )
                }

                item {
                    LyricsTimingHelpSectionTitle(
                        iconName = "timer",
                        text = stringResource(R.string.lyrics_timing_help_timing_controls),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "fast_forward",
                        title = stringResource(R.string.lyrics_timing_continuous_mode),
                        body = stringResource(R.string.lyrics_timing_help_continuous_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "pause_circle",
                        title = stringResource(R.string.lyrics_timing_step_mode),
                        body = stringResource(R.string.lyrics_timing_help_step_mode_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "arrow_upward",
                        title = stringResource(R.string.lyrics_previous_timing_line),
                        body = stringResource(R.string.lyrics_timing_help_previous_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "timer",
                        title = stringResource(R.string.lyrics_timing_help_stamp_title),
                        body = stringResource(R.string.lyrics_timing_help_stamp_body),
                    )
                }
                item {
                    LyricsTimingHelpControl(
                        iconName = "undo",
                        title = stringResource(R.string.action_undo),
                        body = stringResource(R.string.lyrics_timing_help_undo_body),
                    )
                }

                item {
                    LyricsTimingHelpNote(
                        iconName = "description",
                        title = stringResource(R.string.lyrics_timing_help_formats_title),
                        body = stringResource(R.string.lyrics_timing_help_formats_body),
                    )
                }
                item {
                    LyricsTimingHelpNote(
                        iconName = "lightbulb",
                        title = stringResource(R.string.lyrics_timing_help_tips_title),
                        body = stringResource(R.string.lyrics_timing_help_tips_body),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsTimingHelpSectionTitle(iconName: String, text: String) {
    Row(
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MaterialSymbolIcon(iconName, filled = true),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LyricsTimingHelpStep(number: Int, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LyricsTimingHelpControl(
    iconName: String,
    title: String,
    body: String,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MaterialSymbolIcon(iconName, filled = true),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LyricsTimingHelpNote(iconName: String, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = MaterialSymbolIcon(iconName, filled = true),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LyricsEditorHeader(
    songTitle: String,
    formatLabel: String? = null,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = RhythmIcons.Back,
                contentDescription = stringResource(R.string.library_go_back),
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = songTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = context.getString(R.string.lyrics_editor_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!formatLabel.isNullOrBlank()) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = onHelp,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = MaterialSymbolIcon("help", filled = true),
                contentDescription = stringResource(R.string.lyrics_timing_help_open),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun getDocumentDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    val name = cursor.getString(nameIndex)?.trim()
                    if (name.isNullOrEmpty()) null else name
                } else {
                    null
                }
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.w("LyricsEditor", "Unable to read lyrics document name", e)
        null
    }
}
