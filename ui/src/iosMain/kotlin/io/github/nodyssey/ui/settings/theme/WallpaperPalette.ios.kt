package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIDevice

/**
 * Empty, which the type already treats as an answer rather than a failure.
 *
 * iOS does not hand an app the wallpaper — not the image and not colours read off it — so there is
 * nothing here to read and nothing to retry. [supportsWallpaperColorSource] answers false for the
 * same reason, so 取色来源 never offers 壁纸 and this is never asked in the first place; it exists
 * because the type is not nullable, not because a screen is waiting on it.
 */
@Composable
internal actual fun rememberWallpaperPalette(retryKey: Int): WallpaperPalette =
    remember { WallpaperPalette() }

/** `18.0` and the like — what iOS calls its own version. */
actual fun osVersionName(): String = UIDevice.currentDevice.systemVersion

/** False: see [rememberWallpaperPalette]. The tile is hidden rather than shown dead. */
actual fun supportsWallpaperColorSource(): Boolean = false
