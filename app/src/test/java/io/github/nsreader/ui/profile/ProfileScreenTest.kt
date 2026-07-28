package io.github.nsreader.ui.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nsreader.data.AttendanceBoardEntry
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
    fun `signed in profile shows unknown level in the resource cards`() {
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
                    onOpenSpace = {},
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
                    onOpenSpace = {},
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
    fun `header avatar action opens the space page`() {
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
                    onOpenSpace = { clicked = true },
                    onAssets = {},
                    onAttendance = {},
                    onAttendanceBoard = {},
                    onFollow = {},
                    onTools = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("我的主页").performClick()

        check(clicked)
    }

    @Test
    fun `signed attendance shows gain and opens the board`() {
        var boardOpened = false
        composeRule.setContent {
            NodeSeekTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nsreader_dev",
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
            NodeSeekTheme {
                ProfileScreen(
                    state = ProfileUiState(isSignedIn = true, displayName = "nsreader_dev"),
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onSettings = {},
                    onAccountSettings = {},
                    onOpenWebsite = {},
                    onOpenSpace = {},
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
            NodeSeekTheme {
                ProfileScreen(
                    state =
                    ProfileUiState(
                        isSignedIn = true,
                        displayName = "nsreader_dev",
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
}
