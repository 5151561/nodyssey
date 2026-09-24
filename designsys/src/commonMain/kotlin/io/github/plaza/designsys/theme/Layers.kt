package io.github.plaza.designsys.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The three depths every screen is drawn at: the page, the cards on it, and what floats over both.
 *
 * This is the whole of the 轻盈层叠 direction. Rather than rows split by dividers on one flat surface,
 * the page recedes to a grey (`surfaceContainer`), content sits on the brightest surface there is
 * with a shadow barely strong enough to register, and floating controls — the FAB, a menu — sit one
 * step higher. Front-to-back order does the grouping.
 *
 * Material has no single role for "the page" or "a card on it", and which role plays each part is
 * not the same in every mode, which is why this is its own object rather than a set of call-site
 * choices:
 *
 * - **Light**: page `surfaceContainer`, card `surfaceContainerLowest` (white), soft shadows.
 * - **Dark**: the ladder inverts — lower tones are darker — so the page takes `surface` and a card
 *   is *lighter* than it (`surfaceContainer`). No shadows: a shadow on a near-black page is
 *   invisible and only costs a blur.
 * - **墨水屏**: everything is paper. A card is told apart by an outline, the one thing a panel can
 *   draw crisply; see [LocalEinkMode].
 *
 * [PlazaTheme] also writes [page] into `ColorScheme.background`, so every `Scaffold` — whose default
 * container is `background` — takes the page colour without forty call sites saying so.
 */
@Immutable
data class PlazaLayers(
    /** Behind everything: the `Scaffold`, the gaps between cards, a top bar that sits flush. */
    val page: Color,
    /** A card, a group of settings rows, a post — anything that is *content*. */
    val card: Color,
    /**
     * A control that sits on the page rather than in a card — the search pill, an unselected board
     * chip. It is the card colour in light and one step lighter in dark, where a control the same
     * tone as the cards around it would sink into them.
     */
    val raised: Color,
    /** Recessed *inside* a card: a code block, a stats tile, a quoted post. */
    val inset: Color,
    /** The inset hairline between rows of one card. */
    val divider: Color,
    /** Whether [cardShadow] and [floatShadow] draw anything. */
    val shadows: Boolean,
    /** An outline for cards, where shadows cannot be drawn and tone cannot separate them (墨水屏). */
    val cardBorder: Color?,
)

/**
 * [PlazaLayers.cardBorder] as the 1dp stroke a `Surface`, a chip or a `Card` takes — null wherever
 * there is no outline to draw, which is everywhere but 墨水屏.
 */
val PlazaLayers.cardBorderStroke: BorderStroke?
    get() = cardBorder?.let { BorderStroke(1.dp, it) }

/** [PlazaLayers.cardBorder] around a node that takes no border of its own; nothing where there is none. */
fun Modifier.cardBorder(
    layers: PlazaLayers,
    shape: Shape,
): Modifier = layers.cardBorder?.let { border(1.dp, it, shape) } ?: this

/**
 * Derives the layers from whichever scheme won — seed, character palette, system or 墨水屏 — so a
 * reader's colour choice reaches the page and cards the same way it reaches everything else.
 */
fun plazaLayers(
    scheme: ColorScheme,
    darkTheme: Boolean,
    einkMode: Boolean,
): PlazaLayers =
    when {
        einkMode ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surface,
                raised = scheme.surface,
                inset = scheme.surfaceContainer,
                divider = scheme.outlineVariant,
                shadows = false,
                cardBorder = scheme.outline,
            )

        darkTheme ->
            PlazaLayers(
                page = scheme.surface,
                card = scheme.surfaceContainer,
                raised = scheme.surfaceContainerHigh,
                // Two steps under the card rather than one: `surfaceContainerLow` sat so close to the
                // card's `surfaceContainer` that a code block or a stats tile vanished into it.
                inset = scheme.surfaceContainerLowest,
                divider = scheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
                shadows = false,
                cardBorder = null,
            )

        else ->
            PlazaLayers(
                page = scheme.surfaceContainer,
                card = scheme.surfaceContainerLowest,
                raised = scheme.surfaceContainerLowest,
                inset = scheme.surfaceContainer,
                divider = scheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
                shadows = true,
                cardBorder = null,
            )
    }

private const val DIVIDER_ALPHA = 0.6f

/**
 * Defaults to the light layers of a stock scheme, so a composable previewed or tested outside
 * [PlazaTheme] still draws something sensible instead of `Color.Unspecified`.
 */
val LocalPlazaLayers = staticCompositionLocalOf { plazaLayers(lightColorScheme(), darkTheme = false, einkMode = false) }

/**
 * The ink every shadow in the app is drawn with: the neutral hue of the page at a very low alpha,
 * rather than black. Black at the same strength reads as dirt on a tinted page.
 */
private val ShadowInk = Color(0xFF172830)

/**
 * A card's shadow — two layers, a tight contact shadow and a wide soft one, because a single blur
 * either looks like a smudge (wide) or like a border (tight).
 *
 * A plain function of [enabled] rather than a composable modifier: a caller reads
 * [PlazaLayers.shadows] once and passes it, which keeps this an ordinary chain of `dropShadow`s.
 */
fun Modifier.cardShadow(
    shape: Shape,
    enabled: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .dropShadow(shape, Shadow(radius = 2.dp, color = ShadowInk, offset = DpOffset(0.dp, 1.dp), alpha = 0.05f))
            .dropShadow(shape, Shadow(radius = 18.dp, color = ShadowInk, offset = DpOffset(0.dp, 6.dp), alpha = 0.06f))
    }

/** One step above [cardShadow]: a FAB, a floating toolbar, a popup menu. */
fun Modifier.floatShadow(
    shape: Shape,
    enabled: Boolean,
): Modifier =
    if (!enabled) {
        this
    } else {
        this
            .dropShadow(shape, Shadow(radius = 4.dp, color = ShadowInk, offset = DpOffset(0.dp, 2.dp), alpha = 0.12f))
            .dropShadow(shape, Shadow(radius = 24.dp, color = ShadowInk, offset = DpOffset(0.dp, 10.dp), alpha = 0.16f))
    }
