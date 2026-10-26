package io.github.cluno1.sonorus.features.catalog.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsVariant
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomWriteGate
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import io.github.cluno1.sonorus.features.catalog.domain.normalizeCatalogLyricsLanguageTag
import kotlinx.coroutines.launch

/** What the editor opens on: the language being saved and its current text. */
internal data class WorkLyricsEditTarget(val language: String, val text: String)

/**
 * issue 125: the edited version is the one on screen. A Work without lyrics starts empty in its
 * own language, else the device language; "und" is never offered as a language to write.
 */
internal fun workLyricsEditTarget(
    work: WorkSummary,
    shown: CatalogLyricsVariant?,
    deviceLanguage: String,
): WorkLyricsEditTarget {
    if (shown != null) {
        val text = if (shown.format == "plain") shown.lyrics.trim() else workLyricsDisplayText(shown.lyrics)
        return WorkLyricsEditTarget(normalizeCatalogLyricsLanguageTag(shown.language), text)
    }
    val language = listOfNotNull(work.lyricsLanguage, work.language, deviceLanguage)
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
        ?: "zh-Hans"
    return WorkLyricsEditTarget(normalizeCatalogLyricsLanguageTag(language), "")
}

/**
 * issue 125: plain-text editor for a Work's lyrics of one language, opened from the bottom-right
 * column of the Work lyrics page. One save per tap ([CatalogRoomWriteGate]); a stale revision
 * (someone saved first) closes the sheet and reloads the page instead of overwriting them.
 * Time-synced lyrics stay with the songs (player lyrics editor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogWorkLyricsEditSheet(
    work: WorkSummary,
    target: WorkLyricsEditTarget,
    onDismiss: () -> Unit,
    onSaved: (WorkSummary) -> Unit,
    onStale: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = remember { CatalogRoomWriteGate() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(target) { mutableStateOf(target.text) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = text.trim() != target.text.trim()

    fun save() {
        if (!gate.tryStart()) return
        saving = true
        error = null
        scope.launch {
            try {
                CatalogModule.repository(context)
                    .replaceWorkLyrics(work.id, target.language, text, work.revision)
                    .fold(
                        onSuccess = { updated ->
                            Toast.makeText(context, context.getString(R.string.work_lyrics_edit_saved), Toast.LENGTH_SHORT).show()
                            onSaved(updated)
                        },
                        onFailure = { failure ->
                            if (failure is CatalogFailure.StaleRevision) {
                                Toast.makeText(context, context.getString(R.string.work_lyrics_edit_stale), Toast.LENGTH_LONG).show()
                                onStale()
                            } else {
                                error = failure.message ?: context.getString(R.string.work_lyrics_edit_failed)
                            }
                        },
                    )
            } finally {
                saving = false
                gate.finish()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.work_lyrics_edit_title, target.language),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.work_lyrics_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 460.dp),
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.rooms_cancel)) }
                Button(enabled = !saving && changed && text.isNotBlank(), onClick = ::save) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.work_lyrics_edit_save))
                    }
                }
            }
        }
    }
}
