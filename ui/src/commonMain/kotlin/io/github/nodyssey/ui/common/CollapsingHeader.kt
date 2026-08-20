package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * A block of chrome that folds away as the list under it advances — a
 * [androidx.compose.material3.TopAppBar]'s collapse, for content that is not a top app bar.
 *
 * The behaviour is Material's own: pass the same [TopAppBarScrollBehavior] here and to the list's
 * `Modifier.nestedScroll`, and the scroll thresholds, the direction handling and the snap at the end
 * of a fling are all the ones the app bars use. Only the drawing is ours, because a search field and
 * a tab row cannot be poured into a `TopAppBar` slot.
 *
 * The header is *measured* out of the layout rather than merely translated, so whatever sits below it
 * rises into the space it gives up instead of waiting under a hole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = scrollBehavior.state
    Column(
        modifier = modifier
            // Outside the layout below, so it clips to the height that layout reports rather than to
            // the full height the content measured at.
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)

                /*
                 * How far the behaviour is allowed to fold, published from the one place that knows:
                 * this content has no fixed height, and it changes shape outright when the results
                 * screen adds its scope row.
                 *
                 * Read only by the scroll connection, never during composition or layout, which is
                 * what makes writing it from inside a measure pass safe.
                 */
                val limit = -placeable.height.toFloat()
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit

                // Clamped rather than trusted: a header that shrinks while it is folded leaves the
                // behaviour holding an offset deeper than the new content, and the difference would
                // be laid out as a negative height.
                val offset = state.heightOffset.coerceIn(limit, 0f).roundToInt()
                layout(placeable.width, placeable.height + offset) {
                    placeable.place(0, offset)
                }
            },
        content = content,
    )
}
