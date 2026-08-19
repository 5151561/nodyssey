package io.github.plaza.core.net

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Driven through a real client with a terminal interceptor standing in for the network, so what is
 * asserted is the request the chain actually produced. Same shape as `DynamicSignInterceptorTest`.
 */
class BrowserHeadersInterceptorTest {
    private var sent: Request? = null

    private fun send(
        interceptor: BrowserHeadersInterceptor,
        build: Request.Builder.() -> Unit = {},
    ): Request {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(interceptor)
                .addInterceptor { chain ->
                    sent = chain.request()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody(null))
                        .build()
                }.build()
        client
            .newCall(Request.Builder().url("https://img.example.invalid/a.webp").apply(build).build())
            .execute()
            .close()
        return requireNonNull()
    }

    private fun requireNonNull(): Request = checkNotNull(sent) { "no request reached the network" }

    /** The forum's own client: all three, and the reason the image in post-879848 now loads. */
    @Test
    fun `it stamps the browser headers`() {
        val request = send(forumInterceptor())

        assertEquals(UA, request.header("User-Agent"))
        assertEquals(LANGUAGE, request.header("Accept-Language"))
        assertEquals(REFERER, request.header("Referer"))
    }

    /** A third-party host is told what we are, not where we have been. */
    @Test
    fun `without a referer it sends none`() {
        val request = send(BrowserHeadersInterceptor(userAgent = UA, acceptLanguage = LANGUAGE))

        assertEquals(UA, request.header("User-Agent"))
        assertEquals(LANGUAGE, request.header("Accept-Language"))
        assertNull(request.header("Referer"))
    }

    /** A caller that set a header meant it — the signed vote request depends on this. */
    @Test
    fun `it never overwrites a header the caller set`() {
        val request =
            send(forumInterceptor()) {
                header("User-Agent", "caller/1.0")
                header("Accept-Language", "de-DE")
                header("Referer", "https://elsewhere.invalid/")
            }

        assertEquals("caller/1.0", request.header("User-Agent"))
        assertEquals("de-DE", request.header("Accept-Language"))
        assertEquals("https://elsewhere.invalid/", request.header("Referer"))
    }

    private fun forumInterceptor() =
        BrowserHeadersInterceptor(userAgent = UA, acceptLanguage = LANGUAGE, referer = REFERER)

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
        const val LANGUAGE = "zh-CN,zh;q=0.9"
        const val REFERER = "https://www.nodeseek.com/"
    }
}
