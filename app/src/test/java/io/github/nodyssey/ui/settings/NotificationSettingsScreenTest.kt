package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.theme.NodysseyTheme
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
class NotificationSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(
        settings: UserSettings,
        onEnabledChange: (Boolean) -> Unit = {},
        onPollMinutesChange: (Int) -> Unit = {},
    ) {
        NodysseyTheme {
            NotificationSettingsScreen(
                settings = settings,
                onBack = {},
                onEnabledChange = onEnabledChange,
                onPollMinutesChange = onPollMinutesChange,
                onWifiOnlyChange = {},
                onQuietHoursChange = {},
                onNotifyMentionsChange = {},
                onNotifyRepliesChange = {},
                onNotifyMessagesChange = {},
                onOpenTelegram = {},
            )
        }
    }

    @Test
    fun `master switch toggles and reflects the store`() {
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            Screen(
                settings = UserSettings(notificationsEnabled = enabled),
                onEnabledChange = { enabled = it },
            )
        }

        val master = composeRule.onAllNodes(isToggleable()).onFirst()
        master.assertIsOff()
        master.performClick()
        master.assertIsOn()
    }

    @Test
    fun `channel switches are inert while the master switch is off`() {
        composeRule.setContent {
            Screen(settings = UserSettings(notificationsEnabled = false))
        }

        composeRule
            .onAllNodes(isToggleable())[1]
            .assertIsNotEnabled()
    }

    @Test
    fun `frequency choice reports the chosen minutes`() {
        var chosen: Int? = null
        composeRule.setContent {
            Screen(
                settings = UserSettings(notificationsEnabled = true),
                onPollMinutesChange = { chosen = it },
            )
        }

        composeRule.onNodeWithText("30 分钟").assertIsSelected()
        composeRule.onNodeWithText("15 分钟").performClick()
        assertEquals(15, chosen)
    }
}
