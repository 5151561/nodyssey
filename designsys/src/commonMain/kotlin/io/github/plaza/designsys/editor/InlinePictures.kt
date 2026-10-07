package io.github.plaza.designsys.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import kotlin.math.min
import kotlin.math.roundToInt

/** A run of an editor's source that it draws as a picture instead — `![name](url)`, `:ac01:`. */
@Immutable
data class InlinePicture(
    val start: Int,
    val end: Int,
    val url: String,
)

/**
 * Which runs of the text an editor shows as pictures, and how a picture is drawn.
 *
 * The text underneath does not change: what is sent, saved as a draft and handed to the IME is still
 * the Markdown. Only the field's display swaps each run for one placeholder character the size of a
 * sticker, and a picture is laid over it — so the caret steps over a sticker in one move and a
 * backspace takes the whole of it.
 *
 * [find] is the host's because which images are stickers is the host's knowledge: a photo uploaded
 * into the body is the same `![](url)` and should stay a link. It must return runs in order and
 * without overlap, and it runs on every edit, so it should be a scan rather than a parse.
 */
@Stable
class InlinePictures(
    val find: (CharSequence) -> List<InlinePicture>,
    val image: @Composable (url: String, modifier: Modifier) -> Unit,
)

/**
 * The display half: each run becomes [PLACEHOLDER], transparent and enlarged to a sticker's width.
 *
 * Tried first: `BasicTextField` with a `TextFieldState` takes no `inlineContent`, which is the API a
 * `Text` uses for exactly this, so the picture cannot be part of the layout. A placeholder glyph is
 * what reserves the room instead, and [InlinePictureLayer] draws over it. Remove this when the
 * state-based text field accepts inline content.
 */
internal class InlinePictureOutput(
    private val find: (CharSequence) -> List<InlinePicture>,
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        // Back to front, so each replacement leaves the offsets of the ones still to come alone.
        for (picture in find(asCharSequence()).asReversed()) {
            replace(picture.start, picture.end, PLACEHOLDER)
            addStyle(PlaceholderStyle, picture.start, picture.start + PLACEHOLDER.length)
        }
    }
}

/** Where each picture's placeholder sits in the displayed text, which is shorter than the source. */
internal fun displayedOffsets(pictures: List<InlinePicture>): List<Int> {
    var removed = 0
    return pictures.map { picture ->
        val at = picture.start - removed
        removed += picture.end - picture.start - PLACEHOLDER.length
        at
    }
}

/**
 * The pictures, drawn over the placeholders [InlinePictureOutput] left in the field.
 *
 * Sized to the parent — the inner field — and positioned from the field's own layout, less what the
 * field has scrolled: the layout is of the whole text, and the field shows a window onto it.
 */
@Composable
internal fun BoxScope.InlinePictureLayer(
    state: TextFieldState,
    pictures: InlinePictures,
    scrollState: ScrollState,
    layout: () -> TextLayoutResult?,
) {
    val text = state.text
    val found = remember(text, pictures) { pictures.find(text) }
    if (found.isEmpty()) return
    val result = layout() ?: return
    val offsets = remember(found) { displayedOffsets(found) }
    val density = LocalDensity.current
    Box(Modifier.matchParentSize().clipToBounds()) {
        found.forEachIndexed { index, picture ->
            val at = offsets[index]
            // A layout one edit behind the text: draw nothing this frame rather than guess.
            if (at >= result.layoutInput.text.length) return@forEachIndexed
            val box = result.getBoundingBox(at)
            val side = min(box.width, box.height)
            val left = box.left + (box.width - side) / 2
            val top = box.top + (box.height - side) / 2
            key(index, picture.url) {
                pictures.image(
                    picture.url,
                    Modifier
                        .offset { IntOffset(left.roundToInt(), (top - scrollState.value).roundToInt()) }
                        .size(with(density) { side.toDp() }),
                )
            }
        }
    }
}

/**
 * One ideograph rather than a space: a space at the end of a line hangs past the margin instead of
 * wrapping, which would put the sticker outside the field. Never seen — [PlaceholderStyle] is clear.
 */
private const val PLACEHOLDER = "\u53E3"

/** 1.6em of a 15sp body is 24sp, a hair under its 25sp line, so a sticker never pushes a line apart. */
private val PlaceholderStyle = SpanStyle(color = Color.Transparent, fontSize = 1.6.em)

/** Deletes [pictures]' run ending at the caret whole, as the field's own backspace does. */
internal fun TextFieldBuffer.deletePictureBeforeCaret(pictures: InlinePictures?): Boolean {
    if (pictures == null || selection.min != selection.max) return false
    val caret = selection.min
    val picture = pictures.find(asCharSequence()).lastOrNull { it.end == caret } ?: return false
    replace(picture.start, picture.end, "")
    return true
}
