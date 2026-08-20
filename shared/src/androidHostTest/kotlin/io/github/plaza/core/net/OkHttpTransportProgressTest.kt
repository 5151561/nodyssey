package io.github.plaza.core.net

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That an upload's progress actually moves, which is the half of [UploadProgress] a fake cannot hold.
 *
 * The clients above the transport are tested against a recorded one, and all a recorded transport can
 * say is that a listener reached it. Whether the ring the user watches *moves* is a property of this
 * file's `ProgressRequestBody`: a body written in one call reports 0% and then 100%, which is a
 * decoration rather than a signal, and chunking is the whole reason that class exists.
 *
 * Driven through a real [OkHttpClient] with a terminal interceptor that reads the body — so the
 * writes counted here are the writes OkHttp makes, not writes this test arranged.
 */
class OkHttpTransportProgressTest {
    private val client = OkHttpClient
        .Builder()
        .addInterceptor { chain ->
            // Reading the body is what makes it be written; without this nothing is counted.
            Buffer().also { chain.request().body?.writeTo(it) }
            Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"ok":true}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

    private fun upload(bytes: Int) = HttpRequest(
        url = "https://img.example.invalid/upload",
        method = "POST",
        body = HttpBody.Multipart(
            fields = mapOf("token" to "abc"),
            fileField = "image",
            fileName = "photo.webp",
            fileBytes = ByteArray(bytes) { 1 },
            fileMimeType = "image/webp",
        ),
    )

    @Test
    fun `a large body reports progress in steps rather than only at the ends`() = runTest {
        val seen = mutableListOf<Float>()

        OkHttpTransport(client).execute(upload(400_000), seen::add)

        assertTrue(seen.size > 2, "the ring has to move; was $seen")
        assertEquals(0f, seen.first())
        assertEquals(1f, seen.last())
        assertEquals(seen.sorted(), seen, "progress may not run backwards")
    }

    /** Nobody listening is the ordinary case, and it must not cost the body a wrapper. */
    @Test
    fun `a request with no listener still sends its body`() = runTest {
        val response = OkHttpTransport(client).execute(upload(64))

        assertEquals(200, response.code)
    }

    /** The text fields are a rounding error against the file, and the ring is about the file. */
    @Test
    fun `only the file part is counted`() = runTest {
        val seen = mutableListOf<Float>()

        OkHttpTransport(client).execute(upload(64), seen::add)

        // 64 bytes goes out in one chunk, so this is the shortest legal report: start and finish.
        assertEquals(listOf(0f, 1f), seen)
    }
}
