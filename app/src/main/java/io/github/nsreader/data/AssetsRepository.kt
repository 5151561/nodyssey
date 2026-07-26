package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    suspend fun growth(): GrowthSnapshot

    suspend fun signInForToday(mode: AttendanceMode): AttendanceResult

    /** Today's sign-in board, ranked by gain. */
    suspend fun attendanceBoard(page: Int = 1): List<AttendanceBoardEntry>
}

class NetworkAssetsRepository(
    private val profileRepository: ProfileRepository,
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : AssetsRepository {
    private val json = Json { ignoreUnknownKeys = true }

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
        return withContext(dispatchers.default) {
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
    }
}
