package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three one-way marks, against the contract read off the site's own bundle (2026-08-01):
 * `{commentId, action:"add"}` in, `{success, current, coin, message}` back.
 */
class PostReactionTest {

    private fun writer(
        posts: Map<String, String> = emptyMap(),
        gets: Map<String, String> = emptyMap(),
    ): Pair<PostReactionWriter, FakeReactionApi> {
        val api = FakeReactionApi(posts = posts, gets = gets)
        return PostReactionWriter(api) to api
    }

    /**
     * The mapping is the part worth pinning down. 投喂鸡腿 is the site's `like` and 点赞 is its
     * `upvote`; swapping them would spend the reader's chicken legs on the button that is supposed
     * to be free, and nothing else in the app would notice.
     */
    @Test
    fun `sends each action to the endpoint the site uses for it`() =
        runTest {
            val answers =
                mapOf(
                    "/api/statistics/upvote" to """{"success":true,"current":4}""",
                    "/api/statistics/like" to """{"success":true,"current":2,"coin":406}""",
                    "/api/statistics/dislike" to """{"success":true,"current":1,"coin":404}""",
                )
            val (writer, api) = writer(posts = answers)

            writer.react(postId = 1L, commentId = 99L, action = ReactionAction.Upvote)
            writer.react(postId = 1L, commentId = 99L, action = ReactionAction.ChickenLeg)
            writer.react(postId = 1L, commentId = 99L, action = ReactionAction.Dislike)

            assertEquals(
                listOf("/api/statistics/upvote", "/api/statistics/like", "/api/statistics/dislike"),
                api.postedPaths,
            )
            assertTrue(api.postedBodies.all { it == """{"commentId":99,"action":"add"}""" })
        }

    @Test
    fun `reads the new tally and the remaining chicken legs off the answer`() =
        runTest {
            val (writer, _) =
                writer(posts = mapOf("/api/statistics/like" to """{"success":true,"current":7,"coin":405}"""))

            val outcome = writer.react(postId = 1L, commentId = 99L, action = ReactionAction.ChickenLeg)

            assertEquals(7, outcome.current)
            assertEquals(405, outcome.coin)
        }

    /**
     * A refusal is the site's sentence, not a status code: "已经进行过加鸡腿操作" and "鸡腿不足" are the
     * two the reader will actually hit, and neither survives being rendered as an HTTP error.
     */
    @Test
    fun `carries the site's own refusal through`() =
        runTest {
            val (writer, _) =
                writer(
                    posts = mapOf("/api/statistics/like" to """{"success":false,"message":"鸡腿不足"}"""),
                )

            val thrown =
                assertThrows(NodeSeekException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        writer.react(postId = 1L, commentId = 99L, action = ReactionAction.ChickenLeg)
                    }
                }
            assertEquals("鸡腿不足", thrown.detail)
        }

    /**
     * The mark landed; only the number is missing. Reporting failure here would tell the reader to
     * spend a second chicken leg on a floor they have already paid for.
     */
    @Test
    fun `treats a success without a count as landed`() =
        runTest {
            val (writer, _) = writer(posts = mapOf("/api/statistics/upvote" to """{"success":true}"""))

            val outcome = writer.react(postId = 1L, commentId = 99L, action = ReactionAction.Upvote)

            assertNull(outcome.coin)
            assertEquals(
                PostReactions(upvoteCount = 6, upvoted = true),
                PostReactions(upvoteCount = 5).applying(ReactionAction.Upvote, outcome),
            )
        }

    @Test
    fun `folds the answer into the tally the site changed and leaves the others alone`() {
        val before = PostReactions(likeCount = 2, dislikeCount = 1, upvoteCount = 9, upvoted = true)

        val after = before.applying(ReactionAction.ChickenLeg, ReactionOutcome(current = 3, coin = 400))

        assertEquals(3, after.likeCount)
        assertTrue(after.liked)
        assertEquals(1, after.dislikeCount)
        assertEquals(9, after.upvoteCount)
        assertTrue(after.upvoted)
    }

    @Test
    fun `reads today's free allowance`() =
        runTest {
            val (writer, _) =
                writer(
                    gets =
                    mapOf("/api/progress/today?scope=freelike" to """{"maxFreeLike":5,"freeLikeUsed":2}"""),
                )

            assertEquals(3, writer.freeChickenLegs()?.remaining)
        }

    /** A quota lookup that fails must not stand between the reader and a chicken leg they meant to spend. */
    @Test
    fun `reports no allowance rather than failing when the site will not say`() =
        runTest {
            val (writer, _) = writer(gets = mapOf("/api/progress/today?scope=freelike" to "<html>"))

            assertNull(writer.freeChickenLegs())
        }
}

private class FakeReactionApi(
    private val posts: Map<String, String>,
    private val gets: Map<String, String>,
) : JsonApi {
    val postedPaths = mutableListOf<String>()
    val postedBodies = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String = requireNotNull(gets[path]) { "No response for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        postedBodies += body
        return requireNotNull(posts[path]) { "No response for $path" }
    }
}
