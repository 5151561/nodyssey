package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Desktop has no wallpaper palette to take, so 使用系统调色板 has nothing to answer with.
 *
 * Null rather than a guess at a desktop equivalent: neither macOS's accent colour nor Windows's is
 * a Material scheme, and inventing one from it would put a colour on screen that no design decided.
 * [PlazaTheme] falls back to the generated 石墨青 scheme, which is the same thing an Android 11
 * phone shows.
 */
@Composable
internal actual fun platformSystemColorScheme(darkTheme: Boolean): ColorScheme? = null
