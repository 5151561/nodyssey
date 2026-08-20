package io.github.nodyssey.ui.history

import io.github.nodyssey.data.ReadHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

/**
 * The day headings, which are calendar arithmetic and therefore where the off-by-one lives.
 *
 * A fixed zone rather than the device's: "yesterday" is a question about a calendar, and a test that
 * asked it in whatever zone the machine happened to be in would pass or fail by geography.
 */
class HistorySectionsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-08-06 09:00 local. */
    private val now = 1_785_978_000_000L
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun entry(readAt: Long) = ReadHistoryEntry(1, null, null, null, null, null, readAt)

    private fun bucketOf(readAt: Long) =
        historySections(listOf(entry(readAt)), now, zone).single().bucket

    @Test
    fun `a read this morning is today`() {
        assertEquals(HistoryBucket.Today, bucketOf(now - hour))
    }

    /**
     * The case that makes this calendar arithmetic and not elapsed hours: 22 hours ago is still last
     * night if the reader is up early, and last night is 昨天 however few hours have passed.
     */
    @Test
    fun `last night is yesterday even when it is under a day ago`() {
        assertEquals(HistoryBucket.Yesterday, bucketOf(now - 22 * hour))
    }

    @Test
    fun `the sixth day back is still the week and the seventh is not`() {
        assertEquals(HistoryBucket.Week, bucketOf(now - 6 * day))
        assertEquals(HistoryBucket.Earlier, bucketOf(now - 7 * day))
    }

    /** A device whose clock moved backwards. 今天 is wrong by a hair; 更早 would be wrong by a week. */
    @Test
    fun `a timestamp in the future reads as today`() {
        assertEquals(HistoryBucket.Today, bucketOf(now + 2 * hour))
    }

    /** Sections come out newest first whatever order the rows arrive in. */
    @Test
    fun `sections are ordered even when the rows are not`() {
        val sections =
            historySections(
                listOf(entry(now - 8 * day), entry(now), entry(now - 3 * day), entry(now - day)),
                now,
                zone,
            )

        assertEquals(
            listOf(HistoryBucket.Today, HistoryBucket.Yesterday, HistoryBucket.Week, HistoryBucket.Earlier),
            sections.map { it.bucket },
        )
    }
}
