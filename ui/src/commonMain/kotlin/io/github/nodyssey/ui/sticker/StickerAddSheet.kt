package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.nodyssey.data.imagehost.HostedImage
import io.github.nodyssey.data.imagehost.ImageHostError
import io.github.nodyssey.data.sticker.StickerSourceError
import io.github.nodyssey.data.sticker.StickerUpload
import io.github.nodyssey.data.sticker.extractStickerLinks
import io.github.nodyssey.data.sticker.folderName
import io.github.nodyssey.ui.account.formatBytes
import io.github.nodyssey.ui.account.messageRes
import io.github.nodyssey.ui.account.nameRes
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.common.rememberNewClipboardText
import io.github.nodyssey.ui.composer.MAX_IMAGES_PER_PICK
import io.github.nodyssey.ui.composer.rememberImagePicker
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.composer_image_default_name
import io.github.nodyssey.ui.resources.sticker_add
import io.github.nodyssey.ui.resources.sticker_add_count
import io.github.nodyssey.ui.resources.sticker_added_count
import io.github.nodyssey.ui.resources.sticker_added_none
import io.github.nodyssey.ui.resources.sticker_clipboard_found
import io.github.nodyssey.ui.resources.sticker_github_bad_url
import io.github.nodyssey.ui.resources.sticker_github_folder_meta
import io.github.nodyssey.ui.resources.sticker_github_found
import io.github.nodyssey.ui.resources.sticker_github_http
import io.github.nodyssey.ui.resources.sticker_github_license
import io.github.nodyssey.ui.resources.sticker_github_load
import io.github.nodyssey.ui.resources.sticker_github_manage
import io.github.nodyssey.ui.resources.sticker_github_network
import io.github.nodyssey.ui.resources.sticker_github_new_source
import io.github.nodyssey.ui.resources.sticker_github_no_images
import io.github.nodyssey.ui.resources.sticker_github_not_found
import io.github.nodyssey.ui.resources.sticker_github_note
import io.github.nodyssey.ui.resources.sticker_github_pick_folders
import io.github.nodyssey.ui.resources.sticker_github_rate_limited
import io.github.nodyssey.ui.resources.sticker_github_rate_limited_soon
import io.github.nodyssey.ui.resources.sticker_github_save
import io.github.nodyssey.ui.resources.sticker_github_select_all
import io.github.nodyssey.ui.resources.sticker_github_sources
import io.github.nodyssey.ui.resources.sticker_github_subscribe
import io.github.nodyssey.ui.resources.sticker_github_truncated
import io.github.nodyssey.ui.resources.sticker_github_unparsable
import io.github.nodyssey.ui.resources.sticker_github_url_hint
import io.github.nodyssey.ui.resources.sticker_github_url_label
import io.github.nodyssey.ui.resources.sticker_github_url_label_existing
import io.github.nodyssey.ui.resources.sticker_host_added
import io.github.nodyssey.ui.resources.sticker_host_clear
import io.github.nodyssey.ui.resources.sticker_host_connect
import io.github.nodyssey.ui.resources.sticker_host_count
import io.github.nodyssey.ui.resources.sticker_host_empty
import io.github.nodyssey.ui.resources.sticker_host_gif_only
import io.github.nodyssey.ui.resources.sticker_host_hint
import io.github.nodyssey.ui.resources.sticker_keep_original
import io.github.nodyssey.ui.resources.sticker_keep_original_desc
import io.github.nodyssey.ui.resources.sticker_link_note
import io.github.nodyssey.ui.resources.sticker_links_label
import io.github.nodyssey.ui.resources.sticker_paste
import io.github.nodyssey.ui.resources.sticker_preview_broken
import io.github.nodyssey.ui.resources.sticker_preview_count
import io.github.nodyssey.ui.resources.sticker_preview_hint
import io.github.nodyssey.ui.resources.sticker_preview_large
import io.github.nodyssey.ui.resources.sticker_subscribed
import io.github.nodyssey.ui.resources.sticker_tab_github
import io.github.nodyssey.ui.resources.sticker_tab_host
import io.github.nodyssey.ui.resources.sticker_tab_link
import io.github.nodyssey.ui.resources.sticker_tab_upload
import io.github.nodyssey.ui.resources.sticker_upload_done
import io.github.nodyssey.ui.resources.sticker_upload_host_default
import io.github.nodyssey.ui.resources.sticker_upload_not_configured
import io.github.nodyssey.ui.resources.sticker_upload_note
import io.github.nodyssey.ui.resources.sticker_upload_pick
import io.github.nodyssey.ui.resources.sticker_upload_pick_more
import io.github.nodyssey.ui.resources.sticker_upload_picked
import io.github.nodyssey.ui.resources.sticker_upload_progress
import io.github.nodyssey.ui.resources.sticker_upload_to
import io.github.nodyssey.ui.resources.sticker_upload_waiting
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.GroupedListItemSwitch
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaLoadingIndicator
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.UnderlineTabRow
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 添加表情 (1c–1e, 2a): paste links, pick from the connected image host, upload new ones, or
 * subscribe to a GitHub repository's folders.
 *
 * [onNotice] is how a finished add reports back, with its undo, to the line under the panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerAddSheet(
    viewModel: StickerAddViewModel,
    onDismiss: () -> Unit,
    onNotice: (StickerNotice) -> Unit,
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val tabs = listOf(
        StickerAddTab.LINK to Res.string.sticker_tab_link,
        StickerAddTab.HOST to Res.string.sticker_tab_host,
        StickerAddTab.UPLOAD to Res.string.sticker_tab_upload,
        StickerAddTab.GITHUB to Res.string.sticker_tab_github,
    )
    val scope = rememberCoroutineScope()
    val noneText = stringResource(Res.string.sticker_added_none)
    val subscribedText = stringResource(Res.string.sticker_subscribed)
    // What the last 添加 put into 我的, waiting one frame to be worded: the count is a plural-free
    // format string, and resolving it needs composition.
    var added by remember { mutableStateOf<List<String>?>(null) }

    // One 添加 at a time. A second tap while the first is writing would add nothing new and its
    // empty answer would replace the first one's 已添加 · 撤销. Left set once [work] has done its
    // part: the sheet is on its way out, and a tap in that last frame would do the same.
    var busy by remember { mutableStateOf(false) }

    fun submit(work: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            var done = false
            try {
                done = work()
            } finally {
                if (!done) busy = false
            }
        }
    }
    added?.let { urls ->
        val text = if (urls.isEmpty()) noneText else stringResource(Res.string.sticker_added_count, urls.size)
        LaunchedEffect(urls) {
            onNotice(StickerNotice(text, urls))
            added = null
            onDismiss()
        }
    }

    val leaving = rememberLeavingStickerNavigation(onDismiss)

    CompositionLocalProvider(LocalStickerNavigation provides leaving) {
        SheetBody(
            viewModel = viewModel,
            tab = tab,
            tabs = tabs,
            busy = busy,
            onDismiss = onDismiss,
            onAddLinks = {
                submit {
                    added = viewModel.addLinks()
                    true
                }
            },
            onAddHost = {
                submit {
                    added = viewModel.addHostSelection()
                    true
                }
            },
            onSubscribe = {
                submit {
                    viewModel.subscribe().also { subscribed ->
                        if (subscribed) {
                            onNotice(StickerNotice(subscribedText))
                            onDismiss()
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetBody(
    viewModel: StickerAddViewModel,
    tab: StickerAddTab,
    tabs: List<Pair<StickerAddTab, StringResource>>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAddLinks: () -> Unit,
    onAddHost: () -> Unit,
    onSubscribe: () -> Unit,
) {
    PlazaSheet(onDismiss = onDismiss, title = stringResource(Res.string.sticker_add)) {
        UnderlineTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == tab },
            tabs = tabs.map { TabLabel(stringResource(it.second)) },
            onSelect = { viewModel.selectTab(tabs[it].first) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (tab) {
                StickerAddTab.LINK -> LinkTab(viewModel, enabled = !busy, onAdd = onAddLinks)
                StickerAddTab.HOST -> HostTab(viewModel, enabled = !busy, onAdd = onAddHost)
                StickerAddTab.UPLOAD -> UploadTab(viewModel)
                StickerAddTab.GITHUB -> GitHubTab(viewModel, enabled = !busy, onSubscribe = onSubscribe)
            }
        }
    }
}

// ---- 链接 (1c) ------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.LinkTab(
    viewModel: StickerAddViewModel,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    val text by viewModel.linkText.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    // A fresh reader per opening: the sheet is the reader asking, and the clip on it now is what
    // they most likely copied for it.
    val readClipboard = rememberNewClipboardText()
    val clipLinks = remember { extractStickerLinks(readClipboard().orEmpty()) }
    var renaming by remember { mutableStateOf<LinkPreview?>(null) }

    if (clipLinks.isNotEmpty() && !clipLinks.all { it in text }) {
        LayerCard(contentPadding = PaddingValues(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(PlazaIcons.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.sticker_clipboard_found, clipLinks.size), style = MaterialTheme.typography.titleSmall)
                    Text(
                        clipLinks.first().removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { viewModel.appendLinks(clipLinks.joinToString("\n")) }) {
                    Text(stringResource(Res.string.sticker_paste))
                }
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = viewModel::setLinkText,
        label = { Text(stringResource(Res.string.sticker_links_label)) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    if (previews.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.sticker_preview_count, previews.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.sticker_preview_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(previews, key = { it.url }) { preview ->
                LinkPreviewTile(
                    preview = preview,
                    onLoaded = { viewModel.onPreviewLoaded(preview.url, it) },
                    onClick = { renaming = preview },
                )
            }
        }
    }
    SectionNote(stringResource(Res.string.sticker_link_note), contentPadding = PaddingValues(0.dp))
    val ready = previews.count { it.ready }
    Button(onClick = onAdd, enabled = enabled && ready > 0, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.sticker_add_count, ready))
    }
    renaming?.let { target ->
        RenameStickerDialog(
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renamePreview(target.url, it)
                renaming = null
            },
        )
    }
}

@Composable
private fun LinkPreviewTile(
    preview: LinkPreview,
    onLoaded: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(PREVIEW_SIZE).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(PREVIEW_SIZE)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (preview.loads == false) {
                Icon(PlazaIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            } else {
                StickerThumbnail(
                    url = preview.url,
                    contentDescription = preview.name,
                    onResult = onLoaded,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = when {
                preview.loads == false -> stringResource(Res.string.sticker_preview_broken)
                preview.tooLarge -> stringResource(Res.string.sticker_preview_large)
                else -> preview.name
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (preview.loads == false || preview.tooLarge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A picture fetched for a sheet or a grid of stickers. Waived past 仅 Wi-Fi 加载图片 on the panel's
 * own grounds: the reader opened this to look at exactly these, and they are a few kilobytes each.
 */
@Composable
internal fun StickerThumbnail(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onResult: ((Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).allowMeteredImage(true).build() }
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) {
        ImageFallback(modifier = modifier)
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onSuccess = { onResult?.invoke(true) },
            onError = {
                failed = true
                onResult?.invoke(false)
            },
            modifier = modifier,
        )
    }
}

// ---- 我的图床 (1d) ---------------------------------------------------------------------------------

@Composable
private fun ColumnScope.HostTab(
    viewModel: StickerAddViewModel,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    val state by viewModel.host.collectAsStateWithLifecycle()
    val config by viewModel.hostConfig.collectAsStateWithLifecycle()
    val selection by viewModel.hostSelection.collectAsStateWithLifecycle()
    val mine by viewModel.mineUrls.collectAsStateWithLifecycle()
    val gifOnly by viewModel.gifOnly.collectAsStateWithLifecycle()

    when (val current = state) {
        HostListState.Loading -> Box(Modifier.fillMaxWidth().height(HOST_GRID_HEIGHT), contentAlignment = Alignment.Center) {
            PlazaLoadingIndicator()
        }

        is HostListState.Failed -> HostImagesFailed(current.error, onRetry = viewModel::loadHost)

        is HostListState.Loaded -> {
            val images = current.images.filter { !gifOnly || it.isGif() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(
                        Res.string.sticker_host_count,
                        config?.provider?.let { stringResource(it.nameRes()) }.orEmpty(),
                        current.images.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = gifOnly,
                    onClick = { viewModel.setGifOnly(!gifOnly) },
                    label = { Text(stringResource(Res.string.sticker_host_gif_only)) },
                )
            }
            if (images.isEmpty()) {
                Text(
                    stringResource(Res.string.sticker_host_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HostImageGrid(
                    images = images,
                    selection = selection,
                    added = mine,
                    onToggle = viewModel::toggleHost,
                    onDragSelect = viewModel::selectHost,
                )
                SectionNote(stringResource(Res.string.sticker_host_hint), contentPadding = PaddingValues(0.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::clearHostSelection, enabled = selection.isNotEmpty()) {
                    Text(stringResource(Res.string.sticker_host_clear))
                }
                Button(onClick = onAdd, enabled = enabled && selection.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.sticker_add_count, selection.size))
                }
            }
        }
    }
}

private fun HostedImage.isGif(): Boolean =
    mimeType?.equals("image/gif", ignoreCase = true) == true ||
        url.substringBefore('?').endsWith(".gif", ignoreCase = true) ||
        fileName.endsWith(".gif", ignoreCase = true)

/**
 * Why the host's pictures could not be listed, and the one way forward: 图床设置 when the fix is a
 * setting (none connected, a host with no list, a rejected key), otherwise another try.
 */
@Composable
internal fun HostImagesFailed(
    error: ImageHostError,
    onRetry: () -> Unit,
) {
    val navigation = LocalStickerNavigation.current
    Text(
        stringResource(error.messageRes()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val configurable = error == ImageHostError.NotConfigured ||
        error == ImageHostError.Unsupported ||
        error == ImageHostError.InvalidKey
    if (configurable && navigation != null) {
        Button(onClick = navigation.openImageHost, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sticker_host_connect))
        }
    } else {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

/**
 * The host's pictures, four across. A tap toggles one; a long press and a drag selects every cell
 * the finger passes over — 1d's 按住拖动可连续多选 — found by hit-testing the grid's own layout.
 *
 * Shared by 添加表情's 我的图床 tab and the editors' 从图床选择 sheet. [added] is the sticker tab's:
 * pictures already in 我的表情, dimmed and not selectable; the editors pass none.
 */
@Composable
internal fun HostImageGrid(
    images: List<HostedImage>,
    selection: Set<String>,
    onToggle: (String) -> Unit,
    onDragSelect: (String) -> Unit,
    added: Set<String> = emptySet(),
    maxHeight: Dp = HOST_GRID_HEIGHT,
) {
    val gridState = rememberLazyGridState()
    fun urlAt(offset: Offset): String? =
        gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                val start = item.offset
                offset.x >= start.x && offset.x < start.x + item.size.width &&
                    offset.y >= start.y && offset.y < start.y + item.size.height
            }?.key as? String

    LazyVerticalGrid(
        columns = GridCells.Fixed(HOST_COLUMNS),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .pointerInput(images, added) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> urlAt(offset)?.takeIf { it !in added }?.let(onDragSelect) },
                    onDrag = { change, _ -> urlAt(change.position)?.takeIf { it !in added }?.let(onDragSelect) },
                )
            },
    ) {
        items(images, key = { it.url }) { image ->
            val isAdded = image.url in added
            val selected = image.url in selection
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.small) else Modifier,
                    ).clickable(enabled = !isAdded) { onToggle(image.url) },
            ) {
                StickerThumbnail(
                    url = image.url,
                    contentDescription = image.fileName,
                    modifier = Modifier.fillMaxSize().alpha(if (isAdded) 0.4f else 1f),
                )
                if (selected) {
                    SelectionCheck(Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
                if (isAdded) {
                    Text(
                        stringResource(Res.string.sticker_host_added),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SelectionCheck(modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary, modifier = modifier.size(20.dp)) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp))
    }
}

// ---- 上传 (1e) ------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.UploadTab(viewModel: StickerAddViewModel) {
    val config by viewModel.hostConfig.collectAsStateWithLifecycle()
    val keepOriginal by viewModel.keepOriginal.collectAsStateWithLifecycle()
    val uploads by viewModel.uploads.collectAsStateWithLifecycle()
    val navigation = LocalStickerNavigation.current
    val pick = rememberImagePicker(
        maxItems = MAX_IMAGES_PER_PICK,
        fallbackName = stringResource(Res.string.composer_image_default_name),
        onPicked = viewModel::upload,
    )
    val configured = config?.isConfigured == true

    Column {
        GroupedRow(
            title = stringResource(Res.string.sticker_upload_to),
            subtitle = config?.provider?.let { stringResource(Res.string.sticker_upload_host_default, stringResource(it.nameRes())) },
            icon = PlazaIcons.CloudUpload,
            first = true,
            onClick = navigation?.openImageHost,
        )
        GroupedRow(
            title = stringResource(Res.string.sticker_keep_original),
            subtitle = stringResource(Res.string.sticker_keep_original_desc),
            icon = PlazaIcons.Image,
            last = true,
            checked = keepOriginal,
            onCheckedChange = viewModel::setKeepOriginal,
            trailing = { GroupedListItemSwitch(checked = keepOriginal) },
        )
    }
    if (!configured && config != null) {
        Text(
            stringResource(Res.string.sticker_upload_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (uploads.isEmpty()) {
        Button(onClick = pick, enabled = configured, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sticker_upload_pick))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.sticker_upload_picked, uploads.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = pick, enabled = configured) { Text(stringResource(Res.string.sticker_upload_pick_more)) }
        }
        LayerCard(contentPadding = PaddingValues(Spacing.md)) {
            uploads.forEach { upload -> UploadRow(upload, onRetry = { viewModel.retryUpload(upload.id) }) }
        }
        val finished = uploads.count { it.state == StickerUpload.State.DONE || it.state == StickerUpload.State.FAILED }
        Text(
            if (finished == uploads.size) {
                stringResource(Res.string.sticker_upload_done)
            } else {
                stringResource(Res.string.sticker_upload_progress, uploads.count { it.state == StickerUpload.State.DONE } + 1, uploads.size)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    SectionNote(stringResource(Res.string.sticker_upload_note), contentPadding = PaddingValues(0.dp))
}

@Composable
private fun UploadRow(
    upload: StickerUpload,
    onRetry: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StickerThumbnail(
            url = upload.source,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.extraSmall),
        )
        Column(Modifier.weight(1f)) {
            Text(upload.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            upload.error?.let {
                Text(stringResource(it.error.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        when (upload.state) {
            StickerUpload.State.WAITING -> Text(
                stringResource(Res.string.sticker_upload_waiting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StickerUpload.State.UPLOADING -> Text("${(upload.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)

            StickerUpload.State.DONE -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)

            StickerUpload.State.FAILED -> TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
        }
    }
}

// ---- GitHub (2a) ----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.GitHubTab(
    viewModel: StickerAddViewModel,
    enabled: Boolean,
    onSubscribe: () -> Unit,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val url by viewModel.repoUrl.collectAsStateWithLifecycle()
    val repo by viewModel.repo.collectAsStateWithLifecycle()
    val selection by viewModel.folderSelection.collectAsStateWithLifecycle()
    val cdn by viewModel.cdn.collectAsStateWithLifecycle()
    val navigation = LocalStickerNavigation.current

    if (subscriptions.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.sticker_github_sources, subscriptions.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (navigation != null) {
                TextButton(onClick = navigation.openSources) { Text(stringResource(Res.string.sticker_github_manage)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item(key = "new") {
                FilterChip(
                    selected = source == null,
                    onClick = viewModel::newSource,
                    label = { Text(stringResource(Res.string.sticker_github_new_source)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            items(subscriptions, key = { it.slug }) { subscription ->
                FilterChip(
                    selected = source == subscription.slug,
                    onClick = { viewModel.editSource(subscription.slug) },
                    label = { Text("${subscription.slug} · ${subscription.folders.size}") },
                    leadingIcon = { Icon(PlazaIcons.Code, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
    OutlinedTextField(
        value = url,
        onValueChange = viewModel::setRepoUrl,
        label = {
            Text(
                source?.let { stringResource(Res.string.sticker_github_url_label_existing, it) }
                    ?: stringResource(Res.string.sticker_github_url_label),
            )
        },
        placeholder = { Text(stringResource(Res.string.sticker_github_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.loadRepo() }),
        trailingIcon = {
            TextButton(onClick = viewModel::loadRepo, enabled = url.isNotBlank()) {
                Text(stringResource(Res.string.sticker_github_load))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    when (val current = repo) {
        RepoState.Idle -> Unit

        RepoState.Loading -> Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
            PlazaLoadingIndicator()
        }

        RepoState.BadUrl -> ErrorText(stringResource(Res.string.sticker_github_bad_url))

        is RepoState.Failed -> ErrorText(sourceErrorText(current.error))

        is RepoState.Loaded -> {
            val listing = current.listing
            LayerCard(contentPadding = PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(PlazaIcons.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("${listing.owner} / ${listing.repo}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(Res.string.sticker_github_found, listing.ref, listing.folders.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(Res.string.sticker_github_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (listing.truncated) {
                    Text(
                        stringResource(Res.string.sticker_github_truncated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.sticker_github_pick_folders),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::selectAllFolders) { Text(stringResource(Res.string.sticker_github_select_all)) }
            }
            Column(
                modifier = Modifier.heightIn(max = FOLDER_LIST_HEIGHT).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                listing.folders.forEach { folder ->
                    FolderRow(
                        name = folderName(folder.path, listing.repo),
                        meta = stringResource(Res.string.sticker_github_folder_meta, folder.files.size, formatBytes(folder.totalBytes)),
                        samples = folder.files.take(SAMPLE_COUNT).map { cdn.urlFor(listing.owner, listing.repo, listing.sha, it) },
                        checked = folder.path in selection,
                        onToggle = { viewModel.toggleFolder(folder.path) },
                    )
                }
            }
            val chosen = listing.folders.filter { it.path in selection }
            Button(onClick = onSubscribe, enabled = enabled && chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (source == null) Res.string.sticker_github_subscribe else Res.string.sticker_github_save,
                        chosen.size,
                        chosen.sumOf { it.files.size },
                    ),
                )
            }
        }
    }
    SectionNote(stringResource(Res.string.sticker_github_note), contentPadding = PaddingValues(0.dp))
}

@Composable
private fun FolderRow(
    name: String,
    meta: String,
    samples: List<String>,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onToggle)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small)) {
            samples.forEachIndexed { index, sample ->
                StickerThumbnail(
                    url = sample,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = MOSAIC_CELL * (index % 2), y = MOSAIC_CELL * (index / 2))
                        .size(MOSAIC_CELL),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun sourceErrorText(error: StickerSourceError): String =
    when (error) {
        StickerSourceError.NotFound -> stringResource(Res.string.sticker_github_not_found)

        StickerSourceError.NoImages -> stringResource(Res.string.sticker_github_no_images)

        StickerSourceError.Network -> stringResource(Res.string.sticker_github_network)

        StickerSourceError.Unparsable -> stringResource(Res.string.sticker_github_unparsable)

        is StickerSourceError.Http -> stringResource(Res.string.sticker_github_http, error.code)

        is StickerSourceError.RateLimited ->
            error.resetAtEpochSeconds?.let {
                stringResource(Res.string.sticker_github_rate_limited, TimeFormat.absolute(it * 1000).substringAfter(' ').substringBeforeLast(':'))
            } ?: stringResource(Res.string.sticker_github_rate_limited_soon)
    }

private val PREVIEW_SIZE = 72.dp

/** Two by two: a folder's first four pictures as one 44dp mosaic. */
private val MOSAIC_CELL = 22.dp
private val HOST_GRID_HEIGHT = 300.dp
private val FOLDER_LIST_HEIGHT = 280.dp
private const val HOST_COLUMNS = 4
private const val SAMPLE_COUNT = 4
