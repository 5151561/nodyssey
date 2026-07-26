package io.github.nsreader.data.composer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import kotlinx.coroutines.test.StandardTestDispatcher
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
