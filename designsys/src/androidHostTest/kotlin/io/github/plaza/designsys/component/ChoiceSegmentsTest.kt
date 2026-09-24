package io.github.plaza.designsys.component

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two halves of evening out [ChoiceSegments]: a label that wraps takes its neighbours with it,
 * and a row where nothing wraps keeps Material's 40dp outline rather than growing to the 48dp touch
 * target around it. The node found by text is the segment's clickable surface, so its height is the
 * outline's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ChoiceSegmentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `segments whose labels fit keep the 40dp outline`() {
        val labels = listOf("跟随系统", "浅色", "深色")
        composeRule.setContent { PlazaTheme { ChoiceSegments(labels, selectedIndex = 0, onSelect = {}) } }

        labels.forEach {
            assertEquals(it, 40f, composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().height.value, 0.5f)
        }
    }

    @Test
    fun `a label that wraps takes the other segments to its height`() {
        val labels = listOf("A label long enough to need a second line", "Light", "Dark")
        composeRule.setContent { PlazaTheme { ChoiceSegments(labels, selectedIndex = 1, onSelect = {}) } }

        val heights = labels.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().height.value }
        assertTrue("the long label should have wrapped: $heights", heights[0] > 48f)
        heights.forEach { assertEquals(heights.toString(), heights[0], it, 0.5f) }
    }
}
