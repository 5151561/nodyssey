package io.github.nodyssey.data.proxy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ProxyConnectionFailureTest {
    @Test
    fun `transport failures are classified by their actionable cause`() {
        assertFailure(UnknownHostException(), ProxyConnectionFailure.Kind.DNS)
        assertFailure(SocketTimeoutException(), ProxyConnectionFailure.Kind.TIMEOUT)
        assertFailure(ConnectException(), ProxyConnectionFailure.Kind.CONNECTION)
        assertFailure(
            SocketException("SOCKS : authentication failed"),
            ProxyConnectionFailure.Kind.SOCKS_AUTHENTICATION,
        )
        assertFailure(SSLHandshakeException("certificate rejected"), ProxyConnectionFailure.Kind.TLS)
    }

    @Test
    fun `classification looks through wrapper exceptions`() {
        val failure = RuntimeException("request failed", UnknownHostException()).toProxyConnectionFailure()

        assertEquals(ProxyConnectionFailure.Kind.DNS, failure.kind)
        assertEquals("UnknownHostException", failure.exceptionName)
    }

    private fun assertFailure(throwable: Throwable, expected: ProxyConnectionFailure.Kind) {
        val failure = throwable.toProxyConnectionFailure()

        assertEquals(expected, failure.kind)
        assertEquals(throwable.javaClass.simpleName, failure.exceptionName)
    }
}
