package io.github.nsreader.data

import io.github.nsreader.core.AppClock
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.TimeFormat
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.runCatchingExceptCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A daily allowance, as `/progress` shows it: how much of today's cap has been earned.
 *
 * Both halves are nullable because the page that carries them renders client-side. A quota we cannot
 * read has to be distinguishable from a quota that is genuinely zero — Lv1's free feeding allowance
 * really is `0 / 0`, and showing that as "unknown" would be as wrong as the reverse.
 */
data class DailyQuota(
    val used: Int?,
    val total: Int?,
) {
    val isKnown: Boolean get() = used != null && total != null
}

/**
 * Everything the 账户与成长 screen needs.
 *
 * Levelling is chicken-based: the progress bar *is* the chicken count, and Lv1 becomes Lv2 at 400.
 * Only that one threshold is published, so [nextLevelChicken] is null on every other level rather
 * than extrapolated from a curve nobody has stated.
 */
data class GrowthSnapshot(
    val level: Int?,
    val chickenCount: Int?,
    val starCount: Int?,
    val nextLevelChicken: Int?,
    val postQuota: DailyQuota,
    val commentQuota: DailyQuota,
    val attendanceQuota: DailyQuota,
    val feedingQuota: DailyQuota,
)

/** The outcome of signing in for the day: the site answers with a sentence and a chicken count. */
data class AttendanceResult(
    val gain: Int?,
    val message: String?,
)

/** The signed-in account's attendance receipt for the current NodeSeek calendar day. */
data class AttendanceStatus(
    val uid: Long,
    val hasSignedIn: Boolean,
    val gain: Int? = null,
)

/**
 * The site's two sign-in modes, spelled as the wire parameter.
 *
 * `random=true` gambles for a random count (the board shows up to +15); `random=false` takes a flat
 * five. The choice is the user's every day — hardcoding one mode was a bug, not a simplification.
 */
enum class AttendanceMode(val wireValue: String) {
    RANDOM("true"),
    FIXED_FIVE("false"),
}

/** One row of today's sign-in board: who signed, what they rolled. */
data class AttendanceBoardEntry(
    val uid: Long?,
    val name: String,
    val gain: Int?,
    val timeText: String?,
)

interface AssetsRepository {
    /** Shared so “我的” and “账户与成长” cannot disagree after a sign-in. */
    fun observeAttendanceStatus(): Flow<AttendanceStatus?>

    suspend fun growth(): GrowthSnapshot

    /** Checks the account's own ledger without performing the sign-in write. */
    suspend fun refreshAttendanceStatus(uid: Long): AttendanceStatus

    suspend fun signInForToday(mode: AttendanceMode): AttendanceResult

    /** Today's sign-in board, ranked by gain. */
    suspend fun attendanceBoard(page: Int = 1): List<AttendanceBoardEntry>
}

