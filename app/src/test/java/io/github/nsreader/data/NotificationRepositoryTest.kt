package io.github.nsreader.data

import io.github.nsreader.core.net.JsonApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    @Test
    fun `parses unread counts`() =
        runTest {
            val source = FakeJsonSource(mapOf("/api/notification/unread-count" to """{"reply":2,"atMe":1,"all":3}"""))

            val counts = NotificationRepository(source).unreadCounts()

            assertEquals(2, counts.replies)
            assertEquals(1, counts.mentions)
            assertEquals(3, counts.all)
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
                io.github.nsreader.core.TimeFormat.parseTimestamp("2026-07-26 09:56:03"),
                item.createdAtMillis,
            )
            assertEquals("https://www.nodeseek.com/avatar/12.png", item.avatarUrl)
        }

    /** 全部已读 used to be local-only, so the badge came back on the next refresh. */
    @Test
    fun `mark all read posts to the group's own endpoint`() =
        runTest {
            val source = FakeJsonSource(emptyMap())

            NotificationRepository(source).markAllRead(NotificationCategory.MENTIONS)

            assertEquals(listOf("/api/notification/at-me/markViewed?all=true"), source.postedPaths)
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

private class FakeJsonSource(
    private val responses: Map<String, String>,
) : JsonApi {
    val postedPaths = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(responses[path]) { "No response for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        return """{"success":true}"""
    }
}
