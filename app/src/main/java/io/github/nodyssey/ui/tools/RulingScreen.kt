package io.github.nodyssey.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.RulingAction
import io.github.nodyssey.data.RulingKind
import io.github.nodyssey.data.RulingRecord
import io.github.nodyssey.data.RulingTarget
import io.github.nodyssey.ui.common.JumpDestination
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch

@Composable
fun RulingRoute(
    viewModel: RulingViewModel,
    onBack: () -> Unit,
    onPostClick: (Long, Int?) -> Unit,
    onUserClick: (Long) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulingScreen(
        state = state,
        onBack = onBack,
        onLoadMore = viewModel::loadNextPage,
        onLoadPage = viewModel::loadPage,
        onScrollHandled = viewModel::onScrollHandled,
        onRetry = viewModel::retry,
        onRecordClick = { record ->
            // Locals for the same reason as in `AssetsScreen`: these are `val`s in another module,
            // so the null check does not narrow the type at the use site.
            val postId = record.postId
            val targetUid = record.targetUid
            when {
                postId != null -> onPostClick(postId, record.floor)
                targetUid != null -> onUserClick(targetUid)
            }
        },
        // The page the user is on, not the top of the log: a decision they are looking at is on page
        // 37 and nowhere else, and the site's own pager is a hash route the URL can carry.
        onOpenBrowser = { page -> onOpenBrowser(NodeSeekSite.BASE_URL + NodeSeekSite.rulingPath(page)) },
        onSignIn = onSignIn,
        modifier = modifier,
    )
}

