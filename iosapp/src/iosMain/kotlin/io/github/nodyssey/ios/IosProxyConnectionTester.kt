package io.github.nodyssey.ios

import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.plaza.core.runCatchingExceptCancellation

/**
 * 分类列表 needs no session and no signature, which makes it the cheapest real answer the site gives —
 * the same request `NetworkProxyConnectionTester` sends on Android, through whatever client the app
 * is currently routed through.
 *
 * **[classify] can only answer `OTHER` today, and that is a fact about the transport rather than
 * about this class.** The Android tester reads `java.net`'s exception *types* — `UnknownHostException`
 * is DNS, `SocketTimeoutException` is a timeout — and gets them because OkHttp throws them through.
 * `NSUrlSessionTransport` catches its `NSError` and raises `SiteException(SiteError.Network)`, so by
 * the time a failure reaches here the code that said which layer failed is gone. Carrying it through
 * means widening `SiteException`, which is a change to the one contract both platforms are written
 * against — not something to do in passing on the step that first launches an app.
 *
 * The screen degrades correctly: it words an unclassified failure with the exception name beside it,
 * which is why that field exists.
 */
class IosProxyConnectionTester(
    private val jsonSource: JsonSource,
) : ProxyConnectionTester {
    override suspend fun test(): Result<Unit> =
        runCatchingExceptCancellation { jsonSource.getJson(NodeSeekJsonClient.PATH_CATEGORIES) }.map {}

    override fun classify(failure: Throwable): ProxyConnectionFailure =
        ProxyConnectionFailure(
            kind = ProxyConnectionFailure.Kind.OTHER,
            // `simpleName` on Native is the class name without its package, the same thing
            // `javaClass.simpleName` gives the Android side.
            exceptionName = failure::class.simpleName ?: "Throwable",
        )
}
