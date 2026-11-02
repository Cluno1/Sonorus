package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDevice
import io.github.cluno1.sonorus.shared.presentation.components.Material3SettingsGroup
import io.github.cluno1.sonorus.shared.presentation.components.Material3SettingsItem
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.screens.settings.TunerAnimatedSwitch

@Composable
fun DeviceAdminScreen(
    state: CatalogUiState,
    viewModel: CatalogViewModel,
    onOpenDeviceRegistration: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(state.deviceRegistered) {
        if (state.deviceRegistered) viewModel.refreshAdministrator()
    }
    CollapsibleHeaderScreen(
        title = stringResource(R.string.device_admin_title),
        showBackButton = true,
        onBackClick = onBack,
    ) { modifier ->
        when {
            !state.deviceRegistered -> AdministratorDeviceRequired(onOpenDeviceRegistration, modifier)
            state.administratorDashboard != null -> AdministratorDashboard(
                state = state,
                onIssueInvite = viewModel::issueInviteAsAdministrator,
                onSetAdministrator = viewModel::setDeviceAdministrator,
                onRefresh = viewModel::refreshAdministrator,
                modifier = modifier,
            )
            else -> AdministratorLogin(
                state = state,
                onLogin = viewModel::authenticateAdministrator,
                onSetAdministrator = viewModel::setDeviceAdministrator,
                modifier = modifier,
            )
        }
    }
    if (state.administratorDashboard != null) {
        state.issuedInvite?.let { invite ->
            CatalogIssuedInviteDialog(invite, state.serverUrl, viewModel::clearInviteUiState)
        }
    }
}

@Composable
private fun AdministratorDeviceRequired(
    onOpenDeviceRegistration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(MaterialSymbolIcon("admin_panel_settings", filled = true), null, Modifier.size(44.dp))
                Text(stringResource(R.string.device_admin_device_required), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.device_admin_device_required_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenDeviceRegistration) {
                    Text(stringResource(R.string.device_admin_open_registration))
                }
            }
        }
    }
}

@Composable
private fun AdministratorLogin(
    state: CatalogUiState,
    onLogin: (String, String) -> Unit,
    onSetAdministrator: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.administratorSessionActive) {
        if (state.administratorSessionActive) password = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.administratorSessionActive) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.device_admin_login_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.device_admin_login_desc),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.serverUrl.startsWith("http://")) {
                        Text(
                            stringResource(R.string.device_admin_http_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.catalog_admin_username)) },
                        enabled = !state.administratorLoading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.catalog_admin_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !state.administratorLoading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onLogin(username, password) },
                        enabled = username.isNotBlank() && password.isNotEmpty() && !state.administratorLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.device_admin_login_action))
                    }
                }
            }
        } else {
            Text(stringResource(R.string.device_admin_choose_device), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.device_admin_choose_device_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeviceList(
                devices = state.administratorDevices,
                currentDeviceId = state.currentDeviceId,
                busy = state.administratorLoading,
                onSetAdministrator = onSetAdministrator,
            )
        }
        state.adminError?.let { ErrorMessage(it) }
        if (state.administratorLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun AdministratorDashboard(
    state: CatalogUiState,
    onIssueInvite: (String, String, Boolean) -> Unit,
    onSetAdministrator: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashboard = requireNotNull(state.administratorDashboard)
    var userId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var replaceDevice by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.device_admin_device_active), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onRefresh, enabled = !state.administratorLoading) {
            Text(stringResource(R.string.device_admin_refresh))
        }
        Text(stringResource(R.string.device_admin_invite_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = userId, onValueChange = { userId = it },
            label = { Text(stringResource(R.string.catalog_user_id)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = displayName, onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.catalog_user_display_name)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.catalog_replace_existing_device), modifier = Modifier.weight(1f))
            TunerAnimatedSwitch(checked = replaceDevice, onCheckedChange = { replaceDevice = it })
        }
        Button(onClick = { onIssueInvite(userId, displayName, replaceDevice) },
            enabled = userId.isNotBlank() && !state.administratorLoading) {
            Text(stringResource(R.string.device_admin_invite_action))
        }
        Text(stringResource(R.string.device_admin_workspace_devices), style = MaterialTheme.typography.titleMedium)
        DeviceList(dashboard.devices, state.currentDeviceId, state.administratorLoading, onSetAdministrator)
        state.adminError?.let { ErrorMessage(it) }
        if (state.administratorLoading) CircularProgressIndicator()
    }
}

@Composable
private fun DeviceList(
    devices: List<CatalogAdminDevice>,
    currentDeviceId: String?,
    busy: Boolean,
    onSetAdministrator: (String, Boolean) -> Unit,
) {
    val currentDeviceLabel = stringResource(R.string.device_admin_current_device)
    Material3SettingsGroup(
        items = devices.map { device ->
            val current = device.deviceId == currentDeviceId
            Material3SettingsItem(
                icon = MaterialSymbolIcon("smartphone", filled = current),
                title = {
                    Text(
                        device.displayName ?: device.userId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                description = {
                    Text(
                        buildString {
                            append(device.userId)
                            if (current) append(" · ").append(currentDeviceLabel)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = { onSetAdministrator(device.deviceId, !device.isAdministrator) },
                        enabled = device.status == "active" && !busy,
                    ) {
                        Text(
                            stringResource(
                                if (device.isAdministrator) {
                                    R.string.device_admin_device_revoke
                                } else {
                                    R.string.device_admin_device_grant
                                },
                            ),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}

@Composable
private fun ErrorMessage(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        )
    }
}

