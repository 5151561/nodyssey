package io.github.nodyssey.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.SearchHistoryEntry
import io.github.nodyssey.model.SearchTarget
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.CollapsingHeader
import io.github.nodyssey.ui.common.NavigationBarScrollConnection
import io.github.nodyssey.ui.common.NavigationDirectionThreshold
import io.github.nodyssey.ui.common.NoSearchResultsState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_retry
import io.github.nodyssey.ui.resources.action_sort
import io.github.nodyssey.ui.resources.search_advanced
import io.github.nodyssey.ui.resources.search_advanced_active
import io.github.nodyssey.ui.resources.search_advanced_reset
import io.github.nodyssey.ui.resources.search_advanced_sort
import io.github.nodyssey.ui.resources.search_all_boards
import io.github.nodyssey.ui.resources.search_apply_board
import io.github.nodyssey.ui.resources.search_board_section
import io.github.nodyssey.ui.resources.search_clear_all
import io.github.nodyssey.ui.resources.search_clear_board
import io.github.nodyssey.ui.resources.search_clear_query
import io.github.nodyssey.ui.resources.search_hint
import io.github.nodyssey.ui.resources.search_hint_board
import io.github.nodyssey.ui.resources.search_history_empty
import io.github.nodyssey.ui.resources.search_history_scope
import io.github.nodyssey.ui.resources.search_in_board
import io.github.nodyssey.ui.resources.search_load_more_failed
import io.github.nodyssey.ui.resources.search_posts_tab
import io.github.nodyssey.ui.resources.search_recent
import io.github.nodyssey.ui.resources.search_recent_boards
import io.github.nodyssey.ui.resources.search_remove_recent
import io.github.nodyssey.ui.resources.search_user_hint
import io.github.nodyssey.ui.resources.search_user_history_scope
import io.github.nodyssey.ui.resources.search_users_tab
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import io.github.plaza.designsys.component.ChoiceSegments
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.PillTabRow
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.TabLabel
import io.github.plaza.designsys.component.UnderlineTabRow
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.cardShadow
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchRoute(
    viewModel: SearchViewModel,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onBack: () -> Unit,
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
        onApplyOptions = viewModel::applySearchOptions,
        onPostClick = onPostClick,
        onUserClick = onUserClick,
        onBack = onBack,
        onRetry = viewModel::retryUsers,
        onSignIn = onSignIn,
        // The address comes off the failure now, not from a rebuilt search URL.
        onVerify = onVerify,
        modifier = modifier,
        onNavigationBarHiddenChanged = onNavigationBarHiddenChanged,
    )
}

