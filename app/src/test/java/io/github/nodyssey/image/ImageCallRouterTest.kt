package io.github.nodyssey.image

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageCallRouterTest {
    /**
     * The suffix has to anchor at a dot: `evilnodeseek.com` and `nodeseek.com.evil.example` are the
     * two classic ways to look like a host without being it, and a router fooled by either would
     * hand a stranger the client whose jar saves what they set.
     */
    @Test
    fun `only nodeseek and its subdomains count as the forum`() {
        assertEquals(true, isForumImageHost("nodeseek.com"))
        assertEquals(true, isForumImageHost("www.nodeseek.com"))
        assertEquals(true, isForumImageHost("WWW.NODESEEK.COM"))
        assertEquals(false, isForumImageHost("evilnodeseek.com"))
        assertEquals(false, isForumImageHost("nodeseek.com.evil.example"))
        assertEquals(false, isForumImageHost("i.imgur.com"))
    }

    @Test
    fun `a forum image asks the forum client and a foreign one asks the other`() {
        var forumAsked = 0
        var elsewhereAsked = 0
        val client = OkHttpClient()
        val router =
            ImageCallRouter(
                forum = {
                    forumAsked++
                    client
                },
                elsewhere = {
                    elsewhereAsked++
                    client
                },
            )

        router.newCall(Request.Builder().url("https://www.nodeseek.com/avatar/1.png").build())
        assertEquals(1, forumAsked)
        assertEquals(0, elsewhereAsked)

        router.newCall(Request.Builder().url("https://i.imgur.com/cat.png").build())
        assertEquals(1, forumAsked)
        assertEquals(1, elsewhereAsked)
    }
}
