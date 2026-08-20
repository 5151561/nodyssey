package io.github.plaza.core.net

import io.github.plaza.core.toNSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.dataTaskWithURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The ceiling, exercised at the two points it can be enforced.
 *
 * No server: the delegate is driven by hand, which is the only way to assert the thing that matters —
 * that the refusal happens at the *response*, before a body exists. A test that fetched something and
 * checked the answer was null would pass just as happily against the version this replaced, which
 * refused a 40 MB picture after holding all 40 MB.
 */
class BoundedBodyDelegateTest {
    private val session = NSURLSession.sharedSession

    // Never resumed; it exists because `didReceiveData` cancels through the task it is handed.
    private fun task() = session.dataTaskWithURL(NSURL.URLWithString("http://127.0.0.1:1/never")!!)

    private fun response(declaredLength: Int?, code: Long = 200) =
        NSHTTPURLResponse(
            uRL = NSURL.URLWithString("http://example.invalid/a.png")!!,
            statusCode = code,
            HTTPVersion = "HTTP/1.1",
            headerFields =
            declaredLength?.let { mapOf<Any?, Any?>("Content-Length" to it.toString()) }
                ?: emptyMap<Any?, Any?>(),
        )

    private fun delegate(maxBytes: Long, onFinished: (UrlSessionBytes?) -> Unit = {}) =
        BoundedBodyDelegate(maxBytes = maxBytes, proxyCredential = null, onFinished = onFinished)

    private fun BoundedBodyDelegate.offer(response: NSHTTPURLResponse): NSURLSessionResponseDisposition? {
        var disposition: NSURLSessionResponseDisposition? = null
        URLSession(session, task(), response) { disposition = it }
        return disposition
    }

    @Test
    fun aDeclaredLengthOverTheCeilingIsRefusedBeforeTheBody() {
        assertEquals(NSURLSessionResponseCancel, delegate(maxBytes = 1_000).offer(response(declaredLength = 5_000)))
    }

    @Test
    fun aDeclaredLengthUnderTheCeilingIsAllowed() {
        assertEquals(NSURLSessionResponseAllow, delegate(maxBytes = 1_000).offer(response(declaredLength = 500)))
    }

    @Test
    fun anUndeclaredLengthIsAllowedThroughToBeCountedInstead() {
        assertEquals(NSURLSessionResponseAllow, delegate(maxBytes = 1_000).offer(response(declaredLength = null)))
    }

    @Test
    fun aNonSuccessIsRefusedWhateverItsLength() {
        assertEquals(
            NSURLSessionResponseCancel,
            delegate(maxBytes = 1_000).offer(response(declaredLength = 10, code = 403)),
        )
    }

    @Test
    fun anUndeclaredBodyIsStoppedAsSoonAsItCrossesTheCeiling() {
        var read: UrlSessionBytes? = null
        var finished = false
        val delegate = delegate(maxBytes = 1_000) {
            read = it
            finished = true
        }
        assertEquals(NSURLSessionResponseAllow, delegate.offer(response(declaredLength = null)))

        val chunk = ByteArray(600).toNSData()
        delegate.URLSession(session, task(), chunk)
        delegate.URLSession(session, task(), chunk)

        delegate.URLSession(session, task(), didCompleteWithError = null)
        assertEquals(true, finished)
        assertNull(read)
    }

    @Test
    fun aBodyThatStaysUnderTheCeilingIsHandedBack() {
        var read: UrlSessionBytes? = null
        val delegate = delegate(maxBytes = 1_000) { read = it }
        delegate.offer(response(declaredLength = null))
        delegate.URLSession(session, task(), ByteArray(600).toNSData())
        delegate.URLSession(session, task(), didCompleteWithError = null)
        assertEquals(600uL, read?.data?.length)
    }
}
