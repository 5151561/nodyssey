package io.github.nsreader.ui.postdetail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.common.rememberClipboardCopy
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.CommentBody
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.PostBody
import io.github.nsreader.ui.theme.PostTitle
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES
import io.github.nsreader.ui.theme.readableWidth
import kotlinx.coroutines.launch

@Composable
fun PostDetailRoute(
    viewModel: PostDetailViewModel,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val postUrl = viewModel.postUrl()
    PostDetailScreen(
        state = state,
        postUrl = postUrl,
        onBack = onBack,
        onOpenBrowser = onOpenBrowser,
        onSignIn = onSignIn,
        onVerify = { onVerify(postUrl) },
        onImageClick = onImageClick,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadNextPage,
        modifier = modifier,
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
    /** Opens the sign-in page. Separate from [onOpenBrowser] because "登录" is not "看看网页版". */
    onSignIn: () -> Unit = { onOpenBrowser(postUrl) },
    /** Clears a Cloudflare challenge on this thread's own URL. */
    onVerify: () -> Unit = { onOpenBrowser(postUrl) },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.comments.size) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            DetailTopBar(
                title = state.title,
                postUrl = postUrl,
                onBack = onBack,
                onOpenInBrowser = { onOpenBrowser(postUrl) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
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
                        onOpenBrowser = onOpenBrowser,
                        onImageClick = onImageClick,
                        onJumpToFloor = { floor ->
                            val index = state.indexOfFloor(floor)
                            if (index != null) scope.launch { listState.animateScrollToItem(index) }
                        },
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    postUrl: String,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copy = rememberClipboardCopy()
    val linkCopied = stringResource(R.string.post_link_copied)
    val shareLabel = stringResource(R.string.action_share)

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
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
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
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
                )
                // A 6dp tonal band, not a hairline: the break between the post and the replies is
                // the one place in this screen that needs to be unmissable while scrolling past.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
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
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            bottom = Spacing.lg,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                    )
                    if (body.isOriginalPoster) OriginalPosterBadge()
                }
                body.createdAtText?.let { MetaText(it) }
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
                textStyle = PostBody,
                modifier = Modifier.padding(top = Spacing.md),
            )
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
 * row is fixed and everything below it hangs off a 36dp indent — which gives even a two-word reply
 * a shape, and stops a long one from losing its author.
 */
@Composable
private fun CommentRow(
    comment: PostContent,
    onOpenBrowser: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onJumpToFloor: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = 10.dp,
            bottom = Spacing.md,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            )
            // Every reply from the thread's author carries the badge, not just the first: on a long
            // page the reader has long since lost track of who opened it.
            if (comment.isOriginalPoster) OriginalPosterBadge()
            Box(Modifier.weight(1f))
            comment.floor?.let { FloorLabel(it) }
        }
        comment.createdAtText?.let {
            MetaText(it, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
        }
        RichContent(
            nodes = comment.nodes,
            onLinkClick = onOpenBrowser,
            onImageClick = onImageClick,
            onQuoteRefClick = { ref -> onJumpToFloor(ref.floor) },
            textStyle = CommentBody,
            modifier = Modifier.padding(start = 36.dp, top = Spacing.sm),
        )
    }
}

@Composable
private fun OriginalPosterBadge() {
    Text(
        text = stringResource(R.string.post_badge_original_poster),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
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

/** Mirrors [ThreadList]'s item order so a quote reference can scroll to the floor it points at. */
private fun PostDetailUiState.indexOfFloor(floor: String): Int? {
    val position = comments.indexOfFirst { it.floor == floor }
    if (position < 0) return null
    val headerItems = 1 + (if (body != null) 1 else 0) + 1
    return headerItems + position
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
