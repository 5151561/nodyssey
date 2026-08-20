package io.github.plaza.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Wall-clock formatting for the notification and direct-message screens.
 *
 * A forum's JSON endpoints are inconsistent about time: some rows carry an ISO-8601 instant, some
 * carry epoch milliseconds, and some carry a string the server already rendered ("5分钟前"). Parsing
 * is therefore best-effort — [parseTimestamp] answers `null` for the pre-rendered case and callers
 * fall back to showing the server's own string rather than inventing a time.
 *
 * The zone is the device's, not the site's: a timestamp is only useful relative to the reader.
 *
 * On kotlinx-datetime rather than `java.time`, which is what made this file common in step A7 — five
 * repositories call [parseTimestamp] and all five are now in `commonMain`.
 *
 * The wording is Chinese and hardcoded, which is the one place this app lets a data-shaped class
 * decide copy. It holds only for a Chinese-language forum with no localised build;
 * adding a second language means moving these strings to resources and the formatting to the UI.
 * This module is also copied into https://github.com/5151561/plaza, as a copy rather than a
 * dependency — so that app inherits this wording as of the copy and nothing here reaches it since.
 */
object TimeFormat {

    /** Boards 7d/7e/7f pair a relative label with a full timestamp, so both live here. */
    fun parseTimestamp(
        raw: String?,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        value.toLongOrNull()?.let { number ->
            // Ten digits is seconds, thirteen is milliseconds; anything shorter is an id, not a time.
            return when {
                number >= EPOCH_MILLIS_FLOOR -> number
                number >= EPOCH_SECONDS_FLOOR -> number * 1_000
                else -> null
            }
        }
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it.toEpochMilliseconds() }
        // `2026-07-26 09:56:03` and `2026-07-26T09:56:03` — neither carries a zone, so assume local.
        val normalized = value.replace(' ', 'T')
        // `runCatching` rather than a named exception: a malformed civil date is an
        // `IllegalArgumentException` subtype here, and which subtype is not this file's business.
        return runCatching { LocalDateTime.parse(normalized).toInstant(zone).toEpochMilliseconds() }.getOrNull()
    }

    /** `26 分钟前` / `昨天` / `3 天前` / `7月15日` — the leading half of a 7d timestamp row. */
    fun relative(
        millis: Long,
        nowMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val elapsed = nowMillis - millis
        if (elapsed < 0) return absolute(millis, zone)
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val daysApart = day.daysUntil(today)
        return when {
            elapsed < MINUTE -> "刚刚"
            elapsed < HOUR -> "${elapsed / MINUTE} 分钟前"
            daysApart == 0 -> "${elapsed / HOUR} 小时前"
            daysApart == 1 -> "昨天"
            daysApart < WEEK_DAYS -> "$daysApart 天前"
            day.year == today.year -> "${day.monthNumber}月${day.dayOfMonth}日"
            else -> "${day.year}年${day.monthNumber}月${day.dayOfMonth}日"
        }
    }

    /** `2026/7/26 09:56:03` — the trailing half, kept unabbreviated because 7d asks for seconds. */
    fun absolute(
        millis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val time = millis.toLocalDateTime(zone)
        return "${time.year}/${time.monthNumber}/${time.dayOfMonth} " +
            "${time.hour.pad()}:${time.minute.pad()}:${time.second.pad()}"
    }

    /** Conversation-list stamp (7e): the clock today, a weekday this week, a date before that. */
    fun conversationStamp(
        millis: Long,
        nowMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val daysApart = day.daysUntil(today)
        return when {
            daysApart <= 0 -> millis.toLocalDateTime(zone).let { "${it.hour.pad()}:${it.minute.pad()}" }
            daysApart == 1 -> "昨天"
            daysApart < WEEK_DAYS -> WEEKDAYS[day.dayOfWeek.isoDayNumber - 1]
            else -> "${day.monthNumber}月${day.dayOfMonth}日"
        }
    }

    /** The centred chip that separates a run of messages in 7f: `今天 09:41`. */
    fun messageDivider(
        millis: Long,
        nowMillis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val time = millis.toLocalDateTime(zone)
        val clock = "${time.hour.pad()}:${time.minute.pad()}"
        return when (day.daysUntil(today)) {
            0 -> "今天 $clock"
            1 -> "昨天 $clock"
            else -> "${day.monthNumber}月${day.dayOfMonth}日 $clock"
        }
    }

    /** `09:44` — the stamp under a single bubble. */
    fun clock(
        millis: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val time = millis.toLocalDateTime(zone)
        return "${time.hour.pad()}:${time.minute.pad()}"
    }

    private fun Long.toLocalDateTime(zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

    private fun Long.toLocalDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

    private fun Int.pad(): String = toString().padStart(2, '0')

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val WEEK_DAYS = 7
    private const val EPOCH_SECONDS_FLOOR = 1_000_000_000L
    private const val EPOCH_MILLIS_FLOOR = 100_000_000_000L
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}
