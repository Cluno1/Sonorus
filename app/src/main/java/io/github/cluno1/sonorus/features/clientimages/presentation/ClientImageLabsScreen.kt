package io.github.cluno1.sonorus.features.clientimages.presentation

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferBatchEntity
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferState
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageDelivery
import io.github.cluno1.sonorus.features.clientimages.domain.ClientImageRecord
import io.github.cluno1.sonorus.features.clientimages.domain.SelectedClientImage
import io.github.cluno1.sonorus.shared.presentation.components.common.CollapsibleHeaderScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientImageLabsScreen(
    onBackClick: () -> Unit,
    viewModel: ClientImageLabsViewModel = viewModel(),
) {
    val selected by viewModel.selected.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val visibility by viewModel.visibility.collectAsState()
    val adminAvailable by viewModel.administratorViewAvailable.collectAsState()
    val deliveries by viewModel.deliveries.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val legacyDownload by viewModel.legacyDownload.collectAsState()
    val ownImages = viewModel.ownImages.collectAsLazyPagingItems()
    val sharedImages = viewModel.sharedImages.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var wifiOnly by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<ClientImageRecord?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
        viewModel::addUris,
    )
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addTree)
    }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/*"),
        viewModel::completeLegacyDownload,
    )

    LaunchedEffect(Unit) {
        viewModel.messages.collect(snackbar::showSnackbar)
    }
    LaunchedEffect(legacyDownload?.id) {
        legacyDownload?.let { image -> createDocument.launch(downloadName(image)) }
    }
    LaunchedEffect(tab, adminAvailable) {
        if (tab == 2 && adminAvailable) {
            while (true) {
                delay(60_000)
                viewModel.revalidateSharedAccess(sharedImages::refresh)
            }
        }
    }

    CollapsibleHeaderScreen(
        title = "Labs 图片",
        showBackButton = true,
        onBackClick = onBackClick,
    ) { modifier ->
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val tabs = buildList {
                    add("上传")
                    add("我的图片")
                    if (adminAvailable) add("用户共享")
                }
                if (tab >= tabs.size) tab = 0
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) },
                        )
                    }
                }
                when (tab) {
                    0 -> UploadTab(
                        selected = selected,
                        batches = batches,
                        featureEnabled = capabilities?.enabled == true,
                        unavailableReason = capabilities?.unavailableReason,
                        maxBytes = capabilities?.maxImageBytes,
                        wifiOnly = wifiOnly,
                        busy = busy,
                        visibilityEnabled = visibility?.enabled == true,
                        onWifiOnlyChange = { wifiOnly = it },
                        onSelectImages = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onSelectFolder = { folderPicker.launch(null) },
                        onRemove = viewModel::removeSelected,
                        onClear = viewModel::clearSelection,
                        onUpload = { viewModel.startUpload(wifiOnly) },
                        onPause = viewModel::pauseBatch,
                        onResume = viewModel::resumeBatch,
                        onCancel = viewModel::cancelBatch,
                        onVisibilityChange = { enabled ->
                            if (enabled) confirmShare = true else viewModel.updateVisibility(false)
                        },
                    )
                    1 -> ImageGallery(
                        images = ownImages,
                        deliveries = deliveries,
                        shared = false,
                        onRequestThumbnail = viewModel::requestThumbnail,
                        onOpen = { viewModel.openPreview(it, false) },
                    )
                    else -> ImageGallery(
                        images = sharedImages,
                        deliveries = deliveries,
                        shared = true,
                        onRequestThumbnail = viewModel::requestThumbnail,
                        onOpen = { viewModel.openPreview(it, true) },
                    )
                }
            }
        }
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("允许管理员查看图片？") },
            text = {
                Text("开启后，拥有 image:read-shared 权限的管理员可在客户端和后台查看缩略图与预览图，但不能下载原图、删除图片或替你绑定内容。你可以随时关闭。")
            },
            confirmButton = {
                Button(onClick = {
                    confirmShare = false
                    viewModel.updateVisibility(true)
                }) { Text("允许查看") }
            },
            dismissButton = {
                TextButton(onClick = { confirmShare = false }) { Text("取消") }
            },
        )
    }

    preview?.let { state ->
        ImagePreviewDialog(
            record = state.record,
            delivery = state.delivery,
            shared = state.shared,
            onDismiss = viewModel::closePreview,
            onDownload = { viewModel.download(state.record) },
            onDelete = { deleteCandidate = state.record },
        )
    }

    deleteCandidate?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除这张图片？") },
            text = { Text("删除后将无法通过客户端找回。") },
            confirmButton = {
                Button(onClick = {
                    deleteCandidate = null
                    viewModel.delete(record) { ownImages.refresh() }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UploadTab(
    selected: List<SelectedClientImage>,
    batches: List<ClientImageTransferBatchEntity>,
    featureEnabled: Boolean,
    unavailableReason: String?,
    maxBytes: Long?,
    wifiOnly: Boolean,
    busy: Boolean,
    visibilityEnabled: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onSelectImages: () -> Unit,
    onSelectFolder: () -> Unit,
    onRemove: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    onUpload: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (featureEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (featureEnabled) "图片直接上传到 COS" else "图片上传暂不可用",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (featureEnabled) {
                        "支持 PNG、JPEG、静态 WebP；单张上限 ${maxBytes?.let(::formatBytes) ?: "—"}。图片流量不会经过应用后台。"
                    } else {
                        unavailableReason ?: "请先登记目录服务器，并让服务端配置私有 COS。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSelectImages, enabled = featureEnabled && !busy) { Text("选择图片") }
            OutlinedButton(onClick = onSelectFolder, enabled = featureEnabled && !busy) { Text("选择文件夹") }
            if (busy) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("仅 Wi‑Fi 上传", style = MaterialTheme.typography.bodyMedium)
                Text("关闭时移动网络并发 2，Wi‑Fi 并发 3", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("允许管理员查看我的图片", style = MaterialTheme.typography.bodyMedium)
                Text("默认关闭；管理员只能查看小图与预览图", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = visibilityEnabled, onCheckedChange = onVisibilityChange)
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("待上传 ${selected.size} 张", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("清空") }
                Button(onClick = onUpload, enabled = featureEnabled && !busy) { Text("开始上传") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(selected, key = { it.uri.toString() }) { image ->
                SelectedImageCard(image, onRemove)
            }
            items(batches.take(6), key = { "batch-${it.localId}" }) { batch ->
                BatchCard(batch, onPause, onResume, onCancel)
            }
        }
    }
}

@Composable
private fun SelectedImageCard(image: SelectedClientImage, onRemove: (android.net.Uri) -> Unit) {
    Card(onClick = { onRemove(image.uri) }) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.uri)
                    .size(320)
                    .build(),
                contentDescription = image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
            Text(
                image.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(7.dp),
            )
        }
    }
}

@Composable
private fun BatchCard(
    batch: ClientImageTransferBatchEntity,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Card {
        Column(Modifier.padding(10.dp)) {
            Text("后台任务", fontWeight = FontWeight.SemiBold)
            Text(
                "${batch.succeededCount}/${batch.totalCount} · ${batchStateLabel(batch.state)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                if (batch.state == ClientImageTransferState.PAUSED) {
                    TextButton(onClick = { onResume(batch.localId) }) { Text("继续") }
                } else if (batch.state !in setOf("completed", ClientImageTransferState.CANCELLED)) {
                    TextButton(onClick = { onPause(batch.localId) }) { Text("暂停") }
                }
                if (batch.state !in setOf("completed", ClientImageTransferState.CANCELLED)) {
                    TextButton(onClick = { onCancel(batch.localId) }) { Text("取消") }
                }
            }
        }
    }
}

@Composable
private fun ImageGallery(
    images: LazyPagingItems<ClientImageRecord>,
    deliveries: Map<DeliveryKey, ClientImageDelivery>,
    shared: Boolean,
    onRequestThumbnail: (String, Boolean, Boolean) -> Unit,
    onOpen: (ClientImageRecord) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            count = images.itemCount,
            key = { index -> images.peek(index)?.id ?: "placeholder-$index" },
        ) { index ->
            images[index]?.let { record ->
                val descriptor = deliveries[DeliveryKey(record.id, "thumbnail_512", shared)]
                LaunchedEffect(record.id, shared) {
                    onRequestThumbnail(record.id, shared, false)
                }
                RemoteImageCard(record, descriptor, shared, onRequestThumbnail, onOpen)
            }
        }
        if (images.loadState.append is LoadState.Loading || images.loadState.refresh is LoadState.Loading) {
            item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        }
        val error = (images.loadState.refresh as? LoadState.Error)
            ?: (images.loadState.append as? LoadState.Error)
        if (error != null) {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text("加载失败", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = images::retry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun RemoteImageCard(
    record: ClientImageRecord,
    descriptor: ClientImageDelivery?,
    shared: Boolean,
    onRequestThumbnail: (String, Boolean, Boolean) -> Unit,
    onOpen: (ClientImageRecord) -> Unit,
) {
    var refreshed by remember(record.id, shared) { mutableStateOf(false) }
    Card(onClick = { onOpen(record) }) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (descriptor == null) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    AsyncImage(
                        model = deliveryRequest(descriptor, shared),
                        contentDescription = record.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = {
                            if (!refreshed) {
                                refreshed = true
                                onRequestThumbnail(record.id, shared, true)
                            }
                        },
                    )
                }
            }
            Text(
                record.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp),
            )
            Text(
                if (shared) "用户 ${record.ownerUserId.take(8)}" else formatBytes(record.byteSize),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 7.dp),
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    record: ClientImageRecord,
    delivery: ClientImageDelivery,
    shared: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                AsyncImage(
                    model = deliveryRequest(delivery, shared),
                    contentDescription = record.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.height(8.dp))
                Text("${record.width} × ${record.height} · ${formatBytes(record.byteSize)}")
                if (shared) Text("来自用户 ${record.ownerUserId}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (shared) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            } else {
                Row {
                    TextButton(onClick = onDownload) { Text("下载") }
                    TextButton(onClick = onDelete) { Text("删除") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        },
    )
}

@Composable
private fun deliveryRequest(delivery: ClientImageDelivery, shared: Boolean): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(delivery.signedUrl)
        .memoryCacheKey(delivery.stableCacheKey)
        .diskCacheKey(delivery.stableCacheKey)
        .memoryCachePolicy(if (shared) CachePolicy.DISABLED else CachePolicy.ENABLED)
        .diskCachePolicy(if (shared) CachePolicy.DISABLED else CachePolicy.ENABLED)
        .build()

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun batchStateLabel(state: String): String = when (state) {
    "completed" -> "已完成"
    "partial" -> "部分失败"
    ClientImageTransferState.PAUSED -> "已暂停"
    ClientImageTransferState.CANCELLED -> "已取消"
    else -> "上传中"
}

private fun downloadName(record: ClientImageRecord): String {
    val extension = when (record.imageFormat.lowercase()) {
        "jpeg", "jpg" -> ".jpg"
        "webp" -> ".webp"
        else -> ".png"
    }
    return if (record.displayName.lowercase().endsWith(extension)) record.displayName else record.displayName + extension
}
