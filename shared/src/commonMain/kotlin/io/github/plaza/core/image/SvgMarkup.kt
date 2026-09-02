package io.github.plaza.core.image

/**
 * The two questions a caller has to answer about an SVG *before* deciding what will draw it.
 *
 * Both are answered by reading the markup rather than by parsing it into a document, because both
 * are asked in front of the parser: the answer decides which renderer the bytes are handed to.
 *
 * The renderer this exists for is Skia's. `coil-svg` draws SVG through `SkSVGDOM` on every platform
 * except Android, and skiko builds that DOM with neither a font manager nor a resource provider —
 * there is no binding that could pass either (`SVGDOM.cc` calls `SkSVGDOM::MakeFromStream` and
 * nothing else). A document's shapes come out right and its `<text>` and its `<image>` are dropped
 * without a word, which is how NodeSeek's IP cards and the shields.io badges in 测评 became empty
 * frames on iOS while every avatar kept working.
 */
object SvgMarkup {
    /**
     * True when the document asks for something Skia's SVG renderer will silently leave out.
     *
     * Three elements, and each is one of the two missing pieces: `<text>` and `<textPath>` need the
     * font manager, `<image>` needs the resource provider, and `<foreignObject>` is HTML, which that
     * renderer does not do at all.
     *
     * Deliberately generous — a match inside a comment or an attribute value costs a slower render of
     * a document that would have drawn correctly anyway, while a miss costs a blank one. The cheap
     * mistake is the one worth making.
     */
    fun needsFullRenderer(markup: String): Boolean =
        DRAWN_ELSEWHERE.any { markup.contains(it, ignoreCase = true) }

    /**
     * The document's own size in CSS pixels, from `viewBox` if it has one and from `width`/`height`
     * if it does not.
     *
     * `viewBox` first because it is the one that is always in pixels: a document is free to say
     * `width="100%"`, and a percentage of nothing is what an offscreen renderer has no answer for.
     * Null when neither is readable, which is a caller's cue to leave the document to whatever it
     * would have used otherwise rather than to invent a size for it.
     */
    fun intrinsicSize(markup: String): SvgSize? {
        val root = ROOT_TAG.find(markup)?.value ?: return null
        viewBox(root)?.let { return it }
        val width = length(attribute(root, "width")) ?: return null
        val height = length(attribute(root, "height")) ?: return null
        return SvgSize(width, height).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun viewBox(root: String): SvgSize? {
        val raw = attribute(root, "viewBox") ?: return null
        val numbers = raw.split(' ', ',', '\t', '\n').filter(String::isNotBlank).mapNotNull(String::toFloatOrNull)
        if (numbers.size != 4) return null
        return SvgSize(numbers[2], numbers[3]).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun attribute(root: String, name: String): String? =
        Regex("""\s$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(root)?.groupValues?.get(1)

    /**
     * A CSS length in pixels, or null for one written in a unit the answer cannot be given in.
     *
     * `px` is the only suffix accepted on purpose. A `%` is the case this rejects for the reason in
     * [intrinsicSize]; `em`, `pt` and the rest resolve against a context this has none of, and a
     * guess at one would come out as an image at the wrong size rather than as no answer.
     */
    private fun length(raw: String?): Float? =
        raw?.trim()?.removeSuffix("px")?.trim()?.toFloatOrNull()

    /** The opening `<svg …>` tag, which is the only one any of this reads. */
    private val ROOT_TAG = Regex("""<svg\b[^>]*>""", RegexOption.IGNORE_CASE)

    private val DRAWN_ELSEWHERE = listOf("<text", "<image", "<foreignObject")
}

/** A document's own size, in CSS pixels. */
data class SvgSize(
    val width: Float,
    val height: Float,
)

/**
 * This size scaled to fit inside a box, keeping its aspect ratio. A null side is unconstrained.
 *
 * Grows as well as shrinks, which is the point of a vector: a card asked for at twice its own size is
 * *drawn* at twice its own size rather than blown up from a smaller raster. Two boxes are applied one
 * after the other where a caller has both — what the layout asked for, then what the cache allows —
 * and the order does not matter, because each is a ceiling.
 */
fun SvgSize.fitInside(width: Int?, height: Int?): SvgSize {
    if (this.width <= 0f || this.height <= 0f) return this
    val byWidth = width?.let { it / this.width }
    val byHeight = height?.let { it / this.height }
    val scale = listOfNotNull(byWidth, byHeight).minOrNull() ?: return this
    return SvgSize(this.width * scale, this.height * scale)
}

/**
 * The same, except that it only ever shrinks.
 *
 * The difference is what the box *means*. A layout asking for a size is asking for the document to be
 * drawn at it, larger or smaller; a ceiling — the biggest bitmap a cache will hold — is not a request
 * for anything, and treating it as one is how a 720-pixel card came to be drawn at 4096 and cost 30 MB
 * to hold.
 */
fun SvgSize.atMost(width: Int?, height: Int?): SvgSize {
    val fitted = fitInside(width, height)
    return if (fitted.width < this.width) fitted else this
}
