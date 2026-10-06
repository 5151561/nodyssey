package io.github.nodyssey.ui.sticker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nodyssey.data.sticker.MAX_NAME_LENGTH
import io.github.nodyssey.data.sticker.MINE_GROUP_KEY
import io.github.nodyssey.data.sticker.MySticker
import io.github.nodyssey.data.sticker.StickerCdnSettings
import io.github.nodyssey.data.sticker.StickerGroupLayout
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.sticker.SubscribedFolder
import io.github.nodyssey.data.sticker.stickerNameFromPath
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_delete
import io.github.nodyssey.ui.resources.action_rename
import io.github.nodyssey.ui.resources.action_save
import io.github.nodyssey.ui.resources.sticker_add
import io.github.nodyssey.ui.resources.sticker_added
import io.github.nodyssey.ui.resources.sticker_group_mine
import io.github.nodyssey.ui.resources.sticker_menu_move_top
import io.github.nodyssey.ui.resources.sticker_menu_save_to_mine
import io.github.nodyssey.ui.resources.sticker_mine_empty
import io.github.nodyssey.ui.resources.sticker_name_label
import io.github.nodyssey.ui.resources.sticker_rename_title
import io.github.nodyssey.ui.resources.sticker_undo
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.editor.EmojiEntry
import io.github.plaza.designsys.editor.EmojiGroup
import io.github.plaza.designsys.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** `![name](url)` — what a saved or subscribed sticker inserts. The alt text cannot end the syntax early. */
internal fun stickerMarkdown(
    name: String,
    url: String,
): String = "![${name.replace(ALT_UNSAFE, " ").trim()}]($url)"

private val ALT_UNSAFE = Regex("""[\[\]()]""")

/** A line under the panel with an undo — 「已添加到我的表情 · 撤销」. */
internal class StickerNotice(
    val text: String,
    val undoUrls: List<String> = emptyList(),
)

/**
 * The panel's groups — 我的, the subscribed folders and [siteGroups] — in the order 表情管理 keeps
 * (1g), less the ones hidden there. Also hosts what those groups open — 添加表情, the rename dialog —
 * and the undo line under the grid.
 *
 * Whether a pack has an update is said on 表情管理 and not here (1b): the panel is for sending.
 *
 * [content] draws the panel with the groups this hands it.
 */
