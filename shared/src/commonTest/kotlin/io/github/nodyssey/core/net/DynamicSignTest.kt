package io.github.nodyssey.core.net

import io.github.plaza.core.net.HttpBody
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.RecordingTransport
import io.github.plaza.core.net.httpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The vote signature, on the transport that carries it rather than on the digest.
 *
 * Worth pinning precisely: the server currently accepts any value in this header, so a wrong digest
 * would pass in production and only surface if the check were ever tightened. That the digest is
 * really SHA-1 is pinned separately and against the JVM's own — see `DynamicSignDigestTest`. What is
 * here is everything that decision depends on: which requests get signed at all, and what string the
 * signature is taken over.
 */
class DynamicSignTest {
    private fun transport() = RecordingTransport(httpResponse("""{"success":true}"""))

    private suspend fun send(
        url: String,
        method: String = "GET",
        body: HttpBody? = null,
        headers: Map<String, String> = emptyMap(),
        recorder: RecordingTransport = transport(),
    ): HttpRequest {
        DynamicSignTransport(recorder, UA).execute(HttpRequest(url, method, headers, body))
        return recorder.last
    }

    private val HttpRequest.signature: String? get() = headers["x-dynamic-sign"]

    @Test
    fun `signs a vote read over method url user agent and an empty body`() = runTest {
        val url = "$BASE/api/vote/info/2871"

        val sent = send(url)

        assertEquals(dynamicSign("GET", url, UA, ""), sent.signature)
    }

    @Test
    fun `signs a write over its body too`() = runTest {
        val url = "$BASE/api/vote/voteforitem"
        val body = """{"ids":[13201]}"""

        val sent = send(url, method = "POST", body = HttpBody.Text(body))

        assertEquals(dynamicSign("POST", url, UA, body), sent.signature)
    }

    /** The body is only read from — a signed request still has to arrive with its own intact. */
    @Test
    fun `the request body survives being signed`() = runTest {
        val body = HttpBody.Text("""{"deleted":true}""")

        val sent = send("$BASE/api/vote/info/2871", method = "DELETE", body = body)

        assertEquals(body, sent.body)
    }

    /**
     * The digest covers the `User-Agent` the server recomputes from, so the header has to go out
     * carrying the string that was signed. The interceptor this replaced got that by running after
     * the one that fills the header in; a transport decorator has to write it itself.
     */
    @Test
    fun `the signed user agent is the one sent`() = runTest {
        val sent = send("$BASE/api/vote/info/1")

        assertEquals(UA, sent.headers["User-Agent"])
        assertEquals(dynamicSign("GET", "$BASE/api/vote/info/1", UA, ""), sent.signature)
    }

    /** A caller that set its own is signed with the one it set, not with the client's. */
    @Test
    fun `a user agent on the request wins and is what gets signed`() = runTest {
        val theirs = "$UA Extra"

        val sent = send("$BASE/api/vote/info/1", headers = mapOf("User-Agent" to theirs))

        assertEquals(theirs, sent.headers["User-Agent"])
        assertEquals(dynamicSign("GET", "$BASE/api/vote/info/1", theirs, ""), sent.signature)
        assertNotEquals(dynamicSign("GET", "$BASE/api/vote/info/1", UA, ""), sent.signature)
    }

    /** `voter-of-item` carries its id and page in the query, so the query has to be signed. */
    @Test
    fun `the query string is part of the signature`() = runTest {
        val page1 = send("$BASE/api/vote/voter-of-item?id=13201&page=1").signature
        val page2 = send("$BASE/api/vote/voter-of-item?id=13201&page=2").signature

        assertNotEquals(page1, page2)
    }

    /** Scoped: no other endpoint family asks for it, and the site does not send it elsewhere. */
    @Test
    fun `requests outside the vote family are left alone`() = runTest {
        val sent = send("$BASE/api/statistics/collection", method = "POST", body = HttpBody.Text("""{"postId":1}"""))

        assertNull(sent.signature)
        assertNull(sent.headers["User-Agent"])
    }

    /**
     * The path, not the whole URL. `/jump?to=…/api/vote/…` is a link the site itself emits, and
     * matching a bare prefix against the query would sign a request to somewhere else entirely.
     */
    @Test
    fun `the vote path in a query string does not sign anything`() = runTest {
        val sent = send("$BASE/jump?to=$BASE/api/vote/info/1")

        assertNull(sent.signature)
    }

    @Test
    fun `an upload listener passes through untouched`() = runTest {
        val recorder = transport()

        send("$BASE/api/vote/info/1", recorder = recorder)
        assertFalse(recorder.listened)

        DynamicSignTransport(recorder, UA)
            .execute(HttpRequest("$BASE/api/vote/info/1"), onUploadProgress = {})
        assertTrue(recorder.listened)
    }

    private companion object {
        const val BASE = "https://www.nodeseek.com"
        const val UA = "Mozilla/5.0 (Linux; Android 14) NodysseyTest"
    }
}
