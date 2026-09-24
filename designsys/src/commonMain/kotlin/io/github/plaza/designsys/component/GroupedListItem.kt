package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaLayers
import io.github.plaza.designsys.theme.cardShadow

/**
 * One row of a grouped list — the white card of rows every settings page, inbox, history and tools
 * list is built from.
 *
 * Material's [SegmentedListItem] underneath, so the row's layout, its three interaction modes
 * (click with optional long-press, radio [selected], switch [checked]), their semantics and the
 * press feedback are Material's. What this adds is only what the 轻盈层叠 artboards draw differently
 * from Material's segmented list:
 *
 * - **One card, not tiles.** Material separates segments with a gap and small inner corners; here the
 *   rows are flush, square at the seams and rounded only at the group's outer corners ([groupShape]),
 *   with an inset hairline at the top of every row but the first.
 * - **The card's shadow, across rows.** A group is often spread over the items of a `LazyColumn`, so
 *   each row casts a slice of it ([groupSlice]); drawn per row unclipped, each would lay a band
 *   across its neighbour. Inside a [GroupCard] the card is already drawn and the row adds only its
 *   hairline.
 *
 * [first] and [last] say where the row sits in its group; a group of one is both.
 *
 * A switch row ([checked]) goes through Material's plain `onClick` overload rather than its `checked`
 * one: the `checked` overload hard-codes `Role.Checkbox`, and a row drawn with a [Switch] that
 * TalkBack announces as a checkbox is a regression from the `Role.Switch` these rows had before.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedListItem(
    first: Boolean,
    last: Boolean,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** Non-null makes the row one choice of several: it reads as a radio button and [onClick] picks it. */
    selected: Boolean? = null,
    /** Non-null, with [onCheckedChange], makes the whole row a switch. */
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    /**
     * Where the hairline above the row starts. Null lines it up with the headline, measured, so it
     * follows whatever the leading content is — an icon, an avatar — rather than a width each caller
     * has to add up (and Material's own leading gap is 12dp in these rows, not the 16dp a sum guesses).
     */
    dividerInset: Dp? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    colors: ListItemColors = groupedListItemColors(),
) {
    val layers = LocalPlazaLayers.current
    val textStart = remember { TextStart() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val headline: @Composable () -> Unit = {
        Box(Modifier.onPlaced { textStart.headlinePlaced(it, rtl) }) { headlineContent() }
    }
    val shape = groupShape(first, last)
    val shapes =
        ListItemDefaults.shapes(
            shape = shape,
            selectedShape = shape,
            pressedShape = shape,
            focusedShape = shape,
            hoveredShape = shape,
            draggedShape = shape,
        )
    val decorated =
        modifier
            .onPlaced { textStart.row = it }
            .groupSlice(
                layers = layers,
                first = first,
                last = last,
                drawCard = !LocalInGroupCard.current,
                dividerStart = { dividerInset?.toPx() ?: textStart.px.takeUnless { it.isNaN() } ?: GroupDividerInset.toPx() },
            )
    when {
        checked != null && onCheckedChange != null ->
            SegmentedListItem(
                onClick = { onCheckedChange(!checked) },
                shapes = shapes,
                modifier =
                decorated.semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                },
                enabled = enabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                verticalAlignment = verticalAlignment,
                colors = colors,
                content = headline,
            )

        selected != null && onClick != null ->
            SegmentedListItem(
                selected = selected,
                onClick = onClick,
                shapes = shapes,
                modifier = decorated,
                enabled = enabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                verticalAlignment = verticalAlignment,
                colors = colors,
                content = headline,
            )

        onClick != null || onLongClick != null ->
            SegmentedListItem(
                onClick = onClick ?: {},
                shapes = shapes,
                modifier = decorated,
                enabled = enabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                verticalAlignment = verticalAlignment,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                colors = colors,
                content = headline,
            )

        // Material keeps its non-interactive segmented overload out of the common API; the classic
        // `ListItem` draws the same row, and takes the group's shape as a clip instead of a parameter.
        else ->
            ListItem(
                headlineContent = headline,
                modifier = decorated,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
            )
    }
}

