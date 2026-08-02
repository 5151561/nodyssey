package io.github.nodyssey.ui.postlist

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.R
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.common.AppendSpinner
import io.github.nodyssey.ui.common.AvatarShape
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.EmptyFeedState
import io.github.nodyssey.ui.common.MetaText
import io.github.nodyssey.ui.common.NodeSeekErrorState
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.SkeletonBar
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Sizes
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES
import io.github.nodyssey.ui.theme.readableWidth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Stateful entry point. It only wires the ViewModel to the stateless [PostListScreen] below, which is
 * what keeps the screen previewable and testable without a running app.
 */
@Composable
fun PostListRoute(
    viewModel: PostListViewModel,
    onPostClick: (Long) -> Unit,
    onCreatePost: () -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    scrollToTopRequests: Int = 0,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PostListScreen(
        state = state,
        posts = viewModel.feed.collectAsLazyPagingItems(),
        onPostClick = onPostClick,
        onCreatePost = onCreatePost,
        onBoardClick = viewModel::selectCategory,
        onArrangementChange = viewModel::saveBoardArrangement,
        onSortChange = viewModel::selectSort,
        onSignInClick = onSignIn,
        // The challenge is cleared on the URL that failed, so the WebView loads the same list page the
        // request did — a different page can be served without a challenge and prove nothing.
        onRecoverInBrowser = { onVerify(viewModel.challengeUrl()) },
        modifier = modifier,
        onNavigationBarHiddenChanged = onNavigationBarHiddenChanged,
        scrollToTopRequests = scrollToTopRequests,
    )
}

