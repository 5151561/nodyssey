package io.github.plaza.designsys.image

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The JVM's transport failures, which are what both Android and desktop raise.
 *
 * In `jvmCommonMain` rather than twice over in `androidMain` and `jvmMain`: `java.net` is not the
 * Android part of Android, it is the JVM part, and the two targets give identical answers here. What
 * is genuinely Android-only lives in files named for it — see `AndroidClipboard.kt`.
 */
internal actual fun platformImageFailure(throwable: Throwable): ImageLoadFailure =
    when (throwable) {
        is UnknownHostException -> ImageLoadFailure.Unreachable

        // `InterruptedIOException` covers OkHttp's own call timeout, which is not a socket timeout
        // and does not extend it. Both are "it took too long" to a reader.
        is SocketTimeoutException, is InterruptedIOException -> ImageLoadFailure.Timeout

        is IOException -> ImageLoadFailure.Connection

        else -> ImageLoadFailure.Unknown
    }
