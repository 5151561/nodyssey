package io.github.nsreader.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    fun `signed in profile keeps the level badge when level data is unavailable`() {
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
                    onEditProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("Lv —").assertIsDisplayed()
    }

    @Test
    fun `signed in profile displays the provided level`() {
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
                    onEditProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("Lv 3").assertIsDisplayed()
    }

    @Test
    fun `edit profile button invokes its callback`() {
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
                    onEditProfile = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("编辑个人主页").performClick()

        check(clicked)
    }
}
