package io.github.cluno1.sonorus.features.catalog.presentation

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoom
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomUser
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomWriteGate
import io.github.cluno1.sonorus.features.catalog.domain.roomUserLabel
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** issue 124: gathering dates are Beijing dates (UTC+8), same as the server's expiry rule. */
internal val ROOM_ZONE: ZoneOffset = ZoneOffset.ofHours(8)

/** Latest gathering date a room may be created for (server enforces the same 90 days). */
internal const val ROOM_MAX_DAYS_AHEAD = 90L

internal fun roomToday(): LocalDate = LocalDate.now(ROOM_ZONE)

@Composable
internal fun roomDateLabel(date: LocalDate): String {
    val configuration = LocalConfiguration.current
    val pattern = stringResource(R.string.rooms_date_pattern)
    val locale = remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    }
    return remember(date, pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale).format(date) }
}

@Composable
internal fun roomUserName(user: CatalogRoomUser): String =
    roomUserLabel(user, stringResource(R.string.rooms_user_fallback))

/** User-facing text for a failed room call; an expired or deleted room gets its own message. */
internal fun roomErrorMessage(context: Context, error: Throwable): String = when (error) {
    is CatalogFailure.RoomUnavailable -> context.getString(R.string.rooms_room_gone)
    is CatalogFailure.StaleRevision -> context.getString(R.string.rooms_stale)
    is CatalogFailure -> error.message ?: context.getString(R.string.rooms_action_failed)
    else -> context.getString(R.string.rooms_action_failed)
}

/**
 * Name + gathering date, then one create call. The idempotency key is made once per dialog, so a
 * retry after a lost response replays the same room instead of creating a second one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogCreateRoomDialog(
    onDismiss: () -> Unit,
    onCreated: (CatalogRoom) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = remember { CatalogRoomWriteGate() }
    val idempotencyKey = remember { UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(nextSunday(roomToday())) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.rooms_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(stringResource(R.string.rooms_create_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(MaterialSymbolIcon("event"), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = "  " + stringResource(R.string.rooms_create_date) + " · " + roomDateLabel(date),
                    )
                }
                Text(
                    text = stringResource(R.string.rooms_create_date_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy,
                onClick = {
                    if (!gate.tryStart()) return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            CatalogModule.rooms(context).createRoom(name, date, idempotencyKey).fold(
                                onSuccess = onCreated,
                                onFailure = { error = roomErrorMessage(context, it) },
                            )
                        } finally {
                            busy = false
                            gate.finish()
                        }
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.rooms_create_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.rooms_cancel)) }
        },
    )

    if (picking) {
        val today = roomToday()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return !day.isBefore(today) && !day.isAfter(today.plusDays(ROOM_MAX_DAYS_AHEAD))
                    }

                    override fun isSelectableYear(year: Int): Boolean =
                        year in today.year..today.plusDays(ROOM_MAX_DAYS_AHEAD).year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        // The picker hands back midnight UTC of the chosen calendar day.
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    picking = false
                }) { Text(stringResource(R.string.rooms_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.rooms_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Default gathering date: the coming Sunday (today when it already is Sunday). */
internal fun nextSunday(today: LocalDate): LocalDate =
    today.plusDays(((7 - today.dayOfWeek.value) % 7).toLong())
