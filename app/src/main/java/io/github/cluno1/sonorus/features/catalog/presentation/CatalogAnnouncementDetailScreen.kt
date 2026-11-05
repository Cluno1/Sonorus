package io.github.cluno1.sonorus.features.catalog.presentation

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementBlock
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementInline
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementDetail
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementImagePolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import io.github.cluno1.sonorus.shared.presentation.components.common.M3CircularLoader
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
import io.github.cluno1.sonorus.ui.LocalMiniPlayerPadding

private const val TAG = "CatalogAnnouncements"

private sealed interface DetailState {
    data object Loading : DetailState
    data object Failed : DetailState
    data object Gone : DetailState
    data class Loaded(val detail: CatalogAnnouncementDetail) : DetailState
}

/**
 * issue 154: the article, drawn natively so it follows the app theme. A reader only counts as having
 * read it once it actually loaded, so an important notice opened offline stays on the home screen.
 */
@Composable
fun CatalogAnnouncementDetailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { CatalogModule.announcements(context) }
    val serverUrl = remember { CatalogModule.repository(context).connection().serverUrl }
    var state by remember(id) { mutableStateOf<DetailState>(DetailState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id, attempt) {
        state = DetailState.Loading
        CatalogModule.announcementsRepository(context).announcement(id).fold(
            onSuccess = {
                controller.markRead(id)
                state = DetailState.Loaded(it)
            },
            onFailure = { error ->
                if (error is CatalogFailure.AnnouncementUnavailable) {
                    controller.remove(id)
                    state = DetailState.Gone
                } else {
                    Log.w(TAG, "announcement detail failed (${error.javaClass.simpleName})")
                    state = DetailState.Failed
                }
            },
        )
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.announcements_title),
        showBackButton = true,
        onBackClick = onBack,
    ) { modifier ->
        when (val current = state) {
            DetailState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { M3CircularLoader() }
            DetailState.Failed -> AnnouncementMessage(
                message = stringResource(R.string.announcements_load_failed),
                onRetry = { attempt++ },
                modifier = modifier,
            )
            DetailState.Gone -> AnnouncementMessage(
                message = stringResource(R.string.announcements_gone),
                onRetry = null,
                modifier = modifier,
            )
            is DetailState.Loaded -> AnnouncementArticle(
                detail = current.detail,
                serverUrl = serverUrl,
                onImageClick = { fullscreenUrl = it },
                modifier = modifier,
            )
        }
    }

    fullscreenUrl?.let { url ->
        AnnouncementImageViewer(url = url, onDismiss = { fullscreenUrl = null })
    }
}

@Composable
private fun AnnouncementArticle(
    detail: CatalogAnnouncementDetail,
    serverUrl: String,
    onImageClick: (String) -> Unit,
    modifier: Modifier,
) {
    val bottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = announcementDate(detail.summary.publishedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Blocks are immutable once loaded, so the position identifies each one.
        itemsIndexed(detail.document.blocks) { _, block ->
            AnnouncementBlockView(block, serverUrl, onImageClick)
        }
        if (detail.document.hasUnsupportedContent) {
            item(key = "unsupported") {
                Text(
                    text = stringResource(R.string.announcements_update_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementBlockView(
    block: AnnouncementBlock,
    serverUrl: String,
    onImageClick: (String) -> Unit,
) {
    when (block) {
        is AnnouncementBlock.Paragraph -> {
            if (block.inlines.isEmpty()) {
                Spacer(Modifier.height(4.dp))
            } else {
                Text(
                    text = rememberAnnouncementText(block.inlines),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is AnnouncementBlock.Heading -> Text(
            text = rememberAnnouncementText(block.inlines),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        is AnnouncementBlock.BulletList -> AnnouncementList(block.items, { "•" }, serverUrl, onImageClick)
        is AnnouncementBlock.OrderedList -> AnnouncementList(
            block.items,
            { index -> "${block.start + index}." },
            serverUrl,
            onImageClick,
        )
        is AnnouncementBlock.Quote -> {
            val barColor = MaterialTheme.colorScheme.outline
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(barColor, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height))
                    }
                    .padding(start = 15.dp),
            ) {
                block.blocks.forEach { AnnouncementBlockView(it, serverUrl, onImageClick) }
            }
        }
        AnnouncementBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is AnnouncementBlock.Image -> AnnouncementImage(block, serverUrl, onImageClick)
    }
}

@Composable
private fun AnnouncementList(
    items: List<List<AnnouncementBlock>>,
    marker: (Int) -> String,
    serverUrl: String,
    onImageClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, blocks ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = marker(index),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    blocks.forEach { AnnouncementBlockView(it, serverUrl, onImageClick) }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementImage(
    block: AnnouncementBlock.Image,
    serverUrl: String,
    onImageClick: (String) -> Unit,
) {
    val url = remember(serverUrl, block.path) { CatalogAnnouncementImagePolicy.url(serverUrl, block.path) } ?: return
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) {
        Text(
            text = stringResource(R.string.announcements_image_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val ratio = if (block.width != null && block.height != null) {
        // Reserving the height keeps the article from jumping while the image loads.
        (block.width.toFloat() / block.height).coerceIn(0.25f, 4f)
    } else {
        null
    }
    AsyncImage(
        model = url,
        contentDescription = block.alt ?: stringResource(R.string.announcements_image),
        contentScale = ContentScale.FillWidth,
        onState = { if (it is AsyncImagePainter.State.Error) failed = true },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onImageClick(url) },
    )
}

@Composable
private fun rememberAnnouncementText(inlines: List<AnnouncementInline>): AnnotatedString {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val openFailed = stringResource(R.string.announcements_link_failed)
    return remember(inlines, linkColor, openFailed) {
        // openUri throws when no browser can take the link; that must not end the app.
        val listener = LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            try {
                uriHandler.openUri(url)
            } catch (error: RuntimeException) {
                Log.w(TAG, "could not open announcement link (${error.javaClass.simpleName})")
                Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
            }
        }
        val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            inlines.forEach { inline ->
                val style = SpanStyle(
                    fontWeight = if (inline.bold) FontWeight.Bold else null,
                    fontStyle = if (inline.italic) FontStyle.Italic else null,
                )
                val href = inline.href
                if (href != null) {
                    withLink(LinkAnnotation.Url(href, linkStyles, listener)) {
                        withStyle(style) { append(inline.text) }
                    }
                } else {
                    withStyle(style) { append(inline.text) }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementImageViewer(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.announcements_image),
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            ) {
                Icon(MaterialSymbolIcon("close"), contentDescription = stringResource(R.string.announcements_close))
            }
        }
    }
}
