package io.github.nodyssey.ui.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.OfflineFailure
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.ui.account.formatBytes
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.siteErrorRecovery
import io.github.nodyssey.ui.common.snackbarDuration
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.action_sort
import io.github.nodyssey.ui.resources.bookmarks_download_all
import io.github.nodyssey.ui.resources.bookmarks_empty_body
import io.github.nodyssey.ui.resources.bookmarks_empty_filter_body
import io.github.nodyssey.ui.resources.bookmarks_empty_filter_title
import io.github.nodyssey.ui.resources.bookmarks_empty_search_body
import io.github.nodyssey.ui.resources.bookmarks_empty_search_title
import io.github.nodyssey.ui.resources.bookmarks_empty_title
import io.github.nodyssey.ui.resources.bookmarks_exit_selection
import io.github.nodyssey.ui.resources.bookmarks_remove_failed
import io.github.nodyssey.ui.resources.bookmarks_removed
import io.github.nodyssey.ui.resources.bookmarks_search
import io.github.nodyssey.ui.resources.bookmarks_search_clear
import io.github.nodyssey.ui.resources.bookmarks_search_hint
import io.github.nodyssey.ui.resources.bookmarks_select_all
import io.github.nodyssey.ui.resources.bookmarks_select_none
import io.github.nodyssey.ui.resources.bookmarks_selected
import io.github.nodyssey.ui.resources.bookmarks_sort_pending
import io.github.nodyssey.ui.resources.bookmarks_sort_replies
import io.github.nodyssey.ui.resources.bookmarks_sort_site
import io.github.nodyssey.ui.resources.bookmarks_title
import io.github.nodyssey.ui.resources.bookmarks_truncated
import io.github.nodyssey.ui.resources.history_undo
import io.github.nodyssey.ui.resources.offline_manage
import io.github.nodyssey.ui.resources.offline_status
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandAppBarState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.StatusView
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.StatusShapes
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BookmarksRoute(
    viewModel: BookmarksViewModel,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge and returns; 收藏 hits one like any other authenticated list. */
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BookmarksScreen(
        state = state,
        onBack = onBack,
        onPostClick = onPostClick,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onVerify = onVerify,
        onRetry = viewModel::retry,
        onFilter = viewModel::setFilter,
        onSort = viewModel::setSort,
        onSearching = viewModel::setSearching,
        onQuery = viewModel::setQuery,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onClearSelection = viewModel::clearSelection,
        onRemoveSelected = viewModel::removeSelected,
        onRestore = viewModel::restore,
        onDownloadSelected = viewModel::downloadSelected,
        onDownloadPending = viewModel::downloadPending,
        onRowOfflineAction = { entry ->
            if (entry.offline is OfflineState.Downloading) {
                viewModel.cancelDownload(entry.postId)
            } else {
                viewModel.download(entry.postId)
            }
        },
        onOfflineSettings = viewModel::updateOfflineSettings,
        onClearOffline = viewModel::clearOffline,
        modifier = modifier,
    )
}

