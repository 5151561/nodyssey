package io.github.plaza.designsys.richtext

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 表情统一缩限 — how big an inline sticker is allowed to be in a post body.
 *
 * Two modes, because the site itself has only one and it is not the one this app shipped with:
 *
 * - [uniform] on: every sticker is drawn in the same square box, [uniformSize] on a side, and its
 *   pixels are fitted into it. Predictable, and at the bottom of the range it is the 20sp box the
 *   body copy was designed around — a sticker that never breaks the line rhythm.
 * - [uniform] off: each sticker is drawn at its own natural size, which is what the web does
 *   (`img.sticker { max-width: 90px }`, natural size otherwise). Sizes vary from sticker to sticker,
 *   and a line that holds one is as tall as it needs to be.
 *
 * A `CompositionLocal` rather than a parameter on [RichContent], on the same reasoning as
 * `LocalReportFormat`: post markup is drawn in six places — a thread, a signature, a message, a
 * space readme, an editor preview — and only one of them has a ViewModel that knows about settings.
 * The default is the app's own default, so a missing provider renders today's behaviour rather than
 * something broken.
 */
@Immutable
data class StickerSizing(
    val uniform: Boolean = true,
    val uniformSize: TextUnit = DEFAULT_STICKER_SIZE,
)

val LocalStickerSizing = staticCompositionLocalOf { StickerSizing() }

/** The box a sticker has always had: 20sp square, centred on the text, never taller than the line. */
val DEFAULT_STICKER_SIZE = 20.sp

/** The slider's ends. The top is [NATURAL_STICKER_MAX_WIDTH] in sp, so the two modes meet there. */
val MIN_STICKER_SIZE = 20.sp
val MAX_STICKER_SIZE = 90.sp

/**
 * The web's `img.sticker { max-width: 90px }`, in dp.
 *
 * A CSS pixel is a dp, so a sticker that the site draws at its natural width is drawn here at the
 * same width, and one wider than 90px is scaled down by the same rule the site uses.
 */
val NATURAL_STICKER_MAX_WIDTH = 90.dp

/**
 * The natural pixel size of every sticker seen so far this process.
 *
 * An inline placeholder has to declare its size *before* its content composes, so natural-size mode
 * cannot ask the image how big it is — it has to already know. This is where the answer is kept:
 * [RichContent] records a sticker's dimensions the first time it decodes, and every later occurrence
 * — the next floor, the next thread — is laid out at the right size from the start.
 *
 * The cost is one relayout the first time a given sticker is seen in a process: it appears in the
 * [DEFAULT_STICKER_SIZE] fallback box and grows to its own size when its pixels arrive. Coil's disk
 * cache survives restarts but this map does not, so that one reflow comes back after a cold start.
 * Persisting it would mean a store that has to be invalidated whenever the site redraws a sticker,
 * for a jump that happens once per launch and only in the mode that asks for varying sizes.
 *
 * Snapshot-backed, so recording a size recomposes whatever is showing that sticker. Process-wide
 * rather than remembered per screen, on purpose: the same 200-odd stickers recur across every thread
 * the user opens, and a per-screen cache would re-learn all of them on every navigation.
 */
object StickerSizeCache {
    private val sizes = mutableStateMapOf<String, IntSize>()

    /** The sticker's natural size in pixels, or null if nothing has decoded it yet. */
    fun naturalSize(url: String): IntSize? = sizes[url]

    fun record(
        url: String,
        width: Int,
        height: Int,
    ) {
        // A zero dimension is a decoder that gave up, not a sticker; storing it would pin the
        // placeholder to an empty box that nothing can ever correct.
        if (width <= 0 || height <= 0) return
        val size = IntSize(width, height)
        if (sizes[url] != size) sizes[url] = size
    }

    /** For tests, which share one process and must not inherit each other's measurements. */
    fun clear() = sizes.clear()
}

/**
 * The box to reserve for a sticker, given what is known about it.
 *
 * [naturalPx] is null until the sticker has decoded once. In uniform mode it is not consulted at
 * all — the box is square whatever the pixels turn out to be — which is what keeps that mode free of
 * the first-sight reflow.
 */
fun StickerSizing.boxSize(
    naturalPx: IntSize?,
    density: Density,
): DpSize =
    with(density) {
        if (uniform || naturalPx == null) {
            val side = if (uniform) uniformSize.toDp() else DEFAULT_STICKER_SIZE.toDp()
            DpSize(side, side)
        } else {
            naturalDpSize(naturalPx)
        }
    }

/**
 * Natural size in dp, scaled down to [NATURAL_STICKER_MAX_WIDTH] the way the site's CSS does.
 *
 * The decoded pixels become dp one for one, with no density division anywhere. That looks like a
 * missing conversion and is the whole point: the numbers coming in are the *file's* pixels, which
 * for a `<img>` with no width attribute are exactly the CSS pixels the site lays the sticker out
 * in — and the Android unit that means what a CSS pixel means is the dp, not the physical pixel.
 *
 * Dividing by density is what shipped first, and it made the mode useless on a real phone: a 90px
 * sticker came out 22.5dp on a 4x screen, under the 27sp line it sits in, so 关掉统一缩限 — the
 * setting whose entire job is 按表情原本的大小显示 — drew every sticker smaller than the 20sp box
 * it was supposed to escape.
 */
private fun naturalDpSize(naturalPx: IntSize): DpSize {
    val width: Dp = naturalPx.width.dp
    val height: Dp = naturalPx.height.dp
    return if (width <= NATURAL_STICKER_MAX_WIDTH) {
        DpSize(width, height)
    } else {
        // max-width shrinks the width and the height follows it; the site does not letterbox.
        val scale = NATURAL_STICKER_MAX_WIDTH / width
        DpSize(NATURAL_STICKER_MAX_WIDTH, height * scale)
    }
}
