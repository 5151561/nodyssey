package io.github.nodyssey.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.R
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchSort
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.common.LoadingState
import io.github.nodyssey.ui.common.NoSearchResultsState
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.postlist.PostRow
import io.github.nodyssey.ui.postlist.toNodeSeekError
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onSortChange: (SearchSort) -> Unit = {},
) {
    var showBoardSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Expanded on arrival: you reached this screen in order to type, and the expanded bar is where
    // the history lives. It used to occupy the results area whenever nothing had been submitted,
    // which is the same thing said with a hand-written branch.
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = queryState,
            searchBarState = searchBarState,
            onSearch = {
                onSearch()
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = {
                Text(
                    stringResource(
                        if (state.target == SearchTarget.USERS) R.string.search_user_hint else R.string.search_hint,
                    ),
                )
            },
            // Expanded, the full-screen bar covers the navigation bar, so the leading slot has to be
            // the way out — a magnifier there is decoration on a screen with no other exit.
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (queryState.text.isNotEmpty()) {
                    IconButton(onClick = { queryState.clearText() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear_query))
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopSearchBar(state = searchBarState, inputField = inputField) },
    ) { padding ->
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            SearchHistory(
                searches = state.searchHistory.filter { it.target == state.target },
                boards = state.boards,
                onHistoryClick = {
                    onHistoryClick(it)
                    scope.launch { searchBarState.animateToCollapsed() }
                },
                onRemoveHistory = onRemoveHistory,
                onClearHistory = onClearHistory,
            )
        }
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            PrimaryTabRow(selectedTabIndex = state.target.ordinal) {
                SearchTab(
                    selected = state.target == SearchTarget.POSTS,
                    title = stringResource(R.string.search_posts_tab),
                    count =
                    postResults
                        ?.itemCount
                        ?.takeIf { postResults.loadState.refresh is LoadState.NotLoading },
                    onClick = { onTargetChange(SearchTarget.POSTS) },
                )
                SearchTab(
                    selected = state.target == SearchTarget.USERS,
                    title = stringResource(R.string.search_users_tab),
                    count = state.userResults.size.takeIf { state.userLoadState == SearchLoadState.Success },
                    onClick = { onTargetChange(SearchTarget.USERS) },
                )
            }

            if (state.target == SearchTarget.POSTS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { showBoardSheet = true },
                        label = {
                            Text(
                                state.selectedBoard
                                    ?.let { slug -> state.boards.firstOrNull { it.slug == slug }?.title ?: slug }
                                    ?: stringResource(R.string.search_all_boards),
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    )
                    if (state.submittedQuery != null) {
                        Box {
                            AssistChip(
                                onClick = { showSortMenu = true },
                                label = {
                                    Text(
                                        stringResource(
                                            if (state.sort == SearchSort.TIME) {
                                                R.string.search_sort_time
                                            } else {
                                                R.string.search_sort_relevance
                                            },
                                        ),
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                            )
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortMenuItem(SearchSort.RELEVANCE, state.sort, onSortChange) { showSortMenu = false }
                                SortMenuItem(SearchSort.TIME, state.sort, onSortChange) { showSortMenu = false }
                            }
                        }
                    }
                }
            }

            SearchContent(
                state = state,
                queryState = queryState,
                onHistoryClick = onHistoryClick,
                onRemoveHistory = onRemoveHistory,
                onClearHistory = onClearHistory,
                onPostClick = onPostClick,
                onUserClick = onUserClick,
                postResults = postResults,
                onRetry = onRetry,
                onSignIn = onSignIn,
                onVerify = onVerify,
            )
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

@Composable
private fun SearchContent(
    state: SearchUiState,
    queryState: TextFieldState,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    postResults: LazyPagingItems<FeedPost>?,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: () -> Unit,
) {
    // Collapsed with nothing submitted is a real state — the bar can be collapsed from the expanded
    // history, or the query cleared from a result list — and it used to show the history. Leaving it
    // blank was measured on device: an empty screen under an empty search box, with the history only
    // reachable by tapping the bar again.
    if (state.submittedQuery == null) {
        SearchHistory(
            searches = state.searchHistory.filter { it.target == state.target },
            boards = state.boards,
            onHistoryClick = onHistoryClick,
            onRemoveHistory = onRemoveHistory,
            onClearHistory = onClearHistory,
        )
        return
    }

    if (state.target == SearchTarget.USERS) {
        when (val loadState = state.userLoadState) {
            SearchLoadState.Idle,
            SearchLoadState.Loading,
            -> LoadingState()

            is SearchLoadState.Error ->
                NodeSeekErrorState(
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
            NodeSeekErrorState(
                error = refresh.error.toNodeSeekError(),
                onRetry = posts::retry,
                onOpenBrowser = onVerify,
                onSignIn = onSignIn,
                onVerify = onVerify,
            )

        is LoadState.NotLoading -> {
            if (posts.itemCount == 0) {
                Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { queryState.clearText() }) }
            } else {
                PostResults(posts = posts, onPostClick = onPostClick)
            }
        }
    }
}

@Composable
private fun PostResults(
    posts: LazyPagingItems<FeedPost>,
    onPostClick: (Long) -> Unit,
) {
    LazyColumn {
        items(
            count = posts.itemCount,
            key = posts.itemKey { it.summary.postId },
        ) { index ->
            posts[index]?.let { post ->
                PostRow(post = post, onClick = { onPostClick(post.summary.postId) })
            }
        }
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
                            stringResource(R.string.search_load_more_failed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = posts::retry) { Text(stringResource(R.string.action_retry)) }
                    }
                }

            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun SortMenuItem(
    sort: SearchSort,
    selectedSort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    closeMenu: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(
                    if (sort == SearchSort.TIME) R.string.search_sort_time else R.string.search_sort_relevance,
                ),
                fontWeight = if (sort == selectedSort) FontWeight.Bold else FontWeight.Normal,
            )
        },
        onClick = {
            closeMenu()
            onSortChange(sort)
        },
    )
}

