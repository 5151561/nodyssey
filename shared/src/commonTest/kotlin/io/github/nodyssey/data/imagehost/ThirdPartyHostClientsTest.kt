package io.github.nodyssey.data.imagehost

import io.github.plaza.core.net.RecordingTransport
import io.github.plaza.core.net.httpResponse
import io.github.plaza.core.net.multipart
import io.github.plaza.core.net.path
import io.github.plaza.core.net.queryParameter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The five hosts that are not nodeimage.com.
 *
 * Each one is here for the specific thing about it that a generic implementation would get wrong:
 * Lsky reports sizes in kilobytes, EasyImage answers HTTP 200 for its own failures, sm.ms wants a
 * bare `Authorization` with no `Bearer`, imgbb wants the key in the query string, and the custom
 * host is told where to look rather than knowing.
 */
class ThirdPartyHostClientsTest {

    // ---- 兰空图床 Lsky Pro -------------------------------------------------------------------

    private val lsky = ImageHostConfig(
        provider = ImageHostProvider.LSKY_PRO,
        siteUrl = "https://img.example.com/",
        token = "1|abcdefg",
    )

    @Test
    fun `lsky posts to the v1 route with a bearer token and the file in file`() = runTest {
        val recorder = RecordingTransport(httpResponse(LSKY_UPLOAD))

        val result = repositoryFor(lsky, recorder).upload(bytes())

        assertEquals("https://img.example.com/i/2026/08/12/abc.png", result.url)
        assertEquals("abc", result.id)
        val request = recorder.request!!
        // The trailing slash on the configured site URL must not become a double slash in the path.
        assertEquals("/api/v1/upload", request.path)
        assertEquals("Bearer 1|abcdefg", request.headers["Authorization"])
        assertEquals("file", request.multipart.fileField)
    }

    /** A token pasted with the prefix already on it must not be sent as `Bearer Bearer …`. */
    @Test
    fun `lsky does not double the bearer prefix`() = runTest {
        val recorder = RecordingTransport(httpResponse(LSKY_UPLOAD))

        repositoryFor(lsky.copy(token = "Bearer 1|abcdefg"), recorder).upload(bytes())

        assertEquals("Bearer 1|abcdefg", recorder.request?.headers?.get("Authorization"))
    }

    /**
     * Lsky stores `getSize() / 1024`, so its `size` is kilobytes as a float. Read as bytes, a 2 MB
     * photo would show up as 2 KB and the 图床管理 total as a rounding error.
     */
    @Test
    fun `lsky sizes are kilobytes rather than bytes`() = runTest {
        val result = repositoryFor(lsky, RecordingTransport(httpResponse(LSKY_UPLOAD))).upload(bytes())

        assertEquals(117_146L, result.sizeBytes)
    }

