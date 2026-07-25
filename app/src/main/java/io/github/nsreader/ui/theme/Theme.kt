package io.github.nsreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun NodeSeekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The brand palette is the default and the point: the app should be recognisable from a
    // screenshot posted back to the forum. Wallpaper colors stay available, but opt-in.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> NodeSeekDarkColorScheme
            else -> NodeSeekLightColorScheme
        }

    // The amber board-tag pair has no Material role, so it rides alongside the scheme rather than
    // being read from a global — otherwise it would not follow the theme.
    CompositionLocalProvider(
        LocalNodeSeekExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NodeSeekTypography,
            shapes = NodeSeekShapes,
            content = content,
        )
    }
}
