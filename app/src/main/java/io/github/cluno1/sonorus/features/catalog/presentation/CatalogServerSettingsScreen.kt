package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.shared.presentation.components.common.RhythmMessages
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogIssuedInvite
import io.github.cluno1.sonorus.features.catalog.domain.CatalogSmartEnrollmentText
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CatalogServerSettingsScreen(
    state: CatalogUiState,
    onEnroll: (String, String) -> Unit,
    onEnrollSmartText: (String) -> Unit,
    onIssueInvite: (String, String, String, String, String, Boolean) -> Unit,
    onClear: () -> Unit,
    onClearInviteUiState: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdminPage by rememberSaveable { mutableStateOf(false) }

    if (showAdminPage) {
        CatalogAdminInviteScreen(
            state = state,
            onIssueInvite = onIssueInvite,
            onClearInviteUiState = onClearInviteUiState,
            onBack = {
                onClearInviteUiState()
                showAdminPage = false
            },
            modifier = modifier,
        )
        return
    }

    CatalogDeviceEnrollmentScreen(
        state = state,
        onEnroll = onEnroll,
        onEnrollSmartText = onEnrollSmartText,
        onClear = onClear,
        onOpenAdmin = {
            onClearInviteUiState()
            showAdminPage = true
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun CatalogDeviceEnrollmentScreen(
    state: CatalogUiState,
    onEnroll: (String, String) -> Unit,
    onEnrollSmartText: (String) -> Unit,
    onClear: () -> Unit,
    onOpenAdmin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by rememberSaveable(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var smartText by rememberSaveable { mutableStateOf("") }
    val miniPlayerBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val adminPageDescription = stringResource(R.string.catalog_admin_issue_invite)

    LaunchedEffect(state.deviceRegistered) {
        if (state.deviceRegistered) {
            serverUrl = state.serverUrl
            inviteCode = ""
            smartText = ""
        }
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_catalog_server),
        showBackButton = true,
        onBackClick = onBack,
        actions = {
            IconButton(onClick = onOpenAdmin) {
                Icon(
                    icon = RhythmIcons.Navigation.SettingsOutlined,
                    contentDescription = adminPageDescription,
                )
            }
        },
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .then(modifier)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp + miniPlayerBottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CatalogSettingsSection {
                Text(
                    stringResource(R.string.catalog_manual_enrollment_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.catalog_server_address)) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text(stringResource(R.string.catalog_one_time_invite)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onEnroll(serverUrl, inviteCode) },
                    enabled = !state.loading && serverUrl.isNotBlank() && inviteCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.catalog_enroll_this_device))
                    }
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.catalog_smart_enrollment_option),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.catalog_enrollment_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = smartText,
                    onValueChange = { smartText = it },
                    label = { Text(stringResource(R.string.catalog_smart_enrollment_text)) },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !state.loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                )
                Button(
                    onClick = { onEnrollSmartText(smartText) },
                    enabled = !state.loading && smartText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.catalog_enroll_this_device))
                    }
                }

                if (state.deviceRegistered) {
                    Text(
                        stringResource(R.string.catalog_device_registered),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.catalog_remove_device_registration))
                    }
                } else if (state.configured) {
                    Text(
                        stringResource(R.string.catalog_legacy_connection_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.catalog_remove_legacy_connection))
                    }
                }
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogAdminInviteScreen(
    state: CatalogUiState,
    onIssueInvite: (String, String, String, String, String, Boolean) -> Unit,
    onClearInviteUiState: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by rememberSaveable(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var adminUsername by rememberSaveable { mutableStateOf("") }
    var adminPassword by rememberSaveable { mutableStateOf("") }
    var userId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var replaceExistingDevice by rememberSaveable { mutableStateOf(false) }
    val miniPlayerBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()

    CollapsibleHeaderScreen(
        title = stringResource(R.string.catalog_admin_issue_invite),
        showBackButton = true,
        onBackClick = onBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .then(modifier)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp + miniPlayerBottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CatalogSettingsSection {
                Text(
                    stringResource(R.string.catalog_admin_http_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.catalog_server_address)) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adminUsername,
                    onValueChange = { adminUsername = it },
                    label = { Text(stringResource(R.string.catalog_admin_username)) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    label = { Text(stringResource(R.string.catalog_admin_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text(stringResource(R.string.catalog_user_id)) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.catalog_user_display_name)) },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.catalog_replace_existing_device))
                        Text(
                            stringResource(R.string.catalog_replace_existing_device_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = replaceExistingDevice,
                        onCheckedChange = { replaceExistingDevice = it },
                        enabled = !state.loading,
                    )
                }
                OutlinedButton(
                    onClick = {
                        onIssueInvite(
                            serverUrl,
                            adminUsername,
                            adminPassword,
                            userId,
                            displayName,
                            replaceExistingDevice,
                        )
                        adminPassword = ""
                    },
                    enabled = !state.loading &&
                        serverUrl.isNotBlank() &&
                        adminUsername.isNotBlank() &&
                        adminPassword.isNotBlank() &&
                        userId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(R.string.catalog_generate_invite))
                    }
                }
                state.adminError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    state.issuedInvite?.let { issuedInvite ->
        CatalogIssuedInviteDialog(
            invite = issuedInvite,
            serverUrl = serverUrl,
            onDismiss = onClearInviteUiState,
        )
    }
}

@Composable
internal fun CatalogIssuedInviteDialog(
    invite: CatalogIssuedInvite,
    serverUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val formattedExpiry = formatInviteExpiry(invite.expiresAt)

    fun createSmartText(): String = context.getString(
        R.string.catalog_smart_enrollment_share_text,
        CatalogSmartEnrollmentText.encode(
            normalizedServerUrl = CatalogEndpoint.normalize(serverUrl),
            inviteCode = invite.inviteCode,
        ),
    )

    fun withSmartText(action: (String) -> Unit) {
        runCatching { createSmartText() }
            .onSuccess(action)
            .onFailure {
                RhythmMessages.error(context, R.string.catalog_smart_enrollment_generation_failed)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_invite_generated)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.catalog_invite_for_user, invite.userId))
                Text(
                    text = invite.inviteCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.catalog_invite_expires_at, formattedExpiry))
                Text(
                    stringResource(R.string.catalog_smart_enrollment_admin_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Sonorus invitation", invite.inviteCode))
                        RhythmMessages.success(context, R.string.catalog_invite_copied)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.catalog_copy_invite))
                }
                Button(
                    onClick = {
                        withSmartText { smartText ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Sonorus smart enrollment", smartText))
                            RhythmMessages.success(context, R.string.catalog_smart_enrollment_copied)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.catalog_copy_smart_enrollment))
                }
                OutlinedButton(
                    onClick = {
                        withSmartText { smartText ->
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, smartText)
                                    },
                                    context.getString(R.string.catalog_share_smart_enrollment),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.catalog_share_smart_enrollment))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sonorus_close))
            }
        },
    )
}

internal fun formatInviteExpiry(value: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

@Composable
private fun CatalogSettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}
