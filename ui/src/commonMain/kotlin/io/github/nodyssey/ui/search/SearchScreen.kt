package io.github.nodyssey.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.common.CollapsingHeader
import io.github.nodyssey.ui.common.NavigationBarScrollConnection
import io.github.nodyssey.ui.common.NavigationDirectionThreshold
import io.github.nodyssey.ui.common.NoSearchResultsState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.postlist.FeedRowPlaceholder
import io.github.nodyssey.ui.postlist.PostRow
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_sort
import io.github.nodyssey.ui.resources.search_all_boards
import io.github.nodyssey.ui.resources.search_apply_board
import io.github.nodyssey.ui.resources.search_board_all_boards_heading
import io.github.nodyssey.ui.resources.search_board_chip_all
import io.github.nodyssey.ui.resources.search_board_range
import io.github.nodyssey.ui.resources.search_board_range_hint
import io.github.nodyssey.ui.resources.search_board_section
import io.github.nodyssey.ui.resources.search_board_single_hint
import io.github.nodyssey.ui.resources.search_clear_all
import io.github.nodyssey.ui.resources.search_clear_query
import io.github.nodyssey.ui.resources.search_hint
import io.github.nodyssey.ui.resources.search_hint_board
import io.github.nodyssey.ui.resources.search_history_board_scope
import io.github.nodyssey.ui.resources.search_history_empty
import io.github.nodyssey.ui.resources.search_history_scope
import io.github.nodyssey.ui.resources.search_load_more_failed
import io.github.nodyssey.ui.resources.search_posts_tab
import io.github.nodyssey.ui.resources.search_recent
import io.github.nodyssey.ui.resources.search_recent_boards
import io.github.nodyssey.ui.resources.search_remove_recent
import io.github.nodyssey.ui.resources.search_submit
import io.github.nodyssey.ui.resources.search_user_hint
import io.github.nodyssey.ui.resources.search_user_history_scope
import io.github.nodyssey.ui.resources.search_users_tab
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import io.github.plaza.designsys.component.ChoiceRow
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.listAvatarSize
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val postResults = viewModel.postResults.collectAsLazyPagingItems()
    SearchScreen(
        state = state,
        postResults = postResults,
        queryState = viewModel.query,
        onSearch = viewModel::submitSearch,
        onTargetChange = viewModel::selectTarget,
        onHistoryClick = viewModel::selectHistory,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        onBoardChange = viewModel::selectBoard,
        onSortChange = viewModel::selectSort,
        onPostClick = onPostClick,
        onUserClick = onUserClick,
        onRetry = viewModel::retryUsers,
        onSignIn = onSignIn,
        onVerify = { onVerify(viewModel.challengeUrl()) },
        modifier = modifier,
        onNavigationBarHiddenChanged = onNavigationBarHiddenChanged,
    )
}

