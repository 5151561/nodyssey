package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.sticker.LinkProbe
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerSubscription
import io.github.nodyssey.data.sticker.SubscribedFolder
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_delete
import io.github.nodyssey.ui.resources.action_save
import io.github.nodyssey.ui.resources.sticker_add
import io.github.nodyssey.ui.resources.sticker_cdn_custom
import io.github.nodyssey.ui.resources.sticker_cdn_custom_desc
import io.github.nodyssey.ui.resources.sticker_cdn_custom_hint
import io.github.nodyssey.ui.resources.sticker_cdn_custom_invalid
import io.github.nodyssey.ui.resources.sticker_cdn_jsdelivr
import io.github.nodyssey.ui.resources.sticker_cdn_jsdelivr_desc
import io.github.nodyssey.ui.resources.sticker_cdn_ms
import io.github.nodyssey.ui.resources.sticker_cdn_note
import io.github.nodyssey.ui.resources.sticker_cdn_raw
import io.github.nodyssey.ui.resources.sticker_cdn_selected_desc
import io.github.nodyssey.ui.resources.sticker_manage_broken
import io.github.nodyssey.ui.resources.sticker_manage_cdn_title
import io.github.nodyssey.ui.resources.sticker_manage_change_folders
import io.github.nodyssey.ui.resources.sticker_manage_check_updates
import io.github.nodyssey.ui.resources.sticker_manage_drag_hint
import io.github.nodyssey.ui.resources.sticker_manage_export
import io.github.nodyssey.ui.resources.sticker_manage_export_desc
import io.github.nodyssey.ui.resources.sticker_manage_exported
import io.github.nodyssey.ui.resources.sticker_manage_folder_count
import io.github.nodyssey.ui.resources.sticker_manage_hidden
import io.github.nodyssey.ui.resources.sticker_manage_hide
import io.github.nodyssey.ui.resources.sticker_manage_mine_empty
import io.github.nodyssey.ui.resources.sticker_manage_mine_header
import io.github.nodyssey.ui.resources.sticker_manage_note
import io.github.nodyssey.ui.resources.sticker_manage_open_repo
import io.github.nodyssey.ui.resources.sticker_manage_pinned
import io.github.nodyssey.ui.resources.sticker_manage_remove_all
import io.github.nodyssey.ui.resources.sticker_manage_remove_broken_body
import io.github.nodyssey.ui.resources.sticker_manage_remove_broken_confirm
import io.github.nodyssey.ui.resources.sticker_manage_selected
import io.github.nodyssey.ui.resources.sticker_manage_show
import io.github.nodyssey.ui.resources.sticker_manage_subs_empty
import io.github.nodyssey.ui.resources.sticker_manage_subscribe_another
import io.github.nodyssey.ui.resources.sticker_manage_tab_mine
import io.github.nodyssey.ui.resources.sticker_manage_tab_subs
import io.github.nodyssey.ui.resources.sticker_manage_timeout
import io.github.nodyssey.ui.resources.sticker_manage_title
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe_body
import io.github.nodyssey.ui.resources.sticker_manage_unsubscribe_confirm
import io.github.nodyssey.ui.resources.sticker_manage_up_to_date
import io.github.nodyssey.ui.resources.sticker_manage_update_count
import io.github.nodyssey.ui.resources.sticker_menu_move_top
import io.github.nodyssey.ui.resources.sticker_update_action
import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.UnderlineTabRow
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManageRoute(
    viewModel: StickerManageViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine by viewModel.mine.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val library = LocalStickerLibrary.current
    val addViewModel = library?.let { viewModel(key = "sticker-add") { StickerAddViewModel(it) } }
    var tab by rememberSaveable { mutableStateOf(0) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<StickerNotice?>(null) }
    val appBarState = rememberOneHandAppBarState()
    val selecting = selection.isNotEmpty()

    LaunchedEffect(Unit) {
        viewModel.checkLinks()
        viewModel.measureCdns()
        viewModel.checkUpdates(force = false)
    }

    fun openAdd(configure: StickerAddViewModel.() -> Unit) {
        addViewModel?.reset()
        addViewModel?.configure()
        addOpen = true
    }

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = if (selecting) {
                    stringResource(Res.string.sticker_manage_selected, selection.size)
                } else {
                    stringResource(Res.string.sticker_manage_title)
                },
                state = appBarState,
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = viewModel::moveSelectedToTop) {
                            Icon(PlazaIcons.VerticalAlignTop, contentDescription = stringResource(Res.string.sticker_menu_move_top))
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    } else if (tab == 0 && addViewModel != null) {
                        IconButton(onClick = { openAdd { } }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.sticker_add))
                        }
                    } else if (tab == 1) {
                        IconButton(onClick = { viewModel.checkUpdates(force = true) }) {
                            Icon(PlazaIcons.Sync, contentDescription = stringResource(Res.string.sticker_manage_check_updates))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().readableWidth()) {
            UnderlineTabRow(
                selectedTabIndex = tab,
                tabs = listOf(
                    TabLabel(stringResource(Res.string.sticker_manage_tab_mine, mine.size)),
                    TabLabel(stringResource(Res.string.sticker_manage_tab_subs, subscriptions.sumOf { it.folders.size })),
                ),
                onSelect = {
                    tab = it
                    viewModel.clearSelection()
                },
            )
            Box(Modifier.weight(1f)) {
                if (tab == 0) {
                    MineTab(viewModel)
                } else {
                    SubscriptionsTab(
                        viewModel = viewModel,
                        onOpenUrl = onOpenUrl,
                        onChangeFolders = { slug -> openAdd { editSource(slug) } },
                        onSubscribeAnother = { openAdd { selectTab(StickerAddTab.GITHUB) } },
                    )
                }
                notice?.let { current ->
                    LaunchedEffect(current) {
                        delay(4_000)
                        notice = null
                    }
                    Snackbar(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
                    ) { Text(current.text) }
                }
            }
        }
    }

    if (addOpen && addViewModel != null) {
        StickerAddSheet(viewModel = addViewModel, onDismiss = { addOpen = false }, onNotice = { notice = it })
    }
}

