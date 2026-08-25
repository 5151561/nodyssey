package io.github.nodyssey.ui.common

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Re-runs `enableEdgeToEdge` with the theme's own answer whenever it changes.
 *
 * The bare call in `MainActivity.onCreate` styles the first frame from the OS's night mode, which
 * is right until 主题外观 disagrees with it: forced dark under a light system left dark icons on
 * the app's dark background. `enableEdgeToEdge` is documented as callable again with new styles,
 * which is exactly what a theme that can change while the Activity lives needs.
 *
 * Both scrims transparent, matching what the bare call resolves on this app's minSdk 26+: the app
 * draws its own background behind the bars, and the explicit styles here exist only to pick the
 * *icon* contrast.
 */
@Composable
internal actual fun SystemBarsMatchTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, darkTheme) {
        val style =
            if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
