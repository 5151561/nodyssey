package io.github.nodyssey.ui.composer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * What the wrench opens: the strip's own contents, as a list you can rearrange.
 *
 * A sheet rather than a settings page, because it is reached mid-sentence. The post editor would
 * survive a navigation — its draft autosaves — but the reply sheet would not: it *is* a sheet, and
 * navigating away dismisses it along with whatever was half-typed.
 *
 * Every change writes through immediately, so there is no 保存 button and nothing to lose by
 * swiping the sheet away. The strip underneath is already rearranged by the time it reappears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarCustomizeSheet(
    layout: ToolbarLayout,
    onChange: (List<EditorAction>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            // No blanket arrangement: the gap between two rows and the gap between a heading and the
            // list under it are different distances, and one `spacedBy` for the whole sheet can only
            // ever be right for one of them.
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.composer_toolbar_customize),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.composer_toolbar_reset))
                }
            }

            SectionLabel(stringResource(R.string.composer_toolbar_enabled), top = Spacing.sm)
            EnabledKeys(enabled = layout.enabled, onChange = onChange)

            if (layout.available.isNotEmpty()) {
                SectionLabel(stringResource(R.string.composer_toolbar_available), top = Spacing.xl)
                Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                    layout.available.forEach { action ->
                        AvailableRow(
                            action = action,
                            onAdd = { onChange(layout.enabled + action) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    top: Dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = Spacing.sm),
    )
}

/**
 * The enabled keys, in strip order, draggable by the handle.
 *
 * A plain `Column` rather than a `LazyColumn`: there are at most as many rows as [EditorAction] has
 * entries, they are all the same height, and that uniformity is what makes the drag arithmetic one
 * division instead of a bounds lookup. The list also sits inside a scrolling sheet, where a lazy list
 * would need a fixed height to measure at all.
 */
@Composable
private fun EnabledKeys(
    enabled: List<EditorAction>,
    onChange: (List<EditorAction>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // The slot, not the row: with a gap between them, one row of travel is one row plus one gap, and
    // stepping by the row alone would make the list run ahead of the finger by a gap per swap.
    val slotHeightPx = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val apply by rememberUpdatedState(onChange)
    /*
     * The order the gesture works on, and the order the rows draw from.
     *
     * A snapshot list rather than the caller's `enabled`, because a drag outruns composition. Several
     * move events can arrive inside one frame, and each has to see the swap the one before it made —
     * `rememberUpdatedState` would not, since it only refreshes when a recomposition happens to land
     * between them, and closing over `enabled` or keying `pointerInput` on it is worse still: the
     * first restarts the block and cancels the drag, the second rearranges the pre-drag order forever.
     *
     * Between gestures the caller is the authority again, which is what the effect below restores.
     */
    val order = remember { mutableStateListOf<EditorAction>().apply { addAll(enabled) } }
    LaunchedEffect(enabled) {
        if (order.toList() != enabled) {
            order.clear()
            order.addAll(enabled)
        }
    }

    fun release() {
        draggedIndex = null
        dragOffset = 0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        order.forEachIndexed { index, action ->
            val dragging = index == draggedIndex
            EnabledRow(
                action = action,
                // The last key never leaves: an empty strip reads back as "never customised" and
                // would hand the defaults straight back on the next open. See `toolbarLayout`.
                canRemove = order.size > MIN_TOOLBAR_KEYS,
                dragging = dragging,
                onRemove = {
                    order.remove(action)
                    apply(order.toList())
                },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) },
                handle = {
                    DragHandle(
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedIndex = index
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = ::release,
                                onDragCancel = ::release,
                            ) { change, amount ->
                                change.consume()
                                val from = draggedIndex ?: return@detectDragGestures
                                dragOffset += amount.y
                                // One row of travel is one swap, and the offset gives that row back
                                // so the dragged item stays under the finger rather than racing it.
                                val steps = (dragOffset / slotHeightPx).roundToInt()
                                if (steps == 0) return@detectDragGestures
                                val to = (from + steps).coerceIn(0, order.lastIndex)
                                if (to == from) return@detectDragGestures
                                dragOffset -= (to - from) * slotHeightPx
                                draggedIndex = to
                                order.add(to, order.removeAt(from))
                                apply(order.toList())
                            }
                        },
                    )
                },
            )
        }
    }
}

/** The grab target. A slot rather than a modifier parameter, so the row stays a plain composable. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.composer_toolbar_reorder)
    Icon(
        imageVector = NodysseyIcons.DragHandle,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(HANDLE_SIZE),
    )
}

@Composable
private fun EnabledRow(
    action: EditorAction,
    canRemove: Boolean,
    dragging: Boolean,
    onRemove: () -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(ROW_HEIGHT),
        // `surface` was the bug behind "挤在一起": it is all but the sheet's own background, so with
        // no gap either the rows fused into one slab. A container tone makes each one an object.
        color = if (dragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.medium,
        shadowElevation = if (dragging) DRAG_ELEVATION else 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm),
        ) {
            handle()
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(KEY_ICON_SIZE),
            )
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                // Kept in place rather than hidden when it is the last key: a control that vanishes
                // is a control the user goes looking for.
                modifier = Modifier.alpha(if (canRemove) 1f else DISABLED_ALPHA),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.composer_toolbar_remove),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AvailableRow(
    action: EditorAction,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            // Aligned with the enabled rows' icons, which sit past a 24dp handle and its gap.
            // Aligned with the enabled rows' key icons, which sit past a handle and its gap — the two
            // halves are the same list, so their glyphs have to share a column.
            .padding(start = Spacing.md + HANDLE_SIZE + Spacing.md, end = Spacing.sm),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(KEY_ICON_SIZE),
        )
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.composer_toolbar_add),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val ROW_HEIGHT = 56.dp

/** Between rows, and therefore part of one drag step. See `slotHeightPx`. */
private val ROW_GAP = Spacing.sm
private val DRAG_ELEVATION = 6.dp
private val HANDLE_SIZE = 24.dp
private val KEY_ICON_SIZE = 22.dp
private const val DISABLED_ALPHA = 0.38f
