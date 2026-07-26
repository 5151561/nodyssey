package io.github.nsreader.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.nsreader.R
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.richtext.RichContent
import io.github.nsreader.ui.theme.PostBody
import io.github.nsreader.ui.theme.Spacing

/**
 * The site's right-hand rules card, moved to the top of the publish preview.
 *
 * Global and permanent, not per-board and not dismissible — that was the correction in §0.8 of the
 * requirements against the earlier drafts, which had it as a 技术-only notice with a close button.
 */
@Composable
fun RuleReminderCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
        ) {
            Icon(NodeSeekIcons.Campaign, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.composer_rule_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.composer_rule_body),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Renders draft Markdown with the reading screen's own typography.
 *
 * The point of a preview is to be wrong in none of the ways that matter, so it goes through the
 * same [RichContent] the detail screen uses rather than a lighter-weight renderer — a code block
 * that wraps differently here than after publishing is a preview that cannot be trusted.
 */
@Composable
fun MarkdownPreviewBody(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
) {
    val uriHandler = LocalUriHandler.current
    val nodes = remember(markdown) { parseMarkdown(markdown) }
    if (nodes.isEmpty()) {
        Text(
            text = stringResource(R.string.composer_preview_empty),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val openExternally: (String) -> Unit = { url ->
        if (NodeSeekSite.isExternalWebUrl(url)) runCatching { uriHandler.openUri(url) }
    }
    RichContent(
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
    authorName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        boardTitle?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
        authorName?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.composer_just_now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
