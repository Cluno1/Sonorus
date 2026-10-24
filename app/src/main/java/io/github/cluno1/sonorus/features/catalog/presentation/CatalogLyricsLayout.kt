package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Below this window size (dp, both axes) lyrics pages keep their phone behaviour. */
private const val TABLET_LYRICS_MIN_DP = 600

/**
 * issue 117/118: true when the lyrics detail uses the tablet split (left pane + right lyrics).
 *
 * Both window axes must reach 600dp, so a landscape phone (height ≈ 400dp) never gets the new
 * tablet behaviour and a tablet split-screen window narrower than 600dp falls back to one column.
 * Uses the live window size, so rotation and multi-window resizing are followed.
 */
@Composable
fun isTabletLyricsLayout(): Boolean = with(LocalDensity.current) {
    val size = LocalWindowInfo.current.containerSize
    val widthDp = size.width.toDp().value.roundToInt()
    val heightDp = size.height.toDp().value.roundToInt()
    minOf(widthDp, heightDp) >= TABLET_LYRICS_MIN_DP
}

/**
 * issue 117: tablet lyrics split. [left] is the entry's own content (player, or nothing for a Work
 * opened from search); [right] is the lyrics detail. When [leftOverlay] is non-null it is drawn
 * above [left], clipped to the left pane only: the left content stays composed underneath (so
 * playback is never rebuilt) while the opaque [Surface] keeps its controls from showing through
 * or receiving touches. The right pane is never covered.
 */
@Composable
fun CatalogLyricsTabletSplit(
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.4f,
    leftOverlay: (@Composable BoxScope.() -> Unit)? = null,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(leftWeight)
                .fillMaxHeight(),
        ) {
            left()
            if (leftOverlay != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(Modifier.fillMaxSize()) { leftOverlay() }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        right()
    }
}