/**
 * Search: a field, what kind of thing to look for, and either the history or the results.
 *
 * The field is pinned to the top with the rest of the screen as the search view. It used to be a
 * collapsing [androidx.compose.material3.AppBarWithSearch] over an
 * [androidx.compose.material3.ExpandedFullScreenSearchBar], which meant the history existed twice
 * (once in the dialog, once behind it) and the board could only be picked from the results screen —
 * after a search had already gone out with the wrong scope.
 *
 * Board and order live behind the 调节 button in the field, in 高级搜索 ([AdvancedSearchSheet]),
 * rather than as a chip group on the page. The setup screen is then the history and nothing else,
 * the one thing a returning reader came for; the button carries a dot whenever the search is scoped,
 * and the placeholder still finishes the sentence (在 技术 版块搜帖子) so the scope is never hidden.
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
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Leaves 搜索 for whatever it was opened from — 首页, in every case the app can reach today.
     *
     * The arrow sits beside the field rather than in an app bar of its own: this screen has no bar,
     * and giving it one to hold a single arrow would push the field, the tabs and the scope row
     * another 64dp down the screen for no other gain.
     */
    onBack: (() -> Unit)? = null,
    postResults: LazyPagingItems<FeedPost>? = null,
    onBoardChange: (String?) -> Unit = {},
    onSortChange: (FeedSort) -> Unit = {},
    /** 高级搜索's button: board and order at once — see [SearchViewModel.applySearchOptions]. */
    onApplyOptions: (String?, FeedSort) -> Unit = { _, _ -> },
    /** Keeps the host navigation bar hidden until the user deliberately scrolls back up. */
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
                SearchInputRow(
                    queryState = queryState,
                    placeholder = searchPlaceholder(state),
                    onBack = onBack,
                    // Arriving with nothing searched yet means arriving to type; arriving back on a
                    // result list does not, and a keyboard over the results would only hide them.
                    autoFocus = state.submittedQuery == null,
                    onSearch = onSearch,
                    // The users tab has no scope to set — the site matches a name fragment and
                    // returns the lot — so there is nothing for the button to open there.
                    onOpenOptions =
                    if (state.target == SearchTarget.POSTS) {
                        {
                            focusManager.clearFocus()
                            showOptionsSheet = true
                        }
                    } else {
                        null
                    },
                    optionsActive = state.hasCustomOptions,
                )

                if (state.submittedQuery == null) {
                    TargetSwitch(selected = state.target, onTargetChange = onTargetChange)
                } else {
                    ResultTabs(state = state, onTargetChange = onTargetChange)
                }

                // Part of the header rather than of the results, because it is chrome for them: the
                // board and the order the list on screen was fetched with.
                if (state.submittedQuery != null && state.target == SearchTarget.POSTS) {
                    ResultScopeRow(
                        state = state,
                        onOpenOptions = { showOptionsSheet = true },
                        onBoardChange = onBoardChange,
                        onSortChange = onSortChange,
                    )
                }
            }

            if (state.submittedQuery == null) {
                // No connection here on purpose. The setup screen is where a query is written, and
                // the field it is written in must not be scrollable off the top by the history list
                // sitting under it.
                SearchHistory(
                    searches = state.searchHistory.filter { it.target == state.target },
                    boards = state.boards,
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

    if (showOptionsSheet) {
        AdvancedSearchSheet(
            boards = state.boards,
            selectedBoard = state.selectedBoard,
            sort = state.sort,
            recentBoards = state.recentBoards,
            // Whether the button runs a search or only sets the scope for the next one.
            willSearch = state.submittedQuery != null || queryState.text.isNotBlank(),
            onDismiss = { showOptionsSheet = false },
            onApply = { board, sort ->
                showOptionsSheet = false
                onApplyOptions(board, sort)
            },
        )
    }
}

/** Whether 高级搜索 holds anything but the defaults — what the dot on the 调节 button says. */
private val SearchUiState.hasCustomOptions: Boolean
    get() = selectedBoard != null || sort != DefaultSearchSort

/**
 * The back arrow and the search pill.
 *
 * [SearchBarDefaults.InputField] is used on its own rather than inside a
 * [androidx.compose.material3.SearchBar]: the collapsed search bar wraps its field in
 * `DisableSoftKeyboard`, because in that pattern typing happens in the expanded copy drawn over it.
 * This screen has no collapsed state to speak of — it is a destination whose whole body is the
 * search view — so the field has to be the one that takes the keyboard. The [SearchBarValue.Expanded]
 * state passed to it is the same statement: what is below the field is the expanded view.
 *
 * The pill is the home screen's search pill, the same raised white on the grey page, so arriving
 * here from it reads as the pill waking up rather than a new screen. There is no separate submit
 * button: the keyboard's search key submits, and so does 高级搜索's button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputRow(
    queryState: TextFieldState,
    placeholder: String,
    autoFocus: Boolean,
    onSearch: () -> Unit,
    onOpenOptions: (() -> Unit)?,
    optionsActive: Boolean,
    onBack: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val layers = LocalPlazaLayers.current

    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) Spacing.xs else Spacing.md, end = Spacing.md, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
            }
        }
        SearchBarDefaults.InputField(
            textFieldState = queryState,
            searchBarState = searchBarState,
            onSearch = {
                onSearch()
                // The results are what was asked for; the keyboard would cover four of them.
                focusManager.clearFocus()
            },
            modifier =
            Modifier
                .weight(1f)
                .cardShadow(CircleShape, layers.shadows)
                .then(layers.cardBorder?.let { Modifier.border(1.dp, it, CircleShape) } ?: Modifier)
                .focusRequester(focusRequester),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            shape = CircleShape,
            // The field's own container is transparent by default because a search bar paints it from
            // the outside. Standing on its own, it has to paint itself — the raised layer, like the
            // home screen's pill.
            colors =
            SearchBarDefaults.inputFieldColors(
                focusedContainerColor = layers.raised,
                unfocusedContainerColor = layers.raised,
            ),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queryState.text.isNotEmpty()) {
                        IconButton(onClick = { queryState.clearText() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.search_clear_query))
                        }
                    }
                    onOpenOptions?.let { OptionsButton(active = optionsActive, onClick = it) }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        if (autoFocus) focusRequester.requestFocus()
    }
}

/** 调节: opens 高级搜索. The dot says the search is scoped, and TalkBack reads the same as a state. */
@Composable
private fun OptionsButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    val activeDescription = stringResource(Res.string.search_advanced_active)
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { if (active) stateDescription = activeDescription },
    ) {
        BadgedBox(
            badge = {
                if (active) Badge(containerColor = MaterialTheme.colorScheme.primary)
            },
        ) {
            Icon(PlazaIcons.Tune, contentDescription = stringResource(Res.string.search_advanced))
        }
    }
}

