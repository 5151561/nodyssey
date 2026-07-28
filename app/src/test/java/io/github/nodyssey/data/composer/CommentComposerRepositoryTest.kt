package io.github.nodyssey.data.composer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every response body here was copied from a live exchange with the sandbox thread (2026-07-28),
 * not invented — the point of the suite is that the app keeps matching a contract we do not own.
 */
@RunWith(RobolectricTestRunner::class)
class CommentComposerRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `publish sends the site's own new-comment payload`() = runTest {
        val recorder = RecordingCommentInterceptor(
            body = """{"redirect":"/post-841108-1","redirectHash":"#3","success":true}""",
        )
        val floor = repository(recorder).publish(
            CommentSubmission(postId = 841108L, body = "  回复正文  "),
        )

        assertEquals(3, floor)
        val request = recorder.request!!
        assertEquals(NodeSeekSite.NEW_COMMENT_API_PATH, request.url.encodedPath)
        assertEquals(NodeSeekSite.BASE_URL, request.header("Origin"))
        assertEquals("https://www.nodeseek.com/post-841108-1", request.header("Referer"))
        assertEquals(16, request.header("Csrf-Token")?.length)

        val payload = Json.parseToJsonElement(request.bodyUtf8()).jsonObject
        assertEquals("回复正文", payload.getValue("content").jsonPrimitive.content)
        assertEquals("new-comment", payload.getValue("mode").jsonPrimitive.content)
        assertEquals(841108L, payload.getValue("postId").jsonPrimitive.long)
    }

    /**
     * The floor number exists nowhere but `redirectHash`. A response without one is still a posted
     * reply, so it must not throw — the caller reads null as "posted, scroll to the end".
     */
    @Test
    fun `a success without a redirect hash reports no floor rather than failing`() = runTest {
        val floor = repository(RecordingCommentInterceptor(body = """{"success":true}"""))
            .publish(CommentSubmission(postId = 1L, body = "x"))

        assertNull(floor)
    }

    @Test
    fun `a rejection keeps the site's own sentence`() = runTest {
        val exception = publishExpecting(
            RecordingCommentInterceptor(
                code = 400,
                body = """{"success":false,"message":"内容不能为空"}""",
            ),
        )

        assertEquals(NodeSeekError.Http(400), exception.error)
        assertEquals("内容不能为空", exception.detail)
    }

    /** A 200 whose body says `success:false` is a refusal, not a posted reply. */
    @Test
    fun `a 200 with success false is a failure`() = runTest {
        val exception = publishExpecting(
            RecordingCommentInterceptor(body = """{"success":false,"message":"该帖已锁定"}"""),
        )

        assertEquals("该帖已锁定", exception.detail)
    }

    @Test
    fun `a Cloudflare 403 surfaces as a challenge, not a login prompt`() = runTest {
        val exception = publishExpecting(
            RecordingCommentInterceptor(
                code = 403,
                body = "<html><script src=\"/cdn-cgi/challenge-platform/h/b.js\"></script></html>",
                mediaType = "text/html",
                headers = mapOf("cf-mitigated" to "challenge"),
            ),
        )

        assertEquals(NodeSeekError.Cloudflare, exception.error)
    }

    @Test
    fun `a plain 401 asks the user to sign in`() = runTest {
        val exception = publishExpecting(
            RecordingCommentInterceptor(code = 401, body = """{"success":false}"""),
        )

        assertEquals(NodeSeekError.LoginRequired, exception.error)
    }

    private fun TestScope.repository(interceptor: Interceptor) =
        StandardTestDispatcher(testScheduler).let { dispatcher ->
            DefaultCommentComposerRepository(
                context = context,
                okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                dispatchers = AppDispatchers(dispatcher, dispatcher),
                clock = AppClock { 0L },
            )
        }

    private suspend fun TestScope.publishExpecting(interceptor: Interceptor): NodeSeekException =
        try {
            repository(interceptor).publish(CommentSubmission(postId = 1L, body = "x"))
            throw AssertionError("publish should have failed")
        } catch (exception: NodeSeekException) {
            exception
        }
}

private class RecordingCommentInterceptor(
    private val code: Int = 200,
    private val body: String,
    private val mediaType: String = "application/json",
    private val headers: Map<String, String> = emptyMap(),
) : Interceptor {
    var request: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        val builder = Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody(mediaType.toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }
}

private fun Request.bodyUtf8(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
