package io.github.plaza.core.image

import android.graphics.Paint
import android.graphics.Typeface
import coil3.svg.Svg
import okio.Buffer
import okio.BufferedSource
import java.util.Locale

/**
 * Teaches Coil's SVG decoder the three things AndroidSVG never learned, by rewriting the document
 * before it reaches the parser.
 *
 * Coil renders SVG through AndroidSVG 1.4, which is an SVG 1.1 renderer. The gaps below make a whole
 * genre of NodeSeek attachment unviewable — every report from the `Check.Place` scripts, which is
 * what most of 测评 is made of. Those are terminal screenshots laid out on a character grid: `ch`
 * wide, `em` tall, one row per line.
 *
 *  * **The CSS `ch` unit does not exist.** `SVG.Unit` is `px|em|ex|in|cm|mm|pt|pc|percent`, and a
 *    length it cannot name is fatal, not ignored: the parser throws `SVGParseException: Invalid
 *    length unit specifier`, so a single `width="74ch"` on the root loses the entire image. `ch` is
 *    on every rect and every text run in these files, so all of them failed to load. See post-881800.
 *
 *  * **`dominant-baseline` is not implemented** — it is absent from the parser's attribute enum, so
 *    the declaration is dropped. These reports centre each row's text in its coloured cell with
 *    `dominant-baseline: central`; without it every line is drawn a third of a line high and the
 *    ANSI background bars sit under the wrong text.
 *
 *  * **A character grid is not a grid in Android's fonts.** The document says its widest line is N
 *    characters and sizes itself `Nch`, which holds in a terminal and in a browser. Android draws
 *    each glyph from whichever fallback font has it, and those disagree with `monospace`: at 14px
 *    where `0` advances 8px, Braille (`⣿`, the sparkline bars in 网络质量) and box drawing advance
 *    10px and `⋮` advances 3px. Lines built from them run past the right edge of the document and
 *    are clipped away. [rewriteForAndroidSvg] therefore sizes the root to the widest line as
 *    *drawn*, never narrower than declared. The grid stays slightly ragged — nothing short of one
 *    `<tspan>` per character could fix that, and AndroidSVG reads only the first `x` of a list — but
 *    nothing is lost off the edge.
 *
 * All three are fixed in the document rather than in the renderer, which keeps this to a string
 * rewrite over a file the renderer was going to read anyway. [rewriteForAndroidSvg] does the work
 * and is where the reasoning per rule lives; this class only measures the font it will be drawn in
 * and hands the result to the real parser.
 */
class CompatSvgParser(
    private val delegate: Svg.Parser = Svg.Parser.DEFAULT,
) : Svg.Parser {
    override fun parse(source: BufferedSource): Svg {
        val bytes = source.readByteString()
        val original = { delegate.parse(Buffer().write(bytes)) }
        // `readByteString` then `utf8()` rather than `readUtf8()` so that a document this rewrite
        // declines still reaches the parser as the bytes that arrived. An SVG announcing some other
        // encoding is one we decline: rewriting it means decoding it, and guessing an encoding to
        // fix a layout quirk would trade a fixable image for a mojibake one.
        val text = bytes.utf8()
        if (declaresNonUtf8Encoding(text)) return original()
        val rewritten = rewriteForAndroidSvg(text, PaintMetrics) ?: return original()
        return delegate.parse(Buffer().writeUtf8(rewritten))
    }

    /**
     * The font AndroidSVG will actually draw with, asked directly.
     *
     * Its `checkGenericFont` resolves only the generic families, so a stack like
     * `SimHei, Consolas, DejaVu Sans Mono, monospace` — what these reports ask for — falls through
     * every real name and lands on [Typeface.MONOSPACE]. Measuring rather than assuming also means
     * [widthOf] sees the same fallback fonts the renderer will reach for, which is the whole point:
     * a glyph `monospace` does not carry is exactly where the character grid stops being one.
     */
    private object PaintMetrics : SvgTextMetrics {
        private const val MEASURE_TEXT_SIZE = 100f

        private val paint by lazy(LazyThreadSafetyMode.NONE) {
            Paint().apply {
                typeface = Typeface.MONOSPACE
                textSize = MEASURE_TEXT_SIZE
            }
        }

        override val chWidthEm: Float get() = paint.measureText("0") / MEASURE_TEXT_SIZE

        override val centralBaselineDyEm: Float
            get() = paint.fontMetrics.let { -(it.ascent + it.descent) / 2f } / MEASURE_TEXT_SIZE

        /**
         * Measured at [MEASURE_TEXT_SIZE] and scaled down rather than measured at [fontSizePx]:
         * glyph advances are hinted to whole pixels at small sizes, and rounding every character up
         * or down would put the answer several characters out over an 80-column line.
         */
        override fun widthOf(text: String, fontSizePx: Float): Float =
            paint.measureText(text) / MEASURE_TEXT_SIZE * fontSizePx
    }

    private companion object {
        private val XML_ENCODING = Regex("""<\?xml[^>]*\bencoding\s*=\s*["']([^"']+)["']""")

        private const val XML_DECLARATION_SEARCH_LENGTH = 200

        fun declaresNonUtf8Encoding(text: String): Boolean =
            XML_ENCODING
                .find(text.take(XML_DECLARATION_SEARCH_LENGTH))
                ?.groupValues
                ?.get(1)
                ?.let { !it.equals("utf-8", ignoreCase = true) && !it.equals("utf8", ignoreCase = true) }
                ?: false
    }
}

