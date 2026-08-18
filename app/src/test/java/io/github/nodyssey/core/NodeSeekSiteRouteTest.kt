package io.github.nodyssey.core
import org.junit.Assert.assertEquals
import org.junit.Test
class NodeSeekSiteRouteTest {
    @Test fun space() {
        assertEquals(
            NodeSeekSite.InternalRoute.Space(23042L),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/space/23042#/general"),
        )
    }

    @Test fun post() {
        assertEquals(
            NodeSeekSite.InternalRoute.Post(832584L, 2),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/post-832584-2"),
        )
    }

    @Test fun external() {
        assertEquals(null, NodeSeekSite.parseInternalRoute("https://ilatency.com/"))
    }

    @Test fun mention() {
        assertEquals(
            NodeSeekSite.InternalRoute.Member("lcy0828"),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/member?t=lcy0828"),
        )
    }

    /**
     * Post bodies are user-submitted HTML kept verbatim for years — a mention or post link typed
     * before the site enforced HTTPS survives as a literal `http://` anchor. Recognizing it as a
     * route is a plain host/path match with no WebView involved, so it must not require HTTPS the
     * way [NodeSeekSite.isTrustedWebViewUrl] does.
     */
    @Test fun `legacy http links still route internally`() {
        assertEquals(
            NodeSeekSite.InternalRoute.Member("lcy0828"),
            NodeSeekSite.parseInternalRoute("http://www.nodeseek.com/member?t=lcy0828"),
        )
        assertEquals(
            NodeSeekSite.InternalRoute.Post(832584L, 2),
            NodeSeekSite.parseInternalRoute("http://www.nodeseek.com/post-832584-2"),
        )
        assertEquals(
            NodeSeekSite.InternalRoute.Space(23042L),
            NodeSeekSite.parseInternalRoute("http://www.nodeseek.com/space/23042"),
        )
    }

    @Test fun `jump to an external target unwraps for the browser`() {
        assertEquals(null, NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/jump?to=https%3A%2F%2Filatency.com%2Fcoverage"))
        assertEquals(
            "https://ilatency.com/coverage",
            NodeSeekSite.unwrapJumpUrl("https://www.nodeseek.com/jump?to=https%3A%2F%2Filatency.com%2Fcoverage"),
        )
    }

    @Test fun `jump to one of our own posts routes internally`() {
        assertEquals(
            NodeSeekSite.InternalRoute.Post(1234L, 1),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/jump?to=https%3A%2F%2Fwww.nodeseek.com%2Fpost-1234-1"),
        )
    }

    /**
     * The two shapes the site's notification page actually links to. Everything that tells them
     * apart is in the fragment, which no `<intent-filter>` can match on — so if this parsing goes,
     * both URLs land on the same screen and the app looks like it ignored half the link.
     */
    @Test fun `notification hash routes`() {
        assertEquals(
            NodeSeekSite.InternalRoute.Notifications(NodeSeekSite.NotificationGroup.MENTIONS),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notification#/atMe"),
        )
        assertEquals(
            NodeSeekSite.InternalRoute.MessageThread(5230L),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notification#/message?mode=talk&to=5230"),
        )
    }

    @Test fun `notification without a conversation is the message list`() {
        assertEquals(
            NodeSeekSite.InternalRoute.Notifications(NodeSeekSite.NotificationGroup.MESSAGES),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notification#/message"),
        )
    }

    /** A bare page, and a group the app does not read, are both still the 通知 tab. */
    @Test fun `notification falls back to the tab itself`() {
        assertEquals(
            NodeSeekSite.InternalRoute.Notifications(null),
            NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notification"),
        )
        assertEquals(
            NodeSeekSite.InternalRoute.Notifications(null),
            NodeSeekSite.parseInternalRoute("https://nodeseek.com/notification/#/replyToMe"),
        )
    }

    /** `/notifications` is not `/notification`, and neither is a post that mentions the word. */
    @Test fun `notification path is matched exactly`() {
        assertEquals(null, NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notifications"))
        assertEquals(null, NodeSeekSite.parseInternalRoute("https://www.nodeseek.com/notification-help"))
    }

    @Test fun `a non-jump url passes through unwrap unchanged`() {
        assertEquals("https://example.com/a", NodeSeekSite.unwrapJumpUrl("https://example.com/a"))
    }
}
