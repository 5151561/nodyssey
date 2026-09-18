package io.github.nodyssey.ui.postdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.comment_reply_target
import io.github.nodyssey.ui.resources.comment_reply_target_collapse
import io.github.nodyssey.ui.resources.comment_reply_target_title
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** 与下方的直接回复按钮独立，头像不可展示时仍保留明确的目标楼层。 */
@Composable
internal fun CommentReplyTargetButton(
    floor: String,
    target: PostContent?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.comment_reply_target, floor)
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
        modifier = modifier.semantics {
            contentDescription = label
            selected = expanded
        },
    ) {
        Icon(PlazaIcons.Reply, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(Spacing.xs))
        if (target != null) {
            UserAvatar(url = target.avatarUrl, name = target.authorName, size = ButtonDefaults.IconSize)
        } else {
            Text(floor, maxLines = 1)
        }
    }
}

/** 回复目标位于正文上方，关闭引用不会改变下方直接回复的展开状态。 */
@Composable
internal fun CommentReplyTargetPreview(
    target: PostContent,
    showBlockedContent: Boolean,
    onCollapse: () -> Unit,
    onJumpToTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewHeight = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() * 3 }
    OutlinedCard(
        onClick = onJumpToTarget,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(PlazaIcons.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(Res.string.comment_reply_target_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.comment_reply_target_collapse))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BlockAware(content = target, revealed = showBlockedContent) {
            CommentPreviewContent(
                content = target,
                onClick = onJumpToTarget,
                maxContentHeight = previewHeight,
            )
        }
    }
}
