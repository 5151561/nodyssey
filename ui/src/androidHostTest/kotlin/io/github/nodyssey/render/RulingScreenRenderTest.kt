package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.RulingAction
import io.github.nodyssey.data.RulingKind
import io.github.nodyssey.data.RulingRecord
import io.github.nodyssey.data.RulingTarget
import io.github.nodyssey.ui.tools.RulingScreen
import io.github.nodyssey.ui.tools.RulingUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Board 9g — 管理记录 on one card with its page rail, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class RulingScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            RulingScreen(
                state =
                RulingUiState(
                    isLoading = false,
                    records = RECORDS,
                    recordPages = List(RECORDS.size) { 1 },
                    firstLoadedPage = 1,
                    lastLoadedPage = 1,
                    totalPages = 100,
                    hasNextPage = true,
                    boardTitles = mapOf("expose" to "曝光"),
                ),
                onBack = {},
                onLoadMore = {},
                onLoadPage = {},
                onScrollHandled = {},
                onRetry = {},
                onRecordClick = {},
                onOpenBrowser = {},
                onVerify = {},
                onSignIn = {},
            )
        }
    }

    @Test
    fun `the log in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("ruling-light")
    }

    @Test
    fun `the log in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("ruling-dark")
    }

    private companion object {
        private fun record(
            id: Long,
            name: String,
            target: RulingTarget,
            reason: String,
            actions: List<RulingAction>,
            moderator: String,
            kind: RulingKind,
        ) = RulingRecord(
            id = id,
            targetName = name,
            targetUid = id,
            target = target,
            postId = id,
            floor = null,
            reason = reason,
            actions = actions,
            moderatorName = moderator,
            createdAtMillis = 1_785_649_006_000L - id * 3_600_000L,
            kind = kind,
        )

        val RECORDS =
            listOf(
                record(1, "fastvps", RulingTarget.POST, "虚假宣传", listOf(RulingAction.Coin(-50), RulingAction.Lock(true), RulingAction.Move("expose")), "admin", RulingKind.MOVE),
                record(2, "轻舟", RulingTarget.POST, "优质测评", listOf(RulingAction.Award(true), RulingAction.Stardust(1)), "mod_a", RulingKind.REWARD),
                record(3, "路人甲", RulingTarget.COMMENT, "人身攻击", listOf(RulingAction.Hide(hidden = true, wholeUser = false), RulingAction.Suspend(3)), "mod_a", RulingKind.BAN),
                record(4, "省钱达人", RulingTarget.POST, "标题不规范", listOf(RulingAction.Title("出一台 HK 2C4G")), "mod_b", RulingKind.PENALTY),
            )
    }
}
