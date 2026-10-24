package io.github.cluno1.sonorus.features.catalog.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import kotlinx.coroutines.launch

/** Chips shown before "N more"; the dialog always lists every tag. */
private const val SUMMARY_TAG_LIMIT = 8

/**
 * issue 116: tag summary of one catalog Work plus the "Manage tags" entry.
 *
 * [work] is the Work being read (never the background player's song); while it is null the row
 * shows nothing. Saving goes straight to the server through the tag-only endpoint and, on success,
 * reports the updated Work through [onWorkUpdated] so the page shows the stored tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogWorkTagsSection(
    work: WorkSummary?,
    onWorkUpdated: (WorkSummary) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (work == null) return
    val tags = work.tags.orEmpty()
    var showDialog by remember(work.id) { mutableStateOf(false) }
    val manageDescription = stringResource(R.string.work_tags_cd_manage)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.work_tags_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            } else {
                tags.take(SUMMARY_TAG_LIMIT).forEach { tag -> WorkTagChip(tag) }
                if (tags.size > SUMMARY_TAG_LIMIT) {
                    WorkTagChip(stringResource(R.string.work_tags_more, tags.size - SUMMARY_TAG_LIMIT))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = manageDescription },
        ) {
            Icon(MaterialSymbolIcon("sell"), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.work_tags_manage))
        }
    }

    if (showDialog) {
        CatalogWorkTagsDialog(
            work = work,
            onDismiss = { showDialog = false },
            onWorkUpdated = { updated ->
                onWorkUpdated(updated)
                showDialog = false
            },
        )
    }
}

/**
 * issue 116: tags of the Work [workId] for pages that only know the id (player lyrics).
 * Reads the cached bundle first, then the server; a late answer for another Work is ignored.
 */
@Composable
fun CatalogWorkTagsForWork(
    workId: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val context = LocalContext.current
    val repository = remember(context) { CatalogModule.repository(context) }
    var work by remember(workId) { mutableStateOf(repository.cachedBundle(workId)?.work) }
    LaunchedEffect(workId) {
        repository.getWorkBundle(workId).onSuccess { bundle ->
            if (bundle.work.id == workId) work = bundle.work
        }
    }
    CatalogWorkTagsSection(
        work = work,
        onWorkUpdated = { updated -> if (updated.id == workId) work = updated },
        modifier = modifier,
        contentColor = contentColor,
    )
}

@Composable
private fun WorkTagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Draft editor: changes stay local until "Save", which sends the whole set once (`[]` clears).
 * Cancel writes nothing. Failures keep the draft; a stale revision offers reloading the latest tags
 * instead of overwriting someone else's edit.
 */
@Composable
private fun CatalogWorkTagsDialog(
    work: WorkSummary,
    onDismiss: () -> Unit,
    onWorkUpdated: (WorkSummary) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { CatalogModule.repository(context) }
    val scope = rememberCoroutineScope()
    var base by remember(work.id) { mutableStateOf(work) }
    val draft = remember(work.id) { mutableStateListOf(*work.tags.orEmpty().toTypedArray()) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }

    val check = CatalogWorkTagsPolicy.check(draft)
    val changed = draft.toList() != base.tags.orEmpty()
    val savedMessage = stringResource(R.string.work_tags_saved)

    // Reloading only refreshes the dialog draft; the page keeps its tags until a save succeeds.
    fun reload() {
        saving = true
        scope.launch {
            repository.getWorkBundle(base.id, forceRefresh = true)
                .onSuccess { bundle ->
                    base = bundle.work
                    draft.clear()
                    draft.addAll(bundle.work.tags.orEmpty())
                    failure = null
                }
                .onFailure { failure = it }
            saving = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Column {
                Text(stringResource(R.string.work_tags_dialog_title))
                Text(
                    text = work.canonicalTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val problems = (check as? CatalogWorkTagsPolicy.Check.Invalid)?.problems.orEmpty()
                draft.forEachIndexed { index, value ->
                    val problem = problems[index]
                    OutlinedTextField(
                        value = value,
                        onValueChange = { draft[index] = it },
                        enabled = !saving,
                        singleLine = true,
                        isError = problem != null,
                        supportingText = problem?.let { { Text(problemText(it)) } },
                        trailingIcon = {
                            IconButton(onClick = { draft.removeAt(index) }, enabled = !saving) {
                                Icon(
                                    RhythmIcons.Close,
                                    contentDescription = stringResource(R.string.work_tags_remove, value),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (draft.isEmpty()) {
                    Text(
                        text = stringResource(R.string.work_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if ((check as? CatalogWorkTagsPolicy.Check.Invalid)?.tooMany == true) {
                    Text(
                        text = stringResource(R.string.work_tags_error_too_many, CatalogWorkTagsPolicy.MAX_TAGS),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = { draft.add("") },
                    enabled = !saving && draft.size < CatalogWorkTagsPolicy.MAX_TAGS,
                ) {
                    Icon(RhythmIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.work_tags_add))
                }
                failure?.let { error ->
                    Text(
                        text = failureText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (error is CatalogFailure.StaleRevision) {
                        TextButton(onClick = { reload() }, enabled = !saving) {
                            Text(stringResource(R.string.work_tags_reload))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val valid = check as? CatalogWorkTagsPolicy.Check.Valid ?: return@TextButton
                    saving = true
                    failure = null
                    scope.launch {
                        repository.replaceWorkTags(base.id, valid.tags, base.revision)
                            .onSuccess { updated ->
                                Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                                onWorkUpdated(updated)
                            }
                            .onFailure { failure = it }
                        saving = false
                    }
                },
                enabled = !saving && changed && check is CatalogWorkTagsPolicy.Check.Valid,
            ) {
                Text(stringResource(R.string.work_tags_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.work_tags_cancel))
            }
        },
    )
}

@Composable
private fun problemText(problem: CatalogWorkTagsPolicy.Problem): String = when (problem) {
    CatalogWorkTagsPolicy.Problem.EMPTY -> stringResource(R.string.work_tags_error_empty)
    CatalogWorkTagsPolicy.Problem.TOO_LONG ->
        stringResource(R.string.work_tags_error_too_long, CatalogWorkTagsPolicy.MAX_LENGTH)
    CatalogWorkTagsPolicy.Problem.DUPLICATE -> stringResource(R.string.work_tags_error_duplicate)
}

@Composable
private fun failureText(error: Throwable): String = when {
    error is CatalogFailure.StaleRevision -> stringResource(R.string.work_tags_error_stale)
    error is CatalogFailure.Server && error.statusCode == 404 -> stringResource(R.string.work_tags_error_unsupported)
    error is CatalogFailure.Server && error.statusCode == 409 -> stringResource(R.string.work_tags_error_not_editable)
    error is CatalogFailure.Unreachable -> stringResource(R.string.work_tags_error_offline)
    error is CatalogFailure.InvalidCredentials || error is CatalogFailure.Forbidden ->
        stringResource(R.string.work_tags_error_auth)
    else -> stringResource(R.string.work_tags_error_generic, error.message.orEmpty())
}
