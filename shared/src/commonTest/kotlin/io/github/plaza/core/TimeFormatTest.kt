package io.github.plaza.core

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeFormatTest {
    private val zone = TimeZone.of("Asia/Shanghai")

    /** 10:22 +08:00 — the wall clock every board draws in its status bar. Elapsed time floors. */
    private val now = TimeFormat.parseTimestamp("2026-07-26 10:22:03", zone)!!

    @Test
    fun `parses the timestamp formats the endpoints actually send`() {
        assertEquals(
            TimeFormat.parseTimestamp("2026-07-26T09:56:03", zone),
            TimeFormat.parseTimestamp("2026-07-26 09:56:03", zone),
        )
        assertEquals(1_774_000_000_000L, TimeFormat.parseTimestamp("1774000000", zone))
        assertEquals(1_774_000_000_000L, TimeFormat.parseTimestamp("1774000000000", zone))
    }

    @Test
    fun `pre-rendered server wording is not a timestamp`() {
        assertNull(TimeFormat.parseTimestamp("5分钟前", zone))
        assertNull(TimeFormat.parseTimestamp("", zone))
        assertNull(TimeFormat.parseTimestamp(null, zone))
        // Short numbers are ids, not seconds since the epoch.
        assertNull(TimeFormat.parseTimestamp("42", zone))
    }

    @Test
    fun `relative label matches board 7d`() {
        assertEquals("26 分钟前", TimeFormat.relative(at("2026-07-26 09:56:03"), now, zone))
        assertEquals("2 小时前", TimeFormat.relative(at("2026-07-26 08:02:45"), now, zone))
        assertEquals("昨天", TimeFormat.relative(at("2026-07-25 18:09:50"), now, zone))
        assertEquals("3 天前", TimeFormat.relative(at("2026-07-23 21:14:36"), now, zone))
        assertEquals("7月15日", TimeFormat.relative(at("2026-07-15 20:33:28"), now, zone))
        assertEquals("2025年7月15日", TimeFormat.relative(at("2025-07-15 20:33:28"), now, zone))
    }

    /** An hours-old message from yesterday reads as 昨天, never as "20 小时前". */
    @Test
    fun `relative label crosses midnight by calendar day`() {
        assertEquals("昨天", TimeFormat.relative(at("2026-07-25 23:30:00"), now, zone))
    }

    @Test
    fun `absolute label keeps seconds`() {
        assertEquals("2026/7/26 09:56:03", TimeFormat.absolute(at("2026-07-26 09:56:03"), zone))
    }

    @Test
    fun `conversation stamp degrades from clock to weekday to date`() {
        assertEquals("10:18", TimeFormat.conversationStamp(at("2026-07-26 10:18:00"), now, zone))
        assertEquals("昨天", TimeFormat.conversationStamp(at("2026-07-25 10:18:00"), now, zone))
        assertEquals("周三", TimeFormat.conversationStamp(at("2026-07-22 10:18:00"), now, zone))
        assertEquals("7月18日", TimeFormat.conversationStamp(at("2026-07-18 10:18:00"), now, zone))
    }

    @Test
    fun `message divider names the day`() {
        assertEquals("今天 09:41", TimeFormat.messageDivider(at("2026-07-26 09:41:00"), now, zone))
        assertEquals("昨天 09:41", TimeFormat.messageDivider(at("2026-07-25 09:41:00"), now, zone))
        assertEquals("7月18日 09:41", TimeFormat.messageDivider(at("2026-07-18 09:41:00"), now, zone))
    }

    private fun at(value: String): Long = TimeFormat.parseTimestamp(value, zone)!!
}
