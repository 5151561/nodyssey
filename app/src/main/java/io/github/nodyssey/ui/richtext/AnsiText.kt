package io.github.nodyssey.ui.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.nodyssey.model.AnsiSpan

/**
 * Paints the colour runs [io.github.nodyssey.core.html.AnsiParser] recovered back onto the text.
 *
 * The report cards read the colours for their *meaning* — a red 高风险 becomes the theme's error
 * colour — but a terminal block is not a card: it is the output as posted, and the only honest way to
 * draw it is with the palette a terminal would have used. That is why this always renders on
 * [ReportTerminalGround] rather than on the app's surface, in both themes. ANSI has no way to say
 * "and my background is light", so a script that writes white on green assumes a dark ground; put the
 * same escape on a light surface and the white disappears.
 */
@Composable
internal fun rememberTerminalText(
    text: String,
    spans: List<AnsiSpan>,
): AnnotatedString = remember(text, spans) {
    if (spans.isEmpty()) {
        AnnotatedString(text)
    } else {
        buildAnnotatedString {
            append(text)
            spans.forEach { span ->
                val fill = BACKGROUND.getOrElse(span.bg ?: -1) { Color.Unspecified }
                addStyle(
                    SpanStyle(
                        color = inkFor(span.fg, fill),
                        background = fill,
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        textDecoration = if (span.underline) TextDecoration.Underline else null,
                    ),
                    start = span.start,
                    end = span.end,
                )
            }
        }
    }
}

/**
 * The colour a run is drawn in, decided against the fill it sits on rather than looked up on its own.
 *
 * [FOREGROUND] and [BACKGROUND] each solve for the case they were written for, and the two answers
 * contradict each other where the scripts put them together. `hardware.sh` heads its CPU, memory and
 * disk lines with `ESC[47m ESC[30m` — black on white, the inverse-video bar a terminal draws as the
 * loudest thing on the screen, and what the site shows as 白底黑字. Looked up separately that became
 * `#5C6370` on `#6C7380`: black lifted off a ground it was never going to sit on, white darkened for
 * an ink that was never going to be light. 1.27:1, which is not a bar, it is a smudge.
 *
 * So a light fill decides its own ink. It is only ever set to carry something dark — that is the
 * whole reason a script asks for one — and on it black means black again rather than the grey
 * [FOREGROUND] lifts it to. Dark fills keep their ink exactly as it was: they are read against the
 * ground, and that is the case [FOREGROUND] already answers.
 */
private fun inkFor(fg: Int?, fill: Color): Color {
    val onGround = FOREGROUND.getOrElse(fg ?: -1) { Color.Unspecified }
    if (!fill.isSpecified || fill.luminance() <= LIGHT_FILL) return onGround
    // Unset means the terminal's default ink, which is light, and would vanish here.
    if (fg == null || fg == BLACK || fg == BRIGHT_BLACK) return ChipInk
    return if (contrastOf(onGround, fill) >= MIN_INK_CONTRAST) onGround else ChipInk
}

/** WCAG's ratio, which is the only part of the standard that is a number rather than a judgement. */
private fun contrastOf(a: Color, b: Color): Float {
    val (hi, lo) = maxOf(a.luminance(), b.luminance()) to minOf(a.luminance(), b.luminance())
    return (hi + CONTRAST_OFFSET) / (lo + CONTRAST_OFFSET)
}

/** Ink for a light fill: the ground's own near-black, so a chip reads as ink on paper. */
private val ChipInk = Color(0xFF1B1F27)

private const val BLACK = 0
private const val BRIGHT_BLACK = 8
private const val LIGHT_FILL = 0.5f
private const val MIN_INK_CONTRAST = 4.5f
private const val CONTRAST_OFFSET = 0.05f

/**
 * The sixteen colours, as text on [ReportTerminalGround].
 *
 * One Dark's palette, which is where the ground itself came from. Index 0 is grey rather than black:
 * a terminal's own black is its background, so `ESC[30m` drawn literally would be invisible, and the
 * scripts do use it — as the ink inside a coloured badge, where it survives here as a legible dark
 * grey.
 */
private val FOREGROUND = listOf(
    Color(0xFF5C6370),
    Color(0xFFE06C75),
    Color(0xFF98C379),
    Color(0xFFE5C07B),
    Color(0xFF61AFEF),
    Color(0xFFC678DD),
    Color(0xFF56B6C2),
    Color(0xFFABB2BF),
    Color(0xFF6B7280),
    Color(0xFFFF8A94),
    Color(0xFFB5DF9A),
    Color(0xFFF0D08C),
    Color(0xFF83CBFF),
    Color(0xFFDA9BF0),
    Color(0xFF6FD3DF),
    Color(0xFFD7DAE0),
)

/**
 * The same sixteen as a fill, darkened — except the two whites, which are not.
 *
 * A terminal palette is one set of colours used for both roles, which works on a CRT and not here:
 * the scripts write their verdict chips as white on colour, and Compose antialiases 12sp CJK against
 * whatever is behind it. `#E06C75` under `#ABB2BF` is two mid tones with nothing between them. These
 * are the same hues taken far enough down that the report's own foregrounds stay readable on them.
 *
 * White is the exception because it is the one fill a script never asks for in order to put light ink
 * on it. `ESC[47m` is how a report shouts — the bar across a CPU line, the header of a table — and
 * darkening it turns the shout into the same grey as everything else. It stays paper, and [inkFor]
 * gives it ink to match.
 */
private val BACKGROUND = listOf(
    Color(0xFF21252B),
    Color(0xFF8C2F38),
    Color(0xFF3E6B33),
    Color(0xFF7A5C15),
    Color(0xFF2A5C8A),
    Color(0xFF6B3080),
    Color(0xFF2A6570),
    Color(0xFFD7DAE0),
    Color(0xFF3A3F4B),
    Color(0xFFA33B45),
    Color(0xFF4C7F3E),
    Color(0xFF8F6D1A),
    Color(0xFF336FA3),
    Color(0xFF7E3A96),
    Color(0xFF337885),
    Color(0xFFF1F3F6),
)
