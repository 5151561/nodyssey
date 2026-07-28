package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads here are trimmed captures of the live endpoints, taken from a signed-in device on
 * 2026-07-26 — the field names are the site's, not a guess at them.
 */
class MessageRepositoryTest {
    private val listPath = NodeSeekJsonClient.messageListPath()

    /** Most cases have several conversations, where the rows alone say which uid is ours. */
    private fun repository(
        api: JsonApi,
        ownUid: Long? = null,
        onAsked: () -> Unit = {},
    ) = NetworkMessageRepository(api) {
        onAsked()
        ownUid
    }

    private val threadPath = NodeSeekJsonClient.messageThreadPath(5230)

    /**
     * Regression for the crash this screen shipped with: no row says which party is "the other
     * person", so picking a uid field-by-field selected the sender — us — for every row, and the
     * uid-keyed list threw "Key … was already used".
     */
    @Test
    fun `keys each conversation on the counterparty rather than on us`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"success":true,"msgArray":[
                              {"receiver_id":5230,"sender_id":52425,"max_id":7196941,"content":"test",
                               "created_at":"2026-07-26T14:31:28.000Z","viewed":0,
                               "sender_name":"林地雪原-0062","receiver_name":"系统通知"},
                              {"receiver_id":16874,"sender_id":52425,"max_id":7168848,
                               "content":"@WF5151561","created_at":"2026-07-24T16:24:20.000Z","viewed":1,
                               "sender_name":"林地雪原-0062","receiver_name":"adang"},
                              {"receiver_id":52425,"sender_id":51822,"max_id":6115015,"content":"收到了",
                               "created_at":"2026-05-13T14:21:19.000Z","viewed":1,
                               "sender_name":"ZcZiXs","receiver_name":"林地雪原-0062"}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations = repository(api).conversations()

            assertEquals(3, conversations.size)
            assertEquals(conversations.size, conversations.distinctBy(MessageConversation::uid).size)
            // 系统通知 is pinned above the others even though a newer chat exists.
            assertEquals("系统通知", conversations.first().userName)
            assertTrue(conversations.first().isSystem)
            assertEquals(listOf(5230L, 16874L, 51822L), conversations.map(MessageConversation::uid).sorted())
        }

    /**
     * A brand-new account's inbox holds exactly one conversation — the system one — and both uids
     * then occur equally often, so counting occurrences cannot say which is ours. Getting it
     * backwards keys the row on our own uid, loses the name, and puts every incoming message on the
     * right of the thread as though we had written it.
     */
    @Test
    fun `a single-conversation inbox still identifies the counterparty`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"success":true,"msgArray":[
                              {"receiver_id":52425,"sender_id":5230,"content":"您的评论被投喂鸡腿",
                               "created_at":"2026-07-26T14:31:28.000Z","viewed":0,
                               "sender_name":"系统通知","receiver_name":"林地雪原-0062"}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversation =
                repository(api, ownUid = 52425L).conversations().single()

            assertEquals(5230L, conversation.uid)
            assertEquals("系统通知", conversation.userName)
            assertTrue(conversation.isSystem)
            assertFalse(conversation.isSnippetMine)
            assertEquals(1, conversation.unreadCount)
        }

    /** The uid lookup is only worth a request when the rows cannot answer on their own. */
    @Test
    fun `several conversations identify us without asking who we are`() =
        runTest {
            var asked = 0
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"receiver_id":16874,"sender_id":52425,"content":"我发的",
                               "created_at":"2026-07-24T16:24:20.000Z","viewed":1},
                              {"receiver_id":52425,"sender_id":51822,"content":"他发的",
                               "created_at":"2026-05-13T14:21:19.000Z","viewed":1}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations =
                repository(api, onAsked = { asked++ }).conversations()

