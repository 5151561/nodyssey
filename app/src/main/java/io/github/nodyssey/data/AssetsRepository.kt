package io.github.nodyssey.data

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A daily allowance, as `/progress` shows it: how much of today's cap has been earned.
 *
 * Both halves stay nullable now that the numbers are real, because a lookup that fails still has to
 * be distinguishable from a quota that is genuinely zero — an allowance spent down to `0 / 0` is a
 * fact, and showing it as "unknown" would be as wrong as the reverse.
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
 * Levelling is chicken-based, and the thresholds are a published formula rather than the single
 * 400 they were once taken for — see [NodeSeekSite.levelChickenSpan]. The bar runs
 * [levelFloorChicken] → [nextLevelChicken], both null only when the account's level is unknown.
 */
data class GrowthSnapshot(
    val level: Int?,
    val chickenCount: Int?,
    val starCount: Int?,
    /** Where the current level began; the bar's zero, not the account's. */
    val levelFloorChicken: Int?,
    val nextLevelChicken: Int?,
    /** The level the bar is drawn for — the account's, except above Lv5 where the site clamps. */
    val levelBarRank: Int?,
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
    private val creditRepository: CreditRepository,
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
    private val clock: AppClock,
) : AssetsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val attendanceStatus = MutableStateFlow<AttendanceStatus?>(null)

    override fun observeAttendanceStatus(): Flow<AttendanceStatus?> = attendanceStatus.asStateFlow()

    /**
     * The account, plus today's four allowances.
     *
     * The three that `/api/progress/today` carries are fetched together with the profile, and their
     * failure is swallowed on purpose: an allowance we could not read is worth strictly less than the
     * balance and level next to it, so it degrades to "unknown" instead of failing the whole screen.
     */
    override suspend fun growth(): GrowthSnapshot = coroutineScope {
        val progressAsync = async { runCatchingExceptCancellation { progressToday() }.getOrNull() }
        val attendanceAsync = async { runCatchingExceptCancellation { attendanceQuotaToday() }.getOrNull() }
        val profile = profileRepository.profile()
        val progress = progressAsync.await()
        val span = profile.rank?.let(NodeSeekSite::levelChickenSpan)
        GrowthSnapshot(
            level = profile.rank,
            chickenCount = profile.chickenCount,
            starCount = profile.starCount,
            levelFloorChicken = span?.floor,
            nextLevelChicken = span?.next,
            levelBarRank = span?.barRank,
            // Posts are counted in posts on the wire and in chicken legs on screen; comments are
            // already chicken legs. See [NodeSeekSite.PROGRESS_TODAY_API_PATH].
            postQuota =
            DailyQuota(
                used = progress?.postCount?.let { it * CHICKEN_PER_POST },
                total = progress?.postCap?.let { it * CHICKEN_PER_POST } ?: POST_QUOTA_TOTAL,
            ),
            commentQuota =
            DailyQuota(
                used = progress?.commentCount,
                total = progress?.commentCap ?: COMMENT_QUOTA_TOTAL,
            ),
            attendanceQuota = attendanceAsync.await() ?: DailyQuota(null, null),
            feedingQuota = DailyQuota(used = progress?.freeLikeUsed, total = progress?.freeLikeCap),
        )
    }

    /** The three allowances the site publishes as one payload; see [NodeSeekSite.PROGRESS_TODAY_API_PATH]. */
    private suspend fun progressToday(): ProgressToday {
        val body =
            jsonSource.getJson(
                path = NodeSeekSite.PROGRESS_TODAY_API_PATH,
                referer = NodeSeekSite.BASE_URL + "/progress",
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            if (root.bool("success") == false) throw NodeSeekException(NodeSeekError.Unparsable)
            ProgressToday(
                postCount = root.int("postBonusCount"),
                postCap = root.int("maxPostBonusCount"),
                commentCount = root.int("commentBonusCount"),
                commentCap = root.int("maxCommentBonusCount"),
                freeLikeUsed = root.int("freeLikeUsed"),
                freeLikeCap = root.int("maxFreeLike"),
            )
        }
    }

    /**
     * Today's sign-in allowance, which the site draws as a bar that is either empty or full.
     *
     * There is no cap to fill towards: signing in pays a variable roll, so the site fills the bar to
     * whatever was earned and shows a nominal 20 while unsigned. That is reproduced here rather than
     * invented — `gain / gain` after signing in, `0 / 20` before.
     *
     * The record is also the cheap answer to "did I sign in today" — one request against the ten
     * [attendanceGainToday] may spend — so the shared attendance state is updated from it in passing.
     * A silent record is not taken as proof of an unsigned day, though: only `record: null` on an
     * unsigned account has been seen live, so the ledger scan still gets to disagree.
     */
    private suspend fun attendanceQuotaToday(): DailyQuota {
        val gain = attendanceRecordToday() ?: attendanceGainToday()
        profileRepository.selfUid?.let { uid ->
            attendanceStatus.value =
                AttendanceStatus(uid = uid, hasSignedIn = gain != null, gain = gain)
        }
        return if (gain != null) {
            DailyQuota(used = gain, total = gain)
        } else {
            DailyQuota(used = 0, total = ATTENDANCE_QUOTA_NOMINAL)
        }
    }

    /**
     * Today's own sign-in, from the board payload's `record` — the account's row rather than the page
     * of rows, which is what `/progress` reads and why the board is requested at all here.
     *
     * `record: null` is the site's unsigned answer (verified live on 2026-08-02, against an account
     * that had not signed in).
     */
    private suspend fun attendanceRecordToday(): Int? {
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.attendanceBoardPath(1),
                referer = NodeSeekSite.BASE_URL + "/board",
            )
        return withContext(dispatchers.default) {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            (root?.get("record") as? JsonObject)?.int("gain", "amount")
        }
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
            val entries = creditRepository.page(page).entries
            entries
                .firstOrNull { entry ->
                    entry.dateInNodeSeekZone() == today &&
                        ATTENDANCE_REASON in entry.reason &&
                        CHICKEN_REASON in entry.reason
                }?.let { return it.change }
            if (entries.isEmpty()) return null
            val oldestDate =
                entries.mapNotNull(CreditEntry::dateInNodeSeekZone).minOrNull() ?: return null
            if (oldestDate < today) return null
        }
        return null
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

    /** `/api/progress/today` verbatim, before the post pair is converted to chicken legs. */
    private data class ProgressToday(
        val postCount: Int?,
        val postCap: Int?,
        val commentCount: Int?,
        val commentCap: Int?,
        val freeLikeUsed: Int?,
        val freeLikeCap: Int?,
    )

    private companion object {
        const val CHICKEN_PER_POST = 5

        /** Only the fallback for a lookup that failed; the live caps come from the payload. */
        const val POST_QUOTA_TOTAL = 20
        const val COMMENT_QUOTA_TOTAL = 20

        /** What the site's own bar shows for an unsigned day — a placeholder, not a real cap. */
        const val ATTENDANCE_QUOTA_NOMINAL = 20
        const val MAX_ATTENDANCE_LEDGER_PAGES = 10
        const val ATTENDANCE_REASON = "签到收益"
        const val CHICKEN_REASON = "鸡腿"
    }
}

/**
 * Which NodeSeek day a ledger row belongs to.
 *
 * Asia/Shanghai rather than the device's zone, and that is the whole point of this helper: "已签到"
 * is a fact about the site's calendar day, so a phone in UTC−7 must not decide the streak reset
 * happened seven hours early. The ledger *screen* deliberately does the opposite and formats in the
 * reader's zone — see [CreditEntry].
 */
private fun CreditEntry.dateInNodeSeekZone(): LocalDate? =
    createdAtMillis?.let { Instant.ofEpochMilli(it).atZone(NODESEEK_ZONE).toLocalDate() }

private fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(NODESEEK_ZONE).toLocalDate()

private val NODESEEK_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
