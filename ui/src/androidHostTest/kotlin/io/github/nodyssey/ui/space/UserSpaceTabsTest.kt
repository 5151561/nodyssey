package io.github.nodyssey.ui.space

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.paging.PagingData
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The space page's tabs stay once the header card has scrolled away, so a reader deep in 主题帖 can
 * switch without flinging back to the top — and the tab they switch to opens at its own first row.
 * Swiping the content sideways switches tab too, and the swipe is what selects it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class UserSpaceTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var tab by mutableStateOf(SpaceTab.TOPICS)

    /** The tab's own list — the pager around it scrolls to an index too, sideways. */
    private val tabList = hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun setScreen() {
        composeRule.setContent {
            PlazaTheme {
                val topics =
                    remember {
                        flowOf(PagingData.from(List(60) { SpacePost(it.toLong(), "topic $it", null, null, null, 1, 1, null) }))
                    }
                val comments =
                    remember {
                        flowOf(PagingData.from(List(60) { SpaceComment(it.toLong(), it.toLong(), "thread $it", "reply $it", null) }))
                    }
                UserSpaceScreen(
                    state =
                    UserSpaceUiState(uid = 1, isSelf = false, isLoadingProfile = false, name = "homelab_er", selectedTab = tab),
                    topics = topics,
                    comments = comments,
                    onBack = {},
                    onTabSelected = { tab = it },
                    onPostClick = { _, _ -> },
                    onRetryProfile = {},
                    onMessage = {},
                    onEditProfile = {},
                    onOpenBrowser = {},
                    onSignIn = {},
                    onVerify = {},
                )
            }
        }
    }

    @Test
    fun `deep in one tab the tabs are still there, and the next tab opens at its top`() {
        setScreen()

        // Dragged rather than jumped: the header only folds away under a real scroll.
        repeat(6) { composeRule.onNode(tabList).performTouchInput { swipeUp() } }
        composeRule.onNodeWithText("topic 0").assertDoesNotExist()
        composeRule.onNodeWithText("homelab_er").assertIsNotDisplayed()

        composeRule.onNode(hasText("评论") and hasClickAction()).assertIsDisplayed().performClick()

        composeRule.onNodeWithText("reply 0").assertIsDisplayed()
        assertEquals(SpaceTab.COMMENTS, tab)
    }

    @Test
    fun `swiping the content sideways selects the next tab`() {
        setScreen()

        composeRule.onNode(tabList).performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("reply 0").assertIsDisplayed()
        assertEquals(SpaceTab.COMMENTS, tab)
    }
}
