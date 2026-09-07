package io.github.plaza.designsys.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 墨水屏模式 — the scheme for a screen that only shows grey and charges for every pixel it changes.
 *
 * Written by hand for the same reason 角色预设 is, and for a different one on top: a generator solves
 * for hue, and there is no hue here. What it would have to solve for instead — how many greys, and
 * which — is the one question this file answers, and the answer is four. An e-ink panel renders
 * sixteen levels, but only a handful survive being told apart at a glance under a front light, and
 * every extra level is one more place for the panel to dither a flat fill into a screen door.
 *
 * There is no dark table. 墨水屏模式 forces light in `NodysseyRoot` — white on black leaves the
 * heavier ghost and saves nothing on a reflective panel, which is why paper books are not black. If
 * that is ever opened up, the dark scheme is this table with Paper and Ink swapped and Panel moved
 * to the light side of the ground; it is not written until somebody asks for it.
 *
 * Known not covered, all deliberate: the ANSI terminal block keeps its One Dark sixteen
 * (`component/TerminalText.kt` — self-consistent and still readable desaturated), 新手引导's figures
 * keep their alphas (seen once, on the first launch ever), and 主题's own swatches keep their
 * gradients (that screen draws colour itself, and a reader who opens it in this mode should see
 * what the panel will do to it).
 */

/** Ground. */
private val Paper = Color(0xFFFFFFFF)

/** Text, borders, and every accent role — 21:1 on [Paper]. */
private val Ink = Color(0xFF000000)

/**
 * The one middle grey, and the whole of what keeps this scheme from collapsing into two colours.
 *
 * 1.22:1 against [Paper] — nearly invisible on a backlit display, one or two levels on a panel,
 * which is exactly enough to give every container that floats above the content an edge. See the
 * note on the container ladder below for why that edge has to come from a fill rather than a
 * border.
 */
private val Panel = Color(0xFFE8E8E8)

/** Secondary text — 10.9:1 on [Paper], 8.9:1 on [Panel]. */
private val Muted = Color(0xFF3D3D3D)

/** Dividers — 6.9:1 on [Paper]. Not [Ink], or a list of ten rows reads as a grid. */
private val Rule = Color(0xFF5A5A5A)

/**
 * The scheme 墨水屏模式 draws with.
 *
 * Every one of Material's forty-eight roles is named, none left to the baseline: the twelve `…Fixed`
 * roles no component in Material 3 1.5 reads today would come back purple the day one does, and a
 * purple in this scheme is not a mismatch but a hole in its only promise.
 *
 * Three collapses are load-bearing and cannot be undone by picking better values:
 *
 * - `primary`, `secondary`, `tertiary` and `error` are all [Ink]. Four accents that a reader tells
 *   apart by hue on a colour screen are one accent here; what separates them is the icon and the
 *   wording beside them. This is what costs the most — 测评报告's 低风险 / 高风险 lose their green
 *   and their red and keep only their words — and there is no version of 1-bit where it does not.
 * - The container ladder is two rungs, not five. `surface`, `surfaceContainerLowest` and
 *   `surfaceBright` are [Paper]; the other four containers are [Panel]. Flattening all five to
 *   [Paper] would delete tonal elevation, which is the goal — but it also deletes the boundary of
 *   every component that draws on one: `ModalBottomSheet` takes `surfaceContainerLow`,
 *   `NavigationBar` and `DropdownMenu` take `surfaceContainer`, `AlertDialog` and `SearchBar` take
 *   `surfaceContainerHigh`, `Card` and a filled `TextField` take `surfaceContainerHighest`. None of
 *   those exposes a `border`, and `DropdownMenu` keeps its container out of reach entirely, so an
 *   edge cannot be added from the outside. One shared grey buys all of them one, for nothing.
 * - `outline` is [Ink] rather than a grey, so everything that already draws a border — an
 *   `OutlinedTextField`, an unselected `FilterChip`, the borders 墨水屏模式 adds where a shadow used
 *   to be — gets the strongest edge available. `outlineVariant` stays [Rule] for dividers.
 */
internal val EInkColorScheme: ColorScheme =
    lightColorScheme(
        primary = Ink,
        onPrimary = Paper,
        primaryContainer = Panel,
        onPrimaryContainer = Ink,
        inversePrimary = Paper,
        secondary = Ink,
        onSecondary = Paper,
        secondaryContainer = Panel,
        onSecondaryContainer = Ink,
        tertiary = Ink,
        onTertiary = Paper,
        tertiaryContainer = Panel,
        onTertiaryContainer = Ink,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Panel,
        onSurfaceVariant = Muted,
        surfaceTint = Ink,
        inverseSurface = Ink,
        inverseOnSurface = Paper,
        error = Ink,
        onError = Paper,
        errorContainer = Panel,
        onErrorContainer = Ink,
        outline = Ink,
        outlineVariant = Rule,
        scrim = Ink,
        surfaceBright = Paper,
        surfaceDim = Panel,
        surfaceContainer = Panel,
        surfaceContainerHigh = Panel,
        surfaceContainerHighest = Panel,
        surfaceContainerLow = Panel,
        surfaceContainerLowest = Paper,
        primaryFixed = Panel,
        primaryFixedDim = Panel,
        onPrimaryFixed = Ink,
        onPrimaryFixedVariant = Muted,
        secondaryFixed = Panel,
        secondaryFixedDim = Panel,
        onSecondaryFixed = Ink,
        onSecondaryFixedVariant = Muted,
        tertiaryFixed = Panel,
        tertiaryFixedDim = Panel,
        onTertiaryFixed = Ink,
        onTertiaryFixedVariant = Muted,
    )

/**
 * 墨水屏模式's answer for the two pairs Material has no role for.
 *
 * Both containers are [Panel] and both inks are [Ink]: the tonal separation that tells 曝光 from a
 * passing benchmark check on a colour screen is gone, and what is left is the label. The louder
 * alternative — an [Ink] fill under [Paper] text, a solid black block — was not taken: a large
 * inverted area is the single worst thing to put on an e-ink panel, both to draw and to clear.
 */
internal val EInkExtraColors =
    PlazaExtraColors(
        warningContainer = Panel,
        onWarningContainer = Ink,
        successContainer = Panel,
        onSuccessContainer = Ink,
        success = Ink,
        warning = Ink,
    )
