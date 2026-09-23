package io.github.plaza.designsys.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaLayers
import io.github.plaza.designsys.theme.cardShadow

/** A content card's corners: 24dp, round enough to read as an object lifted off the page. */
val LayerCardShape: Shape = RoundedCornerShape(24.dp)

/** What a card's content is inset by, unless it says otherwise. */
val LayerCardPadding = PaddingValues(16.dp)

/** The gap between two cards in a list. Enough to see the page between them, not enough to break the list. */
val LayerCardGap = 10.dp

/** The page margin a list of cards sits in. Narrower than the 16dp text margin so a card's own padding lines its text up with the page's. */
val LayerPageGutter = 12.dp

/**
 * One card on the page — see [io.github.plaza.designsys.theme.PlazaLayers] for why content is drawn
 * this way.
 *
 * A Material [Card] underneath rather than a `Box` with a background, so the press ripple, the click
 * semantics and the shape clip are Material's. The shadow is drawn beside it rather than through the
 * card's own `elevation`, because Material's elevation shadow is a single hard-edged ambient shadow
 * the platform draws in its own colour; the design asks for two soft layers in the page's hue.
 *
 * [onLongClick] turns the whole card into a `combinedClickable`, which [Card]'s `onClick` overload
 * has no parameter for.
 */
@Composable
fun LayerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    shape: Shape = LayerCardShape,
    color: Color = LocalPlazaLayers.current.card,
    contentPadding: PaddingValues = LayerCardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val layers = LocalPlazaLayers.current
    val colors = CardDefaults.cardColors(containerColor = color)
    val border = layers.cardBorder?.let { BorderStroke(1.dp, it) }
    val shadowed = modifier.cardShadow(shape, layers.shadows)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                            onLongClickLabel = onLongClickLabel,
                        )
                    } else {
                        Modifier
                    },
                ).padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
    if (onClick != null && onLongClick == null) {
        Card(onClick = onClick, modifier = shadowed, shape = shape, colors = colors, border = border, content = body)
    } else {
        Card(modifier = shadowed, shape = shape, colors = colors, border = border, content = body)
    }
}

/**
 * A card that holds rows rather than one block of content — a settings group, a day of
 * notifications. The rows bring their own padding and separate themselves with [LayerDivider].
 */
@Composable
fun LayerGroup(
    modifier: Modifier = Modifier,
    shape: Shape = LayerCardShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    LayerCard(
        modifier = modifier,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/**
 * The hairline between two rows of one [LayerGroup], inset past the rows' leading icon so the icons
 * read as one column. [startInset] is 56dp for a 24dp icon at 16dp padding; pass what the rows use.
 */
@Composable
fun LayerDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 56.dp,
    endInset: Dp = 0.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = endInset),
        thickness = 1.dp,
        color = LocalPlazaLayers.current.divider,
    )
}

/**
 * Draws this node as one slice of a card that is spread over the items of a `LazyColumn` — the
 * outer corners from [groupShape], the card colour, 墨水屏's outline, and the card's shadow.
 *
 * A card is one item, and a long list of rows cannot be one item without giving up laziness, so
 * each row draws its own slice. What makes that work is the shadow: a shadow per slice would lay a
 * band across the rows above and below, so each slice's shadow is clipped to its own height and
 * only the first and last may cast past their outer edge. What is left is the two sides, which line
 * up from slice to slice into the shadow of one card.
 *
 * The same drawing `notifications`' `CardSliceRow` does by hand; this is that recipe as a modifier,
 * for lists whose rows carry their own gestures (a swipe, a long-press) and so cannot be handed a
 * ready-made clickable row.
 *
 * Takes [layers] rather than reading [LocalPlazaLayers] itself for the reason [cardShadow] does: a
 * modifier factory stays a plain function, and the caller reads the local once per row.
 */
fun Modifier.layerCardSlice(
    layers: PlazaLayers,
    first: Boolean,
    last: Boolean,
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
}

/** Comfortably past the widest layer of `cardShadow` — 18dp of blur pushed 6dp down. */
private val SLICE_SHADOW_BLEED = 32.dp
