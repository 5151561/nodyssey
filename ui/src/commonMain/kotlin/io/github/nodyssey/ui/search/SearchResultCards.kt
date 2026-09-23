package io.github.nodyssey.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.UserSearchResult
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.common.LockBadge
import io.github.nodyssey.ui.common.NodeSeekIcons
import io.github.nodyssey.ui.common.TITLE_BADGE_SIZE
import io.github.nodyssey.ui.common.lockBadgeDescription
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.credit_level
import io.github.nodyssey.ui.resources.post_badge_awarded
import io.github.nodyssey.ui.resources.post_new_reply_count
import io.github.nodyssey.ui.resources.post_reply_count
import io.github.nodyssey.ui.resources.search_user_comments
import io.github.nodyssey.ui.resources.search_user_joined
import io.github.nodyssey.ui.resources.search_user_topics
import io.github.plaza.designsys.component.GroupDividerInset
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRowTrailing
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.textScaledSize
import org.jetbrains.compose.resources.stringResource

/** A result card's corners: 16dp, a step tighter than the feed's 24 for a card that is a step smaller. */
private val ResultCardShape: Shape = RoundedCornerShape(16.dp)
private val ResultCardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/** 44dp, the artboard's: a user result is the person, so the avatar is larger than a card header's. */
private val UserResultAvatarSize = 44.dp

/**
 * One post in the results: the title with the keyword marked, then board, author, time and replies
 * on one line.
 *
 * Slimmer than the home feed's [io.github.nodyssey.ui.postlist.PostRow] on purpose. A result list is
 * scanned for the keyword, not browsed for who is talking, so the title leads and there is no avatar
 * row above it — five results fit where the feed fits four.
 */
@Composable
internal fun SearchPostCard(
    post: FeedPost,
    highlight: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = post.summary
    LayerCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ResultCardShape,
        contentPadding = ResultCardPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HighlightedText(
            text = summary.title,
            query = highlight,
            style = resultTitleStyle(),
            // A read thread keeps its card and dims its title, the same as the feed does.
            color = if (post.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (post.isRead) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 2,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardTag(title = summary.categoryTitle, slug = summary.categorySlug)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultMeta(summary.authorName, Modifier.weight(1f, fill = false))
                summary.lastActiveText?.let {
                    ResultMeta("·")
                    ResultMeta(it)
                }
            }
            if (summary.isLocked) {
                LockBadge(level = summary.lockLevel, description = lockBadgeDescription(summary.lockLevel))
            }
            if (summary.isAwarded) {
                Icon(
                    NodeSeekIcons.Award,
                    contentDescription = stringResource(Res.string.post_badge_awarded),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(textScaledSize(TITLE_BADGE_SIZE)),
                )
            }
            // The unread delta replaces the total once the thread has been read, as on the feed.
            if (post.newCommentCount > 0) {
                Text(
                    text = stringResource(Res.string.post_new_reply_count, post.newCommentCount),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                summary.commentCount?.let { ReplyCount(it) }
            }
        }
    }
}

@Composable
private fun resultTitleStyle(): TextStyle {
    // 16/24 on the artboard, scaled off the type scale so the reading-size preference reaches it.
    val base = MaterialTheme.typography.titleMedium
    return base.copy(
        fontSize = base.fontSize * RESULT_TITLE_SCALE,
        lineHeight = base.fontSize * RESULT_TITLE_SCALE * RESULT_TITLE_LINE_HEIGHT,
    )
}

private const val RESULT_TITLE_SCALE = 16f / 15f
private const val RESULT_TITLE_LINE_HEIGHT = 24f / 16f

@Composable
private fun ResultMeta(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ReplyCount(count: Int) {
    val description = stringResource(Res.string.post_reply_count, count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        // One announcement, 「42 回复」, rather than an unlabelled icon and a bare number.
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            PlazaIcons.ModeComment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(textScaledSize(TITLE_BADGE_SIZE)),
        )
        ResultMeta(count.toString())
    }
}

/** A paging placeholder the same shape as [SearchPostCard], so the real card replaces it without a jump. */
@Composable
internal fun SearchPostCardPlaceholder() {
    LayerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ResultCardShape,
        contentPadding = ResultCardPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBar(fraction = 0.9f, height = 16.dp)
        SkeletonBar(fraction = 0.6f, height = 16.dp)
        SkeletonBar(fraction = 0.45f, height = 14.dp)
    }
}

