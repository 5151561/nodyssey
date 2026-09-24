package io.github.plaza.designsys.component

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
 * The app's one extended FAB: Material's, at Material's own size, in `primaryContainer` at a
 * control's 14dp corner, lifted by the layer system's [floatShadow] rather than by Material's own
 * elevation — see [io.github.plaza.designsys.theme.PlazaLayers].
 *
 * The size is deliberately not set. The Lean round had it at 44dp, and Material's collapsed width is
 * its FAB token (56dp) with no parameter to change it, so a 44dp-high button folded into a 56×44
 * slab rather than a square. Left alone it folds into the 56dp square Material draws.
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
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        shape = ControlShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier
            .floatShadow(ControlShape, LocalPlazaLayers.current.shadows)
            .semantics { contentDescription = text },
    )
}

/**
 * What [PlazaExtendedFab] stands at, collapsed or not — for a list keeping its foot clear of it.
 * Material's FAB container height, which its defaults do not expose.
 */
val PlazaFabHeight = 56.dp
