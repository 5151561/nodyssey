package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
