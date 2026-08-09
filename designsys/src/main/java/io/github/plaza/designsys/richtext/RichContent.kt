package io.github.plaza.designsys.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.plaza.core.image.ImagesDeferredException
import io.github.plaza.core.image.allowMeteredImage
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.InlineStyle
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.R
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkippedImagePlaceholder
import io.github.plaza.designsys.component.SpecTable
import io.github.plaza.designsys.component.TerminalGround
import io.github.plaza.designsys.component.TerminalInk
import io.github.plaza.designsys.component.asSpecTable
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberTerminalText
import io.github.plaza.designsys.theme.CodeStyle
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.PostBody
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.asProse

/**
 * Renders parsed post markup with real Compose text and images — no WebView.
 *
 * That keeps scrolling in one list, makes the text selectable, and lets the body follow the app's
 * own typography and theme instead of the site's stylesheet.
 *
 * The typographic rules here are the ones that decide whether this app is worth opening daily:
 * 16sp on a 27sp line laid out by the platform's optimal (Knuth-Plass family) line breaker, a
 * hair of air where hanzi meets Latin, block spacing that breathes around headings instead of
 * metering out a flat 10dp, code that scrolls rather than wraps, and never an italic — Chinese
 * has no italic form and synthesised slant is unreadable at body size.
 */
@Composable
fun RichContent(
    nodes: List<RichNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PostBody,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit = { onLinkClick(it.url) },
    /**
     * Draws the vote a [RichNode.VotePlaceholder] stands for.
     *
     * A slot, because a vote is a live server object and this renderer is not always somewhere that
     * may go and get one: the same function draws editor previews, signatures, direct messages and
     * space readmes, none of which has a ViewModel and none of which should be issuing requests. The
     * thread screen passes a real card; everywhere else gets the static placeholder.
     *
     * Reaches nested nodes too — a vote inside a quote, a list item or a tab is the same vote, and
     * has to be the same card. It did not, once: the recursion relied on this default instead of
     * passing the caller's slot down, so a quoted vote quietly became a dead placeholder.
     *
     * Deliberately not a `CompositionLocal`. Forgetting to provide one is invisible to the compiler
     * and to lint, and would show up only as a silently inert card at runtime.
     */
    voteContent: @Composable (Long) -> Unit = { VotePlaceholderCard() },
    codeBlockContent: @Composable (RichNode.CodeBlock) -> Unit = { CodeBlockView(it) },
) {
    // Reading upgrades happen here, at the display seam, so the same styles stay safe to reuse in
    // editors — see `TextStyle.asProse` for why an editor must never inherit them.
    val prose = remember(textStyle) { textStyle.asProse() }
    SelectionContainer(modifier = modifier) {
        RichBlockColumn(
            nodes = nodes,
            onLinkClick = onLinkClick,
            onImageClick = onImageClick,
            onQuoteRefClick = onQuoteRefClick,
            textStyle = prose,
            voteContent = voteContent,
            codeBlockContent = codeBlockContent,
        )
    }
}

/**
 * The block sequence with its vertical rhythm.
 *
 * Not a flat `spacedBy`: a heading binds to what follows it, so it takes extra air above and sits
 * tight on its section — the asymmetry is what makes chapters visible in a long post.
 */
@Composable
private fun RichBlockColumn(
    nodes: List<RichNode>,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
    textStyle: TextStyle,
    // No defaults on the private helpers, deliberately. Both slots recurse, and a default here is
    // what let a vote nested inside a quote silently fall back to the static card: the call site had
    // simply forgotten to pass it, and nothing said so. Required parameters make that a compile error.
    voteContent: @Composable (Long) -> Unit,
    codeBlockContent: @Composable (RichNode.CodeBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) Spacer(Modifier.height(blockSpacing(nodes[index - 1], node)))
            RichBlock(
                node = node,
                onLinkClick = onLinkClick,
                onImageClick = onImageClick,
                onQuoteRefClick = onQuoteRefClick,
                textStyle = textStyle,
                voteContent = voteContent,
                codeBlockContent = codeBlockContent,
            )
        }
    }
}