/**
 * 管理记录.
 *
 * The site presents this as a five-column table, which a 360dp screen cannot carry. Each decision
 * becomes a two-line row instead: who and why on the first line, what was done and by whom on the
 * second. The compound action stays joined by "+" rather than split into badges — "扣 20 鸡腿 + 移动版块
 * 至 促销 + 锁定修改" is one decision and reads as one sentence.
 *
 * The reason moves out of the action and onto the first line, which is the one place this screen
 * departs from the site's wording. The site says 因“灌水”被-10鸡腿 inside every verb of a compound
 * decision; on a phone that repeats the longest string in the row next to the shortest ones.
 *
 * Paging is the comment thread's, component and all: the next page joins the tail as you scroll, and
 * [PageJumpSheet] is there for the travel that scrolling cannot do. A numbered pager sat here before
 * and was the wrong half of that pair — it made every page a deliberate act, including the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulingScreen(
    state: RulingUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadPage: (Int) -> Unit,
    onScrollHandled: () -> Unit,
    onRetry: () -> Unit,
    onRecordClick: (RulingRecord) -> Unit,
    /** Opens the web log at the page named, which is the one the reader is looking at. */
    onOpenBrowser: (Int) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appBarState = rememberOneHandAppBarState()
    var showPageSheet by remember { mutableStateOf(false) }
    var toolbarPinned by rememberSaveable { mutableStateOf(true) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - APPEND_LOOKAHEAD
        }
    }
    LaunchedEffect(shouldLoadMore, state.records.size) {
        if (shouldLoadMore) onLoadMore()
    }

    // The page the reader is looking at, not the furthest one fetched: on an appending list those
    // part company the moment the second page arrives.
    val visiblePage by remember(state.recordPages, state.firstLoadedPage) {
        derivedStateOf {
            state.recordPages.getOrNull(listState.firstVisibleItemIndex)
                ?: state.recordPages.lastOrNull()
                ?: state.firstLoadedPage
        }
    }

    /** Scrolls when the page is already in the list, and asks for it when it is not. */
    fun goToPage(target: Int) {
        // Both branches move the list without a gesture, so neither would fold the app bar on its
        // own and the page asked for would arrive in the half-screen left under it. Its own
        // coroutine rather than in front of the scroll below: the two run together.
        scope.launch { appBarState.fold() }
        val index = state.firstIndexOfPage(target)
        if (index != null && state.isPageLoaded(target)) {
            scope.launch { listState.animateScrollToItem(index) }
        } else {
            onLoadPage(target)
        }
    }

    // Keyed on the pages themselves rather than on the row count: a jump swaps one page of twenty
    // rows for another page of twenty, so a count would not change and this would never re-run.
    LaunchedEffect(state.pendingScroll, state.recordPages) {
        val target = state.pendingScroll ?: return@LaunchedEffect
        if (!state.isPageLoaded(target)) return@LaunchedEffect
        listState.scrollToItem(state.firstIndexOfPage(target) ?: 0)
        onScrollHandled()
    }

    // Same rule as the thread's bar: the foot only counts once there is nothing left to append, or
    // the bar blinks through every batch as "last item visible" flips back and forth.
    val atListTop = !listState.canScrollBackward
    val toolbarExpanded = toolbarPinned || atListTop || state.error != null || showPageSheet

    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        topBar = {
            OneHandTopAppBar(
                title = stringResource(R.string.ruling_title),
                state = appBarState,
                // A parameter rather than a hand-stacked Column: the subtitle then takes its type
                // and colour from the bar — labelMedium in the toolbar, bodyMedium under the big
                // centred title — instead of two values chosen here, and it follows the title
                // through the fold without this screen knowing how far along that fold is.
                subtitle = stringResource(R.string.ruling_subtitle),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenBrowser(visiblePage) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.action_open_in_browser),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
        ) {
            when {
                state.isLoading && state.records.isEmpty() -> LoadingState(Modifier.fillMaxSize())

                state.error != null && state.records.isEmpty() ->
                    SiteErrorState(
                        error = state.error,
                        onRetry = onRetry,
                        onOpenBrowser = { onOpenBrowser(1) },
                        onSignIn = onSignIn,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .floatingToolbarVerticalNestedScroll(
                                expanded = toolbarExpanded,
                                onExpand = { toolbarPinned = true },
                                onCollapse = { toolbarPinned = false },
                            ),
                    ) {
                        items(count = state.records.size, key = { state.records[it].id }) { index ->
                            RulingRow(
                                record = state.records[index],
                                boardTitles = state.boardTitles,
                                onClick = onRecordClick,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (state.isAppending) {
                            item("append") { AppendSpinner() }
                        }
                        // Without this the list would simply stop at page 100 and read as the whole
                        // log — it is a hundredth of it. The cap belongs to the site, so say so.
                        if (!state.hasNextPage && state.totalPages >= RulingViewModel.MAX_PAGES) {
                            item("cap") {
                                Text(
                                    text = stringResource(R.string.ruling_page_cap, RulingViewModel.MAX_PAGES),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                                )
                            }
                        }
                    }
            }

            // A jump replaces the whole list, so the append spinner at its foot would be pointing at
            // rows on their way out. This says "a different page is coming" where the reader is
            // already looking, and it is the only feedback between the tap and the new page.
            if (state.isLoading && state.records.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            if (state.records.isNotEmpty() && state.totalPages > 1) {
                PageJumpRail(
                    expanded = toolbarExpanded,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { goToPage((visiblePage - 1).coerceAtLeast(1)) },
                    onNext = { goToPage((visiblePage + 1).coerceAtMost(state.totalPages)) },
                    onPageClick = { showPageSheet = true },
                    // No FAB under it here, unlike the thread and the feed: the log is read-only,
                    // so the rail is the whole of what floats and it sits where their FAB does.
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.lg),
                )
            }
        }
    }

    if (showPageSheet) {
        PageJumpSheet(
            page = visiblePage,
            totalPages = state.totalPages,
            note = stringResource(R.string.ruling_page_progress, state.records.size),
            // The log keeps no place across visits, so "上次阅读" is this session's own furthest
            // point — offered while the reader has scrolled back from it, and gone once they have not.
            resume =
            state.lastLoadedPage.takeIf { it != visiblePage }?.let { target ->
                JumpDestination(
                    label = stringResource(R.string.page_jump_latest_read, target),
                    icon = PlazaIcons.Bookmark,
                    onGo = {
                        showPageSheet = false
                        goToPage(target)
                    },
                )
            },
            // Newest first, like the feed: the log's own newest entry is on page 1.
            newest =
            JumpDestination(
                label = stringResource(R.string.page_jump_newest),
                icon = PlazaIcons.VerticalAlignTop,
                onGo = {
                    showPageSheet = false
                    goToPage(1)
                },
            ).takeIf { visiblePage > 1 },
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                goToPage(target.coerceIn(1, state.totalPages.coerceAtLeast(1)))
            },
        )
    }
}

/**
 * How many rows from the foot the next page is asked for.
 *
 * Four, the same as the thread. A row here is two lines tall, so four of them is most of a screen —
 * enough that the page lands before the reader reaches the gap, and not so early that idly scrolling
 * two rows costs a request.
 */
private const val APPEND_LOOKAHEAD = 4

