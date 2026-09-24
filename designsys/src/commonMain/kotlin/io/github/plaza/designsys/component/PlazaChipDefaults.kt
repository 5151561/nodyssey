package io.github.plaza.designsys.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.cardBorderStroke

/**
 * The one styling every filter or choice chip takes — Material's `FilterChip`, `InputChip` or
 * `AssistChip` given this [shape], this [Height], these colours and this [border].
 *
 * Selected is the inverse surface, the darkest thing on the page, so a picked chip reads as "you are
 * here" rather than as one more tinted control. Unselected is a control's tone one step off whatever
 * the chip sits on: the raised tone on the page, the inset tone inside a card ([inCard]), where the
 * raised tone is the card's own colour and a chip would be a word floating on it — the same rule as
 * [PlazaFieldDefaults.colors].
 */
object PlazaChipDefaults {
    /**
     * 36dp, a step above Material's 32dp. Where a chip row has room it is a minimum (`heightIn`), so
     * Material's 48dp touch target still sets the row's height and a large text size can grow the
     * chip; a wrapping strip of chips that is laid out on a 36dp pitch pins it (`height`).
     */
    val Height: Dp = 36.dp

    val shape: Shape
        @Composable get() = MaterialTheme.shapes.medium

    /** The unselected container — see the class docs for why it depends on [inCard]. */
    @Composable
    fun containerColor(inCard: Boolean = false): Color =
        if (inCard) LocalPlazaLayers.current.inset else LocalPlazaLayers.current.raised

    @Composable
    fun filterChipColors(
        inCard: Boolean = false,
        containerColor: Color = containerColor(inCard),
        labelColor: Color = MaterialTheme.colorScheme.onSurface,
        /** The unselected chip's icons — its label's colour where the chip is tinted rather than neutral. */
        iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): SelectableChipColors {
        val scheme = MaterialTheme.colorScheme
        return FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            iconColor = iconColor,
            selectedContainerColor = scheme.inverseSurface,
            selectedLabelColor = scheme.inverseOnSurface,
            selectedLeadingIconColor = scheme.inverseOnSurface,
            selectedTrailingIconColor = scheme.inverseOnSurface,
        )
    }

    @Composable
    fun inputChipColors(inCard: Boolean = false): SelectableChipColors {
        val scheme = MaterialTheme.colorScheme
        return InputChipDefaults.inputChipColors(
            containerColor = containerColor(inCard),
            labelColor = scheme.onSurface,
            trailingIconColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.inverseSurface,
            selectedLabelColor = scheme.inverseOnSurface,
            selectedLeadingIconColor = scheme.inverseOnSurface,
            selectedTrailingIconColor = scheme.inverseOnSurface,
        )
    }

    /** An assist chip is never selected; it opens something, like a menu, and wears the unselected look. */
    @Composable
    fun assistChipColors(inCard: Boolean = false): ChipColors =
        AssistChipDefaults.assistChipColors(
            containerColor = containerColor(inCard),
            labelColor = MaterialTheme.colorScheme.onSurface,
            trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    /**
     * The card outline, and only where there is one (墨水屏), where tone alone cannot tell a chip from
     * the paper it sits on. None on a selected chip: the inverse fill is its own edge.
     */
    @Composable
    fun border(selected: Boolean = false): BorderStroke? =
        LocalPlazaLayers.current.cardBorderStroke?.takeUnless { selected }
}
