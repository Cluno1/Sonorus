package io.github.cluno1.sonorus.shared.presentation.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/** Carries the kind so the host can color the snackbar from the Rhythm theme. */
class RhythmSnackbarVisuals(
    override val message: String,
    val kind: RhythmMessageKind,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

/**
 * Sonorus issue 169: the app-wide message host, mounted once at the root of each Activity's content.
 * See [RhythmMessages] for the invariants (newest wins, background fallback).
 */
@Composable
fun RhythmMessageHost(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var counted = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!counted) {
                    RhythmMessages.bus.hostVisible()
                    counted = true
                }
                Lifecycle.Event.ON_STOP -> if (counted) {
                    RhythmMessages.bus.hostHidden()
                    counted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (counted) RhythmMessages.bus.hostHidden()
        }
    }

    val hostState = RhythmMessages.hostState
    LaunchedEffect(Unit) {
        RhythmMessages.bus.latest.filterNotNull().collectLatest { message ->
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                RhythmSnackbarVisuals(
                    message = message.text,
                    kind = message.kind,
                    actionLabel = message.actionLabel,
                    duration = when {
                        message.actionLabel != null -> SnackbarDuration.Long
                        message.long -> SnackbarDuration.Long
                        else -> SnackbarDuration.Short
                    },
                )
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            RhythmMessages.bus.consumed(message.id)
        }
    }

    val insets by RhythmMessages.bottomInsets.collectAsState()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = (insets.lastOrNull()?.second ?: 0.dp).coerceAtLeast(navBar)

    Box(modifier = modifier.fillMaxSize().zIndex(100f)) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottom + 8.dp),
        ) { data -> RhythmSnackbar(data) }
    }
}

@Composable
private fun RhythmSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? RhythmSnackbarVisuals)?.kind ?: RhythmMessageKind.INFO
    val colors = MaterialTheme.colorScheme
    val (container, content, glyph) = when (kind) {
        RhythmMessageKind.SUCCESS -> Triple(colors.primaryContainer, colors.onPrimaryContainer, "check_circle")
        RhythmMessageKind.ERROR -> Triple(colors.errorContainer, colors.onErrorContainer, "error")
        RhythmMessageKind.INFO -> Triple(colors.inverseSurface, colors.inverseOnSurface, "info")
    }
    Snackbar(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = container,
        contentColor = content,
        actionContentColor = content,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = { data.performAction() }) { Text(label, color = content) }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = MaterialSymbolIcon(glyph, filled = true), contentDescription = null, tint = content)
            Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
