package io.github.nodyssey.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun NodysseyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The brand palette is the default and the point: the app should be recognisable from a
    // screenshot posted back to the forum. Wallpaper colors stay available, but opt-in.
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> NodysseyDarkColorScheme
            else -> NodysseyLightColorScheme
        }

    // The amber board-tag pair has no Material role, so it rides alongside the scheme rather than
    // being read from a global — otherwise it would not follow the theme.
    CompositionLocalProvider(
        LocalNodysseyExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            // Remembered rather than rebuilt: the scale only moves when the reading-size setting
            // does, and each call copies three TextStyles.
            typography = remember(fontScale) { nodysseyTypography(fontScale) },
            shapes = NodysseyShapes,
            content = content,
        )
    }
}
