package io.github.nsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.nsreader.data.settings.ThemeMode
import io.github.nsreader.data.settings.UserSettings
import io.github.nsreader.ui.theme.NodeSeekTheme

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
