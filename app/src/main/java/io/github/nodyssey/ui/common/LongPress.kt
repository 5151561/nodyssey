package io.github.nodyssey.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.withTimeout

/**
 * Fires once when a press is held without moving.
 *
 * Hand-rolled rather than `combinedClickable` because it sits *above* a chip: a chip consumes the
 * pointer down for its own ripple, so anything watching on the main pass never sees a gesture
 * start. Reading the initial pass gets the press before the chip claims it, and watching for slop
 * means a scroll or a drag still cancels it.
 *
 * Two callers so far, both of them a row of chips whose long press opens an editor: the home
 * board strip, and 我的主题 in 主题.
 */
fun Modifier.longPressToEdit(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val heldStill =
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled += change.positionChangeIgnoreConsumed()
                            if (travelled.getDistance() > viewConfiguration.touchSlop) break
                        }
                    }
                    // Lifted, or moved far enough to be a scroll: either way, not a long press.
                    false
                } catch (_: PointerEventTimeoutCancellationException) {
                    true
                }
            if (heldStill) onLongPress()
        }
    }
