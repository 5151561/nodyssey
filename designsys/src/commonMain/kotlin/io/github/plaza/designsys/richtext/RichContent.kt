package io.github.plaza.designsys.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.InlineStyle
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.SkippedImagePlaceholder
import io.github.plaza.designsys.component.SpecTable
import io.github.plaza.designsys.component.TerminalGround
import io.github.plaza.designsys.component.TerminalInk
import io.github.plaza.designsys.component.WrapCell
import io.github.plaza.designsys.component.WrapCellImage
import io.github.plaza.designsys.component.WrapTable
import io.github.plaza.designsys.component.asSpecTable
import io.github.plaza.designsys.component.imageLoadFailureText
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberTerminalText
import io.github.plaza.designsys.component.specTableFits
import io.github.plaza.designsys.image.ImageLoadFailure
import io.github.plaza.designsys.image.ImagesDeferredException
import io.github.plaza.designsys.image.allowMeteredImage
import io.github.plaza.designsys.image.diagnoseImageFailure
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.richtext_action_copy
import io.github.plaza.designsys.resources.richtext_action_open_in_browser
import io.github.plaza.designsys.resources.richtext_action_retry
import io.github.plaza.designsys.resources.richtext_code_copied
import io.github.plaza.designsys.resources.richtext_fold_collapse
import io.github.plaza.designsys.resources.richtext_fold_expand
import io.github.plaza.designsys.resources.richtext_fold_title_fallback
import io.github.plaza.designsys.resources.richtext_image_label
import io.github.plaza.designsys.resources.richtext_image_load_failed
import io.github.plaza.designsys.resources.richtext_image_loading
import io.github.plaza.designsys.resources.richtext_image_view_full
import io.github.plaza.designsys.resources.richtext_quote_reply
import io.github.plaza.designsys.resources.richtext_sticker_description
import io.github.plaza.designsys.theme.CodeStyle
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.PostBody
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.asProse
import org.jetbrains.compose.resources.stringResource
import coil3.size.Size as CoilSize

