package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.cluno1.sonorus.features.scores.presentation.RemoteScoreScreen
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevision
import io.github.cluno1.sonorus.features.catalog.domain.MusicXmlRuntimeSanitizer
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryScoreWork
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.shared.data.repository.PlaybackMediaKind
import io.github.cluno1.sonorus.shared.data.repository.PlaybackSubject
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scorePublishedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun formatScoreRevisionTime(
    value: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val normalized = value.trim().replace(' ', 'T')
    return runCatching {
        OffsetDateTime.parse(normalized)
            .atZoneSameInstant(zoneId)
            .format(scorePublishedAtFormatter)
    }.getOrElse {
        runCatching {
            LocalDateTime.parse(normalized).format(scorePublishedAtFormatter)
        }.getOrDefault(value)
    }
}

@Composable
fun CatalogRemoteScoreScreen(
    workId: String,
    revisionId: String,
    title: String,
    scoreLabel: String,
    expectedPartCount: Int,
    scoreWork: CatalogLibraryScoreWork? = null,
    initialScoreId: String? = null,
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onOpenChorus: (
        workId: String,
        scoreId: String,
        revisionId: String,
        title: String,
    ) -> Unit,
    // issue 112: settings-panel entries that jump to this Work's lyrics page / song playback
    // page. Lyrics is always reachable (an empty-lyrics state is a valid landing); song is
    // null when the Work has no playable song in the synced library, so the entry is hidden.
    onOpenWorkLyrics: () -> Unit = {},
    onOpenWorkSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scoreChorusLabEnabled by remember(context) {
        AppSettings.getInstance(context).scoreChorusLabEnabled
    }.collectAsState()
    var selectedScoreId by remember(scoreWork?.workId, scoreWork?.scoreOptions, initialScoreId) {
        mutableStateOf(
            if (scoreWork == null) {
                initialScoreId
            } else {
                scoreWork.resolveInitialScoreId(initialScoreId)
            }
        )
    }
    val selectedOption = scoreWork?.scoreOptions?.firstOrNull { it.scoreId == selectedScoreId }
        ?: scoreWork?.scoreOptions?.firstOrNull { it.scoreId == scoreWork.defaultScoreId }
    val activeRevisionId = selectedOption?.revisionId ?: revisionId
    val activePartCount = selectedOption?.partCount ?: expectedPartCount
    var retry by remember { mutableIntStateOf(0) }
    var history by remember(activeRevisionId) { mutableStateOf<List<ScoreRevision>>(emptyList()) }
    var selectedIndex by remember(activeRevisionId) { mutableIntStateOf(0) }
    var bytes by remember(activeRevisionId) { mutableStateOf<ByteArray?>(null) }
    var error by remember(activeRevisionId) { mutableStateOf<String?>(null) }

    LaunchedEffect(activeRevisionId, retry) {
        history = emptyList()
        selectedIndex = 0
        bytes = null
        error = null
        viewModel.scoreHistory(activeRevisionId).fold(
            onSuccess = { history = it },
            onFailure = { error = it.message ?: "谱面修订链加载失败" },
        )
    }

    LaunchedEffect(history, selectedIndex) {
        val revision = history.getOrNull(selectedIndex) ?: return@LaunchedEffect
        bytes = null
        error = null
        viewModel.scoreBytes(revision).fold(
            onSuccess = {
                runCatching { MusicXmlRuntimeSanitizer.forAlphaTab(it) }.fold(
                    onSuccess = { runtimeBytes -> bytes = runtimeBytes },
                    onFailure = { failure -> error = failure.message ?: "谱面安全检查失败" },
                )
            },
            onFailure = { error = it.message ?: "谱面文件下载失败" },
        )
    }

    when {
        bytes != null -> RemoteScoreScreen(
            title = scoreWork?.title ?: title,
            canonicalMusicXml = checkNotNull(bytes),
            onBackClick = onBack,
            onChorusClick = if (scoreChorusLabEnabled) {
                {
                    onOpenChorus(
                        workId,
                        selectedOption?.scoreId
                            ?: history.getOrNull(selectedIndex)?.scoreId
                            ?: initialScoreId.orEmpty(),
                        history.getOrNull(selectedIndex)?.id ?: activeRevisionId,
                        scoreWork?.title ?: title,
                    )
                }
            } else null,
            scoreLabel = selectedOption?.scoreLabel ?: scoreLabel.takeIf(String::isNotBlank),
            revisionLabel = history.getOrNull(selectedIndex)?.let {
                stringResource(R.string.score_revision_label, it.revisionNo)
            },
            revisionTimeLabel = history.getOrNull(selectedIndex)?.let {
                formatScoreRevisionTime(it.createdAt)
            },
            canOpenNewerRevision = selectedIndex > 0,
            canOpenOlderRevision = selectedIndex < history.lastIndex,
            onOpenNewerRevision = { selectedIndex-- },
            onOpenOlderRevision = { selectedIndex++ },
            scoreSettingsContent = { stopPlayback ->
                CatalogScoreSelectionCard(
                    scoreWork = scoreWork,
                    selectedScoreId = selectedOption?.scoreId,
                    onSelectScore = { selectedScoreId = it },
                )
                // issue 112: leaving the score page (either entry) always stops the alphaTab
                // synthesis first, so its audio never keeps playing underneath the next screen.
                CatalogScoreQuickLink(
                    icon = RhythmIcons.Player.Lyrics,
                    label = stringResource(R.string.score_view_lyrics),
                    onClick = {
                        stopPlayback()
                        onOpenWorkLyrics()
                    },
                )
                onOpenWorkSong?.let { openSong ->
                    CatalogScoreQuickLink(
                        icon = RhythmIcons.Play,
                        label = stringResource(R.string.score_play_song),
                        onClick = {
                            stopPlayback()
                            openSong()
                        },
                    )
                }
            },
            expectedPartCount = activePartCount,
            playbackSubject = history.getOrNull(selectedIndex)?.let { revision ->
                PlaybackSubject(
                    subjectId = "rhythm-score:score:${revision.scoreId}",
                    mediaKind = PlaybackMediaKind.SCORE,
                    title = scoreWork?.title ?: title,
                    artist = scoreWork?.artist,
                    collection = selectedOption?.scoreLabel ?: scoreLabel.takeIf(String::isNotBlank),
                    artworkUri = scoreWork?.coverUrl,
                    workId = scoreWork?.workId ?: workId,
                    scoreId = revision.scoreId,
                    revisionId = revision.id,
                )
            },
            modifier = modifier,
        )
        error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        RhythmIcons.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        checkNotNull(error),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    FilledTonalButton(onClick = { retry++ }) {
                        Text(stringResource(R.string.score_retry))
                    }
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.score_back))
                    }
                }
            }
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            M3CircularLoader()
        }
    }
}

@Composable
internal fun CatalogScoreSelectionCard(
    scoreWork: CatalogLibraryScoreWork?,
    selectedScoreId: String?,
    onSelectScore: (String) -> Unit,
    enabled: Boolean = true,
) {
    scoreWork?.takeIf { it.scoreOptions.size > 1 }?.let { work ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        RhythmIcons.ScoreFilled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.score_selection),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    work.scoreOptions.forEach { option ->
                        FilterChip(
                            selected = option.scoreId == selectedScoreId,
                            onClick = { onSelectScore(option.scoreId) },
                            enabled = enabled,
                            label = {
                                Text(
                                    "${option.scoreLabel} · ${stringResource(R.string.score_revision_label, option.revisionNo)}",
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** issue 112: a single-line settings-panel entry that jumps to another page for this Work. */
@Composable
private fun CatalogScoreQuickLink(
    icon: MaterialSymbolIcon,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