/**
 * Search, in the order the site itself asks for it: say what kind of thing, say where, then type.
 *
 * The input is pinned to the top and the rest of the screen is the search view — tabs, the board the
 * search is scoped to, and either the history or the results. It used to be a collapsing
 * [androidx.compose.material3.AppBarWithSearch] over an
 * [androidx.compose.material3.ExpandedFullScreenSearchBar], which meant the history existed twice
 * (once in the dialog, once behind it) and the board could only be picked from the results screen —
 * after a search had already gone out with the wrong scope.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    queryState: TextFieldState,
    onSearch: () -> Unit,
    onTargetChange: (SearchTarget) -> Unit,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
    postResults: LazyPagingItems<FeedPost>? = null,
    onBoardChange: (String?) -> Unit = {},
    onSortChange: (FeedSort) -> Unit = {},
    /** Keeps the host navigation bar hidden until the user deliberately scrolls back up. */
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
) {
    var showBoardSheet by remember { mutableStateOf(false) }

    /*
     * The chrome above the results folds away as they scroll, which on a phone is the difference
     * between five results and seven: the field, the tabs and the scope row cost ~170dp of an 800dp
     * screen and none of them is being read while the reader is going down a list.
     *
     * The same `enterAlways` the home feed's title bar uses, so a short drag back up returns the lot.
     */
    val headerScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // The bar at the bottom answers the same gesture, through the same connection the feed uses, so
    // the two screens hide it on the same 16dp of committed direction.
    val directionThresholdPx = with(LocalDensity.current) { NavigationDirectionThreshold.toPx() }
    val currentOnNavigationBarHiddenChanged by rememberUpdatedState(onNavigationBarHiddenChanged)
    val navigationBarScrollConnection =
        remember(directionThresholdPx) {
            // Called through the updated-state holder rather than handed the callback directly: the
            // connection outlives the recompositions that hand this screen a fresh lambda.
            NavigationBarScrollConnection(directionThresholdPx) { hidden ->
                currentOnNavigationBarHiddenChanged(hidden)
            }
        }
    // The host must not stay stuck bar-less if this screen leaves the composition.
    DisposableEffect(Unit) { onDispose { currentOnNavigationBarHiddenChanged(false) } }

    /*
     * Anything that replaces the list under the header also unfolds it, and brings the bar back.
     *
     * Submitting, switching tab, and re-scoping all throw the rows away, so the scroll they were
     * folded by is gone too. Clearing the query matters most: the setup screen underneath may have
     * nothing scrollable on it at all, and a header left folded there would take the search field off
     * the screen with no gesture left that could bring it back.
     */
    LaunchedEffect(state.submittedQuery, state.target, state.selectedBoard, state.sort) {
        headerScrollBehavior.state.heightOffset = 0f
        headerScrollBehavior.state.contentOffset = 0f
        navigationBarScrollConnection.reveal()
    }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            CollapsingHeader(headerScrollBehavior) {
                SearchInputField(
                    queryState = queryState,
                    placeholder = searchPlaceholder(state),
                    // Arriving with nothing searched yet means arriving to type; arriving back on a
                    // result list does not, and a keyboard over the results would only hide them.
                    autoFocus = state.submittedQuery == null,
                    onSearch = onSearch,
                )

                PrimaryTabRow(selectedTabIndex = state.target.ordinal) {
                    SearchTab(
                        selected = state.target == SearchTarget.POSTS,
                        title = stringResource(Res.string.search_posts_tab),
                        // No count: `/search` never returns a total, and the number of rows loaded so
                        // far is not one — it grows as you scroll, which reads as the site changing.
                        count = null,
                        onClick = { onTargetChange(SearchTarget.POSTS) },
                    )
                    SearchTab(
                        selected = state.target == SearchTarget.USERS,
                        title = stringResource(Res.string.search_users_tab),
                        count = state.userResults.size.takeIf { state.userLoadState == SearchLoadState.Success },
                        onClick = { onTargetChange(SearchTarget.USERS) },
                    )
                }

                // Part of the header rather than of the results, because it is chrome for them: the
                // board and the order the list on screen was fetched with.
                if (state.submittedQuery != null && state.target == SearchTarget.POSTS) {
                    ResultScopeRow(
                        state = state,
                        onOpenBoardSheet = { showBoardSheet = true },
                        onSortChange = onSortChange,
                    )
                }
            }

            if (state.submittedQuery == null) {
                // No connection here on purpose. The setup screen is where a query is written, and
                // the field it is written in must not be scrollable off the top by the history list
                // sitting under it.
                SearchSetup(
                    state = state,
                    onBoardChange = onBoardChange,
                    onHistoryClick = onHistoryClick,
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                )
            } else {
                // On the wrapper rather than on each list: posts and users are two different
                // composables with a list each, and nested scroll reaches this from either.
                //
                // The direction detector goes outside the header's connection for the same reason it
                // does on the feed — nested inside, the header would eat the first ~180dp of every
                // downward scroll and the bar below would only start hiding once it had finished.
                Box(
                    Modifier
                        .nestedScroll(navigationBarScrollConnection)
                        .nestedScroll(headerScrollBehavior.nestedScrollConnection),
                ) {
                    SearchResults(
                        state = state,
                        queryState = queryState,
                        onPostClick = onPostClick,
                        onUserClick = onUserClick,
                        postResults = postResults,
                        onRetry = onRetry,
                        onSignIn = onSignIn,
                        onVerify = onVerify,
                    )
                }
            }
        }
    }

    if (showBoardSheet) {
        BoardRangeSheet(
            boards = state.boards,
            selected = state.selectedBoard,
            recentBoards = state.recentBoards,
            onDismiss = { showBoardSheet = false },
            onApply = {
                onBoardChange(it)
                showBoardSheet = false
            },
        )
    }
}

