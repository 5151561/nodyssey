package io.github.nodyssey.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How far a gesture has to commit to one direction before the bar answers it. */
internal val NavigationDirectionThreshold = 16.dp

/**
 * Turns deliberate user scroll direction into a sticky navigation-bar state.
 *
 * A negative Y delta advances the list and hides the bar. A positive delta moves back toward earlier
 * rows and reveals it. Fling/programmatic deltas are ignored, so neither momentum nor coming to rest
 * can reveal the bar on the user's behalf.
 *
 * Shared by the feed and by search so the bar answers the same gesture on both. It consumes nothing
 * and can therefore sit outside another connection — which is where it belongs, so that a collapsing
 * header nested inside it cannot swallow the deltas this reads direction from.
 */
internal class NavigationBarScrollConnection(
    private val directionThresholdPx: Float,
    private val onHiddenChanged: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var accumulatedDeltaY = 0f
    private var isHidden = false

    init {
        require(directionThresholdPx > 0f)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val deltaY = available.y
        if (source != NestedScrollSource.UserInput || deltaY == 0f) return Offset.Zero

        if (accumulatedDeltaY != 0f && accumulatedDeltaY * deltaY < 0f) {
            accumulatedDeltaY = 0f
        }
        accumulatedDeltaY += deltaY

        if (abs(accumulatedDeltaY) >= directionThresholdPx) {
            val shouldHide = accumulatedDeltaY < 0f
            accumulatedDeltaY = 0f
            if (shouldHide != isHidden) {
                isHidden = shouldHide
                onHiddenChanged(shouldHide)
            }
        }
        return Offset.Zero
    }

    /**
     * The end of a gesture, fling included, which is where the distance it did not spend is dropped.
     *
     * Done here rather than left to each screen to notice: a partial swipe left on the books would
     * make the *next* one flip the bar early, and a screen whose list state it does not own has no
     * way to observe the gesture ending at all.
     */
    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        resetGesture()
        return Velocity.Zero
    }

    fun resetGesture() {
        accumulatedDeltaY = 0f
    }

    /**
     * Puts the bar back without a gesture having asked for it.
     *
     * Only for jumps the user initiated elsewhere — a programmatic scroll produces no user deltas, so
     * without this the connection would still believe it is hidden and the next downward scroll would
     * have nothing left to hide.
     */
    fun reveal() {
        accumulatedDeltaY = 0f
        if (isHidden) {
            isHidden = false
            onHiddenChanged(false)
        }
    }
}
