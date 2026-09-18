package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.nodyssey.core.CommentReplies
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_collapse
import io.github.nodyssey.ui.resources.action_expand
import io.github.nodyssey.ui.resources.comment_replies_count
import io.github.nodyssey.ui.resources.comment_replies_open_floor
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.fadeToBackground
import org.jetbrains.compose.resources.stringResource

/**
 * 直接回复预览。
 *
 * 一张卡只装它自己的直接回复，卡上再带一个「N 条回复」把下一层展开 —— A 的 #4 下面是 B 的 #7，
 * #7 这张卡上再点开才是 A 回 #7 的 #10。一次把整棵子树拉平的话，热帖里 #1 那一层就是半个帖子。
 *
 * [indices] 是要画的那几条在 [comments] 里的下标，[commentKeys] 与 [comments] 对齐：预览卡和它
 * 镜像的那一行用同一个身份，两边不会各算各的。[depth] 从 0 开始，到 [MAX_REPLY_DEPTH] 为止，
 * 再往下由卡片点击跳到原楼层，在那里继续展开。
 */
@Composable
internal fun CommentRepliesList(
    indices: List<Int>,
    comments: List<PostContent>,
    commentKeys: List<String>,
    commentReplies: CommentReplies,
    showBlockedContent: Boolean,
    onJumpToFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    if (indices.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        indices.forEach { index ->
            val reply = comments.getOrNull(index) ?: return@forEach
            val key = commentKeys.getOrNull(index) ?: return@forEach
            key(key) {
                BlockAware(content = reply, revealed = showBlockedContent) {
                    CommentReplyCard(
                        reply = reply,
                        replyKey = key,
                        children = commentReplies.directRepliesOf(index),
                        comments = comments,
                        commentKeys = commentKeys,
                        commentReplies = commentReplies,
                        showBlockedContent = showBlockedContent,
                        onJumpToFloor = onJumpToFloor,
                        depth = depth,
                        modifier = Modifier.testTag("reply-preview-$key"),
                    )
                }
            }
        }
    }
}

/**
 * 嵌套几层为止。
 *
 * 每一层要吃掉一个头像加两侧内边距的宽度，360dp 的屏幕上第四层的正文已经窄到读不下去；到了这里
 * 「N 条回复」改成跳到原楼层，那条楼层在主列表里自己就是第 0 层，可以接着往下点。
 */
private const val MAX_REPLY_DEPTH = 2

@Composable
private fun CommentReplyCard(
    reply: PostContent,
    replyKey: String,
    children: List<Int>,
    comments: List<PostContent>,
    commentKeys: List<String>,
    commentReplies: CommentReplies,
    showBlockedContent: Boolean,
    onJumpToFloor: (String) -> Unit,
    depth: Int,
    modifier: Modifier = Modifier,
) {
    val jump = { reply.floor?.let(onJumpToFloor) ?: Unit }
    var expanded by rememberSaveable(replyKey) { mutableStateOf(false) }
    val canExpand = depth < MAX_REPLY_DEPTH
    OutlinedCard(
        onClick = jump,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        CommentPreviewContent(content = reply, onClick = jump) {
            if (children.isNotEmpty()) {
                QuietReaction(
                    icon = PlazaIcons.ModeComment,
                    label = stringResource(Res.string.comment_replies_count, children.size),
                    count = children.size.toString(),
                    // 到了深度上限就不再往里套，点它去原楼层 —— 那里是新的第 0 层。
                    onClick = if (canExpand) ({ expanded = !expanded }) else jump,
                    modifier = Modifier
                        .offset(x = -ButtonDefaults.TextButtonContentPadding.horizontalInset())
                        .testTag("reply-expand-$replyKey"),
                    trailingIcon = when {
                        !canExpand -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                        expanded -> Icons.Default.KeyboardArrowUp
                        else -> Icons.Default.KeyboardArrowDown
                    },
                    trailingIconDescription = stringResource(
                        when {
                            !canExpand -> Res.string.comment_replies_open_floor
                            expanded -> Res.string.action_collapse
                            else -> Res.string.action_expand
                        },
                    ),
                )
                if (expanded && canExpand) {
                    CommentRepliesList(
                        indices = children,
                        comments = comments,
                        commentKeys = commentKeys,
                        commentReplies = commentReplies,
                        showBlockedContent = showBlockedContent,
                        onJumpToFloor = onJumpToFloor,
                        modifier = Modifier.padding(top = Spacing.xs).testTag("comment-replies-$replyKey"),
                        depth = depth + 1,
                    )
                }
            }
        }
    }
}

/**
 * 两个方向的预览共用内容和点击规则。
 *
 * 正文走 `PostRichContent` 的只读模式：整段预览的每一次触摸都属于外面那张卡片，而卡片只做一件事
 * —— 跳到原楼层。里面的折叠、复制、报告标签不是被盖住，而是根本不画，因为一个复制不到东西的按钮
 * 比没有更糟。[maxContentHeight] 再把它裁到大约三行并淡出末行。
 */
@Composable
internal fun CommentPreviewContent(
    content: PostContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxContentHeight: Dp? = null,
    /** 正文下面的位置，回复卡用来放它自己的「N 条回复」和展开后的下一层。 */
    footer: @Composable () -> Unit = {},
) {
    val previewBackground = MaterialTheme.colorScheme.surfaceContainerLow
    val eink = LocalEinkMode.current
    val density = LocalDensity.current
    // 裁掉的高度不等于「有东西被裁掉」：短消息的预览不该无端淡出末行。
    var contentHeight by remember(content) { mutableIntStateOf(0) }
    val clipped = maxContentHeight != null && contentHeight > with(density) { maxContentHeight.roundToPx() }
    val bodyModifier = if (maxContentHeight == null) {
        Modifier
    } else {
        Modifier
            .heightIn(max = maxContentHeight)
            .clipToBounds()
            .then(
                if (clipped) {
                    Modifier.fadeToBackground(previewBackground, maxContentHeight / 3, eink)
                } else {
                    Modifier
                },
            )
    }
    // 头像只占它自己那一行，正文和下一层回复走满卡片宽度 —— 和 `CommentRow` 同一个形状。
    // 把正文整列缩进到头像右边，头像下面就空出一条白带，越往里嵌越窄。
    Column(
        modifier = modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            UserAvatar(url = content.avatarUrl, name = content.authorName, size = Sizes.avatarComment)
            Text(
                text = content.authorName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            content.floor?.let { FloorLabel(it) }
        }
        Box(bodyModifier) {
            PostRichContent(
                nodes = content.nodes,
                onLinkClick = { onClick() },
                onImageClick = { onClick() },
                onQuoteRefClick = { onClick() },
                textStyle = MaterialTheme.typography.bodySmall,
                selectable = false,
                interactive = false,
                // 三行里放不下更多，而一个块一旦进入组合，它的图片就已经在下载了：
                // 一条带大图的楼层被十条回复引用过，展开就是十次请求，只为显示三行。
                maxBlocks = maxContentHeight?.let { PREVIEW_MAX_BLOCKS },
                modifier = if (maxContentHeight == null) {
                    Modifier
                } else {
                    Modifier
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { contentHeight = it.height }
                },
            )
        }
        footer()
    }
}

/** 摘要预览最多组合几个顶层块 —— 够填满三行，又不至于把整条楼层的图片一起拖进来。 */
private const val PREVIEW_MAX_BLOCKS = 3
