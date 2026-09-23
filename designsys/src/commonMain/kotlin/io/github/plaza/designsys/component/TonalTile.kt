package io.github.plaza.designsys.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** A tile's corners unless it says otherwise. */
val TonalTileShape: Shape = RoundedCornerShape(20.dp)

/**
 * A tonal block tapped as a whole — a balance, a mark, a choice, a page key — with its content
 * stacked in a column: typically an icon, a title and a supporting line.
 *
 * Material's clickable [Surface] underneath (the selectable overload when [selected] is non-null), so
 * the ripple, the disabled state and the button or selected semantics are Material's. Not a `Card`:
 * a card's content column does not receive the tile's minimum height, so a key or a tall choice could
 * not centre what it holds.
 */
@Composable
fun TonalTile(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = contentColorFor(containerColor),
    enabled: Boolean = true,
    /** Non-null makes the tile one choice of several, read as selected or not. */
    selected: Boolean? = null,
    shape: Shape = TonalTileShape,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(2.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
    if (selected != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = body,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = body,
        )
    }
}
