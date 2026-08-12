package io.github.nodyssey.ui.postdetail

import android.content.Intent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.countOf
import io.github.nodyssey.model.hasSpent
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.PageJumpSheet
import io.github.nodyssey.ui.common.PageJumpToolbarContent
import io.github.nodyssey.ui.common.RoleBadgeRow
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.composer.FloorReference
import io.github.nodyssey.ui.composer.ReplyComposerHost
import io.github.nodyssey.ui.composer.ReplyComposerViewModel
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.AppendSpinner
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.LoadingState
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.PostTitle
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.asSignature
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch

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
        onLoadMore = viewModel::loadNextPage,
        onLoadPage = viewModel::loadPage,
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
        onPublish = { replyViewModel.publish { viewModel.refresh() } },
        onClearError = replyViewModel::clearPublishError,
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
    onLoadPage: (Int) -> Unit = { onLoadMore() },
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
    onVerify: () -> Unit = { onOpenBrowser(postUrl) },
    /** `null` opens an empty reply; a floor addresses one (6d). The editor itself is hosted by the route. */
    onReply: (FloorReference?) -> Unit = {},
    /** Appends one more 引用 block to whatever the editor already holds. */
    onQuote: (FloorReference) -> Unit = {},
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showPageSheet by remember { mutableStateOf(false) }
    var pageToolbarExpanded by rememberSaveable { mutableStateOf(true) }
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
    // `canScrollBackward` is the same predicate, already maintained by LazyListState as plain state —
    // no derivedStateOf and no per-frame read of the two scroll fields. Its `canScrollForward` twin is
    // deliberately *not* used for [atListEnd] below: that one becomes true only once the list is
    // actually scrolled to the bottom, where this reads true as soon as the last item shows its head.
    val atListTop = !listState.canScrollBackward
    val atListEnd by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 1
        }
    }
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
    val positionWorthRecording = state.body != null && (visiblePage > 1 || !atListTop)
    LaunchedEffect(visiblePage, visibleFloor, positionWorthRecording) {
        if (positionWorthRecording) onReadingPositionChange(visiblePage, visibleFloor)
    }

    /** Scrolls when the page is already in the list, and asks for it when it is not. */
    fun goToPage(target: Int) {
        val index = state.firstIndexOfPage(target)
        if (index != null && target in state.firstLoadedPage..state.lastLoadedPage) {
            scope.launch { listState.animateScrollToItem(index) }
        } else {
            onLoadPage(target)
        }
    }
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
     * The nested-scroll state drives the toolbar; boundaries only pin it open where that is stable.
     * The bottom counts solely once the thread is fully loaded — while auto-append still runs, the
     * "last item visible" instant flips back and forth with every batch of inserted comments, and
     * tying expansion to it (or to isAppending itself) made the bar blink through a long scroll.
     */
    val toolbarExpanded =
        pageToolbarExpanded ||
            atListTop ||
            (atListEnd && !state.hasNextPage && !state.isAppending) ||
            state.error != null ||
            showPageSheet

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                title = state.title,
                showTitle = showCollapsedTitle,
                postUrl = postUrl,
                onBack = onBack,
                onOpenInBrowser = { onOpenBrowser(postUrl) },
                showBackButton = showBackButton,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val error = state.error
            when {
                state.body == null && state.isLoading -> ThreadSkeleton()

                state.body == null && error != null ->
                    SiteErrorState(
                        error = error,
                        onRetry = onRetry,
                        // A locked thread is fixed by signing in, not by loading it again in a
                        // browser; a challenge is fixed on this thread's own URL.
                        onOpenBrowser =
                        when (error) {
                            SiteError.LoginRequired -> onSignIn
                            SiteError.Cloudflare -> onVerify
                            else -> ({ onOpenBrowser(postUrl) })
                        },
                        // 等级不足 has no action that clears it, so the way out is the only button —
                        // and this screen, unlike a tab root, always has somewhere to go back to.
                        onBack = onBack.takeIf { showBackButton },
                    )

                else ->
                    ThreadList(
                        state = state,
                        listState = listState,
                        onOpenBrowser = onLinkClick,
                        onImageClick = onImageClick,
                        onJumpToFloor = { floor ->
                            // A quote can point anywhere in the thread, including pages nobody has
                            // opened. Scroll when it is here, fetch its page when it is not.
                            val index = state.indexOfFloor(floor)
                            if (index != null) {
                                scope.launch { listState.animateScrollToItem(index) }
                            } else {
                                onJumpToFloor(floor)
                            }
                        },
                        onReact = { content, action ->
                            val commentId = content.commentId
                            when {
                                // Same rule as the editor: the account has to exist before the
                                // action, not after a rejection that also spent the tap.
                                !state.isSignedIn -> onSignIn()

                                commentId == null -> Unit

                                // 点赞 costs nothing and the site does not confirm it either.
                                action == ReactionAction.Upvote -> onReact(commentId, action)

                                else -> {
                                    confirmTarget = ReactionConfirm(content, commentId, action)
                                    if (action == ReactionAction.ChickenLeg) onLoadFreeChickenLegs()
                                }
                            }
                        },
                        onReplyToFloor = onReply,
                        onQuoteFloor = onQuote,
                        onAuthorClick = onAuthorClick,
                        // Same gate as the editor and the marks: the account has to exist before the
                        // action, not after a rejection that also spent the tap.
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

            // A jump replaces the whole list, so the append spinner at its foot would be pointing at
            // content that is on its way out. This says "a different page is coming" where the reader
            // is already looking — and it is the only feedback between the tap and the new page.
            if (state.isLoading && state.body != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }

            if (state.body != null && !replyOpen) {
                DetailBottomActions(
                    toolbarExpanded = toolbarExpanded,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { goToPage((visiblePage - 1).coerceAtLeast(1)) },
                    onNext = { goToPage((visiblePage + 1).coerceAtMost(state.totalPages)) },
                    onPageClick = { showPageSheet = true },
                    onReply = { onReply(null) },
                    modifier = Modifier.align(Alignment.BottomEnd),
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

    // The site's own sentence is the whole value here — "鸡腿不足", "已经进行过加鸡腿操作" — so it is
    // shown verbatim, and our wording only stands in for the failures that never reached the site.
    val failure = state.reactionFailure
    val fallback = failure?.error?.shortMessage()
    LaunchedEffect(failure) {
        if (failure == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(failure.detail?.takeIf { it.isNotBlank() } ?: fallback.orEmpty())
        onReactionFailureShown()
    }

    // Same treatment for the star, kept separate so a refused collection and a refused mark cannot
    // clear each other's message.
    val collectFailure = state.collectFailure
    val collectFallback = collectFailure?.error?.shortMessage()
    LaunchedEffect(collectFailure) {
        if (collectFailure == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            collectFailure.detail?.takeIf { it.isNotBlank() } ?: collectFallback.orEmpty(),
        )
        onCollectFailureShown()
    }

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
            progress = stringResource(R.string.post_page_progress, visiblePage, state.totalPages, loadedFloors),
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                goToPage(target.coerceIn(1, state.totalPages.coerceAtLeast(1)))
            },
            // Clamped so a place left in a thread that has since lost pages still resolves to a
            // button that goes somewhere — the ViewModel lands it on the last page for the same reason.
            resumePage =
            (resume?.page ?: state.lastLoadedPage).coerceIn(1, state.totalPages.coerceAtLeast(1)),
            onResume = {
                showPageSheet = false
                if (resume != null) onResumeReading() else goToPage(state.lastLoadedPage)
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
    HorizontalFloatingToolbar(
        expanded = toolbarExpanded,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.post_reply_action)) },
                icon = {
                    Icon(
                        PlazaIcons.Reply,
                        contentDescription = null,
                    )
                },
                onClick = onReply,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        // Material's floating toolbar is a fixed 64dp and offers no size parameter, which leaves it
        // standing 8dp taller than the 回复 FAB inside it. Collapsed, that FAB becomes an 80dp round
        // button centred on these bounds, so 12dp of it now falls below them — hence the extra
        // bottom margin, which puts it back exactly where it sat before.
        modifier = modifier
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.sm + 12.dp,
            ).height(56.dp),
    ) {
        PageJumpToolbarContent(
            page = page,
            totalPages = totalPages,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageClick = onPageClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    showTitle: Boolean,
    postUrl: String,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
    showBackButton: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copy = rememberClipboardCopy()
    val linkCopied = stringResource(R.string.post_link_copied)
    val shareLabel = stringResource(R.string.action_share)

    TopAppBar(
        title = {
            Text(
                text = if (showTitle) title else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenInBrowser) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = stringResource(R.string.action_open_in_browser),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_link)) },
                        onClick = {
                            copy("post", postUrl, linkCopied)
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(shareLabel) },
                        onClick = {
                            menuOpen = false
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "$title\n$postUrl")
                                }
                            context.startActivity(Intent.createChooser(send, shareLabel))
                        },
                    )
                }
            }
        },
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * The thread as one scroll.
 *
 * The site paginates comments; this does not. Later pages append into the same list, so the reader
 * never meets a "page 2" boundary — which is the single biggest difference from the mobile web.
 */
