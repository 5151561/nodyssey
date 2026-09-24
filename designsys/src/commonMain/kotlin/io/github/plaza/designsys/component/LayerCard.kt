package io.github.plaza.designsys.component

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaLayers
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.cardShadow

/**
 * A content card's corner radius: 16dp, still round enough to read as an object lifted off the page.
 * The Lean round's 2a took it down from 24, with the padding, gap and gutter below, so a phone shows
 * twice the feed it did; a grouped list's outer corners ([groupShape]) follow it.
 */
val LayerCardRadius = 16.dp

/** A content card's corners — see [LayerCardRadius]. */
val LayerCardShape: Shape = RoundedCornerShape(LayerCardRadius)

/** What a card's content is inset by, unless it says otherwise. */
private val LayerCardPadding = PaddingValues(12.dp)

/** The gap between two cards in a list. Enough to see the page between them, not enough to break the list. */
val LayerCardGap = 6.dp

/** The page margin a list of cards sits in. Narrower than the text margin so a card's own padding lines its text up with the page's. */
val LayerPageGutter = 8.dp

/**
 * One card on the page — see [io.github.plaza.designsys.theme.PlazaLayers] for why content is drawn
 * this way.
 *
 * A Material [Card] underneath rather than a `Box` with a background, so the press ripple, the click
 * semantics and the shape clip are Material's. The shadow is drawn beside it rather than through the
 * card's own `elevation`, because Material's elevation shadow is a single hard-edged ambient shadow
 * the platform draws in its own colour; the design asks for two soft layers in the page's hue.
 */
@Composable
fun LayerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = LayerCardShape,
    contentPadding: PaddingValues = LayerCardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val layers = LocalPlazaLayers.current
    val colors = CardDefaults.cardColors(containerColor = layers.card)
    val border = layers.cardBorderStroke
    val shadowed = modifier.cardShadow(shape, layers.shadows)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = shadowed, shape = shape, colors = colors, border = border, content = body)
    } else {
        Card(modifier = shadowed, shape = shape, colors = colors, border = border, content = body)
    }
}

/**
 * An inset hairline inside a card, for content that is not a list of rows — a report table. [startInset] is 56dp for a 24dp icon at 16dp padding; pass what the rows use.
 */
@Composable
fun LayerDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 56.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = 1.dp,
        color = LocalPlazaLayers.current.divider,
    )
}