private fun blockSpacing(
    prev: RichNode,
    current: RichNode,
): Dp = when {
    current is RichNode.Heading -> 18.dp
    prev is RichNode.Heading -> 6.dp
    else -> 10.dp
}

@Composable
private fun RichBlock(
    node: RichNode,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
    textStyle: TextStyle,
    voteContent: @Composable (Long) -> Unit,
    codeBlockContent: @Composable (RichNode.CodeBlock) -> Unit,
) {
    when (node) {
        is RichNode.VotePlaceholder -> voteContent(node.voteId)

        is RichNode.Paragraph -> InlineText(node.inlines, textStyle, onLinkClick, onQuoteRefClick)

        is RichNode.Heading ->
            InlineText(
                inlines = node.inlines,
                style =
                textStyle.copy(
                    // A multiple of the text it heads, not a fixed sp. The same renderer draws a
                    // 16sp opening post and a signature two thirds that size, and absolute sizes
                    // made a signature's `##` line the largest type on the floor it hung off — 20sp
                    // bold over a 15sp reply, which is not what the site does with the same markup.
                    // Sized off the base, headings also finally follow the reading-size preference,
                    // which the fixed sizes ignored: at the largest setting an h1 came out *below*
                    // the body it introduced.
                    fontSize =
                    textStyle.fontSize *
                        when (node.level) {
                            1 -> 1.375f
                            2 -> 1.25f
                            3 -> 1.125f
                            else -> 1.0625f
                        },
                    // Weight, never size alone: a level-4 heading and body text are two points
                    // apart and would otherwise be indistinguishable in Chinese.
                    fontWeight = FontWeight.Bold,
                    // Balanced rather than optimal: a two-line heading with one stranded word
                    // looks worse than two even lines.
                    lineBreak = LineBreak.Heading,
                ),
                onLinkClick = onLinkClick,
                onQuoteRefClick = onQuoteRefClick,
            )

        is RichNode.BlockImage -> BlockImage(node = node, onImageClick = onImageClick)

        is RichNode.CodeBlock -> codeBlockContent(node)

        is RichNode.Quote ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .width(3.dp)
                        .heightIn(min = 20.dp)
                        // A washed-out primary rather than outlineVariant: the bar is the one mark
                        // that says "quoted", and at outline contrast it read as a stray divider.
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            RoundedCornerShape(2.dp),
                        ),
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
                            voteContent = voteContent,
                            codeBlockContent = codeBlockContent,
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
                            // Markers are scaffolding, not content, so they step back one level of
                            // contrast and let the item text carry the line.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    voteContent = voteContent,
                                    codeBlockContent = codeBlockContent,
                                )
                            }
                        }
                    }
                }
            }

        is RichNode.Table -> DataTable(node = node, onLinkClick = onLinkClick)

        is RichNode.Tabs ->
            TabGroup(
                node = node,
                onLinkClick = onLinkClick,
                onImageClick = onImageClick,
                onQuoteRefClick = onQuoteRefClick,
                textStyle = textStyle,
                voteContent = voteContent,
                codeBlockContent = codeBlockContent,
            )

        RichNode.Divider ->
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
            )
    }
}

/**
 * A tab group, one tab visible at a time.
 *
 * Showing one at a time is the whole point of the node: the posts that use it carry several reports
 * of a couple of hundred lines each, and the site files them behind tabs for the same reason. The
 * strip scrolls rather than wrapping or squeezing — four labels do not fit across 360dp.
 */
