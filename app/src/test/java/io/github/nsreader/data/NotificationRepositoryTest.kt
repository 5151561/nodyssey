package io.github.nsreader.data

import io.github.nsreader.core.net.JsonSource
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
) : JsonSource {
    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(responses[path]) { "No response for $path" }
}
