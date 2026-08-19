package io.github.nodyssey.data.proxy

import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.runCatchingExceptCancellation
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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

/** A connection-test failure reduced to distinctions that point at different network layers. */
data class ProxyConnectionFailure(
    val kind: Kind,
    /** The concrete cause stays visible so a screenshot is useful even for an unclassified failure. */
    val exceptionName: String,
) {
    enum class Kind {
        DNS,
        TIMEOUT,
        CONNECTION,
        SOCKS_AUTHENTICATION,
        TLS,
        OTHER,
    }
}

/**
 * Finds the actionable transport cause without exposing exception messages, which can contain a
 * private proxy hostname or destination URL.
 */
fun Throwable.toProxyConnectionFailure(): ProxyConnectionFailure {
    val causes = generateSequence(this) { it.cause }.toList()
    val diagnosed =
        causes.firstOrNull(Throwable::isSocksAuthenticationFailure)
            ?: causes.firstOrNull { it is UnknownHostException }
            ?: causes.firstOrNull { it is SocketTimeoutException || it is InterruptedIOException }
            ?: causes.firstOrNull { it is ConnectException }
            ?: causes.firstOrNull { it is SSLException }
            ?: causes.firstOrNull { it is SocketException }
            ?: causes.last()

    val kind =
        when {
            diagnosed.isSocksAuthenticationFailure() -> ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION

            diagnosed is UnknownHostException -> ProxyConnectionFailure.Kind.DNS

            diagnosed is SocketTimeoutException || diagnosed is InterruptedIOException ->
                ProxyConnectionFailure.Kind.TIMEOUT

            diagnosed is ConnectException || diagnosed is SocketException -> ProxyConnectionFailure.Kind.CONNECTION

            diagnosed is SSLException -> ProxyConnectionFailure.Kind.TLS

            else -> ProxyConnectionFailure.Kind.OTHER
        }
    return ProxyConnectionFailure(kind, diagnosed.javaClass.simpleName)
}

private fun Throwable.isSocksAuthenticationFailure(): Boolean =
    this is SocketException &&
        message.orEmpty().contains("SOCKS", ignoreCase = true) &&
        message.orEmpty().contains("auth", ignoreCase = true)
