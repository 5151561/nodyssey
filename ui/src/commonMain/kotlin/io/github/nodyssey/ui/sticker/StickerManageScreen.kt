package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.sticker.StickerCdn
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.ui.account.nameRes
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.action_save
import io.github.nodyssey.ui.resources.sticker_backup_export
import io.github.nodyssey.ui.resources.sticker_backup_export_desc
import io.github.nodyssey.ui.resources.sticker_backup_exported
import io.github.nodyssey.ui.resources.sticker_backup_import
import io.github.nodyssey.ui.resources.sticker_backup_import_desc
import io.github.nodyssey.ui.resources.sticker_backup_import_failed
import io.github.nodyssey.ui.resources.sticker_backup_import_hint
import io.github.nodyssey.ui.resources.sticker_backup_import_note
import io.github.nodyssey.ui.resources.sticker_backup_import_title
import io.github.nodyssey.ui.resources.sticker_backup_imported
import io.github.nodyssey.ui.resources.sticker_backup_importing
import io.github.nodyssey.ui.resources.sticker_backup_not_backup
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
import io.github.nodyssey.ui.resources.sticker_group_mine
import io.github.nodyssey.ui.resources.sticker_manage_add
import io.github.nodyssey.ui.resources.sticker_manage_backup
import io.github.nodyssey.ui.resources.sticker_manage_broken_count
import io.github.nodyssey.ui.resources.sticker_manage_cdn
import io.github.nodyssey.ui.resources.sticker_manage_folder_count
import io.github.nodyssey.ui.resources.sticker_manage_github
import io.github.nodyssey.ui.resources.sticker_manage_github_summary
import io.github.nodyssey.ui.resources.sticker_manage_github_summary_none
import io.github.nodyssey.ui.resources.sticker_manage_groups_label
import io.github.nodyssey.ui.resources.sticker_manage_has_update
import io.github.nodyssey.ui.resources.sticker_manage_hidden_label
import io.github.nodyssey.ui.resources.sticker_manage_hide
import io.github.nodyssey.ui.resources.sticker_manage_host
import io.github.nodyssey.ui.resources.sticker_manage_host_none
import io.github.nodyssey.ui.resources.sticker_manage_host_summary
import io.github.nodyssey.ui.resources.sticker_manage_list_separator
import io.github.nodyssey.ui.resources.sticker_manage_more_groups
import io.github.nodyssey.ui.resources.sticker_manage_show
import io.github.nodyssey.ui.resources.sticker_manage_site
import io.github.nodyssey.ui.resources.sticker_manage_sources
import io.github.nodyssey.ui.resources.sticker_manage_timeout
import io.github.nodyssey.ui.resources.sticker_manage_title
import io.github.nodyssey.ui.resources.sticker_manage_total
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SectionNote
import io.github.plaza.designsys.component.groupSlice
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 表情管理 (1g): every group of the panel in one list, and where they come from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManageRoute(
    viewModel: StickerManageViewModel,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSources: () -> Unit,
    onOpenImageHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val brokenMine by viewModel.brokenMine.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val hostConfig by viewModel.hostConfig.collectAsStateWithLifecycle()
    val cdn by viewModel.cdn.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    val library = LocalStickerLibrary.current
    val addViewModel = library?.let { viewModel(key = "sticker-add") { StickerAddViewModel(it) } }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var cdnOpen by rememberSaveable { mutableStateOf(false) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<StickerNotice?>(null) }
    val appBarState = rememberOneHandAppBarState()
    val scope = rememberCoroutineScope()
    val copy = rememberClipboardCopy()
    val exported = stringResource(Res.string.sticker_backup_exported)

    LaunchedEffect(Unit) {
        viewModel.checkLinks()
        viewModel.measureCdns()
        viewModel.checkUpdates(force = false)
    }

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.sticker_manage_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (addViewModel != null) {
                        FilledTonalButton(
                            onClick = {
                                addViewModel.reset()
                                addOpen = true
                            },
                            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                            modifier = Modifier.padding(end = Spacing.sm),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(Res.string.sticker_manage_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .padding(horizontal = LayerPageGutter, vertical = Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(Res.string.sticker_manage_groups_label), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(Res.string.sticker_manage_total, groups.sumOf { it.count }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.md, top = 10.dp, bottom = 2.dp),
                    )
                }
                GroupList(
                    groups = groups,
                    brokenMine = brokenMine,
                    cdn = cdn,
                    onOpen = onOpenGroup,
                    onMove = viewModel::dragMove,
                    onDragEnd = viewModel::dragEnd,
                    onSetHidden = viewModel::setHidden,
                )

                SectionLabel(stringResource(Res.string.sticker_manage_sources), modifier = Modifier.padding(top = Spacing.md))
                GroupCard {
                    val updates = subscriptions.count { it.hasUpdate }
                    GroupedRow(
                        title = stringResource(Res.string.sticker_manage_github),
                        subtitle = if (updates > 0) {
                            stringResource(Res.string.sticker_manage_github_summary, subscriptions.size, updates)
                        } else {
                            stringResource(Res.string.sticker_manage_github_summary_none, subscriptions.size)
                        },
                        icon = PlazaIcons.FolderZip,
                        first = true,
                        onClick = onOpenSources,
                        trailing = if (updates > 0) {
                            { Badge() }
                        } else {
                            null
                        },
                    )
                    GroupedRow(
                        title = stringResource(Res.string.sticker_manage_host),
                        subtitle = hostConfig?.takeIf { it.isConfigured }?.let {
                            stringResource(Res.string.sticker_manage_host_summary, stringResource(it.provider.nameRes()))
                        } ?: stringResource(Res.string.sticker_manage_host_none),
                        icon = PlazaIcons.CloudUpload,
                        onClick = onOpenImageHost,
                    )
                    GroupedRow(
                        title = stringResource(Res.string.sticker_manage_cdn),
                        subtitle = cdnSummary(cdn, latency),
                        icon = PlazaIcons.Speed,
                        last = true,
                        onClick = { cdnOpen = true },
                    )
                }

                SectionLabel(stringResource(Res.string.sticker_manage_backup), modifier = Modifier.padding(top = Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BackupTile(
                        title = stringResource(Res.string.sticker_backup_export),
                        subtitle = stringResource(Res.string.sticker_backup_export_desc),
                        icon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { scope.launch { copy("stickers", viewModel.exportBackup(), exported) } },
                        modifier = Modifier.weight(1f),
                    )
                    BackupTile(
                        title = stringResource(Res.string.sticker_backup_import),
                        subtitle = stringResource(Res.string.sticker_backup_import_desc),
                        icon = { Icon(PlazaIcons.Download, contentDescription = null) },
                        onClick = {
                            viewModel.resetImport()
                            importOpen = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(Spacing.lg))
            }
            notice?.let { current ->
                LaunchedEffect(current) {
                    delay(4_000)
                    notice = null
                }
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md)) { Text(current.text) }
            }
        }
    }

    if (addOpen && addViewModel != null) {
        StickerAddSheet(viewModel = addViewModel, onDismiss = { addOpen = false }, onNotice = { notice = it })
    }
    if (cdnOpen) {
        CdnSheet(viewModel = viewModel, onDismiss = { cdnOpen = false })
    }
    if (importOpen) {
        ImportDialog(viewModel = viewModel, onDismiss = { importOpen = false })
    }
}

