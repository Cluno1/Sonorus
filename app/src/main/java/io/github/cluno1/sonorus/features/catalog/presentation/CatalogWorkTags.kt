package io.github.cluno1.sonorus.features.catalog.presentation

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.ImeAction
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

/**
 * issue 116 / 121: tag row of one catalog Work — a single horizontally scrolling line of chips
 * with an icon-only "manage tags" button, so it costs one short line of the lyrics page.
 *
 * [work] is the Work being read (never the background player's song); while it is null the row
 * shows nothing. Saving goes straight to the server through the tag-only endpoint and, on success,
 * reports the updated Work through [onWorkUpdated] so the page shows the stored tags.
 */
@Composable
fun CatalogWorkTagsSection(
    work: WorkSummary?,
    onWorkUpdated: (WorkSummary) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (work == null) return
    val tags = work.tags.orEmpty()
    var showEditor by remember(work.id) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.work_tags_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            } else {
                tags.forEach { tag -> WorkTagChip(tag) }
            }
        }
        IconButton(onClick = { showEditor = true }) {
            Icon(
                MaterialSymbolIcon("sell"),
                contentDescription = stringResource(R.string.work_tags_cd_manage),
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }

    if (showEditor) {
        CatalogWorkTagsEditorSheet(
            work = work,
            onDismiss = { showEditor = false },
            onWorkUpdated = { updated ->
                onWorkUpdated(updated)
                showEditor = false
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
 * issue 121: bottom-sheet tag editor. The draft holds the selected labels; the user picks from
 * every existing tag (most used first, filtered by the input) or adds a new one from the input.
 * Nothing is written until "Save", which sends the whole set once (`[]` clears); cancel writes
 * nothing. A stale revision keeps the draft and offers reloading the Work's latest tags.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CatalogWorkTagsEditorSheet(
    work: WorkSummary,
    onDismiss: () -> Unit,
    onWorkUpdated: (WorkSummary) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { CatalogModule.repository(context) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var base by remember(work.id) { mutableStateOf(work) }
    val draft = remember(work.id) { mutableStateListOf(*work.tags.orEmpty().toTypedArray()) }
    var query by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf(repository.cachedLibraryTags()) }
    var catalogFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Throwable?>(null) }

    LaunchedEffect(Unit) {
        repository.listLibraryTags()
            .onSuccess {
                catalog = it
                catalogFailed = false
            }
            .onFailure { catalogFailed = catalog.isEmpty() }
    }

    val check = CatalogWorkTagsPolicy.check(draft)
    val changed = draft.toList() != base.tags.orEmpty()
    val selectedKeys = draft.map(CatalogWorkTagsPolicy::key).toSet()
    val entry = CatalogWorkTagsPolicy.resolveEntry(query, catalog, draft)
    val visibleCatalog = CatalogWorkTagsPolicy.filterCatalog(catalog, query)
    val savedMessage = stringResource(R.string.work_tags_saved)

    fun addEntry() {
        val label = entry ?: return
        if (draft.size >= CatalogWorkTagsPolicy.MAX_TAGS) return
        draft.add(label)
        query = ""
    }

    fun toggle(label: String) {
        val index = draft.indexOfFirst { CatalogWorkTagsPolicy.key(it) == CatalogWorkTagsPolicy.key(label) }
        if (index >= 0) draft.removeAt(index) else if (draft.size < CatalogWorkTagsPolicy.MAX_TAGS) draft.add(label)
    }

    // Reloading only refreshes the sheet's draft; the page keeps its tags until a save succeeds.
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

    fun save() {
        val valid = check as? CatalogWorkTagsPolicy.Check.Valid ?: return
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
    }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.work_tags_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = work.canonicalTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = stringResource(R.string.work_tags_selected),
                style = MaterialTheme.typography.labelLarge,
            )
            if (draft.isEmpty()) {
                Text(
                    text = stringResource(R.string.work_tags_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    draft.toList().forEach { label ->
                        InputChip(
                            selected = true,
                            onClick = { if (!saving) toggle(label) },
                            label = { Text(label) },
                            trailingIcon = {
                                Icon(
                                    RhythmIcons.Close,
                                    contentDescription = stringResource(R.string.work_tags_remove, label),
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = !saving,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.work_tags_search_or_add)) },
                leadingIcon = { Icon(RhythmIcons.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addEntry() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (entry != null) {
                val isNew = CatalogWorkTagsPolicy.isNewTag(entry, catalog)
                AssistChip(
                    onClick = ::addEntry,
                    enabled = !saving && draft.size < CatalogWorkTagsPolicy.MAX_TAGS,
                    label = {
                        Text(
                            stringResource(
                                if (isNew) R.string.work_tags_create else R.string.work_tags_add_existing,
                                entry,
                            ),
                        )
                    },
                    leadingIcon = { Icon(RhythmIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            } else if (CatalogWorkTagsPolicy.normalize(query).codePointCount(0, CatalogWorkTagsPolicy.normalize(query).length) >
                CatalogWorkTagsPolicy.MAX_LENGTH
            ) {
                Text(
                    text = stringResource(R.string.work_tags_error_too_long, CatalogWorkTagsPolicy.MAX_LENGTH),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.work_tags_all),
                style = MaterialTheme.typography.labelLarge,
            )
            when {
                catalogFailed -> Text(
                    text = stringResource(R.string.work_tags_catalog_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                visibleCatalog.isEmpty() && catalog.isNotEmpty() -> Text(
                    text = stringResource(R.string.work_tags_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visibleCatalog.forEach { tag ->
                        FilterChip(
                            selected = CatalogWorkTagsPolicy.key(tag.label) in selectedKeys,
                            onClick = { if (!saving) toggle(tag.label) },
                            label = { Text(stringResource(R.string.work_tags_catalog_item, tag.label, tag.workCount)) },
                        )
                    }
                }
            }

            if ((check as? CatalogWorkTagsPolicy.Check.Invalid)?.tooMany == true) {
                Text(
                    text = stringResource(R.string.work_tags_error_too_many, CatalogWorkTagsPolicy.MAX_TAGS),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(stringResource(R.string.work_tags_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { save() },
                    enabled = !saving && changed && check is CatalogWorkTagsPolicy.Check.Valid,
                ) {
                    Text(stringResource(R.string.work_tags_save))
                }
            }
        }
    }
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