/**
 * Transparent in every state: the card itself is painted by [groupSlice], so a pressed, selected or
 * disabled row stays part of its card and only Material's state layer shows on top. Disabled matters
 * most — Material's own disabled container is `surface`, which is the *page* in dark mode, so a
 * disabled row would cut a page-coloured band through its card. The content still dims the way
 * Material dims it.
 *
 * [contentColor] tints the headline and the leading icon together — the one destructive row in a
 * group (退出登录) differs from its neighbours in nothing else.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun groupedListItemColors(
    contentColor: Color = Color.Unspecified,
    /** A tint over the card for a row that is marked rather than selected — the reader's own entry. */
    containerColor: Color = Color.Transparent,
): ListItemColors {
    val card = containerColor
    return if (contentColor.isSpecified) {
        ListItemDefaults.segmentedColors(
            containerColor = card,
            selectedContainerColor = card,
            disabledContainerColor = card,
            contentColor = contentColor,
            leadingContentColor = contentColor,
        )
    } else {
        ListItemDefaults.segmentedColors(
            containerColor = card,
            selectedContainerColor = card,
            disabledContainerColor = card,
        )
    }
}

val GroupDividerInset = 16.dp

/**
 * A group's card drawn once around everything in it, for a group that is not spread over a lazy
 * list — a settings block, where a header, a grid or a report can share the card with the rows under
 * it. Content that is not a [GroupedListItem] has nowhere else to get a card from.
 *
 * The [GroupedListItem]s inside it stop drawing slices and keep only their hairline, so their
 * `first` / `last` then decide nothing but whether a row has a line above it.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LayerCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        CompositionLocalProvider(LocalInGroupCard provides true) { content() }
    }
}

private val LocalInGroupCard = staticCompositionLocalOf { false }

/**
 * Where a row's headline starts, from the row's start edge — written when the headline is placed,
 * read when the hairline is drawn, so a change moves the line without recomposing the row.
 */
private class TextStart {
    var row: LayoutCoordinates? = null
    var px by mutableFloatStateOf(Float.NaN)

    fun headlinePlaced(
        headline: LayoutCoordinates,
        rtl: Boolean,
    ) {
        val row = row?.takeIf { it.isAttached } ?: return
        val x = row.localPositionOf(headline, Offset.Zero).x
        px = if (rtl) row.size.width - (x + headline.size.width) else x
    }
}

/**
 * The hairline at a row's top edge, drawn over the row rather than laid out as its own node, so the
 * row's measured height — and with it Material's list-item metrics — does not change.
 */
private fun Modifier.groupDivider(
    layers: PlazaLayers,
    start: Density.() -> Float,
): Modifier =
    drawWithContent {
        drawContent()
        val start = start()
        val y = 0.5.dp.toPx()
        val (from, to) =
            if (layoutDirection == LayoutDirection.Rtl) {
                Offset(0f, y) to Offset(size.width - start, y)
            } else {
                Offset(start, y) to Offset(size.width, y)
            }
        drawLine(layers.divider, from, to, strokeWidth = 1.dp.toPx())
    }

/**
 * Draws this node as one row's slice of a group card: the card colour clipped to [groupShape], 墨水屏's
 * outline, the hairline above every row but the first, and a slice of the card's shadow.
 *
 * [GroupedListItem] is built on it. Use it directly only for a row that carries a gesture of its own
 * and so cannot be the list item itself — a swipe-to-dismiss row keeps the card still while the item
 * inside it moves.
 *
 * The shadow is what makes slicing work: each slice's shadow is clipped to its own height and only the
 * first and last may cast past their outer edge, so the two sides line up from slice to slice into the
 * shadow of one card. A shadow per row unclipped would lay a band across every seam.
 *
 * Takes [layers] rather than reading [LocalPlazaLayers] so it stays a plain modifier factory.
 */
fun Modifier.groupSlice(
    layers: PlazaLayers,
    first: Boolean,
    last: Boolean,
    dividerInset: Dp = GroupDividerInset,
): Modifier = groupSlice(layers, first, last, drawCard = true, dividerStart = { dividerInset.toPx() })

