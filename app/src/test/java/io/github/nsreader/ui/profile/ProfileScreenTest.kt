package io.github.nsreader.ui.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nsreader.ui.theme.NodeSeekTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `signed in profile shows unknown level in the resource cards`() {
        composeRule.setContent {
            NodeSeekTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "NodeSeek 用户",
                        level = null,
                    ),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onAssets = {},
                    onFollow = {},
                    onTools = {},
                )
            }
        }

        composeRule.onNodeWithText("Lv —").assertIsDisplayed()
        composeRule.onNodeWithText("等级").assertIsDisplayed()
    }

    @Test
    fun `signed in profile replaces attendance streak with level`() {
        composeRule.setContent {
            NodeSeekTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nsreader_dev",
                        level = "Lv 3",
                    ),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onAssets = {},
                    onFollow = {},
                    onTools = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Lv 3").assertCountEquals(1)
        composeRule.onNodeWithText("等级").assertIsDisplayed()
        composeRule.onNodeWithText("连续签到").assertDoesNotExist()
    }

    @Test
    fun `header avatar action opens the space page`() {
        var clicked = false
        composeRule.setContent {
            NodeSeekTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nsreader_dev"),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = { clicked = true },
                    onAssets = {},
                    onFollow = {},
                    onTools = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("我的主页").performClick()

        check(clicked)
    }
}