/** Placeholder as the rest of the sentence the scope has already started. */
@Composable
private fun searchPlaceholder(state: SearchUiState): String {
    if (state.target == SearchTarget.USERS) return stringResource(Res.string.search_user_hint)
    val board = state.selectedBoard ?: return stringResource(Res.string.search_hint)
    return stringResource(Res.string.search_hint_board, state.boards.title(board))
}

private fun List<Board>.title(slug: String): String = firstOrNull { it.slug == slug }?.title ?: slug

private val TargetOrder = listOf(SearchTarget.POSTS, SearchTarget.USERS)

private fun SearchTarget.labelRes(): StringResource =
    if (this == SearchTarget.USERS) Res.string.search_users_tab else Res.string.search_posts_tab

/** 帖子 / 用户 before anything is searched: the pill tabs, since this is part of the form rather than navigation. */
@Composable
private fun TargetSwitch(
    selected: SearchTarget,
    onTargetChange: (SearchTarget) -> Unit,
) {
    PillTabRow(
        selectedTabIndex = TargetOrder.indexOf(selected),
        tabs = TargetOrder.map { TabLabel(stringResource(it.labelRes())) },
        onSelect = { onTargetChange(TargetOrder[it]) },
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg),
    )
}

/** 帖子 / 用户 once there are results: Material's underline tabs, flush with the page. */
@Composable
private fun ResultTabs(
    state: SearchUiState,
    onTargetChange: (SearchTarget) -> Unit,
) {
    UnderlineTabRow(
        selectedTabIndex = TargetOrder.indexOf(state.target),
        // No count on 帖子: `/search` never returns a total, and the number of rows loaded so far is
        // not one — it grows as you scroll, which reads as the site changing.
        tabs =
        TargetOrder.map { target ->
            val count =
                state.userResults.size.takeIf {
                    target == SearchTarget.USERS && state.userLoadState == SearchLoadState.Success
                }
            val title = stringResource(target.labelRes())
            TabLabel(if (count == null) title else "$title · $count")
        },
        onSelect = { onTargetChange(TargetOrder[it]) },
        modifier = Modifier.padding(top = Spacing.md),
    )
}

