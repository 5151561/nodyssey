package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ThreadPreview
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.composer.PostEditTarget
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.countOf
import io.github.nodyssey.model.hasSpent
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.BottomPullToRefreshBox
import io.github.nodyssey.ui.common.JumpDestination
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.NumberEntry
import io.github.nodyssey.ui.common.PageJumpRail
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.RoleBadgeRow
import io.github.nodyssey.ui.common.SiteErrorSnackbar
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.nodyssey.ui.common.rememberShareText
import io.github.nodyssey.ui.common.sharedThreadAuthor
import io.github.nodyssey.ui.common.sharedThreadAvatar
import io.github.nodyssey.ui.common.sharedThreadBoard
import io.github.nodyssey.ui.common.sharedThreadTitle
import io.github.nodyssey.ui.composer.FloorReference
import io.github.nodyssey.ui.composer.ReplyComposerHost
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_copy_link
import io.github.nodyssey.ui.resources.action_more
import io.github.nodyssey.ui.resources.action_open_in_browser
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.action_share
import io.github.nodyssey.ui.resources.chicken_dialog_body
import io.github.nodyssey.ui.resources.chicken_dialog_body_free
import io.github.nodyssey.ui.resources.chicken_dialog_confirm
import io.github.nodyssey.ui.resources.chicken_dialog_title
import io.github.nodyssey.ui.resources.dislike_dialog_body
import io.github.nodyssey.ui.resources.dislike_dialog_confirm
import io.github.nodyssey.ui.resources.dislike_dialog_title
import io.github.nodyssey.ui.resources.page_jump_at_page
import io.github.nodyssey.ui.resources.page_jump_at_page_floor
import io.github.nodyssey.ui.resources.page_jump_latest
import io.github.nodyssey.ui.resources.page_jump_latest_read
import io.github.nodyssey.ui.resources.page_jump_latest_read_floor
import io.github.nodyssey.ui.resources.page_jump_resume_title
import io.github.nodyssey.ui.resources.post_auto_paging
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_badge_original_poster
import io.github.nodyssey.ui.resources.post_body_copied
import io.github.nodyssey.ui.resources.post_body_empty
import io.github.nodyssey.ui.resources.post_collect_action
import io.github.nodyssey.ui.resources.post_collected_action
import io.github.nodyssey.ui.resources.post_comment_blocked
import io.github.nodyssey.ui.resources.post_comment_blocked_show
import io.github.nodyssey.ui.resources.post_comments_empty
import io.github.nodyssey.ui.resources.post_comments_empty_hint
import io.github.nodyssey.ui.resources.post_comments_header
import io.github.nodyssey.ui.resources.post_copy_body
import io.github.nodyssey.ui.resources.post_edit_action
import io.github.nodyssey.ui.resources.post_edited
import io.github.nodyssey.ui.resources.post_edited_at
import io.github.nodyssey.ui.resources.post_floor_actions
import io.github.nodyssey.ui.resources.post_link_copied
import io.github.nodyssey.ui.resources.post_open_original
import io.github.nodyssey.ui.resources.post_page_progress
import io.github.nodyssey.ui.resources.post_quote_action
import io.github.nodyssey.ui.resources.post_quote_floor
import io.github.nodyssey.ui.resources.post_reaction_chicken
import io.github.nodyssey.ui.resources.post_reaction_cost
import io.github.nodyssey.ui.resources.post_reaction_dislike
import io.github.nodyssey.ui.resources.post_reaction_feed
import io.github.nodyssey.ui.resources.post_reaction_free
import io.github.nodyssey.ui.resources.post_reaction_free_today
import io.github.nodyssey.ui.resources.post_reaction_irreversible
import io.github.nodyssey.ui.resources.post_reaction_like
import io.github.nodyssey.ui.resources.post_reaction_spent
import io.github.nodyssey.ui.resources.post_reply_action
import io.github.nodyssey.ui.resources.post_reply_to
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerCardGap
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.QuotePreview
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.TonalTile
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.PostTitle
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.asSignature
import io.github.plaza.designsys.theme.floatShadow
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PostDetailRoute(
    viewModel: PostDetailViewModel,
    replyViewModel: ReplyComposerViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onImageClick: (List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
    /** Opens the editor on a floor this account wrote. */
    onEdit: (PostEditTarget) -> Unit = {},
    /** Draws the votes embedded in the thread; see [PostDetailScreen]. */
    voteContent: @Composable (Long) -> Unit = {},
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val replyState by replyViewModel.uiState.collectAsStateWithLifecycle()
    val postUrl = viewModel.postUrl()
    // Every image in the thread as currently loaded, so the viewer can page between them without
    // going back to the data layer — the URLs were already parsed into the rendered content.
    val images =
        remember(state.body, state.comments, state.showBlockedContent) { state.imageUrls() }
    PostDetailScreen(
        state = state,
        postUrl = postUrl,
        onBack = onBack,
        onOpenBrowser = onOpenBrowser,
        onLinkClick = onLinkClick,
        onAuthorClick = onAuthorClick,
        onSignIn = onSignIn,
        onVerify = { onVerify(postUrl) },
        onImageClick = { url -> onImageClick(images.ifEmpty { listOf(url) }, url) },
        onRetry = viewModel::refresh,
        onPullRefresh = viewModel::pullRefresh,
        onRefreshPage = viewModel::refreshPage,
        onLoadMore = viewModel::loadNextPage,
        onRefreshTail = viewModel::refreshTail,
        onLoadPage = viewModel::loadPage,
        onExtendToPage = viewModel::extendToPage,
        onJumpToFloor = viewModel::jumpToFloor,
        onScrollHandled = viewModel::onScrollHandled,
        onReadingPositionChange = viewModel::recordReadingPosition,
        onResumeReading = viewModel::resumeReading,
        showBackButton = showBackButton,
        // Replying needs an account; sending an anonymous reply into the void is the one outcome
        // the editor must not produce, so the sign-in page comes first.
        onReply = { target -> if (state.isSignedIn) replyViewModel.open(target) else onSignIn() },
        // 引用 needs the account for the same reason 回复 does, and it writes into the same editor.
        onQuote = { floor -> if (state.isSignedIn) replyViewModel.quote(floor) else onSignIn() },
        onEdit = onEdit,
        onReact = viewModel::react,
        onLoadFreeChickenLegs = viewModel::loadFreeChickenLegs,
        onReactionFailureShown = viewModel::onReactionFailureShown,
        onCollect = viewModel::toggleCollect,
        onCollectFailureShown = viewModel::onCollectFailureShown,
        voteContent = voteContent,
        stardustContent = stardustContent,
        replyOpen = replyState.visible,
        modifier = modifier,
    )

    ReplyComposerHost(
        state = replyState,
        onDismiss = replyViewModel::close,
        bodyState = replyViewModel.bodyState,
        onClearReplyTo = replyViewModel::clearReplyTo,
        onPreviewChange = replyViewModel::setPreviewing,
        onPickImages = replyViewModel::addImages,
        onRemoveAttachment = replyViewModel::removeAttachment,
        onRetryAttachment = replyViewModel::retryUpload,
        onRetryFailedUploads = replyViewModel::retryFailedUploads,
        // The floor number the site reports rides through to the jump: refresh() alone re-read the
        // window's first page, and on a multi-page thread the reply the reader just wrote stayed
        // out of sight. See [PostDetailViewModel.showPublishedReply].
        onPublish = { replyViewModel.publish(viewModel::showPublishedReply) },
        onClearError = replyViewModel::clearPublishError,
        onSignIn = onSignIn,
        onVerify = { onVerify(postUrl) },
        onToolbarChange = replyViewModel::setToolbar,
        onToolbarReset = replyViewModel::resetToolbar,
        onCreateVote = replyViewModel::createVote,
        onDismissVoteCreation = replyViewModel::dismissVoteCreation,
        payeeUid = replyViewModel::receiveCodePayeeUid,
        onInsertReceiveCode = replyViewModel::insertReceiveCode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    postUrl: String,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** [onRetry] as a pull gesture; separate so the view model can route it to its own indicator. */
    onPullRefresh: () -> Unit = onRetry,
    /** The same gesture at the other end of a finished thread — see [PostDetailViewModel.refreshTail]. */
    onRefreshTail: () -> Unit = {},
    /** Re-fetches the given page in place — the 刷新 menu item, aimed at the page on screen. */
    onRefreshPage: (Int) -> Unit = { onRetry() },
    onLoadPage: (Int) -> Unit = { onLoadMore() },
    /**
     * Fetches a page adjoining the loaded ones without moving the reader — the step controls scroll
     * into it themselves. See [PostDetailViewModel.extendToPage].
     */
    onExtendToPage: (Int) -> Unit = onLoadPage,
    /** Scrolls to a floor, fetching its page first when that floor is not loaded. */
    onJumpToFloor: (String) -> Unit = {},
    onScrollHandled: () -> Unit = {},
    /** Reports where the reader is, so a later visit can offer to come back to it. */
    onReadingPositionChange: (Int, String?) -> Unit = { _, _ -> },
    /** Returns to where a previous visit left off; only reachable when there was one. */
    onResumeReading: () -> Unit = {},
    showBackButton: Boolean = true,
    /** Opens the sign-in page. Separate from [onOpenBrowser] because "登录" is not "看看网页版". */
    onSignIn: () -> Unit = { onOpenBrowser(postUrl) },
    /** Clears a Cloudflare challenge on this thread's own URL. */
    onVerify: (String) -> Unit,
    /** `null` opens an empty reply; a floor addresses one (6d). The editor itself is hosted by the route. */
    onReply: (FloorReference?) -> Unit = {},
    /** Appends one more 引用 block to whatever the editor already holds. */
    onQuote: (FloorReference) -> Unit = {},
    /**
     * 编辑. Offered only where [PostContent.isMine] says the site itself would offer it, which is
     * also the only case where the endpoint would accept the write.
     */
    onEdit: (PostEditTarget) -> Unit = {},
    /** Spends one mark on a floor. Confirmation, where the site has one, happens before this. */
    onReact: (Long, ReactionAction) -> Unit = { _, _ -> },
    /** Asked for when a 投喂 confirmation opens, so it can say whether this one is free. */
    onLoadFreeChickenLegs: () -> Unit = {},
    onReactionFailureShown: () -> Unit = {},
    /** Collects the thread, or takes it out. Whole-thread, so only the opening post offers it. */
    onCollect: () -> Unit = {},
    onCollectFailureShown: () -> Unit = {},
    /**
     * Draws the votes embedded in the body and the comments.
     *
     * Supplied by the navigation layer, which is the only place that can reach [AppContainer] to
     * build a per-vote ViewModel. Defaults to nothing, which is what a preview or a screen test wants.
     */
    voteContent: @Composable (Long) -> Unit = {},
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)? = null,
    /** Hides the bottom toolbar while the editor covers it. */
    replyOpen: Boolean = false,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var confirmTarget by remember { mutableStateOf<ReactionConfirm?>(null) }

    /** The floor whose 1c panel is open — from its ⋯, or a long press on its card. */
    var floorActions by remember { mutableStateOf<FloorActions?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showPageSheet by remember { mutableStateOf(false) }
    var pageToolbarExpanded by rememberSaveable { mutableStateOf(true) }
    val density = LocalDensity.current

    /**
     * The room the thread keeps below itself: the tallest [DetailBottomActions] has measured to.
     *
     * Not a written-down number, because there is no single right one to write. The rail stands two
     * keys taller expanded than retracted, and the reply FAB grows with the reader's text scale, so
     * the controls are anywhere between roughly 140dp and 240dp tall depending on the state and the
     * phone. Keyed on the density so a change of text scale measures again from scratch rather than
     * keeping a mark set at the old one.
     *
     * The tallest rather than the current height, and that is the part that matters on a thread
     * short enough to fit one screen. Retracting the rail is a scroll gesture, and this room is what
     * makes such a thread scrollable at all — so following the height back down would shorten the
     * list mid-drag, bounce the reader to a top they never left, and unfold the rail again over the
     * floor it had just moved off. The high-water mark keeps that loop from existing: a long thread
     * ends a rail's-worth of air above a retracted rail, which is the same air the reader sees the
     * moment they scroll back up and it unfolds.
     *
     * [ThreadBottomBarRoom] is the first frame's answer, before there is anything to measure.
     */
    var bottomActionsHeight by remember(density) { mutableStateOf(ThreadBottomBarRoom) }
    val collapsedTitleThreshold = with(LocalDensity.current) { 72.dp.roundToPx() }
    val showCollapsedTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapsedTitleThreshold
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    // `canScrollBackward` is the predicate itself, already maintained by LazyListState as plain
    // state — no derivedStateOf and no per-frame read of the two scroll fields.
    val atListTop = !listState.canScrollBackward
    LaunchedEffect(shouldLoadMore, state.comments.size) {
        if (shouldLoadMore) onLoadMore()
    }

    // The page the reader is looking at, not the furthest page fetched. Pages already in the list
    // are navigated by scrolling; only pages outside the loaded slice involve the network.
    val visiblePage by remember(state.commentPages, state.body != null, state.firstLoadedPage) {
        derivedStateOf {
            val commentIndex = listState.firstVisibleItemIndex - state.headerItemCount
            if (commentIndex < 0) {
                state.firstLoadedPage
            } else {
                state.commentPages.getOrNull(commentIndex)
                    ?: state.commentPages.lastOrNull()
                    ?: state.firstLoadedPage
            }
        }
    }

    // The topmost floor on screen, which is what makes a return exact rather than page-accurate. Null
    // above the first comment — the title and the opening post belong to no page's floors.
    val visibleFloor by remember(state.comments, state.body != null) {
        derivedStateOf {
            state.comments
                .getOrNull(listState.firstVisibleItemIndex - state.headerItemCount)
                ?.floor
        }
    }
    // Two gates, both against overwriting a real read's place with a visit that never happened. There
    // has to be a thread to be positioned in — an empty screen mid-fetch reports page 1 — and the
    // reader has to have got somewhere: every thread parks at its top on open, so opening one and
    // backing straight out would otherwise reset it to page 1.
    val positionWorthRecording = state.hasContent && (visiblePage > 1 || !atListTop)
    LaunchedEffect(visiblePage, visibleFloor, positionWorthRecording) {
        if (positionWorthRecording) onReadingPositionChange(visiblePage, visibleFloor)
    }

    // The state as it is *now*, for the waits below: `state` is this composition's value and a
    // coroutine holding it would never see the floors it is waiting for.
    val latestState by rememberUpdatedState(state)

    /** Set while 到最新 is waiting for the last page it just asked for. */
    var pendingBottom by remember { mutableStateOf(false) }

    /**
     * 上一页 / 下一页 on a thread that is also one continuous scroll, in the same three cases the feed
     * has: scroll where it can, read on where the page adjoins, and jump only where it does not.
     *
     * The page next to the loaded ones was the teleport. It is fetched without replacing anything —
     * that part was always right — but the landing went through [PostDetailUiState.pendingScroll],
     * which snaps, because that path exists for *arriving*: a notification about floor #127 should
     * put the reader on it, not scroll them there from wherever they were. Stepping a page is the
     * opposite gesture, so it asks for the floors without a pending scroll and walks into them.
     */
    fun goToPage(target: Int) {
        scope.launch {
            val index = state.firstIndexOfPage(target)
            if (index != null && target in state.firstLoadedPage..state.lastLoadedPage) {
                listState.animateScrollToItem(index)
                return@launch
            }
            val adjoins = target == state.lastLoadedPage + 1 || target == state.firstLoadedPage - 1
            if (adjoins && target in 1..state.totalPages) {
                // Asked for first, then walked to: the fetch and the scroll to the end the new floors
                // will join are the same half-second, and ordering them the other way spends the
                // animation before the request has left.
                onExtendToPage(target)
                listState.animateScrollToItem(if (target > state.lastLoadedPage) state.lastItemIndex else 0)
                val arrived =
                    withTimeoutOrNull(PAGE_WAIT_MILLIS) {
                        snapshotFlow {
                            latestState.firstIndexOfPage(target)
                                ?.takeIf { target in latestState.firstLoadedPage..latestState.lastLoadedPage }
                        }.filterNotNull().first()
                    }
                if (arrived != null) {
                    listState.animateScrollToItem(arrived)
                    return@launch
                }
            }
            onLoadPage(target)
        }
    }

    /**
     * 到最新 — the newest floor, which is the foot of the last page rather than its head.
     *
     * Separate from 最后一页 because standing on that page and standing at the end of it are two
     * different places, and on a thread being replied to it is the second one people mean. The flag
     * outlives the fetch: the floors are not here yet when the last page has to be asked for, so the
     * scroll waits for the emission that carries them.
     */
    fun goToLatest() {
        val last = state.totalPages.coerceAtLeast(1)
        if (last in state.firstLoadedPage..state.lastLoadedPage) {
            scope.launch { listState.animateScrollToItem(state.lastItemIndex) }
        } else {
            pendingBottom = true
            onLoadPage(last)
        }
    }
    LaunchedEffect(pendingBottom, state.lastLoadedPage, state.comments.size) {
        if (!pendingBottom) return@LaunchedEffect
        if (state.lastLoadedPage < state.totalPages || state.comments.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(state.lastItemIndex)
        pendingBottom = false
    }

    /**
     * 点标题看正文 — the way back to the opening post from a read that started past it.
     *
     * Offered only when the body is genuinely absent, which is a thread opened straight onto a later
     * page and never cached before: NodeSeek serves the opening post on page 1 alone. Page 1 is then
     * fetched like any other page, and [firstIndexOfPage] lands the scroll on the top of the list
     * rather than on that page's first floor, because the post is what was asked for.
     *
     * "Genuinely absent" means floors arrived and the post was not among them — not merely that
     * nothing has arrived yet. A thread still loading has no body either, and this used to offer the
     * button to it; nobody saw that until the loading state started drawing the real header, at
     * which point the button appeared for the length of the fetch and then vanished, dropping
     * everything under it — including an avatar still settling out of its flight — by its own
     * height.
     *
     * Same answer the site gives — its title is a link to `/post-703863-1` — rather than a control of
     * our own, so a reader who knows the web knows this one.
     */
    val openOriginalPost: (() -> Unit)? =
        if (state.body == null && state.comments.isNotEmpty()) ({ goToPage(1) }) else null

    // Keyed on the pages themselves rather than on how many comments there are: a jump swaps one page
    // of ten floors for another page of ten floors, so a count would not change and the effect would
    // never re-run against the content it was waiting for.
    LaunchedEffect(state.pendingScroll, state.commentPages) {
        val target = state.pendingScroll ?: return@LaunchedEffect
        // The fetch has finished but the new comments may not have flowed out of Room yet; wait for
        // the emission that carries them rather than scrolling to a stale end-of-list.
        if (target.page !in state.firstLoadedPage..state.lastLoadedPage) return@LaunchedEffect
        // Holding the page and holding its floors are not the same thing, and page 1 is where they
        // come apart: an empty thread on its very first frame already reports 1..1, so a notification
        // about a floor on page 1 used to be answered against a list with nothing in it — scrolled to
        // the top, marked handled, and gone by the time the floors arrived. Waiting for a comment
        // from that page or later covers the deleted-page case too, where its own never turn up.
        if (state.commentPages.none { it >= target.page }) return@LaunchedEffect
        // The floor when the site named one and it is on the page; otherwise the page's own start.
        // A floor can be missing from the page it was computed for — it was deleted, or the thread
        // was renumbered under it — and landing on the right page beats not moving at all.
        val index = target.floor?.let { state.indexOfFloor(it) }
            ?: state.firstIndexOfPage(target.page)
            ?: (state.headerItemCount + state.comments.size - 1).coerceAtLeast(0)
        listState.scrollToItem(index)
        onScrollHandled()
    }
    /*
     * The nested-scroll state drives the toolbar; only the top pins it open. The foot of the thread
     * deliberately does not: reaching the end is where the reader is *reading*, and a bar that
     * unfolds itself there is furniture arriving over the last floor rather than a control asked for.
     */
    val toolbarExpanded =
        pageToolbarExpanded ||
            atListTop ||
            state.error != null ||
            showPageSheet

    /**
     * One mark on one floor, from wherever it was asked for — the floor's own row, or a tile of its
     * 1c panel. Both go through the same gates, so neither can spend what the other would have asked
     * about first.
     */
    val reactTo: (PostContent, ReactionAction) -> Unit = { content, action ->
        val commentId = content.commentId
        when {
            // Same rule as the editor: the account has to exist before the action, not after a
            // rejection that also spent the tap.
            !state.isSignedIn -> onSignIn()

            commentId == null -> Unit

            // 点赞 costs nothing and the site does not confirm it either.
            action == ReactionAction.Upvote -> onReact(commentId, action)

            else -> {
                confirmTarget = ReactionConfirm(content, commentId, action)
                if (action == ReactionAction.ChickenLeg) onLoadFreeChickenLegs()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                title = state.title,
                showTitle = showCollapsedTitle,
                onTitleClick = openOriginalPost,
                postUrl = postUrl,
                onBack = onBack,
                onOpenInBrowser = { onOpenBrowser(postUrl) },
                onRefresh = { onRefreshPage(visiblePage) },
                showBackButton = showBackButton,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val error = state.error
            when {
                /*
                 * Only where nothing told us anything about this thread — a notification, a deep
                 * link, a cold start into a restored destination.
                 *
                 * A thread opened from a list does *not* come through here, even while it is still
                 * loading. It goes straight to [ThreadList], which draws what the list already knew
                 * and grey bars for the rest. Sending it here instead would put the loading state
                 * and the thread in two different subtrees, and swapping one for the other disposes
                 * the four shared elements mid-landing and flies them again.
                 */
                !state.hasContent && state.isLoading && state.preview == null ->
                    UnopenedThreadSkeleton()

                !state.hasContent && error != null ->
                    SiteErrorState(
                        error = error,
                        onRetry = onRetry,
                        // A locked thread is fixed by signing in, not by loading it again in a
                        // browser; a challenge is fixed on this thread's own URL. Both are named
                        // rather than folded into [onOpenBrowser] by a `when` here — picking the
                        // recovery per error is SiteErrorState's job, and the copy of that decision
                        // this used to hold is exactly the copy that goes stale.
                        onOpenBrowser = { onOpenBrowser(postUrl) },
                        onVerify = onVerify,
                        onSignIn = onSignIn,
                        // 等级不足 has no action that clears it, so the way out is the only button —
                        // and this screen, unlike a tab root, always has somewhere to go back to.
                        onBack = onBack.takeIf { showBackButton },
                    )

                else ->
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onPullRefresh,
                    ) {
                        // The same gesture at the foot of a thread the site has no more pages of:
                        // where auto-append stops, dragging on re-reads the last page and brings in
                        // the floors posted since. Disabled while a next page exists, because there
                        // the drag already means "load it".
                        BottomPullToRefreshBox(
                            isRefreshing = state.isRefreshingTail,
                            onRefresh = onRefreshTail,
                            enabled = state.hasContent && !state.hasNextPage && !state.isAppending,
                            // Clear of the floating controls rather than under them: the same room
                            // the list's last floor keeps puts the spinner where it can be seen.
                            indicatorBottomPadding = bottomActionsHeight,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            ThreadList(
                                state = state,
                                listState = listState,
                                bottomRoom = bottomActionsHeight,
                                onOpenOriginalPost = openOriginalPost,
                                onOpenBrowser = onLinkClick,
                                onImageClick = onImageClick,
                                onJumpToFloor = { floor ->
                                    // A quote can point anywhere in the thread, including pages nobody
                                    // has opened. Scroll when it is here, fetch its page when it is not.
                                    val index = state.indexOfFloor(floor)
                                    if (index != null) {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    } else {
                                        onJumpToFloor(floor)
                                    }
                                },
                                onReact = reactTo,
                                onOpenFloorActions = { floorActions = it },
                                onReplyToFloor = onReply,
                                onQuoteFloor = onQuote,
                                onEditFloor = { target ->
                                    // Same gate as everything else that writes: an expired session is
                                    // discovered before the editor opens, not after a save is refused.
                                    if (state.isSignedIn) onEdit(target) else onSignIn()
                                },
                                onAuthorClick = onAuthorClick,
                                // Same gate as the editor and the marks: the account has to exist before
                                // the action, not after a rejection that also spent the tap.
                                onCollect = { if (state.isSignedIn) onCollect() else onSignIn() },
                                voteContent = voteContent,
                                stardustContent = stardustContent,
                                modifier =
                                Modifier.floatingToolbarVerticalNestedScroll(
                                    expanded = toolbarExpanded,
                                    onExpand = { pageToolbarExpanded = true },
                                    onCollapse = { pageToolbarExpanded = false },
                                ),
                            )
                        }
                    }
            }

            // A jump replaces the whole list, so the append spinner at its foot would be pointing at
            // content that is on its way out. This says "a different page is coming" where the reader
            // is already looking — and it is the only feedback between the tap and the new page.
            // A pull-to-refresh is the one load excused: its own indicator is already saying it.
            if (state.isLoading && !state.isRefreshing && state.hasContent) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).describedAsLoading(),
                )
            }

            if (state.hasContent && !replyOpen) {
                DetailBottomActions(
                    toolbarExpanded = toolbarExpanded,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { goToPage((visiblePage - 1).coerceAtLeast(1)) },
                    onNext = { goToPage((visiblePage + 1).coerceAtMost(state.totalPages)) },
                    onPageClick = { showPageSheet = true },
                    onReply = { onReply(null) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .onSizeChanged { size ->
                            val measured = with(density) { size.height.toDp() }
                            if (measured > bottomActionsHeight) bottomActionsHeight = measured
                        },
                )
            }
        }
    }

    confirmTarget?.let { target ->
        ReactionConfirmDialog(
            target = target,
            freeChickenLegs = state.freeChickenLegs,
            onDismiss = { confirmTarget = null },
            onConfirm = {
                confirmTarget = null
                onReact(target.commentId, target.action)
            },
        )
    }

    floorActions?.let { target ->
        // The floor as it is now, not as it was when the panel opened: a mark landing while the
        // panel is up has to turn its tile to 已表态 rather than leave it offering a second spend.
        val live = state.contentWithId(target.content.commentId) ?: target.content
        FloorActionSheet(
            content = live,
            actions = target,
            pending = state.pendingReactionFor(live),
            freeChickenLegs = state.freeChickenLegs,
            onDismiss = { floorActions = null },
            onReact = { action ->
                floorActions = null
                reactTo(live, action)
            },
        )
    }
    // The 投喂 tile says whether today's feed is free, which only the site knows. Asked for as the
    // panel opens — the same once-per-thread request the confirmation makes — and only for an
    // account that could spend one.
    val actionsOpen = floorActions != null
    LaunchedEffect(actionsOpen) {
        if (actionsOpen && state.isSignedIn) onLoadFreeChickenLegs()
    }

    // The site's own sentence is the whole value here — "鸡腿不足", "已经进行过加鸡腿操作" — so it is
    // shown verbatim, and our wording only stands in for the failures that never reached the site.
    //
    // A wall is the exception, and the reason this goes through [SiteErrorSnackbar]: Cloudflare and
    // a signed-out account send no sentence and are not cleared by pressing again, so they get the
    // control that does clear them. No 重试 — a mark is spent on one floor, and this far from the
    // tap there is nothing left to name.
    val failure = state.reactionFailure
    SiteErrorSnackbar(
        error = failure?.error,
        snackbarHostState = snackbarHostState,
        onShown = onReactionFailureShown,
        detail = failure?.detail,
        onVerify = onVerify,
        onSignIn = onSignIn,
    )

    // Same treatment for the star, kept separate so a refused collection and a refused mark cannot
    // clear each other's message.
    val collectFailure = state.collectFailure
    SiteErrorSnackbar(
        error = collectFailure?.error,
        snackbarHostState = snackbarHostState,
        onShown = onCollectFailureShown,
        detail = collectFailure?.detail,
        onVerify = onVerify,
        onSignIn = onSignIn,
        onRetry = onCollect,
    )

    if (showPageSheet) {
        val loadedFloors = state.comments.size + if (state.body != null) 1 else 0
        // "上次阅读" is the place a previous visit left behind when there is one, and otherwise the
        // far end of this session's own reading. Both are "where I had got to"; only the first
        // survives closing the thread, and a thread opened for the first time has neither until it
        // has been scrolled.
        val resume = state.resumePosition
        PageJumpSheet(
            page = visiblePage,
            totalPages = state.totalPages,
            note = stringResource(Res.string.post_page_progress, loadedFloors),
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                goToPage(target.coerceIn(1, state.totalPages.coerceAtLeast(1)))
            },
            // Clamped so a place left in a thread that has since lost pages still resolves to a
            // chip that goes somewhere — the ViewModel lands it on the last page for the same reason.
            resume =
            (resume?.page ?: state.lastLoadedPage)
                .coerceIn(1, state.totalPages.coerceAtLeast(1))
                .takeIf { it != visiblePage }
                ?.let { target ->
                    JumpDestination(
                        label = stringResource(Res.string.page_jump_resume_title),
                        detail =
                        resume?.floor?.let { stringResource(Res.string.page_jump_at_page_floor, target, it) }
                            ?: stringResource(Res.string.page_jump_at_page, target),
                        icon = PlazaIcons.Bookmark,
                        onGo = {
                            showPageSheet = false
                            if (resume != null) onResumeReading() else goToPage(state.lastLoadedPage)
                        },
                    )
                },
            // The thread's newest is the foot of its last page, which is not where 最后一页 lands.
            newest =
            JumpDestination(
                label = stringResource(Res.string.page_jump_latest),
                detail = stringResource(Res.string.page_jump_at_page, state.totalPages.coerceAtLeast(1)),
                icon = PlazaIcons.VerticalAlignBottom,
                onGo = {
                    showPageSheet = false
                    goToLatest()
                },
            ),
            numberEntry = NumberEntry.Floor,
            // The floor's page is arithmetic the site fixes — see NodeSeekSite.COMMENTS_PER_PAGE —
            // and [onJumpToFloor] already fetches that page and lands on the floor, which is the same
            // trip a notification about it makes.
            onGoToFloor = { floor ->
                showPageSheet = false
                onJumpToFloor("#$floor")
            },
        )
    }
}

