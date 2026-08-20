@file:OptIn(ExperimentalForeignApi::class)

package io.github.plaza.designsys.editor

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

/**
 * Foundation's composed character sequence, which is the nearest thing iOS has to a grapheme break.
 *
 * Indices need no translation: `NSString` counts UTF-16 code units and so does Kotlin's `String`, so
 * the number this returns addresses the same position the caller passed in.
 *
 * **It is not the same segmentation `BreakIterator` performs, and the difference is worth naming.**
 * A composed character sequence is a base plus its combining marks — which covers every case the
 * emoji panel can produce, its variation-selector ❤️ included, and the panel contains no
 * zero-width-joiner sequence at all. What it does not cover is a ZWJ sequence the reader *pasted*:
 * `👨‍👩‍👧` is one grapheme to `BreakIterator` and three composed sequences to Foundation, so
 * backspace on iOS would take it apart a person at a time. Foundation has no grapheme-cluster API to
 * reach for instead — Swift does that segmentation in its standard library, not in `NSString`.
 */
internal actual fun previousGraphemeBoundary(text: String, index: Int): Int {
    if (index <= 0) return 0
    val range =
        (text as NSString).rangeOfComposedCharacterSequenceAtIndex((index - 1).toULong())
    return range.useContents { location.toInt() }
}
