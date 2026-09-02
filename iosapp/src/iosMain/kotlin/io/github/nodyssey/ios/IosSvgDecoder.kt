package io.github.nodyssey.ios

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.pxOrElse
import coil3.svg.Svg
import coil3.svg.SvgDecoder
import coil3.svg.isSvg
import io.github.plaza.core.image.SvgMarkup
import io.github.plaza.core.image.SvgSize
import io.github.plaza.core.image.atMost
import io.github.plaza.core.image.fitInside
import okio.Buffer
import okio.FileSystem
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.math.roundToInt

/**
 * The SVG decoder this app installs, which is two renderers behind one door.
 *
 * Coil's own [SvgDecoder] draws through Skia here, and Skia's SVG renderer leaves out `<text>` and
 * `<image>` — see [SvgMarkup], which has the measurement and the reason. That is invisible for the
 * cartoon avatars the site generates, which are shapes, and ruinous for everything else: an IP card
 * in a readme, a shields.io badge, a 测评 report. So a document that asks for either is handed to
 * [WebKitSvgRasterizer] and every other one keeps the cheap path.
 *
 * The routing is by content rather than by URL because nothing in a URL says which kind of document
 * is behind it — the site serves both from `/avatar/<uid>.png`, extension and all.
 *
 * Every step that could fail falls back to Skia rather than to an error: no readable size, no window
 * to render in, a snapshot the system refused. A card drawn without its text is a poor image; the
 * app's 「图片加载失败」in its place is a worse one, and it is what this platform drew before.
 */
@OptIn(ExperimentalCoilApi::class)
internal class IosSvgDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val rasterizer: WebKitSvgRasterizer,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        // Read once, here: the routing question is about the bytes, and an [ImageSource] answers
        // `source()` once. Both renderers are given the same array back.
        val bytes = source.source().use { it.readByteArray() }
        val markup = bytes.decodeToString()
        if (!SvgMarkup.needsFullRenderer(markup)) return skia(bytes)

        val intrinsic = SvgMarkup.intrinsicSize(markup) ?: return skia(bytes)
        val size = pixelSize(intrinsic) ?: return skia(bytes)
        val png = rasterizer.rasterize(bytes, intrinsic, size.first, size.second) ?: return skia(bytes)
        return DecodeResult(
            image = png.toImage() ?: return skia(bytes),
            // The bitmap is one rendering of a document that can always be drawn again larger, which
            // is what this flag tells the memory cache — the same answer `SvgDecoder` gives.
            isSampled = true,
        )
    }

    /** Coil's decoder, given the bytes this one has already read. */
    private suspend fun skia(bytes: ByteArray): DecodeResult? =
        SvgDecoder(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM),
            options = options,
            // Named, and the default: it is what picks the primary constructor out of the two this
            // class has, the other being a deprecated shape whose defaults would match this call too.
            parser = Svg.Parser.DEFAULT,
        ).decode()

    /**
     * The size to render at: what the request asked for, held to the document's own aspect ratio and
     * to the cache's ceiling.
     *
     * A request with no size of its own — the first pass the readme makes, which is asking how big
     * this image *is* — renders at the document's own size, which is what the Skia path returns for
     * the same request and what the screen measures its layout from.
     */
    private fun pixelSize(intrinsic: SvgSize): Pair<Int, Int>? {
        if (intrinsic.width <= 0f || intrinsic.height <= 0f) return null
        val size =
            intrinsic
                .fitInside(options.size.width.px(), options.size.height.px())
                .atMost(options.maxBitmapSize.width.px(), options.maxBitmapSize.height.px())
        return size.width.roundToInt().coerceAtLeast(1) to size.height.roundToInt().coerceAtLeast(1)
    }

    /** A dimension in pixels, or null for the one Coil writes as "no constraint". */
    private fun Dimension.px(): Int? = pxOrElse { 0 }.takeIf { it > 0 }

    /** PNG bytes as an image the rest of Coil can hold, or null for bytes it could not read. */
    private fun ByteArray.toImage(): coil3.Image? {
        val decoded = runCatching { Image.makeFromEncoded(this) }.getOrNull() ?: return null
        return try {
            Bitmap.makeFromImage(decoded).apply { setImmutable() }.asImage()
        } finally {
            decoded.close()
        }
    }

    /**
     * Installed in place of `SvgDecoder.Factory`, and applicable to exactly what that one is: this
     * class *is* the SVG decoder now, and it decides per document which renderer draws it.
     */
    class Factory(
        private val rasterizer: WebKitSvgRasterizer = WebKitSvgRasterizer(),
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            // The same two tests `SvgDecoder.Factory` makes, in the same order: what the server said,
            // then what the first bytes look like. `isSvg` peeks rather than reads — the source has to
            // reach the decoder untouched.
            val isSvg = result.mimeType == MIME_TYPE_SVG || DecodeUtils.isSvg(result.source.source())
            if (!isSvg) return null
            return IosSvgDecoder(result.source, options, rasterizer)
        }

        private companion object {
            /** `coil-svg` keeps its copy of this internal, and it is the one thing needed from it. */
            const val MIME_TYPE_SVG = "image/svg+xml"
        }
    }
}
