package io.github.bbs1.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decode layer against responses shaped like the plugin actually sends them — field names and
 * nesting mirror plaza-bbs1org's payload builders, not a convenient invention.
 */
class ApiPayloadTest {

    @Test
    fun `topics page decodes the plugin's list item shape`() {
        val raw = """
            {"ok":1,"topics":[{
                "id":7,"forum_id":2,"forum_name":"水区","title":"你好","highlight_style":"",
                "created_at":1754700000,"reply_count":3,"last_reply_at":1754701000,
                "last_reply_username":"bob","is_pinned":1,
                "author":{"id":5,"username":"alice","avatar":{"style":"dylan","seed":"a","url":"https://cdn/a.png"},
                          "group_id":1,"group_name":"用户","points":10,"is_banned":0,"is_muted":0}
            }],"page":1,"page_size":30,"total":90,"has_next_page":true,"sort":"comment"}
        """.trimIndent()

        val page = decodeApiPayload<ApiTopicsPage>(raw)

        assertEquals(1, page.topics.size)
        val topic = page.topics.first()
        assertEquals(7L, topic.id)
        assertEquals("水区", topic.forumName)
        assertEquals(1, topic.isPinned)
        assertEquals("alice", topic.author.username)
        assertEquals("https://cdn/a.png", topic.author.avatar.url)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun `topic page computes has-next from totals`() {
        val raw = """
            {"ok":1,
             "topic":{"id":7,"forum_id":2,"forum_name":"水区","title":"你好","created_at":1,
                      "reply_count":120,"is_pinned":0,"author":{"id":5,"username":"alice"},
                      "body":"正文","body_html":"<p>正文</p>","view_count":9,"reply_order":0,"can_edit":false},
             "replies":[{"id":11,"body":"回","body_html":"<p>回</p>","created_at":2,"updated_at":2,
                         "author":{"id":6,"username":"bob"},"floor":1}],
             "page":1,"page_size":50,"reply_count":120,"can_reply":true}
        """.trimIndent()

        val page = decodeApiPayload<ApiTopicPage>(raw)

        assertEquals("正文", page.topic.body)
        assertEquals(1, page.replies.single().floor)
        assertTrue(page.canReply)
        assertTrue(page.hasNextPage)
        assertFalse(page.copy(page = 3).hasNextPage)
    }

    @Test
    fun `forum list carries this identity's write permissions`() {
        val raw = """
            {"ok":1,"forums":[
                {"id":1,"name":"公告","description":"","sort":0,"can_view":true,"can_post":false,"can_reply":true},
                {"id":2,"name":"水区","description":"","sort":1,"can_view":true,"can_post":true,"can_reply":true}
            ]}
        """.trimIndent()

        val forums = decodeApiPayload<ApiForumsPage>(raw).forums

        assertEquals(listOf(false, true), forums.map { it.canPost })
        assertEquals(listOf(true, true), forums.map { it.canReply })
    }

    @Test
    fun `login answers with a token and the user it belongs to`() {
        val raw = """
            {"ok":1,"token":"2.1786000000.abcd","token_expires_at":1786000000,
             "user":{"id":2,"username":"alice","avatar":{"style":"dylan","seed":"a","url":"https://cdn/a.png"},
                     "group_id":1,"group_name":"用户","points":3,"is_banned":0,"is_muted":0,
                     "email":"a@example.com","bio":"","created_at":1,"unread_notifications":0,
                     "can_manage":false,"can_admin":false,"can_speak":true}}
        """.trimIndent()

        val auth = decodeApiPayload<ApiAuth>(raw)

        assertEquals("2.1786000000.abcd", auth.token)
        assertEquals(1786000000L, auth.tokenExpiresAt)
        assertEquals("alice", auth.user.username)
        assertEquals("https://cdn/a.png", auth.user.avatar.url)
        assertTrue(auth.user.canSpeak)
    }

    @Test
    fun `a created reply arrives without the floor the thread numbers it by`() {
        val raw = """
            {"ok":1,"topic_id":7,"reply_id":42,
             "reply":{"id":42,"body":"回","body_html":"<p>回</p>","created_at":9,"updated_at":9,
                      "author":{"id":6,"username":"bob"}}}
        """.trimIndent()

        val created = decodeApiPayload<ApiReplyCreated>(raw)

        assertEquals(42L, created.replyId)
        assertEquals(7L, created.topicId)
        // Not a decode quirk to work around later: the endpoint genuinely does not number the floor,
        // so whoever shows the reply computes it.
        assertEquals(0, created.reply.floor)
    }

    @Test
    fun `a refused credential is told apart from any other refusal`() {
        val e = assertThrows(Bbs1ApiException.Unauthorized::class.java) {
            decodeApiPayload<ApiMeta>("""{"ok":0,"message":"登录凭证无效或已过期"}""", httpCode = 401)
        }
        assertEquals("登录凭证无效或已过期", e.userMessage)
    }

    @Test
    fun `a refusal with any other status stays a plain server refusal`() {
        assertThrows(Bbs1ApiException.Server::class.java) {
            decodeApiPayload<ApiMeta>("""{"ok":0,"message":"无权限"}""", httpCode = 403)
        }
    }

    @Test
    fun `server refusal surfaces its own message`() {
        val e = assertThrows(Bbs1ApiException.Server::class.java) {
            decodeApiPayload<ApiMeta>("""{"ok":0,"message":"接口不存在：nope"}""")
        }
        assertEquals("接口不存在：nope", e.userMessage)
    }

    @Test
    fun `refusal without a message still says something`() {
        val e = assertThrows(Bbs1ApiException.Server::class.java) {
            decodeApiPayload<ApiMeta>("""{"ok":0}""")
        }
        assertEquals("操作失败", e.userMessage)
    }

    @Test
    fun `html page classifies as not an api`() {
        assertThrows(Bbs1ApiException.NotBbs1Api::class.java) {
            decodeApiPayload<ApiMeta>("<!DOCTYPE html><html><body>404</body></html>")
        }
    }

    @Test
    fun `json without the ok envelope classifies as not an api`() {
        assertThrows(Bbs1ApiException.NotBbs1Api::class.java) {
            decodeApiPayload<ApiMeta>("""{"status":"fine"}""")
        }
    }

    @Test
    fun `top-level json array classifies as not an api`() {
        assertThrows(Bbs1ApiException.NotBbs1Api::class.java) {
            decodeApiPayload<ApiMeta>("""[1,2,3]""")
        }
    }

    @Test
    fun `ok envelope with a payload of the wrong shape classifies as not an api`() {
        assertThrows(Bbs1ApiException.NotBbs1Api::class.java) {
            // topic must be an object; a site that answers ok:1 but not this schema is not this API.
            decodeApiPayload<ApiTopicPage>("""{"ok":1,"topic":"nope"}""")
        }
    }

    @Test
    fun `meta needs nothing but ok to succeed`() {
        // Older or newer plugin builds may trim fields; identity-free payloads default themselves.
        val meta = decodeApiPayload<ApiMeta>("""{"ok":1,"site":{"name":"FORUM"}}""")
        assertEquals("FORUM", meta.site.name)
        assertEquals("", meta.site.description)
    }
}
