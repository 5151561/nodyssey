package io.github.nsreader.core.html

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityStatsParserTest {
    @Test
    fun `reads the real member total from a captured homepage`() {
        assertEquals(
            55_232L,
            CommunityStatsParser.parseMemberCount(Fixtures.load("page-1.html")),
        )
    }

    @Test
    fun `accepts grouped totals from the user count panel`() {
        val html =
            """
            <div class="nsk-panel">
              <h4>📈用户数目📈</h4>
              <div>目前论坛共有 70,123 位 seeker</div>
            </div>
            """.trimIndent()

        assertEquals(70_123L, CommunityStatsParser.parseMemberCount(html))
    }

    @Test
    fun `rejects unrelated numbers outside the user count panel`() {
        try {
            CommunityStatsParser.parseMemberCount("<div>目前论坛共有99999位seeker</div>")
            fail("Expected an unparsable response")
        } catch (exception: NodeSeekException) {
            assertEquals(NodeSeekError.Unparsable, exception.error)
        }
    }
}
