package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.diagnostics.AppIdentity
import io.github.nodyssey.data.diagnostics.DeviceIdentity
import io.github.nodyssey.data.diagnostics.NetworkDiagnostics
import io.github.nodyssey.data.diagnostics.NetworkEnvironment
import io.github.nodyssey.data.diagnostics.NetworkTransport
import io.github.nodyssey.data.diagnostics.ProbeResult
import io.github.nodyssey.data.diagnostics.ProbeTarget
import io.github.nodyssey.data.diagnostics.ProbeTiming
import io.github.nodyssey.data.diagnostics.SessionSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 网络自检's sequencing, which is the part of this screen that can be wrong without looking wrong.
 *
 * Two probes overlapping would still fill every row in, with numbers that describe two transfers
 * sharing one connection — a screen that reports half the real speed on exactly the connections it
 * was built to measure. Nothing on screen would say so, which is why it is pinned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val diagnostics = FakeNetworkDiagnostics()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the second probe does not start until the first has answered`() = runTest(dispatcher) {
        NetworkCheckViewModel(diagnostics)
        advanceUntilIdle()

        // The forum probe is still in flight, so nothing may have asked for the second one yet —
        // two transfers on the wire at once is the failure this whole ordering exists to prevent.
        assertEquals(listOf(ProbeTarget.FORUM), diagnostics.started)

        diagnostics.answer(ProbeTarget.FORUM)
        advanceUntilIdle()
        assertEquals(listOf(ProbeTarget.FORUM, ProbeTarget.UPDATES), diagnostics.started)
    }

    @Test
    fun `asking again while a run is in flight does not start a second one`() = runTest(dispatcher) {
        val viewModel = NetworkCheckViewModel(diagnostics)
        advanceUntilIdle()

        viewModel.run()
        viewModel.run()
        advanceUntilIdle()

        assertEquals(listOf(ProbeTarget.FORUM), diagnostics.started)
    }
}

/**
 * Probes that answer only when told to, so a test can observe the gap between one starting and the
 * next. A fake that returned immediately would pass whether the ViewModel sequenced its calls or
 * fired both at once.
 */
private class FakeNetworkDiagnostics : NetworkDiagnostics {
    val started = mutableListOf<ProbeTarget>()
    private val pending = mutableMapOf<ProbeTarget, CompletableDeferred<ProbeResult>>()

    override suspend fun environment(): NetworkEnvironment =
        NetworkEnvironment(
            device = DeviceIdentity("Pixel 8", "Android 15 (API 35)"),
            appVersion = "1.2.12",
            transport = NetworkTransport.WIFI,
            vpnActive = true,
            metered = false,
            proxy = null,
            dohProvider = null,
            customTabsProvider = AppIdentity("Chrome", "com.android.chrome"),
            defaultBrowser = AppIdentity("Chrome", "com.android.chrome"),
            session = SessionSummary(
                signedIn = true,
                hasClearance = true,
                cookieNames = listOf("session", "cf_clearance"),
                webView = AppIdentity("Android System WebView 131.0.6778.39", "com.google.android.webview"),
            ),
        )

    override suspend fun probe(target: ProbeTarget): ProbeResult {
        started += target
        return pending.getOrPut(target) { CompletableDeferred() }.await()
    }

    fun answer(target: ProbeTarget) {
        pending.getOrPut(target) { CompletableDeferred() }.complete(ANSWER)
    }

    private companion object {
        val ANSWER =
            ProbeResult.Answered(
                statusCode = 200,
                timing = ProbeTiming(
                    dnsMillis = 12,
                    connectMillis = 40,
                    tlsMillis = 60,
                    firstByteMillis = 200,
                    totalMillis = 1_200,
                    bytes = 60_000,
                ),
            )
    }
}