/**
 * Board and order on the results screen, where changing either re-runs the search that is showing.
 *
 * Both are server parameters, not local filtering: `category` picks the one board `/search` accepts,
 * and `sortBy` is the boards' own 新评论 / 新帖子 — which is why the order reads the same here as it
 * does on the home feed.
 *
 * A scoped search shows its board as an input chip whose ✕ widens it back to the whole site, the
 * common undo; the chip itself, like 全部版块 when nothing is scoped, opens 高级搜索.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultScopeRow(
    state: SearchUiState,
    onOpenOptions: () -> Unit,
    onBoardChange: (String?) -> Unit,
    onSortChange: (FeedSort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val layers = LocalPlazaLayers.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val board = state.selectedBoard
        if (board != null) {
            val title = state.boards.title(board)
            InputChip(
                selected = true,
                onClick = onOpenOptions,
                label = { Text(title) },
                trailingIcon = {
                    Box(
                        modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = stringResource(Res.string.search_clear_board, title)) {
                                onBoardChange(null)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.search_clear_board, title),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    }
                },
                colors =
                InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = null,
            )
        } else {
            FilterChip(
                selected = false,
                onClick = onOpenOptions,
                label = { Text(stringResource(Res.string.search_all_boards)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(containerColor = layers.raised),
                border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
            )
        }
        Box(Modifier.weight(1f))
        Box {
            TextButton(onClick = { showSortMenu = true }) {
                Text(
                    stringResource(state.sort.labelRes()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(Res.string.action_sort),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.widthIn(min = 180.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = layers.raised,
            ) {
                SortOrder.forEachIndexed { index, sort ->
                    SortMenuItem(sort, index, state.sort, onSortChange) { showSortMenu = false }
                }
            }
        }
    }
}

/** The order the site's own sort menu lists them in, and the order every picker here uses. */
private val SortOrder = listOf(FeedSort.LAST_REPLY, FeedSort.POST_TIME)

private fun FeedSort.labelRes(): StringResource =
    if (this == FeedSort.POST_TIME) Res.string.sort_by_post_time else Res.string.sort_by_reply_time

/** Material's selectable menu item: the current order sits on a tonal pill with a tick. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortMenuItem(
    sort: FeedSort,
    index: Int,
    selectedSort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
    closeMenu: () -> Unit,
) {
    val isCurrent = sort == selectedSort
    DropdownMenuItem(
        selected = isCurrent,
        onClick = {
            closeMenu()
            onSortChange(sort)
        },
        text = { Text(stringResource(sort.labelRes()), fontWeight = if (isCurrent) FontWeight.SemiBold else null) },
        shapes = MenuDefaults.itemShape(index, SortOrder.size),
        // Same reason as the home feed's menu: the tick is decoration, `selected` is what TalkBack
        // reads out as 已选中.
        modifier = Modifier.semantics { selected = isCurrent },
        trailingIcon = {
            if (isCurrent) Icon(Icons.Default.Check, contentDescription = null)
        },
        colors =
        MenuDefaults.selectableItemColors(
            containerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    queryState: TextFieldState,
    onPostClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    postResults: LazyPagingItems<FeedPost>?,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
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
                    onOpenBrowser = { onVerify(loadState.error.webViewUrl(NodeSeekSite.BASE_URL)) },
                    onSignIn = onSignIn,
                    onVerify = onVerify,
                )

            SearchLoadState.Success -> {
                if (state.userResults.isEmpty()) {
                    Box(Modifier.fillMaxSize()) { NoSearchResultsState(onClearQuery = { queryState.clearText() }) }
                } else {
                    // One item holding the whole group: see [UserResultGroup] for why one card.
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = LayerPageGutter, vertical = Spacing.md),
                    ) {
                        item("users") {
                            UserResultGroup(
                                users = state.userResults,
                                highlight = state.submittedQuery,
                                onUserClick = onUserClick,
                            )
                        }
                    }
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
                onOpenBrowser = { onVerify(refresh.error.toSiteError().webViewUrl(NodeSeekSite.BASE_URL)) },
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
        LazyColumn(
            contentPadding = PaddingValues(start = LayerPageGutter, end = LayerPageGutter, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(
                count = posts.itemCount,
                key = posts.itemKey { it.summary.postId },
            ) { index ->
                // Same pager, same reason as the feed: a counted-but-unloaded row keeps its space.
                when (val post = posts[index]) {
                    null -> SearchPostCardPlaceholder()

                    else ->
                        SearchPostCard(
                            post = post,
                            highlight = highlight,
                            onClick = { onPostClick(post.summary.postId) },
                        )
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
                    PlazaSpinner(Modifier.describedAsLoading(), size = 24.dp)
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

/**
 * Past searches, each one a whole search rather than a word, as one card of rows.
 *
 * The second line is the scope it ran with, so the same word searched in two boards is two rows and
 * tapping either re-runs exactly what it says. A board scope wears the board's own tag, the same one
 * the results carry, so 情报 reads as 情报 at a glance rather than as a word in a sentence.
 */