// ---- 面板里的组 ------------------------------------------------------------------------------------

/** Past this many groups the list shows its first [COLLAPSED_GROUPS] and a 「还有 …」 row. */
private const val COLLAPSE_AFTER = 6
private const val COLLAPSED_GROUPS = 5

/**
 * The groups, reorderable by holding a row (or grabbing its handle) and hidden by swiping it left.
 *
 * The reorder is hand-rolled: neither Compose nor Material reorders a list. A row follows the finger
 * and swaps with its neighbour once it is past half that neighbour's height — the swap goes through
 * [onMove] and comes back as a new order, and the finger's offset is reduced by the height it just
 * crossed so the row stays under it. Replace with the platform's when one ships.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupList(
    groups: List<StickerGroupItem>,
    brokenMine: Int,
    cdn: StickerCdnSettings,
    onOpen: (String) -> Unit,
    onMove: (dragged: String, target: String) -> Unit,
    onDragEnd: () -> Unit,
    onSetHidden: (String, Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsible = groups.size > COLLAPSE_AFTER
    val shown = if (collapsible && !expanded) groups.take(COLLAPSED_GROUPS) else groups
    val rest = groups.drop(shown.size)
    val heights = remember { mutableStateMapOf<String, Int>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val keys by rememberUpdatedState(shown.map { it.key })
    val layers = LocalPlazaLayers.current

    fun drag(amount: Float) {
        val key = dragging ?: return
        offset += amount
        val index = keys.indexOf(key)
        val next = keys.getOrNull(index + 1)
        val previous = keys.getOrNull(index - 1)
        val nextHeight = next?.let { heights[it] } ?: 0
        val previousHeight = previous?.let { heights[it] } ?: 0
        if (next != null && offset > nextHeight / 2f) {
            onMove(key, next)
            offset -= nextHeight
        } else if (previous != null && offset < -previousHeight / 2f) {
            onMove(key, previous)
            offset += previousHeight
        }
    }

    fun endDrag() {
        if (dragging != null) onDragEnd()
        dragging = null
        offset = 0f
    }

    Column {
        shown.forEachIndexed { index, item ->
            androidx.compose.runtime.key(item.key) {
                val isDragged = dragging == item.key
                val startDrag: (Offset) -> Unit = {
                    dragging = item.key
                    offset = 0f
                }
                val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
                    change.consume()
                    drag(amount.y)
                }
                Box(
                    Modifier
                        .onSizeChanged { heights[item.key] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragged) offset else 0f
                            shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                        }.pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = startDrag,
                                onDrag = onDrag,
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag,
                            )
                        },
                ) {
                    GroupRow(
                        item = item,
                        brokenMine = brokenMine,
                        cdn = cdn,
                        first = index == 0,
                        last = index == shown.lastIndex && rest.isEmpty(),
                        onClick = { onOpen(item.key) },
                        onToggleHidden = { onSetHidden(item.key, !item.hidden) },
                        handle = Modifier.pointerInput(item.key) {
                            detectDragGestures(
                                onDragStart = startDrag,
                                onDrag = onDrag,
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag,
                            )
                        },
                    )
                }
            }
        }
        if (rest.isNotEmpty()) {
            val separator = stringResource(Res.string.sticker_manage_list_separator)
            val names = rest.take(2).map { groupTitle(it) }.joinToString(separator)
            Box(Modifier.fillMaxWidth().groupSlice(layers, first = false, last = true)) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.align(Alignment.Center)) {
                    Text(stringResource(Res.string.sticker_manage_more_groups, names, rest.size))
                }
            }
        }
    }
}

@Composable
internal fun groupTitle(item: StickerGroupItem): String =
    when (item) {
        is StickerGroupItem.Mine -> stringResource(Res.string.sticker_group_mine)
        is StickerGroupItem.Folder -> item.folder.name
        is StickerGroupItem.Site -> item.group.title()
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupRow(
    item: StickerGroupItem,
    brokenMine: Int,
    cdn: StickerCdnSettings,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
    /** Goes on the drag handle: grabbing it moves the row at once, without the long press. */
    handle: Modifier = Modifier,
) {
    val layers = LocalPlazaLayers.current
    val toggleLabel = stringResource(if (item.hidden) Res.string.sticker_manage_show else Res.string.sticker_manage_hide)
    // `remember` rather than `rememberSwipeToDismissBoxState` for ReadHistoryScreen's reason: a swipe
    // is a gesture in progress and has no business being saved with the row.
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipe = remember(positionalThreshold) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth().groupSlice(layers, first, last)) {
        SwipeToDismissBox(
            state = swipe,
            enableDismissFromStartToEnd = false,
            // Not a dismissal: the row stays, hidden or shown, and springs back into place.
            onDismiss = {
                onToggleHidden()
                scope.launch { swipe.reset() }
            },
            backgroundContent = { HideBackdrop(toggleLabel, hidden = item.hidden) },
        ) {
            ListItem(
                onClick = onClick,
                colors = ListItemDefaults.colors(containerColor = layers.card),
                // Square in every state, or the backdrop shows at the corners; see ReadHistoryScreen.
                shapes = ListItemDefaults.shapes(
                    shape = RectangleShape,
                    selectedShape = RectangleShape,
                    pressedShape = RectangleShape,
                    focusedShape = RectangleShape,
                    hoveredShape = RectangleShape,
                    draggedShape = RectangleShape,
                ),
                // Swipe and hold are gestures; TalkBack gets 隐藏 by name.
                modifier = Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(toggleLabel) {
                            onToggleHidden()
                            true
                        },
                    )
                },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            PlazaIcons.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = handle.size(20.dp),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        GroupIcon(item, cdn, Modifier.alpha(if (item.hidden) HIDDEN_ALPHA else 1f))
                    }
                },
                supportingContent = { Text(groupSubtitle(item, brokenMine), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    if (item.hidden) {
                        Icon(PlazaIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                },
            ) {
                Text(
                    groupTitle(item),
                    style = groupedRowTitleStyle(),
                    color = if (item.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun groupSubtitle(
    item: StickerGroupItem,
    brokenMine: Int,
) = buildAnnotatedString {
    val separator = " · "
    if (item.hidden) {
        append(stringResource(Res.string.sticker_manage_hidden_label))
    } else {
        append(stringResource(Res.string.sticker_manage_folder_count, item.count))
    }
    when (item) {
        is StickerGroupItem.Mine -> if (brokenMine > 0 && !item.hidden) {
            append(separator)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append(stringResource(Res.string.sticker_manage_broken_count, brokenMine))
            }
        }

        is StickerGroupItem.Folder -> {
            append(separator)
            append(item.subscription.repo)
            if (item.updatable && !item.hidden) {
                append(separator)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append(stringResource(Res.string.sticker_manage_has_update))
                }
            }
        }

        is StickerGroupItem.Site -> {
            append(separator)
            append(stringResource(Res.string.sticker_manage_site))
        }
    }
}

/** What tells the three kinds apart at a glance: a heart for 我的, the pack's own pictures, NS for the site. */
@Composable
internal fun GroupIcon(
    item: StickerGroupItem,
    cdn: StickerCdnSettings,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val base = modifier.size(GROUP_ICON_SIZE).clip(shape)
    when (item) {
        is StickerGroupItem.Mine -> Box(base.background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
        }

        is StickerGroupItem.Folder -> {
            val urls = folderEntries(item.folder.copy(files = item.folder.files.take(4)), cdn).map { it.url }
            Column(base.background(MaterialTheme.colorScheme.surfaceContainerHigh), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                urls.chunked(2).take(2).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        row.forEach { url ->
                            StickerThumbnail(url = url, contentDescription = null, modifier = Modifier.weight(1f).fillMaxSize())
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        is StickerGroupItem.Site -> Box(base.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text(
                SITE_MARK,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HideBackdrop(
    label: String,
    hidden: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (hidden) PlazaIcons.Visibility else PlazaIcons.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

// ---- 来源 · 加载源 ---------------------------------------------------------------------------------

@Composable
private fun cdnSummary(
    cdn: StickerCdnSettings,
    latency: Map<StickerCdn, CdnLatency>,
): String {
    val name = when (cdn.effective) {
        StickerCdn.JSDELIVR -> stringResource(Res.string.sticker_cdn_jsdelivr)
        StickerCdn.GITHUB_RAW -> stringResource(Res.string.sticker_cdn_raw)
        StickerCdn.CUSTOM -> stringResource(Res.string.sticker_cdn_custom)
    }
    return when (val speed = latency[cdn.effective]) {
        is CdnLatency.Millis -> "$name · ${stringResource(Res.string.sticker_cdn_ms, speed.value.toInt())}"
        CdnLatency.Unreachable -> "$name · ${stringResource(Res.string.sticker_manage_timeout)}"
        null, CdnLatency.Measuring -> name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CdnSheet(
    viewModel: StickerManageViewModel,
    onDismiss: () -> Unit,
) {
    val cdn by viewModel.cdn.collectAsStateWithLifecycle()
    val latency by viewModel.latency.collectAsStateWithLifecycle()
    var editingCustom by remember { mutableStateOf(false) }

    PlazaSheet(onDismiss = onDismiss, title = stringResource(Res.string.sticker_manage_cdn)) {
        Column(
            Modifier.padding(horizontal = LayerPageGutter).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
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
            SectionNote(stringResource(Res.string.sticker_cdn_note))
        }
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

// ---- 备份 -----------------------------------------------------------------------------------------

@Composable
private fun BackupTile(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LayerCard(onClick = onClick, modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        icon()
        Text(title, style = groupedRowTitleStyle())
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 导入: a field to paste into rather than a read of the clipboard on open — Android lets an app read
 * the clipboard only while it has focus, and a field is the same one gesture either way.
 */
@Composable
private fun ImportDialog(
    viewModel: StickerManageViewModel,
    onDismiss: () -> Unit,
) {
    val outcome by viewModel.import.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val done = outcome as? ImportOutcome.Done
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sticker_backup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (done == null) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(Res.string.sticker_backup_import_hint)) },
                        isError = outcome == ImportOutcome.NotABackup,
                        supportingText = if (outcome == ImportOutcome.NotABackup) {
                            { Text(stringResource(Res.string.sticker_backup_not_backup)) }
                        } else {
                            null
                        },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    )
                    Text(
                        stringResource(Res.string.sticker_backup_import_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (outcome == ImportOutcome.Running) Text(stringResource(Res.string.sticker_backup_importing))
                } else {
                    Text(stringResource(Res.string.sticker_backup_imported, done.result.stickers, done.result.folders))
                    if (done.result.failedRepos.isNotEmpty()) {
                        Text(
                            stringResource(
                                Res.string.sticker_backup_import_failed,
                                done.result.failedRepos.joinToString(stringResource(Res.string.sticker_manage_list_separator)),
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (done == null) {
                TextButton(
                    onClick = { viewModel.importBackup(text) },
                    enabled = text.isNotBlank() && outcome != ImportOutcome.Running,
                ) { Text(stringResource(Res.string.sticker_backup_import)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_done)) }
            }
        },
        dismissButton = if (done == null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
        } else {
            null
        },
    )
}

private val GROUP_ICON_SIZE = 36.dp
private const val HIDDEN_ALPHA = 0.5f

/** 站点自带: the site's initials, the one mark the design gives a pack that is nobody's upload. */
private const val SITE_MARK = "NS"