@Composable
private fun DetailBottomActions(
    toolbarExpanded: Boolean,
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.End,
        // 4dp, not 8: the rail's bottom key already carries 4dp of touch-target slack under its
        // paint, and the two together are the 8dp the design puts between the rail and the FAB.
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PageJumpRail(
            expanded = toolbarExpanded,
            page = page,
            totalPages = totalPages,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageClick = onPageClick,
        )
        // The screen's own FAB rather than the toolbar's: 回复 is the one action here that must stay
        // where the thumb last left it, and Material's toolbar rounds its FAB up to 80dp the moment
        // the bar collapses. Shrinking to an icon is the whole of the change it makes now.
        // Material's own elevation is switched off and the layer's float shadow drawn instead — see
        // [floatShadow]: the FAB is one step above the cards, in the page's hue rather than black.
        ExtendedFloatingActionButton(
            text = { Text(stringResource(Res.string.post_reply_action)) },
            icon = {
                Icon(
                    PlazaIcons.Reply,
                    contentDescription = null,
                )
            },
            onClick = onReply,
            expanded = toolbarExpanded,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.floatShadow(FloatingActionButtonDefaults.extendedFabShape, LocalPlazaLayers.current.shadows),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    showTitle: Boolean,
    /** Jumps to the opening post, for the reads that start on a page without one. Null when there is nothing to jump to. */
    onTitleClick: (() -> Unit)?,
    postUrl: String,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRefresh: () -> Unit,
    showBackButton: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shareText = rememberShareText()
    val copy = rememberClipboardCopy()
    val openOriginalPostLabel = stringResource(Res.string.post_open_original)
    val linkCopied = stringResource(Res.string.post_link_copied)
    val shareLabel = stringResource(Res.string.action_share)

    TopAppBar(
        title = {
            // Clickable only while the bar is actually showing the title: the collapsed bar is where
            // a reader deep in someone else's page 7 meets the title at all, and an invisible tap
            // target over an empty bar is worse than no affordance.
            val jump = onTitleClick?.takeIf { showTitle }
            Text(
                text = if (showTitle) title else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (jump != null) {
                    Modifier.clickable(onClick = jump).semantics {
                        contentDescription = openOriginalPostLabel
                    }
                } else {
                    Modifier
                },
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenInBrowser) {
                Icon(
                    PlazaIcons.OpenInNew,
                    contentDescription = stringResource(Res.string.action_open_in_browser),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // The menu twin of pull-to-refresh, for the reader who is thirty floors down and
                    // not about to scroll back up to pull.
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_refresh)) },
                        onClick = {
                            menuOpen = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_copy_link)) },
                        onClick = {
                            copy("post", postUrl, linkCopied)
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(shareLabel) },
                        onClick = {
                            menuOpen = false
                            shareText("$title\n$postUrl", shareLabel)
                        },
                    )
                }
            }
        },
        // Flush with the page: the bar is part of the grey the cards sit on, not a band of its own.
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** What Material's extended FAB stands at, collapsed or not — the rail is stacked on top of it. */
private val ReplyFabHeight = 56.dp