@Composable
private fun SearchHistory(
    searches: List<SearchHistoryEntry>,
    boards: List<Board>,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onClearHistory) { Text(stringResource(R.string.search_clear_all)) }
    }
    if (searches.isEmpty()) {
        Text(
            stringResource(R.string.search_history_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg),
        )
    } else {
        LazyColumn {
            items(searches, key = SearchHistoryEntry::key) { recent ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onHistoryClick(recent) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (recent.target == SearchTarget.POSTS) NodysseyIcons.History else NodysseyIcons.PersonSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                        Text(recent.query)
                        Text(
                            historyScope(recent, boards),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemoveHistory(recent) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_remove_recent, recent.query),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun historyScope(
    entry: SearchHistoryEntry,
    boards: List<Board>,
): String {
    if (entry.target == SearchTarget.USERS) return stringResource(R.string.search_user_history_scope)
    val slug = entry.categorySlug ?: return stringResource(R.string.search_history_scope)
    val name = boards.firstOrNull { it.slug == slug }?.title ?: slug
    return stringResource(R.string.search_history_board_scope, name)
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
                UserAvatar(url = user.avatarUrl, name = user.name, size = 40.dp)
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
 * Picks the one board the search is scoped to.
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
            Text(stringResource(R.string.search_board_range), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.search_board_range_hint),
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
                BoardRadioRow(
                    title = stringResource(R.string.search_all_boards),
                    selected = picked == null,
                    onSelect = { picked = null },
                )
                if (recent.isNotEmpty()) {
                    Text(
                        stringResource(R.string.search_recent_boards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BoardRadioGrid(recent, picked) { picked = it }
                }
                Text(
                    stringResource(R.string.search_board_all_boards_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoardRadioGrid(remaining, picked) { picked = it }
            }
            Button(onClick = { onApply(picked) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.search_apply_board))
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
                BoardRadioRow(
                    title = board.title,
                    selected = board.slug != null && board.slug == selected,
                    onSelect = { onSelect(board.slug) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BoardRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SearchPreview() {
    NodysseyTheme {
        SearchScreen(
            state =
            SearchUiState(
                searchHistory =
                listOf(
                    SearchHistoryEntry("腾讯云轻量", SearchTarget.POSTS),
                    SearchHistoryEntry("NodeSeek", SearchTarget.USERS),
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
