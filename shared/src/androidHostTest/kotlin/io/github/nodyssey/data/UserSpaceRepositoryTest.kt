package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.composer.PostPermission
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These endpoints are the site's own XHRs, not a published API, so the repository reads fields by
 * candidate name. What the tests pin down is the tolerance itself: a renamed key must not empty a tab,
 * and a payload we cannot recognise at all must be reported rather than shown as "nothing here".
 */
class UserSpaceRepositoryTest {
    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    @Test
    fun `reads topics wrapped in a data envelope`() =
        runTest {
            val source =
                FakeSpaceJsonSource(
                    """
                    {"success":true,"data":{"discussions":[
                      {"post_id":286417,"title":"第一次用 nftables 做端口转发","category":"tech",
                       "category_title":"技术","comments":12,"views":843,"created_at_str":"3天前"}
                    ],"totalPage":1}}
                    """.trimIndent(),
                )
            val repository = NetworkUserSpaceRepository(source, dispatchers)

            val page = repository.topics(uid = 12043, page = 1)

            assertEquals(NodeSeekJsonClient.discussionListPath(12043, 1), source.requestedPath)
            assertEquals(1, page.items.size)
            val topic = page.items.first()
            assertEquals(286417L, topic.postId)
            assertEquals("第一次用 nftables 做端口转发", topic.title)
            assertEquals("技术", topic.categoryTitle)
            assertEquals("tech", topic.categorySlug)
            assertEquals(12, topic.commentCount)
            assertEquals(843, topic.viewCount)
            assertFalse(page.hasNextPage)
        }

    /**
     * `rank` is 阅读权限, and the live payload carries little else: a topic row read off
     * `/api/content/list-discussions` on 2026-08-31 was `{"rank":1,"title":"…","post_id":903281}`
     * and nothing more. 0 is 公开, 255 is 私有, in between is the level a reader needs.
     */
    @Test
    fun `reads the reading permission a topic row carries`() =
        runTest {
            val source =
                FakeSpaceJsonSource(
                    """
                    {"success":true,"discussions":[
                      {"rank":0,"title":"公开的","post_id":1},
                      {"rank":3,"title":"三级可见","post_id":2},
                      {"rank":255,"title":"仅自己","post_id":3},
                      {"title":"没有这个字段","post_id":4}
                    ]}
                    """.trimIndent(),
                )

            val topics = NetworkUserSpaceRepository(source, dispatchers).topics(21596, 1).items

            assertEquals(PostPermission.PUBLIC, topics[0].permission)
            assertEquals(3, topics[1].permission.requiredLevel)
            assertEquals(PostPermission.PRIVATE, topics[2].permission)
            assertEquals(null, topics[2].permission.requiredLevel)
            // A row with no `rank` must read as unrestricted rather than as a lock we invented.
            assertEquals(PostPermission.PUBLIC, topics[3].permission)
        }

    /** `pid` / `nComment` / `click` are the other spellings the site uses for the same three fields. */
    @Test
    fun `accepts the alternative field names and string ids`() =
        runTest {
            val source =
                FakeSpaceJsonSource(
                    """
                    {"postList":[
                      {"pid":"9001","subject":"标题","nComment":"7","click":"120"}
                    ]}
                    """.trimIndent(),
                )

            val topic = NetworkUserSpaceRepository(source, dispatchers).topics(1, 1).items.single()

            assertEquals(9001L, topic.postId)
            assertEquals("标题", topic.title)
            assertEquals(7, topic.commentCount)
            assertEquals(120, topic.viewCount)
        }

    @Test
    fun `flattens comment markup into a one line excerpt`() =
        runTest {
            val source =
                FakeSpaceJsonSource(
                    """
                    {"comments":[
                      {"post_id":700,"comment_id":866042,"post_title":"求教如何改用户名",
                       "content":"<p>还没有这个功能，其实可以考虑<strong>花费星辰</strong></p>",
                       "created_at_str":"19小时前"}
                    ]}
                    """.trimIndent(),
                )

            val comment = NetworkUserSpaceRepository(source, dispatchers).comments(1, 1).items.single()

            assertEquals(700L, comment.postId)
            assertEquals(866042L, comment.commentId)
            assertEquals("还没有这个功能，其实可以考虑花费星辰", comment.excerpt)
        }

    /** An empty list is a real answer and must not look like a failure. */
    @Test
    fun `treats an empty list as an empty tab`() =
        runTest {
            val page =
                NetworkUserSpaceRepository(FakeSpaceJsonSource("""{"discussions":[]}"""), dispatchers)
                    .topics(1, 1)

            assertTrue(page.items.isEmpty())
            assertFalse(page.hasNextPage)
        }

    @Test
    fun `reports an unrecognisable payload instead of showing it as empty`() =
        runTest {
            val exception =
                runCatching {
                    NetworkUserSpaceRepository(FakeSpaceJsonSource("""{"success":false}"""), dispatchers)
                        .collections(1)
                }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (exception as? SiteException)?.error)
        }

    /** With no paging metadata, a full-looking page is assumed to have a successor. */
    @Test
    fun `offers a next page when the response fills one`() =
        runTest {
            val rows = (1..20).joinToString(",") { """{"post_id":$it,"title":"t$it"}""" }
            val page =
                NetworkUserSpaceRepository(FakeSpaceJsonSource("""{"list":[$rows]}"""), dispatchers)
                    .collections(1)

            assertEquals(20, page.items.size)
            assertTrue(page.hasNextPage)
        }

    /**
     * Regression: guessing "no more pages" from a row-count threshold silently truncated every list
     * whose real page size was below the guess. Any non-empty page without metadata offers more.
     */
    @Test
    fun `offers a next page even when a metadata-less page is short`() =
        runTest {
            val rows = (1..3).joinToString(",") { """{"post_id":$it,"title":"t$it"}""" }
            val page =
                NetworkUserSpaceRepository(FakeSpaceJsonSource("""{"list":[$rows]}"""), dispatchers)
                    .collections(1)

            assertEquals(3, page.items.size)
            assertTrue(page.hasNextPage)
        }

    /**
     * Regression: with several unrecognised arrays the walk must not gamble on one — handing back a
     * sibling block (badges, a pager) would render a confident empty tab from the wrong array.
     */
    @Test
    fun `refuses to choose between several unrecognised arrays`() =
        runTest {
            val payload =
                """
                {"badges":[{"badge_id":1,"label":"元老"}],
                 "rows":[{"post_id":1,"title":"t1"}]}
                """.trimIndent()
            val exception =
                runCatching {
                    NetworkUserSpaceRepository(FakeSpaceJsonSource(payload), dispatchers).collections(1)
                }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (exception as? SiteException)?.error)
        }
}

private class FakeSpaceJsonSource(
    private val response: String,
) : JsonSource {
    var requestedPath: String? = null
        private set

    override suspend fun getJson(path: String, referer: String): String {
        requestedPath = path
        return response
    }
}
