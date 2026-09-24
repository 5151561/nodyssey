package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import io.github.plaza.designsys.theme.LocalPlazaLayers

/**
 * The outlined segmented control: 8dp ends, a tick on the selected segment, card-white where it is
 * not selected so it reads as a control sitting on the card rather than a hole in it. Shared by every
 * one-of-a-few choice — 明暗, 测评报告, 配色来源, 代理类型, the search sort — so they cannot drift.
 */
@Composable
fun ChoiceSegments(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val card = LocalPlazaLayers.current.card
    val colors =
        SegmentedButtonDefaults.colors(
            inactiveContainerColor = card,
            disabledInactiveContainerColor = card,
        )
    // Intrinsic height so a segment whose label wraps takes its neighbours with it, rather than
    // standing taller than the row it is part of.
    val touchTarget = LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp }
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).fillWrappedRow(touchTarget),
                enabled = enabled,
                colors = colors,
                shape =
                SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = labels.size,
                    baseShape = MaterialTheme.shapes.small,
                ),
            ) {
                // Free to wrap: a segment only sets a minimum height, and a clipped label — English,
                // or a large font — is worse than a taller button.
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/**
 * `fillMaxHeight()`, but only in a row that a wrapped label has made taller than [touchTarget].
 *
 * `SegmentedButton`'s Surface reserves a 48dp touch target around its 40dp outline, and that
 * reservation is part of its intrinsic height. Under the row's `height(IntrinsicSize.Min)` a plain
 * `fillMaxHeight()` therefore handed every segment exactly 48dp and stretched the outline to fill
 * it. A row no taller than the touch target has nothing to even out, so it is measured as it would
 * be without the fill. Goes when `SingleChoiceSegmentedButtonRow` evens out its segments itself.
 */
private fun Modifier.fillWrappedRow(touchTarget: Dp): Modifier =
    layout { measurable, constraints ->
        val stretch = constraints.hasBoundedHeight && constraints.maxHeight > touchTarget.roundToPx()
        val placeable =
            measurable.measure(
                if (stretch) constraints.copy(minHeight = constraints.maxHeight) else constraints,
            )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
