package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.NodeSeekStickers
import io.github.nodyssey.ui.common.BoardTag
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.composer_just_now
import io.github.nodyssey.ui.resources.composer_preview_empty
import io.github.nodyssey.ui.resources.composer_rule_body
import io.github.nodyssey.ui.resources.composer_rule_title
import io.github.nodyssey.ui.richtext.PostRichContent
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.InlineBanner
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PostBody
import io.github.plaza.designsys.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The site's right-hand rules card, moved to the top of the publish preview.
 *
 * Global and permanent, not per-board and not dismissible — that was the correction in §0.8 of the
 * requirements against the earlier drafts, which had it as a 技术-only notice with a close button.
 */
@Composable
fun RuleReminderCard(modifier: Modifier = Modifier) {
    InlineBanner(
        title = stringResource(Res.string.composer_rule_title),
        text = stringResource(Res.string.composer_rule_body),
        icon = PlazaIcons.Campaign,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier,
    )
}

/**
 * Renders draft Markdown with the reading screen's own typography.
 *
 * The point of a preview is to be wrong in none of the ways that matter, so it goes through the
 * same [RichContent] the detail screen uses rather than a lighter-weight renderer — a code block
 * that wraps differently here than after publishing is a preview that cannot be trusted.
 *
 * Stickers are one of those ways. The emoji panel sits on this very editor and inserts `:ac01:`,
 * which the site expands when the post is published — so a preview without the resolver shows six
 * characters of punctuation where the published post will show a picture, on the one screen whose
 * whole job is to say what the post will look like.
 */
@Composable
fun MarkdownPreviewBody(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
) {
    val uriHandler = LocalUriHandler.current
    val nodes = remember(markdown) { parseMarkdown(markdown, NodeSeekStickers::urlFor) }
    if (nodes.isEmpty()) {
        Text(
            text = stringResource(Res.string.composer_preview_empty),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val openExternally: (String) -> Unit = { url ->
        if (NodeSeekSite.isExternalWebUrl(url)) runCatching { uriHandler.openUri(url) }
    }
    PostRichContent(
        nodes = nodes,
        onLinkClick = openExternally,
        onImageClick = openExternally,
        textStyle = textStyle,
        modifier = modifier,
    )
}

/** The board chip + author + "刚刚" line under the preview title (7b). */
@Composable
fun PreviewByline(
    boardTitle: String?,
    boardSlug: String?,
    authorName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoardTag(title = boardTitle, slug = boardSlug)
        authorName?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(Res.string.composer_just_now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
