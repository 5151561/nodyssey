package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.common.PlazaSheet
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_delete
import io.github.nodyssey.ui.resources.action_more
import io.github.nodyssey.ui.resources.action_rename
import io.github.nodyssey.ui.resources.sticker_added
import io.github.nodyssey.ui.resources.sticker_added_count
import io.github.nodyssey.ui.resources.sticker_group_broken_banner
import io.github.nodyssey.ui.resources.sticker_group_from
import io.github.nodyssey.ui.resources.sticker_group_remove
import io.github.nodyssey.ui.resources.sticker_group_select
import io.github.nodyssey.ui.resources.sticker_image_copy
import io.github.nodyssey.ui.resources.sticker_image_link_copied
import io.github.nodyssey.ui.resources.sticker_manage_export
import io.github.nodyssey.ui.resources.sticker_manage_exported
import io.github.nodyssey.ui.resources.sticker_manage_hide
import io.github.nodyssey.ui.resources.sticker_manage_mine_empty
import io.github.nodyssey.ui.resources.sticker_manage_open_repo
import io.github.nodyssey.ui.resources.sticker_manage_remove_broken_body
import io.github.nodyssey.ui.resources.sticker_manage_remove_broken_confirm
import io.github.nodyssey.ui.resources.sticker_manage_selected
import io.github.nodyssey.ui.resources.sticker_manage_show
import io.github.nodyssey.ui.resources.sticker_menu_move_top
import io.github.nodyssey.ui.resources.sticker_menu_save_to_mine
import io.github.nodyssey.ui.resources.sticker_undo
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 组详情 (1h). 我的 is the full set — rename, move, delete, drag to reorder; a subscribed folder or
 * a site pack can only be saved into 我的. Tapping a cell opens its sheet; the bar's checklist
 * starts 多选.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerGroupRoute(
    viewModel: StickerGroupViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selecting by viewModel.selecting.collectAsStateWithLifecycle()
    var opened by remember { mutableStateOf<GroupCell?>(null) }
    var renaming by remember { mutableStateOf<GroupCell?>(null) }
    var confirmingBroken by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<StickerNotice?>(null) }
    val appBarState = rememberOneHandAppBarState()
    val copy = rememberClipboardCopy()
    val linkCopied = stringResource(Res.string.sticker_image_link_copied)
    val exported = stringResource(Res.string.sticker_manage_exported, page?.item?.count ?: 0)
    val addedOne = stringResource(Res.string.sticker_added)
    val current = page
    val item = current?.item
    val mine = item is StickerGroupItem.Mine
    val addedText: @Composable (List<String>) -> String = { urls ->
        if (urls.size == 1) addedOne else stringResource(Res.string.sticker_added_count, urls.size)
    }
    var added by remember { mutableStateOf<List<String>?>(null) }
    added?.let { urls ->
        val text = addedText(urls)
        LaunchedEffect(urls) {
            if (urls.isNotEmpty()) notice = StickerNotice(text, urls)
            added = null
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = when {
                    selecting -> stringResource(Res.string.sticker_manage_selected, selection.size)
                    item != null -> groupTitle(item)
                    else -> ""
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
                    when {
                        selecting && mine -> {
                            IconButton(onClick = viewModel::moveSelectedToTop, enabled = selection.isNotEmpty()) {
                                Icon(PlazaIcons.VerticalAlignTop, contentDescription = stringResource(Res.string.sticker_menu_move_top))
                            }
                            IconButton(onClick = viewModel::deleteSelected, enabled = selection.isNotEmpty()) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                            }
                        }

                        selecting -> IconButton(
                            onClick = { viewModel.saveSelectedToMine { added = it } },
                            enabled = selection.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = stringResource(Res.string.sticker_menu_save_to_mine))
                        }

                        item != null -> {
                            if (current.cells.any { it.url != null }) {
                                IconButton(onClick = viewModel::startSelecting) {
                                    Icon(PlazaIcons.Checklist, contentDescription = stringResource(Res.string.sticker_group_select))
                                }
                            }
                            GroupMenu(
                                item = item,
                                onToggleHidden = { viewModel.setHidden(!item.hidden) },
                                onExport = { copy("stickers", viewModel.exportLinks(), exported) },
                                onOpenRepo = (item as? StickerGroupItem.Folder)?.let { folder ->
                                    { onOpenUrl("https://github.com/${folder.subscription.slug}") }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (current != null) {
                GroupGrid(
                    page = current,
                    selection = selection,
                    selecting = selecting,
                    onOpen = { cell -> if (cell.url != null) opened = cell },
                    onToggle = viewModel::toggle,
                    onDragMove = viewModel::dragMove,
                    onDragEnd = viewModel::dragEnd,
                    onRemoveBroken = { confirmingBroken = true },
                )
            }
            notice?.let { shown ->
                LaunchedEffect(shown) {
                    delay(4_000)
                    notice = null
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
                    action = if (shown.undoUrls.isEmpty()) {
                        null
                    } else {
                        {
                            TextButton(onClick = {
                                viewModel.undoAdd(shown.undoUrls)
                                notice = null
                            }) { Text(stringResource(Res.string.sticker_undo)) }
                        }
                    },
                ) { Text(shown.text) }
            }
        }
    }

    opened?.let { cell ->
        CellSheet(
            cell = cell,
            mine = mine,
            onDismiss = { opened = null },
            onRename = {
                opened = null
                renaming = cell
            },
            onMoveToTop = {
                opened = null
                viewModel.moveToTop(cell.id)
            },
            onCopy = {
                opened = null
                copy("sticker", cell.url.orEmpty(), linkCopied)
            },
            onDelete = {
                opened = null
                viewModel.delete(cell.id)
            },
            onSaveToMine = {
                opened = null
                viewModel.saveToMine(cell) { added = it }
            },
        )
    }
    renaming?.let { target ->
        RenameStickerDialog(
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                viewModel.rename(target.id, name)
            },
        )
    }
    if (confirmingBroken) {
        AlertDialog(
            onDismissRequest = { confirmingBroken = false },
            title = { Text(stringResource(Res.string.sticker_manage_remove_broken_confirm, current?.brokenCount ?: 0)) },
            text = { Text(stringResource(Res.string.sticker_manage_remove_broken_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeBroken()
                    confirmingBroken = false
                }) { Text(stringResource(Res.string.sticker_group_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingBroken = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun GroupMenu(
    item: StickerGroupItem,
    onToggleHidden: () -> Unit,
    onExport: () -> Unit,
    onOpenRepo: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (item.hidden) Res.string.sticker_manage_show else Res.string.sticker_manage_hide)) },
                leadingIcon = { Icon(if (item.hidden) PlazaIcons.Visibility else PlazaIcons.VisibilityOff, contentDescription = null) },
                onClick = {
                    open = false
                    onToggleHidden()
                },
            )
            if (item is StickerGroupItem.Mine) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sticker_manage_export)) },
                    leadingIcon = { Icon(PlazaIcons.Link, contentDescription = null) },
                    enabled = item.count > 0,
                    onClick = {
                        open = false
                        onExport()
                    },
                )
            }
            onOpenRepo?.let { openRepo ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.sticker_manage_open_repo)) },
                    leadingIcon = { Icon(PlazaIcons.Code, contentDescription = null) },
                    onClick = {
                        open = false
                        openRepo()
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupGrid(
    page: GroupPage,
    selection: Set<String>,
    selecting: Boolean,
    onOpen: (GroupCell) -> Unit,
    onToggle: (String) -> Unit,
    onDragMove: (String, String) -> Unit,
    onDragEnd: () -> Unit,
    onRemoveBroken: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val mine = page.item is StickerGroupItem.Mine
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
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxSize()
            .readableWidth()
            // Hand-rolled: neither Compose nor Material has a reorderable grid. In 我的 a long press
            // picks a cell up and each cell the finger crosses swaps in; a long press that never
            // moves selects instead, as it does in every other group. Replace with the platform's
            // when one ships.
            .pointerInput(page.cells.size, layoutDirection, mine) {
                val padding = Offset(
                    contentPadding.calculateLeftPadding(layoutDirection).toPx(),
                    contentPadding.calculateTopPadding().toPx(),
                )
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragging = keyAt(offset, padding)?.takeIf { it.startsWith(CELL_KEY) }
                        moved = false
                    },
                    onDrag = { change, _ ->
                        val from = dragging ?: return@detectDragGesturesAfterLongPress
                        if (!mine) return@detectDragGesturesAfterLongPress
                        val over = keyAt(change.position, padding)?.takeIf { it.startsWith(CELL_KEY) } ?: return@detectDragGesturesAfterLongPress
                        if (over != from) {
                            moved = true
                            onDragMove(from.removePrefix(CELL_KEY), over.removePrefix(CELL_KEY))
                        }
                    },
                    onDragEnd = {
                        val key = dragging
                        val id = key?.removePrefix(CELL_KEY)
                        if (id != null && !moved && page.cells.any { it.id == id && it.url != null }) onToggle(id)
                        if (moved) onDragEnd()
                        dragging = null
                    },
                    onDragCancel = {
                        if (moved) onDragEnd()
                        dragging = null
                    },
                )
            },
    ) {
        if (page.brokenCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InlineBanner(
                    text = stringResource(Res.string.sticker_group_broken_banner, page.brokenCount),
                    icon = PlazaIcons.LinkOff,
                    action = { TextButton(onClick = onRemoveBroken) { Text(stringResource(Res.string.sticker_group_remove)) } },
                )
            }
        }
        if (page.cells.isEmpty() && mine) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(Res.string.sticker_manage_mine_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
        }
        items(page.cells, key = { CELL_KEY + it.id }) { cell ->
            GroupCellView(
                cell = cell,
                selected = cell.id in selection,
                lifted = dragging == CELL_KEY + cell.id,
                onClick = { if (selecting) onToggle(cell.id) else onOpen(cell) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun GroupCellView(
    cell: GroupCell,
    selected: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                .then(
                    if (selected || lifted) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                cell.url == null -> Text(cell.id, fontSize = 28.sp)

                cell.broken -> Icon(PlazaIcons.BrokenImage, contentDescription = cell.name, tint = MaterialTheme.colorScheme.onSurfaceVariant)

                else -> StickerThumbnail(
                    url = cell.url,
                    contentDescription = cell.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(Spacing.xs),
                )
            }
            if (selected) SelectionCheck(Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        if (cell.url != null) {
            Text(
                cell.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (cell.broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One picture's details and what can be done with it, from the bottom (1h). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellSheet(
    cell: GroupCell,
    mine: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMoveToTop: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onSaveToMine: () -> Unit,
) {
    val url = cell.url ?: return
    PlazaSheet(onDismiss = onDismiss) {
        Column(
            Modifier.padding(horizontal = LayerPageGutter).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cell.broken) {
                        Icon(PlazaIcons.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StickerThumbnail(
                            url = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(Spacing.xs),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(cell.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        url.removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(Res.string.sticker_group_from, url.substringAfter("://").substringBefore('/')),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (mine) {
                    SheetAction(Icons.Default.Edit, stringResource(Res.string.action_rename), onRename, Modifier.weight(1f))
                    SheetAction(PlazaIcons.VerticalAlignTop, stringResource(Res.string.sticker_menu_move_top), onMoveToTop, Modifier.weight(1f))
                    SheetAction(PlazaIcons.Link, stringResource(Res.string.sticker_image_copy), onCopy, Modifier.weight(1f))
                    SheetAction(
                        Icons.Default.Delete,
                        stringResource(Res.string.action_delete),
                        onDelete,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    SheetAction(Icons.Default.FavoriteBorder, stringResource(Res.string.sticker_menu_save_to_mine), onSaveToMine, Modifier.weight(1f))
                    SheetAction(PlazaIcons.Link, stringResource(Res.string.sticker_image_copy), onCopy, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.sm),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = if (color == Color.Unspecified) LocalContentColor.current else color)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val GRID_COLUMNS = 4
private const val CELL_KEY = "c:"