/**
 * Stateless list.
 *
 * Rows arrive as [LazyPagingItems], so appending, retrying and the end-of-list condition are Paging's
 * concern rather than hand-written bookkeeping. Previews stay possible because a [PagingData] can be
 * built from a plain list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    state: PostListUiState,
    posts: LazyPagingItems<FeedPost>,
    onPostClick: (Long) -> Unit,
    onBoardClick: (String?) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onSignInClick: () -> Unit,
    onRecoverInBrowser: () -> Unit,
    modifier: Modifier = Modifier,
    /** Commits an edit made on the board strip itself: the pill order, and which boards are parked. */
    onArrangementChange: (order: List<String>, parked: Set<String>) -> Unit = { _, _ -> },
    onCreatePost: () -> Unit = {},
    /** Keeps the host navigation bar hidden until the user deliberately scrolls toward the list start. */
    onNavigationBarHiddenChanged: (Boolean) -> Unit = {},
    /**
     * How many times the host has asked for the top of the feed — one per tap on the already-selected
     * 首页 tab. Any increase scrolls; the count itself means nothing, which is what lets two taps in a
     * row read as two requests without the host having to clear a flag afterwards.
     */
    scrollToTopRequests: Int = 0,
) {
    val listState = rememberLazyListState()
    val directionThresholdPx = with(LocalDensity.current) { NavigationDirectionThreshold.toPx() }
    val currentOnNavigationBarHiddenChanged by
        rememberUpdatedState(onNavigationBarHiddenChanged)
    var navigationBarHidden by remember { mutableStateOf(false) }
    val navigationBarScrollConnection =
        remember(directionThresholdPx) {
            FeedNavigationBarScrollConnection(directionThresholdPx) { hidden ->
                navigationBarHidden = hidden
                currentOnNavigationBarHiddenChanged(hidden)
            }
        }

    /*
     * Switching boards or sort order is the one case where the previous scroll offset is meaningless.
     *
     * Coming back from a thread is not a switch, but it looks like one from inside a `LaunchedEffect`:
     * the screen left the composition while the thread was open, so the effect starts again on the
     * same board and used to throw away the offset the list had just restored. Remembering which feed
     * the list was last reset for — saved alongside that offset, so it survives the same trips — is
     * what tells a real switch apart from a return.
     */
    var lastResetFeed by rememberSaveable { mutableStateOf(feedIdentity(state)) }
    LaunchedEffect(state.categorySlug, state.sort) {
        val feed = feedIdentity(state)
        if (feed != lastResetFeed) {
            lastResetFeed = feed
            listState.scrollToItem(0)
        }
    }

    val scrollActive = listState.isScrollInProgress
    // Ending a gesture clears only its partial distance. It deliberately does not reveal the bar.
    LaunchedEffect(scrollActive) {
        if (!scrollActive) navigationBarScrollConnection.resetGesture()
    }
    // The host must not stay stuck bar-less if this screen leaves the composition.
    DisposableEffect(Unit) { onDispose { currentOnNavigationBarHiddenChanged(false) } }

    /*
     * The two ways back to the start of the feed: the 首页 tab, and the wordmark above the list.
     *
     * The bar is revealed first and without waiting for the animation, because neither route came
     * from a user scroll and the direction connection would otherwise leave the list sitting at its
     * top with no bar — the one position from which nothing can be reached.
     */
    val scope = rememberCoroutineScope()
    suspend fun scrollToTop() {
        navigationBarScrollConnection.reveal()
        // Animating hundreds of rows past would take seconds and show nothing. Jumping to the last
        // screenful first costs the same gesture a fixed, short animation however deep the user is.
        if (listState.firstVisibleItemIndex > SCROLL_TO_TOP_ANIMATED_ITEMS) {
            listState.scrollToItem(SCROLL_TO_TOP_ANIMATED_ITEMS)
        }
        listState.animateScrollToItem(0)
    }
    /*
     * Which request has already been answered, remembered exactly as the board identity above is and
     * for the same reason: opening a thread takes this screen out of the composition, so a plain
     * `LaunchedEffect` on the count alone would run again on the way back and throw away the offset
     * the list had just restored. A tap that happened before the thread was opened is not a request
     * to scroll after returning from it.
     */
    var answeredScrollRequest by rememberSaveable { mutableIntStateOf(scrollToTopRequests) }
    LaunchedEffect(scrollToTopRequests) {
        if (scrollToTopRequests != answeredScrollRequest) {
            answeredScrollRequest = scrollToTopRequests
            scrollToTop()
        }
    }

    val refreshState = posts.loadState.refresh
    val appendState = posts.loadState.append

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                HomeTopBar(
                    sort = state.sort,
                    onSortChange = onSortChange,
                    onTitleClick = { scope.launch { scrollToTop() } },
                )
                BoardStrip(
                    boards = state.boards,
                    parkedBoards = state.parkedBoards,
                    selectedSlug = state.categorySlug,
                    onBoardClick = onBoardClick,
                    onArrangementChange = onArrangementChange,
                )
            }
        },
        floatingActionButton = {
            // Follow the same sticky direction state as the navigation bar. Stopping cannot briefly
            // flip this value, so the built-in extended-FAB animation gets one stable target.
            ExtendedFloatingActionButton(
                onClick = onCreatePost,
                expanded = !navigationBarHidden,
                icon = {
                    Icon(Icons.Default.Add, contentDescription = null)
                },
                text = { Text(stringResource(R.string.action_create_post)) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val showSkeleton = posts.itemCount == 0 && refreshState is LoadState.Loading

            // Crossfade rather than a hard swap: a skeleton that snaps to content flashes, and the
            // structure underneath is identical anyway, so there is nothing to animate but opacity.
            Crossfade(targetState = showSkeleton, label = "feed-skeleton") { skeleton ->
                when {
                    skeleton -> FeedSkeleton()

                    // An error only takes over the screen when there is nothing cached to show. With
                    // rows on screen the failure is not worth losing the content over.
                    posts.itemCount == 0 && refreshState is LoadState.Error -> {
                        val error = refreshState.error.toNodeSeekError()
                        NodeSeekErrorState(
                            error = error,
                            onRetry = posts::refresh,
                            // Both recoveries open a browser, but not the same page: a challenge is
                            // cleared on the list URL, a locked board on the sign-in page.
                            onOpenBrowser = onRecoverInBrowser,
                            onSignIn = onSignInClick,
                            boardTitle = state.selectedBoardTitle,
                            onBrowseElsewhere = { onBoardClick(null) }.takeIf { state.categorySlug != null },
                        )
                    }

                    posts.itemCount == 0 && refreshState is LoadState.NotLoading ->
                        EmptyFeedState(
                            onBrowseElsewhere = { onBoardClick(null) }.takeIf { state.categorySlug != null },
                        )

                    else ->
                        PullToRefreshBox(
                            isRefreshing = refreshState is LoadState.Loading,
                            onRefresh = posts::refresh,
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .nestedScroll(navigationBarScrollConnection)
                                    .readableWidth(),
                            ) {
                                items(
                                    count = posts.itemCount,
                                    key = posts.itemKey { it.summary.postId },
                                ) { index ->
                                    val post = posts[index]
                                    if (post != null) {
                                        PostRow(
                                            post = post,
                                            onClick = { onPostClick(post.summary.postId) },
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                                if (appendState is LoadState.Loading) {
                                    item(key = "append-spinner") { AppendSpinner() }
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * Which feed the rows on screen belong to — the board and the order, as one saveable value.
 *
 * A plain string rather than the pair itself so it goes into a `Bundle` unchanged, and so comparing
 * "the same feed as before" cannot depend on how a null slug or an enum happens to be stored.
 */
private fun feedIdentity(state: PostListUiState): String = "${state.categorySlug.orEmpty()}/${state.sort.name}"

private val NavigationDirectionThreshold = 16.dp

/** How much of the feed a "back to the top" actually animates past; anything beyond it is a jump. */
private const val SCROLL_TO_TOP_ANIMATED_ITEMS = 12

/**
 * Drops the avatar onto the title's cap line.
 *
 * Even with the first line's leading trimmed, a 15sp line box still starts a few pixels above the
 * tallest glyph — ascent is not cap height — so a top-aligned avatar reads as floating higher than
 * the title next to it. Measured at 15sp on the row as built. An offset rather than padding so the
 * row keeps its height and the list still fits nine of them on a 800dp screen.
 */
private val AvatarCapOffset = 5.dp

/**
 * Turns deliberate user scroll direction into a sticky navigation-bar state.
 *
 * A negative Y delta advances the feed and hides the bar. A positive delta moves back toward earlier
 * rows and reveals it. Fling/programmatic deltas are ignored, so neither momentum nor coming to rest
 * can reveal the bar on the user's behalf.
 */
internal class FeedNavigationBarScrollConnection(
    private val directionThresholdPx: Float,
    private val onHiddenChanged: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var accumulatedDeltaY = 0f
    private var isHidden = false

    init {
        require(directionThresholdPx > 0f)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val deltaY = available.y
        if (source != NestedScrollSource.UserInput || deltaY == 0f) return Offset.Zero

        if (accumulatedDeltaY != 0f && accumulatedDeltaY * deltaY < 0f) {
            accumulatedDeltaY = 0f
        }
        accumulatedDeltaY += deltaY

        if (abs(accumulatedDeltaY) >= directionThresholdPx) {
            val shouldHide = accumulatedDeltaY < 0f
            accumulatedDeltaY = 0f
            if (shouldHide != isHidden) {
                isHidden = shouldHide
                onHiddenChanged(shouldHide)
            }
        }
        return Offset.Zero
    }

    fun resetGesture() {
        accumulatedDeltaY = 0f
    }

    /**
     * Puts the bar back without a gesture having asked for it.
     *
     * Only for jumps the user initiated elsewhere — a programmatic scroll produces no user deltas, so
     * without this the connection would still believe it is hidden and the next downward scroll would
     * have nothing left to hide.
     */
    fun reveal() {
        accumulatedDeltaY = 0f
        if (isHidden) {
            isHidden = false
            onHiddenChanged(false)
        }
    }
}

/**
 * The home app bar carries the wordmark and exactly one action.
 *
 * Account and search both have their own tab at the bottom, so putting them up here as well would be
 * two ways to reach the same place. Sort has nowhere else to live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    sort: FeedSort,
    onSortChange: (FeedSort) -> Unit,
    onTitleClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                // The wordmark doubles as the second way back to the top, and the only one that
                // works while the navigation bar is hidden — which is exactly when a reader deep in
                // the feed wants it. Height rather than padding grows the target to 48dp, so the
                // title stays on the same start inset as the board strip below it.
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClickLabel = stringResource(R.string.action_scroll_to_top),
                        onClick = onTitleClick,
                    ).heightIn(min = Sizes.minTouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = NodysseyIcons.SwapVert,
                        contentDescription = stringResource(R.string.action_sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortMenuItem(R.string.sort_by_reply_time, FeedSort.LAST_REPLY, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                    SortMenuItem(R.string.sort_by_post_time, FeedSort.POST_TIME, sort) {
                        onSortChange(it)
                        menuOpen = false
                    }
                }
            }
        },
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    value: FeedSort,
    current: FeedSort,
    onClick: (FeedSort) -> Unit,
) {
    val isCurrent = value == current
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onClick(value) },
        // Which order is in force was carried entirely by the tick, and a decorative tick is not
        // information: TalkBack read the two items identically. `selected` is what puts "已选中"
        // into the announcement, so the icon can stay decoration.
        modifier = Modifier.semantics { selected = isCurrent },
        trailingIcon = {
            if (isCurrent) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * One topic.
 *
 * The title is the only thing with visual weight; everything else is a 12sp meta line under it. That
 * is the whole design of this list — nine of these fit on a 800dp screen, and the title is legible in
 * every one of them.
 */
@Composable
internal fun PostRow(
    post: FeedPost,
    onClick: () -> Unit,
    highlight: String? = null,
) {
    val summary = post.summary
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (summary.isPinned) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ).padding(start = 14.dp, end = Spacing.lg, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (summary.isPinned) {
            Box(
                modifier =
                Modifier
                    .offset(y = AvatarCapOffset)
                    .size(Sizes.avatarList)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = NodysseyIcons.PushPin,
                    contentDescription = stringResource(R.string.post_badge_pinned),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            UserAvatar(
                url = summary.avatarUrl,
                name = summary.authorName,
                size = Sizes.avatarList,
                modifier = Modifier.offset(y = AvatarCapOffset),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = highlighted(summary.title, highlight, MaterialTheme.colorScheme.primary),
                    // Trimmed at the top so the glyphs start at the row's top edge instead of 3sp
                    // below it: 15/21 leaves leading above the first line, and against a 40dp avatar
                    // that gap read as the avatar sitting higher than the title beside it.
                    style =
                    MaterialTheme.typography.titleMedium.copy(
                        lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Proportional,
                            trim = LineHeightStyle.Trim.FirstLineTop,
                        ),
                    ),
                    // A read thread is dimmed rather than hidden — the list is still the user's
                    // history, and the drop in both weight and contrast is legible at a glance
                    // without adding a badge that would cost a row's worth of space.
                    color =
                    if (post.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (post.isRead) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (summary.isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription =
                        summary.lockLevel
                            ?.let { stringResource(R.string.post_badge_locked_level, it) }
                            ?: stringResource(R.string.post_badge_locked),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 16dp per the b1 §8 只读→锁定 mapping spec.
                        modifier = Modifier
                            .padding(start = Spacing.xs)
                            .size(16.dp),
                    )
                    summary.lockLevel?.let { level ->
                        Text(
                            text = level.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PostMetaRow(post)
        }
    }
}

/**
 * The searched-for words picked out of the title.
 *
 * Literal and case-insensitive, because that is what the search itself is: the site matches the raw
 * string, so a cleverer match here would paint a word the results were not chosen for. Returns the
 * plain title when nothing is being searched, which is every list but the search results.
 */
internal fun highlighted(
    title: String,
    query: String?,
    color: Color,
): AnnotatedString {
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty() || !title.contains(needle, ignoreCase = true)) return AnnotatedString(title)
    return buildAnnotatedString {
        var cursor = 0
        while (true) {
            val match = title.indexOf(needle, cursor, ignoreCase = true)
            if (match < 0) break
            append(title, cursor, match)
            withStyle(SpanStyle(color = color)) { append(title, match, match + needle.length) }
            cursor = match + needle.length
        }
        append(title, cursor, title.length)
    }
}

/**
 * The meta line, in the order a scanning eye wants it: what board, who, how busy, how fresh.
 *
 * It flows rather than clips so that a large system font wraps it onto a second line instead of
 * pushing the timestamp — the single most useful item here — off the edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostMetaRow(post: FeedPost) {
    val summary = post.summary
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BoardTag(title = summary.categoryTitle, slug = summary.categorySlug)
        MetaText(summary.authorName, singleLine = true)
        if (summary.isPinned) MetaText(stringResource(R.string.post_badge_pinned), singleLine = true)
        if (post.newCommentCount > 0) {
            NewReplyBadge(post.newCommentCount)
        } else {
            summary.commentCount?.let { MetaText(stringResource(R.string.post_reply_count, it), singleLine = true) }
        }
        summary.viewCount?.let { MetaText(stringResource(R.string.post_view_count, it), singleLine = true) }
        summary.lastActiveText?.let { MetaText(it, singleLine = true) }
    }
}

/** Replaces the reply count once the user has read the thread: the delta is the useful number. */
@Composable
private fun NewReplyBadge(count: Int) {
    Text(
        text = stringResource(R.string.post_new_reply_count, count),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/**
 * First-load placeholder, laid out row for row like the real list.
 *
 * A spinner tells the user to wait; a skeleton with the right shape tells them what is coming and
 * measurably feels faster for a list whose structure never varies.
 */
@Composable
private fun FeedSkeleton(modifier: Modifier = Modifier) {
    val widths = listOf(0.88f, 0.68f, 0.94f, 0.75f, 0.84f, 0.62f, 0.90f, 0.71f, 0.80f)
    val metaWidths = listOf(0.52f, 0.44f, 0.57f, 0.40f, 0.49f, 0.46f, 0.38f, 0.53f, 0.45f)

    Column(modifier.fillMaxSize()) {
        widths.forEachIndexed { index, width ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = Spacing.lg, top = Spacing.md, bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(Sizes.avatarList)
                        .clip(AvatarShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SkeletonBar(fraction = width, height = 13.dp)
                    SkeletonBar(fraction = metaWidths[index], height = 11.dp)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Previews use the real scraped rows from the design doc rather than lorem ipsum: the layout has to
// survive a 40-character Chinese title next to a two-character one, and made-up copy never proves it.
// -------------------------------------------------------------------------------------------------

internal val previewState =
    PostListUiState(
        boards =
        listOf(
            Board(null, "综合", null),
            Board("daily", "日常", null),
            Board("tech", "技术", null),
            Board("info", "情报", null),
            Board("review", "测评", null),
            Board("trade", "交易", null),
            Board("dev", "Dev", null),
            Board("inside", "内版", null, adminOnly = true),
        ),
    )

private fun summary(
    id: Long,
    title: String,
    author: String,
    board: String,
    slug: String,
    replies: Int?,
    views: Int?,
    time: String,
    pinned: Boolean = false,
    locked: Boolean = false,
) = PostSummary(
    postId = id,
    title = title,
    authorName = author,
    authorUid = id,
    avatarUrl = null,
    categoryTitle = board,
    categorySlug = slug,
    viewCount = views,
    commentCount = replies,
    lastActiveText = time,
    lastActiveTitle = null,
    isPinned = pinned,
    isLocked = locked,
)

internal fun previewFeed(): Flow<PagingData<FeedPost>> =
    flowOf(
        PagingData.from(
            listOf(
                FeedPost(
                    summary(
                        0, "【公告】NodeSeek 社区规则与常见问题", "admin", "综合", "front",
                        null, null, "", pinned = true,
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        1,
                        "iLatency公测，一个新的社区内Ping站，邀你共建",
                        "酒神",
                        "Dev",
                        "dev",
                        340,
                        30594,
                        "28分钟前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        2,
                        "移动说回馈老用户，十一年宽带合约，一次性交3年宽带费，后续8年不缴费，还送一部手机",
                        "宝宝困困",
                        "日常",
                        "daily",
                        53,
                        1551,
                        "11秒前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                // Read, with replies since — the state the read-mark table exists to render.
                FeedPost(
                    summary(3, "为什么codex 还没重置呢?", "bigxiang", "日常", "daily", 6, 89, "30秒前"),
                    isRead = true,
                    newCommentCount = 4,
                ),
                FeedPost(
                    summary(
                        4,
                        "【出】剩余价值包push出JP.TKY.TRI.Basic",
                        "demain",
                        "交易",
                        "trade",
                        6,
                        124,
                        "20秒前",
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(5, "收北京腾讯云无忧235", "jswcph", "交易", "trade", 0, 2, "3秒前"),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(6, "暗黑模式下热榜显示不清(样式)", "wh1te", "Dev", "dev", 1, 21, "28秒前"),
                    isRead = true,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(
                        7, "可恶，咋我就是原价订阅啊", "深蓝色的天", "日常", "daily",
                        11, 379, "11秒前", locked = true,
                    ),
                    isRead = false,
                    newCommentCount = 0,
                ),
                FeedPost(
                    summary(8, "曝光某商家跑路", "anon", "曝光", "expose", 22, 900, "1分钟前"),
                    isRead = false,
                    newCommentCount = 0,
                ),
            ),
        ),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list")
@Composable
private fun PostListScreenPreview() {
    NodysseyTheme {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSortChange = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list · dark")
@Composable
private fun PostListScreenDarkPreview() {
    NodysseyTheme(darkTheme = true) {
        PostListScreen(
            state = previewState,
            posts = previewFeed().collectAsLazyPagingItems(),
            onPostClick = {},
            onBoardClick = {},
            onSortChange = {},
            onSignInClick = {},
            onRecoverInBrowser = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post list · skeleton")
@Composable
private fun PostListSkeletonPreview() {
    NodysseyTheme { FeedSkeleton() }
}