/**
 * The room to keep below the thread until the floating controls have been measured — one frame.
 *
 * The retracted rail's own arithmetic: the group's 16dp of bottom padding, the FAB, the 4dp gap
 * above it, and the single page key the rail keeps when its arrows are away, plus a line of air. An
 * underestimate on the frame it is used for is invisible; the measurement replaces it before
 * anything is scrolled.
 */
private val ThreadBottomBarRoom =
    Spacing.lg + ReplyFabHeight + Spacing.xs + Sizes.minTouchTarget + Spacing.sm

/**
 * The thread as one scroll.
 *
 * The site paginates comments; this does not. Later pages append into the same list, so the reader
 * never meets a "page 2" boundary — which is the single biggest difference from the mobile web.
 *
 * Drawn as cards on the page (1b): the opening post is one card holding the title as well as the
 * post, and every reply is a card of its own. The gaps between them do the separating the dividers
 * used to.
 */
@Composable
private fun ThreadList(
    state: PostDetailUiState,
    listState: LazyListState,
    /** What the floating controls measured to — see the property that holds it in [PostDetailScreen]. */
    bottomRoom: Dp,
    /** See [PostDetailScreen]'s own — null when the opening post is already in the list. */
    onOpenOriginalPost: (() -> Unit)?,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    onReact: (PostContent, ReactionAction) -> Unit,
    /** Opens a floor's 1c panel — the marks and actions its own row has no room for. */
    onOpenFloorActions: (FloorActions) -> Unit,
    onReplyToFloor: (FloorReference?) -> Unit,
    onQuoteFloor: (FloorReference) -> Unit,
    onEditFloor: (PostEditTarget) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding =
        PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = Spacing.xs, bottom = bottomRoom),
        verticalArrangement = Arrangement.spacedBy(LayerCardGap),
        modifier = modifier
            .fillMaxHeight()
            .readableWidth(),
    ) {
        /*
         * Present whether or not the thread has arrived — which is the whole reason this is one item
         * and not an `item` guarded by `state.body != null`.
         *
         * The title, the avatar and the author's name inside it are shared elements, still settling
         * out of their flight from a feed row while the thread is on its way. A guard here would
         * dispose them the instant the body landed and compose fresh ones in a new subtree; the
         * shared-element machinery reads that as a *second* transition and flies them again, from
         * wherever the new node happened to be measured first. Same call site, changing arguments,
         * and nothing moves. See [ThreadOpeningPost].
         *
         * The title used to be an item of its own above this one. It moved inside when the opening
         * post became a card, because a card cannot be split across two items without a seam where
         * one item's shadow falls over the other's paint.
         */
        item(key = "body") {
            val body = state.body
            // The opening post is always on page 1, wherever the reader currently is.
            val onEdit = body?.commentId
                ?.takeIf { body.isMine }
                ?.let { id ->
                    {
                        onEditFloor(
                            PostEditTarget(
                                postId = state.postId,
                                commentId = id,
                                page = 1,
                                isOpeningPost = true,
                            ),
                        )
                    }
                }
            ThreadOpeningPost(
                title = state.title,
                isAwarded = state.isAwarded,
                onOpenOriginalPost = onOpenOriginalPost,
                body = body,
                preview = state.preview,
                postId = state.postId,
                showBlockedContent = state.showBlockedContent,
                onOpenBrowser = onOpenBrowser,
                onImageClick = onImageClick,
                onJumpToFloor = onJumpToFloor,
                pendingReaction = body?.let { state.pendingReactionFor(it) },
                onReact = { action -> body?.let { onReact(it, action) } },
                onAuthorClick = onAuthorClick,
                // The opening post keeps the site's own set: no 回复 or 引用 of it, 编辑 on your own.
                onMore = body?.let { { onOpenFloorActions(FloorActions(it, onReply = null, onQuote = null, onEdit)) } },
                collected = state.collected,
                collectionCount = state.collectionCount,
                collectPending = state.collectPending,
                onCollect = onCollect,
                voteContent = voteContent,
                stardustContent = stardustContent,
            )
        }

        if (state.hasContent) {
            item(key = "comments-header") {
                CommentsHeader(count = state.comments.size)
            }
        } else {
            // "共 0 条回复" would be a claim, and nobody has counted yet.
            item(key = "comments-skeleton") { CommentSkeletons() }
        }

        itemsIndexed(
            items = state.comments,
            key = { index, comment -> comment.commentId ?: -index.toLong() - 1 },
        ) { index, comment ->
            // The site page this floor came from, which is the only page whose `__config__` carries
            // its Markdown. Read from the index rather than derived from the floor number: the list
            // is one scroll over several pages, and the two disagree as soon as a floor above has
            // been deleted.
            val onEdit = comment.commentId
                ?.takeIf { comment.isMine }
                ?.let { id ->
                    {
                        onEditFloor(
                            PostEditTarget(
                                postId = state.postId,
                                commentId = id,
                                page = state.commentPages.getOrNull(index) ?: state.lastLoadedPage,
                                isOpeningPost = false,
                            ),
                        )
                    }
                }
            // 回复 gives way to 编辑 on this account's own floor, which is what the site does.
            val onReply = { onReplyToFloor(comment.toFloorReference()) }.takeIf { onEdit == null }
            val onQuote = { comment.toFloorReference()?.let(onQuoteFloor) ?: Unit }
            BlockAware(content = comment, revealed = state.showBlockedContent) {
                CommentRow(
                    comment = comment,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = onImageClick,
                    onJumpToFloor = onJumpToFloor,
                    pendingReaction = state.pendingReactionFor(comment),
                    onReact = { action -> onReact(comment, action) },
                    onReply = onReply,
                    onEdit = onEdit,
                    onMore = { onOpenFloorActions(FloorActions(comment, onReply, onQuote, onEdit)) },
                    onAuthorClick = onAuthorClick,
                    voteContent = voteContent,
                    stardustContent = stardustContent,
                )
            }
        }

        if (state.isAppending) {
            item(key = "appending") { AppendSpinner() }
        }
    }
}

