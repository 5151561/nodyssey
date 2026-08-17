package io.github.plaza.core.net

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CrossOriginRefererInterceptorTest {
    private val forum = "https://www.nodeseek.com/"

    private fun request(
        url: String,
        referer: String? = forum,
    ): Request =
        Request
            .Builder()
            .url(url)
            .apply { referer?.let { header("Referer", it) } }
            .build()

    @Test
    fun `the hop the caller asked for keeps its referer`() {
        val asked = "https://pic1.imgdb.cn/i/0348lKJSVIHbmDwXEn1dEX.jpg"

        val sent = withoutForeignReferer(asked.toHttpUrl(), request(asked))

        assertEquals(forum, sent.header("Referer"))
    }

    /**
     * The bug (2026-08-17): `pic1.imgdb.cn` 302s to Baidu's CDN, which 403s anything arriving with a
     * referrer. The browser drops it because that redirect carries `Referrer-Policy: no-referrer`;
     * OkHttp carries every header but `Authorization` across a redirect, so the app has to.
     */
    @Test
    fun `a redirect to another host loses it`() {
        val sent =
            withoutForeignReferer(
                "https://pic1.imgdb.cn/i/0348lKJSVIHbmDwXEn1dEX.jpg".toHttpUrl(),
                request("https://wkphoto.cdn.bcebos.com/b2de9c82d158ccbfb03a3d6109d8bc3eb03541e3.jpg"),
            )

        assertNull(sent.header("Referer"))
    }

    /** Same host, other scheme — an upgrade, not the redirect off-site this is about. */
    @Test
    fun `an https upgrade of the same host keeps it`() {
        val sent =
            withoutForeignReferer(
                "http://www.nodeseek.com/avatar/52425.png".toHttpUrl(),
                request("https://www.nodeseek.com/avatar/52425.png"),
            )

        assertEquals(forum, sent.header("Referer"))
    }

    @Test
    fun `a request that never had one is passed through untouched`() {
        val hop = request("https://wkphoto.cdn.bcebos.com/b.jpg", referer = null)

        assertSame(hop, withoutForeignReferer("https://pic1.imgdb.cn/i/a.jpg".toHttpUrl(), hop))
    }
}
