package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * iOS has no wallpaper palette to take, so 使用系统调色板 has nothing to answer with.
 *
 * The same null the desktop actual returns, and for a stronger reason than "no API": iOS does not
 * expose the wallpaper to an app at all, and its own accent colour is a tint, not a Material scheme.
 * [PlazaTheme] falls back to the generated 石墨青 scheme.
 */
@Composable
internal actual fun platformSystemColorScheme(darkTheme: Boolean): ColorScheme? = null
