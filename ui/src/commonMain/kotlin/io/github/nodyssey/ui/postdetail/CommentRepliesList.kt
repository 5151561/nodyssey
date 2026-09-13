package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.comment_replies_collapse
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** 直接回复预览；展开不改变主列表中的楼层顺序。 */
@Composable
internal fun CommentRepliesList(
    replies: List<PostContent>,
    onCollapse: () -> Unit,
    showBlockedContent: Boolean,
    onJumpToFloor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) return

    val replyKeys = remember(replies) { replies.commentKeys() }
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
        TextButton(
            onClick = onCollapse,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(Res.string.comment_replies_collapse))
        }
    }
}

@Composable
private fun CommentReplyCard(
    reply: PostContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        interactionSource = interactionSource,
    ) {
        Box {
            Row(
                modifier = Modifier.padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                UserAvatar(url = reply.avatarUrl, name = reply.authorName, size = Sizes.avatarComment)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = reply.authorName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        reply.floor?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PostRichContent(
                        nodes = reply.nodes,
                        onLinkClick = { onClick() },
                        onImageClick = { onClick() },
                        onQuoteRefClick = { onClick() },
                        textStyle = MaterialTheme.typography.bodySmall,
                        selectable = false,
                    )
                }
            }
            // selectable 只关闭选区，代码块和折叠仍有自己的点击操作。
            // 覆盖层把预览内的触摸统一交给卡片；RichContent 支持整体只读后即可移除。
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .clearAndSetSemantics {},
            )
        }
    }
}
