package io.github.nsreader.ui.postdetail

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.PostContent
import io.github.nsreader.model.RichNode
import io.github.nsreader.ui.common.BoardTag
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.RoleBadgeRow
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.common.rememberClipboardCopy
import io.github.nsreader.ui.composer.ReplyComposerHost
import io.github.nsreader.ui.composer.ReplyComposerViewModel
import io.github.nsreader.ui.composer.ReplyQuote
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.PostTitle
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth
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
    initialFloor: String? = null,
    showBackButton: Boolean = true,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val replyState by replyViewModel.uiState.collectAsStateWithLifecycle()
    val postUrl = viewModel.postUrl()
    // Every image in the thread as currently loaded, so the viewer can page between them without
    // going back to the data layer — the URLs were already parsed into the rendered content.
    val images = remember(state.body, state.comments) { state.imageUrls() }
    PostDetailScreen(
        state = state,
        initialFloor = initialFloor,
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
        onPageScrollHandled = viewModel::onPageScrollHandled,
        showBackButton = showBackButton,
        // Replying needs an account; sending an anonymous reply into the void is the one outcome
        // the editor must not produce, so the sign-in page comes first.
        onReply = { quote -> if (state.isSignedIn) replyViewModel.open(quote) else onSignIn() },
        replyOpen = replyState.visible,
        modifier = modifier,
    )

    ReplyComposerHost(
        state = replyState,
        onDismiss = replyViewModel::close,
        onBodyChange = replyViewModel::updateBody,
        onClearQuote = replyViewModel::clearQuote,
        onPreviewChange = replyViewModel::setPreviewing,
        onPickImages = replyViewModel::addImages,
        onRemoveAttachment = replyViewModel::removeAttachment,
        onRetryAttachment = replyViewModel::retryUpload,
        onRetryFailedUploads = replyViewModel::retryFailedUploads,
        onPublish = { replyViewModel.publish { viewModel.refresh() } },
        onClearError = replyViewModel::clearPublishError,
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
    onPageScrollHandled: () -> Unit = {},
    initialFloor: String? = null,
    showBackButton: Boolean = true,
    /** Opens the sign-in page. Separate from [onOpenBrowser] because "登录" is not "看看网页版". */
    onSignIn: () -> Unit = { onOpenBrowser(postUrl) },
    /** Clears a Cloudflare challenge on this thread's own URL. */
    onVerify: () -> Unit = { onOpenBrowser(postUrl) },
    /** `null` opens an empty reply; a quote answers one floor (6d). The editor itself is hosted by the route. */
    onReply: (ReplyQuote?) -> Unit = {},
    /** Hides the bottom toolbar while the editor covers it. */
    replyOpen: Boolean = false,
    /** Body/comment links. Separate from [onOpenBrowser] so our own URLs can stay in the app. */
    onLinkClick: (String) -> Unit = onOpenBrowser,
    /** Opens the tapped author's space. */
    onAuthorClick: (Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var chickenTarget by remember { mutableStateOf<PostContent?>(null) }
    var showPageSheet by remember { mutableStateOf(false) }
    var pageToolbarExpanded by rememberSaveable { mutableStateOf(true) }
    var hasJumpedToInitialFloor by remember(initialFloor) { mutableStateOf(false) }
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
    val atListTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
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
    LaunchedEffect(initialFloor, state.comments.size) {
        if (hasJumpedToInitialFloor) return@LaunchedEffect
        val floor = initialFloor ?: return@LaunchedEffect
        val index = state.indexOfFloor(floor) ?: return@LaunchedEffect
        listState.scrollToItem(index)
        hasJumpedToInitialFloor = true
    }

    // The page the reader is looking at, not the furthest page fetched. Pages already in the list
    // are navigated by scrolling; only pages beyond the loaded prefix involve the network.
    val visiblePage by remember(state.commentPages, state.body != null) {
        derivedStateOf {
            val commentIndex = listState.firstVisibleItemIndex - state.headerItemCount
            if (commentIndex < 0) {
                1
            } else {
                state.commentPages.getOrNull(commentIndex)
                    ?: state.commentPages.lastOrNull()
                    ?: 1
            }
        }
    }
    fun scrollToPage(target: Int) {
        val index = state.firstIndexOfPage(target)
        if (index != null) {
            scope.launch { listState.animateScrollToItem(index) }
        } else {
            onLoadPage(target)
        }
    }
    LaunchedEffect(state.pendingScrollPage, state.commentPages.size) {
        val target = state.pendingScrollPage ?: return@LaunchedEffect
        // The fetch has finished but the new comments may not have flowed out of Room yet; wait for
        // the emission that carries them rather than scrolling to a stale end-of-list.
        if (target > state.page) return@LaunchedEffect
        val index = state.firstIndexOfPage(target)
            ?: (state.headerItemCount + state.comments.size - 1).coerceAtLeast(0)
        listState.scrollToItem(index)
        onPageScrollHandled()
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
                    NodeSeekErrorState(
                        error = error,
                        onRetry = onRetry,
                        // A locked thread is fixed by signing in, not by loading it again in a
                        // browser; a challenge is fixed on this thread's own URL.
                        onOpenBrowser =
                        when (error) {
                            NodeSeekError.LoginRequired -> onSignIn
                            NodeSeekError.Cloudflare -> onVerify
                            else -> ({ onOpenBrowser(postUrl) })
                        },
                    )

                else ->
                    ThreadList(
                        state = state,
                        listState = listState,
                        onOpenBrowser = onLinkClick,
                        onImageClick = onImageClick,
                        onJumpToFloor = { floor ->
                            val index = state.indexOfFloor(floor)
                            if (index != null) scope.launch { listState.animateScrollToItem(index) }
                        },
                        onChickenClick = { chickenTarget = it },
                        onReplyToFloor = onReply,
                        onAuthorClick = onAuthorClick,
                        modifier =
                        Modifier.floatingToolbarVerticalNestedScroll(
                            expanded = toolbarExpanded,
                            onExpand = { pageToolbarExpanded = true },
                            onCollapse = { pageToolbarExpanded = false },
                        ),
                    )
            }

            if (state.body != null && !replyOpen) {
                DetailBottomActions(
                    toolbarExpanded = toolbarExpanded,
                    page = visiblePage,
                    totalPages = state.totalPages,
                    onPrevious = { scrollToPage((visiblePage - 1).coerceAtLeast(1)) },
                    onNext = {
                        val next = (visiblePage + 1).coerceAtMost(state.totalPages)
                        if (next <= state.page) scrollToPage(next) else onLoadPage(next)
                    },
                    onPageClick = { showPageSheet = true },
                    onReply = { onReply(null) },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    chickenTarget?.let { target ->
        FeedChickenDialog(
            onDismiss = { chickenTarget = null },
            onConfirm = { chickenTarget = null },
            authorName = target.authorName,
        )
    }

    if (showPageSheet) {
        PageJumpSheet(
            page = visiblePage,
            loadedPage = state.page,
            totalPages = state.totalPages,
            loadedFloors = state.comments.size + if (state.body != null) 1 else 0,
            onDismiss = { showPageSheet = false },
            onGo = { target ->
                showPageSheet = false
                val clamped = target.coerceIn(1, state.totalPages.coerceAtLeast(1))
                if (clamped <= state.page) scrollToPage(clamped) else onLoadPage(clamped)
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
                        NodeSeekIcons.Reply,
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
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        DetailFloatingToolbarContent(
            page = page,
            totalPages = totalPages,
            onPrevious = onPrevious,
            onNext = onNext,
            onPageClick = onPageClick,
        )
    }
}

@Composable
private fun DetailFloatingToolbarContent(
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
) {
    val previousPageDescription = stringResource(R.string.post_previous_page)
    val nextPageDescription = stringResource(R.string.post_next_page)
    Row(
        modifier = Modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = page > 1,
            modifier =
            Modifier.semantics {
                contentDescription = previousPageDescription
            },
        ) {
            Text(
                "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPageClick, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
            Text(stringResource(R.string.post_page_of, page, totalPages))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        IconButton(
            onClick = onNext,
            enabled = page < totalPages,
            modifier =
            Modifier.semantics {
                contentDescription = nextPageDescription
            },
        ) {
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageJumpSheet(
    page: Int,
    loadedPage: Int,
    totalPages: Int,
    loadedFloors: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var input by remember { mutableStateOf(page.toString()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.post_jump_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.post_page_progress, page, totalPages, loadedFloors),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            androidx.compose.material3.OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { character -> character.isDigit() } },
                label = { Text(stringResource(R.string.post_page_input, totalPages)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { onGo(1) }) { Text(stringResource(R.string.post_first_page)) }
                TextButton(onClick = { onGo(loadedPage) }) { Text(stringResource(R.string.post_latest_read)) }
                TextButton(onClick = { onGo(totalPages) }) { Text(stringResource(R.string.post_last_page)) }
            }
            Button(onClick = { input.toIntOrNull()?.let(onGo) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.post_go_page, input.ifBlank { page.toString() }))
            }
        }
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
    onChickenClick: (PostContent) -> Unit,
    onReplyToFloor: (ReplyQuote?) -> Unit,
    onAuthorClick: (Long) -> Unit,
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
                OriginalPost(
                    body = body,
                    onOpenBrowser = onOpenBrowser,
                    onImageClick = onImageClick,
                    onJumpToFloor = onJumpToFloor,
                    onChickenClick = { onChickenClick(body) },
                    onAuthorClick = onAuthorClick,
                )
            }
        }

        item(key = "comments-header") {
            CommentsHeader(count = state.comments.size)
        }

        itemsIndexed(
            items = state.comments,
            key = { index, comment -> comment.commentId ?: -index.toLong() - 1 },
        ) { _, comment ->
            CommentRow(
                comment = comment,
                onOpenBrowser = onOpenBrowser,
                onImageClick = onImageClick,
                onJumpToFloor = onJumpToFloor,
                onChickenClick = { onChickenClick(comment) },
                onReply = { onReplyToFloor(comment.toReplyQuote()) },
                onAuthorClick = onAuthorClick,
            )
        }

        if (state.isAppending) {
            item(key = "appending") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                }
            }
        }
    }
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
            style = PostTitle,
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

@Composable
private fun OriginalPost(
    body: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
    onChickenClick: () -> Unit,
    onAuthorClick: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = 12.dp),
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
                RichContent(
                    nodes = body.nodes,
                    onLinkClick = onOpenBrowser,
                    onImageClick = onImageClick,
                    onQuoteRefClick = { onJumpToFloor(it.floor) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            ReactionRow(onChickenClick = onChickenClick)
        }
    }
}

@Composable
private fun CommentsHeader(count: Int) {
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
    onChickenClick: () -> Unit,
    onReply: () -> Unit,
    onAuthorClick: (Long) -> Unit,
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
                    modifier = Modifier.weight(1f, fill = false),
                )
                FloorBadges(comment)
            }
            comment.floor?.let { FloorLabel(it) }
        }
        FloorTimeLine(comment, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
        RichContent(
            nodes = comment.nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            onQuoteRefClick = { ref -> onJumpToFloor(ref.floor) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        ReactionRow(onChickenClick = onChickenClick, onReply = onReply)
    }
}

/** Tappable only when the uid was actually parsed; a dead ripple would promise a screen we cannot open. */
private fun Modifier.authorClickable(uid: Long?, onAuthorClick: (Long) -> Unit): Modifier =
    if (uid == null) this else clickable { onAuthorClick(uid) }

@Composable
private fun ReactionRow(
    onChickenClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReply: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietReaction(Icons.Default.ThumbUp, R.string.post_reaction_like, "0")
        QuietReaction(
            icon = NodeSeekIcons.ChickenLeg,
            label = R.string.post_reaction_chicken,
            count = "0",
            onClick = onChickenClick,
        )
        QuietReaction(NodeSeekIcons.ThumbDown, R.string.post_reaction_dislike, "0")
        // The one interaction on a floor that is actually wired up. It carries the floor into the
        // editor as a quote, which is what 6d's "回复 #12 · nssk" header is showing.
        onReply?.let {
            QuietReaction(
                icon = NodeSeekIcons.Reply,
                label = R.string.post_reply_action,
                count = "",
                onClick = it,
            )
        }
    }
}