/**
 * The search box, always editable and always at the top.
 *
 * [SearchBarDefaults.InputField] is used on its own rather than inside a
 * [androidx.compose.material3.SearchBar]: the collapsed search bar wraps its field in
 * `DisableSoftKeyboard`, because in that pattern typing happens in the expanded copy drawn over it.
 * This screen has no collapsed state to speak of — it is a destination whose whole body is the
 * search view — so the field has to be the one that takes the keyboard. The [SearchBarValue.Expanded]
 * state passed to it is the same statement: what is below the field is the expanded view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputField(
    queryState: TextFieldState,
    placeholder: String,
    autoFocus: Boolean,
    onSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val submit = {
        onSearch()
        // The results are what was asked for; the keyboard would cover four of them.
        focusManager.clearFocus()
    }

    SearchBarDefaults.InputField(
        textFieldState = queryState,
        searchBarState = searchBarState,
        onSearch = { submit() },
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .focusRequester(focusRequester),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // The field's own container is transparent by default because a search bar paints it from
        // the outside. Standing on its own, it has to paint itself — same token the bar would use.
        colors =
        SearchBarDefaults.inputFieldColors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (queryState.text.isNotEmpty()) {
                    IconButton(onClick = { queryState.clearText() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.search_clear_query))
                    }
                }
                FilledIconButton(onClick = submit) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search_submit))
                }
            }
        },
    )

    LaunchedEffect(Unit) {
        if (autoFocus) focusRequester.requestFocus()
    }
}

/** Placeholder as the third step of the sentence the two pickers above it have already started. */
@Composable
private fun searchPlaceholder(state: SearchUiState): String {
    if (state.target == SearchTarget.USERS) return stringResource(Res.string.search_user_hint)
    val board = state.selectedBoard ?: return stringResource(Res.string.search_hint)
    return stringResource(Res.string.search_hint_board, state.boards.title(board))
}

private fun List<Board>.title(slug: String): String = firstOrNull { it.slug == slug }?.title ?: slug

@Composable
private fun SearchTab(
    selected: Boolean,
    title: String,
    count: Int?,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(if (count == null) title else "$title · $count") },
    )
}

/**
 * Everything between opening search and submitting one: what to scope it to, and what was searched
 * before. The board picker is a permanent chip group rather than the sheet the results screen opens,
 * because here it is part of writing the query — the placeholder finishes the sentence it starts.
 */
@Composable
private fun ColumnScope.SearchSetup(
    state: SearchUiState,
    onBoardChange: (String?) -> Unit,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    // The users tab has no scope to pick — the site matches a name fragment and returns the lot —
    // so it goes straight to the history, with the tab row's own line as its separator.
    if (state.target == SearchTarget.POSTS) {
        BoardChips(
            boards = state.boards,
            selected = state.selectedBoard,
            onSelect = onBoardChange,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg))
    } else {
        Spacer(Modifier.size(Spacing.md))
    }
    SearchHistory(
        searches = state.searchHistory.filter { it.target == state.target },
        boards = state.boards,
        onHistoryClick = onHistoryClick,
        onRemoveHistory = onRemoveHistory,
        onClearHistory = onClearHistory,
    )
}

/**
 * One board, or the whole site.
 *
 * Single choice because `/search` takes a single `category` and applies it itself — the same reason
 * [BoardRangeSheet] is a radio list. 全部 is a real option, not an empty selection: it is what the
 * site does when the parameter is absent.
 */
@Composable
private fun BoardChips(
    boards: List<Board>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.search_board_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(Res.string.search_board_single_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BoardChip(
            title = stringResource(Res.string.search_board_chip_all),
            selected = selected == null,
            onSelect = { onSelect(null) },
        )
        boards.forEach { board ->
            BoardChip(
                title = board.title,
                selected = board.slug != null && board.slug == selected,
                onSelect = { onSelect(board.slug) },
            )
        }
    }
}

/**
 * No leading tick, deliberately.
 *
 * A tick makes the selected chip ~26dp wider, and in a wrapping group that re-flows every chip after
 * it: on a narrow phone picking a board moved the rest of the list between three and four rows,
 * under the finger that was still choosing. The filled container against the outlined ones already
 * says which one is on, TalkBack reads 已选中 from [FilterChip]'s own semantics either way, and the
 * tick is optional decoration in Material's own spec — so it is the part that goes.
 */
@Composable
private fun BoardChip(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(title) },
    )
}

/**
 * Board and order on the results screen, where changing either re-runs the search that is showing.
 *
 * Both are server parameters, not local filtering: `category` picks the one board `/search` accepts,
 * and `sortBy` is the boards' own 新评论 / 新帖子 — which is why the order reads the same here as it
 * does on the home feed.
 */
