package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

/**
 * `NSByteCountFormatter`, which is the same kind of answer `Formatter.formatShortFileSize` is on
 * Android: the platform's own wording, in the reader's own locale.
 *
 * `CountStyleFile` rather than `Memory` — it counts in powers of ten and labels them MB and GB, which
 * is what the Android side has been showing since API 26. The two platforms therefore agree on the
 * number as well as on who decides it.
 */
@Composable
actual fun rememberFileSizeLabel(bytes: Long): String =
    remember(bytes) {
        NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)
    }

/**
 * `NSNumberFormatter` in its decimal style, which groups by the reader's locale.
 *
 * The formatter is remembered separately from the string: building one is not free, and 关于 asks this
 * for three statistics on the same screen.
 */
@Composable
actual fun rememberGroupedNumber(value: Long): String {
    val formatter =
        remember { NSNumberFormatter().apply { numberStyle = NSNumberFormatterDecimalStyle } }
    return remember(formatter, value) {
        formatter.stringFromNumber(platform.Foundation.NSNumber(long = value)).orEmpty()
    }
}
