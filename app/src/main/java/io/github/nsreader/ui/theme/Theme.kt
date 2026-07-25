package io.github.nsreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NodeSeekBlue = Color(0xFF2D7FF9)

private val LightColors = lightColorScheme(
    primary = NodeSeekBlue,
    onPrimary = Color.White,
    surface = Color(0xFFFDFDFD),
    background = Color(0xFFFDFDFD),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outlineVariant = Color(0xFFE6E8EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EB0FF),
    onPrimary = Color(0xFF00305F),
    surface = Color(0xFF121316),
    background = Color(0xFF121316),
    surfaceVariant = Color(0xFF1E2024),
    onSurfaceVariant = Color(0xFF9AA1AC),
    outlineVariant = Color(0xFF2A2D33),
)

@Composable
fun NodeSeekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You looks great but a reading app benefits from a predictable, quiet palette,
    // so wallpaper colors are opt-in rather than the default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NodeSeekTypography,
        content = content,
    )
}
