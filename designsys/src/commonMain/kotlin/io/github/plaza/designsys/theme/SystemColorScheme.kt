package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The scheme the OS built from the wallpaper, or null where there is no such thing.
 *
 * A seam rather than a shared implementation because 系统调色板 is not a Material feature every
 * platform can be expected to have: `dynamicLightColorScheme` / `dynamicDarkColorScheme` exist in
 * androidx `material3` and only there — in Compose Multiplatform's `material3` the two names appear
 * in documentation and nowhere in the sources, because taking colour from the wallpaper is an
 * Android 12+ capability rather than something Material specifies.
 *
 * Null is therefore the honest answer for a platform without one, and [PlazaTheme] already handles
 * it: it falls back to the generated 石墨青 scheme, which is what a reader with 使用系统调色板
 * switched on already sees on an Android 11 phone.
 */
@Composable
internal expect fun platformSystemColorScheme(darkTheme: Boolean): ColorScheme?
