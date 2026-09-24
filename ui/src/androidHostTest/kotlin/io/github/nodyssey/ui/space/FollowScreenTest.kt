package io.github.nodyssey.ui.space

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.ui.assertContentUnderBigTitle
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h800dp")
class FollowScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** On a tablet the tabs and the list sit in the same centred column as the big title over them. */
    @Test
    fun `on a wide window the list sits under its title`() {
        composeRule.setContent {
            PlazaTheme {
                FollowScreen(
                    state =
                    FollowUiState(
                        selectedTab = FollowTab.FOLLOWING,
                        following = SpaceListState(items = listOf(FollowUser(11_203, "kvm_fan", null)), loaded = true),
                    ),
                    onBack = {},
                    onTabSelected = {},
                    onUserClick = {},
                    onRetry = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                )
            }
        }

        composeRule.assertContentUnderBigTitle(title = "关注与粉丝", content = composeRule.onNodeWithText("kvm_fan"))
    }
}
