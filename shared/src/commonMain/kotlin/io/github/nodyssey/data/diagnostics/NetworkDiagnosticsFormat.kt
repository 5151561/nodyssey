package io.github.nodyssey.data.diagnostics

/**
 * The numbers on 网络自检, rendered.
 *
 * Here rather than in the screen because they are the part worth testing: the screen's job is
 * labels and layout, and neither of those can be wrong in a way that misleads a diagnosis. A size
 * that reads 0.0 KB, or a rate that rounds 5 KB/s to 0, is a wrong answer wearing the right shape.
 *
 * `commonMain` has no `String.format` and no `NumberFormat`, which is the reason for the integer
 * arithmetic below rather than a preference for it. One decimal place throughout: this is a
 * screenshot pasted into a thread, and the difference between 5.4 and 5.44 KB/s has never mattered
 * to anyone reading one.
 */
private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * 1024L
private const val TENTHS = 10L

/** One whole unit, counted in the tenths [tenths] returns. */
private const val TENTHS_PER_UNIT = BYTES_PER_KB * TENTHS

/** Bytes with a unit — "834 B", "68.2 KB", "1.4 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_KB) return "$bytes B"
    // The unit is chosen from the *rounded* reading, not from the raw byte count. Comparing the raw
    // count against a megabyte would send 1 048 500 bytes down the kilobyte branch, where one decimal
    // place renders it "1024.0 KB" — a number no reader parses as "about a megabyte", and squarely
    // inside the range this screen reports: a healthy connection here is a megabyte a second.
    val kilobytes = tenths(bytes, BYTES_PER_KB)
    return if (kilobytes < TENTHS_PER_UNIT) {
        "${decimal(kilobytes)} KB"
    } else {
        "${decimal(tenths(bytes, BYTES_PER_MB))} MB"
    }
}

/** Throughput with a unit — the same scale as [formatBytes], per second. */
fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

/**
 * A duration in the unit that keeps it readable — "412 ms", "12.4 s".
 *
 * The switch is at ten seconds rather than at one: a page read is hundreds of milliseconds and a
 * slow one is thousands, and printing both as seconds would collapse the range this screen is for
 * into 0.4 and 3.2.
 */
fun formatMillis(millis: Long): String =
    if (millis < MILLIS_READABLE_AS_MILLIS) "$millis ms" else "${decimal(tenths(millis, MILLIS_PER_SECOND))} s"

private const val MILLIS_READABLE_AS_MILLIS = 10_000L
private const val MILLIS_PER_SECOND = 1000L

/**
 * [value] over [unit], in tenths, rounded half-up.
 *
 * Half-up rather than truncating: truncation turns 1.58 KB into "1.5 KB" every time, which reads as
 * a measurement rather than as the systematic under-count it is.
 *
 * Tenths rather than a formatted string, so the caller can pick a unit by looking at the rounded
 * value — see [formatBytes], where that is the difference between "1.0 MB" and "1024.0 KB".
 */
private fun tenths(
    value: Long,
    unit: Long,
): Long = (value * TENTHS + unit / 2) / unit

private fun decimal(tenths: Long): String = "${tenths / TENTHS}.${tenths % TENTHS}"
