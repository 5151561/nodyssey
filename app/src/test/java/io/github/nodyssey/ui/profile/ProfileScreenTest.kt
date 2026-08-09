package io.github.nodyssey.ui.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.plaza.designsys.theme.NodysseyTheme
import org.junit.Assert.assertEquals
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
            NodysseyTheme {
                ProfileScreen(
                    state = ProfileUiState(),
                    onSignIn = { signInOpened = true },
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = {},
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
            NodysseyTheme {
                ProfileScreen(
                    state = ProfileUiState(),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = { settingsOpened = true },
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = { toolsOpened = true },
                )
            }
        }

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("社区工具").performClick()

        check(settingsOpened)
        check(toolsOpened)
    }

    @Test
    fun `signed in profile shows unknown level in the resource cards`() {
        composeRule.setContent {
            NodysseyTheme {
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
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
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
            NodysseyTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        level = "Lv 3",
                    ),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
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
    fun `tapping the header name opens the space page`() {
        var clicked = false
        composeRule.setContent {
            NodysseyTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nodyssey_dev"),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = { clicked = true },
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = {},
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
            NodysseyTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nodyssey_dev",
                        hasSignedInToday = true,
                        attendanceGain = 7,
                    ),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = { boardOpened = true },
                    onFollow = {},
                    onTools = {},
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
            NodysseyTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nodyssey_dev"),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = { attendanceOpened = true },
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = {},
                )
            }
        }

        composeRule.onNodeWithText("今日签到 · 领鸡腿").performClick()

        check(attendanceOpened)
    }

    @Test
    fun `attendance board is rendered over the profile screen`() {
        composeRule.setContent {
            NodysseyTheme {
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
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
                    onCollections = {},
                    onHistory = {},
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = {},
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
