package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtworkPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.shared.presentation.components.icons.RhythmIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest edge a page is decoded at; enough for the 4x zoom cap without unbounded bitmaps. */
private const val PAGE_MAX_DECODE_PX = 2048

/** Below this release speed (px/s) a drag just stops instead of flinging. */
private const val FLING_MIN_VELOCITY = 50f

/**
 * Source pages of a song (issue 52 / 117), shown as one bounded canvas (issue 123).
 *
 * Every page of the selected document is stacked on one canvas the reader pinches around the
 * fingers and drags with one finger at any zoom, inside the fixed range of
 * [LyricSourceCanvasBounds]; double tap or the "fit width" button brings it back. The same view
 * is used on phones (the whole lyrics area) and tablets (the left pane).
 *
 * The share button sends the selected document's pages as one image — the page itself, or all
 * pages combined ([LyricSourceSharer]); [shareTitle] names the file (falls back to the document title).
 *
 * Only pages near the viewport are composed, so long documents keep a bounded number of decoded
 * bitmaps; transforms only change the draw matrix and never re-decode.
 */
@Composable
internal fun CatalogLyricSourceImagesView(
    images: List<CatalogLyricSourceImage>,
    modifier: Modifier = Modifier,
    shareTitle: String? = null,
) {
    val documents = remember(images) {
        images.distinctBy(CatalogLyricSourceImage::documentId)
    }
    var selectedDocumentId by remember(documents) {
        mutableStateOf(documents.firstOrNull()?.documentId)
    }
    val selectedPages = remember(images, selectedDocumentId) {
        LyricSourceSharePolicy.pagesOf(images, selectedDocumentId)
    }
    // A new document, or reopening the view, starts at fit width.
    val controller = remember(selectedDocumentId) { LyricSourceCanvasController() }

    Column(modifier = modifier) {
        if (documents.size > 1) {
            LyricSourceDocumentPicker(
                documents = documents,
                selectedDocumentId = selectedDocumentId,
                selectedTitle = selectedPages.firstOrNull()?.documentTitle.orEmpty(),
                onSelect = { selectedDocumentId = it },
            )
        }
        Box(Modifier.fillMaxSize()) {
            LyricSourceCanvas(pages = selectedPages, controller = controller)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = !controller.isFit,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    LyricSourceToolbarButton(
                        icon = "fit_width",
                        description = stringResource(R.string.lyric_source_fit_width),
                        onClick = controller::animateToFit,
                    )
                }
                if (selectedPages.isNotEmpty()) {
                    LyricSourceShareButton(
                        pages = selectedPages,
                        title = shareTitle?.takeIf(String::isNotBlank)
                            ?: selectedPages.first().documentTitle,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricSourceDocumentPicker(
    documents: List<CatalogLyricSourceImage>,
    selectedDocumentId: String?,
    selectedTitle: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FilledTonalButton(
            onClick = { expanded = true },
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                imageVector = MaterialSymbolIcon("menu_book"),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = MaterialSymbolIcon("arrow_drop_down"),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.align(Alignment.Center),
        ) {
            documents.forEach { document ->
                DropdownMenuItem(
                    text = { Text(document.documentTitle) },
                    onClick = {
                        expanded = false
                        onSelect(document.documentId)
                    },
                    trailingIcon = if (document.documentId == selectedDocumentId) {
                        {
                            Icon(
                                imageVector = MaterialSymbolIcon("check"),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun LyricSourceToolbarButton(
    icon: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    // Floats over a white page: a tonal, slightly translucent container keeps it readable there
    // and in dark theme without hiding the score underneath.
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        ),
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                imageVector = MaterialSymbolIcon(icon),
                contentDescription = description,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LyricSourceShareButton(pages: List<CatalogLyricSourceImage>, title: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = remember { LyricSourceShareGate() }
    var preparing by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.lyric_source_share_chooser)
    val failedMessage = stringResource(R.string.lyric_source_share_failed)

    // One tap shares one image straight away: the page, or all pages combined (no menu).
    fun share() {
        if (!gate.tryStart()) return
        preparing = true
        val request = LyricSourceShareRequest(
            title = title,
            pages = pages,
            caption = LyricSourceSharePolicy.caption(title, pages),
        )
        val sharer = LyricSourceSharer(context)
        scope.launch {
            try {
                sharer.prepare(request).fold(
                    onSuccess = { file -> sharer.launch(file, request.caption, chooserTitle) },
                    onFailure = { Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show() },
                )
            } finally {
                preparing = false
                gate.finish()
            }
        }
    }

    LyricSourceToolbarButton(
        icon = "share",
        description = stringResource(R.string.lyric_source_share),
        onClick = ::share,
        enabled = !preparing,
        content = if (preparing) {
            { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
        } else {
            null
        },
    )
}

/** Transform state of one canvas, plus its settle / fling / reset animations. */
@Stable
internal class LyricSourceCanvasController {
    var transform by mutableStateOf(LyricSourceCanvasTransform())
        private set
    var bounds by mutableStateOf<LyricSourceCanvasBounds?>(null)
        private set
    val isFit: Boolean get() = bounds?.isFit(transform) ?: true

    private var scope: CoroutineScope? = null
    private var animation: Job? = null
    private var lastPivot = Offset.Zero

    internal fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    /** A new viewport width re-fits (the canvas is laid out at fit width); a new height only clamps. */
    internal fun updateBounds(next: LyricSourceCanvasBounds) {
        val previous = bounds
        bounds = next
        stop()
        transform = if (previous == null || previous.viewportWidth != next.viewportWidth ||
            previous.contentHeight != next.contentHeight
        ) {
            next.fit()
        } else {
            // e.g. insets or a mini player changed the height: keep the reading position.
            next.clamp(transform)
        }
    }

    internal fun stop() {
        animation?.cancel()
        animation = null
    }

    internal fun transformBy(zoom: Float, pan: Offset, pivot: Offset) {
        val current = bounds ?: return
        lastPivot = pivot
        var next = transform
        if (zoom != 1f) next = current.zoom(next, zoom, pivot.x, pivot.y, elastic = true)
        next = current.pan(next, pan.x, pan.y, rubberBand = true)
        transform = next
    }

    /** Finger(s) lifted: snap back inside the range, or keep gliding with [velocity] (px/s). */
    internal fun release(velocity: Offset) {
        val current = bounds ?: return
        if (!current.isWithinBounds(transform)) {
            animateTo(current.settle(transform, lastPivot.x, lastPivot.y))
        } else if (abs(velocity.x) > FLING_MIN_VELOCITY || abs(velocity.y) > FLING_MIN_VELOCITY) {
            fling(velocity)
        }
    }

    internal fun canPanHorizontally(): Boolean = bounds?.canPanHorizontally(transform.scale) ?: false

    internal fun doubleTap(at: Offset) {
        val current = bounds ?: return
        animateTo(current.doubleTap(transform, at.x, at.y))
    }

    fun animateToFit() {
        val current = bounds ?: return
        // Back to fit width around the viewport's top edge, keeping the page being read in view.
        animateTo(current.clamp(current.zoom(transform, current.minScale / transform.scale, 0f, 0f, elastic = false)))
    }

    private fun animateTo(target: LyricSourceCanvasTransform) {
        val runner = scope ?: return
        stop()
        val start = transform
        animation = runner.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { fraction, _ ->
                transform = LyricSourceCanvasTransform(
                    scale = start.scale + (target.scale - start.scale) * fraction,
                    offsetX = start.offsetX + (target.offsetX - start.offsetX) * fraction,
                    offsetY = start.offsetY + (target.offsetY - start.offsetY) * fraction,
                )
            }
        }
    }

    private fun fling(velocity: Offset) {
        val runner = scope ?: return
        stop()
        val glide = Animatable(Offset(transform.offsetX, transform.offsetY), Offset.VectorConverter)
        animation = runner.launch {
            glide.animateDecay(velocity, exponentialDecay()) {
                val current = bounds ?: return@animateDecay
                val next = current.clamp(transform.copy(offsetX = value.x, offsetY = value.y))
                transform = next
                // Both axes pinned at the edge: nothing left to glide.
                val pinnedX = next.offsetX != value.x || velocity.x == 0f
                val pinnedY = next.offsetY != value.y || velocity.y == 0f
                if (pinnedX && pinnedY) animation?.cancel()
            }
        }
    }
}

@Composable
private fun LyricSourceCanvas(
    pages: List<CatalogLyricSourceImage>,
    controller: LyricSourceCanvasController,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    controller.attach(scope)
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val layout = remember(pages, viewport.width, density) {
        if (viewport.width <= 0) {
            null
        } else {
            with(density) {
                layoutLyricSourcePages(
                    heightToWidthRatios = pages.map { it.heightPx.toFloat() / it.widthPx.toFloat() },
                    viewportWidth = viewport.width.toFloat(),
                    horizontalPadding = 12.dp.toPx(),
                    labelHeight = 26.dp.toPx(),
                    gap = 12.dp.toPx(),
                    bottomPadding = 96.dp.toPx(),
                )
            }
        }
    }
    val nextBounds = remember(layout, viewport.height, pages) {
        if (layout == null || viewport.height <= 0) {
            null
        } else {
            LyricSourceCanvasBounds(
                viewportWidth = viewport.width.toFloat(),
                viewportHeight = viewport.height.toFloat(),
                contentWidth = layout.contentWidth,
                contentHeight = layout.contentHeight,
                maxScale = lyricSourceMaxScale(
                    pages.maxOfOrNull(CatalogLyricSourceImage::widthPx) ?: 0,
                    layout.imageWidth,
                ),
            )
        }
    }
    LaunchedEffect(nextBounds) {
        nextBounds?.let(controller::updateBounds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(controller) {
                detectTapGestures(onDoubleTap = controller::doubleTap)
            }
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    controller.stop()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pointerCount = 1
                    var zoomTotal = 1f
                    var panTotal = Offset.Zero
                    var transforming = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size != pointerCount) {
                            // Adding or lifting a finger jumps the centroid; restart the velocity.
                            pointerCount = pressed.size
                            tracker.resetTracking()
                        }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (!transforming) {
                            zoomTotal *= zoom
                            panTotal += pan
                            val zoomMotion = abs(1f - zoomTotal) * event.calculateCentroidSize(useCurrent = false)
                            transforming = zoomMotion > viewConfiguration.touchSlop ||
                                panTotal.getDistance() > viewConfiguration.touchSlop
                            // At fit width a mostly sideways one-finger swipe cannot move the
                            // canvas: leave it to the surrounding screen instead of eating it.
                            if (transforming && pressed.size == 1 && zoomMotion <= viewConfiguration.touchSlop &&
                                abs(panTotal.x) > abs(panTotal.y) && !controller.canPanHorizontally()
                            ) {
                                transforming = false
                                break
                            }
                        }
                        if (transforming) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            controller.transformBy(zoom, pan, centroid)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (pressed.size == 1) tracker.addPosition(pressed[0].uptimeMillis, pressed[0].position)
                    }
                    if (transforming) {
                        val velocity = if (pointerCount == 1) tracker.calculateVelocity() else null
                        controller.release(velocity?.let { Offset(it.x, it.y) } ?: Offset.Zero)
                    }
                }
            },
    ) {
        val bounds = controller.bounds
        if (layout == null || bounds == null) return@Box
        val transform = controller.transform
        // Compose the pages within one viewport above and below what is visible.
        val top = bounds.visibleContentTop(transform)
        val bottom = bounds.visibleContentBottom(transform)
        val margin = bottom - top
        val visible = layout.pagesIn(top - margin, bottom + margin)
        with(density) {
            Box(
                modifier = Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(layout.contentWidth.toDp(), layout.contentHeight.toDp())
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                    },
            ) {
                for (index in visible) {
                    val page = pages[index]
                    val slot = layout.slots[index]
                    key(page.sourcePageId) {
                        LyricSourceCanvasPage(
                            page = page,
                            modifier = Modifier.offset {
                                IntOffset(layout.imageLeft.roundToInt(), slot.labelTop.roundToInt())
                            },
                            labelHeightDp = (slot.imageTop - slot.labelTop).toDp(),
                            imageWidthDp = layout.imageWidth.toDp(),
                            imageHeightDp = slot.imageHeight.toDp(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricSourceCanvasPage(
    page: CatalogLyricSourceImage,
    modifier: Modifier,
    labelHeightDp: androidx.compose.ui.unit.Dp,
    imageWidthDp: androidx.compose.ui.unit.Dp,
    imageHeightDp: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    var loadFailed by remember(page.sourcePageId) { mutableStateOf(false) }
    var retryKey by remember(page.sourcePageId) { mutableIntStateOf(0) }
    val pageLabel = page.displayLabel
        ?: stringResource(R.string.lyrics_source_pdf_page, page.physicalPageNumber)
    val request = remember(page.imageAssetId, retryKey) {
        // Decode near source resolution (capped) so zooming in stays sharp.
        val longEdge = maxOf(page.widthPx, page.heightPx)
        val factor = min(1f, PAGE_MAX_DECODE_PX.toFloat() / longEdge)
        ImageRequest.Builder(context)
            .data(Uri.parse(CatalogArtworkPolicy.uri(page.imageAssetId)))
            .size((page.widthPx * factor).roundToInt().coerceAtLeast(1), (page.heightPx * factor).roundToInt().coerceAtLeast(1))
            // Scanned hymnal pages are mostly black on white: 565 halves the bitmap memory.
            .allowRgb565(true)
            .build()
    }
    Column(modifier = modifier) {
        Text(
            text = "${page.documentTitle} · $pageLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .requiredSize(imageWidthDp, labelHeightDp)
                .padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.requiredSize(imageWidthDp, imageHeightDp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = request,
                    contentDescription = stringResource(
                        R.string.lyrics_source_page_description,
                        page.documentTitle,
                        page.physicalPageNumber,
                    ),
                    contentScale = ContentScale.Fit,
                    onSuccess = { loadFailed = false },
                    onError = { loadFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (loadFailed) {
                    FilledTonalButton(
                        onClick = {
                            loadFailed = false
                            retryKey += 1
                        },
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.updates_retry))
                    }
                }
            }
        }
    }
}