private fun Modifier.groupSlice(
    layers: PlazaLayers,
    first: Boolean,
    last: Boolean,
    drawCard: Boolean,
    dividerStart: Density.() -> Float,
): Modifier {
    val divider = if (first) Modifier else Modifier.groupDivider(layers, dividerStart)
    if (!drawCard) return then(divider)
    val shape = groupShape(first, last)
    val shadowed =
        if (!layers.shadows) {
            this
        } else {
            this
                .drawWithContent {
                    val bleed = SLICE_SHADOW_BLEED.toPx()
                    clipRect(
                        left = -bleed,
                        top = if (first) -bleed else 0f,
                        right = size.width + bleed,
                        bottom = if (last) size.height + bleed else size.height,
                    ) { this@drawWithContent.drawContent() }
                }.cardShadow(SliceShadowShape(first, last), enabled = true)
        }
    return shadowed
        .clip(shape)
        .background(layers.card)
        .then(layers.cardBorder?.let { Modifier.groupOutline(it, first, last) } ?: Modifier)
        .then(divider)
}

/**
 * 墨水屏's outline for one slice: the card's sides, plus its top or bottom only where this slice is
 * the group's end. The seam edges are drawn past the slice and clipped away, so the sides run
 * unbroken from row to row and no seam gets a rule of its own on top of the hairline.
 */
private fun Modifier.groupOutline(
    color: Color,
    first: Boolean,
    last: Boolean,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val top = if (first) stroke / 2 else -stroke
        val bottom = if (last) size.height - stroke / 2 else size.height + stroke
        val outline = groupShape(first, last).createOutline(Size(size.width - stroke, bottom - top), layoutDirection, this)
        translate(left = stroke / 2, top = top) { drawOutline(outline, color, style = Stroke(stroke)) }
    }

/** Comfortably past the widest layer of `cardShadow` — 18dp of blur pushed 6dp down. */
private val SLICE_SHADOW_BLEED = 32.dp

/**
 * What one slice casts its shadow from: its own shape, run on past each seam by [SLICE_SHADOW_BLEED].
 *
 * Cast from the row's own shape, a middle row's side shadow faded out towards both of its seams, so
 * the card's edges came out as a notch of light at every seam. Stretched, every slice casts a section
 * of one long card, and [groupSlice]'s clip trims it back to the row.
 *
 * Always as a path, whatever [groupShape] hands back. Compose's shadow renderer (ui-graphics
 * 1.12.0-rc01, `ShadowRenderer.updateParamsFromOutline`) keeps only the corner radius of an
 * `Outline.Rectangle`, and of an `Outline.Rounded` whose corners are all alike, and casts that at the
 * node's own size and position — so a middle row, whose shape is a plain rectangle, lost its stretch
 * and went on fading at both seams. A path is the one outline it casts where it is. Remove the
 * conversion if the renderer comes to honour a rectangle's bounds.
 */
private class SliceShadowShape(
    private val first: Boolean,
    private val last: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // A group of one is the whole card, and has nothing to stretch past.
        if (first && last) return groupShape(first = true, last = true).createOutline(size, layoutDirection, density)
        val bleed = with(density) { SLICE_SHADOW_BLEED.toPx() }
        val top = if (first) 0f else -bleed
        val bottom = if (last) size.height else size.height + bleed
        val outline = groupShape(first, last).createOutline(Size(size.width, bottom - top), layoutDirection, density)
        val shift = Offset(0f, top)
        return Outline.Generic(
            Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect.translate(shift))
                    is Outline.Rounded -> addRoundRect(outline.roundRect.translate(shift))
                    is Outline.Generic -> addPath(outline.path, shift)
                }
            },
        )
    }

    override fun equals(other: Any?): Boolean = other is SliceShadowShape && other.first == first && other.last == last

    override fun hashCode(): Int = 31 * first.hashCode() + last.hashCode()
}

/**
 * The switch a toggle row carries: Material's own, with the tick in its thumb while it is on.
 *
 * `onCheckedChange` is always null — the row around it ([GroupedListItem] with `checked`) is the
 * toggle, which gives the whole row one hit target and one semantics node rather than a row and a
 * switch that both claim the tap.
 */
@Composable
fun GroupedListItemSwitch(
    checked: Boolean,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent =
        if (checked) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}
