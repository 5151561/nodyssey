package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the four `/api/fans` calls as read out of the site's own `fans` bundle on 2026-08-02.
 *
 * Three things here are worth breaking a build over.
 *
 * The first is the **signed-out guard**: a signed-out read is answered `{"success":true,
 * "memberList":[]}` — HTTP 200, no error field — so without the guard the screen would tell a
 * signed-out user they follow nobody. That is the exact lie the page refused to tell for the whole
 * time it was unwired, and it would come back silently.
 *
 * The second is that the list kind is the **last path segment**, not the `?type=` query parameter the
 * web route uses. The third is the write body's field name, `followed_member_id`, which is snake_case
 * where the rest of this API's write bodies are camelCase.
 */
class FollowRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    private fun repository(api: FakeFansApi, signedIn: Boolean = true) =
        NetworkFollowRepository(api, dispatchers) { signedIn }

    @Test
    fun `reads 我的关注 off the follow endpoint`() =
        runTest {
            val api =
                FakeFansApi(
                    """
                    {"success":true,"memberList":[
                      {"member_id":4471,"member_name":"nssk","rank":4,"coin":2041,"bio":null},
                      {"member_id":302,"member_name":"酒神","rank":6,"coin":9001,"bio":"喝酒"}]}
                    """.trimIndent(),
                )

            val users = repository(api).following()

            assertEquals("/api/fans/follow", api.requestedPath)
            assertEquals(NodeSeekSite.BASE_URL + "/fans?type=follow", api.requestedReferer)
            assertEquals(listOf(4_471L, 302L), users.map(FollowUser::uid))
            assertEquals(listOf("nssk", "酒神"), users.map(FollowUser::name))
            // No avatar field on the row; the site's own card builds this URL from the id.
            assertEquals("${NodeSeekSite.BASE_URL}/avatar/4471.png", users.first().avatarUrl)
        }

    /** The web route says `?type=fans`; the endpoint says `/fans`. Getting this wrong returns 关注. */
    @Test
    fun `reads 我的粉丝 off the fans endpoint`() =
        runTest {
            val api = FakeFansApi("""{"success":true,"memberList":[{"member_id":7,"member_name":"a"}]}""")

            repository(api).followers()

            assertEquals("/api/fans/fans", api.requestedPath)
            assertEquals(NodeSeekJsonClient.fansListPath(followers = true), api.requestedPath)
        }

    /** An account that really follows nobody. Distinct from the case below, and it must stay distinct. */
    @Test
    fun `reads an empty list as empty rather than as a failure`() =
        runTest {
            assertTrue(repository(FakeFansApi("""{"success":true,"memberList":[]}""")).following().isEmpty())
        }

    /**
     * The whole reason this repository takes a signed-in check.
     *
     * The site answers a signed-out read with the same 200 and the same empty array as the test above.
     * Nothing in the payload distinguishes them, so the distinction has to be made before the request.
     */
    @Test
    fun `refuses to call at all while signed out`() =
        runTest {
            val api = FakeFansApi("""{"success":true,"memberList":[]}""")

            val exception =
                runCatching { repository(api, signedIn = false).following() }.exceptionOrNull()

            assertEquals(NodeSeekError.LoginRequired, (exception as? NodeSeekException)?.error)
            assertEquals("no request should have been sent", null, api.requestedPath)
        }

    /** No `memberList` means the shape moved. Reporting that as "no follows" is what we refuse to do. */
    @Test
    fun `reports a payload without a member list as unparsable`() =
        runTest {
            val exception =
                runCatching {
                    repository(FakeFansApi("""{"success":true,"someOtherKey":{"a":1}}""")).following()
                }.exceptionOrNull()

            assertEquals(NodeSeekError.Unparsable, (exception as? NodeSeekException)?.error)
        }

    @Test
    fun `carries the site's own sentence when a read is refused`() =
        runTest {
            val exception =
                runCatching {
                    repository(FakeFansApi("""{"success":false,"message":"用户未登录"}""")).following()
                }.exceptionOrNull()

            assertEquals("用户未登录", (exception as? NodeSeekException)?.detail)
        }

    @Test
    fun `follows by posting the member id to the add endpoint`() =
        runTest {
            val api = FakeFansApi("""{"success":true}""")

            repository(api).follow(52_425)

            assertEquals("/api/fans/add", api.postedPath)
            assertEquals("""{"followed_member_id":52425}""", api.postedBody)
            assertEquals(NodeSeekSite.BASE_URL + "/space/52425", api.postedReferer)
        }

    @Test
    fun `unfollows through the del endpoint with the same body`() =
        runTest {
            val api = FakeFansApi("""{"success":true}""")

            repository(api).unfollow(52_425)

            assertEquals("/api/fans/del", api.postedPath)
            assertEquals("""{"followed_member_id":52425}""", api.postedBody)
        }

    /** A refusal arrives as a 200 with `success:false`, so status alone would read it as landed. */
    @Test
    fun `treats a success false answer to a write as a refusal`() =
        runTest {
            val exception =
                runCatching {
                    repository(FakeFansApi("""{"success":false,"message":"对方已屏蔽你"}""")).follow(1)
                }.exceptionOrNull()

            assertEquals("对方已屏蔽你", (exception as? NodeSeekException)?.detail)
        }
}

private class FakeFansApi(
    private val body: String,
) : JsonApi {
    var requestedPath: String? = null
        private set
    var requestedReferer: String? = null
        private set
    var postedPath: String? = null
        private set
    var postedBody: String? = null
        private set
    var postedReferer: String? = null
        private set

    override suspend fun getJson(
        path: String,
        referer: String,
    ): String {
        requestedPath = path
        requestedReferer = referer
        return body
    }

    override suspend fun postJson(
        path: String,
        body: String,
        referer: String,
    ): String {
        postedPath = path
        postedBody = body
        postedReferer = referer
        return this.body
    }
}
