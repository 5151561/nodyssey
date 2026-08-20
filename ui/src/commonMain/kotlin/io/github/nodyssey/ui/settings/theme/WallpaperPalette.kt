package io.github.nodyssey.ui.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * What 动态取色 has to offer, and whether it managed to read anything at all.
 *
 * [candidates] is ordered the way j1 says — most prominent first — and empty exactly when the read
 * failed, which is what puts the 读不到壁纸 card on screen. Empty is also what a platform with no
 * wallpaper to read answers, and the same card is the right thing to show for it.
 */
internal data class WallpaperPalette(
    val candidates: List<Color> = emptyList(),
    /** Whether this phone has a system Monet palette to hand through (API 31+ on Android). */
    val systemPaletteAvailable: Boolean = false,
)

/**
 * Reads the wallpaper's colours, and re-reads them when [retryKey] moves.
 *
 * `expect` because "the wallpaper" is not a thing every platform has, and where it is, reading it is
 * a system service call. See the Android actual for why the colours rather than the image.
 */
@Composable
internal expect fun rememberWallpaperPalette(retryKey: Int): WallpaperPalette

/** A counter to hang 重试 on; the read is synchronous, so re-reading is all retrying can mean. */
@Composable
internal fun rememberRetryKey(): Pair<Int, () -> Unit> {
    var key by remember { mutableIntStateOf(0) }
    return key to { key++ }
}

/**
 * The OS version as 动态取色 names it when it has to explain why the system palette is unavailable.
 *
 * A string rather than a number: it is quoted into a sentence, and what counts as a version name is
 * the platform's own business — "16" on Android, and something with a different shape anywhere else.
 */
expect fun osVersionName(): String

/**
 * Whether 取色来源 should offer 壁纸 at all.
 *
 * False hides the tile rather than showing a dead one. On Android this is API 31+, which is where
 * both the system accents and `dynamicLightColorScheme` start existing.
 */
expect fun supportsWallpaperColorSource(): Boolean
