package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.model.hasVoted
import io.github.nodyssey.model.totalCount
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vote endpoints, with the responses captured from the live site on 2026-08-06. */
class VoteRepositoryTest {

    /**
     * The single most important assertion here. Before this account votes, the site sends the options
     * with no `count` key at all — and a zero would render as "nobody picked this" on a vote with
     * dozens of participants.
     */
    @Test
    fun `an unvoted read leaves the counts unknown rather than zero`() =
        runTest {
            val api = FakeVoteApi(gets = mapOf("/api/vote/info/2871" to UNVOTED))

            val vote = NetworkVoteRepository(api).info(2871)

            assertEquals("哪个运营商比较好", vote.title)
            assertEquals(57815L, vote.ownerUid)
            assertTrue(vote.isPublic)
            assertFalse(vote.multiple)
            assertFalse(vote.locked)
            assertEquals(3, vote.items.size)
            assertTrue(vote.items.all { it.count == null })
            assertTrue(vote.items.all { it.voters.isEmpty() })
            assertFalse(vote.hasVoted)
            assertNull(vote.totalCount)
        }

    @Test
    fun `a voted read carries the counts and the first page of voters`() =
        runTest {
            val api = FakeVoteApi(gets = mapOf("/api/vote/info/2871" to VOTED))

            val vote = NetworkVoteRepository(api).info(2871)

            assertTrue(vote.hasVoted)
            assertEquals(40, vote.totalCount)
            assertEquals(listOf(3515L, 10429L), vote.items.first().voters)
            assertTrue(vote.items.first().voted)
        }

    /** Single choice sends an array too — the site's wire format has no scalar form. */
    @Test
    fun `a single choice is submitted as a one-element array`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/voteforitem" to SUCCESS))

            NetworkVoteRepository(api).submit(2871, listOf(13201))

