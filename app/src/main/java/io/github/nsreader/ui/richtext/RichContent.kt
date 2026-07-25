package io.github.nsreader.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.InlineStyle
import io.github.nsreader.model.RichNode
import io.github.nsreader.ui.theme.CodeStyle
import io.github.nsreader.ui.theme.PostBody

/**
 * Renders parsed post markup with real Compose text and images — no WebView.
 *
 * That keeps scrolling in one list, makes the text selectable, and lets the body follow the app's
 * own typography and theme instead of the site's stylesheet.
 */
@Composable
fun RichContent(
    nodes: List<RichNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
) {
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            nodes.forEach { node ->
                RichBlock(
                    node = node,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    textStyle = textStyle,
                )
            }
        }
    }
}

@Composable
private fun RichBlock(
    node: RichNode,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    textStyle: TextStyle,
) {
    when (node) {
        is RichNode.Paragraph -> InlineText(node.inlines, textStyle, onLinkClick)

        is RichNode.Heading -> InlineText(
            inlines = node.inlines,
            style = textStyle.copy(
                fontSize = when (node.level) {
                    1 -> 22.sp
                    2 -> 20.sp
                    3 -> 18.sp
                    else -> 17.sp
                },
                fontWeight = FontWeight.SemiBold,
            ),
            onLinkClick = onLinkClick,
        )

        is RichNode.BlockImage -> AsyncImage(
            model = node.url,
            contentDescription = node.alt,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                // Very tall screenshots would otherwise push the whole thread off screen.
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onImageClick(node.url) },
        )

        is RichNode.CodeBlock -> CodeBlock(node)

        is RichNode.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(3.dp)
                    .heightIn(min = 20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
            )
            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                node.children.forEach {
                    RichBlock(
                        node = it,
                        onLinkClick = onLinkClick,
                        onImageClick = onImageClick,
                        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }

        is RichNode.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            node.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (node.ordered) "${index + 1}." else "•",
                        style = textStyle,
                        modifier = Modifier.width(if (node.ordered) 24.dp else 16.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.forEach {
                            RichBlock(
                                node = it,
                                onLinkClick = onLinkClick,
                                onImageClick = onImageClick,
                                textStyle = textStyle,
                            )
                        }
                    }
                }
            }
        }

        is RichNode.Table -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            node.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cell ->
                        Text(text = cell, style = textStyle, modifier = Modifier.width(120.dp))
                    }
                }
            }
        }

        RichNode.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CodeBlock(node: RichNode.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        node.language?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        // Code must never reflow, so it scrolls sideways instead of wrapping.
        Text(
            text = node.code,
            style = CodeStyle,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Builds one [AnnotatedString] per paragraph so text, links and stickers share a single layout
 * pass and wrap together the way they do on the web.
 */
@Composable
private fun InlineText(
    inlines: List<InlineNode>,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
) {
    if (inlines.isEmpty()) return

    val stickers = inlines.filterIsInstance<InlineNode.Sticker>()
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    val text = buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is InlineNode.Text -> withSpan(inline.style, codeBackground) { append(inline.text) }

                is InlineNode.Link -> withLink(
                    LinkAnnotation.Url(
                        url = inline.url,
                        styles = linkStyles,
                        linkInteractionListener = { onLinkClick(inline.url) },
                    ),
                ) {
                    withSpan(inline.style, codeBackground) { append(inline.text) }
                }

                is InlineNode.Sticker -> appendInlineContent(inline.url, inline.alt ?: "[表情]")

                InlineNode.LineBreak -> append("\n")
            }
        }
    }

    val inlineContent = stickers.associate { sticker ->
        sticker.url to InlineTextContent(
            Placeholder(
                width = STICKER_SIZE,
                height = STICKER_SIZE,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            AsyncImage(
                model = sticker.url,
                contentDescription = sticker.alt,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Text(text = text, style = style, inlineContent = inlineContent)
}

private val STICKER_SIZE = 20.sp

private inline fun AnnotatedString.Builder.withSpan(
    style: InlineStyle,
    codeBackground: androidx.compose.ui.graphics.Color,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val span = SpanStyle(
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        fontFamily = if (style.code) FontFamily.Monospace else null,
        background = if (style.code) codeBackground else androidx.compose.ui.graphics.Color.Unspecified,
        textDecoration = if (style.strikethrough) TextDecoration.LineThrough else null,
    )
    pushStyle(span)
    block()
    pop()
}