@Composable
private fun TabGroup(
    node: RichNode.Tabs,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
    textStyle: TextStyle,
    voteContent: @Composable (Long) -> Unit,
    codeBlockContent: @Composable (RichNode.CodeBlock) -> Unit,
) {
    if (node.tabs.isEmpty()) return

    var selected by rememberSaveable { mutableIntStateOf(0) }
    // Re-parsing a thread can change the tab count, and the saved index outlives the old node.
    val index = selected.coerceIn(0, node.tabs.lastIndex)

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        SecondaryScrollableTabRow(
            selectedTabIndex = index,
            containerColor = Color.Transparent,
            edgePadding = Spacing.sm,
            divider = {},
        ) {
            node.tabs.forEachIndexed { position, tab ->
                Tab(
                    selected = position == index,
                    onClick = { selected = position },
                    text = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RichBlockColumn(
            nodes = node.tabs[index].children,
            onLinkClick = onLinkClick,
            onImageClick = onImageClick,
            onQuoteRefClick = onQuoteRefClick,
            textStyle = textStyle,
            voteContent = voteContent,
            codeBlockContent = codeBlockContent,
            modifier = Modifier.padding(Spacing.md),
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
    var retryToken by remember(node.url) { mutableIntStateOf(0) }
    var phase by remember(node.url, allowMetered, retryToken) {
        mutableStateOf(InlineImagePhase.Loading)
    }

    val context = LocalContext.current
    val request =
        remember(node.url, allowMetered, retryToken) {
            ImageRequest
                .Builder(context)
                .data(node.url)
                .allowMeteredImage(allowMetered)
                .build()
        }

    when (phase) {
        InlineImagePhase.Deferred -> {
            SkippedImagePlaceholder(onLoad = { allowMetered = true })
            return
        }

        InlineImagePhase.Error -> {
            InlineImageError(onRetry = { retryToken++ })
            return
        }

        else -> Unit
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
            key(request) {
                AsyncImage(
                    model = request,
                    contentDescription = node.alt,
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    onSuccess = { success ->
                        phase = InlineImagePhase.Success
                        val image = success.result.image
                        if (image.width > 0) {
                            val scaled = availableWidth * (image.height.toFloat() / image.width.toFloat())
                            cropped = scaled > Sizes.maxInlineImageHeight
                        }
                    },
                    onError = { error ->
                        phase = if (error.result.throwable is ImagesDeferredException) {
                            InlineImagePhase.Deferred
                        } else {
                            InlineImagePhase.Error
                        }
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (phase == InlineImagePhase.Loading) {
                                Modifier.height(INLINE_IMAGE_LOADING_HEIGHT)
                            } else {
                                Modifier
                            },
                        ).heightIn(max = Sizes.maxInlineImageHeight),
                )
            }
            if (phase == InlineImagePhase.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(INLINE_IMAGE_LOADING_HEIGHT)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.richtext_image_loading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
                        text = stringResource(R.string.richtext_image_view_full),
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
private fun InlineImageError(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(INLINE_IMAGE_LOADING_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.richtext_image_load_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.richtext_action_retry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private enum class InlineImagePhase { Loading, Success, Deferred, Error }

private val INLINE_IMAGE_LOADING_HEIGHT = 132.dp

/** The language tag and the copy button on a terminal ground: present, but not competing with it. */
private const val TERMINAL_CHROME_ALPHA = 0.7f

/**
 * A fenced code block, or terminal output when it carries colour runs.
 *
 * Public because it is [RichContent]'s default `codeBlockContent`, and a caller that overrides the
 * slot for *some* blocks needs a way to say "draw the rest normally".
 */
@Composable
fun CodeBlockView(node: RichNode.CodeBlock) {
    val copy = rememberClipboardCopy()
    val confirmation = stringResource(R.string.richtext_code_copied)
    val copyLabel = stringResource(R.string.richtext_action_copy)

    // A block carrying colour runs is terminal output, and it is drawn on the terminal's own ground
    // in both themes — see [rememberTerminalText] for why that palette cannot move onto a light
    // surface. Ordinary code keeps the app's surface, where it has always been.
    val terminal = node.spans.isNotEmpty()
    val code = rememberTerminalText(node.code, node.spans)
    val ground = if (terminal) TerminalGround else MaterialTheme.colorScheme.surfaceContainer
    val ink = if (terminal) TerminalInk else MaterialTheme.colorScheme.onSurface
    val chrome = if (terminal) TerminalInk.copy(alpha = TERMINAL_CHROME_ALPHA) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ground),
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
                color = chrome,
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
                    imageVector = PlazaIcons.ContentCopy,
                    contentDescription = null,
                    tint = chrome,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = copyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome,
                )
            }
        }
        // Code must never reflow, so it scrolls sideways instead of wrapping.
        Text(
            text = code,
            style = CodeStyle,
            color = ink,
            softWrap = false,
            modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md),
        )
    }
}

/**
 * Tables are rare here and mostly numeric — latency, packet loss, prices.
 *
 * They get a border and a header fill so the columns stay legible while scrolling sideways, and
 * tabular figures so the numbers line up on their decimal point. Cells keep their links: a 拼车
 * post files its NodeQuality reports in a table column, and a link the reader can see but not
 * follow is the same as no link at all.
 */
@Composable
private fun DataTable(
    node: RichNode.Table,
    onLinkClick: (String) -> Unit,
) {
    val linkStyles =
        TextLinkStyles(
            style =
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        )
    val codeBackground = MaterialTheme.colorScheme.surfaceContainer
    // One remembered listener, for the reason spelled out in [InlineText]: a lambda built per cell
    // makes every `LinkAnnotation.Url` compare unequal and rebuilds the whole grid each pass.
    val linkListener =
        remember(onLinkClick) {
            LinkInteractionListener { link ->
                if (link is LinkAnnotation.Url) onLinkClick(link.url)
            }
        }
    val content = node.content
    val quoteLabels =
        content.flatten().flatten().filterIsInstance<InlineNode.QuoteRef>().associateWith { ref ->
            stringResource(R.string.richtext_quote_reply, ref.name, ref.floor)
        }
    val cells =
        remember(content, linkStyles, codeBackground, linkListener, quoteLabels) {
            content.map { row ->
                row.map { cellText(it, linkStyles, codeBackground, linkListener, quoteLabels) }
            }
        }
    val (columns, rows) = cells.asSpecTable()
    SpecTable(columns = columns, rows = rows)
}

/**
 * One table cell's inline content, flattened to a single line.
 *
 * A cell is a fixed-width box on one line, so the two nodes that need a layout of their own are
 * reduced to their text — a sticker to its alt, a quote reference to its label — rather than being
 * given a placeholder that would make the row taller than the rest of the grid. Links keep their
 * annotation, which is the whole reason a cell carries inline content instead of a string.
 */
private fun cellText(
    inlines: List<InlineNode>,
    linkStyles: TextLinkStyles,
    codeBackground: Color,
    linkListener: LinkInteractionListener,
    quoteLabels: Map<InlineNode.QuoteRef, String>,
): AnnotatedString = buildAnnotatedString {
    inlines.forEach { inline ->
        when (inline) {
            is InlineNode.Text -> withSpan(inline.style, codeBackground) { append(inline.text) }

            is InlineNode.Link ->
                withLink(
                    LinkAnnotation.Url(
                        url = inline.url,
                        styles = linkStyles,
                        linkInteractionListener = linkListener,
                    ),
                ) {
                    withSpan(inline.style, codeBackground) { append(inline.text) }
                }

            is InlineNode.Sticker -> append(inline.alt.orEmpty())

            is InlineNode.QuoteRef -> append(quoteLabels[inline].orEmpty())

            InlineNode.LineBreak -> append(' ')
        }
    }
}

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
                fontSize = QUOTE_LABEL_SIZE,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    val quoteLabels =
        inlines.filterIsInstance<InlineNode.QuoteRef>().associateWith { ref ->
            stringResource(R.string.richtext_quote_reply, ref.name, ref.floor)
        }

    /*
     * One listener per kind, remembered, rather than a fresh lambda per annotation.
     *
     * `LinkAnnotation.Url.equals` compares its `linkInteractionListener`, which for a lambda means
     * reference equality. A listener built inside the loop is a new instance on every composition, so
     * the whole AnnotatedString compared unequal every time: the `remember(text)` below reset the
     * layout result to null and the quote chips lost their background for a frame, `Text` could never
     * skip recomposition, and the string was rebuilt from scratch on every pass.
     *
     * Each listener recovers what it needs from the annotation it is handed — the URL sits on the
     * annotation, and the quote reference comes back through its tag.
     */
    val linkListener =
        remember(onLinkClick) {
            LinkInteractionListener { link ->
                if (link is LinkAnnotation.Url) onLinkClick(link.url)
            }
        }
    val quotesByTag =
        remember(inlines) {
            inlines.filterIsInstance<InlineNode.QuoteRef>()
                .associateBy { QUOTE_PREFIX + it.name + it.floor }
        }
    val quoteListener =
        remember(quotesByTag, onQuoteRefClick) {
            LinkInteractionListener { link ->
                if (link is LinkAnnotation.Clickable) quotesByTag[link.tag]?.let(onQuoteRefClick)
            }
        }

    // The ranges are offsets into the string the same pass produces, so the two are remembered
    // together or not at all.
    val (text, quoteRanges) =
        remember(
            inlines,
            linkStyles,
            quoteLinkStyles,
            codeBackground,
            quoteLabels,
            linkListener,
            quoteListener,
        ) {
            val ranges = mutableListOf<IntRange>()
            val built =
                buildAnnotatedString {
                    // 盘古之白: a hair of air wherever hanzi meets halfwidth letters or digits,
                    // added by the layout rather than the text, so selection and copy still hand
                    // back exactly what was posted.
                    var prevChar = '\u0000'
                    var prevIsCode = false

                    /**
                     * Records where the seams fall in [text] before it is appended, then applies
                     * the spacing spans once the characters exist. Verbatim runs (inline code) are
                     * left untouched on either side of the seam.
                     */
                    fun appendWithPangu(
                        text: String,
                        isCode: Boolean,
                        append: () -> Unit,
                    ) {
                        val seams =
                            if (isCode) {
                                emptyList()
                            } else {
                                buildList {
                                    text.forEachIndexed { offset, char ->
                                        val left =
                                            when {
                                                offset > 0 -> text[offset - 1]
                                                prevIsCode -> '\u0000'
                                                else -> prevChar
                                            }
                                        if (isPanguSeam(left, char)) add(length + offset - 1)
                                    }
                                }
                            }
                        append()
                        seams.forEach { addStyle(PANGU_SPAN, it, it + 2) }
                        if (text.isNotEmpty()) {
                            prevChar = text.last()
                            prevIsCode = isCode
                        }
                    }

                    inlines.forEach { inline ->
                        when (inline) {
                            is InlineNode.Text ->
                                appendWithPangu(inline.text, inline.style.code) {
                                    withSpan(inline.style, codeBackground) { append(inline.text) }
                                }

                            is InlineNode.Link ->
                                appendWithPangu(inline.text, inline.style.code) {
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = inline.url,
                                            styles = linkStyles,
                                            linkInteractionListener = linkListener,
                                        ),
                                    ) {
                                        withSpan(inline.style, codeBackground) { append(inline.text) }
                                    }
                                }

                            is InlineNode.Sticker -> {
                                appendInlineContent(
                                    STICKER_PREFIX + inline.url,
                                    inline.alt ?: "[表情]",
                                )
                                prevChar = '\u0000'
                                prevIsCode = false
                            }

                            is InlineNode.QuoteRef -> {
                                val start = length
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = QUOTE_PREFIX + inline.name + inline.floor,
                                        styles = quoteLinkStyles,
                                        linkInteractionListener = quoteListener,
                                    ),
                                ) {
                                    // Spaces reserve the chip's horizontal padding without introducing a
                                    // separate inline layout whose baseline can drift from this paragraph.
                                    append(QUOTE_HORIZONTAL_SPACE)
                                    append(quoteLabels.getValue(inline).replace(' ', '\u00A0'))
                                    append(QUOTE_HORIZONTAL_SPACE)
                                }
                                ranges += start until length
                                prevChar = ' '
                                prevIsCode = false
                            }

                            InlineNode.LineBreak -> {
                                append("\n")
                                prevChar = ' '
                                prevIsCode = false
                            }
                        }
                    }
                }
            built to ranges.toList()
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
                        // Sticker markup often has no alt text, and an image with no
                        // content description is silent to a screen reader.
                        contentDescription =
                        sticker.alt ?: stringResource(R.string.richtext_sticker_description),
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
            val labelCenter = QUOTE_LABEL_CENTER.toPx()
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
                    // Hung off the baseline rather than centred in the line box. The box is as tall
                    // as the *paragraph's* 16sp font plus its leading, and its middle sits well
                    // above the middle of a 12sp label — which drew the chip a couple of sp high,
                    // with the label grazing its bottom edge.
                    val centerY = layout.getLineBaseline(line) - labelCenter
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
private val QUOTE_LABEL_SIZE = 12.sp

