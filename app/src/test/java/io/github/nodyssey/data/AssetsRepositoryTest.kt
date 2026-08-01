package io.github.nodyssey.data

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the attendance contract verified on the live site — above all the quirk that a repeat
 * sign-in answers **HTTP 500 with a meaningful JSON body**. A cleanup that checks the status before
 * the body would silently break repeat-sign-in messaging; these tests are what would fail instead.
 */
class AssetsRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    // The real CreditRepository rather than a fake one, deliberately: the attendance check reads the
    // chicken ledger, so these tests are also what pins that the two agree about the wire format.
    private fun repository(
        source: JsonSource,
        profiles: ProfileRepository = UnusedProfileRepository,
    ) = NetworkAssetsRepository(
        profiles,
        NetworkCreditRepository(source, dispatchers),
        source,
        dispatchers,
        AppClock { 1_753_718_400_000L }, // 2025-07-29T00:00:00+08:00
    )

    private fun growthRepository(source: JsonSource) = repository(source, FakeProfileRepository)

    @Test
    fun `parses a successful sign-in and requests the chosen mode`() =
        runTest {
            val source =
                FakePostJsonSource(
                    code = 200,
                    body = """{"success":true,"message":"签到收益5个鸡腿","gain":5,"current":344}""",
                )

            val result = repository(source).signInForToday(AttendanceMode.RANDOM)

            assertEquals(NodeSeekJsonClient.attendancePath("true"), source.requestedPath)
            assertEquals(5, result.gain)
            assertEquals("签到收益5个鸡腿", result.message)
        }

    /** The live site answers a repeat sign-in with HTTP 500 plus the sentence the user needs. */
    @Test
    fun `reports the repeat sign-in sentence despite its 500 status`() =
        runTest {
            val source =
                FakePostJsonSource(
                    code = 500,
                    body = """{"success":false,"message":"今天已完成签到，请勿重复操作"}""",
                )

            val result = repository(source).signInForToday(AttendanceMode.FIXED_FIVE)

            assertEquals("今天已完成签到，请勿重复操作", result.message)
            assertNull(result.gain)
        }

    /** `current` carries the account balance; showing it as today's gain would be worse than nothing. */
    @Test
    fun `never mistakes the balance for the day's gain`() =
        runTest {
            val source =
                FakePostJsonSource(
                    code = 200,
                    body = """{"success":true,"message":"已签到","current":4071}""",
                )

            val result = repository(source).signInForToday(AttendanceMode.RANDOM)

            assertNull(result.gain)
        }

    /** A bare refusal carries nothing to show, so it must not read as a completed sign-in. */
    @Test
    fun `treats a bare refusal as unparsable rather than success`() =
        runTest {
            val source = FakePostJsonSource(code = 200, body = """{"success":false}""")

            val exception =
                runCatching { repository(source).signInForToday(AttendanceMode.RANDOM) }
                    .exceptionOrNull()

            assertEquals(NodeSeekError.Unparsable, (exception as? NodeSeekException)?.error)
        }

    @Test
    fun `maps an unauthenticated sign-in to LoginRequired`() =
        runTest {
            val source = FakePostJsonSource(code = 401, body = "")

            val exception =
                runCatching { repository(source).signInForToday(AttendanceMode.RANDOM) }
                    .exceptionOrNull()

            assertEquals(NodeSeekError.LoginRequired, (exception as? NodeSeekException)?.error)
        }

    @Test
    fun `detects today's gain from the signed in account ledger`() =
        runTest {
            val source =
                FakeCreditJsonSource(
                    """{"success":true,"data":[[7,344,"签到收益7个鸡腿","2025-07-28T16:30:00.000Z"]]}""",
                )
            val repository = repository(source)

            val status = repository.refreshAttendanceStatus(uid = 31037)

            assertEquals(true, status.hasSignedIn)
            assertEquals(7, status.gain)
            assertEquals(NodeSeekJsonClient.creditLedgerPath(1), source.requestedPath)
            assertEquals(status, repository.observeAttendanceStatus().first())
        }

    @Test
    fun `reports unsigned when today's ledger has no attendance income`() =
        runTest {
            val repository =
                repository(
                    FakeCreditJsonSource(
                        """{"success":true,"data":[[1,337,"回帖奖励","2025-07-28T17:00:00.000Z"],[5,336,"签到收益5个鸡腿","2025-07-27T03:00:00.000Z"]]}""",
                    ),
                )

            val status = repository.refreshAttendanceStatus(uid = 31037)

            assertEquals(false, status.hasSignedIn)
            assertNull(status.gain)
        }

    /**
     * The unit conversion `/progress` performs, pinned: posts arrive counted in *posts* and are shown
     * in chicken legs, comments arrive already counted in chicken legs. Reading both raw would put
     * today's posting allowance on screen as `0 / 4`.
     */
    @Test
    fun `reads today's allowances and converts the post pair to chicken legs`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to
                        """{"success":true,"postBonusCount":1,"maxPostBonusCount":4,"commentBonusCount":3,"maxCommentBonusCount":20,"maxFreeLike":1,"freeLikeUsed":1}""",
                    NodeSeekJsonClient.attendanceBoardPath(1) to """{"list":[],"record":null,"total":0}""",
                    NodeSeekJsonClient.creditLedgerPath(1) to """{"success":true,"data":[]}""",
                )

            val growth = growthRepository(source).growth()

            assertEquals(DailyQuota(5, 20), growth.postQuota)
            assertEquals(DailyQuota(3, 20), growth.commentQuota)
            assertEquals(DailyQuota(1, 1), growth.feedingQuota)
        }

    /** A zero allowance is a fact the site stated; that is not the same as one we could not read. */
    @Test
    fun `keeps a genuinely zero feeding allowance distinct from an unknown one`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to
                        """{"success":true,"postBonusCount":0,"maxPostBonusCount":4,"commentBonusCount":0,"maxCommentBonusCount":20,"maxFreeLike":0,"freeLikeUsed":0}""",
                    NodeSeekJsonClient.attendanceBoardPath(1) to """{"list":[],"record":null}""",
                    NodeSeekJsonClient.creditLedgerPath(1) to """{"success":true,"data":[]}""",
                )

            val growth = growthRepository(source).growth()

            assertEquals(DailyQuota(0, 0), growth.feedingQuota)
            assertEquals(true, growth.feedingQuota.isKnown)
        }

    /**
     * An allowance is worth less than the balance and level beside it, so losing one must not lose the
     * screen — it degrades to "unknown", which is exactly what the card is built to render.
     */
    @Test
    fun `keeps the account when today's allowances cannot be read`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to "<html>Just a moment…</html>",
                    NodeSeekJsonClient.attendanceBoardPath(1) to "<html>Just a moment…</html>",
                    NodeSeekJsonClient.creditLedgerPath(1) to "<html>Just a moment…</html>",
                )

            val growth = growthRepository(source).growth()

            assertEquals(2, growth.level)
            assertEquals(409, growth.chickenCount)
            // The level bar comes off a formula, not the network, so it survives what the allowances did not.
            assertEquals(400, growth.levelFloorChicken)
            assertEquals(900, growth.nextLevelChicken)
            assertNull(growth.postQuota.used)
            assertEquals(20, growth.postQuota.total)
            assertEquals(DailyQuota(null, null), growth.attendanceQuota)
        }

    /** Signing in pays a variable roll, so the bar fills to whatever was earned rather than to a cap. */
    @Test
    fun `fills the attendance allowance from the board's own record`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to """{"success":true}""",
                    NodeSeekJsonClient.attendanceBoardPath(1) to
                        """{"list":[],"record":{"id":5603588,"member_id":52425,"gain":14,"created_at":"2026-08-02T01:00:00.000Z"}}""",
                )
            val repository = growthRepository(source)

            val growth = repository.growth()

            assertEquals(DailyQuota(14, 14), growth.attendanceQuota)
            assertEquals(true, repository.observeAttendanceStatus().first()?.hasSignedIn)
            assertEquals(14, repository.observeAttendanceStatus().first()?.gain)
        }

    /**
     * `record: null` is the site's unsigned answer — but only on an account that had not signed in,
     * which is the one case seen live. The ledger scan still gets to disagree, and when it finds
     * today's sign-in the bar must follow it rather than the silent record.
     */
    @Test
    fun `falls back to the ledger when the board's record says nothing`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to """{"success":true}""",
                    NodeSeekJsonClient.attendanceBoardPath(1) to """{"list":[],"record":null}""",
                    NodeSeekJsonClient.creditLedgerPath(1) to
                        """{"success":true,"data":[[7,344,"签到收益7个鸡腿","2025-07-28T16:30:00.000Z"]]}""",
                )

            val growth = growthRepository(source).growth()

            assertEquals(DailyQuota(7, 7), growth.attendanceQuota)
        }

    /** Nothing anywhere says the day is unsigned, which is the site's own empty bar: `0 / 20`. */
    @Test
    fun `shows an unsigned day as an empty bar`() =
        runTest {
            val source =
                FakeRoutedJsonSource(
                    NodeSeekSite.PROGRESS_TODAY_API_PATH to """{"success":true}""",
                    NodeSeekJsonClient.attendanceBoardPath(1) to """{"list":[],"record":null}""",
                    NodeSeekJsonClient.creditLedgerPath(1) to """{"success":true,"data":[]}""",
                )

            val growth = growthRepository(source).growth()

            assertEquals(DailyQuota(0, 20), growth.attendanceQuota)
        }
}

