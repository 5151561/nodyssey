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

    /**
     * Regression for the Galaxy S24 crash: the live endpoint answers with one row per *message*, so
     * a counterparty with two messages appeared twice and the uid-keyed list threw
     * "Key … was already used". Rows must fold into one conversation per counterparty.
     */
    @Test
    fun `folds the flat message list into one conversation per counterparty`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"sender_id":52425,"receiver_id":9,"sender_name":"nssk",
                               "content":"改名的事我问过管理","created_at":"2026-07-26 10:18:00","viewed":0},
                              {"sender_id":9,"receiver_id":52425,"receiver_name":"nssk",
                               "content":"有消息同步我","created_at":"2026-07-26 10:02:00","viewed":1},
                              {"sender_id":52425,"receiver_id":9,"sender_name":"nssk",
                               "content":"UID 显示说是在做了","created_at":"2026-07-26 10:15:00","viewed":0},
                              {"sender_id":1,"receiver_id":9,"sender_name":"系统通知",
                               "content":"您的[评论](/post-1-1)被用户[iwil](/space/4471)投喂鸡腿",
                               "created_at":"2026-07-26 09:12:00","viewed":0}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations = NetworkMessageRepository(api).conversations()

            // One row per counterparty, uids unique, system pinned first despite being older.
            assertEquals(listOf("系统通知", "nssk"), conversations.map(MessageConversation::userName))
            assertEquals(conversations.size, conversations.distinctBy(MessageConversation::uid).size)
            assertTrue(conversations.first().isSystem)
            val nssk = conversations[1]
            // Newest message wins the snippet; only their unread messages count.
            assertEquals("改名的事我问过管理", nssk.snippet)
            assertFalse(nssk.isSnippetMine)
            assertEquals(2, nssk.unreadCount)
        }

    /** Rows that carry only the counterparty's id (no receiver field) still group correctly. */
    @Test
    fun `member-style rows without a receiver id fold by sender`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"member_id":2,"member_name":"nssk","content":"第一条","created_at":"2026-07-26 10:00:00"},
                              {"member_id":2,"member_name":"nssk","content":"第二条","created_at":"2026-07-26 10:05:00"},
                              {"member_id":3,"member_name":"demain","content":"你好","created_at":"2026-07-26 09:00:00"}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations = NetworkMessageRepository(api).conversations()

            assertEquals(2, conversations.size)
            assertEquals("第二条", conversations.first { it.userName == "nssk" }.snippet)
        }

    /** The thread is the flat list sliced to one counterparty — there is no talk endpoint (404ed). */
    @Test
    fun `thread slices the list to one counterparty and derives direction`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"id":2,"sender_id":9,"receiver_id":4471,"receiver_name":"iwil",
                               "content":"那大概什么时候？","created_at":"2026-07-26 09:44:00"},
                              {"id":1,"sender_id":4471,"receiver_id":9,"sender_name":"iwil",
                               "content":"改名的事我问过管理","created_at":"2026-07-26 09:41:00"},
                              {"id":3,"sender_id":7,"receiver_id":9,"sender_name":"demain",
                               "content":"别的会话的消息","created_at":"2026-07-26 09:50:00"}
                             ]}
                            """.trimIndent(),
                    ),
                )

            val thread = NetworkMessageRepository(api).thread(4471)

            assertEquals("iwil", thread.userName)
            // Another counterparty's message never leaks in; sorted oldest first whatever the wire order.
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
                        listPath to
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
