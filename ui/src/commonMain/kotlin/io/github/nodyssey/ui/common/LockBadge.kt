package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_badge_locked
import io.github.nodyssey.ui.resources.post_badge_locked_level
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The 阅读权限 mark beside a thread title: a lock, and the level a reader needs when there is one.
 *
 * Shared between the feed and a user's space because the two lists badge the same fact, and a lock
 * that is 16sp in one and 14dp in the other reads as two different marks. Being drawn at all is
 * what says "restricted"; [level] is null when the restriction has no number to state — a 私有
 * thread, or a feed row whose lock icon carried no digit — and [description] is then the only thing
 * a screen reader has, which is why it is the caller's to say.
 */
@Composable
fun LockBadge(
    level: Int?,
    description: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        Icons.Default.Lock,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        // 16dp per the b1 §8 只读→锁定 mapping spec, in sp so it tracks the title it stands beside
        // rather than shrinking against a raised reading size.
        modifier = modifier
            .padding(start = Spacing.xs)
            .size(textScaledSize(TITLE_BADGE_SIZE)),
    )
    if (level != null) {
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What a lock says when it carries a level, and when it does not. */
@Composable
fun lockBadgeDescription(level: Int?): String =
    level
        ?.let { stringResource(Res.string.post_badge_locked_level, it) }
        ?: stringResource(Res.string.post_badge_locked)

/** The lock and the 加精 mark beside a title, at the size the b1 §8 badge spec gives them. */
val TITLE_BADGE_SIZE = 16.sp
