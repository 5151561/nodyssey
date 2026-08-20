package io.github.nodyssey.data.proxy

/**
 * "测试连接" — one round trip through whatever client the app is currently routed through, so a saved
 * proxy is verified against the exact client the forum uses, not a client built specially for the
 * test.
 *
 * The interface and the failure vocabulary are here; the implementation is not. Dialling the request
 * means naming an HTTP client and reading *why* it failed means naming `java.net`'s exception types,
 * so both stay in the module that has them — see `NetworkProxyConnectionTester` in `:app`.
 */
interface ProxyConnectionTester {
    suspend fun test(): Result<Unit>

    /**
     * What the failure [test] returned actually was, in the vocabulary below.
     *
     * On the tester rather than as an extension on `Throwable`, which is what it was until step D1:
     * the classification reads the exception *types* a particular client throws, and the screen that
     * shows the answer no longer has one.
     */
    fun classify(failure: Throwable): ProxyConnectionFailure
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