@Composable
private fun ResultScopeRow(
    state: SearchUiState,
    onOpenBoardSheet: () -> Unit,
    onSortChange: (FeedSort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selected when the search is scoped to one board, so the row says at a glance whether the
        // results are the whole site or a corner of it.
        FilterChip(
            selected = state.selectedBoard != null,
            onClick = onOpenBoardSheet,
            label = {
                Text(
                    state.selectedBoard
                        ?.let { slug -> state.boards.title(slug) }
                        ?: stringResource(Res.string.search_all_boards),
                )
            },
            leadingIcon =
            if (state.selectedBoard != null) {
                {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else {
                null
            },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Icon(
                    PlazaIcons.SwapVert,
                    contentDescription = stringResource(Res.string.action_sort),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(state.sort.labelRes()))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                SortMenuItem(FeedSort.LAST_REPLY, state.sort, onSortChange) { showSortMenu = false }
                SortMenuItem(FeedSort.POST_TIME, state.sort, onSortChange) { showSortMenu = false }
            }
        }
    }
}
private fun FeedSort.labelRes(): StringResource =
    if (this == FeedSort.POST_TIME) Res.string.sort_by_post_time else Res.string.sort_by_reply_time

@Composable
private fun SearchResults(
    state: SearchUiState,
    queryState: TextFieldState,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    postResults: LazyPagingItems<FeedPost>?,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
) {
    if (state.target == SearchTarget.USERS) {
        when (val loadState = state.userLoadState) {
            SearchLoadState.Idle,
            SearchLoadState.Loading,
            -> LoadingState()

            is SearchLoadState.Error ->
                SiteErrorState(
                    error = loadState.error,
                    onRetry = onRetry,
                    onOpenBrowser = onVerify,
                    onSignIn = onSignIn,
                    onVerify = onVerify,
                )

            SearchLoadState.Success -> {
                if (state.userResults.isEmpty()) {
                    Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { queryState.clearText() }) }
                } else {
                    UserResults(users = state.userResults, onUserClick = onUserClick)
                }
            }
        }
        return
    }

    val posts = postResults ?: return LoadingState()
    when (val refresh = posts.loadState.refresh) {
        LoadState.Loading -> LoadingState()

        is LoadState.Error ->
            SiteErrorState(
                error = refresh.error.toSiteError(),
                onRetry = posts::retry,
                onOpenBrowser = onVerify,
                onSignIn = onSignIn,
                onVerify = onVerify,
            )

        is LoadState.NotLoading -> {
            if (posts.itemCount == 0) {
                Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { queryState.clearText() }) }
            } else {
                PostResults(
                    posts = posts,
                    highlight = state.submittedQuery,
                    onPostClick = onPostClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostResults(
    posts: LazyPagingItems<FeedPost>,
    highlight: String?,
    onPostClick: (Long) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = posts.loadState.refresh is LoadState.Loading,
        onRefresh = posts::refresh,
    ) {
        LazyColumn {
            items(
                count = posts.itemCount,
                key = posts.itemKey { it.summary.postId },
            ) { index ->
                // Same pager, same reason as the feed: a counted-but-unloaded row keeps its space.
                when (val post = posts[index]) {
                    null -> FeedRowPlaceholder()

                    else -> {
                        PostRow(
                            post = post,
                            onClick = { onPostClick(post.summary.postId) },
                            highlight = highlight,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            appendRow(posts)
        }
    }
}

/** The tail of the result list: the next page arriving, or the one that did not. */
private fun LazyListScope.appendRow(posts: LazyPagingItems<FeedPost>) {
    when (posts.loadState.append) {
        LoadState.Loading ->
            item("appending") {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }

        is LoadState.Error ->
            item("append-failed") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.search_load_more_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = posts::retry) { Text(stringResource(Res.string.action_retry)) }
                }
            }

        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun SortMenuItem(
    sort: FeedSort,
    selectedSort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
    closeMenu: () -> Unit,
) {
    val isCurrent = sort == selectedSort
    DropdownMenuItem(
        text = { Text(stringResource(sort.labelRes())) },
        // Same reason as the home feed's menu: the tick is decoration, `selected` is what TalkBack
        // reads out as 已选中.
        modifier = Modifier.semantics { selected = isCurrent },
        trailingIcon = {
            if (isCurrent) Icon(Icons.Default.Check, contentDescription = null)
        },
        onClick = {
            closeMenu()
            onSortChange(sort)
        },
    )
}

/**
 * Past searches, each one a whole search rather than a word.
 *
 * The second line is the scope it ran with, so the same word searched in two boards is two rows and
 * tapping either re-runs exactly what it says.
 */
@Composable
private fun SearchHistory(
    searches: List<SearchHistoryEntry>,
    boards: List<Board>,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.search_recent),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClearHistory) { Text(stringResource(Res.string.search_clear_all)) }
    }
    if (searches.isEmpty()) {
        Text(
            stringResource(Res.string.search_history_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
    } else {
        LazyColumn {
            items(searches, key = SearchHistoryEntry::key) { recent ->
                ListItem(
                    headlineContent = { Text(recent.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(historyScope(recent, boards)) },
                    leadingContent = {
                        Icon(
                            if (recent.target == SearchTarget.POSTS) {
                                PlazaIcons.History
                            } else {
                                PlazaIcons.PersonSearch
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemoveHistory(recent) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(Res.string.search_remove_recent, recent.query),
                            )
                        }
                    },
                    modifier = Modifier.clickable { onHistoryClick(recent) },
                )
            }
        }
    }
}

@Composable
private fun historyScope(
    entry: SearchHistoryEntry,
    boards: List<Board>,
): String {
    if (entry.target == SearchTarget.USERS) return stringResource(Res.string.search_user_history_scope)
    val slug = entry.categorySlug ?: return stringResource(Res.string.search_history_scope)
    return stringResource(Res.string.search_history_board_scope, boards.title(slug))
}

@Composable
private fun UserResults(
    users: List<UserSearchResult>,
    onUserClick: (Long) -> Unit,
) {
    LazyColumn {
        items(users, key = UserSearchResult::uid) { user ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onUserClick(user.uid) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(url = user.avatarUrl, name = user.name, size = listAvatarSize())
                Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    Text(user.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        userDetail(user),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun userDetail(user: UserSearchResult): String =
    buildList {
        add("UID ${user.uid}")
        user.level?.let { add("Lv $it") }
        user.topicCount?.let { add("主题 $it") }
        user.commentCount?.let { add("评论 $it") }
        user.bio?.let { add(it) }
        if (user.bio == null) user.joinedText?.let { add("加入 $it") }
    }.joinToString(" · ")

/**
 * Picks the one board the search is scoped to, from the results screen.
 *
 * Single choice because `/search` takes a single `category` and applies it itself. The checkbox
 * version of this sheet could only pretend to filter by several: it searched the whole site and
 * dropped the rows that did not match, so a board with few hits made the app walk page after page
 * looking for something to show. 全部版块 is a real option here, not an empty selection — it is what
 * the site does when the parameter is absent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRangeSheet(
    boards: List<Board>,
    selected: String?,
    recentBoards: List<String>,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit,
) {
    var picked by remember(selected) { mutableStateOf(selected) }
    val recent = recentBoards.mapNotNull { slug -> boards.firstOrNull { it.slug == slug } }
    val remaining = boards.filterNot { board -> recent.any { it.slug == board.slug } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(Res.string.search_board_range), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.search_board_range_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The board list scrolls on its own so the apply row below stays reachable even on a
            // short window; the button is the whole point of opening the sheet.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ChoiceRow(
                    label = stringResource(Res.string.search_all_boards),
                    selected = picked == null,
                    onSelect = { picked = null },
                )
                if (recent.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.search_recent_boards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BoardRadioGrid(recent, picked) { picked = it }
                }
                Text(
                    stringResource(Res.string.search_board_all_boards_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoardRadioGrid(remaining, picked) { picked = it }
            }
            Button(onClick = { onApply(picked) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.search_apply_board))
            }
        }
    }
}

/** Two columns: fourteen boards in seven rows fit a phone screen without a scroll to the button. */
@Composable
private fun BoardRadioGrid(
    boards: List<Board>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    boards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { board ->
                ChoiceRow(
                    label = board.title,
                    selected = board.slug != null && board.slug == selected,
                    onSelect = { onSelect(board.slug) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SearchPreview() {
    PlazaTheme {
        SearchScreen(
            state =
            SearchUiState(
                boards =
                listOf(
                    Board("daily", "日常", null),
                    Board("tech", "技术", null),
                    Board("info", "情报", null),
                    Board("review", "测评", null),
                    Board("trade", "交易", null),
                    Board("carpool", "拼车", null),
                    Board("dev", "Dev", null),
                ),
                selectedBoard = "trade",
                searchHistory =
                listOf(
                    SearchHistoryEntry("腾讯云轻量", SearchTarget.POSTS, categorySlug = "trade"),
                    SearchHistoryEntry("腾讯云轻量", SearchTarget.POSTS),
                    SearchHistoryEntry("nodequality", SearchTarget.POSTS, categorySlug = "tech"),
                ),
            ),
            queryState = rememberTextFieldState(),
            onSearch = {},
            onTargetChange = {},
            onHistoryClick = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onPostClick = {},
            onUserClick = {},
            onRetry = {},
            onSignIn = {},
            onVerify = {},
        )
    }
}