/**
 * How far the quote label's optical middle sits above the baseline, and so where the chip drawn
 * behind it is centred.
 *
 * Just over a third of the type size is where the middle of a hanzi's em box (0.38em above the
 * baseline) and the middle of Latin cap height (0.36em) both land — close enough that one figure
 * centres a label that is usually both at once, `@某人 #12`.
 */
private val QUOTE_LABEL_CENTER = QUOTE_LABEL_SIZE * 0.37f
private const val QUOTE_HORIZONTAL_SPACE = "\u00A0\u00A0"
private const val STICKER_PREFIX = "sticker:"
private const val QUOTE_PREFIX = "quote:"

/**
 * The \u76D8\u53E4\u4E4B\u767D spacing span, sized in em so it follows the reading-size preference.
 *
 * Android letter spacing puts half the extra advance on each side of every glyph, so the span
 * covers *both* seam characters: the two inner halves meet at the seam as one full 0.125em
 * (\u22482sp at body size) gap, and only a subliminal 0.0625em leaks to the outer sides. A narrower
 * one-character span would put as much space inside the word as at the seam; a real space
 * character would survive into copied text.
 */
private val PANGU_SPAN = SpanStyle(letterSpacing = 0.125.em)

/** A seam is hanzi (or \u3007) touching a halfwidth letter or digit, in either order. */
private fun isPanguSeam(
    left: Char,
    right: Char,
): Boolean = (isCjkIdeograph(left) && isHalfwidthAlnum(right)) ||
    (isHalfwidthAlnum(left) && isCjkIdeograph(right))