@Composable
internal fun StickerPanelHost(
    library: StickerLibrary,
    siteGroups: List<EmojiGroup>,
    content: @Composable (groups: List<EmojiGroup>) -> Unit,
) {
    val mine by remember(library) { library.mine }.collectAsStateWithLifecycle(emptyList())
    val subscriptions by remember(library) { library.subscriptions }.collectAsStateWithLifecycle(emptyList())
    val layout by remember(library) { library.groupLayout }.collectAsStateWithLifecycle(StickerGroupLayout())
    val cdn by remember(library) { library.cdn }.collectAsStateWithLifecycle(StickerCdnSettings())
    val addViewModel = viewModel(key = "sticker-add") { StickerAddViewModel(library) }
    val scope = rememberCoroutineScope()

    var addOpen by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<MySticker?>(null) }
    var notice by remember { mutableStateOf<StickerNotice?>(null) }

    // Throttled inside to once per six hours per repository, so opening the panel is cheap.
    LaunchedEffect(library) { library.checkForUpdates(force = false) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

    val addedText = stringResource(Res.string.sticker_added)
    val mineTitle = stringResource(Res.string.sticker_group_mine)
    val mineEmpty = stringResource(Res.string.sticker_mine_empty)
    val openAdd = {
        addViewModel.reset()
        addOpen = true
    }

    val mineGroup = EmojiGroup(
        title = { mineTitle },
        entries = mine.map { EmojiEntry.Sticker(name = it.name, shortcode = stickerMarkdown(it.name, it.url), url = it.url) },
        key = MINE_GROUP_KEY,
        icon = Icons.Default.Favorite,
        leadingCell = { modifier -> AddStickerCell(onClick = openAdd, modifier = modifier) },
        entryMenu = { entry, dismiss ->
            (entry as? EmojiEntry.Sticker)?.let { sticker ->
                MineStickerMenu(
                    onMoveToTop = {
                        dismiss()
                        scope.launch { library.moveToTop(listOf(sticker.url)) }
                    },
                    onRename = {
                        dismiss()
                        renaming = MySticker(sticker.url, sticker.name)
                    },
                    onDelete = {
                        dismiss()
                        scope.launch { library.remove(listOf(sticker.url)) }
                    },
                )
            }
        },
        emptyText = mineEmpty,
    )
    val saveToMine: @Composable ColumnScope.(EmojiEntry, () -> Unit) -> Unit = { entry, dismiss ->
        val sticker = entry as? EmojiEntry.Sticker
        if (sticker != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.sticker_menu_save_to_mine)) },
                leadingIcon = { Icon(Icons.Default.FavoriteBorder, contentDescription = null) },
                onClick = {
                    dismiss()
                    scope.launch {
                        val added = library.add(listOf(MySticker(sticker.url, sticker.name)))
                        if (added.isNotEmpty()) notice = StickerNotice(addedText, added)
                    }
                },
            )
        }
    }
    // The default order until the stored one is read — a frame at most. Not an empty list: the panel
    // picks the tab it opens on from the groups it is first handed, and with none it would open on
    // an empty 最近使用 for a new reader rather than on the site's stickers.
    val groups = arrangeGroups(mine, subscriptions, layout, siteGroups)
        .filterNot { it.hidden }
        .map { item ->
            when (item) {
                is StickerGroupItem.Mine -> mineGroup
                is StickerGroupItem.Folder -> folderGroup(item, cdn, saveToMine)
                is StickerGroupItem.Site -> item.group
            }
        }

    Box {
        content(groups)
        notice?.let { current ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
                action = if (current.undoUrls.isEmpty()) {
                    null
                } else {
                    {
                        TextButton(onClick = {
                            addViewModel.undoAdd(current.undoUrls)
                            notice = null
                        }) { Text(stringResource(Res.string.sticker_undo)) }
                    }
                },
            ) { Text(current.text) }
        }
    }

    if (addOpen) {
        StickerAddSheet(
            viewModel = addViewModel,
            onDismiss = { addOpen = false },
            onNotice = { notice = it },
        )
    }
    renaming?.let { target ->
        RenameStickerDialog(
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { library.rename(target.url, name) }
            },
        )
    }
}

@Composable
private fun folderGroup(
    item: StickerGroupItem.Folder,
    cdn: StickerCdnSettings,
    saveToMine: @Composable ColumnScope.(EmojiEntry, () -> Unit) -> Unit,
): EmojiGroup {
    val folder = item.folder
    return key(item.key) {
        EmojiGroup(
            title = { folder.name },
            // Remembered per folder: a pack is a few hundred links, and the panel recomposes on every
            // insert, which is when the recents change.
            entries = remember(folder, cdn) { folderEntries(folder, cdn) },
            key = item.key,
            entryMenu = saveToMine,
        )
    }
}

/** A subscribed folder's pictures as the panel and 1h draw them, each linked through [cdn]. */
internal fun folderEntries(
    folder: SubscribedFolder,
    cdn: StickerCdnSettings,
): List<EmojiEntry.Sticker> =
    folder.files.map { path ->
        val url = cdn.urlFor(folder.owner, folder.repo, folder.pinnedSha, path)
        val name = stickerNameFromPath(path)
        EmojiEntry.Sticker(name = name, shortcode = stickerMarkdown(name, url), url = url)
    }

@Composable
private fun MineStickerMenu(
    onMoveToTop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.sticker_menu_move_top)) },
        leadingIcon = { Icon(PlazaIcons.VerticalAlignTop, contentDescription = null) },
        onClick = onMoveToTop,
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.action_rename)) },
        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        onClick = onRename,
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.action_delete)) },
        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
        onClick = onDelete,
    )
}

/** 我的's first cell: the way into 添加表情, scrolling with the stickers after it. */
@Composable
private fun AddStickerCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.sticker_add), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun RenameStickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sticker_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(Res.string.sticker_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private const val NOTICE_MILLIS = 4_000L
