package io.github.plaza.designsys.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Whether the app is currently drawing dark.
 *
 * `isSystemInDarkTheme()` is not the same question: 深色 and 定时 both make the app dark while the
 * system stays light. Anything that has to tell a surface *outside* the Compose tree which way the
 * app is leaning — the Custom Tab toolbar, so far — reads this instead of re-deriving the setting
 * and getting a different answer.
 */
val LocalPlazaDarkTheme = staticCompositionLocalOf { false }

/**
 * The in-app reading-size preference, for the few things that are sized in `sp` but are not text.
 *
 * [plazaTypography] carries the scale for everything written in a Material role. What it cannot
 * reach is type declared outside the scale — the board tag, which sits between `labelSmall` and
 * nothing — and the icons that stand in for words on a meta line, which have to grow with the
 * number beside them or the row stops reading as one line. Those read the scale from here.
 *
 * Already clamped: a caller multiplies rather than re-deciding the bounds.
 */
val LocalPlazaFontScale = staticCompositionLocalOf { 1f }

/**
 * 单手模式 — whether [io.github.plaza.designsys.component.OneHandTopAppBar] is allowed any blank
 * above its toolbar at all.
 *
 * A composition local rather than a parameter on the bar, because the answer is the same on all
 * twenty-odd screens that carry one and none of them has anything to add to it. Off, every such bar
 * measures its blank at zero and is an ordinary pinned Material toolbar — the screens themselves do
 * not change, which is what keeps the switch from being a second layout to maintain.
 *
 * Defaults to on, so a preview or a test that only calls [PlazaTheme] gets the bar the app ships
 * with.
 */
val LocalOneHandMode = staticCompositionLocalOf { true }

@Composable
fun PlazaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Every generated scheme in the app comes from a seed — 石墨青, the wallpaper candidates and a
    // hand-picked colour alike — so this one parameter is the whole colour input for all three. The
    // default is 石墨青, which is what the app should be recognisable as from a screenshot posted
    // back to the forum. 角色预设 is the exception; see [characterPalette].
    seedColor: Color = PlazaDefaultSeed,
    paletteStyle: PlazaPaletteStyle = PlazaPaletteStyle.SOFT,
    /**
     * 角色预设 — a scheme written by hand rather than grown from [seedColor].
     *
     * It wins over both of the other two when set, because it is not a seed and there is nothing
     * for the generator or the system palette to do with it. See [PlazaCharacterPalette].
     */
    characterPalette: PlazaCharacterPalette? = null,
    /**
     * 使用系统调色板 — take the OS's own Monet scheme rather than generating one.
     *
     * Ignored wherever the platform has no such palette to take — on Android that is below API 31.
     * The stored setting is left alone: it is the reader's answer, not this phone's capability.
     */
    useSystemPalette: Boolean = false,
    fontScale: Float = 1f,
    /** 单手模式; see [LocalOneHandMode] for what turning it off does. */
    oneHandMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Asked for only when it could win: 角色预设 beats it, and building a scheme that is about to be
    // discarded is work on every recomposition of the whole app. Null means this platform — or this
    // Android version — has no system palette; see [platformSystemColorScheme].
    val systemColorScheme =
        if (useSystemPalette && characterPalette == null) platformSystemColorScheme(darkTheme) else null
    val colorScheme =
        when {
            characterPalette != null -> characterPalette.colorScheme(darkTheme)

            systemColorScheme != null -> systemColorScheme

            // Remembered because generating one is real work — an HCT solve per role — and it would
            // otherwise rerun on every recomposition of the whole app.
            else ->
                remember(seedColor, darkTheme, paletteStyle) {
                    plazaSeedColorScheme(seedColor, darkTheme, paletteStyle)
                }
        }

    // The amber board-tag pair has no Material role, so it rides alongside the scheme rather than
    // being read from a global — otherwise it would not follow the theme.
    CompositionLocalProvider(
        LocalPlazaExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors,
        LocalPlazaDarkTheme provides darkTheme,
        LocalPlazaFontScale provides fontScale.coerceIn(MIN_TYPE_SCALE, MAX_TYPE_SCALE),
        LocalOneHandMode provides oneHandMode,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            // Remembered rather than rebuilt: the scale only moves when the reading-size setting
            // does, and each call copies three TextStyles.
            typography = remember(fontScale) { plazaTypography(fontScale) },
            shapes = PlazaShapes,
            content = content,
        )
    }
}
