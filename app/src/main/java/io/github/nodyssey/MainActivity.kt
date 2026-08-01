package io.github.nodyssey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.data.settings.ThemeMode
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.data.settings.isTimedNightHour
import io.github.nodyssey.ui.common.rememberExternalUriHandler
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.theme.NodysseyTheme
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NodysseyApp).container
        // Only a poll notification carries the extra; a cold start from it should land on 通知.
        // A saved UI state still wins — see the rememberSaveable inside MainNavigation.
        val initialTab =
            if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTIFICATIONS) {
                TopLevelDestination.NOTIFICATIONS
            } else {
                TopLevelDestination.HOME
            }

        setContent {
            // Theme reads the settings SSOT directly. No copy is kept anywhere, so changing the
            // setting can never leave part of the app on the old value.
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.TIMED -> isTimedNightNow()
            }

            NodysseyTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                fontScale = settings.fontScale,
            ) {
                // Every link that leaves the app goes through LocalUriHandler — the explicit
                // `openUri` calls in Navigation and the ones Compose resolves for a link inside post
                // text alike. Overriding it here, inside the theme so the tab can match the colours
                // on screen, is what makes 外部链接打开方式 apply everywhere at once.
                CompositionLocalProvider(
                    LocalUriHandler provides
                        rememberExternalUriHandler(settings.externalLinkTarget),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainNavigation(container = container, initialTab = initialTab)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "io.github.nodyssey.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"
    }
}

/**
 * True while the 定时 night window is on, re-evaluated once a minute.
 *
 * A minute is coarse enough to cost nothing and fine enough that the theme flips within a minute of
 * the boundary — nobody is watching the screen at 19:00:00 to catch the difference. The wall clock is
 * read directly rather than injected: this composable exists for one Activity, and the pure decision
 * it wraps ([isTimedNightHour]) is what the tests cover.
 */
@Composable
private fun isTimedNightNow(): Boolean {
    val isNight by produceState(
        initialValue = isTimedNightHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
    ) {
        while (true) {
            delay(60_000)
            value = isTimedNightHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        }
    }
    return isNight
}