            assertEquals(0, asked)
            assertEquals(listOf(16874L, 51822L), conversations.map(MessageConversation::uid).sorted())
        }

    @Test
    fun `marks the snippet as ours only when we sent the last message`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        listPath to
                            """
                            {"msgArray":[
                              {"receiver_id":16874,"sender_id":52425,"content":"我发的",
                               "created_at":"2026-07-24T16:24:20.000Z","viewed":1,
                               "sender_name":"我","receiver_name":"adang"},
                              {"receiver_id":52425,"sender_id":51822,"content":"他发的",
                               "created_at":"2026-05-13T14:21:19.000Z","viewed":0,
                               "sender_name":"ZcZiXs","receiver_name":"我"}
                            ]}
                            """.trimIndent(),
                    ),
                )

            val conversations = repository(api).conversations().associateBy { it.userName }

            assertTrue(conversations.getValue("adang").isSnippetMine)
            assertFalse(conversations.getValue("ZcZiXs").isSnippetMine)
            // Unread counts only their messages, so the one we sent never shows a badge.
            assertEquals(0, conversations.getValue("adang").unreadCount)
            assertEquals(1, conversations.getValue("ZcZiXs").unreadCount)
        }

    /** The full history lives behind `with/{uid}`; the list only ever holds the latest message. */
    @Test
    fun `thread reads the whole history and the talkTo header`() =
        runTest {
            val api =
                FakeJsonApi(
                    mapOf(
                        threadPath to
                            """
                            {"success":true,
                             "talkTo":{"member_id":5230,"member_name":"系统通知","rank":3},
                             "msgArray":[
                               {"id":7169823,"receiver_id":52425,"sender_id":5230,"viewed":1,
                                "content":"您的帖子被投喂鸡腿","created_at":"2026-07-24T18:15:47.000Z",
                                "is_markdown":1},
                               {"id":7169651,"receiver_id":52425,"sender_id":5230,"viewed":1,
                                "content":"您的评论被投喂鸡腿","created_at":"2026-07-24T17:46:45.000Z",
                                "is_markdown":1},
                               {"id":7196941,"receiver_id":5230,"sender_id":52425,"viewed":0,
                                "content":"test","created_at":"2026-07-26T14:31:28.000Z","is_markdown":0}
                             ]}
                            """.trimIndent(),
                    ),
                )

            val thread = repository(api).thread(5230)

            assertEquals("系统通知", thread.userName)
            assertEquals(3, thread.level)
            // Oldest first, whatever order the endpoint used.
            assertEquals(listOf("7169651", "7169823", "7196941"), thread.messages.map(DirectMessage::id))
            assertFalse(thread.messages[0].isMine)
            assertTrue(thread.messages.last().isMine)
            // Rendering follows the message's own flag, not the composer's switch.
            assertTrue(thread.messages[0].isMarkdown)
            assertFalse(thread.messages.last().isMarkdown)
        }

    /** The recipient field is camel-cased here and nowhere else on this API. */
    @Test
    fun `send posts receiverUid`() =
        runTest {
            val api =
                FakeJsonApi(
                    posts = mapOf(NodeSeekJsonClient.PATH_MESSAGE_SEND to """{"success":true}"""),
                )

            repository(api).send(5230, "行", markdown = true)

            val body = api.postedBodies.single()
            assertTrue(body, body.contains("\"receiverUid\":5230"))
            assertTrue(body, body.contains("\"markdown\":true"))
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
                runCatching { repository(api).send(4471, "在吗", markdown = false) }
                    .exceptionOrNull() as NodeSeekException

            assertEquals(NodeSeekError.Unknown, failure.error)
            assertEquals("对方已屏蔽你", failure.message)
        }

    @Test
    fun `mark all read hits the site's own all=true endpoint`() =
        runTest {
            val api =
                FakeJsonApi(
                    posts = mapOf(NodeSeekJsonClient.PATH_MESSAGE_MARK_VIEWED_ALL to """{"success":true}"""),
                )

            repository(api).markAllRead()

            assertEquals(listOf(NodeSeekJsonClient.PATH_MESSAGE_MARK_VIEWED_ALL), api.postedPaths)
        }
}

private class FakeJsonApi(
    private val responses: Map<String, String> = emptyMap(),
    private val posts: Map<String, String> = emptyMap(),
) : JsonApi {
    val postedBodies = mutableListOf<String>()
    val postedPaths = mutableListOf<String>()

    override suspend fun getJson(path: String, referer: String): String =
        requireNotNull(responses[path]) { "No response for $path" }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        postedPaths += path
        postedBodies += body
        return requireNotNull(posts[path]) { "No response for $path" }
    }
}