@Composable
private fun QuietReaction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    count: String,
    onClick: (() -> Unit)? = null,
) {
    TextButton(onClick = onClick ?: {}, enabled = onClick != null) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(18.dp))
        if (count.isNotEmpty()) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

@Composable
private fun FeedChickenDialog(
    authorName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                NodeSeekIcons.ChickenLeg,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.chicken_dialog_title)) },
        text = {
            Text(stringResource(R.string.chicken_dialog_body, authorName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.chicken_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
 * The quote context the reply editor opens with.
 *
 * `null` for a floor with no number — a reply that says "回复 #" answers nothing, and the editor
 * handles a missing quote perfectly well by opening empty.
 */
private fun PostContent.toReplyQuote(): ReplyQuote? {
    val number = floor?.trimStart('#')?.toIntOrNull() ?: return null
    return ReplyQuote(
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

@Composable
private fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Items in [ThreadList] before the first comment: title, comments header, and the body when present. */
private val PostDetailUiState.headerItemCount: Int
    get() = 2 + (if (body != null) 1 else 0)

/** Mirrors [ThreadList]'s item order so a quote reference can scroll to the floor it points at. */
private fun PostDetailUiState.indexOfFloor(floor: String): Int? {
    val position = comments.indexOfFirst { it.floor == floor }
    if (position < 0) return null
    return headerItemCount + position
}

/**
 * The list index where [page] starts, or null when no loaded comment belongs to it yet. `>=` rather
 * than `==` so a page whose comments were all deleted resolves to the next page instead of nowhere.
 */
private fun PostDetailUiState.firstIndexOfPage(page: Int): Int? {
    if (page <= 1) return 0
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
                    .clip(CircleShape)
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                SkeletonBar(0.5f, 12.dp)
            }
            SkeletonBar(0.85f, 12.dp)
        }
    }
}

@Composable
private fun SkeletonBar(
    fraction: Float,
    height: Dp,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
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
    NodeSeekTheme {
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
    NodeSeekTheme(darkTheme = true) {
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
 */
internal fun PostDetailUiState.imageUrls(): List<String> =
    (listOfNotNull(body) + comments)
        .flatMap { content -> content.nodes.imageUrls() }
        .distinct()

private fun List<RichNode>.imageUrls(): List<String> =
    flatMap { node ->
        when (node) {
            is RichNode.BlockImage -> listOf(node.url)
            is RichNode.Quote -> node.children.imageUrls()
            is RichNode.ListBlock -> node.items.flatMap { it.imageUrls() }
            else -> emptyList()
        }
    }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "Post detail · skeleton")
@Composable
private fun PostDetailSkeletonPreview() {
    NodeSeekTheme { ThreadSkeleton() }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 400, name = "Post detail · loading spinner")
@Composable
private fun LoadingStatePreview() {
    NodeSeekTheme { LoadingState() }
}
