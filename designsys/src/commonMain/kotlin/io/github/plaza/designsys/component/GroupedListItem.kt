package io.github.plaza.designsys.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
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
 *   each row casts a slice of it ([groupSliceShadow]); drawn per row unclipped, each would lay a band
 *   across its neighbour.
 *
 * [first] and [last] say where the row sits in its group; a group of one is both.
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
    /** Where the hairline above the row starts: past a 24dp leading icon by default, at the text otherwise. */
    dividerInset: Dp = if (leadingContent != null) GroupDividerInsetWithIcon else GroupDividerInset,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    colors: ListItemColors = groupedListItemColors(),
) {
    val layers = LocalPlazaLayers.current
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
    val decorated = modifier.groupSlice(layers, first, last, dividerInset)
    when {
        checked != null && onCheckedChange != null ->
            SegmentedListItem(
                checked = checked,
                onCheckedChange = onCheckedChange,
                shapes = shapes,
                modifier = decorated,
                enabled = enabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                verticalAlignment = verticalAlignment,
                colors = colors,
                content = headlineContent,
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
                content = headlineContent,
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
                content = headlineContent,
            )

        // Material keeps its non-interactive segmented overload out of the common API; the classic
        // `ListItem` draws the same row, and takes the group's shape as a clip instead of a parameter.
        else ->
            ListItem(
                headlineContent = headlineContent,
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
 * Transparent in every state: the card itself is painted by [groupSlice], so a pressed or selected
 * row stays part of its card and only Material's state layer shows on top.
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
            contentColor = contentColor,
            leadingContentColor = contentColor,
        )
    } else {
        ListItemDefaults.segmentedColors(containerColor = card, selectedContainerColor = card)
    }
}

val GroupDividerInset = 16.dp
val GroupDividerInsetWithIcon = 56.dp

/**
 * The hairline at a row's top edge, drawn over the row rather than laid out as its own node, so the
 * row's measured height — and with it Material's list-item metrics — does not change.
 */
private fun Modifier.groupDivider(
    layers: PlazaLayers,
    inset: Dp,
): Modifier =
    drawWithContent {
        drawContent()
        val start = inset.toPx()
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
): Modifier {
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
                }.cardShadow(shape, enabled = true)
        }
    return shadowed
        .clip(shape)
        .background(layers.card)
        .then(layers.cardBorder?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
        .then(if (first) Modifier else Modifier.groupDivider(layers, dividerInset))
}

/** Comfortably past the widest layer of `cardShadow` — 18dp of blur pushed 6dp down. */
private val SLICE_SHADOW_BLEED = 32.dp

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