/**
 * The users a name fragment matched, as one card of rows.
 *
 * One card rather than a card each: the rows are the same kind of thing answering the same question,
 * and a stack of person-sized cards read as a feed of posts. The list is the site's whole answer —
 * `/api/account/find` returns one capped page — so drawing it as one group costs nothing a lazy
 * per-row list would save.
 */
@Composable
internal fun UserResultGroup(
    users: List<UserSearchResult>,
    highlight: String?,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        users.forEachIndexed { index, user ->
            GroupedListItem(
                first = index == 0,
                last = index == users.lastIndex,
                onClick = { onUserClick(user.uid) },
                leadingContent = { UserAvatar(url = user.avatarUrl, name = user.name, size = UserResultAvatarSize) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HighlightedText(
                            text = user.name,
                            query = highlight,
                            style = LocalTextStyle.current,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Lv beside the name, as a neutral tag: a level is a fact about the account.
                        user.level?.let {
                            TonalTag(
                                text = stringResource(Res.string.credit_level, it),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                supportingContent = { Text(userDetail(user)) },
                trailingContent = { GroupedRowTrailing(showChevron = true) },
                dividerInset = GroupDividerInset + UserResultAvatarSize + GroupDividerInset,
            )
        }
    }
}

/** UID · 主题 · 评论 · 加入 — the level moved up beside the name, so it is not repeated here. */
@Composable
private fun userDetail(user: UserSearchResult): String =
    buildList {
        add("UID ${user.uid}")
        user.topicCount?.let { add(stringResource(Res.string.search_user_topics, it)) }
        user.commentCount?.let { add(stringResource(Res.string.search_user_comments, it)) }
        user.bio?.let { add(it) }
        if (user.bio == null) {
            user.joinedText?.let { add(stringResource(Res.string.search_user_joined, it)) }
        }
    }.joinToString(" · ")

/**
 * [text] with every occurrence of [query] set on a rounded tonal mark, the way the artboards pick the
 * keyword out.
 *
 * Literal and case-insensitive, because that is what the search itself is: the site matches the raw
 * string, so a cleverer match here would mark a word the results were not chosen for.
 *
 * Drawn behind the text from its layout rather than as a `SpanStyle.background`: a span background
 * is a hard rectangle the exact height of the glyph run, and the mark on the artboard has 4dp
 * corners and a little air either side. The colour change of the matched run is still a span, so the
 * text itself stays one ordinary [Text] with ordinary semantics.
 */
@Composable
internal fun HighlightedText(
    text: String,
    query: String?,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val ranges = remember(text, query) { matchRanges(text, query) }
    val markColor = MaterialTheme.colorScheme.primaryContainer
    val onMark = MaterialTheme.colorScheme.onPrimaryContainer
    val annotated =
        remember(text, ranges, onMark) {
            buildAnnotatedString {
                append(text)
                ranges.forEach { addStyle(SpanStyle(color = onMark), it.first, it.last + 1) }
            }
        }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier =
        modifier.drawBehind {
            val result = layout ?: return@drawBehind
            ranges.forEach { drawMark(result, it, markColor) }
        },
    )
}

internal fun matchRanges(
    text: String,
    query: String?,
): List<IntRange> {
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty()) return emptyList()
    return buildList {
        var cursor = 0
        while (true) {
            val match = text.indexOf(needle, cursor, ignoreCase = true)
            if (match < 0) break
            add(match until match + needle.length)
            cursor = match + needle.length
        }
    }
}

/** One mark per line the match runs across; nothing past the last line the ellipsis left visible. */
private fun DrawScope.drawMark(
    layout: TextLayoutResult,
    range: IntRange,
    color: Color,
) {
    val visibleEnd = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
    val start = range.first
    val end = minOf(range.last + 1, visibleEnd)
    if (start >= end) return
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(end - 1)
    // 2dp rather than the artboard's 3: the mark overhangs its run rather than widening it, and a
    // CJK font's narrow space leaves less than 3dp between the keyword and its neighbour.
    val padding = 2.dp.toPx()
    val inset = 2.dp.toPx()
    for (line in firstLine..lastLine) {
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        val from = if (line == firstLine) start else layout.getLineStart(line)
        val to = if (line == lastLine) end else lineEnd
        if (from >= to) continue
        val left = layout.getHorizontalPosition(from, usePrimaryDirection = true)
        val right = if (to >= lineEnd) layout.getLineRight(line) else layout.getHorizontalPosition(to, usePrimaryDirection = true)
        val top = layout.getLineTop(line) + inset
        val bottom = layout.getLineBottom(line) - inset
        drawRoundRect(
            color = color,
            topLeft = Offset(left - padding, top),
            size = Size(right - left + padding * 2, bottom - top),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
}
