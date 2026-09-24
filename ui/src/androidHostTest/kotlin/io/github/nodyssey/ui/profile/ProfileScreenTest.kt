package io.github.nodyssey.ui.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