/**
 * Draws [floor], or the one-line stand-in the site's block list has earned it.
 *
 * A blocked floor is *kept*, not dropped: NodeSeek sends it and hides it, so the app collapses it the
 * same way. Dropping it would renumber nothing — floors carry their own numbers — but it would leave
 * a reply quoting #12 pointing at a floor that, as far as the reader can tell, never existed.
 *
 * [revealed] is 临时显示被屏蔽内容 and opens every floor at once; the row's own 显示 opens exactly one
 * and forgets it when the item scrolls out of the composition, which is the lighter of the two ways
 * to answer "what did they actually say".
 */
@Composable
private fun BlockAware(
    content: PostContent,
    revealed: Boolean,
    floor: @Composable () -> Unit,
) {
    var openedHere by rememberSaveable(content.commentId) { mutableStateOf(false) }
    if (!content.isBlocked || revealed || openedHere) {
        floor()
    } else {
        // A card like the floor it stands in for, but a slim one: it is a line, not a post.
        LayerCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = 2.dp, bottom = 2.dp),
        ) {
            BlockedFloorRow(floor = content.floor, onShow = { openedHere = true })
        }
    }
}

@Composable
private fun BlockedFloorRow(
    floor: String?,
    onShow: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            PlazaIcons.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = floor?.let { "$it · " }.orEmpty() + stringResource(Res.string.post_comment_blocked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShow) {
            Text(stringResource(Res.string.post_comment_blocked_show))
        }
    }
}

