package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PreferencesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 夜间模式 belongs to 设置 · 主题, so this screen must not grow a second copy of it. */
    @Test
    fun `the screen carries no night mode rows`() {
        composeRule.setContent {
            PlazaTheme {
                PreferencesScreen(
                    state = PreferencesUiState(holidayTheme = true),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onHolidayThemeChange = {},
                    onBoardHiddenChange = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("启用节日主题").assertExists()
        composeRule.onNodeWithText("自动夜间模式").assertDoesNotExist()
        composeRule.onNodeWithText("夜间模式依据").assertDoesNotExist()
    }
}
