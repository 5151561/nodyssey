package io.github.nsreader.data

import io.github.nsreader.core.AppClock
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.net.JsonPostResponse
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
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

    private fun repository(source: JsonSource) =
        NetworkAssetsRepository(
            UnusedProfileRepository,
            source,
            dispatchers,
            AppClock { 1_753_718_400_000L }, // 2025-07-29T00:00:00+08:00
        )

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

private object UnusedProfileRepository : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile = error("These tests never load a profile")

    override suspend fun profile(uid: Long): UserProfile = error("These tests never load a profile")
}
