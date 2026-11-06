package io.github.cluno1.sonorus.shared.presentation.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon

/**
 * issue 170: the one small side button of the player, the lyrics pages and the score page.
 *
 * The skeleton is the player's lyrics-mode corner button (ExpressivePlayerScreen, immersive
 * button): a 40dp circle, no border, a 20dp icon, colours from the page. Every side button on
 * those pages uses this so they cannot drift apart again (before issue 170 there were tonal 44dp
 * buttons, small FABs and a "icon + text + arrow" pill side by side).
 *
 * Colours: the player passes its own glass colours (they follow its accent / artwork / mono
 * background). Other pages use [RhythmSideButtonDefaults.themeGlass], which follows the app theme
 * (issue 164: the lyrics pages use the theme background).
 *
 * The popup ([RhythmGlassMenu]) is semi-transparent with a hairline border. A real backdrop blur
 * is not used: a DropdownMenu is its own window and Compose cannot blur what is behind it, so a
 * higher alpha keeps the text readable instead.
 */
@Immutable
data class RhythmSideButtonColors(
    val container: Color,
    val content: Color,
    val selectedContainer: Color,
    val selectedContent: Color,
    val menuContainer: Color,
    val menuContent: Color,
    val menuBorder: Color,
)

object RhythmSideButtonDefaults {
    val Size = 40.dp
    val IconSize = 20.dp
    /** Gap between two side buttons in a column. */
    val Spacing = 12.dp

    @Composable
    fun themeGlass(): RhythmSideButtonColors {
        val scheme = MaterialTheme.colorScheme
        return RhythmSideButtonColors(
            container = scheme.surfaceContainerHigh.copy(alpha = 0.82f),
            content = scheme.onSurface,
            selectedContainer = scheme.primary.copy(alpha = 0.35f),
            selectedContent = scheme.primary,
            menuContainer = scheme.surfaceContainerHigh.copy(alpha = 0.9f),
            menuContent = scheme.onSurface,
            menuBorder = scheme.onSurface.copy(alpha = 0.12f),
        )
    }

    /** Player colours: [container]/[content] are the player's control colours. */
    @Composable
    fun player(container: Color, content: Color, accent: Color): RhythmSideButtonColors =
        RhythmSideButtonColors(
            container = container,
            content = content,
            selectedContainer = accent.copy(alpha = 0.35f),
            selectedContent = accent,
            menuContainer = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            menuContent = MaterialTheme.colorScheme.onSurface,
            menuBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
}

@Composable
fun RhythmSideIconButton(
    icon: MaterialSymbolIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
) {
    Box(
        modifier = modifier
            .size(RhythmSideButtonDefaults.Size)
            .clip(CircleShape)
            .background(colors.container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(RhythmSideButtonDefaults.IconSize),
        )
    }
}

/** On/off variant (source images, romanization): selected uses the accent, like the player's favorite. */
@Composable
fun RhythmSideToggleButton(
    icon: MaterialSymbolIcon,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
) {
    Box(
        modifier = modifier
            .size(RhythmSideButtonDefaults.Size)
            .clip(CircleShape)
            .background(if (checked) colors.selectedContainer else colors.container)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) colors.selectedContent else colors.content,
            modifier = Modifier.size(RhythmSideButtonDefaults.IconSize),
        )
    }
}

/** The glass popup that side buttons open (issue 170). */
@Composable
fun RhythmGlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.menuContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, colors.menuBorder),
        modifier = Modifier.widthIn(min = 160.dp),
        content = content,
    )
}

/** One row of [RhythmGlassMenu]: full [label], optional short [tag] before it, a check when [selected]. */
@Composable
fun RhythmGlassMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: RhythmSideButtonColors = RhythmSideButtonDefaults.themeGlass(),
    tag: String? = null,
) {
    DropdownMenuItem(
        text = { Text(text = label, maxLines = 1) },
        onClick = onClick,
        leadingIcon = tag?.let { t ->
            {
                Text(
                    text = t,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.menuContent.copy(alpha = 0.7f),
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = MaterialSymbolIcon("check"),
                    contentDescription = null,
                    tint = colors.selectedContent,
                )
            }
        } else {
            null
        },
        colors = MenuDefaults.itemColors(
            textColor = colors.menuContent,
            leadingIconColor = colors.menuContent,
            trailingIconColor = colors.selectedContent,
        ),
    )
}
