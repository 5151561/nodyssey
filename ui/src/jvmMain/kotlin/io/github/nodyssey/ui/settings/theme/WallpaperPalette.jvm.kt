package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable

/**
 * No wallpaper to read.
 *
 * An empty palette is what the 读不到壁纸 card is for, so the screen already words this case; and
 * [supportsWallpaperColorSource] returning false keeps 取色来源 from offering the tile that leads to
 * it in the first place.
 */
@Composable
internal actual fun rememberWallpaperPalette(retryKey: Int): WallpaperPalette = WallpaperPalette()

actual fun osVersionName(): String = System.getProperty("os.version").orEmpty()

actual fun supportsWallpaperColorSource(): Boolean = false
