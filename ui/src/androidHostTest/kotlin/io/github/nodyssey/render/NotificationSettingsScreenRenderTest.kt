package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.settings.NotificationSettingsScreen
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 通知提醒 in both themes, tall enough to show the Telegram card at the bottom (6d). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp")
class NotificationSettingsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            NotificationSettingsScreen(
                settings = UserSettings(notificationsEnabled = true, notificationQuietHours = true),
                onBack = {},
                onEnabledChange = {},
                onPollMinutesChange = {},
                onWifiOnlyChange = {},
                onQuietHoursChange = {},
                onNotifyInteractionsChange = {},
                onNotifyMessagesChange = {},
                onOpenTelegram = {},
            )
        }
    }

    @Test
    fun `notification settings in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("notification-settings-light")
    }

    @Test
    fun `notification settings in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("notification-settings-dark")
    }
}
