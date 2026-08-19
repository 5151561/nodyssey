package io.github.plaza.designsys.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The scheme the OS built from the wallpaper, or null where there is no such thing.
 *
 * Its own platform-named file because 系统调色板 is not a Material feature this module could
 * reasonably expect everywhere: `dynamicLightColorScheme` / `dynamicDarkColorScheme` exist in
 * androidx `material3` and *only* there — in Compose Multiplatform's `material3` the two names
 * appear in documentation and nowhere in the sources, because taking colour from the wallpaper is an
 * Android 12+ capability rather than something Material specifies. Returning null is therefore the
 * honest answer for a platform without one, and it is also the answer this phone gives below API 31.
 *
 * [PlazaTheme] falls back to the generated 石墨青 scheme when it gets null, which is what a reader
 * with 使用系统调色板 switched on already sees on an Android 11 phone.
 */
@Composable
internal fun platformSystemColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