@Composable
private fun RulingRow(
    record: RulingRecord,
    boardTitles: Map<String, String>,
    onClick: (RulingRecord) -> Unit,
) {
    // The row exists to say someone was penalised; the only useful next question is what for, so it
    // opens the post at the floor in question. An account-level decision names no post and falls back
    // to the member's space, which is where the site's own row links too.
    val destination = record.postId != null || record.targetUid != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = destination) { onClick(record) }
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = record.kind.icon(),
                contentDescription = null,
                tint = record.kind.tint(),
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Built as an annotated string rather than one format string so the user name can carry the
            // weight — it is what the eye scans for in a log of other people's punishments.
            val targetKind = record.target.label()?.let { stringResource(R.string.ruling_target_kind, it) }
            val reason = record.reason?.let { stringResource(R.string.ruling_reason, it) }
            Text(
                text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(record.targetName) }
                    targetKind?.let(::append)
                    reason?.let(::append)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = record.metaLine(boardTitles),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RulingRecord.metaLine(boardTitles: Map<String, String>): String {
    val actions =
        actions
            .map { it.label(boardTitles) }
            .joinToString(" + ")
            .ifBlank { stringResource(R.string.ruling_action_none) }
    val time = createdAtMillis?.let(TimeFormat::absolute).orEmpty()
    return moderatorName
        ?.let { stringResource(R.string.ruling_meta, actions, it, time) }
        ?: stringResource(R.string.ruling_meta_no_moderator, actions, time)
}

/** `null` for an account-level decision: the site writes the bare name, with no "的…" after it. */
@Composable
private fun RulingTarget.label(): String? =
    when (this) {
        RulingTarget.POST -> stringResource(R.string.ruling_target_post)
        RulingTarget.COMMENT -> stringResource(R.string.ruling_target_comment)
        RulingTarget.USER -> null
    }

/**
 * One verb, in the app's voice.
 *
 * The signed 鸡腿/星辰 figure becomes 加/扣 with an unsigned number: "扣 10 鸡腿" is how the deduction is
 * spoken about, and "-10鸡腿" is how a table column prints it.
 *
 * A board that has since been renamed or retired resolves to nothing, and the row shows the slug it
 * was moved to rather than dropping the fact that it moved.
 */
@Composable
private fun RulingAction.label(boardTitles: Map<String, String>): String =
    when (this) {
        is RulingAction.Coin ->
            if (diff >= 0) {
                stringResource(R.string.ruling_action_coin_add, diff)
            } else {
                stringResource(R.string.ruling_action_coin_deduct, -diff)
            }

        is RulingAction.Stardust ->
            if (diff >= 0) {
                stringResource(R.string.ruling_action_stardust_add, diff)
            } else {
                stringResource(R.string.ruling_action_stardust_deduct, -diff)
            }

        is RulingAction.Title -> stringResource(R.string.ruling_action_title, title)

        is RulingAction.Move -> stringResource(R.string.ruling_action_move, boardTitles[boardSlug] ?: boardSlug)

        is RulingAction.ReadRank -> stringResource(R.string.ruling_action_rank, rank)

        is RulingAction.Lock ->
            stringResource(if (locked) R.string.ruling_action_lock else R.string.ruling_action_unlock)

        is RulingAction.Award ->
            stringResource(if (award) R.string.ruling_action_award else R.string.ruling_action_award_cancel)

        is RulingAction.Hide ->
            stringResource(
                when {
                    hidden && wholeUser -> R.string.ruling_action_hide_all
                    hidden -> R.string.ruling_action_hide
                    wholeUser -> R.string.ruling_action_unhide_all
                    else -> R.string.ruling_action_unhide
                },
            )

        is RulingAction.Pin ->
            stringResource(if (pinned) R.string.ruling_action_pin else R.string.ruling_action_unpin)

        is RulingAction.Suspend ->
            days
                ?.let { stringResource(R.string.ruling_action_suspend, it) }
                ?: stringResource(R.string.ruling_action_unsuspend)
    }

private fun RulingKind.icon(): ImageVector =
    when (this) {
        RulingKind.PENALTY -> PlazaIcons.Gavel
        RulingKind.BAN -> PlazaIcons.Block
        RulingKind.MOVE -> PlazaIcons.SwapVert
        RulingKind.PERMISSION -> PlazaIcons.Visibility
        RulingKind.REWARD -> NodeSeekIcons.ChickenLeg
    }