@Composable
private fun ThreadList(
    state: PostDetailUiState,
    listState: LazyListState,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    onReact: (PostContent, ReactionAction) -> Unit,
    onReplyToFloor: (FloorReference?) -> Unit,
    onQuoteFloor: (FloorReference) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = modifier
            .fillMaxHeight()
            .readableWidth(),
    ) {
        item(key = "title") {
            ThreadHeader(title = state.title, body = state.body)
        }

        state.body?.let { body ->
            item(key = "body") {
                BlockAware(content = body, revealed = state.showBlockedContent) {
                    OriginalPost(
                        body = body,
                        onOpenBrowser = onOpenBrowser,
                        onImageClick = onImageClick,
                        onJumpToFloor = onJumpToFloor,
                        pendingReaction = state.pendingReactionFor(body),
                        onReact = { action -> onReact(body, action) },
                        onAuthorClick = onAuthorClick,
                        collected = state.collected,
                        collectionCount = state.collectionCount,
                        collectPending = state.collectPending,
                        onCollect = onCollect,
                        voteContent = voteContent,
                        stardustContent = stardustContent,
                    )
                }
            }
        }

        item(key = "comments-header") {
            CommentsHeader(count = state.comments.size)
        }

        itemsIndexed(
            items = state.comments,
            key = { index, comment -> comment.commentId ?: -index.toLong() - 1 },
        ) { _, comment ->
            BlockAware(content = comment, revealed = state.showBlockedContent) {
                CommentRow(
                    comment = comment,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = onImageClick,
                    onJumpToFloor = onJumpToFloor,
                    pendingReaction = state.pendingReactionFor(comment),
                    onReact = { action -> onReact(comment, action) },
                    onReply = { onReplyToFloor(comment.toFloorReference()) },
                    onQuote = { comment.toFloorReference()?.let(onQuoteFloor) },
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
        BlockedFloorRow(floor = content.floor, onShow = { openedHere = true })
    }
}

@Composable
private fun BlockedFloorRow(
    floor: String?,
    onShow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
            text = floor?.let { "$it · " }.orEmpty() + stringResource(R.string.post_comment_blocked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShow) {
            Text(stringResource(R.string.post_comment_blocked_show))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadHeader(
    title: String,
    body: PostContent?,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = 6.dp,
            bottom = 14.dp,
        ),
    ) {
        Text(
            text = title,
            style = PostTitle.hangLeadingPunctuation(title),
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            BoardTag(title = body?.categoryTitle, slug = null)
            body?.createdAtText?.let { MetaText(it) }
        }
    }
}

/**
 * The opening post, laid out flat like every other floor.
 *
 * It used to sit in a rounded container, which cost two levels of horizontal inset and boxed in the
 * long bodies this forum is full of. The larger avatar, the bodyLarge text and the comments header
 * below already mark it as the opening post, so the container was only spending width.
 */
@Composable
private fun OriginalPost(
    body: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onAuthorClick: (Long) -> Unit,
    /* Collection is whole-thread, so it belongs on the opening post and nowhere else. */
    collected: Boolean?,
    collectionCount: Int?,
    collectPending: Boolean,
    onCollect: () -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The identity block opens the author's space; the floor label stays outside it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .weight(1f)
                    .authorClickable(body.authorUid, onAuthorClick),
            ) {
                UserAvatar(
                    url = body.avatarUrl,
                    name = body.authorName,
                    size = Sizes.avatarOriginalPost,
                )
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = body.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        FloorBadges(body)
                    }
                    FloorTimeLine(body)
                }
            }
            body.floor?.let { FloorLabel(it) }
        }

        if (body.nodes.isEmpty()) {
            Text(
                text = stringResource(R.string.post_body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
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
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        UserSignature(
            nodes = body.signatureNodes,
            bodyStyle = MaterialTheme.typography.bodyLarge,
            onOpenBrowser = onOpenBrowser,
            onImageClick = onImageClick,
            onJumpToFloor = onJumpToFloor,
        )
        ReactionRow(
            reactions = body.reactions,
            pending = pendingReaction,
            onReact = onReact,
            collected = collected,
            collectionCount = collectionCount,
            collectPending = collectPending,
            onCollect = onCollect,
        )
    }
}

@Composable
private fun CommentsHeader(count: Int) {
    // Without the opening post's container, this line is what separates it from the replies.
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.post_comments_header, count),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.post_auto_paging),
            style = MaterialTheme.typography.labelSmall,
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
                text = stringResource(R.string.post_comments_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.post_comments_empty_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One reply.
 *
 * Real replies on this forum are anywhere between two characters and several screens, so the header
 * row is fixed, the timestamp hangs under the author name, and the body runs the full width — a
 * 36dp indent on every body line wasted a fifth of a phone's width on empty space.
 */
@Composable
private fun CommentRow(
    comment: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    pendingReaction: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    onReply: () -> Unit,
    onQuote: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    voteContent: @Composable (Long) -> Unit,
    stardustContent: (@Composable (RichNode.StardustReceive) -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.lg,
            bottom = Spacing.lg,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The identity block opens the author's space; the floor label stays outside it.
            // Same shape as the opening post's header: the avatar centres on the name *and* the
            // timestamp as one block — hanging the timestamp outside on its own indent left the
            // avatar aligned to nothing.
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
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = comment.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        FloorBadges(comment)
                    }
                    FloorTimeLine(comment)
                }
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
            modifier = Modifier.padding(top = Spacing.sm),
        )
        UserSignature(
            nodes = comment.signatureNodes,
            bodyStyle = MaterialTheme.typography.bodyMedium,
            onOpenBrowser = onOpenBrowser,
            onImageClick = onImageClick,
            onJumpToFloor = onJumpToFloor,
        )
        ReactionRow(
            reactions = comment.reactions,
            pending = pendingReaction,
            onReact = onReact,
            onReply = onReply,
            onQuote = onQuote,
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
) {
    if (nodes.isEmpty()) return

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
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

/** Tappable only when the uid was actually parsed; a dead ripple would promise a screen we cannot open. */
private fun Modifier.authorClickable(uid: Long?, onAuthorClick: (Long) -> Unit): Modifier =
    if (uid == null) this else clickable { onAuthorClick(uid) }

/** `ButtonDefaults.TextButtonContentPadding`'s horizontal inset. */
private val TEXT_BUTTON_CONTENT_INSET = 12.dp

@Composable
private fun ReactionRow(
    reactions: PostReactions?,
    pending: ReactionAction?,
    onReact: (ReactionAction) -> Unit,
    modifier: Modifier = Modifier,
    onReply: (() -> Unit)? = null,
    onQuote: (() -> Unit)? = null,
    /*
     * Collection is whole-thread on NodeSeek, so only the opening post passes these; every comment
     * row leaves them at their defaults and the star simply is not drawn. Null [collected] is the
     * same absence for a different reason — no fetched page has said which way it points.
     */
    collected: Boolean? = null,
    collectionCount: Int? = null,
    collectPending: Boolean = false,
    onCollect: (() -> Unit)? = null,
) {
    Row(
        // Every reaction is a TextButton, which keeps 12dp of content padding inside its own bounds.
        // Laid out honestly the last icon stops 12dp short of the margin the floor label and the
        // body text sit on; shifting the row out by exactly that much lines the ink up instead.
        modifier = modifier
            .fillMaxWidth()
            .offset(x = TEXT_BUTTON_CONTENT_INSET)
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First in an End-arranged row, so it draws leftmost and the three marks keep their places.
        if (collected != null && onCollect != null) {
            QuietReaction(
                icon = if (collected) Icons.Default.Star else PlazaIcons.StarBorder,
                label = if (collected) R.string.post_collected_action else R.string.post_collect_action,
                count = collectionCount?.toString().orEmpty(),
                selected = collected,
                pending = collectPending,
                onClick = onCollect.takeUnless { collectPending },
            )
        }
        REACTION_ORDER.forEach { (action, icon) ->
            QuietReaction(
                icon = icon,
                label = action.labelRes(),
                // No count at all rather than a zero when the page did not carry the tallies: an
                // unread number and "nobody has done this" are different claims.
                count = reactions?.countOf(action)?.toString().orEmpty(),
                spent = reactions?.hasSpent(action) == true,
                pending = pending == action,
                // A floor whose tallies we never read is a floor we cannot say is unspent — offering
                // the button there invites a round trip that ends in "已经进行过".
                onClick = if (reactions != null && pending == null) ({ onReact(action) }) else null,
            )
        }
        // Not reactions, but they have always lived on this row: they carry the floor into the
        // editor, which is what 6d's "回复 #12 · nssk" header is showing. Two buttons rather than
        // one because the site has two — 回复 addresses the author, 引用 reproduces the floor — and
        // collapsing them into a single action is what made every answer from here read as a quote.
        onQuote?.let {
            QuietReaction(
                icon = PlazaIcons.FormatQuote,
                label = R.string.post_quote_action,
                count = "",
                onClick = it,
            )
        }
        onReply?.let {
            QuietReaction(
                icon = PlazaIcons.Reply,
                label = R.string.post_reply_action,
                count = "",
                onClick = it,
            )
        }
    }
}

/** Left to right as 6d draws them: 点赞, 投喂鸡腿, 点踩. */
private val REACTION_ORDER =
    listOf(
        ReactionAction.Upvote to Icons.Default.ThumbUp,
        ReactionAction.ChickenLeg to NodeSeekIcons.ChickenLeg,
        ReactionAction.Dislike to PlazaIcons.ThumbDown,
    )

private fun ReactionAction.labelRes(): Int =
    when (this) {
        ReactionAction.Upvote -> R.string.post_reaction_like
        ReactionAction.ChickenLeg -> R.string.post_reaction_chicken
        ReactionAction.Dislike -> R.string.post_reaction_dislike
    }

@Composable
private fun QuietReaction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
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
            else -> ButtonDefaults.textButtonColors()
        },
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
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
                    if (isChicken) R.string.chicken_dialog_title else R.string.dislike_dialog_title,
                ),
            )
        },
        text = {
            Text(
                when {
                    free ->
                        stringResource(
                            R.string.chicken_dialog_body_free,
                            target.content.authorName,
                            freeChickenLegs.remaining,
                        )

                    isChicken -> stringResource(R.string.chicken_dialog_body, target.content.authorName)

                    else -> stringResource(R.string.dislike_dialog_body)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(
                        if (isChicken) R.string.chicken_dialog_confirm else R.string.dislike_dialog_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The header's badge chips: everything the parser read, with 楼主 prepended when the page marked
 * the author as OP some other way (the opening post itself carries no `is-poster` span on page 1
 * of some templates). Capped at three plus a +N chip — see [RoleBadgeRow].
 */
@Composable
private fun FloorBadges(content: PostContent) {
    val opLabel = stringResource(R.string.post_badge_original_poster)
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
            EditedMarker(content.editedAtText)
        }
    }
}

@Composable
private fun EditedMarker(fullText: String?) {
    val label = stringResource(R.string.post_edited)
    val underline = MaterialTheme.colorScheme.outlineVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
        Modifier
            // The site's marker text ("edited 36min ago") is the accessible name; the visible
            // label compresses it to two characters.
            .semantics { contentDescription = fullText ?: label }
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

/** Items in [ThreadList] before the first comment: title, comments header, and the body when present. */
private val PostDetailUiState.headerItemCount: Int
    get() = 2 + (if (body != null) 1 else 0)

/** The mark in flight on [content], if any — only one floor at a time can have one. */
private fun PostDetailUiState.pendingReactionFor(content: PostContent): ReactionAction? =
    pendingReaction?.takeIf { it.commentId == content.commentId }?.action

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

/** The detail-screen skeleton: a title block, a body, and the first few replies. */
@Composable
private fun ThreadSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(0.92f, 22.dp)
        SkeletonBar(0.55f, 22.dp)
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        repeat(3) {
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

            else -> emptyList()
        }
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · skeleton")
@Composable
private fun PostDetailSkeletonPreview() {
    PlazaTheme { ThreadSkeleton() }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 400, name = "Post detail · loading spinner")
@Composable
private fun LoadingStatePreview() {
    PlazaTheme { LoadingState() }
}
