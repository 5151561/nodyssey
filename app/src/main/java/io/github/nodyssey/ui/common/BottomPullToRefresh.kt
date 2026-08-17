package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Pull-to-refresh at the foot of a list rather than at its head.
 *
 * Material has no parameter for this. `Modifier.pullToRefresh` reads a *downward* drag and clamps
 * the distance it has been pulled with `coerceAtLeast(0f)`, so it can only ever answer at the top of
 * the content — verified in material3 1.5.0-alpha24, `PullToRefreshModifierNode.consumeAvailableOffset`.
 * What is hand-rolled here is that one node with its signs turned around, including its drag
 * multiplier and its overshoot tension so the gesture feels like the one at the other end. Everything
 * else is still Material's: the pulled distance lives in a real [PullToRefreshState], and the
 * indicator is [PullToRefreshDefaults.Indicator], rotated half a turn so that it rises out of the
 * bottom edge and its arrow points the way the finger is going. Delete all of it the day the
 * modifier learns a direction.
 *
 * Composes like [androidx.compose.material3.pulltorefresh.PullToRefreshBox], and nests inside one:
 * this connection sits closer to the scrollable, so it is offered the leftover drag first and the
 * one at the top sees nothing while a pull is running here.
 *
 * @param isRefreshing whether the refresh this gesture asked for is still running
 * @param onRefresh invoked once, when a pull past [threshold] is released
 * @param enabled false while there is nothing at this end to refresh — an unfinished list, say,
 *   where the same drag means "load the next page" instead
 * @param indicatorBottomPadding how far above the bottom edge the indicator surfaces. Zero rises
 *   out of the edge itself, which is right until something floats over it — give it whatever room a
 *   bottom bar has already taken and it clears that instead.
 */
@Composable
fun BottomPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PullToRefreshState = rememberPullToRefreshState(),
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
    indicatorBottomPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val connection = remember(state, scope) { BottomPullToRefreshConnection(scope, state) }
    SideEffect {
        connection.thresholdPx = thresholdPx
        connection.isRefreshing = isRefreshing
        connection.enabled = enabled
        connection.onRefresh = { latestOnRefresh() }
    }
    // The indicator parks at the threshold for as long as the load runs, and retracts when it lands —
    // including the load that ends before the finger is even lifted.
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) state.animateToThreshold() else connection.retract()
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            maxDistance = threshold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = indicatorBottomPadding)
                .graphicsLayer { rotationZ = HALF_TURN_DEGREES },
        )
    }
}

/**
 * [androidx.compose.material3.pulltorefresh.PullToRefreshModifierNode], read from the other end.
 *
 * [distancePulled] is negative here — it is a drag *up*, past content that has stopped moving — and
 * every comparison against it is the mirror of the one Material makes. The unconsumed drag is taken
 * in `onPostScroll`, given back in `onPreScroll` as soon as the finger reverses, and spent or
 * dropped in `onPreFling`, which is where a release either crosses the threshold or does not.
 */
private class BottomPullToRefreshConnection(
    private val scope: CoroutineScope,
    private val state: PullToRefreshState,
) : NestedScrollConnection {
    var thresholdPx = 1f
    var isRefreshing = false
    var enabled = true
    var onRefresh: () -> Unit = {}

    private var distancePulled = 0f

    /** How far the indicator has come, as a fraction of the threshold. Never negative. */
    private val adjustedDistancePulled: Float
        get() = -distancePulled * DRAG_MULTIPLIER

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            state.isAnimating || !enabled -> Offset.Zero

            // Swiping back down: unwind the pull before the list is allowed to move again.
            source == NestedScrollSource.UserInput && available.y > 0f -> consumeAvailableOffset(available)

            else -> Offset.Zero
        }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            state.isAnimating || !enabled -> Offset.Zero

            source == NestedScrollSource.UserInput -> {
                val taken = consumeAvailableOffset(available)
                scope.launch { if (!state.isAnimating) state.snapTo(verticalOffsetFraction()) }
                taken
            }

            else -> Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity = Velocity(0f, onRelease(available.y))

    /** Puts the indicator away without a release having asked for it — the refresh has landed. */
    suspend fun retract() {
        try {
            state.animateToHidden()
        } finally {
            distancePulled = 0f
        }
    }

    private fun consumeAvailableOffset(available: Offset): Offset {
        if (isRefreshing) return Offset.Zero
        val newDistance = (distancePulled + available.y).coerceAtMost(0f)
        val dragConsumed = newDistance - distancePulled
        distancePulled = newDistance
        return Offset(0f, dragConsumed)
    }

    private suspend fun onRelease(velocity: Float): Float {
        if (isRefreshing) return 0f
        if (adjustedDistancePulled > thresholdPx) onRefresh()

        val consumed =
            when {
                // A fling that never dragged the indicator — an ordinary fling inside the list.
                distancePulled == 0f -> 0f

                // Downwards, back into the list: it is the list's fling, not ours.
                velocity > 0f -> 0f

                else -> velocity
            }
        retract()
        return consumed
    }

    /**
     * Where the indicator sits, in thresholds. Material's tension curve: linear up to the threshold,
     * then an overshoot that keeps growing at a decreasing rate and stops at 200% of it.
     */
    private fun verticalOffsetFraction(): Float {
        val progress = adjustedDistancePulled / thresholdPx
        if (progress <= 1f) return progress
        val linearTension = (progress - 1f).coerceIn(0f, 2f)
        return 1f + linearTension - linearTension.pow(2) / 4f
    }
}

/** Material's own, for a gesture that has to feel like the one at the top of the same list. */
private const val DRAG_MULTIPLIER = 0.5f

private const val HALF_TURN_DEGREES = 180f
