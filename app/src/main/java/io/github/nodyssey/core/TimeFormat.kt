package io.github.nodyssey.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Wall-clock formatting for the notification and direct-message screens.
 *
 * NodeSeek's JSON endpoints are inconsistent about time: some rows carry an ISO-8601 instant, some
 * carry epoch milliseconds, and some carry a string the server already rendered ("5分钟前"). Parsing
 * is therefore best-effort — [parseTimestamp] answers `null` for the pre-rendered case and callers
 * fall back to showing the server's own string rather than inventing a time.
 *
 * The zone is the device's, not the site's: a timestamp is only useful relative to the reader.
 *
 * The wording is Chinese and hardcoded, which is the one place this app lets a data-shaped class
 * decide copy. It holds only because NodeSeek is a Chinese-language forum with no localised build;
 * adding a second language means moving these strings to resources and the formatting to the UI.
 */
object TimeFormat {

    /** Boards 7d/7e/7f pair a relative label with a full timestamp, so both live here. */
    fun parseTimestamp(
        raw: String?,
        zone: ZoneId = ZoneId.systemDefault(),
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
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it.toEpochMilli() }
        // `2026-07-26 09:56:03` and `2026-07-26T09:56:03` — neither carries a zone, so assume local.
        val normalized = value.replace(' ', 'T')
        return try {
            LocalDateTime.parse(normalized).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** `26 分钟前` / `昨天` / `3 天前` / `7月15日` — the leading half of a 7d timestamp row. */
    fun relative(
        millis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val elapsed = nowMillis - millis
        if (elapsed < 0) return absolute(millis, zone)
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val daysApart = ChronoUnit.DAYS.between(day, today)
        return when {
            elapsed < MINUTE -> "刚刚"
            elapsed < HOUR -> "${elapsed / MINUTE} 分钟前"
            daysApart == 0L -> "${elapsed / HOUR} 小时前"
            daysApart == 1L -> "昨天"
            daysApart < WEEK_DAYS -> "$daysApart 天前"
            day.year == today.year -> "${day.monthValue}月${day.dayOfMonth}日"
            else -> "${day.year}年${day.monthValue}月${day.dayOfMonth}日"
        }
    }

    /** `2026/7/26 09:56:03` — the trailing half, kept unabbreviated because 7d asks for seconds. */
    fun absolute(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = millis.toLocalDateTime(zone)
        return "${time.year}/${time.monthValue}/${time.dayOfMonth} " +
            "${time.hour.pad()}:${time.minute.pad()}:${time.second.pad()}"
    }

    /** Conversation-list stamp (7e): the clock today, a weekday this week, a date before that. */
    fun conversationStamp(
        millis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val daysApart = ChronoUnit.DAYS.between(day, today)
        return when {
            daysApart <= 0L -> millis.toLocalDateTime(zone).let { "${it.hour.pad()}:${it.minute.pad()}" }
            daysApart == 1L -> "昨天"
            daysApart < WEEK_DAYS -> WEEKDAYS[day.dayOfWeek.value - 1]
            else -> "${day.monthValue}月${day.dayOfMonth}日"
        }
    }

    /** The centred chip that separates a run of messages in 7f: `今天 09:41`. */
    fun messageDivider(
        millis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val day = millis.toLocalDate(zone)
        val today = nowMillis.toLocalDate(zone)
        val time = millis.toLocalDateTime(zone)
        val clock = "${time.hour.pad()}:${time.minute.pad()}"
        return when (ChronoUnit.DAYS.between(day, today)) {
            0L -> "今天 $clock"
            1L -> "昨天 $clock"
            else -> "${day.monthValue}月${day.dayOfMonth}日 $clock"
        }
    }

    /** `09:44` — the stamp under a single bubble. */
    fun clock(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = millis.toLocalDateTime(zone)
        return "${time.hour.pad()}:${time.minute.pad()}"
    }

    private fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = toLocalDateTime(zone).toLocalDate()

    private fun Int.pad(): String = toString().padStart(2, '0')

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val WEEK_DAYS = 7L
    private const val EPOCH_SECONDS_FLOOR = 1_000_000_000L
    private const val EPOCH_MILLIS_FLOOR = 100_000_000_000L
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}
