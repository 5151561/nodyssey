package io.github.nodyssey.ui.settings.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * What 动态取色 has to offer, and whether it managed to read anything at all.
 *
 * [candidates] is ordered the way j1 says — most prominent first — and empty exactly when the read
 * failed, which is what puts the 读不到壁纸 card on screen.
 */
internal data class WallpaperPalette(
    val candidates: List<Color> = emptyList(),
    /** Whether this phone has a system Monet palette to hand through (API 31+). */
    val systemPaletteAvailable: Boolean = false,
)

/**
 * Reads the wallpaper's colours, and re-reads them when [retryKey] moves.
 *
 * `getWallpaperColors` rather than the wallpaper bitmap: it needs no permission, it is what the
 * system's own theme picker reads, and it is the only half of the wallpaper this app has any use
 * for. Reading the image itself would mean `MANAGE_EXTERNAL_STORAGE` on anything since API 33 —
 * which is why j1's wallpaper thumbnail is not drawn here.
 *
 * On API 31+ the three system accent tones join the list. They are wallpaper-derived too — the same
 * extraction, run by the system rather than by this call — so they belong in the same row, and they
 * are what takes the list from the three colours `WallpaperColors` carries to j1's six.
 */
@Composable
internal fun rememberWallpaperPalette(retryKey: Int): WallpaperPalette {
    val context = LocalContext.current
    return remember(context, retryKey) { readWallpaperPalette(context) }
}

/** A counter to hang 重试 on; the read is synchronous, so re-reading is all retrying can mean. */
@Composable
internal fun rememberRetryKey(): Pair<Int, () -> Unit> {
    var key by remember { mutableIntStateOf(0) }
    return key to { key++ }
}

private fun readWallpaperPalette(context: Context): WallpaperPalette {
    val systemPalette = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fromWallpaper =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
                ?.let { listOfNotNull(it.primaryColor, it.secondaryColor, it.tertiaryColor) }
                ?.map { Color(it.toArgb()) }
                .orEmpty()
        } else {
            emptyList()
        }
    val fromSystem = if (systemPalette) systemAccents(context) else emptyList()
    return WallpaperPalette(
        // Distinct because a plain wallpaper hands the same tone back twice, and two identical dots
        // read as a control that does not respond.
        candidates = (fromWallpaper + fromSystem).distinct().take(MAX_CANDIDATES),
        systemPaletteAvailable = systemPalette,
    )
}

/**
 * The system's own wallpaper-derived accents, at the tone the palette is named for.
 *
 * Referenced by id rather than through `dynamicLightColorScheme`, which returns a whole
 * `ColorScheme` and not the seeds behind it — and it is a seed this screen is picking. The ids only
 * exist from API 31, which is why they are behind their own annotated function rather than inline in
 * a `when`: lint reads the annotation, not the flag the caller computed two lines earlier.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun systemAccents(context: Context): List<Color> =
    listOf(
        android.R.color.system_accent1_500,
        android.R.color.system_accent2_500,
        android.R.color.system_accent3_500,
    ).mapNotNull { id -> runCatching { Color(ContextCompat.getColor(context, id)) }.getOrNull() }

private const val MAX_CANDIDATES = 6