/** Answers per path, because [NetworkAssetsRepository.growth] reads three different endpoints. */
private class FakeRoutedJsonSource(
    vararg responses: Pair<String, String>,
) : JsonSource {
    private val bodies = responses.toMap()

    override suspend fun getJson(path: String, referer: String): String =
        bodies[path] ?: error("Unexpected request: $path")

    override suspend fun postJson(path: String, referer: String): JsonPostResponse =
        error("These tests only GET")
}

private class FakeCreditJsonSource(
    private val body: String,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(path: String, referer: String): String {
        requestedPath = path
        return body
    }

    override suspend fun postJson(path: String, referer: String): JsonPostResponse =
        error("These tests only GET")
}

private class FakePostJsonSource(
    private val code: Int,
    private val body: String,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(path: String, referer: String): String =
        error("These tests only POST")

    override suspend fun postJson(path: String, referer: String): JsonPostResponse {
        requestedPath = path
        return JsonPostResponse(code, body)
    }
}

/** The account behind the live capture these tests were written from. */
private object FakeProfileRepository : ProfileRepository {
    override val selfUid: Long = 52425

    override suspend fun profile(refresh: Boolean): UserProfile =
        UserProfile(
            uid = 52425,
            name = "tester",
            avatarUrl = "",
            rank = 2,
            chickenCount = 409,
            starCount = 4,
        )

    override suspend fun profile(uid: Long): UserProfile = profile()
}

private object UnusedProfileRepository : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile = error("These tests never load a profile")

    override suspend fun profile(uid: Long): UserProfile = error("These tests never load a profile")
}
