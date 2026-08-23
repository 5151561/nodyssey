package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohConfigProblem
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class DohSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeDohSettings()
    private val tester = FakeDnsResolutionTester()

    /** Android's answer to both, which is what this ViewModel is exercised against — see [DohCapabilities]. */
    private fun viewModel() =
        DohSettingsViewModel(
            settings,
            tester,
            DohCapabilities(canChooseRecordTypes = true, canFallBackToSystem = true),
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The screen is a draft: a server address typed halfway is not one to start resolving through. */
    @Test
    fun `no field reaches storage until 保存`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setProvider(DohProvider.CUSTOM)
        viewModel.updateUrl("https://doh.example/dns-query")
        viewModel.setIncludeIPv6(false)
        advanceUntilIdle()

        assertEquals(DohConfig(), settings.saved.value)
    }

    /** 保存 is only offered while the switch is on, so the switch cannot be part of the draft. */
    @Test
    fun `关掉主开关不用点保存`() = runTest(dispatcher) {
        settings.saved.value = DohConfig(enabled = true, provider = DohProvider.GOOGLE)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(DohConfig(enabled = false, provider = DohProvider.GOOGLE), settings.saved.value)
    }

    @Test
    fun `保存 commits the whole draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.setProvider(DohProvider.CUSTOM)
        viewModel.updateUrl(" https://doh.example/dns-query ")
        viewModel.updateBootstrap("10.0.0.53")
        viewModel.setFallbackToSystem(true)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            DohConfig(
                enabled = true,
                provider = DohProvider.CUSTOM,
                customUrl = "https://doh.example/dns-query",
                customBootstrap = "10.0.0.53",
                fallbackToSystem = true,
            ),
            settings.saved.value,
        )
        assertNull(viewModel.uiState.value.problem)
    }

    /** A preset needs nothing typed, so the fields the custom row asks for cannot hold it up. */
    @Test
    fun `a preset saves with the custom fields empty`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.setProvider(DohProvider.CLOUDFLARE)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(DohProvider.CLOUDFLARE, settings.saved.value.provider)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun `a server that is not an https url is refused rather than saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.setProvider(DohProvider.CUSTOM)
        viewModel.updateUrl("http://doh.example/dns-query")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(DohConfigProblem.INVALID_URL, viewModel.uiState.value.problem)
        // The switch wrote itself; the server it refused did not land.
        assertEquals(DohConfig(enabled = true), settings.saved.value)
        assertEquals(0, tester.calls)
    }

    /** 测试解析 asks the live resolver, so it has to save the draft before the answer means anything. */
    @Test
    fun `测试解析 saves first, then reports the addresses`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.setProvider(DohProvider.GOOGLE)
        viewModel.test()
        advanceUntilIdle()

        assertEquals(DohProvider.GOOGLE, settings.saved.value.provider)
        assertEquals(1, tester.calls)
        assertEquals(false, viewModel.uiState.value.testing)
        assertEquals(listOf("104.21.32.1"), viewModel.uiState.value.resolution?.addresses)
    }

    @Test
    fun `a failed lookup is reported as its type and cleared by the next edit`() = runTest(dispatcher) {
        tester.result = Result.failure(UnknownHostException("www.nodeseek.com"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.test()
        advanceUntilIdle()

        assertEquals("UnknownHostException", viewModel.uiState.value.testFailure)
        assertNull(viewModel.uiState.value.resolution)

        viewModel.setProvider(DohProvider.CLOUDFLARE)
        assertNull(viewModel.uiState.value.testFailure)
    }
}

private class FakeDohSettings : DohSettings {
    val saved = MutableStateFlow(DohConfig())

    override val config: Flow<DohConfig> = saved

    override suspend fun save(config: DohConfig) {
        saved.value = config
    }

    override suspend fun setEnabled(enabled: Boolean) {
        saved.value = saved.value.copy(enabled = enabled)
    }
}

private class FakeDnsResolutionTester : DnsResolutionTester {
    var calls = 0
        private set
    var result: Result<DnsResolution> =
        Result.success(DnsResolution("www.nodeseek.com", listOf("104.21.32.1"), elapsedMillis = 42))

    override suspend fun resolve(): Result<DnsResolution> {
        calls++
        return result
    }
}