/**
 * Renders parsed post markup with real Compose text and images — no WebView.
 *
 * That keeps scrolling in one list, makes the text selectable, and lets the body follow the app's
 * own typography and theme instead of the site's stylesheet.
 *
 * The typographic rules here are the ones that decide whether this app is worth opening daily:
 * 16sp on a 27sp line broken greedily, the way a browser breaks it and for the reason spelled out
 * in `TextStyle.asProse` — headings included, which is why nothing here overrides `lineBreak`. A
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
    /**
     * Draws a [RichNode.StardustReceive].
     *
     * A slot for the same reason [voteContent] is one, but with a default that is the whole card
     * rather than a stand-in: a receive code's marker carries everything the card says, so the five
     * callers with no ViewModel lose only the live tally and the 付款 button. The thread screen
     * passes a version that has both.
     *
     * The default draws no avatar. Turning a uid into a picture URL is a fact about a particular
     * forum, and this module does not know one — see the app's `PostRichContent`, which does.
     */
    stardustContent: @Composable (RichNode.StardustReceive) -> Unit = { StardustReceiveCard(it) },
    /**
     * Whether a long press inside the body starts a text selection.
     *
     * On everywhere a body is read. Off where the long press is already spoken for: a direct-message
     * bubble puts 复制 and 引用 on it, and a selection handle rising under the finger on the Markdown
     * bubbles while the plain ones did nothing was exactly the asymmetry that menu exists to end.
     */
    selectable: Boolean = true,
) {
    // Reading upgrades happen here, at the display seam, so the same styles stay safe to reuse in
    // editors — see `TextStyle.asProse` for why an editor must never inherit them.
    val prose = remember(textStyle) { textStyle.asProse() }
    val blocks: @Composable (Modifier) -> Unit = { blockModifier ->
        RichBlockColumn(
            nodes = nodes,
            onLinkClick = onLinkClick,
            onImageClick = onImageClick,
            onQuoteRefClick = onQuoteRefClick,
            textStyle = prose,
            voteContent = voteContent,
            codeBlockContent = codeBlockContent,
            stardustContent = stardustContent,
            modifier = blockModifier,
        )
    }
    // The container is what a selection needs, so it goes away entirely rather than being told to
    // select nothing — `DisableSelection` inside one would still leave the gesture detector there.
    if (selectable) {
        SelectionContainer(modifier = modifier) { blocks(Modifier) }
    } else {
        blocks(modifier)
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
    stardustContent: @Composable (RichNode.StardustReceive) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Consecutive images are one unit, not a stack of blocks. A run of badges — the `runs` and
    // `license` shields at the top of half the script posts here — is written as images side by
    // side, and giving each its own row read as a column of banners where the site shows one line
    // of small marks. Inside the unit they flow: small images share a row, a full-width screenshot
    // still takes its own.
    val units =
        remember(nodes) {
            buildList<MutableList<RichNode>> {
                nodes.forEach { node ->
                    if (node is RichNode.BlockImage && lastOrNull()?.last() is RichNode.BlockImage) {
                        last() += node
                    } else {
                        add(mutableListOf(node))
                    }
                }
            }
        }
    Column(modifier = modifier) {
        units.forEachIndexed { index, unit ->
            if (index > 0) Spacer(Modifier.height(blockSpacing(units[index - 1].last(), unit.first())))
            val images = unit.filterIsInstance<RichNode.BlockImage>()
            if (images.size > 1) {
                ImageFlow(images = images, onImageClick = onImageClick, onLinkClick = onLinkClick)
            } else {
                RichBlock(
                    node = unit.single(),
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    onQuoteRefClick = onQuoteRefClick,
                    textStyle = textStyle,
                    voteContent = voteContent,
                    codeBlockContent = codeBlockContent,
                    stardustContent = stardustContent,
                )
            }
        }
    }
}

/** A run of adjacent images: badges flow onto one line, full-width screenshots keep their own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageFlow(
    images: List<RichNode.BlockImage>,
    onImageClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        images.forEach { image ->
            BlockImage(node = image, onImageClick = onImageClick, onLinkClick = onLinkClick)
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
    stardustContent: @Composable (RichNode.StardustReceive) -> Unit,
) {
    when (node) {
        is RichNode.VotePlaceholder -> voteContent(node.voteId)

        is RichNode.StardustReceive -> stardustContent(node)

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
                    // No line-break override: a heading breaks the way the body around it does,
                    // which `asProse` has already set to greedy. This used to force
                    // `LineBreak.Heading`, whose Balanced strategy evens the lines out — and even
                    // lines in Chinese means a short one. post-584268's `GitHub项目地址（欢迎Star
                    // 关注）： <url>` came out filling 0.69 of the column against greedy's 0.92,
                    // with `关注）：` pushed onto the next line while 305px sat empty beside it.
                    // See `ProseLineBreakTest`.
                ),
                onLinkClick = onLinkClick,
                onQuoteRefClick = onQuoteRefClick,
            )

        is RichNode.BlockImage -> BlockImage(node = node, onImageClick = onImageClick, onLinkClick = onLinkClick)

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
                            stardustContent = stardustContent,
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
                                    stardustContent = stardustContent,
                                )
                            }
                        }
                    }
                }
            }

        is RichNode.Table -> DataTable(node = node, onLinkClick = onLinkClick, onImageClick = onImageClick)

        is RichNode.Tabs ->
            TabGroup(
                node = node,
                onLinkClick = onLinkClick,
                onImageClick = onImageClick,
                onQuoteRefClick = onQuoteRefClick,
                textStyle = textStyle,
                voteContent = voteContent,
                codeBlockContent = codeBlockContent,
                stardustContent = stardustContent,
            )

        is RichNode.Fold ->
            FoldBlock(
                node = node,
                onLinkClick = onLinkClick,
                onImageClick = onImageClick,
                onQuoteRefClick = onQuoteRefClick,
                textStyle = textStyle,
                voteContent = voteContent,
                codeBlockContent = codeBlockContent,
                stardustContent = stardustContent,
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
    stardustContent: @Composable (RichNode.StardustReceive) -> Unit,
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
            stardustContent = stardustContent,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

/**
 * A `<details>` block, closed until the reader asks for it.
 *
 * Starting closed is the point of the node — the author folded this away — so the summary is all
 * that is on screen, and everything below it is drawn only once the block is open. That also keeps
 * a fold holding a tab group or a 200-line report off the first frame of a long thread.
 *
 * Hand-rolled rather than composed out of an official component: Material 3 1.5.0-alpha24 ships no
 * disclosure/accordion — `ExpandedListTokens` exists in `material3.tokens` but no composable reads
 * it, so there is nothing public to call. Replace this with that component when one lands. The
 * shape, the container colour and the chevron behaviour are deliberately the same as
 * `ReportCard`'s, which is the app's other collapsible block.
 */
@Composable
private fun FoldBlock(
    node: RichNode.Fold,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onQuoteRefClick: (InlineNode.QuoteRef) -> Unit,
    textStyle: TextStyle,
    voteContent: @Composable (Long) -> Unit,
    codeBlockContent: @Composable (RichNode.CodeBlock) -> Unit,
    stardustContent: @Composable (RichNode.StardustReceive) -> Unit,
) {
    var expanded by rememberSaveable(node.title) { mutableStateOf(node.open) }
    // The chevron turns rather than flipping, for the reason `ReportCard` gives: an indicator that
    // snaps while the block below it slides reads as two separate events.
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "fold-chevron",
    )

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .defaultMinSize(minHeight = Sizes.minTouchTarget)
                .padding(start = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // A `<summary>` may be empty, and an unlabelled row gives the reader nothing to
                // decide with — the browser falls back to its own word here for the same reason.
                text = node.title.ifBlank { stringResource(Res.string.richtext_fold_title_fallback) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.sm),
            )
            Box(
                modifier = Modifier.size(Sizes.minTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription =
                    stringResource(
                        if (expanded) Res.string.richtext_fold_collapse else Res.string.richtext_fold_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronAngle),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RichBlockColumn(
                    nodes = node.children,
                    onLinkClick = onLinkClick,
                    onImageClick = onImageClick,
                    onQuoteRefClick = onQuoteRefClick,
                    textStyle = textStyle,
                    voteContent = voteContent,
                    codeBlockContent = codeBlockContent,
                    stardustContent = stardustContent,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

/**
 * An image on its own line: screen-wide when it is a screenshot, natural size when it is not.
 *
 * Screenshots of benchmark output are the single most common attachment on this forum and are often
 * three screens tall; letting one push the whole thread out of view is worse than cropping it. The
 * crop keeps the top — where the interesting part always is — and says so.
 *
 * An image *narrower* than the column keeps its own size instead of being stretched: the other
 * common attachment here is a 20-pixel-tall repository badge, and scaled to fill the column it
 * became a blurry banner. Source pixels are read as dp, which is exactly how the site's own pages
 * size an `<img>` — CSS pixels — so the two renderings agree on how big "small" is.
 *
 * When 仅 Wi-Fi 加载图片 skips the image it is replaced by [SkippedImagePlaceholder] rather than by
 * nothing, and a tap on that placeholder re-requests the image with the preference waived for this
 * one image — see [allowMeteredImage].
 *
 * How big the image turned out to be is remembered in [NaturalImageSizes] rather than only in this
 * composable. A thread scrolls, and a `remember` keyed on the URL dies with the row that left the
 * screen: scrolling back put every image through its first-load path again — for an SVG that is two
 * rasterisations, the first at full size purely to ask how big it is — with the row collapsed to a
 * spinner in between. On a page of Check.Place reports that reads as the thread jumping under the
 * finger every time it is scrolled up. Knowing the size on the first frame skips the measuring pass
 * *and* lets the placeholder hold the space the image is about to take.
 */
@Composable
private fun BlockImage(
    node: RichNode.BlockImage,
    onImageClick: (String) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    var naturalSize by remember(node.url) { mutableStateOf(NaturalImageSizes[node.url]) }
    var allowMetered by remember(node.url) { mutableStateOf(false) }
    var retryToken by remember(node.url) { mutableIntStateOf(0) }
    var phase by remember(node.url, allowMetered, retryToken) {
        mutableStateOf(InlineImagePhase.Loading)
    }
    // Keyed like `phase`, so a retry clears the last reason rather than showing it under the next
    // attempt's spinner.
    var failure by remember(node.url, allowMetered, retryToken) {
        mutableStateOf<ImageLoadFailure?>(null)
    }

    val context = LocalPlatformContext.current

    when (phase) {
        InlineImagePhase.Deferred -> {
            SkippedImagePlaceholder(onLoad = { allowMetered = true })
            return
        }

        InlineImagePhase.Error -> {
            InlineImageError(
                failure = failure,
                onRetry = { retryToken++ },
                onOpenInBrowser = { onLinkClick(node.url) },
            )
            return
        }

        else -> Unit
    }

    // No `fillMaxWidth` on the constraints box: in an [ImageFlow] row the composable must report
    // its true width or two badges could never share a line.
    BoxWithConstraints {
        val availableWidth = maxWidth
        val natural = naturalSize
        val displayWidth =
            if (natural != null && natural.width.dp < availableWidth) natural.width.dp else availableWidth
        val displayHeight =
            natural?.let { displayWidth * (it.height.toFloat() / it.width.toFloat()) }
        val cropped = displayHeight != null && displayHeight > Sizes.maxInlineImageHeight
        // The space to hold open while the image is on its way. A known size holds exactly the space
        // the image will take, so nothing below it moves when it arrives; a first-ever load has
        // nothing to go on and falls back to a spinner-sized band.
        val pendingHeight = displayHeight?.coerceAtMost(Sizes.maxInlineImageHeight) ?: INLINE_IMAGE_LOADING_HEIGHT
        val density = LocalDensity.current
        val request =
            remember(node.url, allowMetered, retryToken, natural, displayWidth, density) {
                ImageRequest
                    .Builder(context)
                    .data(node.url)
                    .allowMeteredImage(allowMetered)
                    .apply {
                        // A bitmap decodes at its own size or smaller, so the decoded dimensions
                        // *are* the source's and the default constraint sizing can stand. A vector
                        // rasterises at whatever size it is asked for, which made "how big is this
                        // SVG" come back as "as big as the column" — the badges this fix exists for
                        // measured 360dp wide and stacked one per line. So an SVG is first decoded
                        // at its declared size to learn it, then redrawn at the width it will
                        // actually occupy so the raster stays sharp on dense screens.
                        if (node.url.isSvgUrl()) {
                            if (natural == null) {
                                size(coil3.size.Size.ORIGINAL)
                            } else {
                                with(density) {
                                    val height = displayWidth * (natural.height.toFloat() / natural.width.toFloat())
                                    size(displayWidth.roundToPx(), height.roundToPx())
                                }
                            }
                        }
                    }.build()
            }
        Box(
            modifier =
            Modifier
                .width(displayWidth)
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
                        // First write wins: an SVG's second, sharp decode comes back at display
                        // size in physical pixels, and recording that as "natural" would grow the
                        // image a density-multiple per pass.
                        if (naturalSize == null && image.width > 0 && image.height > 0) {
                            val measured = IntSize(image.width, image.height)
                            naturalSize = measured
                            NaturalImageSizes.put(node.url, measured)
                        }
                    },
                    onError = { error ->
                        val throwable = error.result.throwable
                        if (throwable is ImagesDeferredException) {
                            phase = InlineImagePhase.Deferred
                        } else {
                            failure = diagnoseImageFailure(throwable)
                            phase = InlineImagePhase.Error
                        }
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (phase == InlineImagePhase.Loading) {
                                Modifier.height(pendingHeight)
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
                        .height(pendingHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(Res.string.richtext_image_loading),
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
                        text = stringResource(Res.string.richtext_image_view_full),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * What is left where an image failed: what happened, and the two things worth trying.
 *
 * The reason line is the point. This used to say 图片加载失败 and offer 重试, which describes every
 * failure identically and recommends the one action that cannot help the most common of them — an
 * image host that answers the app with a Cloudflare challenge refuses the retry exactly as it
 * refused the first request, while a browser is handed the picture. post-879848 is that case, and
 * from inside the app it was indistinguishable from a dead link.
 *
 * 用浏览器打开 goes through the same link handler as any other link in the post, so the image opens
 * wherever the reader's links open.
 */
@Composable
private fun InlineImageError(
    failure: ImageLoadFailure?,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val reason = imageLoadFailureText(failure)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A minimum rather than a fixed height: the reason line wraps on a narrow screen, and a
            // fixed box would clip the buttons under it.
            .heightIn(min = INLINE_IMAGE_LOADING_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(horizontal = Spacing.md),
        ) {
            Icon(
                imageVector = PlazaIcons.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(Res.string.richtext_image_load_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (reason != null) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(Res.string.richtext_action_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
                Text(
                    text = stringResource(Res.string.richtext_action_open_in_browser),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenInBrowser)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

private enum class InlineImagePhase { Loading, Success, Deferred, Error }

/** True when the URL's path names an SVG; the query string and fragment don't get a say. */
private fun String.isSvgUrl(): Boolean = substringBefore('#').substringBefore('?').endsWith(".svg", ignoreCase = true)

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
    val confirmation = stringResource(Res.string.richtext_code_copied)
    val copyLabel = stringResource(Res.string.richtext_action_copy)

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
 * Tables are two kinds of thing on this forum, and each kind gets the layout built for it.
 *
 * A *numeric* table — latency, packet loss, prices — reads row against row, so it goes to
 * [SpecTable]: one line per cell, tabular figures, the first column pinned while the rest scroll.
 * A *prose* table — plans, terms, notes, or the 2×2 grid of screenshots half the script posts
 * lay their results out in — reads cell by cell, and a single-line grid either truncates it or
 * puts most of it a screen away. Those go to [WrapTable], which does what the site's own
 * stylesheet does: fit the whole table to the screen and let cells wrap.
 *
 * The split is decided by measurement, not by guessing at content: a table goes to [SpecTable]
 * exactly when SpecTable could show every cell whole — nothing past a column cap, nothing to
 * ellipsize — and holds no images. So the one grid that truncates is only ever given tables it
 * cannot truncate.
 *
 * Cells keep their links either way: on the forums this was written against, a group-buy post
 * files its benchmark reports in a table column, and a link the reader can see but not follow is
 * the same as no link at all.
 */
@Composable
private fun DataTable(
    node: RichNode.Table,
    onLinkClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
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
            stringResource(Res.string.richtext_quote_reply, ref.name, ref.floor)
        }
    val cells =
        remember(content, linkStyles, codeBackground, linkListener, quoteLabels) {
            content.map { row ->
                row.map { inlines ->
                    WrapCell(
                        text = cellText(inlines, linkStyles, codeBackground, linkListener, quoteLabels),
                        images =
                        inlines.filterIsInstance<InlineNode.Image>().map {
                            WrapCellImage(url = it.url, alt = it.alt)
                        },
                    )
                }
            }
        }
    val hasImages = remember(cells) { cells.any { row -> row.any { it.images.isNotEmpty() } } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        val texts = remember(cells) { cells.map { row -> row.map(WrapCell::text) } }
        val (columns, rows) = texts.asSpecTable()
        if (!hasImages && rows.isNotEmpty() && specTableFits(columns, rows, available)) {
            SpecTable(columns = columns, rows = rows)
        } else {
            WrapTable(rows = cells, onImageClick = onImageClick)
        }
    }
}

/**
 * One table cell's inline content, flattened to a single string.
 *
 * The nodes that need a layout of their own are reduced to their text — a sticker to its alt, a
 * quote reference to its label — rather than being given a placeholder that would make the row
 * taller than the rest of the grid; an ordinary image contributes nothing here because [DataTable]
 * lifts it out and hands it to [WrapTable] whole. Links keep their annotation, which is the whole
 * reason a cell carries inline content instead of a string.
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

            // Not flattened to text: the cell's images are lifted out beside this string and drawn
            // as thumbnails by [WrapTable] — see [DataTable], which routes any table holding one
            // off the single-line grid.
            is InlineNode.Image -> Unit

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
            stringResource(Res.string.richtext_quote_reply, ref.name, ref.floor)
        }
    val imageLabels =
        inlines.filterIsInstance<InlineNode.Image>().associateWith { image ->
            image.alt ?: stringResource(Res.string.richtext_image_label)
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
            imageLabels,
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

                            // An ordinary image caught inside running text. Its real surfaces —
                            // block position, table cell — draw the pixels; here there is only a
                            // line of type to join, so it joins as a link named by its alt text.
                            is InlineNode.Image -> {
                                val label = imageLabels.getValue(inline)
                                appendWithPangu(label, false) {
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = inline.url,
                                            styles = linkStyles,
                                            linkInteractionListener = linkListener,
                                        ),
                                    ) {
                                        append(label)
                                    }
                                }
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

    val stickerSizing = LocalStickerSizing.current
    val density = LocalDensity.current
    // Reads the size cache, so the first decode of a sticker this process has not seen relays out
    // the paragraph around its real size. In 统一缩限 mode the cache is not consulted and there is
    // nothing to relay out.
    val stickerBoxes =
        inlines.filterIsInstance<InlineNode.Sticker>().associate { sticker ->
            sticker.url to stickerSizing.boxSize(StickerSizeCache.naturalSize(sticker.url), density)
        }
    val stickerContent =
        inlines.filterIsInstance<InlineNode.Sticker>().associate { sticker ->
            val box = stickerBoxes.getValue(sticker.url)
            STICKER_PREFIX + sticker.url to
                InlineTextContent(
                    Placeholder(
                        width = with(density) { box.width.toSp() },
                        height = with(density) { box.height.toSp() },
                        // Centred on the text, not the baseline: a box hung off the baseline would
                        // push the whole line taller and break the 27sp rhythm. That is what keeps
                        // the smallest 统一缩限 setting — a 20sp square — inside the line at all.
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    // A sticker that fails leaves a mark, the same as a block image does. It used to
                    // leave the 20sp box empty, which is indistinguishable from a space: a reply
                    // that is nothing but 表情 came out blank, and 仅 Wi-Fi 加载图片 emptied every
                    // sticker in the thread with nothing to say it had.
                    var failure by remember(sticker.url) { mutableStateOf<StickerFailure?>(null) }
                    val failed = failure
                    if (failed == null) {
                        val context = LocalPlatformContext.current
                        val request =
                            remember(sticker.url) {
                                ImageRequest
                                    .Builder(context)
                                    .data(sticker.url)
                                    // Decoded whole, not to the box. Before its first decode a
                                    // sticker sits in the 20sp fallback box, and a request sized to
                                    // that box would come back 20sp wide — natural-size mode would
                                    // record *that* as the sticker's natural size and never grow.
                                    // Stickers are 90px at most, so keeping them whole costs
                                    // nothing, and it means one cached bitmap per sticker rather
                                    // than one per slider position.
                                    .size(CoilSize.ORIGINAL)
                                    .build()
                            }
                        AsyncImage(
                            model = request,
                            // Sticker markup often has no alt text, and an image with no
                            // content description is silent to a screen reader.
                            contentDescription =
                            sticker.alt ?: stringResource(Res.string.richtext_sticker_description),
                            onSuccess = { state ->
                                StickerSizeCache.record(
                                    url = sticker.url,
                                    width = state.result.image.width,
                                    height = state.result.image.height,
                                )
                            },
                            onError = { error ->
                                failure = if (error.result.throwable is ImagesDeferredException) {
                                    StickerFailure.Deferred
                                } else {
                                    StickerFailure.Failed
                                }
                            },
                            // Fits the box rather than filling its width: 统一缩限 hands every
                            // sticker the same square, and a tall one used to be drawn to that
                            // square's width and overdraw the lines above and below it.
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ImageFallback(
                            modifier = Modifier.fillMaxSize(),
                            deferred = failed == StickerFailure.Deferred,
                        )
                    }
                }
        }

    var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style.fitStickers(stickerBoxes.values, density),
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

/**
 * Gives up the paragraph's fixed line height when a 表情 in it is taller than that line.
 *
 * `PostBody` and its neighbours set an explicit `lineHeight` — 27sp on a 16sp body, the leading a
 * solid block of hanzi needs — and Compose enforces it with a span that rewrites every line's
 * ascent and descent to exactly that. It is enforced against inline content too, so a sticker
 * enlarged past the line by 表情大小 kept its own box and got no room for it: the box stayed
 * centred on a 27sp line and drew straight through the line above, which is 问题 #90.
 *
 * A browser has no such conflict — `line-height` is a *minimum* there, and a line holding a tall
 * inline image simply grows. Compose has the same idea in `LineHeightStyle.Mode.Minimum`, but it
 * cannot be used for this: the span works out its metrics from the first line it is asked about and
 * reuses them for the rest of the paragraph, so a paragraph that opens with text and ends with a
 * sticker still forces the sticker's line back to 27sp. Verified against ui-text 1.12.0-beta01 —
 * `LineHeightStyleSpan.chooseHeight` calls `calculateTargetMetrics` only while `firstAscent` is
 * unset.
 *
 * So the line height comes off entirely, and only for the paragraphs that need it. Their lines fall
 * back to the font's own metrics — a little tighter than 27sp — and the line holding the sticker
 * grows to hold it, which is the browser's layout and the only one where nothing is covered up. Any
 * paragraph whose stickers fit the line, which is every paragraph at the default 20sp setting, is
 * left exactly as it was.
 */
private fun TextStyle.fitStickers(
    boxes: Collection<DpSize>,
    density: Density,
): TextStyle {
    if (lineHeight.isUnspecified) return this
    val line = with(density) { lineHeight.toDp() }
    val tallest = boxes.maxOfOrNull { it.height } ?: 0.dp
    return if (tallest <= line) this else copy(lineHeight = TextUnit.Unspecified)
}

/** Why a sticker is not on screen, kept apart for the reason [ImageFallback] states. */
private enum class StickerFailure { Deferred, Failed }

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
                InlineNode.Link(text = "example.com", url = "https://example.com"),
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
            code = "curl -sL https://get.example.com/v1 | bash\n# 输出结果复制为 Markdown，横向滚动不换行 →→→→→→→→",
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
                    listOf(InlineNode.Link(text = "报告", url = "https://example.com/r/demo")),
                ),
                listOf("洛杉矶 4837", "152ms", "1.2%", "—").map { listOf(InlineNode.Text(it)) },
            ),
        ),
        RichNode.Fold(
            title = "折叠：默认收起，点击标题展开",
            children =
            listOf(
                RichNode.Paragraph(
                    listOf(InlineNode.Text("折叠里的内容照常渲染，标签页、代码块、表格都不会被摊平。")),
                ),
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
