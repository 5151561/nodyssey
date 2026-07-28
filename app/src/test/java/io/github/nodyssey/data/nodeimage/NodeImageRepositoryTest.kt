package io.github.nodyssey.data.nodeimage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeImageSite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the **key-authenticated** endpoint answers — captured off the device on 2026-07-28, verbatim.
 *
 * Note `image_id` and the nested `links.direct`. Reading only the flat `url` of the *other* shape
 * (below) is what made every real upload fail with "Unparsable" after the host had already stored
 * the image, so this constant is the regression.
 */
private const val UPLOAD_RESPONSE =
    """{"success":true,"message":"Image uploaded successfully",""" +
        """"image_id":"2ML3842AkHwIQxoJTifYldSYOsYfFIw5",""" +
        """"filename":"2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp","size":5316,"links":{""" +
        """"direct":"https://cdn.nodeimage.com/i/2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp",""" +
        """"html":"<img src=\"https://cdn.nodeimage.com/i/2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp\">",""" +
        """"markdown":"![image](https://cdn.nodeimage.com/i/2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp)"}}"""

/** What the site's own cookie-authenticated uploader answers: camelCase, flat, `url` at the root. */
private const val LEGACY_UPLOAD_RESPONSE =
    """{"success":true,"message":"上传成功","imageId":"Yzk9P567htkDWMzJzUiQXvqADYMuLJs2",""" +
        """"filename":"Yzk9P567htkDWMzJzUiQXvqADYMuLJs2.webp","size":1214,""" +
        """"url":"https://cdn.nodeimage.com/i/Yzk9P567htkDWMzJzUiQXvqADYMuLJs2.webp"}"""

/** Shaped like a real key (64 hex) and deliberately not one; the tests only check it round-trips. */
private const val API_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

