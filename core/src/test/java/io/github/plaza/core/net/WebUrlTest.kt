package io.github.plaza.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI
import java.net.URISyntaxException

/**
 * [WebUrl] against the `java.net.URI` it replaced, URL by URL.
 *
 * A differential test rather than a list of expectations, because what is being preserved is not a
 * specification — it is the answers one particular parser gave, and those answers are what every
 * host check in the app was written against. Anything asserted by hand here would be asserting what
 * the author *believed* `URI` did; a link written to slip past a host check is exactly the case
 * where that belief is wrong.
 *
 * `java.net.URI` is JVM API and this test runs on the JVM, which is the point: the test may keep the
 * dependency the code being tested has just shed, and that is what makes the shedding checkable. If
 * `:core` ever compiles for a non-JVM target, this file stays behind with the JVM one.
 */
class WebUrlTest {

    /**
     * Every shape that matters, mostly for the reason it matters rather than for coverage.
     *
     * The two that are load-bearing beyond parsing: `user@host` — where the host is the part after
     * the `@`, which is the whole point of writing it — and every non-web scheme, where the answer
     * has to be "no host" rather than a host lifted out of what follows the colon.
     */
    private val urls = listOf(
        // The site, as it is actually linked.
        "https://www.nodeseek.com/signIn.html",
        "https://nodeseek.com/",
        "http://www.nodeseek.com/post-832584-2",
        "https://www.nodeseek.com",
        "https://www.nodeseek.com/member?t=lcy0828",
        "https://www.nodeseek.com/member?t=%E4%B8%AD%E6%96%87",
        "https://www.nodeseek.com/jump?to=https%3A%2F%2Filatency.com%2Fcoverage",
        "https://www.nodeseek.com/jump?to=",
        "https://www.nodeseek.com/notification#/atMe",
        "https://www.nodeseek.com/notification#/message?mode=talk&to=5230",
        "https://nodeseek.com/notification/#/replyToMe",
        "https://www.nodeseek.com/space/23042#/general",
        // Hosts written to be mistaken for the site.
        "https://www.nodeseek.com.evil.example/",
        "https://www.nodeseek.com@evil.example/",
        "https://user:pw@nodeseek.com/x",
        "https://evil.example/?x=www.nodeseek.com",
        "https://evil.example/#www.nodeseek.com",
        // Schemes that are not the web.
        "javascript:alert(1)",
        "content://www.nodeseek.com/private",
        "file:///data/data/io.github.nodyssey/file",
        "mailto:someone@nodeseek.com",
        "nsapp://stardust-receive?member_id=1&diff=2",
        "intent://www.nodeseek.com/#Intent;scheme=https;end",
        // Ports.
        "https://www.nodeseek.com:443/x",
        "https://www.nodeseek.com:8443/x",
        "https://www.nodeseek.com:abc/x",
        "https://www.nodeseek.com:/x",
        "https://[::1]:8080/x",
        "https://[::1]/x",
        // Hostnames `java.net.URI` refuses to vouch for.
        "https://foo_bar.example/",
        "https://.nodeseek.com/x",
        "https://nodeseek.com./x",
        "https://-nodeseek.com/x",
        "https://1.2.3.4/x",
        "https://例え.jp/",
        "https://www.nodeseek.com/%E4%B8%AD/中文?q=中文#中文",
        // Case, which no comparison in the app may depend on.
        "HTTPS://WWW.NODESEEK.COM/x",
        "https://WWW.NodeSeek.COM/x",
        // Escapes, in each of the three places one can appear.
        "https://www.nodeseek.com/a%2Fb",
        "https://www.nodeseek.com/x?q=a%26b",
        "https://www.nodeseek.com/x#frag%20ment",
        "https://www.nodeseek.com/%E4%B8%AD%E6%96%87",
        // Trailing and empty parts.
        "https://www.nodeseek.com/x?",
        "https://www.nodeseek.com/x#",
        "https://www.nodeseek.com/?#",
    )

    /** What `URI` threw on, [WebUrl.parse] answers null to — the callers treated both the same way. */
    private val rejected = listOf(
        "https://exa mple.com/",
        "https://www.nodeseek.com/pa th",
        "https://www.nodeseek.com/a%2",
        "https://www.nodeseek.com/a%zz",
        "https://www.nodeseek.com/a\\b",
        "https://www.nodeseek.com/<script>",
        "https://",
        "//www.nodeseek.com/x",
        "/post-123",
        "",
        "   ",
        "not a url",
    )

    @Test
    fun `reads every part the way java net URI read it`() {
        urls.forEach { url ->
            val expected = URI(url)
            val actual = WebUrl.parse(url) ?: error("$url did not parse")

            assertEquals("$url scheme", expected.scheme.lowercase(), actual.scheme)
            assertEquals("$url userInfo", expected.userInfo, actual.userInfo)
            assertEquals("$url host", expected.host, actual.host)
            assertEquals("$url port", expected.port, actual.port)
            assertEquals("$url path", expected.path, actual.path)
            assertEquals("$url rawQuery", expected.rawQuery, actual.rawQuery)
            assertEquals("$url fragment", expected.fragment, actual.fragment)
        }
    }

    @Test
    fun `refuses everything java net URI refused to parse as an absolute url`() {
        rejected.forEach { url ->
            val jvmRefused = try {
                !URI(url.trim()).isAbsolute
            } catch (exception: URISyntaxException) {
                true
            }

            assertEquals("$url should be refused by java.net.URI too", true, jvmRefused)
            assertNull(url, WebUrl.parse(url))
        }
    }

    /**
     * The one deliberate departure, and the reason it cannot reach anything the site wrote.
     *
     * `URLDecoder`, which read these values before, is a *form* decoder: it turns `+` into a space.
     * `encodeURIComponent` — the only thing that writes the site's own `/jump?to=` and `?t=` values —
     * escapes a literal plus as `%2B`, so no link the site generates contains a bare `+` to disagree
     * about. A hand-typed one now reaches the browser as written instead of with a space in it.
     */
    @Test
    fun `a plus in a query value stays a plus`() {
        val url = WebUrl.parse("https://www.nodeseek.com/jump?to=https%3A%2F%2Fexample.com%2Fa+b")

        assertEquals("https://example.com/a+b", url?.queryParameter("to"))
    }

    @Test
    fun `an escape inside a query value survives the split on ampersand`() {
        val url = WebUrl.parse("https://www.nodeseek.com/x?q=a%26b&r=2")

        assertEquals("a&b", url?.queryParameter("q"))
        assertEquals("2", url?.queryParameter("r"))
    }

    @Test
    fun `a query field the url does not carry reads as null, and an empty one as empty`() {
        val url = WebUrl.parse("https://www.nodeseek.com/jump?to=")

        assertEquals("", url?.queryParameter("to"))
        assertNull(url?.queryParameter("from"))
    }
}
