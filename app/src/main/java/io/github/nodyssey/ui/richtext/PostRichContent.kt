package io.github.nodyssey.ui.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.github.nodyssey.core.report.QualityReportParser
import io.github.nodyssey.data.settings.ReportFormat
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.richtext.CodeBlockView
import io.github.plaza.designsys.richtext.RichContent
import io.github.plaza.designsys.richtext.VotePlaceholderCard
import io.github.plaza.designsys.theme.PostBody

/**
 * [RichContent] with the one thing NodeSeek adds to a code block: benchmark reports.
 *
 * The renderer in `:designsys` draws a fenced block as a fenced block, which is all it can honestly
 * do — that a particular eighty-column ASCII layout is a NodeQuality report, and that the app knows
 * how to redraw it as cards, is knowledge about the 测评 board. It arrives through the
 * `codeBlockContent` slot.
 *
 * Every screen goes through this rather than calling [RichContent] directly, because a report can
 * turn up anywhere a code block can — a direct message, a signature, a space readme — and a wrapper
 * used by six screens out of seven would be a bug waiting for the seventh.
 */
@Composable
fun PostRichContent(
    nodes: List<RichNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit = { onLinkClick(it.url) },
    voteContent: @Composable (Long) -> Unit = { VotePlaceholderCard() },
) {
    RichContent(
        nodes = nodes,
        onLinkClick = onLinkClick,
        onImageClick = onImageClick,
        modifier = modifier,
        textStyle = textStyle,
        onQuoteRefClick = onQuoteRefClick,
        voteContent = voteContent,
        codeBlockContent = { CodeOrReport(it) },
    )
}

/**
 * Draws a benchmark report as a report and everything else as code.
 *
 * The test is whether [QualityReportParser] can make sense of the text, not what language the site
 * tagged it with: `language-ansi` is also how an unrelated coloured terminal paste arrives, and that
 * is not a report. Parsing is cheap next to the layout it feeds and is remembered on the node, so a
 * scroll past a thread of them does not repeat it.
 *
 * It is still parsed under 原文 ([ReportFormat.SOURCE]): the parse is what says this block is a
 * report at all rather than an ordinary code block, and its title is what the inline block is
 * labelled with.
 */
@Composable
private fun CodeOrReport(node: RichNode.CodeBlock) {
    val report = remember(node) { QualityReportParser.parse(node.code, node.spans) }
    if (report == null) {
        CodeBlockView(node)
        return
    }

    var showingSource by rememberSaveable(node.code) { mutableStateOf(false) }

    when (LocalReportFormat.current) {
        ReportFormat.ADAPTED -> ReportCard(report = report, onShowSource = { showingSource = true })

        ReportFormat.SOURCE ->
            ReportSourceBlock(
                title = report.title,
                source = node.code,
                spans = node.spans,
                columns = node.columns,
                onExpand = { showingSource = true },
            )
    }

    if (showingSource) {
        ReportSourceDialog(
            title = report.title,
            source = node.code,
            spans = node.spans,
            columns = node.columns,
            onDismiss = { showingSource = false },
        )
    }
}