/** What the rewrite needs to know about the font the document will be drawn in. */
internal interface SvgTextMetrics {
    /** The advance of `0`, in em — CSS's definition of `1ch`. */
    val chWidthEm: Float

    /** How far to drop a baseline to centre its line on a given y, in em. */
    val centralBaselineDyEm: Float

    /** The width [text] will occupy when drawn at [fontSizePx], in px. */
    fun widthOf(text: String, fontSizePx: Float): Float
}

/**
 * Rewrites [svg] into the subset of SVG that AndroidSVG understands, or returns null when it already
 * is — a null means "nothing here needs me", and the caller passes the original bytes through.
 */
internal fun rewriteForAndroidSvg(svg: String, metrics: SvgTextMetrics): String? {
    var out = svg

    // `ch` becomes `em` rather than `px` on purpose: em is the one font-relative unit AndroidSVG
    // resolves itself, so the rewrite stays correct whatever font-size the document's CSS turns out
    // to set, and it never has to guess one. Only inside tags — a `<tspan>` may legitimately contain
    // the text "5ch", and mangling what a report says is worse than not drawing it.
    out =
        TAG.replace(out) { tag ->
            CH_LENGTH.replace(tag.value) { length ->
                val value = length.groupValues[1].toFloatOrNull() ?: return@replace length.value
                "${(value * metrics.chWidthEm).svgNumber()}em"
            }
        }

    // The root's own size is the one place the rewrite has to commit to pixels: AndroidSVG reports
    // `getDocumentWidth() == -1` for any font-relative root size, and an SVG with no intrinsic size
    // and no viewBox leaves Coil nothing to scale to. Width is also where the grid has to be made
    // honest — see the class comment — so it is the wider of what the document declares and what its
    // longest line will actually draw as. Everything inside stays relative and stays put.
    ROOT_TAG.find(out)?.let { root ->
        val fontSize = CSS_FONT_SIZE.find(out)?.groupValues?.get(1)?.toFloatOrNull() ?: DEFAULT_FONT_SIZE_PX
        val drawnWidth = widestDrawnLine(out, metrics, fontSize)
        val absolute =
            ROOT_SIZE.replace(root.value) { size ->
                val declared = size.groupValues[2].toFloatOrNull()?.times(fontSize) ?: return@replace size.value
                val px = if (size.groupValues[1] == "width") maxOf(declared, drawnWidth) else declared
                """${size.groupValues[1]}="${px.svgNumber()}px""""
            }
        out = out.replaceRange(root.range, absolute)
    }

    // Emulating `dominant-baseline: central` with an explicit dy is only right for a document that
    // asked for it, so this is gated on the document's own stylesheet saying so. A single dy shifts
    // the whole run, tspans included: it moves the current text position, not one glyph.
    if (CENTRAL_BASELINE.containsMatchIn(out)) {
        out = TEXT_WITHOUT_DY.replace(out) { """<text dy="${metrics.centralBaselineDyEm.svgNumber()}em"""" }
    }

    return out.takeIf { it != svg }
}

/** How far right the longest line reaches once drawn, in px. Lengths here are already in em. */
private fun widestDrawnLine(svg: String, metrics: SvgTextMetrics, fontSizePx: Float): Float =
    TEXT_ELEMENT
        .findAll(svg)
        .maxOfOrNull { line ->
            val start = X_IN_EM.find(line.groupValues[1])?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            start * fontSizePx + metrics.widthOf(drawnText(line.groupValues[2]), fontSizePx)
        } ?: 0f

/**
 * The characters a `<text>` puts on screen: its `<tspan>` markup removed, its entities resolved.
 *
 * Numeric references matter as much as named ones — these reports separate the mail providers they
 * tested with `&#43;`, and measuring that as five characters rather than one made a 73-column line
 * come out 30 columns too wide, which would have padded the document with empty space.
 */
private fun drawnText(markup: String): String =
    ENTITY.replace(markup.replace(TAG, "")) { entity ->
        val body = entity.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: entity.value

            body.startsWith("#") -> body.drop(1).toIntOrNull()?.toChar()?.toString() ?: entity.value

            else -> NAMED_ENTITIES[body] ?: entity.value
        }
    }

private val ENTITY = Regex("""&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);""")

private val NAMED_ENTITIES =
    mapOf("lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "amp" to "&")

/**
 * A length the SVG grammar accepts and a reader can still recognise: `Float.toString` would put
 * `0.90000004` in the file. `Locale.ROOT` because a decimal comma is not a number here.
 */
private fun Float.svgNumber(): String =
    "%.4f".format(Locale.ROOT, this).trimEnd('0').trimEnd('.')

private val TAG = Regex("""<[a-zA-Z/][^>]*>""")
private val CH_LENGTH = Regex("""(-?[0-9]*\.?[0-9]+)ch(?=["';\s)])""")
private val ROOT_TAG = Regex("""<svg\b[^>]*>""")
private val ROOT_SIZE = Regex("""\b(width|height)="([0-9.]+)em"""")
private val CSS_FONT_SIZE = Regex("""font-size\s*:\s*([0-9.]+)px""")
private val CENTRAL_BASELINE = Regex("""dominant-baseline\s*:\s*(central|middle)""")
private val TEXT_WITHOUT_DY = Regex("""<text\b(?![^>]*\bdy=)""")
private val TEXT_ELEMENT = Regex("""<text\b([^>]*)>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
private val X_IN_EM = Regex("""\bx="(-?[0-9.]+)em"""")

/** CSS's initial `font-size`, used only when the document sets none of its own. */
private const val DEFAULT_FONT_SIZE_PX = 16f
