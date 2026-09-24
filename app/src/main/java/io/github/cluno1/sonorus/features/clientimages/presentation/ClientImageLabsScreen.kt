package io.github.cluno1.sonorus.features.clientimages.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.cluno1.sonorus.shared.presentation.components.icons.Icon
import io.github.cluno1.sonorus.shared.presentation.components.icons.MaterialSymbolIcon
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
    LaunchedEffect(legacyDownload?.record?.id) {
        legacyDownload?.let { download -> createDocument.launch(downloadName(download.record)) }
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
        title = "图片空间",
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
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        divider = {},
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(title, fontWeight = FontWeight.SemiBold) },
                            )
                        }
                    }
                }
                when (tab) {
                    0 -> UploadTab(
                        selected = selected,
                        batches = batches,
                        featureEnabled = capabilities?.enabled == true,
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
                Text("开启后，管理员可以查看并下载你上传的原图，但不能删除或修改。关闭后会立即停止共享。")
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
            onDownload = { viewModel.download(state.record, state.shared) },
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
                .padding(top = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (featureEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = if (featureEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon = MaterialSymbolIcon("add_photo_alternate", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (featureEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onError
                            },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (featureEnabled) "上传你的图片" else "图片上传暂不可用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (featureEnabled) {
                            "支持 PNG、JPEG、静态 WebP，单张最大 ${maxBytes?.let(::formatBytes) ?: "—"}"
                        } else {
                            "请稍后重试，或检查账号连接状态"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (featureEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSelectImages,
                enabled = featureEnabled && !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(MaterialSymbolIcon("photo_library", filled = true), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择图片")
            }
            OutlinedButton(
                onClick = onSelectFolder,
                enabled = featureEnabled && !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(MaterialSymbolIcon("folder", filled = true), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择文件夹")
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column {
                UploadPreferenceRow(
                    icon = "wifi",
                    title = "仅在 Wi‑Fi 下上传",
                    description = "适合一次上传大量图片",
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                UploadPreferenceRow(
                    icon = "admin_panel_settings",
                    title = "允许管理员查看",
                    description = if (visibilityEnabled) {
                        "管理员可以查看并下载原图"
                    } else {
                        "仅你自己可以查看"
                    },
                    checked = visibilityEnabled,
                    onCheckedChange = onVisibilityChange,
                )
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("待上传 ${selected.size} 张", fontWeight = FontWeight.SemiBold)
                    Text("轻触图片可从列表移除", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClear) { Text("清空") }
                Button(
                    onClick = onUpload,
                    enabled = featureEnabled && !busy,
                    shape = RoundedCornerShape(16.dp),
                ) { Text("开始上传") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected.isEmpty() && batches.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyUploadState()
                }
            }
            items(selected, key = { it.uri.toString() }) { image ->
                SelectedImageCard(image, onRemove)
            }
            items(
                items = batches.take(6),
                key = { "batch-${it.localId}" },
                span = { GridItemSpan(maxLineSpan) },
            ) { batch ->
                BatchCard(batch, onPause, onResume, onCancel)
            }
        }
    }
}

@Composable
private fun UploadPreferenceRow(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon = MaterialSymbolIcon(icon, filled = true),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyUploadState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    MaterialSymbolIcon("imagesmode", filled = true),
                    null,
                    Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("选择图片开始上传", style = MaterialTheme.typography.titleMedium)
        Text(
            "可以一次选择多张，也可以选择整个文件夹",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedImageCard(image: SelectedClientImage, onRemove: (android.net.Uri) -> Unit) {
    Card(
        onClick = { onRemove(image.uri) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
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
                    .aspectRatio(1f),
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
    val completed = (batch.succeededCount + batch.failedCount).coerceAtMost(batch.totalCount)
    val progress = if (batch.totalCount == 0) 0f else completed.toFloat() / batch.totalCount
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            MaterialSymbolIcon("cloud_upload", filled = true),
                            null,
                            Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("上传任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$completed/${batch.totalCount} · ${batchStateLabel(batch.state)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
    val loading = images.loadState.refresh is LoadState.Loading
    val error = (images.loadState.refresh as? LoadState.Error)
        ?: (images.loadState.append as? LoadState.Error)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GallerySummaryCard(shared = shared, count = images.itemCount, onRefresh = images::refresh)
        }
        if (images.itemCount == 0 && !loading && error == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GalleryEmptyState(shared = shared, onRefresh = images::refresh)
            }
        }
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
        if (error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("暂时无法加载图片", color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "请检查账号连接后重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = images::retry) { Text("重试") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GallerySummaryCard(shared: Boolean, count: Int, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        MaterialSymbolIcon(if (shared) "group" else "photo_library", filled = true),
                        null,
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (shared) "用户共享" else "我的图片",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (count > 0) "已显示 $count 张" else if (shared) "查看用户授权的图片" else "查看和管理已上传的图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRefresh) { Text("刷新") }
        }
    }
}

@Composable
private fun GalleryEmptyState(shared: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    MaterialSymbolIcon(if (shared) "no_accounts" else "add_photo_alternate", filled = true),
                    null,
                    Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (shared) "还没有用户共享图片" else "还没有上传图片",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (shared) "用户开启管理员查看后，图片会出现在这里" else "完成上传后，图片会保存在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(16.dp)) { Text("重新加载") }
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
    Card(
        onClick = { onOpen(record) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
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
            Row {
                TextButton(onClick = onDownload) { Text("下载原图") }
                if (!shared) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
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
