package io.github.nodyssey.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs

/**
 * Whether a horizontal swipe anywhere in the content means back — iOS 26's full-screen pop gesture.
 * iOS only: Android's back gesture belongs to the system edge, and the desktop has none.
 */
internal expect val contentSwipeBackSupported: Boolean

/**
 * Back by swiping anywhere in the content toward the trailing edge, the way iOS 26 pops a
 * `UINavigationController` — feeding the same [NavigationEventInput] path the platform's edge swipe
 * does, so `NavDisplay` previews and finishes it with the predictive-pop transition it already has.
 *
 * Hand-rolled because nothing official covers it: Compose Multiplatform 1.13's iOS input
 * (`IosBackNavigationEventInput`) attaches only `UIScreenEdgePanGestureRecognizer`s, and UIKit's own
 * `interactiveContentPopGestureRecognizer` belongs to a `UINavigationController` this app does not
 * have — every screen is one Compose scene. Remove this if Compose Multiplatform grows a content
 * swipe of its own.
 *
 * It yields to whatever under the finger claims the drag, as UIKit's does: a horizontal scroller
 * that moves, a vertical list, a swipe-to-dismiss row, a drag handle. The one child it takes the
 * drag from is a horizontal scroller already at its leading edge, which reports the swipe as
 * leftover it cannot scroll — the way a `UIScrollView` scrolled to its start lets the pop through.
 * A scroller that moved at all during the drag keeps it, so scrolling back to the start and
 * carrying on does not turn into a back.
 */
@Composable
internal fun Modifier.contentSwipeBack(enabled: Boolean): Modifier {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { ContentSwipeInput() }
    if (dispatcher != null) {
        DisposableEffect(dispatcher) {
            dispatcher.addInput(input)
            onDispose { dispatcher.removeInput(input) }
        }
    }
    val isEnabled by rememberUpdatedState(enabled && dispatcher != null)
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    return this
        .nestedScroll(input.scrolls)
        .pointerInput(input) {
            detectContentSwipe(
                input = input,
                // Idle, too: an edge swipe the platform is already running owns the transition.
                canStart = {
                    isEnabled &&
                        input.canStart() &&
                        dispatcher?.transitionState?.value is NavigationEventTransitionState.Idle
                },
                isRtl = { layoutDirection == LayoutDirection.Rtl },
            )
        }
}

private suspend fun PointerInputScope.detectContentSwipe(
    input: ContentSwipeInput,
    canStart: () -> Boolean,
    isRtl: () -> Boolean,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    input.scrolls.reset()
    if (!canStart()) return@awaitEachGesture
    // Towards the trailing edge is back: rightwards in a left-to-right layout.
    val sign = if (isRtl()) -1f else 1f
    val edge = if (isRtl()) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

    // Deciding, on the Main pass — after every child has had its look at each move.
    var travelled = Offset.Zero
    var start = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
        if (!change.pressed || event.changes.count { it.pressed } > 1) return@awaitEachGesture
        travelled += change.positionChangeIgnoreConsumed()
        velocity.addPosition(change.uptimeMillis, change.position)
        if (input.scrolls.scrolled) return@awaitEachGesture
        // A child owns this drag; wait for it either to move (above) or to report leftover.
        if (change.isConsumed && !input.scrolls.leftover) continue
        if (travelled.getDistance() < viewConfiguration.touchSlop) continue
        val towardsBack = travelled.x * sign
        if (towardsBack <= 0f || abs(travelled.y) > towardsBack) return@awaitEachGesture
        if (!canStart()) return@awaitEachGesture
        change.consume()
        start = change.position
        break
    }

    // Claimed: consume on the Initial pass from here, so every child below sees the drag as taken
    // and lets go of it — a list mid-slop, a press, a scroller at its edge.
    input.started(NavigationEvent(swipeEdge = edge, progress = 0f, touchX = start.x, touchY = start.y))
    var finished = false
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            velocity.addPosition(change.uptimeMillis, change.position)
            val distance = (change.position.x - start.x) * sign
            if (!change.pressed) {
                val speed = velocity.calculateVelocity().x * sign / density
                if (shouldComplete(speed, distance / size.width)) input.completed() else input.cancelled()
                finished = true
                break
            }
            val progress = (distance / size.width).coerceIn(0f, 1f)
            input.progressed(
                NavigationEvent(swipeEdge = edge, progress = progress, touchX = change.position.x, touchY = change.position.y),
            )
        }
    } finally {
        // A pointer that vanished, or the node leaving mid-drag, must not strand the transition.
        if (!finished) input.cancelled()
    }
}

/**
 * The release rule of Compose Multiplatform's own edge swipe (`IosBackNavigationEventInput`), so the
 * two gestures finish alike: a flick towards back completes, a flick away cancels, and a slow release
 * completes only past 30% of the width. [speed] is in dp — iOS points — per second, positive towards
 * back; [fraction] is the distance travelled over the width.
 */
internal fun shouldComplete(
    speed: Float,
    fraction: Float,
): Boolean =
    when {
        speed > 100f -> true
        speed < -10f -> false
        else -> fraction >= 0.3f
    }

private class ContentSwipeInput : NavigationEventInput() {
    private var hasEnabledHandlers = false
    private var idle = true

    val scrolls = ScrollWitness()

    fun canStart(): Boolean = hasEnabledHandlers && idle

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        this.hasEnabledHandlers = hasEnabledHandlers
    }

    fun started(event: NavigationEvent) {
        idle = false
        dispatchOnBackStarted(event)
    }

    fun progressed(event: NavigationEvent) = dispatchOnBackProgressed(event)

    fun completed() {
        idle = true
        dispatchOnBackCompleted()
    }

    fun cancelled() {
        if (idle) return
        idle = true
        dispatchOnBackCancelled()
    }
}

/**
 * What the scrollers under the finger did with the current drag: [scrolled] once any of them moved,
 * and [leftover] once one was offered horizontal travel it had no room for.
 */
internal class ScrollWitness : NestedScrollConnection {
    var scrolled = false
        private set
    var leftover = false
        private set

    fun reset() {
        scrolled = false
        leftover = false
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        // By component, not `consumed != Offset.Zero`: a scroller pinned at its edge reports -0.0,
        // and Offset compares its packed bits, so that reads as having moved.
        if (consumed.x != 0f || consumed.y != 0f) scrolled = true
        if (available.x != 0f) leftover = true
        return Offset.Zero
    }
}
