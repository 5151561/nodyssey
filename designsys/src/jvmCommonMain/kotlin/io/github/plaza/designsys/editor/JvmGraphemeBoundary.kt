package io.github.plaza.designsys.editor

import java.text.BreakIterator

/**
 * `java.text.BreakIterator`, which both the JVM targets have.
 *
 * In `jvmCommonMain` rather than in each of them: the segmentation Android performs here is the
 * platform's JVM half, not its Android half, and desktop gives the same answer.
 */
internal actual fun previousGraphemeBoundary(text: String, index: Int): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    return iterator.preceding(index).let { if (it == BreakIterator.DONE) 0 else it }
}
