package io.github.plaza.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That cancelling the coroutine stops the request, which a recorded transport cannot show: `execute`
 * blocks a thread, and only a hook on the `Call` reaches it there. Without one, 表情管理's eight-second
 * probe timeout waited for OkHttp's own timeouts instead.
 */
class OkHttpTransportCancellationTest {
    @Test
    fun `a timeout around a hanging request cancels the call`() = runBlocking {
        val cancelled = AtomicBoolean(false)
        // Stands in for a server that never answers: holds the call until it is cancelled, or until
        // a deadline far past the timeout below so a regression fails instead of hanging.
        val client = OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val deadline = System.nanoTime() + 5_000_000_000L
                while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
                if (chain.call().isCanceled()) {
                    cancelled.set(true)
                    throw IOException("Canceled")
                }
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            }.build()

        val response = withContext(Dispatchers.IO) {
            withTimeoutOrNull(200) { OkHttpTransport(client).execute(HttpRequest("https://slow.example.invalid/a.png", method = "HEAD")) }
        }

        assertNull(response)
        assertTrue(cancelled.get(), "the timeout has to reach the Call, not only the coroutine")
    }
}
