package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    @Test
    fun `parses unread counts`() =
        runTest {
            val source = FakeJsonSource(mapOf(UNREAD_COUNT to """{"reply":2,"atMe":1,"all":3}"""))
            val repository = NotificationRepository(source)

            val counts = repository.refreshCounts()

            assertEquals(2, counts.replies)
            assertEquals(1, counts.mentions)
            assertEquals(3, counts.all)
            // The badge everyone else reads is the same value, not a copy the caller has to pass on.
            assertEquals(counts, repository.counts.value)
        }

    @Test
    fun `parses reply list and floor target`() =
        runTest {
            val path = "/api/notification/reply-to-me/list?page=1"
            val source =
                FakeJsonSource(
                    mapOf(
                        path to
                            """
                            {"data":{"replyList":[{
                              "comment_id":42,
                              "post_id":703863,
                              "floor_id":12,
                              "commenter_name":"nssk",
                              "content":"还没有这个功能",
                              "post_title":"求教如何改用户名",
                              "created_at":"5分钟前",
                              "viewed":0
                            }]}}
                            """.trimIndent(),
                    ),
                )

            val item = NotificationRepository(source).notifications(NotificationCategory.REPLIES).single()

            assertEquals(703863L, item.postId)
            assertEquals("#12", item.floor)
            assertEquals("nssk", item.actorName)
            assertTrue(item.isUnread)
        }

    /** §1.5: 私信 is a conversation list, so it has no notification endpoint of its own. */
    @Test
    fun `the message group has no notification rows`() =
        runTest {
            val items =
                NotificationRepository(FakeJsonSource(emptyMap()))
                    .notifications(NotificationCategory.MESSAGES)

            assertTrue(items.isEmpty())
        }

    @Test
    fun `keeps both the parsed instant and the server wording`() =
        runTest {
            val path = "/api/notification/at-me/list?page=1"
            val source =
                FakeJsonSource(
                    mapOf(
                        path to
                            """
                            {"atList":[{"id":1,"member_id":12,"username":"nssk",
                              "post_title":"求教如何改用户名","created_at":"2026-07-26 09:56:03"}]}
                            """.trimIndent(),
                    ),
                )

            val item = NotificationRepository(source).notifications(NotificationCategory.MENTIONS).single()

            assertEquals("2026-07-26 09:56:03", item.createdAtText)
            assertEquals(
                io.github.plaza.core.TimeFormat.parseTimestamp("2026-07-26 09:56:03"),
                item.createdAtMillis,
            )
            assertEquals("https://www.nodeseek.com/avatar/12.png", item.avatarUrl)
        }

    /** 全部已读 used to be local-only, so the badge came back on the next refresh. */
    @Test
    fun `mark all read posts to the group's own endpoint`() =
        runTest {
            val source = FakeJsonSource(mapOf(UNREAD_COUNT to """{"reply":0,"atMe":0}"""))

            NotificationRepository(source).markAllRead(NotificationCategory.MENTIONS)

            assertEquals(listOf("/api/notification/at-me/markViewed?all=true"), source.postedPaths)
        }

    /**
     * Bug: opening a notification greyed the row out and left the badge alone, so the number that
     * sent the user there outlived being acted on.
     */
    @Test
    fun `opening one notification clears it server-side and re-reads the badge`() =
        runTest {
            val source = FakeJsonSource(mapOf(UNREAD_COUNT to """{"reply":0,"atMe":1}"""))
            val repository = NotificationRepository(source)

            repository.markViewed(
                listOf(NotificationSource(NotificationCategory.MENTIONS, viewedId = 42L, isUnread = true)),
            )

            assertEquals(listOf("/api/notification/at-me/markViewed"), source.postedPaths)
            assertEquals(listOf("""{"atMe":[42]}"""), source.postedBodies)
            // …and the answer to `unread-count` is what the badge ends up showing.
            assertEquals(1, repository.counts.value.mentions)
        }

    /** Each group names its id array differently; there is no shared `ids` spelling. */
    @Test
    fun `each group posts its own field name`() =
        runTest {
            assertEquals("""{"atMe":[1]}""", markViewedBody(NotificationCategory.MENTIONS, listOf(1L)))
            assertEquals("""{"replys":[1]}""", markViewedBody(NotificationCategory.REPLIES, listOf(1L)))
            assertEquals("""{"messages":[1]}""", markViewedBody(NotificationCategory.MESSAGES, listOf(1L)))
        }

    /** The row id `markViewed` wants is `id`, which on the reply endpoint is not `comment_id`. */
    @Test
    fun `keeps the row id the mark-viewed call needs`() =
        runTest {
            val path = "/api/notification/reply-to-me/list?page=1"
            val source =
                FakeJsonSource(mapOf(path to """{"replyList":[{"id":7,"comment_id":42,"post_id":1}]}"""))

            val item = NotificationRepository(source).notifications(NotificationCategory.REPLIES).single()

            assertEquals("42", item.id)
            assertEquals(42L, item.commentId)
            assertEquals(7L, item.sources.single().viewedId)
        }

    /**
     * The merge: one comment, both groups, one row that clears both.
     *
     * This is what the 通知 tab is: the site's 回复 button writes `@name #7` into every reply it
     * sends, so the same comment raises a row in each list and reading them separately means reading
     * it twice.
     */
    @Test
    fun `a comment in both groups becomes one row carrying both`() =
        runTest {
            val source =
                FakeJsonSource(
                    mapOf(
                        REPLY_LIST to
                            """{"replyList":[{"id":7,"comment_id":42,"post_id":1,"floor_id":3,
                               "content":"还没有这个功能","created_at":"2026-07-26 09:56:03","viewed":0}]}""",
                        AT_LIST to
                            """{"atList":[{"id":1,"comment_id":42,"post_id":1,
                               "created_at":"2026-07-26 09:56:03","viewed":0}]}""",
                        UNREAD_COUNT to """{"reply":1,"atMe":1}""",
                    ),
                )
            val repository = NotificationRepository(source)
            repository.refreshCounts()

            val item = repository.interactions().single()

            assertEquals("42", item.id)
            assertTrue(item.isReply)
            assertTrue(item.isMention)
            // The reply row is the one kept, so the floor — and therefore the tap target — survives.
            assertEquals("#3", item.floor)
            assertEquals(
                listOf(7L, 1L),
                item.sources.map { it.viewedId },
            )
            // Two unread rows, one thing to read: the badge counts the row, not the rows.
            assertEquals(1, repository.counts.value.interactions)
        }

    /** Two different comments are two rows, however alike the endpoints' own row ids happen to be. */
    @Test
    fun `rows naming different comments stay apart`() =
        runTest {
            val source =
                FakeJsonSource(
                    mapOf(
                        REPLY_LIST to
                            """{"replyList":[{"id":1,"comment_id":42,"post_id":1,
                               "created_at":"2026-07-26 09:56:03"}]}""",
                        AT_LIST to
                            """{"atList":[{"id":1,"comment_id":43,"post_id":1,
                               "created_at":"2026-07-26 10:56:03"}]}""",
                    ),
                )

            val items = NotificationRepository(source).interactions()

            assertEquals(listOf("43", "42"), items.map { it.id })
        }

    /** A row that names no comment cannot be identified, so it is shown rather than folded blind. */
    @Test
    fun `rows with nothing to identify them are left alone`() =
        runTest {
            val source =
                FakeJsonSource(
                    mapOf(
                        REPLY_LIST to """{"replyList":[{"id":1,"post_id":1}]}""",
                        AT_LIST to """{"atList":[{"id":1,"post_id":1}]}""",
                    ),
                )

            val items = NotificationRepository(source).interactions()

            assertEquals(2, items.size)
            // …and their display keys still differ, or the list they share would collide on them.
            assertEquals(2, items.map { it.id }.toSet().size)
        }

    /** The badge has to move in the same frame as the row, not a round trip later. */
    @Test
    fun `noting a read drops the badge before the network answers`() =
        runTest {
            val source = FakeJsonSource(mapOf(UNREAD_COUNT to """{"reply":0,"atMe":3,"message":2}"""))
            val repository = NotificationRepository(source)
            repository.refreshCounts()

            repository.noteRead(NotificationCategory.MESSAGES, 2)

            assertEquals(0, repository.counts.value.messages)
            assertEquals(3, repository.counts.value.mentions)
        }

    @Test
    fun `viewed notification is not unread`() =
        runTest {
            val path = "/api/notification/at-me/list?page=1"
            val source = FakeJsonSource(mapOf(path to """{"list":[{"id":1,"viewed":true}]}"""))

            val item = NotificationRepository(source).notifications(NotificationCategory.MENTIONS).single()

            assertFalse(item.isUnread)
        }
}

private const val UNREAD_COUNT = "/api/notification/unread-count"
private const val REPLY_LIST = "/api/notification/reply-to-me/list?page=1"
private const val AT_LIST = "/api/notification/at-me/list?page=1"

private class FakeJsonSource(
    private val responses: Map<String, String>,
) : JsonApi {
    val postedPaths = mutableListOf<String>()
    val postedBodies = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(responses[path]) { "No response for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        postedBodies += body
        return """{"success":true}"""
    }
}