/**
 * 收藏, as its own screen (board i1).
 *
 * Three screens' worth of behaviour in one composable, because they are one screen: the plain list,
 * the same list with a selection on it, and the 离线管理 sheet over it. What changes between the first
 * two is the top bar, the leading edge of each row and what floats at the bottom; the list itself is
 * the same list, which is why exiting multi-select does not cost a scroll position.
 *
 * Everything offline is behind [BookmarksUiState.offlineAvailable], which is what a build without a
 * download engine behind it reads as — see `OfflineLibrary`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    state: BookmarksUiState,
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge on the site's own page, then comes back here. */
    onVerify: () -> Unit,
    onFilter: (BookmarkFilter) -> Unit,
    onSort: (BookmarkSort) -> Unit,
    onSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onStartSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: ((removed: List<BookmarkEntry>, failed: SiteError?) -> Unit) -> Unit,
    onRestore: (List<BookmarkEntry>) -> Unit,
    onDownloadSelected: () -> Unit,
    onDownloadPending: () -> Unit,
    onRowOfflineAction: (BookmarkEntry) -> Unit,
    onOfflineSettings: (OfflineSettings) -> Unit,
    onClearOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var managing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val undoLabel = stringResource(Res.string.history_undo)

    // 移出收藏 goes through without a confirmation, per the board, so the way back is a Snackbar — and
    // a refusal has to reach the reader too, since the rows disappeared optimistically. Both land in
    // state rather than being shown from the callback: the callback runs on the view model's scope,
    // where `stringResource` cannot be reached, and the message needs one.
    var removed by remember { mutableStateOf<List<BookmarkEntry>?>(null) }
    var removeFailure by remember { mutableStateOf<SiteError?>(null) }
    val removeSelected = {
        onRemoveSelected { entries, failure ->
            if (failure != null) removeFailure = failure else removed = entries
        }
    }

    removed?.let { entries ->
        val message = stringResource(Res.string.bookmarks_removed, entries.size)
        LaunchedEffect(entries) {
            val outcome =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (outcome == SnackbarResult.ActionPerformed) onRestore(entries)
            removed = null
        }
    }

    // A refused 移出收藏 carries the button that unblocks it, not just the reason: the rows already
    // disappeared optimistically, so a reader told 需要确认一下你不是机器人 and given nothing to press
    // is looking at a list that is wrong with no way to make it right.
    removeFailure?.let { error ->
        val message = stringResource(Res.string.bookmarks_remove_failed, error.shortMessage())
        val recovery =
            siteErrorRecovery(
                error = error,
                onVerify = onVerify,
                onSignIn = onSignIn,
                onRetry = removeSelected,
            )
        LaunchedEffect(error) {
            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = recovery?.label,
                    duration = snackbarDuration(error),
                )
            removeFailure = null
            if (result == SnackbarResult.ActionPerformed) recovery?.onClick?.invoke()
        }
    }

    // Back exits multi-select before it leaves the screen: the selection is a mode, and a mode the
    // system back button cannot cancel is a mode people get stuck in.
    PlazaBackHandler(enabled = state.inSelection, onBack = onClearSelection)
    PlazaBackHandler(
        enabled = !state.inSelection && state.isSearching,
        onBack = { onSearching(false) },
    )

    val appBarState = rememberOneHandAppBarState()
    // The pullable bar is the plain state's alone. Multi-select and 搜索 are modes with a job in
    // hand — one has a toolbar of its own, the other has the keyboard up and wants every row it can
    // get — and both keep the ordinary 64dp bar. The connection is attached on the same condition,
    // because a bar that is not on screen must not go on eating the list's first 200dp of scroll.
    val oneHanded = !state.inSelection && !state.isSearching
    Scaffold(
        modifier = modifier.then(if (oneHanded) Modifier.nestedScroll(appBarState.nestedScrollConnection) else Modifier),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.inSelection) {
                SelectionTopBar(
                    count = state.selection?.size ?: 0,
                    allSelected = state.allVisibleSelected,
                    onClose = onClearSelection,
                    onSelectAll = onToggleSelectAll,
                )
            } else {
                BookmarksTopBar(
                    state = state,
                    appBarState = appBarState,
                    onBack = onBack,
                    onSearching = onSearching,
                    onQuery = onQuery,
                    onManage = { managing = true },
                )
            }
        },
        floatingActionButton = {
            if (!state.inSelection && state.offlineAvailable && state.pendingDownloadCount > 0) {
                // The content overload rather than the `icon`/`text` one: that overload wraps its
                // label in an animation container that does not surface the text to semantics, so
                // the pill announced itself as an unnamed button.
                ExtendedFloatingActionButton(
                    onClick = onDownloadPending,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(PlazaIcons.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = stringResource(Res.string.bookmarks_download_all, state.pendingDownloadCount),
                        style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (!state.inSelection) {
                    BookmarkFilterRow(
                        state = state,
                        onFilter = onFilter,
                        sortMenu = { SortMenu(current = state.sort, onSort = onSort) },
                    )
                    // Not while a selection is up: that mode has its own toolbar and its own bar, and
                    // the strip is about a list the reader is reading rather than one they are acting on.
                    state.error?.takeIf { state.isStale }?.let { error ->
                        BookmarkStaleBanner(
                            error = error,
                            onRetry = onRetry,
                            onSignIn = onSignIn,
                            onVerify = onVerify,
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    BookmarkList(
                        state = state,
                        onPostClick = onPostClick,
                        onOpenBrowser = onOpenBrowser,
                        onRetry = onRetry,
                        onStartSelection = { postId ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartSelection(postId)
                        },
                        onToggleSelection = onToggleSelection,
                        onRowOfflineAction = onRowOfflineAction,
                        onSignIn = onSignIn,
                        onVerify = onVerify,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The whole point of reading the list off disk is that the walk behind it is no
                    // longer something to wait for — so it gets a hairline at the top of the rows
                    // instead of the screen. Overlaid rather than laid out, because a strip that
                    // appears and disappears on every load would shove the list 4dp each way.
                    if (state.isSyncing && state.entries.isNotEmpty()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).describedAsLoading())
                    }
                }
            }
            if (state.inSelection) {
                SelectionToolbar(
                    selectedCount = state.selection?.size ?: 0,
                    alreadyOfflineCount =
                    state.selected.count {
                        it.offline is OfflineState.Downloaded || it.offline is OfflineState.Stale
                    },
                    estimateBytes = state.selectionEstimateBytes,
                    offlineAvailable = state.offlineAvailable,
                    onDownload = onDownloadSelected,
                    onRemove = removeSelected,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (managing) {
        OfflineManageSheet(
            usage = state.usage,
            settings = state.offlineSettings,
            onSettingsChange = onOfflineSettings,
            onClear = onClearOffline,
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun BookmarkList(
    state: BookmarksUiState,
    onPostClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    onRetry: () -> Unit,
    onStartSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onRowOfflineAction: (BookmarkEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state.visible
    when {
        state.isLoading -> LoadingState(modifier)

        state.error != null && state.entries.isEmpty() ->
            SiteErrorState(
                error = state.error,
                onRetry = onRetry,
                onOpenBrowser = { onOpenBrowser(NodeSeekSite.BASE_URL) },
                onSignIn = onSignIn,
                onVerify = onVerify,
                modifier = modifier,
            )

        visible.isEmpty() ->
            StatusView(
                icon = PlazaIcons.Bookmark,
                shape = StatusShapes.Empty,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                title = stringResource(state.emptyTitleRes),
                description = stringResource(state.emptyBodyRes),
                modifier = modifier,
            )

        else ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                // Room under the last row for whichever thing is floating over it. Both are about
                // the same height, so one number covers the list in either mode.
                contentPadding = PaddingValues(bottom = FLOATING_CLEARANCE),
            ) {
                if (state.truncated) {
                    item(key = "truncated") {
                        Text(
                            text =
                            stringResource(Res.string.bookmarks_truncated, BookmarksViewModel.MAX_PAGES),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        )
                    }
                }
                items(visible, key = { it.postId }) { entry ->
                    BookmarkRow(
                        entry = entry,
                        offlineAvailable = state.offlineAvailable,
                        selected = state.selection?.contains(entry.postId),
                        onClick = {
                            if (state.inSelection) onToggleSelection(entry.postId) else onPostClick(entry.postId)
                        },
                        onLongClick = {
                            if (state.inSelection) onToggleSelection(entry.postId) else onStartSelection(entry.postId)
                        },
                        onOfflineAction = { onRowOfflineAction(entry) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
    }
}

/** 64dp of toolbar plus its 16dp inset, rounded up so the last row clears the FAB too. */
private val FLOATING_CLEARANCE = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksTopBar(
    state: BookmarksUiState,
    appBarState: OneHandAppBarState,
    onBack: () -> Unit,
    onSearching: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onManage: () -> Unit,
) {
    val back =
        @Composable {
            IconButton(onClick = { if (state.isSearching) onSearching(false) else onBack() }) {
                Icon(
                    imageVector =
                    if (state.isSearching) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription =
                    stringResource(if (state.isSearching) Res.string.action_close else Res.string.action_back),
                )
            }
        }
    if (state.isSearching) {
        // A plain bar for as long as the box is open: [OneHandTopAppBar] takes a title string rather
        // than a slot, and a search field belongs in a bar that is not also a pull target.
        TopAppBar(
            title = { BookmarkSearchField(query = state.query.orEmpty(), onQuery = onQuery) },
            navigationIcon = back,
        )
        return
    }
    OneHandTopAppBar(
        title = stringResource(Res.string.bookmarks_title),
        state = appBarState,
        // 已离线 N 篇 · 占用 X. It used to be a strip of its own between the chips and the list;
        // as a subtitle it costs no row at all, and this is a standing fact about the screen rather
        // than a control — which is what a subtitle is for. Null until something has been
        // downloaded: 「已离线 0 篇 · 占用 0 B」 is a line that says nothing and still takes a line.
        subtitle =
        if (state.offlineAvailable && state.usage.posts > 0) {
            stringResource(Res.string.offline_status, state.usage.posts, formatBytes(state.usage.totalBytes))
        } else {
            null
        },
        navigationIcon = back,
        actions = {
            IconButton(onClick = { onSearching(true) }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(Res.string.bookmarks_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.offlineAvailable) {
                // Straight to 离线管理 rather than a ⋮ that opens onto a menu of one. The overflow
                // was there when the board put 排序 in it too; 排序 has its own control beside the
                // filter chips, so what was left was a tap to reveal a single item.
                IconButton(onClick = onManage) {
                    Icon(
                        imageVector = PlazaIcons.CloudDone,
                        contentDescription = stringResource(Res.string.offline_manage),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/**
 * 搜索, over the list already in memory.
 *
 * In the bar rather than as its own screen because there is nothing to fetch: the whole collection
 * is loaded, so the results are the list narrowing under the box as it is typed, and pushing that
 * onto a second destination would put a navigation step in front of an instant answer.
 */
@Composable
private fun BookmarkSearchField(
    query: String,
    onQuery: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.bookmarks_search_hint)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.bookmarks_search_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors =
        TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** ⇅ — three orderings of a list that is entirely in memory. See [BookmarkSort]. */
@Composable
private fun SortMenu(
    current: BookmarkSort,
    onSort: (BookmarkSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(Sizes.minTouchTarget)) {
            Icon(
                imageVector = PlazaIcons.SwapVert,
                contentDescription = stringResource(Res.string.action_sort),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookmarkSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = {
                        expanded = false
                        onSort(sort)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.bookmarks_selected, count),
                style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.bookmarks_exit_selection),
                )
            }
        },
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(
                    // The control is one button doing two things, so it says which one it is about
                    // to do — 全选 on a bar that already says 已选 6 项 out of 6 is a button with no
                    // visible effect.
                    text =
                    stringResource(
                        if (allSelected) Res.string.bookmarks_select_none else Res.string.bookmarks_select_all,
                    ),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        },
        // Tinted rather than the surface the plain bar sits on: the mode has to be visible from the
        // top of the screen, not only from the checkboxes halfway down it.
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

private val BookmarkSort.labelRes: StringResource
    get() =
        when (this) {
            BookmarkSort.SITE -> Res.string.bookmarks_sort_site
            BookmarkSort.REPLIES -> Res.string.bookmarks_sort_replies
            BookmarkSort.PENDING_FIRST -> Res.string.bookmarks_sort_pending
        }

private val BookmarksUiState.emptyTitleRes: StringResource
    get() =
        when {
            isSearching -> Res.string.bookmarks_empty_search_title
            filter != BookmarkFilter.ALL -> Res.string.bookmarks_empty_filter_title
            else -> Res.string.bookmarks_empty_title
        }

private val BookmarksUiState.emptyBodyRes: StringResource
    get() =
        when {
            isSearching -> Res.string.bookmarks_empty_search_body
            filter != BookmarkFilter.ALL -> Res.string.bookmarks_empty_filter_body
            else -> Res.string.bookmarks_empty_body
        }

// -------------------------------------------------------------------------------------------------
// Previews
//
// The three boards of i1, in both themes. They pass `offlineAvailable = true` and a hand-built set of
// download states because the shipped `OfflineLibrary` reports neither — these are the only place the
// five states can be seen side by side until the engine lands, which is exactly what they are for.
// -------------------------------------------------------------------------------------------------

private fun previewEntries() =
    listOf(
        BookmarkEntry(
            postId = 1,
            title = "iLatency公测，一个新的社区内Ping站，邀你共建",
            categoryTitle = "Dev",
            categorySlug = "dev",
            authorName = "酒神",
            commentCount = 340,
            createdAtText = "28分钟前",
            offline = OfflineState.Downloaded(bytes = 2_936_012),
        ),
        BookmarkEntry(
            postId = 2,
            title = "小鸡救砖与流媒体解锁思路合集（长期更新）",
            categoryTitle = "技术",
            categorySlug = "tech",
            authorName = "测速菌",
            commentCount = 128,
            createdAtText = "昨天",
            offline = OfflineState.Downloading(progress = 0.62f),
        ),
        BookmarkEntry(
            postId = 3,
            title = "移动说回馈老用户，十一年宽带合约，一次性交3年宽带费",
            categoryTitle = "日常",
            categorySlug = "daily",
            authorName = "宝宝困困",
            commentCount = 53,
            createdAtText = "上周",
            offline = OfflineState.Stale(behindReplies = 3, bytes = 1_782_579),
        ),
        BookmarkEntry(
            postId = 4,
            title = "黑五预热：各家年付小鸡汇总帖",
            categoryTitle = "交易",
            categorySlug = "trade",
            authorName = "demain",
            commentCount = 76,
            createdAtText = "3天前",
            offline = OfflineState.NotDownloaded,
        ),
        BookmarkEntry(
            postId = 5,
            title = "Debian 13 上用 nftables 做端口转发的坑",
            categoryTitle = "技术",
            categorySlug = "tech",
            authorName = "nssk",
            commentCount = 41,
            createdAtText = "上周",
            offline = OfflineState.Failed(OfflineFailure.OutOfSpace),
        ),
        BookmarkEntry(
            postId = 6,
            title = "求一个批量测延迟的脚本，最好能出 markdown 表格",
            categoryTitle = "技术",
            categorySlug = "tech",
            authorName = "咕咕咕",
            commentCount = 17,
            createdAtText = "上周",
            offline = OfflineState.NotDownloaded,
        ),
    )

private fun previewState(selection: Set<Long>? = null) =
    BookmarksUiState(
        entries = previewEntries(),
        isSyncing = false,
        selection = selection,
        selectionEstimateBytes = if (selection == null) null else 4_823_449,
        offlineAvailable = true,
        usage =
        OfflineUsage(
            posts = 5,
            textBytes = 2_202_009,
            imageBytes = 10_800_332,
            freeBytes = 3_435_973_836,
        ),
    )

@Composable
private fun BookmarksPreviewHost(
    darkTheme: Boolean,
    state: BookmarksUiState,
) {
    PlazaTheme(darkTheme = darkTheme) {
        BookmarksScreen(
            state = state,
            onBack = {},
            onPostClick = {},
            onOpenBrowser = {},
            onRetry = {},
            onSignIn = {},
            onVerify = {},
            onFilter = {},
            onSort = {},
            onSearching = {},
            onQuery = {},
            onStartSelection = {},
            onToggleSelection = {},
            onToggleSelectAll = {},
            onClearSelection = {},
            onRemoveSelected = {},
            onRestore = {},
            onDownloadSelected = {},
            onDownloadPending = {},
            onRowOfflineAction = {},
            onOfflineSettings = {},
            onClearOffline = {},
        )
    }
}

@Preview(name = "i1 收藏", widthDp = 360, heightDp = 800)
@Composable
private fun BookmarksPreview() = BookmarksPreviewHost(darkTheme = false, state = previewState())

@Preview(name = "i1 收藏 · Dark", widthDp = 360, heightDp = 800)
@Composable
private fun BookmarksDarkPreview() = BookmarksPreviewHost(darkTheme = true, state = previewState())

@Preview(name = "i1 收藏 · 多选", widthDp = 360, heightDp = 800)
@Composable
private fun BookmarksSelectionPreview() =
    BookmarksPreviewHost(darkTheme = false, state = previewState(selection = setOf(1L, 3L, 4L)))

@Preview(name = "i1 收藏 · 多选 · Dark", widthDp = 360, heightDp = 800)
@Composable
private fun BookmarksSelectionDarkPreview() =
    BookmarksPreviewHost(darkTheme = true, state = previewState(selection = setOf(1L, 3L, 4L)))

/** The library-unavailable form: the list, its filter and multi-select, and nothing about offline. */
@Preview(name = "i1 收藏 · 无离线引擎", widthDp = 360, heightDp = 800)
@Composable
private fun BookmarksWithoutOfflinePreview() =
    BookmarksPreviewHost(
        darkTheme = false,
        state =
        previewState().copy(
            offlineAvailable = false,
            entries = previewEntries().map { it.copy(offline = OfflineState.NotDownloaded) },
        ),
    )
