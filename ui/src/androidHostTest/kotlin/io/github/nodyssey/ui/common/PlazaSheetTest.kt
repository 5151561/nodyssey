package io.github.nodyssey.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A sheet's title is a heading a screen reader can jump to; its subtitle and content are not. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PlazaSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `the title is announced as a heading`() {
        composeRule.setContent {
            PlazaTheme {
                PlazaSheet(onDismiss = {}, title = "检查频率", subtitle = "间隔越短越费电") {
                    Text("每 30 分钟")
                }
            }
        }

        composeRule.onNode(hasText("检查频率") and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText("间隔越短越费电") and isHeading()).assertDoesNotExist()
        composeRule.onNode(hasText("每 30 分钟") and isHeading()).assertDoesNotExist()
    }
}
