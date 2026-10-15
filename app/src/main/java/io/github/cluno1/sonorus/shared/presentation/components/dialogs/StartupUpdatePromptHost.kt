/*
 * SPDX-FileCopyrightText: 2026 Sonorus contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.shared.presentation.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cluno1.sonorus.BuildConfig
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.viewmodel.rememberAppUpdaterViewModel

/**
 * Checks for a newer Sonorus build whenever the app is opened or returns to the foreground and,
 * at most once per local day, offers a one-tap upgrade that the user may skip.
 * Mount once at the activity root.
 */
@Composable
fun StartupUpdatePromptHost(enabled: Boolean = true) {
    val updaterViewModel = rememberAppUpdaterViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && enabled) {
                // Resume after the "install unknown apps" page continues a pending install.
                updaterViewModel.onAppForegroundForStartupUpgrade()
                updaterViewModel.checkForUpdatesOnAppOpen()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val prompt by updaterViewModel.startupPrompt.collectAsState()
    val installPending by updaterViewModel.startupInstallPending.collectAsState()
    val isDownloading by updaterViewModel.isDownloading.collectAsState()
    val downloadProgress by updaterViewModel.downloadProgress.collectAsState()
    val upgradeError by updaterViewModel.startupUpgradeError.collectAsState()

    val target = prompt ?: return
    val busy = installPending || isDownloading

    AlertDialog(
        onDismissRequest = { if (!busy) updaterViewModel.skipStartupPrompt() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        icon = {
            Icon(
                imageVector = MaterialSymbolIcon("system_update", filled = true),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.update_prompt_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.update_prompt_message, target.versionName, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (target.whatsNew.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.update_prompt_release_notes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        target.whatsNew.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when {
                    isDownloading -> {
                        Text(
                            text = stringResource(R.string.update_prompt_downloading, downloadProgress.toInt().coerceIn(0, 100)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    installPending -> Text(
                        text = stringResource(R.string.update_prompt_install_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    upgradeError != null -> Text(
                        text = stringResource(R.string.update_prompt_failed, upgradeError.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!busy) {
                    Button(
                        onClick = { updaterViewModel.startStartupUpgrade() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("download", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (upgradeError != null) R.string.update_prompt_retry else R.string.update_prompt_upgrade,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = { updaterViewModel.skipStartupPrompt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.update_prompt_skip))
                    }
                } else {
                    OutlinedButton(
                        onClick = { updaterViewModel.cancelStartupUpgrade() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.update_prompt_cancel))
                    }
                }
            }
        },
        dismissButton = {},
        shape = RoundedCornerShape(28.dp),
    )
}
