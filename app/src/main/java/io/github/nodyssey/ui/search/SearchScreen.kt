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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        onQueryChange = viewModel::updateQuery,
        onSearch = viewModel::submitSearch,
        onTargetChange = viewModel::selectTarget,
        onHistoryClick = viewModel::selectHistory,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        onBoardsChange = viewModel::setBoards,
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
    onQueryChange: (String) -> Unit,
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
    onBoardsChange: (Set<String>) -> Unit = {},
    onSortChange: (SearchSort) -> Unit = {},
) {
    var showBoardSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().readableWidth(),
        ) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = {
                    Text(
                        stringResource(
                            if (state.target == SearchTarget.USERS) R.string.search_user_hint else R.string.search_hint,
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
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
                                if (state.selectedBoards.isEmpty()) {
                                    stringResource(R.string.search_all_boards)
                                } else {
                                    stringResource(R.string.search_selected_boards, state.selectedBoards.size)
                                },
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
                onQueryChange = onQueryChange,
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
            selected = state.selectedBoards,
            recentBoards = state.recentBoards,
            onDismiss = { showBoardSheet = false },
            onApply = {
                onBoardsChange(it)
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
    onQueryChange: (String) -> Unit,
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
                    Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { onQueryChange("") }) }
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
                Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { onQueryChange("") }) }
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
    if (entry.categorySlugs.isEmpty()) return stringResource(R.string.search_history_scope)
    val names = entry.categorySlugs.map { slug -> boards.firstOrNull { it.slug == slug }?.title ?: slug }
    return stringResource(R.string.search_history_board_scope, names.joinToString("、"))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRangeSheet(
    boards: List<Board>,
    selected: Set<String>,
    recentBoards: List<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var checked by remember(selected) { mutableStateOf(selected) }
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
        val toggle: (Board, Boolean) -> Unit = { board, isChecked ->
            board.slug?.let { slug -> checked = if (isChecked) checked + slug else checked - slug }
        }
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
            // The board list scrolls on its own so the reset/apply row below stays reachable even
            // on a short window; the buttons are the whole point of opening the sheet.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (recent.isNotEmpty()) {
                    Text(
                        stringResource(R.string.search_recent_boards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BoardCheckboxGrid(recent, checked, toggle)
                }
                Text(
                    stringResource(R.string.search_all_boards),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoardCheckboxGrid(remaining, checked, toggle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { checked = emptySet() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.search_reset))
                }
                Button(onClick = { onApply(checked) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.search_apply_boards, checked.size))
                }
            }
        }
    }
}

/** Two columns: fourteen boards in seven rows fit a phone screen without a scroll to the buttons. */
@Composable
private fun BoardCheckboxGrid(
    boards: List<Board>,
    checked: Set<String>,
    onToggle: (Board, Boolean) -> Unit,
) {
    boards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { board ->
                BoardCheckboxRow(
                    board = board,
                    checked = board.slug in checked,
                    onCheckedChange = { onToggle(board, it) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BoardCheckboxRow(
    board: Board,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            board.title,
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
            onQueryChange = {},
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
