package io.github.plaza.core.image

import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.NetworkResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ImageLoadFailureTest {

    /** The post-879848 case: an image host that answers the app with Cloudflare's interstitial. */
    @Test
    fun `a cloudflare challenge is not just another 403`() {
        val failure = diagnoseImageFailure(httpException(code = 403, "cf-mitigated" to "challenge"))

        assertEquals(ImageLoadFailure.Challenge(403), failure)
    }

    /** Cloudflare has served the interstitial as a 503 too; the header decides, not the status. */
    @Test
    fun `a challenge is recognised whatever status carries it`() {
        val failure = diagnoseImageFailure(httpException(code = 503, "cf-mitigated" to "challenge"))

        assertEquals(ImageLoadFailure.Challenge(503), failure)
    }

    @Test
    fun `header matching ignores case on both name and value`() {
        val failure = diagnoseImageFailure(httpException(code = 403, "CF-Mitigated" to "CHALLENGE"))

        assertEquals(ImageLoadFailure.Challenge(403), failure)
    }

    /**
     * Every response from a Cloudflare-fronted host carries these, the successful ones included, so
     * treating them as a challenge signal would relabel every ordinary refusal as one.
     */
    @Test
    fun `cloudflare's ordinary headers do not make a response a challenge`() {
        val failure =
            diagnoseImageFailure(
                httpException(code = 403, "server" to "cloudflare", "cf-ray" to "a2cd2bae889ee428-SIN"),
            )

        assertEquals(ImageLoadFailure.Http(403), failure)
    }

    @Test
    fun `a plain refusal keeps its status`() {
        assertEquals(ImageLoadFailure.Http(404), diagnoseImageFailure(httpException(code = 404)))
    }

    @Test
    fun `an unresolvable host reads as unreachable`() {
        assertEquals(ImageLoadFailure.Unreachable, diagnoseImageFailure(UnknownHostException("img.invalid")))
    }

    @Test
    fun `both flavours of running out of time read as a timeout`() {
        assertEquals(ImageLoadFailure.Timeout, diagnoseImageFailure(SocketTimeoutException("read")))
        // OkHttp's whole-call timeout, which is *not* a SocketTimeoutException.
        assertEquals(ImageLoadFailure.Timeout, diagnoseImageFailure(InterruptedIOException("timeout")))
    }

    @Test
    fun `another transport failure reads as a connection failure`() {
        assertEquals(ImageLoadFailure.Connection, diagnoseImageFailure(EOFException("closed mid-body")))
    }

    /** Coil raises a decode failure with no type of its own — see [ImageLoadFailure.Unknown]. */
    @Test
    fun `anything else stays unknown`() {
        assertEquals(ImageLoadFailure.Unknown, diagnoseImageFailure(IllegalStateException("decode")))
        assertEquals(ImageLoadFailure.Unknown, diagnoseImageFailure(null))
    }

    private fun httpException(code: Int, vararg headers: Pair<String, String>) =
        HttpException(
            NetworkResponse(
                code = code,
                headers =
                headers
                    .fold(NetworkHeaders.Builder()) { builder, (name, value) -> builder.add(name, value) }
                    .build(),
            ),
        )
}
