package io.github.nsreader.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    fun link(params: LuckyDrawParams, zone: ZoneId = ZoneId.systemDefault()): String {
        val drawAt =
            LocalDateTime
                .ofInstant(Instant.ofEpochMilli(params.drawAtMillis), zone)
                .format(DRAW_TIME_FORMAT)
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
    fun formatDrawTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(DISPLAY_TIME_FORMAT)

    private val DRAW_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
