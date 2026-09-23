package io.github.nodyssey.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.cardShadow

/**
 * One row of a card that is spread over the items of a `LazyColumn` (boards 5a and 5b).
 *
 * The day groups of 互动 and the conversation card of 私信 are each one white card on the page, but
 * a card is one item and these lists are not short, so each row draws its own slice of the card:
 * the outer corners from [groupShape], an inset hairline at its top when it is not the first. That
 * is how `GroupedRow` does it, too.
 *
 * Unlike `GroupedRow`, the slices keep the card's shadow. A shadow per slice would lay a band across
 * the row above and below it, so each slice's shadow is clipped to its own height and only the first
 * and last are allowed to cast past their outer edge — what is left is the two sides, which line up
 * from slice to slice into the shadow of one card.
 */
@Composable
internal fun CardSliceRow(
    first: Boolean,
    last: Boolean,
    dividerInset: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layers = LocalPlazaLayers.current
    val shape = groupShape(first, last)
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .slicedCardShadow(shape, layers.shadows, first, last)
            .clip(shape)
            .background(layers.card)
            .then(layers.cardBorder?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (!first) LayerDivider(startInset = dividerInset)
        content()
    }
}

private fun Modifier.slicedCardShadow(
    shape: Shape,
    enabled: Boolean,
    first: Boolean,
    last: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .drawWithContent {
                val bleed = SHADOW_BLEED.toPx()
                clipRect(
                    left = -bleed,
                    top = if (first) -bleed else 0f,
                    right = size.width + bleed,
                    bottom = if (last) size.height + bleed else size.height,
                ) { this@drawWithContent.drawContent() }
            }.cardShadow(shape, enabled = true)
    }

/** Comfortably past the widest layer of `cardShadow` — 18dp of blur pushed 6dp down. */
private val SHADOW_BLEED = 32.dp

/**
 * The quiet heading above a group of cards — 今天, 更早, 全部私信.
 *
 * Not `SectionLabel`: that one is primary-coloured and labels a block of settings, while these only
 * say where one run of the same list ends and the next begins, and 5a draws them in the grey of the
 * time stamps under the rows.
 */
@Composable
internal fun ListGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
    )
}
