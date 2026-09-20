package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.ui.postdetail.PostDetailScreen
import io.github.nodyssey.ui.postdetail.PostDetailUiState
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 帖子详情 in both themes — a thread rendered as native Compose content, which is the app's whole
 * pitch, so the second picture on the promo page. See [PostListScreenRenderTest] on the null avatars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class PostDetailScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            PostDetailScreen(
                state = STATE,
                postUrl = "https://www.nodeseek.com/post-100-1",
                onBack = {},
                onOpenBrowser = {},
                onImageClick = {},
                onRetry = {},
                onLoadMore = {},
                onVerify = {},
            )
        }
    }

    @Test
    fun `the thread in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("post-detail-light")
    }

    @Test
    fun `the thread in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("post-detail-dark")
    }

    private companion object {
        private fun paragraphs(vararg text: String): List<RichNode> =
            text.map { RichNode.Paragraph(listOf(InlineNode.Text(it))) }

        private fun floor(
            id: Long,
            author: String,
            floor: String,
            createdAt: String,
            text: List<String>,
            isOp: Boolean = false,
            badges: List<String> = emptyList(),
            reactions: PostReactions? = null,
        ) = PostContent(
            commentId = id,
            floor = floor,
            authorName = author,
            authorUid = id,
            avatarUrl = null,
            isOriginalPoster = isOp,
            badges = badges,
            createdAtText = createdAt,
            createdAtTitle = null,
            categoryTitle = null,
            nodes = paragraphs(*text.toTypedArray()),
            reactions = reactions,
        )

        val BODY =
            PostContent(
                commentId = 1000,
                floor = null,
                authorName = "homelab_er",
                authorUid = 1000,
                avatarUrl = null,
                isOriginalPoster = true,
                badges = listOf("Lv.4"),
                createdAtText = "2小时前",
                createdAtTitle = null,
                categoryTitle = "技术",
                nodes =
                paragraphs(
                    "折腾了一年 NAS，从最早的一块二手硬盘挂在路由器上，到现在四盘位 + 万兆内网，中间踩的坑够写一篇了。这里挑几个印象最深的，给准备入坑的朋友避个雷。",
                    "第一个坑是电源。别省这点钱，杂牌电源在硬盘同时启动时压不住，轻则掉盘重则数据损坏。换了额定 450W 的之后再没出过问题。",
                    "第二个是备份策略。RAID 不是备份，RAID 不是备份，RAID 不是备份。重要的东西一定要有异地冷备，我现在是本地一份 + 云端加密一份。",
                ),
                reactions = PostReactions(likeCount = 86, upvoteCount = 214, dislikeCount = 1),
            )

        val COMMENTS =
            listOf(
                floor(
                    id = 1001,
                    author = "轻舟",
                    floor = "1",
                    createdAt = "1小时前",
                    text = listOf("同折腾党，电源那条深有体会。补充一点：机械硬盘尽量买不同批次，避免同时坏盘。"),
                    reactions = PostReactions(likeCount = 12, upvoteCount = 40),
                ),
                floor(
                    id = 1002,
                    author = "homelab_er",
                    floor = "2",
                    createdAt = "1小时前",
                    text = listOf("对，这个太重要了。我第一批四块盘是同型号同批次，用了两年差点一起阵亡。"),
                    isOp = true,
                    reactions = PostReactions(upvoteCount = 18),
                ),
                floor(
                    id = 1003,
                    author = "过路人",
                    floor = "3",
                    createdAt = "48分钟前",
                    text = listOf("云端冷备用的哪家？想找个便宜大碗的对象存储。"),
                    reactions = PostReactions(upvoteCount = 6),
                ),
                floor(
                    id = 1004,
                    author = "省钱达人",
                    floor = "4",
                    createdAt = "30分钟前",
                    text = listOf("Mark 一下，正好准备装第一台，收藏了慢慢看。感谢楼主的总结！"),
                ),
            )

        val STATE =
            PostDetailUiState(
                postId = 100,
                title = "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻",
                body = BODY,
                comments = COMMENTS,
                commentPages = List(COMMENTS.size) { 1 },
                totalPages = 3,
                hasNextPage = true,
                isSignedIn = true,
                collected = false,
                collectionCount = 37,
                isAwarded = true,
                freeChickenLegs = FreeChickenLegs(max = 5, used = 2),
            )
    }
}
