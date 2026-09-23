package io.github.nodyssey.render

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.composer.PostPermission
import io.github.nodyssey.ui.account.ProfileFieldsScreen
import io.github.nodyssey.ui.account.ProfileFieldsUiState
import io.github.nodyssey.ui.mycontent.MyCommentRow
import io.github.nodyssey.ui.mycontent.MyContentScreen
import io.github.nodyssey.ui.mycontent.MyContentUiState
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.my_comments_count
import io.github.nodyssey.ui.space.FollowScreen
import io.github.nodyssey.ui.space.FollowTab
import io.github.nodyssey.ui.space.FollowUiState
import io.github.nodyssey.ui.space.SpaceListState
import io.github.nodyssey.ui.space.SpaceTab
import io.github.nodyssey.ui.space.UserSpaceScreen
import io.github.nodyssey.ui.space.UserSpaceUiState
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.StatusShapes
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A user's space (3a) on 概况 and on 主题帖, 关注与粉丝 (9h), 个人信息 (3b) and 我的评论 — the card
 * lists 3a sets the look for — in both themes. See [PostListScreenRenderTest] on the null avatars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class UserSpaceScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Space(state: UserSpaceUiState) {
        val topics = remember { flowOf(PagingData.from(TOPICS)) }.collectAsLazyPagingItems()
        UserSpaceScreen(
            state = state,
            topics = topics,
            onBack = {},
            onTabSelected = {},
            onPostClick = { _, _ -> },
            onRetryProfile = {},
            onMessage = {},
            onEditProfile = {},
            onOpenBrowser = {},
            onSignIn = {},
            onVerify = {},
        )
    }

    @Composable
    private fun Follow() {
        FollowScreen(
            state =
            FollowUiState(
                selectedTab = FollowTab.FOLLOWING,
                following = SpaceListState(items = FOLLOWING, loaded = true),
            ),
            onBack = {},
            onTabSelected = {},
            onUserClick = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }

    @Composable
    private fun Fields() {
        ProfileFieldsScreen(
            state =
            ProfileFieldsUiState(
                isLoading = false,
                displayName = "homelab_er",
                bio = "在家里折腾 NAS 和软路由，偶尔写点踩坑记录。",
                signature = "[我的博客](https://homelab.example) · 能跑就别动",
                readme = "## 设备清单\n- 四盘位 NAS · ZFS\n- 软路由 N100 · OpenWrt\n- 两台海外小鸡，线路见帖子",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onBioChange = {},
            onSignatureChange = {},
            onReadmeChange = {},
            onAvatarPicked = {},
            onAvatarFailed = {},
            onSave = {},
        )
    }

    @Composable
    private fun Comments() {
        MyContentScreen(
            title = "我的评论",
            state = MyContentUiState(items = COMMENTS, loadedCount = COMMENTS.size, totalCount = 96, endReached = true),
            countRes = Res.string.my_comments_count,
            emptyIcon = PlazaIcons.ChatBubble,
            emptyShape = StatusShapes.Empty,
            emptyTitle = "",
            emptyBody = "",
            emptyAction = "",
            onEmptyAction = {},
            onBack = {},
            onBoardSelected = {},
            onSortSelected = {},
            onLoadMore = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
            onVerify = {},
        ) { comment: SpaceComment ->
            MyCommentRow(
                postTitle = comment.postTitle,
                excerpt = comment.excerpt,
                floor = comment.floor,
                createdAtText = comment.createdAtText,
                onClick = {},
            )
        }
    }

    private fun render(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { PlazaTheme(darkTheme = darkTheme) { content() } }
        composeRule.onRoot().captureRender(name)
    }

    @Test
    fun `someone else's space in light`() = render("space-light", darkTheme = false) { Space(OTHER) }

    @Test
    fun `someone else's space in dark`() = render("space-dark", darkTheme = true) { Space(OTHER) }

    @Test
    fun `own space on topics in light`() =
        render("space-topics-light", darkTheme = false) { Space(SELF) }

    @Test
    fun `own space on topics in dark`() = render("space-topics-dark", darkTheme = true) { Space(SELF) }

    @Test
    fun `following in light`() = render("follow-light", darkTheme = false) { Follow() }

    @Test
    fun `following in dark`() = render("follow-dark", darkTheme = true) { Follow() }

    @Test
    fun `profile fields in light`() = render("profile-fields-light", darkTheme = false) { Fields() }

    @Test
    fun `profile fields in dark`() = render("profile-fields-dark", darkTheme = true) { Fields() }

    @Test
    fun `my comments in light`() = render("my-comments-light", darkTheme = false) { Comments() }

    private companion object {
        val OTHER =
            UserSpaceUiState(
                uid = 18_842,
                isSelf = false,
                isLoadingProfile = false,
                name = "轻舟",
                level = 3,
                bio = "在香港和东京各跑一台小鸡，主业写 Go。偶尔发测评，欢迎私信交流线路。",
                readme =
                """
                ## 关于我的机器
                常驻 CN2 GIA 与软银线路，测评脚本统一用 `yabs.sh`，原始输出都贴在帖子里。

                收车、出车请走交易版，私信只聊技术问题。
                - 拼车请先看置顶规则
                - 别直接加我 Telegram
                - 博客：[blog.example](https://blog.example)
                - 探针：[ping.example](https://ping.example)
                - 线路：CN2 GIA / 软银
                """.trimIndent(),
                joinedDays = 612,
                chickenCount = 1286,
                topicCount = 47,
                commentCount = 1302,
                followed = false,
                selectedTab = SpaceTab.GENERAL,
            )

        val SELF =
            OTHER.copy(
                uid = 21_736,
                isSelf = true,
                name = "homelab_er",
                level = 1,
                selectedTab = SpaceTab.TOPICS,
            )

        val TOPICS =
            listOf(
                SpacePost(1, "四盘位 NAS 选 ZFS 还是 Btrfs？", "技术", "tech", null, 12, 843, "3天前"),
                SpacePost(2, "签到鸡腿是随机的吗？连续七天都是 6 个", "日常", "daily", null, 18, 976, "上周"),
                SpacePost(3, "【出】甲骨文 ARM 一台求接手", "交易", "trade", null, 6, 412, "5月2日", PostPermission(3)),
            )

        val FOLLOWING =
            listOf(
                FollowUser(11_203, "kvm_fan", null),
                FollowUser(8_841, "codemonkey", null),
                FollowUser(5_520, "轻舟", null),
                FollowUser(30_117, "sunrise_vps", null),
            )

        val COMMENTS =
            listOf(
                SpaceComment(1, 11, "四盘位 NAS 选 ZFS 还是 Btrfs？", "ZFS 吃内存，但快照和校验值得。", "2小时前", "#12"),
                SpaceComment(2, 12, "求推荐一台香港小鸡", "看你跑什么，监控的话最便宜那档就够。", "昨天", "#3"),
            )
    }
}
