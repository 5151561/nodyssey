package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.Board
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.ui.postlist.HomeFeedStates
import io.github.nodyssey.ui.postlist.PostListScreen
import io.github.nodyssey.ui.postlist.PostListUiState
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页 feed in both themes — the app's front door, so the first picture on the promo page.
 *
 * Avatars are left `null` on purpose: the JVM has no network, so a real `/avatar/<uid>.png` would
 * never paint. `null` takes the same fallback the app uses for a picture-less account — the coloured
 * monogram — which renders deterministically and reads far better than an empty grey square.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostListScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            val feed =
                remember {
                    flowOf(
                        PagingData.from(
                            data = FEED,
                            sourceLoadStates =
                            LoadStates(
                                refresh = LoadState.NotLoading(false),
                                prepend = LoadState.NotLoading(true),
                                append = LoadState.NotLoading(true),
                            ),
                        ),
                    )
                }
            PostListScreen(
                state = PostListUiState(boards = BOARDS),
                postsFor = { feed.collectAsLazyPagingItems() },
                feedStates = rememberSaveable(saver = HomeFeedStates.Saver) { HomeFeedStates() },
                onPostClick = {},
                onBoardClick = {},
                onSortChange = {},
                onSignInClick = {},
                onRecoverInBrowser = {},
            )
        }
    }

    @Test
    fun `the feed in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("post-list-light")
    }

    @Test
    fun `the feed in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("post-list-dark")
    }

    /** The two menus of the header, open over the feed; each marks its current entry. */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the sort menu in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithContentDescription("排序方式").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/post-list-sort-light.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the site switcher in light`() {
        composeRule.setContent { Screen(darkTheme = false) }
        composeRule.onNodeWithContentDescription("切换站点").performClick()
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/post-list-sites-light.png")
    }

    private companion object {
        val BOARDS =
            listOf(
                Board(null, "综合", null),
                Board("daily", "日常", null),
                Board("tech", "技术", null),
                Board("info", "情报", null),
            )

        private fun post(
            id: Long,
            title: String,
            author: String,
            category: String,
            comments: Int,
            views: Int,
            lastActive: String,
            newComments: Int = 0,
            pinned: Boolean = false,
            awarded: Boolean = false,
            read: Boolean = false,
        ) = FeedPost(
            summary =
            PostSummary(
                postId = id,
                title = title,
                authorName = author,
                authorUid = id,
                avatarUrl = null,
                categoryTitle = category,
                categorySlug = category,
                viewCount = views,
                commentCount = comments,
                lastActiveText = lastActive,
                lastActiveTitle = null,
                isPinned = pinned,
                isLocked = false,
                isAwarded = awarded,
            ),
            isRead = read,
            newCommentCount = newComments,
            page = null,
        )

        val FEED =
            listOf(
                post(1, "【公告】NodeSeek 社区行为准则与版规更新", "管理员", "公告", 42, 8600, "5分钟前", pinned = true),
                post(2, "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻", "homelab_er", "技术", 128, 3400, "刚刚", newComments = 6, awarded = true),
                post(3, "有没有那种用了就回不去的小众 App，求推荐", "轻舟", "日常", 87, 2100, "3分钟前", newComments = 2),
                post(4, "黑五机场怎么选？把我对比的几家整理成了表格", "过路人", "情报", 65, 4800, "12分钟前"),
                post(5, "第一次跑长文本翻译，本地模型和 API 的取舍", "codemonkey", "技术", 33, 1500, "18分钟前", read = true),
                post(6, "深夜放毒：分享一个我常做的十分钟快手菜", "厨房杀手", "日常", 54, 1900, "26分钟前", read = true),
                post(7, "关于最近 VPS 涨价，说说我的续费策略", "省钱达人", "情报", 19, 900, "34分钟前", read = true),
            )
    }
}
