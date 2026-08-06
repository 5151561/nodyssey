package io.github.nodyssey.core.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

/**
 * The vote signature.
 *
 * Worth pinning precisely: the server currently accepts any value in this header, so a wrong digest
 * would pass in production and only surface if the check were ever tightened.
 *
 * Driven through a real [OkHttpClient] with a terminal interceptor standing in for the network, so
 * the request under assertion is the one the chain actually produced — no [okhttp3.Interceptor.Chain]
 * of our own, and no server to run.
 */
class DynamicSignInterceptorTest {
    private var sent: Request? = null

    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(DynamicSignInterceptor())
            .addInterceptor { chain ->
                sent = chain.request()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"success":true}""".toResponseBody(JSON))
                    .build()
            }.build()

    private fun send(
        url: String,
        method: String = "GET",
        body: String? = null,
        userAgent: String = UA,
    ): Request {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .method(method, body?.toRequestBody(JSON))
                .build()
        client.newCall(request).execute().close()
        return requireNotNull(sent)
    }

    private fun signOf(request: Request): String? = request.header("x-dynamic-sign")

    @Test
    fun `signs a vote read over method, url, user agent and an empty body`() {
        val url = "$BASE/api/vote/info/2871"

        val signed = send(url)

        assertEquals(sha1Hex(listOf("GET", url, UA, "").joinToString(SEPARATOR)), signOf(signed))
    }

    @Test
    fun `signs a write over its body too`() {
        val url = "$BASE/api/vote/voteforitem"
        val body = """{"ids":[13201]}"""

        val signed = send(url, method = "POST", body = body)

        assertEquals(sha1Hex(listOf("POST", url, UA, body).joinToString(SEPARATOR)), signOf(signed))
    }

    /** The body is only peeked at — a signed request still has to arrive with its body intact. */
    @Test
    fun `the request body survives being signed`() {
        val body = """{"deleted":true}"""

        val signed = send("$BASE/api/vote/info/2871", method = "DELETE", body = body)

        assertNotNull(signOf(signed))
        val buffer = Buffer().also { requireNotNull(signed.body).writeTo(it) }
        assertEquals(body, buffer.readUtf8())
    }

    /**
     * The digest covers the UA the server recomputes from, so the two have to be the same string. If
     * these matched, the interceptor would be signing something other than what it sends.
     */
    @Test
    fun `a different user agent produces a different signature`() {
        val first = signOf(send("$BASE/api/vote/info/1", userAgent = UA))
        val second = signOf(send("$BASE/api/vote/info/1", userAgent = "$UA Extra"))

        assertNotEquals(first, second)
    }

    /** `voter-of-item` carries its id and page in the query, so the query has to be signed. */
    @Test
    fun `the query string is part of the signature`() {
        val page1 = signOf(send("$BASE/api/vote/voter-of-item?id=13201&page=1"))
        val page2 = signOf(send("$BASE/api/vote/voter-of-item?id=13201&page=2"))

        assertNotEquals(page1, page2)
    }

    /** Scoped: no other endpoint family asks for it, and the site does not send it elsewhere. */
    @Test
    fun `requests outside the vote family are left alone`() {
        val signed = send("$BASE/api/statistics/collection", method = "POST", body = """{"postId":1}""")

        assertNull(signOf(signed))
    }

    private fun sha1Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val BASE = "https://www.nodeseek.com"
        const val UA = "Mozilla/5.0 (Linux; Android 14) NodysseyTest"
        const val SEPARATOR = "\n\n"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