            assertEquals(listOf("POST" to """{"ids":[13201]}"""), api.calls("/api/vote/voteforitem"))
        }

    @Test
    fun `multiple choices go in one request`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/voteforitem" to SUCCESS))

            NetworkVoteRepository(api).submit(2871, listOf(13201, 13203))

            assertEquals(listOf("POST" to """{"ids":[13201,13203]}"""), api.calls("/api/vote/voteforitem"))
        }

    /**
     * A refusal arrives as 403 with the reason in the body. Read as a status code it would become
     * "please sign in", which is exactly wrong for a signed-in reader who simply does not own it.
     */
    @Test
    fun `a refusal surfaces the site's own sentence rather than the status`() =
        runTest {
            val api =
                FakeVoteApi(
                    writes =
                    mapOf(
                        "/api/vote/lock/2871" to
                            JsonPostResponse(
                                403,
                                """{"success":false,"message":"You are not the owner of vote"}""",
                            ),
                    ),
                )

            val thrown =
                assertThrows(SiteException::class.java) {
                    runTestBlocking { NetworkVoteRepository(api).setLocked(2871, true) }
                }

            assertEquals("You are not the owner of vote", thrown.detail)
        }

    @Test
    fun `creating returns the new vote id`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/info" to JsonPostResponse(200, CREATED)))

            val id = NetworkVoteRepository(api).create("标题", multiple = false, isPublic = true, items = listOf("甲", "乙"))

            assertEquals(3001L, id)
            assertEquals(
                listOf("POST" to """{"title":"标题","multiple":false,"isPublic":true,"items":["甲","乙"]}"""),
                api.calls("/api/vote/info"),
            )
        }

    /** Quotes and backslashes in an option would otherwise produce a body the server cannot parse. */
    @Test
    fun `option text with quotes is escaped`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/info" to JsonPostResponse(200, CREATED)))

            NetworkVoteRepository(api).create("""说"是"""", multiple = true, isPublic = false, items = listOf("""a"b"""))

            assertEquals(
                listOf("POST" to """{"title":"说\"是\"","multiple":true,"isPublic":false,"items":["a\"b"]}"""),
                api.calls("/api/vote/info"),
            )
        }

    /** Deletion is a DELETE that carries a body, which is unusual and is what the site expects. */
    @Test
    fun `deleting sends DELETE with a body, not a POST`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/info/2871" to SUCCESS))

            NetworkVoteRepository(api).delete(2871)

            assertEquals(listOf("DELETE" to """{"deleted":true}"""), api.calls("/api/vote/info/2871"))
        }

    /** A moderator-only refusal arrives as HTTP 500 with `status:404` inside. Body first, always. */
    @Test
    fun `a moderator-only refusal wrapped in a 500 still reads as its message`() =
        runTest {
            val api =
                FakeVoteApi(
                    writes =
                    mapOf(
                        "/api/vote/info/2871" to
                            JsonPostResponse(500, """{"message":"Not admin account","status":404,"success":false}"""),
                    ),
                )

            val thrown =
                assertThrows(SiteException::class.java) {
                    runTestBlocking { NetworkVoteRepository(api).delete(2871) }
                }

            assertEquals("Not admin account", thrown.detail)
        }

    @Test
    fun `locking posts the flag`() =
        runTest {
            val api = FakeVoteApi(writes = mapOf("/api/vote/lock/2871" to SUCCESS))

            NetworkVoteRepository(api).setLocked(2871, false)

            assertEquals(listOf("POST" to """{"locked":false}"""), api.calls("/api/vote/lock/2871"))
        }

    @Test
    fun `voters come back as bare uids for the page asked for`() =
        runTest {
            val api =
                FakeVoteApi(
                    gets =
                    mapOf(
                        "/api/vote/voter-of-item?id=13201&page=2" to
                            """{"success":true,"voters":[3515,10429,18533]}""",
                    ),
                )

            val voters = NetworkVoteRepository(api).voters(13201, 2)

            assertEquals(listOf(3515L, 10429L, 18533L), voters)
        }

    private companion object {
        val SUCCESS = JsonPostResponse(200, """{"success":true}""")

        const val CREATED = """{"success":true,"vote":{"id":3001}}"""

        /** Captured from `GET /api/vote/info/2871` while this account had not voted. */
        const val UNVOTED = """
            {"success":true,"vote":{"uid":57815,"title":"哪个运营商比较好","multiple":false,"isPublic":true,
             "id":2871,"locked":false,"items":[
               {"vote_item_id":13201,"vote_id":2871,"text":"移动","voted":false},
               {"vote_item_id":13202,"vote_id":2871,"text":"联通","voted":false},
               {"vote_item_id":13203,"vote_id":2871,"text":"电信","voted":false}]}}
        """

        /** The same vote once voted: the options grow a `count`, and a public one grows `voters`. */
        const val VOTED = """
            {"success":true,"vote":{"uid":57815,"title":"哪个运营商比较好","multiple":false,"isPublic":true,
             "id":2871,"locked":false,"items":[
               {"vote_item_id":13201,"vote_id":2871,"text":"移动","voted":true,"count":12,"voters":[3515,10429]},
               {"vote_item_id":13202,"vote_id":2871,"text":"联通","voted":false,"count":5,"voters":[]},
               {"vote_item_id":13203,"vote_id":2871,"text":"电信","voted":false,"count":23,"voters":[]}]}}
        """
    }
}

/**
 * Bridges JUnit's [assertThrows], which needs a plain lambda, to the suspending calls under test.
 * `runTest` inside `runTest` throws before the assertion can see the real failure.
 */
private fun runTestBlocking(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

private class FakeVoteApi(
    private val gets: Map<String, String> = emptyMap(),
    private val writes: Map<String, JsonPostResponse> = emptyMap(),
) : JsonApi {
    /** Every write, as `path -> (method, body)`, so a test can assert the whole shape at once. */
    private val recorded = mutableListOf<Triple<String, String, String>>()

    fun calls(path: String): List<Pair<String, String>> =
        recorded.filter { it.first == path }.map { it.second to it.third }

    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(gets[path]) { "No read stubbed for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String =
        error("vote writes must go through sendJson so the status stays readable; got POST $path")

    override suspend fun sendJson(
        method: String,
        path: String,
        body: String,
        referer: String,
        extraHeaders: Map<String, String>,
    ): JsonPostResponse {
        recorded += Triple(path, method, body)
        return requireNotNull(writes[path]) { "No write stubbed for $path" }
    }
}
