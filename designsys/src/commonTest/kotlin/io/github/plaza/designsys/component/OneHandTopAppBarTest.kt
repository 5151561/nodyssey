package io.github.plaza.designsys.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pull is bounded by a share of the whole bar, and is zero where there is no window to spare.
 *
 * Both ends are the point. Without the floor a landscape phone gives away nearly half of an already
 * short window to an empty title; without the ceiling a tablet can be pulled open on a 450dp hole.
 * The middle cases are asserted as the fraction minus the toolbar rather than as bare numbers, so
 * they keep meaning something when the fraction is retuned.
 */
class OneHandExpandedBlankTest {
    @Test
    fun `a landscape phone gets no one-hand mode at all`() {
        assertEquals(0.dp, oneHandExpandedBlank(windowHeight = 360.dp))
    }

    @Test
    fun `a short phone can still be pulled, just less far`() {
        assertEquals(600f * 0.4f - 64f, oneHandExpandedBlank(windowHeight = 600.dp).value, 0.01f)
    }

    @Test
    fun `a phone in portrait can be pulled to two fifths of the window`() {
        assertEquals(800f * 0.4f - 64f, oneHandExpandedBlank(windowHeight = 800.dp).value, 0.01f)
    }

    @Test
    fun `a tablet stops growing`() {
        assertEquals(320.dp, oneHandExpandedBlank(windowHeight = 1280.dp))
    }
}

/**
 * There is always a title on screen, at every height the bar can be left at.
 *
 * The bar rests wherever the finger let go, so every fraction in 0..1 is somewhere a reader can sit
 * and look at the screen. An earlier pair of curves faded the small title out by 0.4 and started the
 * big one at 0.4, which is invisible under a snapping bar — it never stops in there — and is a
 * screen with no title at all under this one. Complementary curves are the fix, and this is the
 * assertion that keeps them complementary.
 */
class OneHandTitleCrossfadeTest {
    @Test
    fun `only the small title is drawn when collapsed`() {
        assertEquals(1f, collapsedTitleAlpha(0f), 0f)
        assertEquals(0f, expandedTitleAlpha(0f), 0f)
    }

    @Test
    fun `only the big title is drawn when open`() {
        assertEquals(0f, collapsedTitleAlpha(1f), 0f)
        assertEquals(1f, expandedTitleAlpha(1f), 0f)
    }

    @Test
    fun `the two alphas always sum to one`() {
        for (step in 0..100) {
            val fraction = step / 100f
            assertEquals(
                1f,
                collapsedTitleAlpha(fraction) + expandedTitleAlpha(fraction),
                0.001f,
                "at fraction $fraction",
            )
        }
    }

    @Test
    fun `the handover runs the right way round`() {
        var previous = expandedTitleAlpha(0f)
        for (step in 1..100) {
            val next = expandedTitleAlpha(step / 100f)
            assertTrue(next >= previous, "the big title faded out between ${step - 1} and $step")
            previous = next
        }
    }
}

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
    fun `a bar told not to expand opens folded`() {
        val state = OneHandAppBarState(0f, 0f).apply { maxHeightPx = max }

        assertEquals(0f, state.heightPx, 0f)
        assertEquals(0f, state.fraction, 0f)
    }

    @Test
    fun `a bar that opened folded can still be pulled`() {
        val state = OneHandAppBarState(0f, 0f).apply { maxHeightPx = max }

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
    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
    @Test
    fun `folding on the screen's own initiative leaves nothing standing`() = runTest {
        val state = state()

        withContext(TestMonotonicFrameClock(this)) { state.fold() }

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

    @Test
    fun `a rotation into a shorter window brings an open bar down with it`() {
        val state = state()

        state.maxHeightPx = 80f

        assertEquals(80f, state.heightPx, 0f)
        assertEquals(1f, state.fraction, 0f)
    }
}