// ---- 我的 (1f) ------------------------------------------------------------------------------------

@Composable
private fun MineTab(viewModel: StickerManageViewModel) {
    val mine by viewModel.mine.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val broken by viewModel.broken.collectAsStateWithLifecycle()
    val copy = rememberClipboardCopy()
    val exported = stringResource(Res.string.sticker_manage_exported, mine.size)
    val gridState = rememberLazyGridState()
    var dragging by remember { mutableStateOf<String?>(null) }
    var moved by remember { mutableStateOf(false) }

    val contentPadding = PaddingValues(horizontal = LayerPageGutter + Spacing.sm, vertical = Spacing.md)
    val layoutDirection = LocalLayoutDirection.current

    /**
     * The cell under [position], given in the grid's own coordinates. An item's offset is measured
     * from inside the content padding, so [padding] — that padding's top-left corner in pixels —
     * comes off the pointer first; without it every hit lands on the cell up and to the left.
     */
    fun keyAt(
        position: Offset,
        padding: Offset,
    ): String? {
        val offset = position - padding
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offset.x >= item.offset.x && offset.x < item.offset.x + item.size.width &&
                offset.y >= item.offset.y && offset.y < item.offset.y + item.size.height
        }?.key as? String
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(MANAGE_COLUMNS),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            // Hand-rolled: neither Compose nor Material has a reorderable grid. A long press picks a
            // cell up and each cell the finger crosses swaps in; a long press that never moves is a
            // selection instead. Replace with the platform's when one ships.
            .pointerInput(mine.size, layoutDirection) {
                val padding = Offset(
                    contentPadding.calculateLeftPadding(layoutDirection).toPx(),
                    contentPadding.calculateTopPadding().toPx(),
                )
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragging = keyAt(offset, padding)?.takeIf { it.startsWith(URL_KEY) }
                        moved = false
                    },
                    onDrag = { change, _ ->
                        val from = dragging ?: return@detectDragGesturesAfterLongPress
                        val over = keyAt(change.position, padding)?.takeIf { it.startsWith(URL_KEY) } ?: return@detectDragGesturesAfterLongPress
                        if (over != from) {
                            moved = true
                            viewModel.dragMove(from.removePrefix(URL_KEY), over.removePrefix(URL_KEY))
                        }
                    },
                    onDragEnd = {
                        val key = dragging
                        if (key != null && !moved) viewModel.toggle(key.removePrefix(URL_KEY))
                        if (moved) viewModel.dragEnd()
                        dragging = null
                    },
                    onDragCancel = {
                        if (moved) viewModel.dragEnd()
                        dragging = null
                    },
                )
            },
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.sticker_manage_mine_header, mine.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.sticker_manage_drag_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (mine.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(Res.string.sticker_manage_mine_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
        }
        items(mine, key = { URL_KEY + it.url }) { sticker ->
            val selected = sticker.url in selection
            Box(
                modifier = Modifier
                    .animateItem()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                    .then(
                        if (selected || dragging == URL_KEY + sticker.url) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        } else {
                            Modifier
                        },
                    ).clickable { viewModel.toggle(sticker.url) },
                contentAlignment = Alignment.Center,
            ) {
                StickerThumbnail(
                    url = sticker.url,
                    contentDescription = sticker.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(Spacing.xs),
                )
                if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        if (broken.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrokenLinksCard(broken = broken, onRemoveAll = viewModel::removeBroken)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            GroupCard(Modifier.padding(top = Spacing.sm)) {
                GroupedRow(
                    title = stringResource(Res.string.sticker_manage_export),
                    subtitle = stringResource(Res.string.sticker_manage_export_desc),
                    icon = Icons.Default.Share,
                    first = true,
                    last = true,
                    enabled = mine.isNotEmpty(),
                    showChevron = false,
                    onClick = { copy("stickers", viewModel.exportText(), exported) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionNote(stringResource(Res.string.sticker_manage_note))
        }
    }
}

@Composable
private fun BrokenLinksCard(
    broken: List<BrokenSticker>,
    onRemoveAll: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.sticker_manage_remove_broken_confirm, broken.size)) },
            text = { Text(stringResource(Res.string.sticker_manage_remove_broken_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveAll()
                    confirming = false
                }) { Text(stringResource(Res.string.sticker_manage_remove_all), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    LayerCard(modifier = Modifier.padding(top = Spacing.sm), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(PlazaIcons.LinkOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                stringResource(Res.string.sticker_manage_broken, broken.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirming = true }) { Text(stringResource(Res.string.sticker_manage_remove_all)) }
        }
        broken.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(PlazaIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    item.sticker.url.removePrefix("https://"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (val probe = item.probe) {
                        is LinkProbe.Broken -> probe.code.toString()
                        else -> stringResource(Res.string.sticker_manage_timeout)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- 订阅 (2c) ------------------------------------------------------------------------------------

@Composable
private fun SubscriptionsTab(
    viewModel: StickerManageViewModel,
    onOpenUrl: (String) -> Unit,
    onChangeFolders: (String) -> Unit,
    onSubscribeAnother: () -> Unit,
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val cdn by viewModel.cdn.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val checkError by viewModel.checkError.collectAsStateWithLifecycle()
    val upToDate by viewModel.checkedUpToDate.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<String?>(null) }
    var editingCustom by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LayerPageGutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (subscriptions.isEmpty()) {
            SectionNote(stringResource(Res.string.sticker_manage_subs_empty))
        }
        subscriptions.forEach { subscription ->
            SubscriptionCard(
                subscription = subscription,
                onUpdate = { viewModel.applyUpdate(subscription.slug) },
                onToggleHidden = { folder -> viewModel.setHidden(subscription.slug, folder.path, !folder.hidden) },
                onMove = { from, to -> viewModel.moveFolder(subscription.slug, from, to) },
                onChangeFolders = { onChangeFolders(subscription.slug) },
                onOpenRepo = { onOpenUrl("https://github.com/${subscription.slug}") },
                onUnsubscribe = { confirming = subscription.slug },
            )
        }
        checkError?.let { Text(sourceErrorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (upToDate) {
            Text(
                stringResource(Res.string.sticker_manage_up_to_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column {
            SectionLabel(stringResource(Res.string.sticker_manage_cdn_title))
            GroupCard {
                CdnRow(
                    title = stringResource(Res.string.sticker_cdn_jsdelivr),
                    subtitle = stringResource(Res.string.sticker_cdn_jsdelivr_desc),
                    choice = StickerCdn.JSDELIVR,
                    current = cdn.effective,
                    latency = latency[StickerCdn.JSDELIVR],
                    first = true,
                    onSelect = { viewModel.selectCdn(StickerCdn.JSDELIVR) },
                )
                CdnRow(
                    title = stringResource(Res.string.sticker_cdn_raw),
                    subtitle = "raw.githubusercontent.com",
                    choice = StickerCdn.GITHUB_RAW,
                    current = cdn.effective,
                    latency = latency[StickerCdn.GITHUB_RAW],
                    onSelect = { viewModel.selectCdn(StickerCdn.GITHUB_RAW) },
                )
                CdnRow(
                    title = stringResource(Res.string.sticker_cdn_custom),
                    subtitle = cdn.normalizedCustomBase() ?: stringResource(Res.string.sticker_cdn_custom_desc),
                    choice = StickerCdn.CUSTOM,
                    current = cdn.effective,
                    latency = latency[StickerCdn.CUSTOM],
                    last = true,
                    onSelect = {
                        if (cdn.normalizedCustomBase() == null) editingCustom = true else viewModel.selectCdn(StickerCdn.CUSTOM)
                    },
                    onLongSelect = { editingCustom = true },
                )
            }
        }
        OutlinedButton(onClick = onSubscribeAnother, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text(stringResource(Res.string.sticker_manage_subscribe_another))
        }
        SectionNote(stringResource(Res.string.sticker_cdn_note))
    }

    confirming?.let { slug ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(Res.string.sticker_manage_unsubscribe_confirm, slug)) },
            text = { Text(stringResource(Res.string.sticker_manage_unsubscribe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsubscribe(slug)
                    confirming = null
                }) { Text(stringResource(Res.string.sticker_manage_unsubscribe), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (editingCustom) {
        CustomCdnDialog(
            initial = cdn.customBase,
            onDismiss = { editingCustom = false },
            onSave = {
                editingCustom = false
                viewModel.setCustomCdn(it)
            },
        )
    }
}

@Composable
private fun CdnRow(
    title: String,
    subtitle: String,
    choice: StickerCdn,
    current: StickerCdn,
    latency: CdnLatency?,
    onSelect: () -> Unit,
    first: Boolean = false,
    last: Boolean = false,
    onLongSelect: (() -> Unit)? = null,
) {
    val selected = choice == current
    GroupedListItem(
        first = first,
        last = last,
        selected = selected,
        onClick = onSelect,
        onLongClick = onLongSelect,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(title, style = groupedRowTitleStyle()) },
        supportingContent = {
            Text(
                if (selected) stringResource(Res.string.sticker_cdn_selected_desc, subtitle) else subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when (latency) {
                null, CdnLatency.Measuring -> Unit

                is CdnLatency.Millis -> Text(stringResource(Res.string.sticker_cdn_ms, latency.value.toInt()), style = MaterialTheme.typography.labelMedium)

                CdnLatency.Unreachable -> Text(
                    stringResource(Res.string.sticker_manage_timeout),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun CustomCdnDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = value.trim().startsWith("https://") && value.trim().length > "https://".length
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sticker_cdn_custom)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.sticker_cdn_custom_hint)) },
                supportingText = {
                    Text(
                        if (valid || value.isBlank()) {
                            stringResource(Res.string.sticker_cdn_custom_desc)
                        } else {
                            stringResource(Res.string.sticker_cdn_custom_invalid)
                        },
                    )
                },
                isError = !valid && value.isNotBlank(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = valid) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun SubscriptionCard(
    subscription: StickerSubscription,
    onUpdate: () -> Unit,
    onToggleHidden: (SubscribedFolder) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onChangeFolders: () -> Unit,
    onOpenRepo: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    LayerCard(contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(PlazaIcons.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("${subscription.owner} / ${subscription.repo}", style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        Res.string.sticker_manage_pinned,
                        subscription.pinnedSha.take(7),
                        TimeFormat.relative(subscription.checkedAtMillis, now),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (subscription.hasUpdate) {
                TextButton(onClick = onUpdate) {
                    Text(
                        if (subscription.newCount > 0) {
                            stringResource(Res.string.sticker_manage_update_count, subscription.newCount)
                        } else {
                            stringResource(Res.string.sticker_update_action)
                        },
                    )
                }
            }
        }
        subscription.folders.forEachIndexed { index, folder ->
            FolderManageRow(
                folder = folder,
                onToggleHidden = { onToggleHidden(folder) },
                onDragBy = { steps -> onMove(index, (index + steps).coerceIn(0, subscription.folders.lastIndex)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChangeFolders) { Text(stringResource(Res.string.sticker_manage_change_folders)) }
            TextButton(onClick = onOpenRepo) { Text(stringResource(Res.string.sticker_manage_open_repo)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onUnsubscribe) {
                Text(stringResource(Res.string.sticker_manage_unsubscribe), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * One folder of a subscription. The handle reorders by whole rows: a vertical drag of one row's
 * height moves the folder one place — hand-rolled for the reason the grid above is, nothing in
 * Compose or Material reorders a list.
 */
@Composable
private fun FolderManageRow(
    folder: SubscribedFolder,
    onToggleHidden: () -> Unit,
    onDragBy: (Int) -> Unit,
) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = FOLDER_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            PlazaIcons.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.pointerInput(folder.path) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val steps = (dragged / FOLDER_ROW_HEIGHT.toPx()).toInt()
                        if (steps != 0) onDragBy(steps)
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                ) { _, amount -> dragged += amount }
            },
        )
        Text(
            if (folder.hidden) stringResource(Res.string.sticker_manage_hidden, folder.name) else folder.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (folder.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(Res.string.sticker_manage_folder_count, folder.files.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggleHidden) {
            Icon(
                if (folder.hidden) PlazaIcons.VisibilityOff else PlazaIcons.Visibility,
                contentDescription = stringResource(if (folder.hidden) Res.string.sticker_manage_show else Res.string.sticker_manage_hide),
            )
        }
    }
}

private const val MANAGE_COLUMNS = 5
private const val URL_KEY = "u:"
private val FOLDER_ROW_HEIGHT = 48.dp