    /** This host answers HTTP 200 with `status:false` for a refusal it can describe. */
    @Test
    fun `lsky reports a 200 refusal as a rejected file`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"status":false,"message":"图片格式不允许","data":[]}"""))

        val error = assertImageHostFails { repositoryFor(lsky, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Rejected(200), error.error)
        assertEquals("图片格式不允许", error.detail)
    }

    @Test
    fun `lsky reads its paginated list and deletes by key`() = runTest {
        val listing = RecordingTransport(
            httpResponse(
                """{"status":true,"message":"success","data":{"current_page":1,"data":[""" +
                    """{"key":"abc","name":"abc.png","origin_name":"照片.png","size":114.4,""" +
                    """"mimetype":"image/png","date":"2026-08-12 09:31:00",""" +
                    """"links":{"url":"https://img.example.com/i/abc.png"}}]}}""",
            ),
        )
        val images = repositoryFor(lsky, listing).images()

        assertEquals(1, images.size)
        assertEquals("照片.png", images.first().fileName)
        assertEquals(117_146L, images.first().sizeBytes)

        val deletion = RecordingTransport(httpResponse("""{"status":true,"message":"删除成功"}"""))
        repositoryFor(lsky, deletion).delete(images.first())

        assertEquals("DELETE", deletion.request?.method)
        assertEquals("/api/v1/images/abc", deletion.request?.path)
    }

    // ---- 简单图床 EasyImage -----------------------------------------------------------------

    private val easyImage = ImageHostConfig(
        provider = ImageHostProvider.EASY_IMAGE,
        siteUrl = "https://img.example.com",
        token = "tok123",
    )

    @Test
    fun `easyimage sends the token as a form field beside the image`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """{"result":"success","code":200,"url":"https://img.example.com/i/2026/08/a.jpg",""" +
                    """"srcName":"a","thumb":"https://img.example.com/i/2026/08/a.th.jpg","message":"success"}""",
            ),
        )

        val result = repositoryFor(easyImage, recorder).upload(bytes())

        assertEquals("https://img.example.com/i/2026/08/a.jpg", result.url)
        val request = recorder.request!!
        assertEquals("/api/index.php", request.path)
        assertEquals(mapOf("token" to "tok123"), request.multipart.fields)
        assertEquals("image", request.multipart.fileField)
    }

    /**
     * This uploader never sets an HTTP status, so a failure arrives as a 200 whose body says
     * otherwise. Trusting the status alone would report a rejected file as a successful upload.
     */
    @Test
    fun `easyimage failures arrive as HTTP 200 and are still failures`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """{"result":"failed","code":204,"message":"没有选择上传的文件"}""",
            ),
        )

        val error = assertImageHostFails { repositoryFor(easyImage, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Rejected(204), error.error)
        assertEquals("没有选择上传的文件", error.detail)
    }

    /** Upload is the only endpoint it publishes; the screen must be told, not shown an empty list. */
    @Test
    fun `easyimage refuses to pretend it has a listing`() = runTest {
        val recorder = RecordingTransport(httpResponse("{}"))

        assertEquals(
            ImageHostError.Unsupported,
            assertImageHostFails { repositoryFor(easyImage, recorder).images() }.error,
        )
        assertNull(recorder.request, "no request may go out for an endpoint that does not exist")
    }

    // ---- sm.ms ------------------------------------------------------------------------------

    private val smms = ImageHostConfig(ImageHostProvider.SMMS, token = "smtoken")

    @Test
    fun `smms authorizes without a bearer prefix and posts smfile`() = runTest {
        val recorder = RecordingTransport(httpResponse(SMMS_UPLOAD))

        val result = repositoryFor(smms, recorder).upload(bytes())

        assertEquals("https://s2.loli.net/2026/08/12/abc.png", result.url)
        assertEquals("deletehash", result.deleteToken)
        assertEquals("smtoken", recorder.request?.headers?.get("Authorization"))
        assertEquals("smfile", recorder.request!!.multipart.fileField)
    }

    /**
     * Re-uploading a file the account already holds is answered with the existing link rather than a
     * second copy. Treating that as an error would make the second attempt at the same photo look
     * broken when the host is being helpful.
     */
    @Test
    fun `smms reuses the existing link when the image is a duplicate`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """{"success":false,"code":"image_repeated","message":"Image upload repeated limit",""" +
                    """"images":"https://s2.loli.net/2026/08/12/abc.png"}""",
            ),
        )

        val result = repositoryFor(smms, recorder).upload(bytes())

        assertEquals("https://s2.loli.net/2026/08/12/abc.png", result.url)
    }

    @Test
    fun `smms deletes by hash rather than by file name`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"success":true,"code":"success"}"""))

        repositoryFor(smms, recorder).delete(
            HostedImage(id = "abc.png", fileName = "abc.png", url = "u", deleteToken = "deletehash"),
        )

        assertEquals("/api/v2/delete/deletehash", recorder.request?.path)
    }

    // ---- imgbb ------------------------------------------------------------------------------

    @Test
    fun `imgbb puts the key in the query string and reads the original url`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """{"data":{"id":"abc","title":"photo","url":"https://i.ibb.co/abc/photo.png",""" +
                    """"display_url":"https://i.ibb.co/abc/photo-small.png","size":4096,""" +
                    """"delete_url":"https://ibb.co/abc/deletetoken"},"success":true,"status":200}""",
            ),
        )

        val result = repositoryFor(
            ImageHostConfig(ImageHostProvider.IMGBB, token = "imgbbkey"),
            recorder,
        ).upload(bytes())

        // The original, not the resized copy — the forum decides how wide to draw it.
        assertEquals("https://i.ibb.co/abc/photo.png", result.url)
        assertEquals("imgbbkey", recorder.request?.queryParameter("key"))
        assertEquals("/1/upload", recorder.request?.path)
    }

    // ---- 自定义图床 ---------------------------------------------------------------------------

    private val custom = ImageHostConfig(
        provider = ImageHostProvider.CUSTOM,
        siteUrl = "https://img.example.com/api/upload",
        custom = CustomHostFields(
            fileField = "smfile",
            headerName = "X-Token",
            headerValue = "secret",
            formFields = "album=forum\nquality=90",
            urlPath = "result.files.0.link",
        ),
    )

    @Test
    fun `a custom host is posted exactly as described and read at the given path`() = runTest {
        val recorder = RecordingTransport(
            httpResponse(
                """{"result":{"files":[{"link":"https://img.example.com/i/a.png"}]}}""",
            ),
        )

        val result = repositoryFor(custom, recorder).upload(bytes())

        assertEquals("https://img.example.com/i/a.png", result.url)
        val request = recorder.request!!
        assertEquals("secret", request.headers["X-Token"])
        assertEquals("smfile", request.multipart.fileField)
        assertEquals(mapOf("album" to "forum", "quality" to "90"), request.multipart.fields)
    }

    /**
     * A relative answer written into a NodeSeek post would resolve against nodeseek.com — a broken
     * image with no clue as to why. The prefix is what the field exists for.
     */
    @Test
    fun `a relative link is completed with the prefix and an absolute one is left alone`() = runTest {
        val relative = repositoryFor(
            custom.copy(
                custom = custom.custom.copy(urlPath = "url", urlPrefix = "https://cdn.example.com/"),
            ),
            RecordingTransport(httpResponse("""{"url":"/i/2026/a.png"}""")),
        ).upload(bytes())
        assertEquals("https://cdn.example.com/i/2026/a.png", relative.url)

        val absolute = repositoryFor(
            custom.copy(
                custom = custom.custom.copy(urlPath = "url", urlPrefix = "https://cdn.example.com/"),
            ),
            RecordingTransport(httpResponse("""{"url":"https://other.example.com/a.png"}""")),
        ).upload(bytes())
        assertEquals("https://other.example.com/a.png", absolute.url)
    }

    /** A wrong path is the likeliest mistake here, so the answer itself comes back with the error. */
    @Test
    fun `a path that finds nothing carries the raw answer into the error`() = runTest {
        val recorder = RecordingTransport(httpResponse("""{"data":{"link":"https://img.example.com/i/a.png"}}"""))

        val error = assertImageHostFails { repositoryFor(custom, recorder).upload(bytes()) }

        assertEquals(ImageHostError.Unparsable, error.error)
        assertTrue(error.detail!!.contains("link"), "the body is what tells the user their path is wrong")
    }

    /** A LAN uploader with no credential at all is a legitimate configuration for this host only. */
    @Test
    fun `a custom host with no credential is still usable`() = runTest {
        val open = ImageHostConfig(
            provider = ImageHostProvider.CUSTOM,
            siteUrl = "http://192.168.1.9:8080/upload",
        )

        assertNull(open.problem())
        assertFalse(ImageHostConfig(ImageHostProvider.SMMS).isConfigured)
    }

    private companion object {
        const val LSKY_UPLOAD =
            """{"status":true,"message":"上传成功","data":{"key":"abc","name":"abc.png",""" +
                """"pathname":"i/2026/08/12/abc.png","origin_name":"照片.png","size":114.4,""" +
                """"mimetype":"image/png","extension":"png","md5":"d41d8","sha1":"da39a",""" +
                """"links":{"url":"https://img.example.com/i/2026/08/12/abc.png",""" +
                """"markdown":"![](https://img.example.com/i/2026/08/12/abc.png)"}}}"""

        const val SMMS_UPLOAD =
            """{"success":true,"code":"success","message":"Upload success.","data":{"file_id":0,""" +
                """"width":1200,"height":800,"filename":"photo.png","storename":"abc.png","size":4096,""" +
                """"path":"/2026/08/12/abc.png","hash":"deletehash",""" +
                """"url":"https://s2.loli.net/2026/08/12/abc.png",""" +
                """"delete":"https://sm.ms/delete/deletehash"},"RequestId":"x"}"""
    }
}
