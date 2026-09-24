package io.github.nodyssey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_new_reply_count
import io.github.nodyssey.ui.resources.post_new_reply_delta
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.plaza.designsys.component.MetaStat
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.textScaledSize
import io.github.plaza.designsys.component.tonalTagTextStyle
import io.github.plaza.designsys.theme.LocalPlazaFontScale
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import org.jetbrains.compose.resources.stringResource

/**
 * A post card's title — the feed's, a space's and 我的主题帖's at 15/22, as the Lean round's 2a sets
 * it. Scaled from `titleMedium` rather than fixed, so the reading-size preference — which is carried
 * in the type scale — reaches it.
 */
@Composable
internal fun postCardTitleStyle(
    sizeSp: Float = 15f,
    lineHeightSp: Float = 22f,
): TextStyle {
    val base = MaterialTheme.typography.titleMedium
    val scale = sizeSp / TITLE_MEDIUM_SP
    return base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.fontSize * scale * (lineHeightSp / sizeSp),
    )
}

/** `titleMedium`'s own size, which [postCardTitleStyle] scales from. */
private const val TITLE_MEDIUM_SP = 15f

/** 3400 → 3.4k: the view count is a magnitude, and the card's foot has room for four characters. */
internal fun compactCount(value: Int): String =
    when {
        value < 1_000 -> value.toString()

        value < 10_000 -> {
            val tenths = value / 100
            if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
        }

        else -> "${value / 1_000}k"
    }

/**
 * The marks a post card carries on its meta line: the lock with its level, then 加精 unless the list
 * says otherwise. Emitted into the caller's row rather than wrapped, so they keep its spacing.
 */
@Composable
internal fun PostBadges(
    summary: PostSummary,
    showAward: Boolean = true,
) {
    if (summary.isLocked) {
        LockBadge(level = summary.lockLevel, description = lockBadgeDescription(summary.lockLevel))
    }
    if (showAward && summary.isAwarded) {
        Icon(
            NodeSeekIcons.Award,
            contentDescription = stringResource(Res.string.post_badge_awarded),
            // The warm role rather than primary: 加精 is a mark the site puts on a thread, not an
            // action this app offers, and the site draws it orange.
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(textScaledSize(TITLE_BADGE_SIZE)),
        )
    }
}

/**
 * A post card's reply figure, emitted into the caller's row like [PostBadges]: the reply count, and
 * after it a small filled +N once the reader has opened the thread and more came in (Lean 2a). The
 * feed and the search results draw it the same way.
 */
@Composable
internal fun PostReplyStat(post: FeedPost) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        post.summary.commentCount?.let {
            MetaStat(
                icon = PlazaIcons.ModeComment,
                value = it.toString(),
                contentDescription = stringResource(Res.string.post_reply_count, it),
            )
        }
        if (post.newCommentCount > 0) NewReplyBadge(post.newCommentCount)
    }
}

@Composable
private fun NewReplyBadge(count: Int) {
    val spoken = stringResource(Res.string.post_new_reply_count, count)
    Text(
        text = stringResource(Res.string.post_new_reply_delta, count),
        style = tonalTagTextStyle().copy(
            fontSize = NEW_REPLY_BADGE_SIZE * LocalPlazaFontScale.current,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
        color = MaterialTheme.colorScheme.onPrimary,
        modifier =
        Modifier
            .semantics { contentDescription = spoken }
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 5.dp),
    )
}

/** The +N's type, a step under a board tag's so the pill sits inside the counts' line. */
private val NEW_REPLY_BADGE_SIZE = 10.sp
