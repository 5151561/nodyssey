package io.github.nodyssey.data.imagehost

import io.github.nodyssey.core.NodeImageSite
import io.github.plaza.core.net.RecordingTransport
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.host
import io.github.plaza.core.net.httpResponse
import io.github.plaza.core.net.multipart
import io.github.plaza.core.net.path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

private val NODE_IMAGE = ImageHostConfig(ImageHostProvider.NODE_IMAGE, token = API_KEY)

class NodeImageClientTest {

    @Test
    fun `upload posts the file as multipart with the key in a header`() = runTest {
        val recorder = RecordingTransport(httpResponse(UPLOAD_RESPONSE))
        val progress = mutableListOf<Float>()

        val result = repositoryFor(NODE_IMAGE, recorder).upload(bytes(40_000), progress::add)

        assertEquals("https://cdn.nodeimage.com/i/2ML3842AkHwIQxoJTifYldSYOsYfFIw5.webp", result.url)
        assertEquals("2ML3842AkHwIQxoJTifYldSYOsYfFIw5", result.id)
        assertEquals(5316L, result.sizeBytes)

        val request = recorder.request!!
        assertEquals("POST", request.method)
        assertEquals("api.nodeimage.com", request.host)
        assertEquals(NodeImageSite.UPLOAD_PATH, request.path)
        assertEquals(API_KEY, request.headers[NodeImageSite.API_KEY_HEADER])
        assertEquals("image", request.multipart.fileField, "the file must go in the part the host reads")
        assertEquals("photo.webp", request.multipart.fileName)
        // The ring is drawn from what the transport reports, so what this asserts is that the
        // listener reached it. That it then moves in more than two steps is a fact about a real
        // transport rather than about this client — see `OkHttpTransportProgressTest`.
        assertTrue(recorder.listened, "the upload listener has to reach the transport")
        assertEquals(1f, progress.last())
    }

    /** The site's own flat shape still parses, so a host-side revert would not break uploads. */
    @Test
    fun `the flat camelCase answer shape is read too`() = runTest {
        val recorder = RecordingTransport(httpResponse(LEGACY_UPLOAD_RESPONSE))

        val result = repositoryFor(NODE_IMAGE, recorder).upload(bytes())

        assertEquals("https://cdn.nodeimage.com/i/Yzk9P567htkDWMzJzUiQXvqADYMuLJs2.webp", result.url)
        assertEquals("Yzk9P567htkDWMzJzUiQXvqADYMuLJs2", result.id)
    }

    /**
     * A NodeSeek cookie must never reach this host, and the key is the only credential on the wire.
     *
     * What this test can hold is the client's half: it adds no header of its own beyond the key. The
     * other half is which transport the client is handed — a session with no cookie jar and no forum
     * referrer — and that is container wiring on both platforms (`DefaultAppContainer`'s
     * `imageHostClient`, `IosAppContainer`'s `imageHostSession`), not something reachable from here.
     */
    @Test
    fun `upload sends nothing but the key and an Accept header`() = runTest {
        val recorder = RecordingTransport(httpResponse(UPLOAD_RESPONSE))

        repositoryFor(NODE_IMAGE, recorder).upload(bytes())

        assertEquals(
            setOf("Accept", NodeImageSite.API_KEY_HEADER),
            recorder.request!!.headers.keys,
        )
    }

    @Test
    fun `a call with no stored key fails before any request goes out`() = runTest {
        val recorder = RecordingTransport(httpResponse(UPLOAD_RESPONSE))
        val repository = repositoryFor(ImageHostConfig(ImageHostProvider.NODE_IMAGE), recorder)

        val error = assertImageHostFails { repository.images() }

        assertEquals(ImageHostError.NotConfigured, error.error)
        assertNull(recorder.request, "nothing may be sent without a key")
    }

    @Test
    fun `a rejected key is told apart from a server fault`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"error":"Invalid API key"}""", code = 401))

        val error = assertImageHostFails { repositoryFor(NODE_IMAGE, recorder).upload(bytes()) }

        assertEquals(ImageHostError.InvalidKey, error.error)
        assertEquals("Invalid API key", error.detail)
    }

    /** A call that never completed is the host being unreachable, not the key being wrong. */
    @Test
    fun `a transport failure comes back as a network error`() = runTest {
        val recorder = RecordingTransport(httpResponse(UPLOAD_RESPONSE)).apply { failWith = SiteError.Network }

        val error = assertImageHostFails { repositoryFor(NODE_IMAGE, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Network, error.error)
    }

    /**
     * The host documents the API key for all four endpoints, but only upload honours it: on device,
     * a key that had just uploaded successfully got 401 「未认证，请先通过NodeSeek授权登录」 from the
     * list. Reporting that as a bad key would push the user to regenerate a working credential.
     */
    @Test
    fun `a 401 from the list means the website is needed rather than a new key`() = runTest {
        val body = httpResponse("""{"error":"未认证，请先通过NodeSeek授权登录"}""", code = 401)

        assertEquals(
            ImageHostError.SessionRequired,
            assertImageHostFails { repositoryFor(NODE_IMAGE, RecordingTransport(body)).images() }.error,
        )
        assertEquals(
            ImageHostError.SessionRequired,
            assertImageHostFails {
                repositoryFor(NODE_IMAGE, RecordingTransport(body))
                    .delete(HostedImage(id = "abc", fileName = "a", url = "u"))
            }.error,
        )
    }

    @Test
    fun `a file the host refuses is not reported as a broken key`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"error":"File too large"}""", code = 413))

        val error = assertImageHostFails { repositoryFor(NODE_IMAGE, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Rejected(413), error.error)
    }

    /**
     * `api.nodeimage.com` is behind a Cloudflare managed challenge, and the interstitial arrives as
     * a 403 — the same status a rejected key uses. Reading it as a bad key would send the user off
     * to regenerate a key that Cloudflare never let the host look at.
     */
    @Test
    fun `a Cloudflare interstitial is not mistaken for a bad key`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                "<html><head><title>Just a moment...</title></head>" +
                    "<body><script src=\"/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1\">" +
                    "</script></body></html>",
                code = 403,
            ),
        )

        val error = assertImageHostFails { repositoryFor(NODE_IMAGE, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Cloudflare, error.error)
    }

    @Test
    fun `the image list reads the host's own row shape`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """[{"imageId":"abc","filename":"abc.webp","userId":"52425",""" +
                    """"url":"https://cdn.nodeimage.com/i/abc.webp","uploadTime":"2026-07-28T04:04:11Z",""" +
                    """"size":1214,"mimetype":"image/webp"}]""",
            ),
        )

        val images = repositoryFor(NODE_IMAGE, recorder).images()

        assertEquals(1, images.size)
        assertEquals("abc", images.first().id)
        assertEquals("abc.webp", images.first().fileName)
        assertEquals(1214L, images.first().sizeBytes)
        assertEquals("image/webp", images.first().mimeType)
    }

    @Test
    fun `delete addresses the image by id`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"success":true}"""))

        repositoryFor(NODE_IMAGE, recorder)
            .delete(HostedImage(id = "abc", fileName = "abc.webp", url = "u"))

        assertEquals("DELETE", recorder.request?.method)
        assertEquals("/api/image/abc", recorder.request?.path)
    }

    /** 200 with `success:false` is a refusal the host can describe, not an upload that worked. */
    @Test
    fun `a 200 that says success false is a refusal`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"success":false,"message":"格式不支持"}"""))

        val error = assertImageHostFails { repositoryFor(NODE_IMAGE, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Rejected(200), error.error)
        assertEquals("格式不支持", error.detail)
    }
}
