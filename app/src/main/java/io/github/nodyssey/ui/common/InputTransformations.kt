package io.github.nodyssey.ui.common

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLength

/**
 * Accepts digits only, up to [maxLength] of them.
 *
 * Rejection happens inside the buffer rather than by filtering the string afterwards, and that is the
 * whole point — filtering in `onValueChange` and feeding the shortened string back leaves the caret at
 * the index it had in the *unfiltered* text. Measured on a field holding `1234` with the caret between
 * `12` and `34`: typing `9a` put the caret at 4 instead of 3, and typing `ab` — nothing accepted at
 * all — moved it from 2 to 4. Every rejected character slid the caret one place right, so the next
 * digit landed in the wrong position.
 *
 * A number keyboard does not prevent this. Paste bypasses the IME entirely, and a post id copied out
 * of a thread carries whitespace while a figure copied out of a table carries thousands separators.
 */
fun digitsOnly(maxLength: Int): InputTransformation =
    InputTransformation
        .maxLength(maxLength)
        .byValue { _, proposed -> proposed.filter(Char::isDigit) }
