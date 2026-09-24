package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.PlazaSpinner

internal enum class MediumButtonStyle { Filled, Tonal, Outlined }

/**
 * The 56dp pill every screen's main action is drawn as — one definition of Material's medium size.
 *
 * Material has no `MediumButton`: the size is a set of `ButtonDefaults.*For(height)` lookups a
 * caller applies to an ordinary [Button]. This is those lookups, taken once, so that the height,
 * shape, padding, label style ([ButtonDefaults.textStyleFor], titleMedium at this height), icon
 * size and icon gap cannot drift apart from one screen to the next.
 *
 * [icon] and [busy] share the leading slot: while busy the icon gives way to a spinner in the
 * button's own content colour, so a filled, tonal or outlined button waits in the same place and
 * the same colour it already draws its label in. [busy] does not disable the button — the caller
 * decides that, since some screens keep the action live while it runs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MediumButtonStyle = MediumButtonStyle.Filled,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    busy: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val sized = modifier.heightIn(min = height)
    val shapes = ButtonDefaults.shapesFor(height)
    val padding = ButtonDefaults.contentPaddingFor(height)
    val body: @Composable RowScope.() -> Unit = {
        ProvideTextStyle(ButtonDefaults.textStyleFor(height)) {
            val leadingSize = ButtonDefaults.iconSizeFor(height)
            if (busy) {
                PlazaSpinner(
                    modifier = Modifier.describedAsLoading(),
                    size = leadingSize,
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(leadingSize))
            }
            if (busy || icon != null) Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            content()
        }
    }
    when (style) {
        MediumButtonStyle.Filled ->
            Button(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = body)

        MediumButtonStyle.Tonal ->
            FilledTonalButton(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = body)

        MediumButtonStyle.Outlined ->
            OutlinedButton(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = body)
    }
}
