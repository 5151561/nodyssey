package io.github.nsreader.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nsreader.model.TermsBlock
import io.github.nsreader.model.TermsDocument
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PrivacyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `content follows f2 hierarchy and original action`() {
        var opened = false
        composeRule.setContent {
            NodeSeekTheme {
                PrivacyScreen(
                    state = PrivacyUiState.Content(
                        TermsDocument(
                            title = "本网站服务协议",
                            effectiveDate = "2022-11-24",
                            blocks = listOf(
                                TermsBlock.Heading(2, "定义和说明"),
                                TermsBlock.Paragraph("协议正文。"),
                            ),
                        ),
                    ),
                    onBack = {},
                    onOpenOriginal = { opened = true },
                    onRetry = {},
                    onOpenWebFallback = {},
                )
            }
        }

        composeRule.onNodeWithText("本网站服务协议").assertIsDisplayed()
        composeRule.onNodeWithText("最新版本生效日期 2022-11-24").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("在浏览器中打开原文").performClick()
        assertTrue(opened)
    }

    @Test
    fun `web fallback is only offered after native load failure`() {
        var openedFallback = false
        composeRule.setContent {
            NodeSeekTheme {
                PrivacyScreen(
                    state = PrivacyUiState.Error,
                    onBack = {},
                    onOpenOriginal = {},
                    onRetry = {},
                    onOpenWebFallback = { openedFallback = true },
                )
            }
        }

        composeRule.onNodeWithText("使用网页版阅读").performClick()
        assertTrue(openedFallback)
    }
}
