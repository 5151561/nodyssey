package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.plaza.designsys.component.PlazaSheetDefaults
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing

/**
 * The app's bottom sheet: Material's [ModalBottomSheet] on the page colour, with one header.
 *
 * The page colour so that what a sheet holds — cards, tiles, grouped rows — reads as cards on a page,
 * the same layering as the screen under the scrim. The corners and the drag handle are
 * [PlazaSheetDefaults]'. The header is [title] in titleLarge SemiBold,
 * marked as a heading so a screen reader can land on it, over an optional [subtitle], with [action]
 * (a text button, typically) at its trailing edge. A sheet that prints no heading passes no [title]
 * and names itself some other way — the page jump sheet uses a pane title.
 *
 * Only the header is laid out here. [content] brings its own padding and scrolling, since what a
 * sheet holds differs too much between sheets for one inset to fit all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlazaSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    dragHandle: @Composable (() -> Unit)? = { PlazaSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = PlazaSheetDefaults.shape,
        containerColor = LocalPlazaLayers.current.page,
        dragHandle = dragHandle,
    ) {
        if (title != null) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xl, end = if (action != null) Spacing.md else Spacing.xl, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.semantics { heading() },
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                action?.invoke()
            }
        }
        content()
    }
}