private fun isCjkIdeograph(char: Char): Boolean =
    when (char.code) {
        in 0x4E00..0x9FFF, in 0x3400..0x4DBF, in 0xF900..0xFAFF, 0x3007 -> true
        else -> false
    }

private fun isHalfwidthAlnum(char: Char): Boolean =
    char in '0'..'9' || char in 'A'..'Z' || char in 'a'..'z'

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
                InlineNode.Sticker(url = "https://example.invalid/sticker/1.png", alt = "笑"),
                InlineNode.Text(" 与文字基线对齐、不撑高行高。"),
            ),
        ),
        RichNode.Paragraph(
            listOf(
                InlineNode.QuoteRef(name = "酒神", floor = "#12", url = "/post-1-1#12"),
                InlineNode.Text(" 引用回复渲染成可点的 tonal 标识，点击跳到对应楼层，而不是一条普通蓝链接。"),
            ),
        ),
        RichNode.Heading(
            inlines = listOf(InlineNode.Text("章节标题：上方 18dp、下方 6dp 的不对称节奏")),
            level = 2,
        ),
        RichNode.Paragraph(
            listOf(
                InlineNode.Text(
                    "中西混排如 4C8G 的 VPS 跑 iperf3 得到 2.5Gbps:汉字与字母数字交界处由排版层" +
                        "补一丝空隙,复制出的文本不含多余字符;整段由最优断行器排布,右缘参差最小化。",
                ),
            ),
        ),
        RichNode.BlockImage(url = "https://example.invalid/demo.png", alt = "示例截图"),
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
            cells =
            listOf(
                listOf("节点", "延迟", "丢包", "报告").map { listOf(InlineNode.Text(it)) },
                listOf(
                    listOf(InlineNode.Text("东京 TRI")),
                    listOf(InlineNode.Text("48ms")),
                    listOf(InlineNode.Text("0%")),
                    listOf(InlineNode.Link(text = "NQ", url = "https://nodequality.com/r/demo")),
                ),
                listOf("洛杉矶 4837", "152ms", "1.2%", "—").map { listOf(InlineNode.Text(it)) },
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
            text = "正文排版规范 · 16sp / 行高 27 / 最优断行 / 盘古之白 / 段距 10 · 标题前 18",
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
    PlazaTheme { RichContentSpec() }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 1240, name = "Body typography spec · dark")
@Composable
private fun RichContentSpecDarkPreview() {
    PlazaTheme(darkTheme = true) { RichContentSpec() }
}
