package io.github.nsreader.data

import io.github.nsreader.core.net.JsonApi
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {
    private val listPath = NodeSeekJsonClient.messageListPath()
    private val threadPath = NodeSeekJsonClient.messageThreadPath(4471)

    @Test
    fun `pins the system conversation above newer chats`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"member_id":2,"member_name":"nssk","sender_id":2,
                               "content":"改名的事我问过管理","created_at":"2026-07-26 10:18:00","unread":2},
                              {"member_id":1,"member_name":"系统通知",
                               "content":"您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿",
                               "created_at":"2026-07-26 09:12:00","unread":1}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations = NetworkMessageRepository(api).conversations()

            assertEquals(listOf("系统通知", "nssk"), conversations.map(MessageConversation::userName))
            assertTrue(conversations.first().isSystem)
            assertEquals(2, conversations[1].unreadCount)
        }

    /** A message whose sender is the person we are talking to is theirs; anything else is ours. */
    @Test
    fun `derives direction from the sender without knowing our own uid`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        threadPath to
                            """
                            {"member":{"member_name":"iwil","rank":4},
                             "msgArray":[
                               {"id":2,"sender_id":9,"content":"那大概什么时候？",
                                "created_at":"2026-07-26 09:44:00"},
                               {"id":1,"sender_id":4471,"content":"改名的事我问过管理",
                                "created_at":"2026-07-26 09:41:00"}
                             ]}
                            """.trimIndent(),
                    ),
                )

            val thread = NetworkMessageRepository(api).thread(4471)

            assertEquals("iwil", thread.userName)
            assertEquals(4, thread.level)
            // Sorted oldest first, whatever order the endpoint used.
            assertEquals(listOf("1", "2"), thread.messages.map(DirectMessage::id))
            assertFalse(thread.messages[0].isMine)
            assertTrue(thread.messages[1].isMine)
        }

    @Test
    fun `an updated timestamp marks a message as edited`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        threadPath to
                            """
                            {"msgArray":[{"id":1,"sender_id":4471,"content":"顶一下",
                              "created_at":"2026-07-26 09:41:00","updated_at":"2026-07-26 09:52:00"}]}
                            """.trimIndent(),
                    ),
                )

            assertTrue(NetworkMessageRepository(api).thread(4471).messages.single().isEdited)
        }

    @Test
    fun `send returns the accepted message`() =
        runTest {
            val api =
                FakeJsonApi(
                    posts =
                    mapOf(
                        NodeSeekJsonClient.PATH_MESSAGE_SEND to
                            """{"success":true,"data":{"id":7,"sender_id":9,"content":"行"}}""",
                    ),
                )

            val sent = NetworkMessageRepository(api).send(4471, "行", markdown = true)

            assertEquals("7", sent?.id)
            assertTrue(sent!!.isMine)
            assertTrue(api.postedBodies.single().contains("\"receiver_id\":4471"))
        }

    @Test
    fun `a rejected send carries the reason`() =
        runTest {
            val api =
                FakeJsonApi(
                    posts =
                    mapOf(
                        NodeSeekJsonClient.PATH_MESSAGE_SEND to
                            """{"success":false,"message":"对方已屏蔽你"}""",
                    ),
                )

            val failure =
                runCatching { NetworkMessageRepository(api).send(4471, "在吗", markdown = false) }
                    .exceptionOrNull() as NodeSeekException

            assertEquals(NodeSeekError.Unknown, failure.error)
            assertEquals("对方已屏蔽你", failure.message)
        }
}

private class FakeJsonApi(
    private val responses: Map<String, String> = emptyMap(),
    private val posts: Map<String, String> = emptyMap(),
) : JsonApi {
    val postedBodies = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(responses[path]) { "No response for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedBodies += body
        return requireNotNull(posts[path]) { "No response for $path" }
    }
}
