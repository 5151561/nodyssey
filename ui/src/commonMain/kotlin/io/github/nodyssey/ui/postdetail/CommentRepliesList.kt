package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.LocalEinkMode
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.fadeToBackground

/**
 * 直接回复预览；展开不改变主列表中的楼层顺序。
 *
 * [replyKeys] 由调用处从主列表的键里取，index 与 [replies] 对齐：预览卡和它镜像的那一行
 * 用同一个身份，两边不会各算各的。
 */
@Composable
internal fun CommentRepliesList(
    replies: List<PostContent>,
    replyKeys: List<String>,
    showBlockedContent: Boolean,
    onJumpToFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        replies.forEachIndexed { index, reply ->
            key(replyKeys[index]) {
                BlockAware(content = reply, revealed = showBlockedContent) {
                    CommentReplyCard(
                        reply = reply,
                        onClick = { reply.floor?.let(onJumpToFloor) },
                        modifier = Modifier.testTag("reply-preview-${replyKeys[index]}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentReplyCard(
    reply: PostContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        CommentPreviewContent(content = reply, onClick = onClick)
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
    Row(
        modifier = modifier.padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        UserAvatar(url = content.avatarUrl, name = content.authorName, size = Sizes.avatarComment)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
        }
    }
}

/** 摘要预览最多组合几个顶层块 —— 够填满三行，又不至于把整条楼层的图片一起拖进来。 */
private const val PREVIEW_MAX_BLOCKS = 3
