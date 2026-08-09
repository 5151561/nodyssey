package io.github.nodyssey.data.composer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostComposerRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `publish uses current NodeSeek discussion contract`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val recorder = RecordingPublishInterceptor()
            val repository =
                DefaultPostComposerRepository(
                    context = context,
                    okHttpClient = OkHttpClient.Builder().addInterceptor(recorder).build(),
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                    clock = AppClock { 0L },
                )

            val postId =
                repository.publish(
                    PostSubmission(
                        title = " 测试标题 ",
                        body = " 测试正文 ",
                        boardSlug = "sandbox",
                        permission = PostPermission.PRIVATE,
                    ),
                )

            assertEquals(703863L, postId)
            val request = assertNotNull(recorder.request).let { recorder.request!! }
            assertEquals(NodeSeekSite.NEW_DISCUSSION_API_PATH, request.url.encodedPath)
            assertEquals(NodeSeekSite.BASE_URL + NodeSeekSite.NEW_DISCUSSION_PATH, request.header("Referer"))
            assertEquals(NodeSeekSite.BASE_URL, request.header("Origin"))
            assertEquals(16, request.header("Csrf-Token")?.length)

            val payload = Json.parseToJsonElement(request.bodyUtf8()).jsonObject
            assertEquals("测试标题", payload.getValue("title").jsonPrimitive.content)
            assertEquals("测试正文", payload.getValue("content").jsonPrimitive.content)
            assertEquals("sandbox", payload.getValue("category").jsonPrimitive.content)
            assertEquals("new-discussion", payload.getValue("mode").jsonPrimitive.content)
            assertEquals(255, payload.getValue("rank").jsonPrimitive.int)
            assertTrue("Legacy permission field must not be sent", "permission" !in payload)
        }

    @Test
    fun `publish resolves id from post-time feed when success response omits it`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val recorder = SuccessWithoutIdInterceptor()
            val repository =
                DefaultPostComposerRepository(
                    context = context,
                    okHttpClient = OkHttpClient.Builder().addInterceptor(recorder).build(),
                    dispatchers = AppDispatchers(dispatcher, dispatcher),
                    clock = AppClock { 0L },
                )

            val postId =
                repository.publish(
                    PostSubmission(
                        title = "NodeSeek Reader Android post test",
                        body = "body",
                        boardSlug = "sandbox",
                        permission = PostPermission.PUBLIC,
                    ),
                )

            assertEquals(841108L, postId)
            assertEquals("/categories/sandbox", recorder.feedRequest?.url?.encodedPath)
            assertEquals("postTime", recorder.feedRequest?.url?.queryParameter("sortBy"))
        }

    /**
     * Cloudflare blocks with 403 too. Reading that as "session expired" sends the user to the
     * sign-in page when what clears the block is the challenge WebView.
     */
    @Test
    fun `a Cloudflare 403 surfaces as a challenge, not a login prompt`() =
        runTest {
            val error =
                publishExpectingError(
                    StaticResponseInterceptor(
                        code = 403,
                        body = "<html><script src=\"/cdn-cgi/challenge-platform/h/b.js\"></script></html>",
                        mediaType = "text/html",
                        headers = mapOf("cf-mitigated" to "challenge"),
                    ),
                )
            assertEquals(SiteError.Cloudflare, error)
        }

    @Test
    fun `a plain 403 still asks the user to sign in`() =
        runTest {
            val error =
                publishExpectingError(
                    StaticResponseInterceptor(code = 403, body = """{"success":false}"""),
                )
            assertEquals(SiteError.LoginRequired, error)
        }

    @Test
    fun `a draft written before 阅读权限 became a range still opens`() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"title":"标题","body":"正文","permission":"LEVEL_ONE","savedAtMillis":1}"""

        val draft = json.decodeFromString<PostDraft>(legacy)

        assertEquals(PostPermission(1), draft.permission)
        assertEquals("标题", draft.title)
        // And it goes back out as the number the site actually takes.
        val rewritten = json.parseToJsonElement(json.encodeToString(draft)).jsonObject
        assertEquals(1, rewritten.getValue("permission").jsonPrimitive.int)
    }

    @Test
    fun `every offered level is one the account has reached`() {
        assertEquals(listOf(0, 1, 2, 255), PostPermission.options(selfRank = 2).map { it.wireValue })
        assertEquals(listOf(0, 1, 255), PostPermission.options(selfRank = null).map { it.wireValue })
        assertEquals(listOf(0, 255), PostPermission.options(selfRank = 0).map { it.wireValue })
    }

    private suspend fun TestScope.publishExpectingError(interceptor: Interceptor): SiteError {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository =
            DefaultPostComposerRepository(
                context = context,
                okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                dispatchers = AppDispatchers(dispatcher, dispatcher),
                clock = AppClock { 0L },
            )
        return try {
            repository.publish(
                PostSubmission(
                    title = "title",
                    body = "body",
                    boardSlug = "sandbox",
                    permission = PostPermission.PUBLIC,
                ),
            )
            throw AssertionError("publish should have failed")
        } catch (exception: SiteException) {
            exception.error
        }
    }
}

private class StaticResponseInterceptor(
    private val code: Int,
    private val body: String,
    private val mediaType: String = "application/json",
    private val headers: Map<String, String> = emptyMap(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
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

private class RecordingPublishInterceptor : Interceptor {
    var request: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        request = chain.request()
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """{"success":true,"postId":703863}"""
                    .toResponseBody("application/json".toMediaType()),
            ).build()
    }
}

private class SuccessWithoutIdInterceptor : Interceptor {
    var feedRequest: Request? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body =
            if (request.method == "POST") {
                """{"success":true}"""
            } else {
                feedRequest = request
                """
                <ul class="post-list">
                  <li class="post-list-item">
                    <div class="post-title">
                      <a href="/post-841108-1">NodeSeek Reader Android post test</a>
                    </div>
                    <div class="post-info">
                      <a class="post-category" href="/categories/sandbox">沙盒</a>
                    </div>
                  </li>
                </ul>
                """.trimIndent()
            }
        val mediaType = if (request.method == "POST") "application/json" else "text/html"
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()
    }
}

private fun Request.bodyUtf8(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
