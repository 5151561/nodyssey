package io.github.nsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.data.settings.UserSettings
import io.github.nsreader.data.settings.isTimedNightHour
import io.github.nsreader.ui.theme.NodeSeekTheme
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NodeSeekApp).container

        setContent {
            // Theme reads the settings SSOT directly. No copy is kept anywhere, so changing the
            // setting can never leave part of the app on the old value.
            val settings by container.settingsRepository.settings
                .collectAsState(initial = UserSettings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.TIMED -> isTimedNightNow()
            }

            NodeSeekTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                fontScale = settings.fontScale,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainNavigation(container = container)
                }
            }
        }
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
