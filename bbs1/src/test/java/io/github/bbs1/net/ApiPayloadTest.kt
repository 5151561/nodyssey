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
        assertTrue(page.hasNextPage)
        assertFalse(page.copy(page = 3).hasNextPage)
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
