package io.github.nodyssey.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import io.github.nodyssey.ui.common.PostBadges
import io.github.nodyssey.ui.common.PostReplyStat
import io.github.nodyssey.ui.common.postCardTitleStyle
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.credit_level
import io.github.nodyssey.ui.resources.search_user_comments
import io.github.nodyssey.ui.resources.search_user_joined
import io.github.nodyssey.ui.resources.search_user_topics
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRowTrailing
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import org.jetbrains.compose.resources.stringResource

/** A result card is a step smaller than the feed's, so its corners are a step tighter: `shapes.large`. */
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
        shape = MaterialTheme.shapes.large,
        contentPadding = ResultCardPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HighlightedText(
            text = summary.title,
            query = highlight,
            style = postCardTitleStyle(sizeSp = 16f, lineHeightSp = 24f),
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
                MetaText(summary.authorName, Modifier.weight(1f, fill = false), singleLine = true)
                summary.lastActiveText?.let {
                    MetaText("·", singleLine = true)
                    MetaText(it, singleLine = true)
                }
            }
            PostBadges(summary)
            PostReplyStat(post)
        }
    }
}

/** A paging placeholder the same shape as [SearchPostCard], so the real card replaces it without a jump. */
@Composable
internal fun SearchPostCardPlaceholder() {
    LayerCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
