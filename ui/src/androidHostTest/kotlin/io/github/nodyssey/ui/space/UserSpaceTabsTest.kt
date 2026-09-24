package io.github.nodyssey.ui.space

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The space page's tabs stick once the header card has scrolled away, so a reader deep in 主题帖 can
 * switch without flinging back to the top — and the tab they switch to opens at its own first row,
 * not at whatever index the old tab had reached.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class UserSpaceTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `deep in one tab the tabs are still there, and the next tab opens at its top`() {
        composeRule.setContent {
            PlazaTheme {
                var tab by remember { mutableStateOf(SpaceTab.TOPICS) }
                val topics =
                    remember {
                        flowOf(PagingData.from(List(60) { SpacePost(it.toLong(), "topic $it", null, null, null, 1, 1, null) }))
                    }.collectAsLazyPagingItems()
                val comments =
                    remember {
                        flowOf(PagingData.from(List(60) { SpaceComment(it.toLong(), it.toLong(), "thread $it", "reply $it", null) }))
                    }.collectAsLazyPagingItems()
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

        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        composeRule.onNodeWithText("topic 0").assertDoesNotExist()

        composeRule.onNode(hasText("评论") and hasClickAction()).assertIsDisplayed().performClick()

        composeRule.onNodeWithText("reply 0").assertIsDisplayed()
    }
}
