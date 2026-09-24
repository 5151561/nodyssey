package io.github.nodyssey.ui.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.data.AttendanceMode
import io.github.plaza.core.net.SiteError
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
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
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `signed out profile renders c7 and starts sign in`() {
        var signInOpened = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(),
                    destinations = ProfileDestinations(),
                    onSignIn = { signInOpened = true },
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("登录 NodeSeek，解锁完整体验").assertIsDisplayed()
        composeRule.onNodeWithText("发帖回复").assertIsDisplayed()
        composeRule.onNodeWithText("通知私信").assertIsDisplayed()
        composeRule.onNodeWithText("签到鸡腿").assertIsDisplayed()
        composeRule.onNodeWithText("登录 NodeSeek").performClick()

        check(signInOpened)
    }

    @Test
    fun `signed out profile keeps guest destinations available`() {
        var settingsOpened = false
        var toolsOpened = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(),
                    destinations =
                    ProfileDestinations(
                        settings = { settingsOpened = true },
                        tools = { toolsOpened = true },
                    ),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        // 设置 is the gear in the bar now, as it is when signed in; 社区工具 is the last guest tile.
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("社区工具"))
        composeRule.onNodeWithText("社区工具").performClick()

        check(settingsOpened)
        check(toolsOpened)
    }

    @Test
    fun `signed in profile shows unknown level in the resource cards`() {
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "NodeSeek 用户",
                        level = null,
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("Lv —").assertIsDisplayed()
        composeRule.onNodeWithText("等级").assertIsDisplayed()
    }

    @Test
    fun `signed in profile replaces attendance streak with level`() {
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        level = "Lv 3",
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Lv 3").assertCountEquals(1)
        composeRule.onNodeWithText("等级").assertIsDisplayed()
        composeRule.onNodeWithText("连续签到").assertDoesNotExist()
    }

    /** 我的 words the level the way 账户与成长 and 鸡腿流水 do: one caption, one form. */
    @Test
    fun `the account card states level progress in the shared wording`() {
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        uid = 1,
                        displayName = "nodyssey_dev",
                        level = "Lv 1",
                        rank = 1,
                        chickenCount = 344,
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("344 / 400 · 还差 56 升到 Lv2").assertIsDisplayed()
    }

    @Test
    fun `the grid lays its sections out and each tile is one jump`() {
        var commentsOpened = false
        var inviteOpened = false
        var followersOpened = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, uid = 88423, displayName = "nodyssey_dev"),
                    destinations =
                    ProfileDestinations(
                        comments = { commentsOpened = true },
                        invite = { inviteOpened = true },
                        followers = { followersOpened = true },
                    ),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        // The groups are cards without headings (2b); the first card's tiles are on screen.
        composeRule.onNodeWithText("主题帖").assertIsDisplayed()
        composeRule.onNodeWithText("我的评论").performClick()
        composeRule.onNodeWithText("我的粉丝").performClick()
        // Below the fold on a 360x800 screen, and inside a `LazyColumn`, so it is not composed at
        // all until the list is scrolled to it. 邀请码 is in 社区 now that there is no 资产 group.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("邀请码"))
        composeRule.onNodeWithText("邀请码").performClick()

        check(commentsOpened)
        check(followersOpened)
        check(inviteOpened)
    }

    /**
     * The balances are the way to their ledgers: a 资产 group of 鸡腿流水 / 星辰流水 / 星辰转账 tiles
     * used to sit under them as a second way to the same pages, and 转账 is on 星辰流水 itself.
     */
    @Test
    fun `each balance opens its own ledger, and no 资产 tiles repeat them`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        uid = 88423,
                        displayName = "nodyssey_dev",
                        chickenCount = 344,
                        starCount = 4,
                        level = "Lv 1",
                    ),
                    destinations =
                    ProfileDestinations(
                        credit = { opened += "credit" },
                        stardust = { opened += "stardust" },
                        assets = { opened += "assets" },
                    ),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("344").performClick()
        composeRule.onNodeWithText("4").performClick()
        composeRule.onNodeWithText("Lv 1").performClick()
        check(opened == listOf("credit", "stardust", "assets")) { "opened $opened" }

        listOf("鸡腿流水", "星辰流水", "星辰转账").forEach { tile ->
            check(composeRule.onAllNodesWithText(tile).fetchSemanticsNodes().isEmpty()) { "$tile is still a tile" }
        }
    }

    /**
     * 草稿箱 and 离线下载 are on board n1 and deliberately not here: the app has one draft, restored
     * by the composer itself, and offline copies are a switch inside 收藏. A tile for either would
     * promise a screen that does not exist.
     */
    @Test
    fun `the grid draws no tile for a feature the app does not have`() {
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, uid = 88423, displayName = "nodyssey_dev"),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("草稿箱").assertDoesNotExist()
        composeRule.onNodeWithText("离线下载").assertDoesNotExist()
    }

    @Test
    fun `tapping the header name opens the space page`() {
        var clicked = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nodyssey_dev"),
                    destinations =
                    ProfileDestinations(
                        space = { clicked = true },
                    ),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("nodyssey_dev").performClick()

        check(clicked)
    }

    @Test
    fun `signed attendance shows gain and opens the board`() {
        var boardOpened = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        hasSignedInToday = true,
                        attendanceGain = 7,
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = { boardOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("今日已签 +7 鸡腿").performClick()

        check(boardOpened)
    }

    @Test
    fun `unsigned attendance opens the sign in flow`() {
        var attendanceOpened = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nodyssey_dev"),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = { attendanceOpened = true },
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("签到").performClick()

        check(attendanceOpened)
    }

    @Test
    fun `the sign in chooser is answered on the profile screen itself`() {
        var picked: AttendanceMode? = null
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        choosingAttendanceMode = true,
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onSignInForToday = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("固定").performClick()

        assertEquals(AttendanceMode.FIXED_FIVE, picked)
    }

    /**
     * 领鸡腿 refused by a wall used to say 需要确认一下你不是机器人 and offer nothing to press, which
     * for a challenge is the whole failure: the one control that clears it lives in the web view,
     * and the reader was never told so.
     */
    @Test
    fun `a wall refusing the sign in offers the verify button`() {
        var verified = false
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        attendanceKnown = true,
                        attendanceFailure = SiteError.Cloudflare("https://www.nodeseek.com/page-2"),
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = { verified = true },
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("需要确认一下你不是机器人").assertIsDisplayed()
        composeRule.onNodeWithText("去验证").performClick()

        assertTrue(verified)
    }

    @Test
    fun `attendance board is rendered over the profile screen`() {
        composeRule.setContent {
            PlazaTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        hasSignedInToday = true,
                        attendanceGain = 7,
                        boardOpen = true,
                        board =
                        listOf(
                            AttendanceBoardEntry(
                                uid = 31037,
                                name = "缭雾",
                                gain = 7,
                                timeText = "刚刚",
                            ),
                        ),
                    ),
                    destinations = ProfileDestinations(),
                    onSignIn = {},
                    onRetry = {},
                    onOpenWebsite = {},
                    onVerify = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                )
            }
        }

        composeRule.onNodeWithText("今日签到榜").assertIsDisplayed()
        composeRule.onNodeWithText("缭雾").assertIsDisplayed()
        composeRule.onNodeWithText("+7").assertIsDisplayed()
    }

    /**
     * 我的 shares the activity's lifecycle and re-enters composition on every tab switch, so an
     * observer that joins an already-resumed owner is handed a replayed ON_RESUME. Reacting to that
     * one is what re-checked the sign-in on every visit.
     */
    @Test
    fun `foreground effect ignores the resume replayed to a late observer`() {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        var refreshes = 0
        var attached by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (attached) RefreshOnReturnToForeground { refreshes++ }
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, refreshes)

        // Leaving 我的 for another tab and coming back: a second late observer, a second replay.
        attached = false
        composeRule.waitForIdle()
        attached = true
        composeRule.waitForIdle()

        assertEquals(0, refreshes)
    }

    @Test
    fun `foreground effect reacts to a real return to the foreground`() {
        val owner = FakeLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        var refreshes = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RefreshOnReturnToForeground { refreshes++ }
            }
        }
        composeRule.waitForIdle()

        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.RESUMED
        composeRule.waitForIdle()

        assertEquals(1, refreshes)
    }
}

private class FakeLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry
}
