package io.github.nsreader.core
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

    @Test fun `a non-jump url passes through unwrap unchanged`() {
        assertEquals("https://example.com/a", NodeSeekSite.unwrapJumpUrl("https://example.com/a"))
    }
}
