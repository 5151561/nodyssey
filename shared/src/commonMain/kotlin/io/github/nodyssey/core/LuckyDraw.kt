package io.github.nodyssey.core

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The parameters of a T-floor draw, as `/lucky` asks for them.
 *
 * NodeSeek's "幸运抽奖" is not a game — it is a notary: you declare in advance which thread, when it
 * closes, how many prizes, which floor counting starts from, and whether one user can win twice. The
 * link that comes out is what gets pasted into the thread, and anyone can open it afterwards to check
 * the result was not chosen after the fact. Nothing is spent.
 */
data class LuckyDrawParams(
    val postId: Long,
    val drawAtMillis: Long,
    val prizeCount: Int,
    val startFloor: Int,
    val dedupeFloors: Boolean,
)

object LuckyDraw {

    const val MAX_PRIZE_COUNT = 999

    /**
     * Builds the public draw link.
     *
     * The query keys follow the site's own form (`post` / `time` / `n` / `start` / `unique`). They are
     * read off the page rather than from a documented API, so this stays one small function with a
     * test: if the site renames a key, one assertion fails instead of a screen going quietly wrong.
     */
    fun link(params: LuckyDrawParams, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val drawAt = params.drawAtMillis.at(zone).let { "${it.year}-${it.monthNumber.pad()}-${it.dayOfMonth.pad()} ${it.hour.pad()}:${it.minute.pad()}" }
        val query =
            listOf(
                "post" to params.postId.toString(),
                "time" to drawAt,
                "n" to params.prizeCount.coerceAtLeast(1).toString(),
                "start" to params.startFloor.coerceAtLeast(0).toString(),
                "unique" to if (params.dedupeFloors) "1" else "0",
            ).joinToString("&") { (key, value) -> "$key=${value.urlEncode()}" }
        return "${NodeSeekSite.BASE_URL}${NodeSeekSite.LUCKY_PATH}?$query"
    }

    /** The same instant, formatted the way the form displays it. */
    fun formatDrawTime(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
        millis.at(zone).let { "${it.year}/${it.monthNumber}/${it.dayOfMonth} ${it.hour.pad()}:${it.minute.pad()}" }

    /*
     * The two patterns above used to be `DateTimeFormatter.ofPattern`, and they are written out here
     * for the reason `TimeFormat` records: `java.time` is the JVM's, and this file is read by a screen
     * that is no longer allowed to assume one. The digits are the same — `yyyy-MM-dd HH:mm` in the
     * link, `yyyy/M/d HH:mm` on the form — and `LuckyDrawTest` is what says so.
     */
    private fun Long.at(zone: TimeZone) = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

    private fun Int.pad(): String = toString().padStart(2, '0')
}
