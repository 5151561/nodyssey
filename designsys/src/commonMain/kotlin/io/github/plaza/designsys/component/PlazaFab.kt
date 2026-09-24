package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.ControlShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.TABULAR_FIGURES
import io.github.plaza.designsys.theme.floatShadow

/**
 * The app's one extended FAB: Material's, a step down to 44dp at a control's 14dp corner, in
 * `primaryContainer`, lifted by the layer system's [floatShadow] rather than by Material's own
 * elevation — see [io.github.plaza.designsys.theme.PlazaLayers].
 *
 * The size is set from outside because Material offers no FAB this small: `SmallExtendedFloatingActionButton`
 * is the 56dp one. A fixed height is honoured because the FAB's own 56dp floor is a default minimum,
 * which yields to any constraint the caller sets. The paddings inside stay Material's, which is why
 * the pill is a little wider than the Lean artboards draw it.
 *
 * [expanded] folds the label away and keeps the icon, the way the feed and the thread tuck the FAB
 * while the reader scrolls on. The label is also set as the button's description, because the
 * icon-and-text overload hides its label from semantics inside the animation that folds it, and the
 * button would otherwise be announced without a name.
 */
@Composable
fun PlazaExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    ExtendedFloatingActionButton(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(PlazaFabIconSize)) },
        onClick = onClick,
        expanded = expanded,
        shape = ControlShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier
            .height(PlazaFabHeight)
            .floatShadow(ControlShape, LocalPlazaLayers.current.shadows)
            .semantics { contentDescription = text },
    )
}

/** What [PlazaExtendedFab] stands at, collapsed or not — for a list keeping its foot clear of it. */
val PlazaFabHeight = 44.dp

private val PlazaFabIconSize = 20.dp
