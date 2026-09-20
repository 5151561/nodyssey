package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            ProfileScreen(
                state = STATE,
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

    private companion object {
        val STATE =
            ProfileUiState(
                isSignedIn = true,
                uid = 88423,
                displayName = "nodyssey_dev",
                avatarUrl = null,
                level = "Lv.4",
                registeredYear = 2023,
                registeredMonth = 6,
                chickenCount = 1286,
                starCount = 4520,
                attendanceKnown = true,
                hasSignedInToday = true,
                attendanceGain = 8,
            )
    }
}
