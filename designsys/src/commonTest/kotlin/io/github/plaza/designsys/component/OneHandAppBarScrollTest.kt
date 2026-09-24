package io.github.plaza.designsys.component

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the bar takes out of a scroll, and when.
 *
 * The asymmetry is the whole design and none of it is visible from the composable: the fold is taken
 * in `onPreScroll` so it happens *before* the page moves, and the reopen is taken in `onPostScroll`
 * from what the page could not use, so it happens only once the page is back at its top. Getting
 * either side wrong still compiles and still animates — it just reopens the bar in the middle of a
 * long page, or scrolls the page while the blank is still standing.
 */
class OneHandAppBarScrollTest {
    private val max = 200f

    private fun state(): OneHandAppBarState =
        OneHandAppBarState(Float.NaN, 0f).apply { maxHeightPx = max }

    @Test
    fun `the bar opens with the screen`() {
        val state = state()
        assertEquals(max, state.heightPx, 0f)
        assertEquals(1f, state.fraction, 0f)
    }

    @Test
    fun `scrolling up folds the blank away before the page moves`() {
        val state = state()

        val taken = state.nestedScrollConnection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)

        assertEquals(-50f, taken.y, 0f)
        assertEquals(150f, state.heightPx, 0f)
    }

    @Test
    fun `a folded bar takes nothing and lets the page scroll`() {
        val state = state()
        state.nestedScrollConnection.onPreScroll(Offset(0f, -max), NestedScrollSource.UserInput)

        val taken = state.nestedScrollConnection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)

        assertEquals(0f, taken.y, 0f)
        assertEquals(0f, state.heightPx, 0f)
    }

    @Test
    fun `a scroll the page consumed does not reopen the bar`() {
        val state = state()
        state.nestedScrollConnection.onPreScroll(Offset(0f, -max), NestedScrollSource.UserInput)

        state.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 60f),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput,
        )

        assertEquals(0f, state.heightPx, 0f)
    }

    @Test
    fun `a scroll the page could not use reopens the bar`() {
        val state = state()
        state.nestedScrollConnection.onPreScroll(Offset(0f, -max), NestedScrollSource.UserInput)

        val taken =
            state.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 60f),
                source = NestedScrollSource.UserInput,
            )

        assertEquals(60f, taken.y, 0f)
        assertEquals(60f, state.heightPx, 0f)
    }

    @Test
    fun `folding the blank away is not counted as scrolling the page under the toolbar`() {
        val state = state()

        state.fold(by = -50f)

        assertFalse(state.isContentOverlapped)
    }

    @Test
    fun `scrolling the page under the toolbar tints it`() {
        val state = state()
        state.fold(by = -max)

        state.scrollPage(by = -80f)

        assertTrue(state.isContentOverlapped)
    }

    @Test
    fun `reaching the top of the page clears the tint`() {
        val state = state()
        state.fold(by = -max)
        state.scrollPage(by = -80f)

        state.nestedScrollConnection.onPreScroll(Offset(0f, 80f), NestedScrollSource.UserInput)
        state.nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, 80f),
            available = Offset(0f, 20f),
            source = NestedScrollSource.UserInput,
        )

        assertFalse(state.isContentOverlapped)
    }

    /** One drag frame the page could not use at all, which is what folds the bar. */
    private fun OneHandAppBarState.fold(by: Float) {
        val taken = nestedScrollConnection.onPreScroll(Offset(0f, by), NestedScrollSource.UserInput)
        nestedScrollConnection.onPostScroll(
            consumed = taken,
            available = Offset.Zero,
            source = NestedScrollSource.UserInput,
        )
    }

    /** One drag frame the page consumed in full, the bar having nothing left to give. */
    private fun OneHandAppBarState.scrollPage(by: Float) {
        nestedScrollConnection.onPreScroll(Offset(0f, by), NestedScrollSource.UserInput)
        nestedScrollConnection.onPostScroll(
            consumed = Offset(0f, by),
            available = Offset.Zero,
            source = NestedScrollSource.UserInput,
        )
    }

    /**
     * The bug this is here for: a page rail moves the list with `animateScrollToItem`, which
     * dispatches no nested scroll whatsoever, so the bar heard nothing, stayed at full height, and
     * the page the reader had just asked for turned up in the bottom half of the screen.
     */
    @Test
    fun `folding on the screen's own initiative leaves nothing standing`() = runTest {
        val state = state()

        withContext(SteppedFrameClock()) { state.fold() }

        assertEquals(0f, state.heightPx, 0f)
        assertEquals(0f, state.fraction, 0f)
    }

    /**
     * The bar stops taking anything once it is full, which is what lets a refresh chain behind it.
     *
     * On a screen with pull-to-refresh the two connections are nested rather than arbitrated: this
     * one sits deeper, so it gets the leftover downward drag first. The chain only works because a
     * full bar returns zero here — if it kept claiming the drag, the refresh below would never see
     * a pixel of it and could not be reached at all.
     */
    @Test
    fun `a full bar takes nothing and passes the pull on`() {
        val state = state()

        val taken =
            state.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 60f),
                source = NestedScrollSource.UserInput,
            )

        assertEquals(0f, taken.y, 0f)
        assertEquals(max, state.heightPx, 0f)
    }

    @Test
    fun `a part-open bar takes only what it still has room for`() {
        val state = state()
        state.nestedScrollConnection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)

        val taken =
            state.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 80f),
                source = NestedScrollSource.UserInput,
            )

        assertEquals(50f, taken.y, 0f)
        assertEquals(max, state.heightPx, 0f)
    }
}

/**
 * A frame clock that hands out one frame per ask, so a spring in a test runs to its end.
 *
 * `TestMonotonicFrameClock` would be the obvious thing and it is a JVM artifact: Compose publishes it
 * for android and desktop only, and this file compiles for `iosArm64` as well. Nothing here needs
 * what that one adds — coordinating frames with the virtual clock matters when a test asserts
 * *mid*-animation, and this one asserts where the animation stopped. The bar
 * settles on a spring with a duration, so the loop ends on its own.
 */
private class SteppedFrameClock : MonotonicFrameClock {
    private var nanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        nanos += FRAME_NANOS
        return onFrame(nanos)
    }

    private companion object {
        /** 60fps, which is what the spring is tuned against. */
        const val FRAME_NANOS = 16_666_667L
    }
}
