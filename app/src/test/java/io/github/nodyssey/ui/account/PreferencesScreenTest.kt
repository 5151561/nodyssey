package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PreferencesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `night basis choices fill the row with equal segments`() {
        composeRule.setContent {
            NodysseyTheme {
                PreferencesScreen(
                    state = PreferencesUiState(autoNight = true),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onHolidayThemeChange = {},
                    onAutoNightChange = {},
                    onNightBasisTimedChange = {},
                    onBoardHiddenChange = { _, _ -> },
                )
            }
        }

        val bounds =
            listOf("跟随系统", "定时（日落）").map { label ->
                composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            }
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val groupWidth = bounds.last().right - bounds.first().left

        assertTrue(groupWidth > rootWidth * 0.7f)
        assertTrue(abs(bounds.first().width - bounds.last().width) <= 2f)
    }
}