@Composable
private fun RulingKind.tint() =
    when (this) {
        RulingKind.BAN -> MaterialTheme.colorScheme.error
        RulingKind.REWARD -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

// -------------------------------------------------------------------------------------------------

/** Shapes taken from live page 1 on 2026-08-02, with names and reasons replaced. */
private val previewRecords =
    listOf(
        RulingRecord(
            id = 30212,
            targetName = "深蓝色的天",
            targetUid = 65236,
            target = RulingTarget.POST,
            postId = 852128,
            floor = null,
            reason = null,
            actions = listOf(RulingAction.Move("daily")),
            moderatorName = "kanata",
            createdAtMillis = 1_785_649_006_000L,
            kind = RulingKind.MOVE,
        ),
        RulingRecord(
            id = 30211,
            targetName = "bigxiang",
            targetUid = 65699,
            target = RulingTarget.COMMENT,
            postId = 852162,
            floor = 6,
            reason = "禁止恶意引战，提倡理性讨论",
            actions = listOf(RulingAction.Coin(-30), RulingAction.Suspend(7)),
            moderatorName = "nsadmin",
            createdAtMillis = 1_785_648_000_000L,
            kind = RulingKind.BAN,
        ),
        RulingRecord(
            id = 30210,
            targetName = "jswcph",
            targetUid = 27225,
            target = RulingTarget.POST,
            postId = 853140,
            floor = null,
            reason = "发卡站未发推广区",
            actions =
            listOf(
                RulingAction.Coin(-20),
                RulingAction.Move("promotion"),
                RulingAction.ReadRank(255),
                RulingAction.Lock(true),
            ),
            moderatorName = "kanata",
            createdAtMillis = 1_785_560_000_000L,
            kind = RulingKind.MOVE,
        ),
        RulingRecord(
            id = 30209,
            targetName = "暮色",
            targetUid = 39158,
            target = RulingTarget.USER,
            postId = null,
            floor = null,
            reason = "论坛不欢迎抽奖号",
            actions = listOf(RulingAction.Coin(-1000), RulingAction.Suspend(1000)),
            moderatorName = "xe",
            createdAtMillis = 1_785_470_000_000L,
            kind = RulingKind.BAN,
        ),
        RulingRecord(
            id = 30208,
            targetName = "haiqing123",
            targetUid = 16032,
            target = RulingTarget.POST,
            postId = 853221,
            floor = null,
            reason = "鼓励优质文章",
            actions = listOf(RulingAction.Coin(50), RulingAction.Award(true)),
            moderatorName = "花火",
            createdAtMillis = 1_785_390_000_000L,
            kind = RulingKind.REWARD,
        ),
        RulingRecord(
            id = 30207,
            targetName = "terrywei",
            targetUid = 62775,
            target = RulingTarget.COMMENT,
            postId = 853231,
            floor = 38,
            reason = null,
            actions = listOf(RulingAction.Hide(hidden = true, wholeUser = false)),
            moderatorName = "kanata",
            createdAtMillis = 1_785_300_000_000L,
            kind = RulingKind.PENALTY,
        ),
    )

private val previewBoards = mapOf("daily" to "日常", "promotion" to "推广")

/** Two pages appended, the reader looking at the second — which is what the toolbar reports. */
private val previewState =
    RulingUiState(
        isLoading = false,
        records = previewRecords,
        recordPages = List(3) { 1 } + List(3) { 2 },
        firstLoadedPage = 1,
        lastLoadedPage = 2,
        totalPages = 100,
        hasNextPage = true,
        boardTitles = previewBoards,
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9d 管理记录")
@Composable
private fun RulingPreview() {
    PlazaTheme {
        RulingScreen(
            state = previewState,
            onBack = {},
            onLoadMore = {},
            onLoadPage = {},
            onScrollHandled = {},
            onRetry = {},
            onRecordClick = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9d 管理记录 · 接页中")
@Composable
private fun RulingAppendingPreview() {
    PlazaTheme {
        RulingScreen(
            state = previewState.copy(isAppending = true),
            onBack = {},
            onLoadMore = {},
            onLoadPage = {},
            onScrollHandled = {},
            onRetry = {},
            onRecordClick = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "9d 管理记录 · 未登录")
@Composable
private fun RulingSignedOutPreview() {
    PlazaTheme(darkTheme = true) {
        RulingScreen(
            state = RulingUiState(isLoading = false, error = SiteError.LoginRequired),
            onBack = {},
            onLoadMore = {},
            onLoadPage = {},
            onScrollHandled = {},
            onRetry = {},
            onRecordClick = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }
}
