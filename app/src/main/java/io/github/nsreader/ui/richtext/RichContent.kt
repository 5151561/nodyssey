package io.github.nsreader.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.nsreader.R
import io.github.nsreader.core.image.ImagesDeferredException
import io.github.nsreader.core.image.allowMeteredImage
import io.github.nsreader.model.InlineNode
import io.github.nsreader.model.InlineStyle
import io.github.nsreader.model.RichNode
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.SkippedImagePlaceholder
import io.github.nsreader.ui.common.rememberClipboardCopy
import io.github.nsreader.ui.theme.CodeStyle
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.PostBody
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.TABULAR_FIGURES

/**
 * Renders parsed post markup with real Compose text and images — no WebView.
 *
 * That keeps scrolling in one list, makes the text selectable, and lets the body follow the app's
 * own typography and theme instead of the site's stylesheet.
 *
 * The typographic rules here are the ones that decide whether this app is worth opening daily:
 * 16sp on a 27sp line, 10dp between blocks, code that scrolls rather than wraps, and never an
 * italic — Chinese has no italic form and synthesised slant is unreadable at body size.
 */
@Composable
fun RichContent(
    nodes: List<RichNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit = { onLinkClick(it.url) },
) {
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            nodes.forEach { node ->
                RichBlock(
                    node = node,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    onQuoteRefClick = onQuoteRefClick,
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
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
    textStyle: TextStyle,
) {
    when (node) {
        is RichNode.Paragraph -> InlineText(node.inlines, textStyle, onLinkClick, onQuoteRefClick)

        is RichNode.Heading ->
            InlineText(
                inlines = node.inlines,
                style =
                textStyle.copy(
                    fontSize =
                    when (node.level) {
                        1 -> 22.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> 17.sp
                    },
                    // Weight, never size alone: a level-4 heading and body text are two points
                    // apart and would otherwise be indistinguishable in Chinese.
                    fontWeight = FontWeight.Bold,
                ),
                onLinkClick = onLinkClick,
                onQuoteRefClick = onQuoteRefClick,
            )

        is RichNode.BlockImage -> BlockImage(node = node, onImageClick = onImageClick)

        is RichNode.CodeBlock -> CodeBlock(node)

        is RichNode.Quote ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(3.dp)
                        .heightIn(min = 20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                )
                Column(
                    modifier = Modifier.padding(start = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    node.children.forEach {
                        RichBlock(
                            node = it,
                            onLinkClick = onLinkClick,
                            onImageClick = onImageClick,
                            onQuoteRefClick = onQuoteRefClick,
                            // One step down in size and contrast, which is the whole signal that
                            // this text is someone else's.
                            textStyle =
                            textStyle.copy(
                                fontSize = textStyle.fontSize * 0.94f,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

        is RichNode.ListBlock ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                node.items.forEachIndexed { index, item ->
                    Row {
                        Text(
                            text = if (node.ordered) "${index + 1}." else "•",
                            style = textStyle.copy(fontFeatureSettings = TABULAR_FIGURES),
                            // Right-aligned numbers keep "9." and "10." on the same left edge.
                            textAlign = if (node.ordered) TextAlign.End else TextAlign.Center,
                            modifier = Modifier.width(if (node.ordered) 20.dp else 16.dp),
                        )
                        Column(
                            modifier = Modifier.padding(start = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            item.forEach {
                                RichBlock(
                                    node = it,
                                    onLinkClick = onLinkClick,
                                    onImageClick = onImageClick,
                                    onQuoteRefClick = onQuoteRefClick,
                                    textStyle = textStyle,
                                )
                            }
                        }
                    }
                }
            }

        is RichNode.Table -> DataTable(node, textStyle)

        RichNode.Divider ->
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
            )
    }
}

/**
 * A full-width image, capped in height.
 *
 * Screenshots of benchmark output are the single most common attachment on this forum and are often
 * three screens tall; letting one push the whole thread out of view is worse than cropping it. The
 * crop keeps the top — where the interesting part always is — and says so.
 *
 * When 仅 Wi-Fi 加载图片 skips the image it is replaced by [SkippedImagePlaceholder] rather than by
 * nothing, and a tap on that placeholder re-requests the image with the preference waived for this
 * one image — see [allowMeteredImage].
 */
@Composable
private fun BlockImage(
    node: RichNode.BlockImage,
    onImageClick: (String) -> Unit,
) {
    var cropped by remember(node.url) { mutableStateOf(false) }
    var allowMetered by remember(node.url) { mutableStateOf(false) }
    // Reset with the request: once the user waives the preference the placeholder must give way
    // even before the new request reports back, or the tap looks like it did nothing.
    var skipped by remember(node.url, allowMetered) { mutableStateOf(false) }

    val context = LocalContext.current
    val request =
        remember(node.url, allowMetered) {
            ImageRequest
                .Builder(context)
                .data(node.url)
                .allowMeteredImage(allowMetered)
                .build()
        }

    if (skipped) {
        SkippedImagePlaceholder(onLoad = { allowMetered = true })
        return
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onImageClick(node.url) },
        ) {
            AsyncImage(
                model = request,
                contentDescription = node.alt,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                onSuccess = { success ->
                    val image = success.result.image
                    if (image.width > 0) {
                        val scaled = availableWidth * (image.height.toFloat() / image.width.toFloat())
                        cropped = scaled > Sizes.maxInlineImageHeight
                    }
                },
                onError = { error ->
                    skipped = error.result.throwable is ImagesDeferredException
                },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = Sizes.maxInlineImageHeight),
            )
            if (cropped) {
                Box(
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            ),
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = stringResource(R.string.post_image_view_full),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(node: RichNode.CodeBlock) {
    val copy = rememberClipboardCopy()
    val confirmation = stringResource(R.string.post_code_copied)
    val copyLabel = stringResource(R.string.action_copy)

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = node.language.orEmpty(),
                style = CodeStyle.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { copy("code", node.code, confirmation) }
                    // A hand-rolled Row gets none of the padding a Material component applies for
                    // it, and a 15dp glyph with 4dp above and below is a 23dp target — half of
                    // what `Sizes.minTouchTarget` calls the brief's hard requirement.
                    .defaultMinSize(minHeight = Sizes.minTouchTarget)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = NodeSeekIcons.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = copyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Code must never reflow, so it scrolls sideways instead of wrapping.
        Text(
            text = node.code,
            style = CodeStyle,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md),
        )
    }
}

/**
 * Tables are rare here and always numeric — latency, packet loss, prices.
 *
 * They get a border and a header fill so the columns stay legible while scrolling sideways, and
 * tabular figures so the numbers line up on their decimal point.
 */
@Composable
private fun DataTable(
    node: RichNode.Table,
    textStyle: TextStyle,
) {
    if (node.rows.isEmpty()) return

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .horizontalScroll(rememberScrollState()),
    ) {
        node.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier =
                Modifier.background(
                    if (rowIndex == 0) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        Color.Transparent
                    },
                ),
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style =
                        textStyle.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                            fontFeatureSettings = TABULAR_FIGURES,
                        ),
                        modifier = Modifier
                            .width(120.dp)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * Builds one [AnnotatedString] per paragraph so text, links, stickers and quote references share a
 * single layout pass and wrap together the way they do on the web.
 */
@Composable
private fun InlineText(
    inlines: List<InlineNode>,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
) {
    if (inlines.isEmpty()) return

    val linkStyles =
        TextLinkStyles(
            style =
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        )
    val codeBackground = MaterialTheme.colorScheme.surfaceContainer
    val quoteBackground = MaterialTheme.colorScheme.primaryContainer
    val quoteLinkStyles =
        TextLinkStyles(
            style =
            SpanStyle(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    val quoteLabels =
        inlines.filterIsInstance<InlineNode.QuoteRef>().associateWith { ref ->
            stringResource(R.string.post_quote_reply, ref.name, ref.floor)
        }
    val quoteRanges = mutableListOf<IntRange>()

    val text =
        buildAnnotatedString {
            inlines.forEach { inline ->
                when (inline) {
                    is InlineNode.Text -> withSpan(inline.style, codeBackground) { append(inline.text) }

                    is InlineNode.Link ->
                        withLink(
                            LinkAnnotation.Url(
                                url = inline.url,
                                styles = linkStyles,
                                linkInteractionListener = { onLinkClick(inline.url) },
                            ),
                        ) {
                            withSpan(inline.style, codeBackground) { append(inline.text) }
                        }

                    is InlineNode.Sticker ->
                        appendInlineContent(
                            STICKER_PREFIX + inline.url,
                            inline.alt ?: "[表情]",
                        )

                    is InlineNode.QuoteRef -> {
                        val start = length
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = QUOTE_PREFIX + inline.name + inline.floor,
                                styles = quoteLinkStyles,
                                linkInteractionListener = { onQuoteRefClick(inline) },
                            ),
                        ) {
                            // Spaces reserve the chip's horizontal padding without introducing a
                            // separate inline layout whose baseline can drift from this paragraph.
                            append(QUOTE_HORIZONTAL_SPACE)
                            append(quoteLabels.getValue(inline).replace(' ', '\u00A0'))
                            append(QUOTE_HORIZONTAL_SPACE)
                        }
                        quoteRanges += start until length
                    }

                    InlineNode.LineBreak -> append("\n")
                }
            }
        }

    val stickerContent =
        inlines.filterIsInstance<InlineNode.Sticker>().associate { sticker ->
            STICKER_PREFIX + sticker.url to
                InlineTextContent(
                    Placeholder(
                        width = STICKER_SIZE,
                        height = STICKER_SIZE,
                        // Centred on the text, not the baseline: a 20sp box hung off the baseline
                        // would push the whole line taller and break the 27sp rhythm.
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    AsyncImage(
                        model = sticker.url,
                        // NodeSeek's sticker markup often has no alt text, and an image with no
                        // content description is silent to a screen reader.
                        contentDescription =
                        sticker.alt ?: stringResource(R.string.image_description_sticker),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
        }

    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        inlineContent = stickerContent,
        onTextLayout = { textLayoutResult = it },
        modifier =
        Modifier.drawBehind {
            val layout = textLayoutResult ?: return@drawBehind
            val chipHeight = QUOTE_HEIGHT.toPx()
            quoteRanges.forEach { range ->
                var start = range.first
                while (start <= range.last) {
                    val line = layout.getLineForOffset(start)
                    var end = start + 1
                    while (end <= range.last && layout.getLineForOffset(end) == line) end++

                    val first = layout.getBoundingBox(start)
                    val last = layout.getBoundingBox(end - 1)
                    val left = minOf(first.left, last.left)
                    val right = maxOf(first.right, last.right)
                    val centerY = (first.top + first.bottom) / 2f
                    drawRoundRect(
                        color = quoteBackground,
                        topLeft = Offset(left, centerY - chipHeight / 2f),
                        size = Size(right - left, chipHeight),
                        cornerRadius = CornerRadius(chipHeight / 2f),
                    )
                    start = end
                }
            }
        },
    )
}

private val STICKER_SIZE = 20.sp
private val QUOTE_HEIGHT = 22.sp
private const val QUOTE_HORIZONTAL_SPACE = "\u00A0\u00A0"
private const val STICKER_PREFIX = "sticker:"
private const val QUOTE_PREFIX = "quote:"

private inline fun AnnotatedString.Builder.withSpan(
    style: InlineStyle,
    codeBackground: Color,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val span =
        SpanStyle(
            // Italic is deliberately absent. The site's markdown allows it, Chinese type has no
            // italic form, and a synthesised slant at 16sp is close to illegible — so emphasis
            // becomes weight instead, which is what the design's typography rules require.
            fontWeight =
            when {
                style.bold -> FontWeight.Bold
                style.italic -> FontWeight.Medium
                else -> null
            },
            fontFamily = if (style.code) FontFamily.Monospace else null,
            background = if (style.code) codeBackground else Color.Unspecified,
            textDecoration = if (style.strikethrough) TextDecoration.LineThrough else null,
        )
    pushStyle(span)
    block()
    pop()
}

// -------------------------------------------------------------------------------------------------
// The typography spec, as a preview.
//
// Every rich-text element the parser can emit, on one screen. It lives here rather than in a design
// file so that changing a rule and forgetting to check the others is not possible: this preview
// renders the real composable, so it goes wrong the moment the rules stop agreeing.
// -------------------------------------------------------------------------------------------------

private fun specNodes(): List<RichNode> =
    listOf(
        RichNode.Paragraph(
            listOf(
                InlineNode.Text(
                    "这是一段标准正文。中文长文阅读是这个客户端的核心场景，行高 1.69、段间距 10dp，" +
                        "保证连续阅读半小时不累。行内可以出现 ",
                ),
                InlineNode.Link(text = "nodequality.app", url = "https://nodequality.app"),
                InlineNode.Text(" 这样的链接，以及表情 "),
                InlineNode.Sticker(url = "https://www.nodeseek.com/static/image/sticker/1.png", alt = "笑"),
                InlineNode.Text(" 与文字基线对齐、不撑高行高。"),
            ),
        ),
        RichNode.Paragraph(
            listOf(
                InlineNode.QuoteRef(name = "酒神", floor = "#12", url = "/post-1-1#12"),
                InlineNode.Text(" 引用回复渲染成可点的 tonal 标识，点击跳到对应楼层，而不是一条普通蓝链接。"),
            ),
        ),
        RichNode.BlockImage(url = "https://www.nodeseek.com/static/image/demo.png", alt = "示例截图"),
        RichNode.CodeBlock(
            code = "curl -sL https://run.nodequality.com/v1 | bash\n# 输出结果复制为 Markdown，横向滚动不换行 →→→→→→→→",
            language = "bash",
        ),
        RichNode.Quote(
            listOf(
                RichNode.Paragraph(
                    listOf(InlineNode.Text("引用块：左侧竖线 + 缩进 + 降低一档对比度，嵌套引用继续缩进。")),
                ),
            ),
        ),
        RichNode.ListBlock(
            ordered = true,
            items =
            listOf(
                listOf(RichNode.Paragraph(listOf(InlineNode.Text("有序列表项目符号右对齐")))),
                listOf(RichNode.Paragraph(listOf(InlineNode.Text("第二项")))),
            ),
        ),
        RichNode.ListBlock(
            ordered = false,
            items = listOf(listOf(RichNode.Paragraph(listOf(InlineNode.Text("无序列表用实心圆点"))))),
        ),
        RichNode.Table(
            rows =
            listOf(
                listOf("节点", "延迟", "丢包"),
                listOf("东京 TRI", "48ms", "0%"),
                listOf("洛杉矶 4837", "152ms", "1.2%"),
            ),
        ),
        RichNode.Divider,
        RichNode.Paragraph(
            listOf(
                InlineNode.Text("行内代码 "),
                InlineNode.Text("sortBy=postTime", InlineStyle(code = true)),
                InlineNode.Text(" 用容器底色，"),
                InlineNode.Text("加粗", InlineStyle(bold = true)),
                InlineNode.Text("用 700 字重（不用斜体）。"),
            ),
        ),
    )

@Composable
private fun RichContentSpec() {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        Text(
            text = "正文排版规范 · 16sp / 行高 27 / 字距 +0.2 / 段距 10",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        RichContent(nodes = specNodes(), onLinkClick = {}, onImageClick = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1240, name = "Body typography spec")
@Composable
private fun RichContentSpecPreview() {
    NodeSeekTheme { RichContentSpec() }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1240, name = "Body typography spec · dark")
@Composable
private fun RichContentSpecDarkPreview() {
    NodeSeekTheme(darkTheme = true) { RichContentSpec() }
}
