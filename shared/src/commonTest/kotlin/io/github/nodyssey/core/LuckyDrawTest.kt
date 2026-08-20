package io.github.nodyssey.core

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The draw link is pasted into a public thread and opened by other people, so its shape is a contract.
 *
 * A fixed zone rather than the device's: the formatted closing time is part of the URL, and a test that
 * passes only in UTC+8 would hide the bug where it does not.
 */
class LuckyDrawTest {
    private val shanghai = TimeZone.of("Asia/Shanghai")

    // 2026-07-27 20:00 +08:00
    private val drawAt = 1_785_153_600_000L

    @Test
    fun `builds the link with the five parameters the site's form has`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 286417,
                    drawAtMillis = drawAt,
                    prizeCount = 3,
                    startFloor = 1,
                    dedupeFloors = true,
                ),
                zone = shanghai,
            )

        assertEquals(
            "https://www.nodeseek.com/lucky?post=286417&time=2026-07-27%2020%3A00&n=3&start=1&unique=1",
            link,
        )
    }

    @Test
    fun `writes dedupe off as zero rather than omitting it`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 1,
                    drawAtMillis = drawAt,
                    prizeCount = 1,
                    startFloor = 0,
                    dedupeFloors = false,
                ),
                zone = shanghai,
            )

        assertTrue(link.endsWith("&start=0&unique=0"))
    }

    /** A zero prize count would produce a draw nobody can win; the builder floors it at one. */
    @Test
    fun `never generates a draw with no prizes`() {
        val link =
            LuckyDraw.link(
                LuckyDrawParams(
                    postId = 9,
                    drawAtMillis = drawAt,
                    prizeCount = 0,
                    startFloor = -4,
                    dedupeFloors = true,
                ),
                zone = shanghai,
            )

        assertTrue(link.contains("&n=1&"))
        assertTrue(link.contains("&start=0&"))
    }

    @Test
    fun `formats the closing time the way the form displays it`() {
        assertEquals("2026/7/27 20:00", LuckyDraw.formatDrawTime(drawAt, shanghai))
    }
}
