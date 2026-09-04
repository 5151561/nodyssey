package io.github.nodyssey.image

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import coil3.request.maxBitmapSize
import coil3.size.pxOrElse
import org.aomedia.avif.android.AvifDecoder
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * AVIF for the phones whose own decoder cannot read it.
 *
 * Android has decoded AVIF since 12 (API 31) — on paper. What the platform actually offers is
 * whatever AV1 decoder the device ships, and a run of Huawei models ship none: the format is in the
 * OS's list of supported types, `ImageDecoder` accepts the file, and then the decode fails. The user
 * sees 图片加载失败 on every AVIF attachment in a thread and on nothing else, which is not a diagnosis
 * anybody arrives at by looking at their own phone. This decoder is the answer to those devices:
 * libavif with its own AV1 decoder compiled in, doing in software what the hardware will not do.
 *
 * It is a fallback and stays one. Coil's own decoder is better at everything it can do — hardware
 * bitmaps, EXIF orientation, the sampling it shares with every other format — so the rule is that
 * the platform gets the first attempt and this only ever runs after the platform has been *seen* to
 * fail. [PlatformAvifSupport] is where that observation is kept:
 *
 *  * Below API 31 there is nothing to try, and every AVIF comes here directly.
 *  * On API 31 and above the first AVIF of the process is decoded through the platform from inside
 *    [decode]. Success is remembered, [Factory] declines every AVIF from then on and Coil's own
 *    decoder has the format back. A failure that libavif then decodes is remembered too, and the
 *    rest of that process's AVIFs skip the attempt and come straight here.
 *
 * So a working phone pays one ordinary decode through a slightly plainer path than Coil's own, once
 * per launch, and a Huawei pays one failed attempt before the pictures start appearing. What this
 * shape does not catch is a device that decodes some AVIFs and not others — a 10-bit or 4:4:4 file
 * on a phone whose decoder only does 8-bit 4:2:0. Coil reports that one as a failed image, the same
 * as it does today; covering it would mean taking the format away from Coil's decoder for good,
 * which is a worse trade for every device that works.
 *
 * Animation is not handled: an `avis` sequence decodes to its first frame. The site has no animated
 * AVIF in it, and a still frame beats a broken image either way.
 */
class AvifImageDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val platform: PlatformAvifSupport = PlatformAvifSupport.shared,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        // Read whole rather than streamed: libavif is handed a buffer, and holding the same bytes is
        // what lets the platform have its attempt first without the source being spent on it. The
        // encoded file is a fraction of the bitmap it becomes — a 12MB ARGB_8888 photo arrives as a
        // few hundred KB — so the copy costs little against what decoding it costs anyway.
        val encoded = source.source().use { it.readByteArray() }

        val platformGetsATurn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && platform.isUntested
        if (platformGetsATurn) {
            val decoded = decodeWithPlatform(encoded)
            if (decoded != null) {
                platform.record(decoded = true)
                return decoded
            }
        }
        val decoded = decodeWithLibavif(encoded)
        // Recorded here rather than beside the failed attempt: a file both decoders choke on is a
        // broken file, and answering "this device cannot decode AVIF" on the strength of one of
        // those would send every image for the rest of the launch down the slow path. Only a decode
        // libavif then completed says the bytes were fine and the platform was not.
        if (platformGetsATurn) platform.record(decoded = false)
        return decoded
    }

    /**
     * One attempt through the platform, shaped like the decoder Coil would have used.
     *
     * `ImageDecoder` rather than `BitmapFactory` because that is what Coil's own `StaticImageDecoder`
     * runs on API 28 and above: probing through the other one could answer for a code path the app
     * never takes. A software allocation because the result is handed back as an ordinary bitmap,
     * and null on any failure — which is the whole point of the call.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun decodeWithPlatform(encoded: ByteArray): DecodeResult? =
        try {
            var sampled = false
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val multiplier = sizeMultiplier(info.size.width, info.size.height)
                    if (multiplier < 1.0) {
                        sampled = true
                        decoder.setTargetSize(
                            scaled(info.size.width, multiplier),
                            scaled(info.size.height, multiplier),
                        )
                    }
                }
            DecodeResult(image = bitmap.asImage(), isSampled = sampled)
        } catch (_: Exception) {
            null
        }

    /**
     * libavif, which carries its own AV1 decoder and so needs nothing from the device.
     *
     * The bitmap is allocated at the size the request asked for and libavif scales into it, so a
     * thumbnail of a 4000px photo never materialises at 4000px. The buffer has to be direct: the JNI
     * reads it through `GetDirectBufferAddress`, which answers null for anything else.
     */
    private fun decodeWithLibavif(encoded: ByteArray): DecodeResult {
        val buffer = ByteBuffer.allocateDirect(encoded.size)
        buffer.put(encoded)
        buffer.rewind()

        val info = AvifDecoder.Info()
        val parsed = withNativeLibrary { AvifDecoder.getInfo(buffer, buffer.remaining(), info) }
        check(parsed && info.width > 0 && info.height > 0) { "libavif could not read the AVIF header." }

        val multiplier = sizeMultiplier(info.width, info.height)
        val bitmap =
            createBitmap(
                width = scaled(info.width, multiplier),
                height = scaled(info.height, multiplier),
                config = config(alphaPresent = info.alphaPresent),
            )
        val decoded =
            try {
                withNativeLibrary { AvifDecoder.decode(buffer, buffer.remaining(), bitmap) }
            } catch (throwable: Throwable) {
                bitmap.recycle()
                throw throwable
            }
        if (!decoded) {
            bitmap.recycle()
            error("libavif could not decode this AVIF image.")
        }
        return DecodeResult(image = bitmap.asImage(), isSampled = multiplier < 1.0)
    }

    /**
     * How much smaller than its own pixels this image is being asked to be, never larger than 1:
     * upscaling here would allocate a bitmap bigger than the file for no more detail in it.
     *
     * `maxBitmapSize` is Coil's own ceiling and matters more here than anywhere else: a full-size
     * decode is the request a phone with no hardware decoder can least afford, and an attachment
     * nobody checked the dimensions of is exactly what a forum is full of.
     */
    private fun sizeMultiplier(width: Int, height: Int): Double =
        DecodeUtils
            .computeSizeMultiplier(
                width,
                height,
                options.size.width.pxOrElse { width },
                options.size.height.pxOrElse { height },
                options.scale,
                options.maxBitmapSize,
            ).coerceAtMost(1.0)

    private fun scaled(dimension: Int, multiplier: Double): Int = (dimension * multiplier).roundToInt().coerceAtLeast(1)

    /**
     * `RGB_565` only where the request asked for it and the image has nothing to be transparent
     * about; everything else, `HARDWARE` included, decodes to `ARGB_8888` — libavif writes into the
     * bitmap's own pixels, so it has to be one that is allocated in this process and mutable.
     */
    private fun config(alphaPresent: Boolean): Bitmap.Config =
        if (!alphaPresent && options.bitmapConfig == Bitmap.Config.RGB_565) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }

    /**
     * `AvifDecoder` swallows the `UnsatisfiedLinkError` from its own `System.loadLibrary` and lets
     * the first native call raise it instead. An `Error` thrown out of here would take the process
     * with it rather than failing one image, so it becomes an exception Coil can report.
     */
    private inline fun <T> withNativeLibrary(block: () -> T): T =
        try {
            block()
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException("libavif's native library is missing on this device.", error)
        }

    /**
     * Claims an AVIF only while the platform has not been seen decoding one.
     *
     * The order of the two checks is deliberate: on a device that decodes AVIF this asks a boolean
     * and returns, rather than reading the head of every image that reaches it.
     */
    class Factory(
        private val platform: PlatformAvifSupport = PlatformAvifSupport.shared,
    ) : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            if (platform.decodesAvif) return null
            if (!DecodeUtils.isAvif(result.source.source())) return null
            return AvifImageDecoder(result.source, options, platform)
        }
    }
}

/**
 * What this device's own decoder has been observed to do with AVIF, for the life of the process.
 *
 * Observed rather than declared: `SDK_INT >= 31` is what the platform *claims*, and the phones this
 * whole file exists for are the ones where the claim is false. So the answer starts as "not yet
 * asked" on those versions and is settled by the first AVIF the app is asked to draw — see
 * [AvifImageDecoder]. Below 31 there is no claim to test and the answer is known from the start.
 *
 * One instance per process ([shared]), read and written from Coil's decode threads, which is what
 * `@Volatile` is for. A race decides nothing worse than two images each taking the platform's turn.
 */
class PlatformAvifSupport(sdkInt: Int = Build.VERSION.SDK_INT) {
    @Volatile
    private var state: State = if (sdkInt >= Build.VERSION_CODES.S) State.UNTESTED else State.MISSING

    /** True once the platform has decoded an AVIF here, which is when this app should stand aside. */
    val decodesAvif: Boolean get() = state == State.PRESENT

    /** True while nothing is known — the state in which the platform is given its attempt. */
    val isUntested: Boolean get() = state == State.UNTESTED

    fun record(decoded: Boolean) {
        state = if (decoded) State.PRESENT else State.MISSING
    }

    private enum class State { UNTESTED, PRESENT, MISSING }

    companion object {
        val shared = PlatformAvifSupport()
    }
}