@Composable
private fun ColumnScope.SearchHistory(
    searches: List<SearchHistoryEntry>,
    boards: List<Board>,
    onHistoryClick: (SearchHistoryEntry) -> Unit,
    onRemoveHistory: (SearchHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.sm, top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.search_recent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearHistory) {
                Text(stringResource(Res.string.search_clear_all), fontWeight = FontWeight.SemiBold)
            }
        }
        if (searches.isEmpty()) {
            Text(
                stringResource(Res.string.search_history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
        } else {
            Column(Modifier.padding(start = LayerPageGutter, end = LayerPageGutter, bottom = Spacing.lg)) {
                searches.forEachIndexed { index, recent ->
                    // Keyed so removing one row does not hand its neighbour's pressed state around.
                    key(recent.key) {
                        HistoryRow(
                            recent = recent,
                            boards = boards,
                            first = index == 0,
                            last = index == searches.lastIndex,
                            onClick = { onHistoryClick(recent) },
                            onRemove = { onRemoveHistory(recent) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    recent: SearchHistoryEntry,
    boards: List<Board>,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    GroupedListItem(
        first = first,
        last = last,
        onClick = onClick,
        leadingContent = {
            Icon(
                if (recent.target == SearchTarget.POSTS) PlazaIcons.History else PlazaIcons.PersonSearch,
                contentDescription = null,
            )
        },
        headlineContent = { Text(recent.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { HistoryScope(recent, boards) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.search_remove_recent, recent.query))
            }
        },
    )
}

@Composable
private fun HistoryScope(
    entry: SearchHistoryEntry,
    boards: List<Board>,
) {
    val slug = entry.categorySlug
    if (entry.target == SearchTarget.POSTS && slug != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoardTag(title = boards.title(slug), slug = slug)
            HistoryScopeText(stringResource(Res.string.search_posts_tab))
        }
        return
    }
    HistoryScopeText(
        stringResource(
            if (entry.target == SearchTarget.USERS) Res.string.search_user_history_scope else Res.string.search_history_scope,
        ),
    )
}

@Composable
private fun HistoryScopeText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * 高级搜索: the one board the search is scoped to, and its order.
 *
 * Single choice because `/search` takes a single `category` and applies it itself. The checkbox
 * version of this sheet could only pretend to filter by several: it searched the whole site and
 * dropped the rows that did not match, so a board with few hits made the app walk page after page
 * looking for something to show. 全部版块 is a real option here, not an empty selection — it is what
 * the site does when the parameter is absent. It leads the 最近使用 row, since it is the choice a
 * reader most often comes back to.
 *
 * Nothing applies until the button: both choices are server parameters, and re-running the search on
 * every tap would spend a request per chip on the way to the one that was meant. The button names
 * what it will do — 在「技术」中搜索 — and with an empty box, where there is nothing to search yet,
 * it only sets the scope and says 应用.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSearchSheet(
    boards: List<Board>,
    selectedBoard: String?,
    sort: FeedSort,
    recentBoards: List<String>,
    willSearch: Boolean,
    onDismiss: () -> Unit,
    onApply: (String?, FeedSort) -> Unit,
) {
    var pickedBoard by remember(selectedBoard) { mutableStateOf(selectedBoard) }
    var pickedSort by remember(sort) { mutableStateOf(sort) }
    val recent = recentBoards.mapNotNull { slug -> boards.firstOrNull { it.slug == slug } }
    val remaining = boards.filterNot { board -> recent.any { it.slug == board.slug } }
    val allBoards = stringResource(Res.string.search_all_boards)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        containerColor = LocalPlazaLayers.current.page,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.padding(start = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.search_advanced),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        pickedBoard = null
                        pickedSort = DefaultSearchSort
                    },
                ) {
                    Text(stringResource(Res.string.search_advanced_reset), fontWeight = FontWeight.SemiBold)
                }
            }
            // The choices scroll on their own so the button below stays reachable even on a short
            // window; the button is the whole point of opening the sheet.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(
                        stringResource(Res.string.search_board_section),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentPadding = PaddingValues(horizontal = Spacing.xs),
                    )
                    LayerCard(
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (recent.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Icon(
                                    PlazaIcons.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    stringResource(Res.string.search_recent_boards),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        BoardChipFlow {
                            BoardChip(allBoards, selected = pickedBoard == null) { pickedBoard = null }
                            recent.forEach { board ->
                                BoardChip(board.title, selected = board.slug == pickedBoard) { pickedBoard = board.slug }
                            }
                        }
                        if (remaining.isNotEmpty()) {
                            LayerDivider(startInset = 0.dp)
                            BoardChipFlow {
                                remaining.forEach { board ->
                                    BoardChip(board.title, selected = board.slug == pickedBoard) {
                                        pickedBoard = board.slug
                                    }
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(
                        stringResource(Res.string.search_advanced_sort),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentPadding = PaddingValues(horizontal = Spacing.xs),
                    )
                    SortSegments(selected = pickedSort, onSelect = { pickedSort = it })
                }
            }
            Button(
                onClick = { onApply(pickedBoard, pickedSort) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) {
                Text(
                    if (willSearch) {
                        stringResource(Res.string.search_in_board, pickedBoard?.let { boards.title(it) } ?: allBoards)
                    } else {
                        stringResource(Res.string.search_apply_board)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BoardChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        content()
    }
}

/**
 * No leading tick, deliberately — the artboard draws one, and this is the one place it is left out.
 *
 * A tick makes the selected chip ~26dp wider, and in a wrapping group that re-flows every chip after
 * it: on a narrow phone picking a board moved the rest of the list between rows, under the finger
 * that was still choosing. The primary fill against the tonal ones already says which one is on,
 * TalkBack reads 已选中 from [FilterChip]'s own semantics either way, and the tick is optional
 * decoration in Material's own spec — so it is the part that goes.
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
        label = { Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(36.dp),
        colors =
        FilterChipDefaults.filterChipColors(
            containerColor = LocalPlazaLayers.current.inset,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = LocalPlazaLayers.current.cardBorder?.let { BorderStroke(1.dp, it) },
    )
}

/** 按回复时间 / 按发帖时间, as Material's segmented buttons with the artboard's 8dp corners. */
@Composable
private fun SortSegments(
    selected: FeedSort,
    onSelect: (FeedSort) -> Unit,
) {
    ChoiceSegments(
        labels = SortOrder.map { stringResource(it.labelRes()) },
        selectedIndex = SortOrder.indexOf(selected),
        onSelect = { onSelect(SortOrder[it]) },
    )
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