@RunWith(RobolectricTestRunner::class)
class NodeImageRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * DataStore is a process singleton keyed by file name, and Robolectric hands every test in this
     * class the same application context — so a key stored by one test is still there for the next.
     * Clearing up front is what keeps "no key configured" testable at all.
     */
    @Before
    fun clearStoredKey() = runTest {
        repository(RecordingInterceptor("{}")).clearApiKey()
    }

    @Test
    fun `upload posts the file as multipart with the key in a header`() = runTest {
        val recorder = RecordingInterceptor(UPLOAD_RESPONSE)
        val repository = repository(recorder)
        repository.setApiKey(API_KEY)

        val progress = mutableListOf<Float>()
        val result = repository.upload(
            NodeImageUpload(ByteArray(40_000) { 1 }, "photo.webp", "image/webp"),
            onProgress = progress::add,
        )

        assertEquals("https://cdn.nodeimage.com/i/2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp", result.url)
        assertEquals("2ML3842AkHwIQxoJTifYldSYOsYfFIw5", result.imageId)
        assertEquals(5316L, result.sizeBytes)

        val request = recorder.request!!
        assertEquals("POST", request.method)
        assertEquals("api.nodeimage.com", request.url.host)
        assertEquals(NodeImageSite.UPLOAD_PATH, request.url.encodedPath)
        assertEquals(API_KEY, request.header(NodeImageSite.API_KEY_HEADER))
        assertTrue(
            "The file must go in the part the host reads",
            recorder.bodyUtf8.contains("""name="image"""") &&
                recorder.bodyUtf8.contains("""filename="photo.webp""""),
        )
        // Chunked writes are what make the tray's ring move; a single write reports 0 then 1.
        assertTrue("progress should be reported incrementally, was $progress", progress.size > 2)
        assertEquals(1f, progress.last(), 0.001f)
    }

    /** The site's own flat shape still parses, so a host-side revert would not break uploads. */
    @Test
    fun `the flat camelCase answer shape is read too`() = runTest {
        val repository = repository(RecordingInterceptor(LEGACY_UPLOAD_RESPONSE))
        repository.setApiKey(API_KEY)

        val result = repository.upload(NodeImageUpload(ByteArray(8), "a.webp", "image/webp"))

        assertEquals("https://cdn.nodeimage.com/i/Yzk9P567htkDWMzJzUiQXvqADYMuLJs2.webp", result.url)
        assertEquals("Yzk9P567htkDWMzJzUiQXvqADYMuLJs2", result.imageId)
    }

    /**
     * A NodeSeek cookie must never reach this host. The repository gets its own client for that
     * reason, and the key is the only credential on the wire.
     */
    @Test
    fun `upload sends no cookie header`() = runTest {
        val recorder = RecordingInterceptor(UPLOAD_RESPONSE)
        val repository = repository(recorder)
        repository.setApiKey(API_KEY)
        repository.upload(NodeImageUpload(ByteArray(8), "a.webp", "image/webp"))

        assertNull(recorder.request?.header("Cookie"))
    }

    @Test
    fun `a call with no stored key fails before any request goes out`() = runTest {
        val recorder = RecordingInterceptor(UPLOAD_RESPONSE)

        val error = assertFails { repository(recorder).images() }

        assertEquals(NodeImageError.NotConfigured, error.error)
        assertNull("nothing may be sent without a key", recorder.request)
    }

    @Test
    fun `a rejected key is told apart from a server fault`() = runTest {
        val repository = repository(RecordingInterceptor("""{"error":"Invalid API key"}""", code = 401))
        repository.setApiKey(API_KEY)

        val error = assertFails {
            repository.upload(NodeImageUpload(ByteArray(8), "a.webp", "image/webp"))
        }

        assertEquals(NodeImageError.InvalidKey, error.error)
        assertEquals("Invalid API key", error.detail)
    }

    /**
     * The host documents the API key for all four endpoints, but only upload honours it: on device,
     * a key that had just uploaded successfully got 401 「未认证，请先通过NodeSeek授权登录」 from the
     * list. Reporting that as a bad key would push the user to regenerate a working credential.
     */
    @Test
    fun `a 401 from the list means the website is needed, not a new key`() = runTest {
        val repository = repository(
            RecordingInterceptor("""{"error":"未认证，请先通过NodeSeek授权登录"}""", code = 401),
        )
        repository.setApiKey(API_KEY)

        assertEquals(NodeImageError.SessionRequired, assertFails { repository.images() }.error)
        assertEquals(NodeImageError.SessionRequired, assertFails { repository.delete("abc") }.error)
    }

    @Test
    fun `a file the host refuses is not reported as a broken key`() = runTest {
        val repository = repository(RecordingInterceptor("""{"error":"File too large"}""", code = 413))
        repository.setApiKey(API_KEY)

        val error = assertFails {
            repository.upload(NodeImageUpload(ByteArray(8), "a.webp", "image/webp"))
        }

        assertEquals(NodeImageError.Rejected(413), error.error)
    }

    /**
     * `api.nodeimage.com` is behind a Cloudflare managed challenge, and the interstitial arrives as
     * a 403 — the same status a rejected key uses. Reading it as a bad key would send the user off
     * to regenerate a key that Cloudflare never let the host look at.
     */
    @Test
    fun `a Cloudflare interstitial is not mistaken for a bad key`() = runTest {
        val repository = repository(
            RecordingInterceptor(
                "<html><head><title>Just a moment...</title></head>" +
                    "<body><script src=\"/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1\">" +
                    "</script></body></html>",
                code = 403,
            ),
        )
        repository.setApiKey(API_KEY)

        val error = assertFails {
            repository.upload(NodeImageUpload(ByteArray(8), "a.webp", "image/webp"))
        }

        assertEquals(NodeImageError.Cloudflare, error.error)
    }

    @Test
    fun `the image list reads the host's own row shape`() = runTest {
        val repository = repository(
            RecordingInterceptor(
                """[{"imageId":"abc","filename":"abc.webp","userId":"52425",""" +
                    """"url":"https://cdn.nodeimage.com/i/abc.webp","uploadTime":"2026-07-28T04:04:11Z",""" +
                    """"size":1214,"mimetype":"image/webp"}]""",
            ),
        )
        repository.setApiKey(API_KEY)

        val images = repository.images()

        assertEquals(1, images.size)
        assertEquals("abc", images.first().imageId)
        assertEquals("abc.webp", images.first().fileName)
        assertEquals(1214L, images.first().sizeBytes)
        assertEquals("image/webp", images.first().mimeType)
    }

    @Test
    fun `delete addresses the image by id`() = runTest {
        val recorder = RecordingInterceptor("""{"success":true}""")
        val repository = repository(recorder)
        repository.setApiKey(API_KEY)

        repository.delete("abc")

        assertEquals("DELETE", recorder.request?.method)
        assertEquals("/api/image/abc", recorder.request?.url?.encodedPath)
    }

    @Test
    fun `clearing the key leaves nothing behind`() = runTest {
        val repository = repository(RecordingInterceptor("{}"))
        repository.setApiKey(API_KEY)
        assertEquals(API_KEY, repository.apiKey.first())

        repository.clearApiKey()

        assertNull(repository.apiKey.first())
    }

    private fun TestScope.repository(interceptor: Interceptor): NodeImageRepository =
        StandardTestDispatcher(testScheduler).let { dispatcher ->
            DefaultNodeImageRepository(
                context = context,
                okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                dispatchers = AppDispatchers(dispatcher, dispatcher),
            )
        }

    private suspend fun assertFails(block: suspend () -> Unit): NodeImageException =
        try {
            block()
            throw AssertionError("call should have failed")
        } catch (exception: NodeImageException) {
            exception
        }
}

private class RecordingInterceptor(
    private val body: String,
    private val code: Int = 200,
) : Interceptor {
    var request: Request? = null
    var bodyUtf8: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val outgoing = chain.request()
        request = outgoing
        bodyUtf8 = Buffer().also { outgoing.body?.writeTo(it) }.readUtf8()
        return Response.Builder()
            .request(outgoing)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