/**
 * Full-width opening punctuation draws its ink in the right half of the em box, so a title like
 * 「【出】92折出SG落地机」 starts a good half character right of everything under it. That gap was
 * invisible while the opening post sat in its own container — the title and the container were on
 * different left edges anyway — and became the most obvious misalignment on the screen once they
 * shared one. Hanging the first line out by half an em puts the bracket's ink back on the margin.
 */
private val HANGING_PUNCTUATION = setOf('【', '「', '『', '《', '〈', '（', '〔', '“', '‘')

private fun TextStyle.hangLeadingPunctuation(text: String): TextStyle =
    if (text.firstOrNull() in HANGING_PUNCTUATION) {
        copy(textIndent = TextIndent(firstLine = fontSize * -0.5f))
    } else {
        this
    }

/** The top of the opening post's card: its tags, then the title under them, as 1b stacks them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHeader(
    title: String,
    postId: Long,
    body: PostContent?,
    isAwarded: Boolean,
    /**
     * What the list that opened this thread already said about it, drawn wherever [body] cannot yet.
     * See [io.github.nodyssey.PostDetailKey.preview].
     */
    preview: ThreadPreview?,
    /** Fetches page 1 and scrolls to the opening post; null when it is already on screen. */
    onOpenOriginalPost: (() -> Unit)? = null,
) {
    Column(
        modifier =
        if (onOpenOriginalPost != null) Modifier.clickable(onClick = onOpenOriginalPost) else Modifier,
    ) {
        val category = body?.categoryTitle ?: preview?.categoryTitle
        if (category != null || isAwarded) {
            FlowRow(
                modifier = Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // The slug comes from the preview even once the body has arrived: the thread page's
                // own markup carries the board's name but not its slug, and the tag colours read the
                // slug first. Without it a tag would change colour under the reader the moment the
                // network answered — and it is the same tag, still in flight from the row.
                BoardTag(
                    title = category,
                    slug = preview?.categorySlug,
                    modifier = Modifier.sharedThreadBoard(postId),
                )
                // A labelled tag rather than the list's diamond: here there is room to name the thing.
                if (isAwarded) {
                    TonalTag(
                        text = stringResource(Res.string.post_badge_awarded),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        Text(
            text = title,
            style = PostTitle.hangLeadingPunctuation(title),
            color = MaterialTheme.colorScheme.onSurface,
            // Where the row's title lands. This header is also what the skeleton draws while the
            // thread loads, so the landing place exists from the first frame of the flight rather
            // than appearing once the network answers.
            modifier = Modifier.sharedThreadTitle(postId),
        )
        if (onOpenOriginalPost != null) {
            // The title alone carries no affordance on a phone — no hover, no underline, nothing to
            // say it goes anywhere — and this is a screen the reader landed on knowing nothing about
            // where the post went. So the tap is spelled out, and the title stays tappable too.
            TextButton(
                onClick = onOpenOriginalPost,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                modifier = Modifier.offset(x = -Spacing.sm),
            ) {
                Icon(
                    PlazaIcons.Article,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(Res.string.post_open_original),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

/**
 * Who wrote a thread, at the size the opening post states it.
 *
 * Extracted because the loading state draws it too, from what the list already knew, and the two
 * have to agree to the pixel: the avatar and the name are mid-flight from a feed row when the
 * loading state is on screen, and they land again — without moving — when the thread replaces it.
 * Two hand-matched copies of this geometry would drift, and the drift would show as a twitch at the
 * exact moment the reader is watching.
 */
@Composable
private fun ThreadAuthorRow(
    postId: Long,
    avatarUrl: String?,
    authorName: String,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    subtitle: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        UserAvatar(
            url = avatarUrl,
            name = authorName,
            size = Sizes.avatarOriginalPost,
            modifier = Modifier.sharedThreadAvatar(postId),
        )
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .sharedThreadAuthor(postId),
                )
                badges()
            }
            subtitle()
        }
    }
}

/**
 * The opening post's card (1b) — tags, title, author, body and its own action row — and, before the
 * thread arrives, the part of it the list that opened this thread already knew.
 *
 * A null [body] is the thread still loading, and this draws it rather than handing the screen over
 * to a separate skeleton, because the title, the avatar and the author's name here are shared
 * elements landing out of a flight from a feed row. Two composables would mean two sets of nodes,
 * and swapping one for the other the moment the body arrived would look to the shared-element
 * machinery like a fresh transition — it would fly them a second time, from wherever the replacement
 * was first measured. One call site whose arguments change is the whole fix: nothing is disposed, so
 * nothing moves.
 */
@Composable
private fun ThreadOpeningPost(
    title: String,
    isAwarded: Boolean,
    onOpenOriginalPost: (() -> Unit)?,
    body: PostContent?,
    preview: ThreadPreview?,
    postId: Long,
    showBlockedContent: Boolean,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onAuthorClick: (Long) -> Unit,
    /** Opens the post's 1c panel; null until there is a post to act on. */
    onMore: (() -> Unit)?,
    /* Collection is whole-thread, so it belongs on the opening post and nowhere else. */
    collected: Boolean?,
    collectionCount: Int?,
    collectPending: Boolean,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
) {
    // [BlockAware]'s job, done here rather than around this composable: a wrapper that swapped the
    // whole opening post out would take the shared elements with it, which is the thing this
    // arrangement exists to prevent. A blocked author is the one case where they *should* go — the
    // point of blocking is that the name and the face are not shown. The title stays: it is the
    // thread's, not the author's.
    var revealedHere by rememberSaveable(body?.commentId) { mutableStateOf(false) }
    val blocked = body != null && body.isBlocked && !showBlockedContent && !revealedHere

    LayerCard(
        modifier = Modifier
            .fillMaxWidth()
            .floorLongPress(onMore.takeUnless { blocked }),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = 18.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ThreadHeader(
            title = title,
            postId = postId,
            body = body,
            isAwarded = isAwarded,
            preview = preview,
            onOpenOriginalPost = onOpenOriginalPost,
        )
        if (body != null && blocked) {
            BlockedFloorRow(floor = body.floor, onShow = { revealedHere = true })
            return@LayerCard
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The identity block opens the author's space; the floor label stays outside it.
            ThreadAuthorRow(
                postId = postId,
                avatarUrl = body?.avatarUrl ?: preview?.avatarUrl,
                authorName = body?.authorName ?: preview?.authorName.orEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .authorClickable(body?.authorUid, onAuthorClick),
                badges = { if (body != null) FloorBadges(body) },
                // A bar the height of the line, until there is a time to put in it. The list knows
                // when the thread was last active, which is not when it was posted.
                subtitle = { if (body != null) FloorTimeLine(body) else MetaLinePlaceholder() },
            )
            body?.floor?.let { FloorLabel(it) }
        }

        if (body == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(0.98f, 0.94f, 0.99f, 0.62f).forEach { SkeletonBar(it, 14.dp) }
            }
            return@LayerCard
        }

        if (body.nodes.isEmpty()) {
            Text(
                text = stringResource(Res.string.post_body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PostRichContent(
                nodes = body.nodes,
                onLinkClick = onOpenBrowser,
                onImageClick = onImageClick,
                onQuoteRefClick = { onJumpToFloor(it.floor) },
                textStyle = MaterialTheme.typography.bodyLarge,
                voteContent = voteContent,
                stardustContent = stardustContent,
            )
        }
        UserSignature(
            nodes = body.signatureNodes,
            bodyStyle = MaterialTheme.typography.bodyLarge,
            onOpenBrowser = onOpenBrowser,
            onImageClick = onImageClick,
            onJumpToFloor = onJumpToFloor,
        )
        OpeningPostActions(
            reactions = body.reactions,
            pending = pendingReaction,
            onReact = onReact,
            collected = collected,
            collectionCount = collectionCount,
            collectPending = collectPending,
            onCollect = onCollect,
            onMore = onMore,
        )
    }
}

@Composable
private fun CommentsHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.post_comments_header, count),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            modifier = Modifier.weight(1f),
        )
        // Where 1b draws a 从早到晚 order control. NodeSeek has no comment order to choose — floors
        // come oldest first and nothing else — so the slot keeps saying how the list continues.
        Text(
            text = stringResource(Res.string.post_auto_paging),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (count == 0) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.post_comments_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.post_comments_empty_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One reply, as its own card (1b).
 *
 * Real replies on this forum are anywhere between two characters and several screens, so the header
 * is one fixed line — avatar, name, badges, time, and the floor number at the far end — and the body
 * runs the card's full width.
 *
 * The foot carries only what a reader does on most floors — 点赞, 投喂, 回复 — and a ⋯ for the rest
 * of the site's set (点踩, 引用), which [FloorActionSheet] lays out with their prices. A long press
 * anywhere on the card outside the text opens the same panel; on the text it selects, as it always has.
 */
@Composable
private fun CommentRow(
    comment: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    /** Null on this account's own floor, where [onEdit] takes its place. */
    onReply: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onMore: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
) {
    LayerCard(
        modifier = Modifier
            .fillMaxWidth()
            .floorLongPress(onMore),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = 14.dp, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The identity block opens the author's space; the floor label stays outside it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier =
                Modifier
                    .weight(1f)
                    .authorClickable(comment.authorUid, onAuthorClick),
            ) {
                UserAvatar(
                    url = comment.avatarUrl,
                    name = comment.authorName,
                    size = Sizes.avatarComment,
                )
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The name gives way first: the badges and the time are short and fixed, a
                    // name is whatever its owner typed.
                    modifier = Modifier.weight(1f, fill = false),
                )
                FloorBadges(comment)
                FloorTimeLine(comment)
            }
            comment.floor?.let { FloorLabel(it) }
        }
        PostRichContent(
            nodes = comment.nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            onQuoteRefClick = { ref -> onJumpToFloor(ref.floor) },
            textStyle = MaterialTheme.typography.bodyMedium,
            voteContent = voteContent,
            stardustContent = stardustContent,
            modifier = Modifier.padding(end = Spacing.md),
        )
        UserSignature(
            nodes = comment.signatureNodes,
            bodyStyle = MaterialTheme.typography.bodyMedium,
            onOpenBrowser = onOpenBrowser,
            onImageClick = onImageClick,
            onJumpToFloor = onJumpToFloor,
            modifier = Modifier.padding(end = Spacing.md),
        )
        CommentFoot(
            reactions = comment.reactions,
            pending = pendingReaction,
            onReact = onReact,
            onReply = onReply,
            onEdit = onEdit,
            onMore = onMore,
        )
    }
}

/**
 * NodeSeek's public Markdown signature, visually separated from the floor's actual content.
 *
 * [bodyStyle] is the style of the floor this signature hangs off, which [asSignature] steps down
 * from — a signature is a footer to *that* text, so it has to stay smaller than it at any reading
 * size rather than sit at a size of its own.
 */
@Composable
private fun UserSignature(
    nodes: List<RichNode>,
    bodyStyle: TextStyle,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) return

    Column(modifier) {
        HorizontalDivider(
            color = LocalPlazaLayers.current.divider,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        PostRichContent(
            nodes = nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            onQuoteRefClick = { onJumpToFloor(it.floor) },
            textStyle = bodyStyle.asSignature().copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** Tappable only when the uid was actually parsed; a dead ripple would promise a screen we cannot open. */
private fun Modifier.authorClickable(uid: Long?, onAuthorClick: (Long) -> Unit): Modifier =
    if (uid == null) this else clickable { onAuthorClick(uid) }

/**
 * A long press on a floor's card opens its 1c panel.
 *
 * Not [LayerCard]'s own `onLongClick`: that makes the card a `combinedClickable`, and a card whose
 * tap does nothing would still ripple on every tap a reader makes while reading. A gesture detector
 * that listens only for the long press leaves the tap alone. Screen readers get the same action
 * through the semantics, beside the ⋯ that is always there.
 */
@Composable
private fun Modifier.floorLongPress(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(onLongPress)
    val label = stringResource(Res.string.post_floor_actions)
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    current()
                },
            )
        }.semantics {
            onLongClick(label) {
                current()
                true
            }
        }
}

/**
 * The opening post's action row (1b): 点赞 and 投喂 as tonal pills with their tallies, then 收藏 and
 * the ⋯ that opens the rest. 点踩 and 编辑 live in the panel; the pills are the two things readers
 * actually do to an opening post.
 */
@Composable
private fun OpeningPostActions(
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    /*
     * Null [collected] is an absence — no fetched page has said which way the bookmark points — and
     * the bookmark is simply not drawn.
     */
    collected: Boolean?,
    collectionCount: Int?,
    collectPending: Boolean,
    onCollect: () -> Unit,
    onMore: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val feedLabel = stringResource(Res.string.post_reaction_feed)
        listOf(ReactionAction.Upvote, ReactionAction.ChickenLeg).forEach { action ->
            val count = reactions?.countOf(action)
            ReactionPill(
                action = action,
                // 点赞 is the one mark with no word beside it in 1b — its thumb is the word.
                text =
                when (action) {
                    ReactionAction.ChickenLeg -> listOfNotNull(feedLabel, count?.toString()).joinToString(" ")
                    else -> count?.toString().orEmpty()
                },
                spent = reactions?.hasSpent(action) == true,
                pending = pending == action,
                // A floor whose tallies we never read is a floor we cannot say is unspent — offering
                // the button there invites a round trip that ends in "已经进行过".
                onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
            )
        }
        Spacer(Modifier.weight(1f))
        if (collected != null) {
            QuietReaction(
                icon = if (collected) PlazaIcons.Bookmark else PlazaIcons.BookmarkBorder,
                label = if (collected) Res.string.post_collected_action else Res.string.post_collect_action,
                count = collectionCount?.toString().orEmpty(),
                selected = collected,
                pending = collectPending,
                onClick = onCollect.takeUnless { collectPending },
            )
        }
        if (onMore != null) FloorMoreButton(onMore)
    }
}

/**
 * 点赞 or 投喂 on the opening post: a tonal pill, filled solid once spent.
 *
 * Spent is drawn, not merely disabled. These marks cannot be undone, so the row has to answer "did I
 * already do this?" at a glance — and in colour rather than by being greyed, because greyed is also
 * what an unusable button looks like to a signed-out reader.
 */
@Composable
private fun ReactionPill(
    action: ReactionAction,
    text: String,
    spent: Boolean,
    pending: Boolean,
    onClick: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val layers = LocalPlazaLayers.current
    // 点赞 wears the primary tone and 投喂 the page's inset grey, as 1b draws them; spent turns
    // each to its own filled colour — the same tertiary 投喂 wears on the 1c panel.
    val (container, content) =
        when (action) {
            ReactionAction.Upvote ->
                if (spent) scheme.primary to scheme.onPrimary else scheme.primaryContainer to scheme.onPrimaryContainer

            else ->
                if (spent) scheme.tertiaryContainer to scheme.onTertiaryContainer else layers.inset to scheme.onSurface
        }
    FilledTonalButton(
        onClick = onClick ?: {},
        enabled = onClick != null && !spent && !pending,
        colors =
        ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (spent) container else container.copy(alpha = DISABLED_PILL_ALPHA),
            disabledContentColor = if (spent) content else content.copy(alpha = DISABLED_PILL_ALPHA),
        ),
        border = layers.cardBorder?.let { BorderStroke(1.dp, it) },
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(ReactionPillHeight),
    ) {
        if (pending) {
            PlazaSpinner(
                modifier = Modifier.describedAsLoading(),
                strokeWidth = 2.dp,
                size = 18.dp,
            )
        } else {
            Icon(action.icon(), contentDescription = stringResource(action.labelRes()), modifier = Modifier.size(20.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * A reply's foot (1b): 点赞 and 投喂 with their tallies on the left, then a quiet 回复 — 编辑 on this
 * account's own floor — and the ⋯ that opens the rest.
 */
@Composable
private fun CommentFoot(
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onReply: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onMore: () -> Unit,
) {
    Row(
        // Each mark is a TextButton, which keeps 12dp of content padding inside its own bounds. Laid
        // out honestly the first icon starts 12dp right of the margin the name and the body sit on;
        // shifting the row back by that much lines the ink up instead.
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = -TEXT_BUTTON_CONTENT_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(ReactionAction.Upvote, ReactionAction.ChickenLeg).forEach { action ->
            QuietReaction(
                icon = action.icon(),
                label = action.labelRes(),
                // No count at all rather than a zero when the page did not carry the tallies: an
                // unread number and "nobody has done this" are different claims.
                count = reactions?.countOf(action)?.toString().orEmpty(),
                spent = reactions?.hasSpent(action) == true,
                pending = pending == action,
                onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
            )
        }
        Spacer(Modifier.weight(1f))
        // The one text button on the row, in the accent: it carries the floor into the editor, which
        // is what 2c's "回复 #12 · nssk" header is showing.
        val (action, icon, label) =
            when {
                onEdit != null -> Triple(onEdit, Icons.Default.Edit, Res.string.post_edit_action)
                onReply != null -> Triple(onReply, PlazaIcons.Reply, Res.string.post_reply_action)
                else -> Triple(null, null, null)
            }
        if (action != null && icon != null && label != null) {
            TextButton(
                onClick = action,
                // The ⋯ beside it brings its own touch slack, so the pair can sit close.
                contentPadding = PaddingValues(horizontal = Spacing.md),
                modifier = Modifier.offset(x = TEXT_BUTTON_CONTENT_INSET),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
        Box(Modifier.offset(x = TEXT_BUTTON_CONTENT_INSET)) { FloorMoreButton(onMore) }
    }
}

/** ⋯ — the way into a floor's 1c panel that does not depend on knowing about the long press. */
@Composable
private fun FloorMoreButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            PlazaIcons.MoreHoriz,
            contentDescription = stringResource(Res.string.post_floor_actions),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `ButtonDefaults.TextButtonContentPadding`'s horizontal inset. */
private val TEXT_BUTTON_CONTENT_INSET = 12.dp

private val ReactionPillHeight = 40.dp

private const val DISABLED_PILL_ALPHA = 0.5f

/** Each mark's glyph, wherever it is drawn — the row, the pill, the 1c tile. */
private fun ReactionAction.icon(): ImageVector =
    when (this) {
        ReactionAction.Upvote -> Icons.Default.ThumbUp
        ReactionAction.ChickenLeg -> NodeSeekIcons.ChickenLeg
        ReactionAction.Dislike -> PlazaIcons.ThumbDown
    }

private fun ReactionAction.labelRes(): StringResource =
    when (this) {
        ReactionAction.Upvote -> Res.string.post_reaction_like
        ReactionAction.ChickenLeg -> Res.string.post_reaction_chicken
        ReactionAction.Dislike -> Res.string.post_reaction_dislike
    }

@Composable
private fun QuietReaction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: StringResource,
    count: String,
    onClick: (() -> Unit)? = null,
    spent: Boolean = false,
    pending: Boolean = false,
    /**
     * On, and still tappable — which is what makes it not [spent].
     *
     * Collection is the only reversible thing on this row, so it needs the "already done" colour
     * without the "and that is final" disabling. Folding it into [spent] would leave a reader who
     * collected a thread unable to un-collect it.
     */
    selected: Boolean = false,
) {
    /*
     * Spent is drawn, not merely disabled. These three cannot be undone, so the row has to answer
     * "did I already do this?" at a glance — and it has to answer it in colour rather than by being
     * greyed, because greyed is also what an unusable button looks like to a signed-out reader.
     */
    TextButton(
        onClick = onClick ?: {},
        enabled = onClick != null && !spent && !pending,
        colors =
        when {
            spent -> ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.primary)

            selected -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)

            // Quiet until used: the accent is kept for what this reader has done, and for 回复.
            else -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        if (pending) {
            PlazaSpinner(
                modifier = Modifier.describedAsLoading(),
                strokeWidth = 2.dp,
                size = 18.dp,
            )
        } else {
            Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(18.dp))
        }
        if (count.isNotEmpty()) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

/** The floor a confirmation is asking about, and what it would spend on it. */
private data class ReactionConfirm(
    val content: PostContent,
    val commentId: Long,
    val action: ReactionAction,
)

/**
 * The one gate in front of an irreversible spend.
 *
 * Only 加鸡腿 and 反对 get one, matching the site: 点赞 costs nothing and is sent on the tap. The body
 * has to name the price, because these are the only two places in the app where reading a thread can
 * cost the reader currency — and 反对 costs *two*, which is the kind of thing a reader discovers
 * afterwards if the dialog only says "确定吗".
 */
@Composable
private fun ReactionConfirmDialog(
    target: ReactionConfirm,
    freeChickenLegs: FreeChickenLegs?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isChicken = target.action == ReactionAction.ChickenLeg
    // Only claim it is free when the site told us so; an unread quota says nothing either way, and
    // "免费" that turns out to have cost a chicken leg is the worse of the two mistakes.
    val free = isChicken && freeChickenLegs != null && freeChickenLegs.remaining > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isChicken) NodeSeekIcons.ChickenLeg else PlazaIcons.ThumbDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(
                    if (isChicken) Res.string.chicken_dialog_title else Res.string.dislike_dialog_title,
                ),
            )
        },
        text = {
            Text(
                when {
                    free ->
                        stringResource(
                            Res.string.chicken_dialog_body_free,
                            target.content.authorName,
                            freeChickenLegs.remaining,
                        )

                    isChicken -> stringResource(Res.string.chicken_dialog_body, target.content.authorName)

                    else -> stringResource(Res.string.dislike_dialog_body)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (isChicken) Res.string.chicken_dialog_confirm else Res.string.dislike_dialog_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/**
 * What a floor's 1c panel can do besides the three marks, as that floor's own row already wired it.
 *
 * Built where the row is, because that is where the answers are: whether this account wrote the
 * floor (编辑 instead of 回复), which site page carries its Markdown, and whether it is the opening
 * post — which the site offers neither 回复 nor 引用 of.
 */
private class FloorActions(
    val content: PostContent,
    val onReply: (() -> Unit)?,
    val onQuote: (() -> Unit)?,
    val onEdit: (() -> Unit)?,
)

/**
 * 楼层互动 (1c): the floor quoted at the top, its three marks as tiles that say what each costs, and
 * the floor's other actions as a list.
 *
 * The set is the site's and fixed — 点赞 / 投喂鸡腿 / 点踩, then 回复 (编辑 on your own floor) and 引用
 * — with 复制正文 the one thing the app adds, because a selection drag across a long floor is the
 * other way to get its text and it is a poor one. A tile only asks for the mark: [onReact] closes
 * the panel and goes through the same gates the row does, so 投喂 and 点踩 still stop at their
 * confirmations and a signed-out reader still goes to sign in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloorActionSheet(
    content: PostContent,
    actions: FloorActions,
    pending: ReactionAction?,
    freeChickenLegs: FreeChickenLegs?,
    onDismiss: () -> Unit,
    onReact: (ReactionAction) -> Unit,
) {
    val layers = LocalPlazaLayers.current
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.post_body_copied)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The page's colour, so the quote, the tiles and the list read as cards on it — the same
        // layering as the thread under the scrim.
        containerColor = layers.page,
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // The panel's head: whose floor this is, and the first line of it, so the reader knows
            // what they are marking.
            QuotePreview(
                title = listOfNotNull(content.authorName, content.floor).joinToString(" · "),
                excerpt = content.nodes.excerpt(),
                leading = { UserAvatar(url = content.avatarUrl, name = content.authorName, size = Sizes.avatarComment) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ReactionAction.Upvote, ReactionAction.ChickenLeg, ReactionAction.Dislike).forEach { action ->
                    val reactions = content.reactions
                    ReactionTile(
                        action = action,
                        count = reactions?.countOf(action),
                        price = action.price(freeChickenLegs),
                        spent = reactions?.hasSpent(action) == true,
                        pending = pending == action,
                        // Same rule as the row: tallies never read are marks we cannot say are unspent.
                        onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val rows =
                buildList {
                    actions.onEdit?.let { add(Triple(Icons.Default.Edit, stringResource(Res.string.post_edit_action), it)) }
                        ?: actions.onReply?.let {
                            add(Triple(PlazaIcons.Reply, stringResource(Res.string.post_reply_to, content.authorName), it))
                        }
                    actions.onQuote?.let {
                        add(Triple(PlazaIcons.FormatQuote, stringResource(Res.string.post_quote_floor), it))
                    }
                    add(
                        Triple(PlazaIcons.ContentCopy, stringResource(Res.string.post_copy_body)) {
                            copy("post", content.nodes.excerpt(), copied)
                        },
                    )
                }
            Column {
                rows.forEachIndexed { index, (icon, label, action) ->
                    GroupedListItem(
                        first = index == 0,
                        last = index == rows.lastIndex,
                        onClick = {
                            onDismiss()
                            action()
                        },
                        leadingContent = { Icon(icon, contentDescription = null) },
                        headlineContent = { Text(label) },
                    )
                }
            }
            Text(
                text = stringResource(Res.string.post_reaction_irreversible),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One mark as a tile: glyph, name and tally, and under them what it costs — 免费, 今日免费 N 次, 扣 2
 * 鸡腿 — or 已表态 once spent. The price is on the tile because the tile is where the choice is made;
 * the confirmation still says it again before anything is spent.
 */
@Composable
private fun ReactionTile(
    action: ReactionAction,
    count: Int?,
    price: String,
    spent: Boolean,
    pending: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, ink) =
        when (action) {
            ReactionAction.Upvote -> scheme.primaryContainer to scheme.onPrimaryContainer
            ReactionAction.ChickenLeg -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            ReactionAction.Dislike -> scheme.surfaceContainerHigh to scheme.onSurface
        }
    TonalTile(
        onClick = onClick ?: {},
        containerColor = container,
        contentColor = ink,
        enabled = onClick != null && !spent && !pending,
        border = LocalPlazaLayers.current.cardBorder?.let { BorderStroke(1.dp, it) },
        contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.sm, top = 14.dp, bottom = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        if (pending) {
            PlazaSpinner(modifier = Modifier.describedAsLoading(), strokeWidth = 2.dp, size = 24.dp)
        } else {
            Icon(action.icon(), contentDescription = null)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(action.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            count?.let {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = if (spent) stringResource(Res.string.post_reaction_spent) else price,
            style = MaterialTheme.typography.labelSmall,
            color = if (action == ReactionAction.Dislike) scheme.onSurfaceVariant else ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What a mark costs, as the tile states it.
 *
 * 投喂 is only called free when the site said today's allowance covers it; an unread quota says the
 * full price, which is what the confirmation says in the same case.
 */
@Composable
private fun ReactionAction.price(freeChickenLegs: FreeChickenLegs?): String =
    when {
        this == ReactionAction.Upvote -> stringResource(Res.string.post_reaction_free)

        this == ReactionAction.ChickenLeg && freeChickenLegs != null && freeChickenLegs.remaining > 0 ->
            stringResource(Res.string.post_reaction_free_today, freeChickenLegs.remaining)

        else -> stringResource(Res.string.post_reaction_cost, chickenLegCost)
    }

/**
 * The header's badge chips: everything the parser read, with 楼主 prepended when the page marked
 * the author as OP some other way (the opening post itself carries no `is-poster` span on page 1
 * of some templates). Capped at three plus a +N chip — see [RoleBadgeRow].
 */
@Composable
private fun FloorBadges(content: PostContent) {
    val opLabel = stringResource(Res.string.post_badge_original_poster)
    val labels =
        if (content.isOriginalPoster && content.badges.none { it == opLabel }) {
            listOf(opLabel) + content.badges
        } else {
            content.badges
        }
    if (labels.isNotEmpty()) RoleBadgeRow(labels)
}

/** The floor's time, and after it the dashed-underline 已编辑 marker (b1 §8). */
@Composable
private fun FloorTimeLine(
    content: PostContent,
    modifier: Modifier = Modifier,
) {
    if (content.createdAtText == null && !content.isEdited) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        content.createdAtText?.let { MetaText(it) }
        if (content.isEdited) {
            if (content.createdAtText != null) MetaText("·")
            EditedMarker(content)
        }
    }
}

/**
 * 已编辑, and after it the moment — `编辑于 5min ago`, the same claim the site prints.
 *
 * The time is dropped rather than invented when the page did not carry one: some floors carry a bare
 * marker, and "编辑于" with nothing after it would read as a truncation.
 */
@Composable
private fun EditedMarker(content: PostContent) {
    val plain = stringResource(Res.string.post_edited)
    val label = content.editedAtText?.let { stringResource(Res.string.post_edited_at, it) } ?: plain
    // The absolute stamp is the accessible name where the page gave one — a relative label read
    // aloud out of context ("5min ago", from a screen opened ten minutes back) dates itself.
    val spoken = content.editedAtTitle?.let { stringResource(Res.string.post_edited_at, it) } ?: label
    val underline = MaterialTheme.colorScheme.outlineVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
        Modifier
            .semantics { contentDescription = spoken }
            .drawBehind {
                // TextDecoration has no dashed variant, so the spec's dashed underline is drawn.
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = underline,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            },
    )
}

/**
 * The context the reply editor opens with.
 *
 * `null` for a floor with no number — an answer that says "回复 #" answers nothing, and the editor
 * handles a missing target perfectly well by opening empty.
 */
private fun PostContent.toFloorReference(): FloorReference? {
    val number = floor?.trimStart('#')?.toIntOrNull() ?: return null
    return FloorReference(
        floor = number,
        author = authorName,
        excerpt = nodes.excerpt(),
        // The absolute timestamp, not the "3小时前" one: the quote outlives the moment it was written.
        postedAt = createdAtTitle,
    )
}

/**
 * The floor's readable text, in full.
 *
 * Deliberately not truncated here: the same string is the chip's label, the preview's quote block
 * *and* the blockquote that goes on the wire, and only the first of those wants to be short. The
 * chip ellipsizes at render time; publishing a quote cut to 40 characters with a `…` would put a
 * mangled quote into a real thread the moment the comment endpoint is wired up.
 */
private fun List<RichNode>.excerpt(): String = mapNotNull { node ->
    when (node) {
        is RichNode.Paragraph -> node.inlines.plainText()
        is RichNode.Heading -> node.inlines.plainText()
        else -> null
    }?.trim()?.takeIf(String::isNotBlank)
}.joinToString("\n")

private fun List<InlineNode>.plainText(): String = joinToString("") { inline ->
    when (inline) {
        is InlineNode.Text -> inline.text
        is InlineNode.Link -> inline.text
        else -> ""
    }
}

/** Tabular figures so `#9` and `#127` sit on the same right edge as the list scrolls. */
@Composable
private fun FloorLabel(floor: String) {
    Text(
        text = floor,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * How long a page step waits at the end of the list for the page it asked for.
 *
 * Long, for the reason the feed's own wait is long: giving up means a jump that replaces the window,
 * and the append spinner is saying what is happening the whole time.
 */
private const val PAGE_WAIT_MILLIS = 15_000L

/**
 * Items in [ThreadList] before the first comment: the comments header, and the opening post's card
 * when there is a body. One less than before the title moved into that card, in both cases — the
 * count for a bodyless thread is exactly as it was relative to the items the list draws.
 */
private val PostDetailUiState.headerItemCount: Int
    get() = 1 + (if (body != null) 1 else 0)

/** The floor with [commentId] among what is loaded, the opening post included. */
private fun PostDetailUiState.contentWithId(commentId: Long?): PostContent? =
    commentId?.let { id -> body?.takeIf { it.commentId == id } ?: comments.firstOrNull { it.commentId == id } }

/** The mark in flight on [content], if any — only one floor at a time can have one. */
private fun PostDetailUiState.pendingReactionFor(content: PostContent): ReactionAction? =
    pendingReaction?.takeIf { it.commentId == content.commentId }?.action

/** The last row of the list — the newest floor when the last page is loaded. */
private val PostDetailUiState.lastItemIndex: Int
    get() = (headerItemCount + comments.size - 1).coerceAtLeast(0)

/** Mirrors [ThreadList]'s item order so a quote reference can scroll to the floor it points at. */
private fun PostDetailUiState.indexOfFloor(floor: String): Int? {
    val position = comments.indexOfFirst { it.floor == floor }
    if (position < 0) return null
    return headerItemCount + position
}

/**
 * The list index where [page] starts, or null when no loaded comment belongs to it yet. `>=` rather
 * than `==` so a page whose comments were all deleted resolves to the next page instead of nowhere.
 *
 * The slice's first page resolves to the first comment rather than to the top of the list: the title
 * and the opening post sit above every page, and a reader who asked for page 12 asked for its floors,
 * not for the post they have already read. Page 1 keeps the whole top, which is where it begins.
 */
private fun PostDetailUiState.firstIndexOfPage(page: Int): Int? {
    if (page <= firstLoadedPage) return if (firstLoadedPage > 1) headerItemCount else 0
    val position = commentPages.indexOfFirst { it >= page }
    if (position < 0) return null
    return headerItemCount + position
}

/**
 * A bar standing in for the posting time, in a box exactly as tall as the line it replaces.
 *
 * The height matters and the bar's own does not: the author's name is centred against this column,
 * so a placeholder even a dp off centres the name a dp high and drops it into place the moment the
 * thread arrives — one visible step, in an element the reader has just watched fly across the
 * screen.
 *
 * The height comes from the style the real line is drawn in, so it follows the reading-size
 * preference along with the text it stands in for. A single-line [MetaText] is exactly its style's
 * `lineHeight` tall.
 */
@Composable
private fun MetaLinePlaceholder() {
    val lineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    Box(Modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
        SkeletonBar(0.22f, 10.dp)
    }
}

/**
 * The skeleton for a thread nothing told us anything about — a notification, a deep link, a cold
 * start straight into a restored destination.
 *
 * Grey all the way down, because grey is the only honest thing this screen can draw when it is
 * holding no facts. Nothing is in flight either, so unlike its sibling above this one owes the
 * final layout nothing.
 */
@Composable
private fun UnopenedThreadSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LayerPageGutter, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(LayerCardGap),
    ) {
        // The same card the opening post will be drawn in, so the page does not change shape when it
        // arrives — only what is in the card does.
        LayerCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = 18.dp, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SkeletonBar(0.92f, 22.dp)
            SkeletonBar(0.55f, 22.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(Sizes.avatarOriginalPost)
                        .clip(AvatarShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBar(0.35f, 12.dp)
                    SkeletonBar(0.22f, 10.dp)
                }
            }
            listOf(0.98f, 0.94f, 0.99f, 0.62f).forEach { SkeletonBar(it, 14.dp) }
        }
        CommentSkeletons()
    }
}

/** The first few reply stubs under the opening post, each in the card its reply will have. Grey either way. */
@Composable
private fun CommentSkeletons() {
    Column(verticalArrangement = Arrangement.spacedBy(LayerCardGap)) {
        repeat(3) {
            LayerCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(Sizes.avatarComment)
                            .clip(AvatarShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    SkeletonBar(0.5f, 12.dp)
                }
                SkeletonBar(0.85f, 12.dp)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------

private fun previewContent(
    floor: String,
    author: String,
    text: String,
    op: Boolean = false,
    quote: Pair<String, String>? = null,
) = PostContent(
    commentId = floor.hashCode().toLong(),
    floor = floor,
    authorName = author,
    authorUid = 1,
    avatarUrl = null,
    isOriginalPoster = op,
    badges = emptyList(),
    createdAtText = "47分钟前",
    createdAtTitle = null,
    categoryTitle = "日常",
    nodes =
    listOf(
        RichNode.Paragraph(
            buildList {
                quote?.let { (name, target) ->
                    add(InlineNode.QuoteRef(name = name, floor = target, url = "/post-1$target"))
                }
                add(InlineNode.Text(text))
            },
        ),
    ),
)

internal val previewState =
    PostDetailUiState(
        title = "为啥nodequality复制格式非常慢，是电脑问题还是怎么回事",
        body =
        previewContent(
            floor = "#0",
            author = "花田错不错",
            text = "nodequality出来的结果，复制格式，后台等了半个小时都没复制好，因为一直没有显示文件下载。",
            op = true,
        ),
        comments =
        listOf(
            previewContent("#1", "jkjoy", "服务器原因吧"),
            previewContent("#2", "linda", "你自己的问题，自查"),
            previewContent("#3", "zhh123", "你试试前台等"),
            previewContent(
                "#4",
                "花田错不错",
                " 只等了几分钟，等不及就后台了",
                op = true,
                quote = "zhh123" to "#3",
            ),
        ),
    )

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail")
@Composable
private fun PostDetailPreview() {
    PlazaTheme {
        PostDetailScreen(
            state = previewState,
            postUrl = "https://www.nodeseek.com/post-1-1",
            onBack = {},
            onOpenBrowser = {},
            onVerify = {},
            onImageClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · dark")
@Composable
private fun PostDetailDarkPreview() {
    PlazaTheme(darkTheme = true) {
        PostDetailScreen(
            state = previewState,
            postUrl = "https://www.nodeseek.com/post-1-1",
            onBack = {},
            onOpenBrowser = {},
            onVerify = {},
            onImageClick = {},
            onRetry = {},
            onLoadMore = {},
        )
    }
}

/**
 * Every block image in the thread, in reading order and without duplicates.
 *
 * Reading order is what makes the viewer's "2 / 4" mean anything: it has to match the order the images
 * appear while scrolling, so a tap on the third screenshot opens page three. Inline stickers are left
 * out — nobody opens a full-screen viewer for an emoji.
 *
 * Collapsed floors contribute nothing, for the same reason: paging past a hidden floor's screenshot
 * would show the reader exactly what the block spared them.
 */
internal fun PostDetailUiState.imageUrls(): List<String> =
    (listOfNotNull(body) + comments)
        .filter { showBlockedContent || !it.isBlocked }
        .flatMap { content -> content.nodes.imageUrls() }
        .distinct()

private fun List<RichNode>.imageUrls(): List<String> =
    flatMap { node ->
        when (node) {
            is RichNode.BlockImage -> listOf(node.url)

            is RichNode.Quote -> node.children.imageUrls()

            is RichNode.ListBlock -> node.items.flatMap { it.imageUrls() }

            // Cell thumbnails open the same viewer, so they page with everything else. Left out,
            // a tapped screenshot was "not in the list" and the viewer fell back to page one —
            // every image in a layout table opened as the post's first badge.
            is RichNode.Table ->
                node.content.flatten().flatMap { cell ->
                    cell.filterIsInstance<InlineNode.Image>().map { it.url }
                }

            is RichNode.Tabs -> node.tabs.flatMap { it.children.imageUrls() }

            // A 折叠 is where a 测评 files its 三网测速 screenshots — post-910421 is three of them.
            // Left out, every image inside one was "not in the list" and opened as page one, the
            // same way a table cell's did before the branch above existed. Counted whether or not
            // the fold is open: what the reader tapped has to be findable, and a viewer that paged
            // past the images of a fold it was opened from would be counting a different post.
            is RichNode.Fold -> node.children.imageUrls()

            else -> emptyList()
        }
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · skeleton")
@Composable
private fun PostDetailSkeletonPreview() {
    PlazaTheme { UnopenedThreadSkeleton() }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 400, name = "Post detail · loading spinner")
@Composable
private fun LoadingStatePreview() {
    PlazaTheme { LoadingState() }
}
