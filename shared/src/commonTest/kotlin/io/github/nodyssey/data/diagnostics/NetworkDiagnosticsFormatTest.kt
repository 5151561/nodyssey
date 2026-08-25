package io.github.nodyssey.data.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The arithmetic behind 网络自检's numbers.
 *
 * Worth pinning down because every one of these is read as a measurement by somebody deciding
 * whether a connection is the problem. A rate computed over the wrong interval, or a size that
 * rounds 5 KB/s down to nothing, is not a cosmetic defect on this screen — it is the screen giving
 * the wrong answer while looking like it worked.
 */
class NetworkDiagnosticsFormatTest {
    private fun timing(
        firstByteMillis: Long,
        totalMillis: Long,
        bytes: Long,
    ) = ProbeTiming(
        dnsMillis = null,
        connectMillis = null,
        tlsMillis = null,
        firstByteMillis = firstByteMillis,
        totalMillis = totalMillis,
        bytes = bytes,
    )

    @Test
    fun `the rate is measured over the body transfer and not over the whole call`() {
        // 60 KB arriving in 2 s, after a far end that spent 8 s thinking about it. Dividing by the
        // full ten seconds would report a third of the truth and read as a broken connection.
        val measured = timing(firstByteMillis = 8_000, totalMillis = 10_000, bytes = 60_000)
        assertEquals(30_000, measured.bodyBytesPerSecond())
    }

    @Test
    fun `a transfer too brief to divide by has no rate rather than a huge one`() {
        assertNull(timing(firstByteMillis = 100, totalMillis = 110, bytes = 60_000).bodyBytesPerSecond())
    }

    @Test
    fun `a body of nothing has no rate`() {
        assertNull(timing(firstByteMillis = 100, totalMillis = 5_000, bytes = 0).bodyBytesPerSecond())
    }

    @Test
    fun `the slow case this screen exists for survives the arithmetic`() {
        // The report that prompted the screen: a page crawling in at about five kilobytes a second.
        val measured = timing(firstByteMillis = 600, totalMillis = 13_600, bytes = 68_000)
        assertEquals("5.1 KB/s", formatRate(measured.bodyBytesPerSecond()!!))
    }

    @Test
    fun `bytes are shown in the unit that keeps them readable`() {
        assertEquals("834 B", formatBytes(834))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("68.2 KB", formatBytes(69_800))
        assertEquals("1.4 MB", formatBytes(1_500_000))
    }

    @Test
    fun `a size is rounded rather than truncated`() {
        // 1.5625 KB. Truncation answers "1.5 KB" and does so for every value in the upper half of
        // every step, which is a systematic under-count wearing the shape of a measurement.
        assertEquals("1.6 KB", formatBytes(1600))
    }

    @Test
    fun `a size that rounds to a whole unit is shown in that unit`() {
        // The kilobyte reading here is 1024.0, which no reader parses as "about a megabyte" — and a
        // megabyte a second is what a healthy connection measures on this screen.
        assertEquals("1.0 MB", formatBytes(1_048_550))
        assertEquals("1.0 MB/s", formatRate(1_048_550))
        // And a hair below it still reads as kilobytes, because there it is still accurate.
        assertEquals("1023.9 KB", formatBytes(1_048_500))
    }

    @Test
    fun `durations stay in milliseconds until seconds are easier to read`() {
        assertEquals("412 ms", formatMillis(412))
        assertEquals("9999 ms", formatMillis(9_999))
        assertEquals("10.0 s", formatMillis(10_000))
        assertEquals("13.4 s", formatMillis(13_400))
    }
}
