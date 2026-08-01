package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.model.PostReactions
import io.github.nodyssey.model.ReactionAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a landed reaction does to the cached thread.
 *
 * The screen reads Room and nothing else, so a mark that the site accepted but that never reached
 * the database would show as having done nothing — and the reader, having no way to tell, would
 * spend a second chicken leg on it.
 */
@RunWith(RobolectricTestRunner::class)
class PostReactionWriteBackTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(api: JsonApi) = OfflineFirstPostRepository(database, remote, clock, PostReactionWriter(api))

    private fun api(answer: String) =
        FakeReactionJsonApi(
            posts =
            mapOf(
                "/api/statistics/like" to answer,
                "/api/statistics/upvote" to answer,
                "/api/statistics/dislike" to answer,
            ),
        )

    @Test
    fun `writes the new tally onto the floor it was spent on`() =
        runTest {
            val repository = repository(api("""{"success":true,"current":4,"coin":399}"""))
            repository.refreshThread(postId = 42, page = 1)
            val before = requireNotNull(repository.thread(42).first())
            val target = requireNotNull(before.comments[0].commentId)

            repository.react(postId = 42, commentId = target, action = ReactionAction.ChickenLeg)

            val after = requireNotNull(repository.thread(42).first())
            val reactions = requireNotNull(after.comments[0].reactions)
            assertEquals(4, reactions.likeCount)
            assertTrue(reactions.liked)
            // The tallies the site did not speak about stay where they were.
            assertEquals(0, reactions.upvoteCount)
            assertFalse(reactions.upvoted)
        }

    @Test
    fun `leaves the other floors alone`() =
        runTest {
            val repository = repository(api("""{"success":true,"current":4}"""))
            repository.refreshThread(postId = 42, page = 1)
            val before = requireNotNull(repository.thread(42).first())

            repository.react(
                postId = 42,
                commentId = requireNotNull(before.comments[0].commentId),
                action = ReactionAction.ChickenLeg,
            )

            val after = requireNotNull(repository.thread(42).first())
            assertNull(after.comments[1].reactions)
            assertNull(requireNotNull(after.body).reactions)
        }

    /** The opening post lives on a different row than the comments, and is reached by a different branch. */
    @Test
    fun `writes onto the opening post too`() =
        runTest {
            val repository = repository(api("""{"success":true,"current":1}"""))
            repository.refreshThread(postId = 42, page = 1)
            val body = requireNotNull(requireNotNull(repository.thread(42).first()).body)

            repository.react(
                postId = 42,
                commentId = requireNotNull(body.commentId),
                action = ReactionAction.Upvote,
            )

            val after = requireNotNull(requireNotNull(repository.thread(42).first()).body)
            assertEquals(1, requireNotNull(after.reactions).upvoteCount)
            assertTrue(requireNotNull(after.reactions).upvoted)
            // Content is not collateral: the write-back replaces the tallies, not the floor.
            assertEquals(body.nodes, after.nodes)
        }

    /**
     * Nothing is applied optimistically. A refused reaction that still moved the count on screen
     * would be the app telling the reader they own something they do not.
     */
    @Test
    fun `leaves the thread untouched when the site refuses`() =
        runTest {
            val repository = repository(api("""{"success":false,"message":"鸡腿不足"}"""))
            repository.refreshThread(postId = 42, page = 1)
            val target = requireNotNull(requireNotNull(repository.thread(42).first()).comments[0].commentId)

            assertThrows(NodeSeekException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repository.react(postId = 42, commentId = target, action = ReactionAction.ChickenLeg)
                }
            }

            assertNull(requireNotNull(repository.thread(42).first()).comments[0].reactions)
        }

    /** A reaction is not a read: it must not make a stale thread look freshly fetched. */
    @Test
    fun `does not renew the thread's freshness`() =
        runTest {
            val repository = repository(api("""{"success":true,"current":1}"""))
            repository.refreshThread(postId = 42, page = 1)
            val target = requireNotNull(requireNotNull(repository.thread(42).first()).comments[0].commentId)
            clock.advanceBy(OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS + 1)
            assertFalse(repository.isThreadFresh(42))

            repository.react(postId = 42, commentId = target, action = ReactionAction.Upvote)

            assertFalse(repository.isThreadFresh(42))
        }

    /** A build with no writer refuses outright rather than reporting a mark it never sent. */
    @Test
    fun `refuses when no writer is wired`() =
        runTest {
            val repository = OfflineFirstPostRepository(database, remote, clock)
            repository.refreshThread(postId = 42, page = 1)

            assertThrows(NodeSeekException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repository.react(postId = 42, commentId = 1L, action = ReactionAction.Upvote)
                }
            }
            assertNull(repository.freeChickenLegs())
        }

    @Test
    fun `folds onto tallies the page already carried`() =
        runTest {
            val repository = repository(api("""{"success":true,"current":6}"""))
            remote.detailResult = { postId, page ->
                val base = FakePostRemoteDataSource.detail(postId, page)
                base.copy(
                    comments =
                    base.comments.mapIndexed { index, comment ->
                        if (index == 0) {
                            comment.copy(reactions = PostReactions(upvoteCount = 5, likeCount = 2, liked = true))
                        } else {
                            comment
                        }
                    },
                )
            }
            repository.refreshThread(postId = 42, page = 1)
            val target = requireNotNull(requireNotNull(repository.thread(42).first()).comments[0].commentId)

            repository.react(postId = 42, commentId = target, action = ReactionAction.Upvote)

            val reactions = requireNotNull(requireNotNull(repository.thread(42).first()).comments[0].reactions)
            assertEquals(6, reactions.upvoteCount)
            assertTrue(reactions.upvoted)
            // The chicken leg this reader had already spent is still spent.
            assertEquals(2, reactions.likeCount)
            assertTrue(reactions.liked)
        }
}

private class FakeReactionJsonApi(
    private val posts: Map<String, String>,
) : JsonApi {
    override suspend fun getJson(path: String, referer: String): String = error("unexpected read of $path")

    override suspend fun postJson(path: String, body: String, referer: String): String =
        requireNotNull(posts[path]) { "No response for $path" }
}
