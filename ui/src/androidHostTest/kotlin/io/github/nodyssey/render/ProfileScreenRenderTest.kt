package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.ui.profile.ProfileDestinations
import io.github.nodyssey.ui.profile.ProfileScreen
import io.github.nodyssey.ui.profile.ProfileUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 我的 in both themes — a signed-in account with its level, tenure and balances. See
 * [PostListScreenRenderTest] on the null avatar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ProfileScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(
        darkTheme: Boolean,
        state: ProfileUiState = STATE,
    ) {
        PlazaTheme(darkTheme = darkTheme) {
            ProfileScreen(
                state = state,
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

    @Test
    fun `the profile in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("profile-light")
    }

    @Test
    fun `the profile in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("profile-dark")
    }

    @Test
    fun `the profile signed in today, in light`() {
        composeRule.setContent {
            Screen(darkTheme = false, state = STATE.copy(hasSignedInToday = true, attendanceGain = 5))
        }

        composeRule.onRoot().captureRender("profile-signed-light")
    }

    @Test
    fun `the signed-out profile in light`() {
        composeRule.setContent { Screen(darkTheme = false, state = ProfileUiState()) }

        composeRule.onRoot().captureRender("profile-guest-light")
    }

    @Test
    fun `the signed-out profile in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = ProfileUiState()) }

        composeRule.onRoot().captureRender("profile-guest-dark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the sign-in chooser in light`() {
        composeRule.setContent { Screen(darkTheme = false, state = STATE.copy(choosingAttendanceMode = true)) }
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/profile-attendance-mode-light.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the attendance board in light`() {
        composeRule.setContent { Screen(darkTheme = false, state = STATE.copy(boardOpen = true, board = BOARD)) }
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/profile-board-light.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun `the attendance board in dark`() {
        composeRule.setContent { Screen(darkTheme = true, state = STATE.copy(boardOpen = true, board = BOARD)) }
        composeRule.waitForIdle()

        captureScreenRoboImage(filePath = "build/outputs/renders/profile-board-dark.png")
    }

    private companion object {
        val BOARD =
            listOf(
                AttendanceBoardEntry(101, "sunrise_vps", 7, null),
                AttendanceBoardEntry(102, "kvm_fan", 3, null),
                AttendanceBoardEntry(103, "轻舟", 5, null),
                AttendanceBoardEntry(104, "codemonkey", 2, null),
                AttendanceBoardEntry(88423, "nodyssey_dev", 5, null),
            )

        val STATE =
            ProfileUiState(
                isSignedIn = true,
                uid = 88423,
                displayName = "nodyssey_dev",
                avatarUrl = null,
                level = "Lv 1",
                rank = 1,
                registeredYear = 2023,
                registeredMonth = 6,
                chickenCount = 344,
                starCount = 4,
                attendanceKnown = true,
            )
    }
}
