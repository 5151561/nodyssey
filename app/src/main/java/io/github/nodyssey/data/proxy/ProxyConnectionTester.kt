package io.github.nodyssey.data.proxy

import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.runCatchingExceptCancellation

/**
 * "测试连接" — one round trip through whatever [io.github.nodyssey.di.AppContainer.okHttpClient] is
 * currently routed through, so a saved proxy is verified against the exact client the forum uses,
 * not a client built specially for the test.
 */
interface ProxyConnectionTester {
    suspend fun test(): Result<Unit>
}

/** 分类列表 needs no session and no signature, which makes it the cheapest real answer the site gives. */
class NetworkProxyConnectionTester(
    private val jsonSource: JsonSource,
) : ProxyConnectionTester {
    override suspend fun test(): Result<Unit> =
        runCatchingExceptCancellation { jsonSource.getJson(NodeSeekJsonClient.PATH_CATEGORIES) }.map {}
}
