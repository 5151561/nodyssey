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
    fontScale: Float = 1f,
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
        /*
         * Plain `MaterialTheme`, and not for lack of trying.
         *
         * `MaterialExpressiveTheme` — with the motion scheme that gives components springs instead
         * of tweens — is compiled into material3 1.4.0 but declared `internal`; so are
         * `MotionScheme` and `MaterialTheme.motionScheme`. It first becomes public API in 1.5.0,
         * which is still alpha. Moving the whole app onto an alpha Material release to gain a
         * motion scheme is not a trade worth making, so what this file provides is the M3 *token*
         * system — colour, type, shape — and the "Expressive" in the branch name refers to that.
         *
         * Revisit when material3 1.5.0 ships stable in the Compose BOM: switch this call and drop
         * the hand-rolled animation specs at the two call sites that have them.
         */
        MaterialTheme(
            colorScheme = colorScheme,
            typography = nodeSeekTypography(fontScale),
            shapes = NodeSeekShapes,
            content = content,
        )
    }
}
