package io.github.plaza.designsys.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the app is currently drawing dark.
 *
 * `isSystemInDarkTheme()` is not the same question: 深色 and 定时 both make the app dark while the
 * system stays light. Anything that has to tell a surface *outside* the Compose tree which way the
 * app is leaning — the Custom Tab toolbar, so far — reads this instead of re-deriving the setting
 * and getting a different answer.
 */
val LocalPlazaDarkTheme = staticCompositionLocalOf { false }

/**
 * The in-app reading-size preference, for the few things that are sized in `sp` but are not text.
 *
 * [plazaTypography] carries the scale for everything written in a Material role. What it cannot
 * reach is type declared outside the scale — the board tag, which sits between `labelSmall` and
 * nothing — and the icons that stand in for words on a meta line, which have to grow with the
 * number beside them or the row stops reading as one line. Those read the scale from here.
 *
 * Already clamped: a caller multiplies rather than re-deciding the bounds.
 */
val LocalPlazaFontScale = staticCompositionLocalOf { 1f }

@Composable
fun PlazaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The brand palette is the default and the point: the app should be recognisable from a
    // screenshot posted back to the forum. The wallpaper and a hand-picked seed stay available,
    // but opt-in.
    colorSource: PlazaColorSource = PlazaColorSource.BRAND,
    seedColor: Color = PlazaDefaultSeed,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Below API 31 there is no wallpaper palette to read, so that source falls through to the brand
    // one. The stored setting is left alone: it is the reader's answer, not this phone's capability.
    val useWallpaper =
        colorSource == PlazaColorSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme =
        when {
            useWallpaper && darkTheme -> dynamicDarkColorScheme(context)

            useWallpaper -> dynamicLightColorScheme(context)

            // Remembered because generating one is real work — an HCT solve per role — and it would
            // otherwise rerun on every recomposition of the whole app.
            colorSource == PlazaColorSource.SEED ->
                remember(seedColor, darkTheme) { plazaSeedColorScheme(seedColor, darkTheme) }

            darkTheme -> PlazaDarkColorScheme

            else -> PlazaLightColorScheme
        }

    // The amber board-tag pair has no Material role, so it rides alongside the scheme rather than
    // being read from a global — otherwise it would not follow the theme.
    CompositionLocalProvider(
        LocalPlazaExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
        LocalPlazaDarkTheme provides darkTheme,
        LocalPlazaFontScale provides fontScale.coerceIn(MIN_TYPE_SCALE, MAX_TYPE_SCALE),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            // Remembered rather than rebuilt: the scale only moves when the reading-size setting
            // does, and each call copies three TextStyles.
            typography = remember(fontScale) { plazaTypography(fontScale) },
            shapes = PlazaShapes,
            content = content,
        )
    }
}