class NetworkAssetsRepository(
    private val profileRepository: ProfileRepository,
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
) : AssetsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val attendanceStatus = MutableStateFlow<AttendanceStatus?>(null)

    override fun observeAttendanceStatus(): Flow<AttendanceStatus?> = attendanceStatus.asStateFlow()

    override suspend fun growth(): GrowthSnapshot {
        val profile = profileRepository.profile()
        return GrowthSnapshot(
            level = profile.rank,
            chickenCount = profile.chickenCount,
            starCount = profile.starCount,
            nextLevelChicken = LEVEL_TWO_CHICKEN.takeIf { profile.rank == 1 },
            // `/progress` is a client-rendered page with no endpoint behind it, so today's four
            // allowances are unknown rather than zero. The card renders them as such.
            postQuota = DailyQuota(null, POST_QUOTA_TOTAL),
            commentQuota = DailyQuota(null, COMMENT_QUOTA_TOTAL),
            attendanceQuota = DailyQuota(null, null),
            feedingQuota = DailyQuota(null, null),
        )
    }

    override suspend fun signInForToday(mode: AttendanceMode): AttendanceResult {
        val response =
            jsonSource.postJson(
                path = NodeSeekJsonClient.attendancePath(mode.wireValue),
                referer = NodeSeekSite.BASE_URL + "/",
            )
        val result = withContext(dispatchers.default) {
            /*
             * The body is read before the status, and deliberately so: a repeat sign-in is answered
             * with **HTTP 500** plus `{"success":false,"message":"今天已完成签到…"}` (verified on the
             * live site). That sentence is the answer the user needs, so any parseable payload with a
             * message wins over the status code; the status only matters once the body says nothing.
             */
            val root = runCatching { json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
            val message = root?.text("message", "msg", "info")
            // A bare `{"success":false}` is a refusal, not an answer: without the site's sentence
            // there is nothing to show, so it falls through to the status-code handling below.
            if (root != null && (message != null || root.bool("success") == true)) {
                return@withContext AttendanceResult(
                    // `current`/`coin` deliberately excluded: those carry the account balance, and
                    // showing 4071 as today's gain would be worse than showing nothing.
                    gain = root.int("gain", "amount"),
                    message = message,
                )
            }
            if (response.code == 401 || response.code == 403) {
                throw NodeSeekException(NodeSeekError.LoginRequired)
            }
            if (!response.isSuccessful) throw NodeSeekException(NodeSeekError.Http(response.code))
            throw NodeSeekException(NodeSeekError.Unparsable)
        }
        profileRepository.selfUid?.let { uid ->
            val resolvedGain =
                result.gain
                    ?: runCatchingExceptCancellation {
                        attendanceGainToday()
                    }.getOrNull()
            attendanceStatus.value =
                AttendanceStatus(
                    uid = uid,
                    hasSignedIn = true,
                    gain = resolvedGain,
                )
        }
        return result
    }

    override suspend fun refreshAttendanceStatus(uid: Long): AttendanceStatus {
        val gain = attendanceGainToday()
        return AttendanceStatus(
            uid = uid,
            hasSignedIn = gain != null,
            gain = gain,
        ).also { attendanceStatus.value = it }
    }

    /**
     * The public board has thousands of rows and cannot identify an arbitrary account cheaply. The
     * signed-in account's own credit ledger is newest-first, so one page normally answers the question;
     * another page is requested only while the oldest returned row is still from today.
     */
    private suspend fun attendanceGainToday(): Int? {
        val today = clock.nowMillis().toDate()
        for (page in 1..MAX_ATTENDANCE_LEDGER_PAGES) {
            val entries = creditEntries(page)
            entries
                .firstOrNull { entry ->
                    entry.date == today &&
                        ATTENDANCE_REASON in entry.reason &&
                        CHICKEN_REASON in entry.reason
                }?.let { return it.change }
            if (entries.isEmpty()) return null
            val oldestDate = entries.mapNotNull(CreditEntry::date).minOrNull() ?: return null
            if (oldestDate < today) return null
        }
        return null
    }

    private suspend fun creditEntries(page: Int): List<CreditEntry> {
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.creditLedgerPath(page),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.CREDIT_PATH,
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) as? JsonObject }
                    .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            val rows = root["data"] as? JsonArray ?: throw NodeSeekException(NodeSeekError.Unparsable)
            rows.mapNotNull { element ->
                val row = element as? JsonArray ?: return@mapNotNull null
                CreditEntry(
                    change = row.getOrNull(0).intValue() ?: return@mapNotNull null,
                    reason = row.getOrNull(2).textValue() ?: return@mapNotNull null,
                    date =
                    TimeFormat
                        .parseTimestamp(row.getOrNull(3).textValue(), NODESEEK_ZONE)
                        ?.toDate(),
                )
            }
        }
    }

    override suspend fun attendanceBoard(page: Int): List<AttendanceBoardEntry> {
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.attendanceBoardPath(page),
                referer = NodeSeekSite.BASE_URL + "/board",
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) }
                    .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
            val rows =
                root.findObjectArray("list", "board", "data")
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            rows.mapNotNull { row ->
                val name = row.text("member_name", "username", "name") ?: return@mapNotNull null
                AttendanceBoardEntry(
                    uid = row.long("member_id", "uid"),
                    name = name,
                    gain = row.int("gain", "amount"),
                    timeText = row.text("created_at_str", "created_at", "createdAt"),
                )
            }
        }
    }

    private companion object {
        const val LEVEL_TWO_CHICKEN = 400
        const val POST_QUOTA_TOTAL = 20
        const val COMMENT_QUOTA_TOTAL = 20
        const val MAX_ATTENDANCE_LEDGER_PAGES = 10
        const val ATTENDANCE_REASON = "签到收益"
        const val CHICKEN_REASON = "鸡腿"
    }
}

private data class CreditEntry(
    val change: Int,
    val reason: String,
    val date: LocalDate?,
)

private fun kotlinx.serialization.json.JsonElement?.intValue(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.removePrefix("+")?.toIntOrNull()
}

private fun kotlinx.serialization.json.JsonElement?.textValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(NODESEEK_ZONE).toLocalDate()

private val NODESEEK_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
