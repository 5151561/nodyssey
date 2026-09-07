package io.github.plaza.designsys.component

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The claim `PlazaSpinner` exists to make: on paper, nothing moves.
 *
 * Two frames half a second apart, compared pixel for pixel. It is worth this much machinery because
 * the failure it guards against is invisible in every other kind of test — a spinner that still
 * spins renders, lays out, passes its semantics assertions and looks right in a screenshot, and is
 * wrong only in the one dimension a screenshot has none of. The clock is driven by hand rather than
 * left to advance on its own, so the test states which two frames it compared instead of racing.
 *
 * The second test is the control. Without it the first one passes just as happily against a spinner
 * that was never drawn at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PlazaSpinnerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `on electronic paper the wait mark never redraws`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PlazaTheme(einkMode = true) { PlazaSpinner() }
        }

        val first = pixels()
        composeRule.mainClock.advanceTimeBy(500)
        val second = pixels()

        assertArrayEquals(first, second)
    }

    @Test
    fun `off it is still the Material spinner, and it moves`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PlazaTheme { PlazaSpinner() }
        }

        val first = pixels()
        composeRule.mainClock.advanceTimeBy(500)
        val second = pixels()

        assertFalse(
            "the control frame is identical too — this test is no longer proving anything",
            first.contentEquals(second),
        )
    }

    private fun pixels(): IntArray = composeRule.onRoot().captureToImage().toPixelMap().buffer
}
