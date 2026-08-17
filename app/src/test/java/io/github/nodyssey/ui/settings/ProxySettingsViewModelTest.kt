package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyConfigProblem
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxyScope
import io.github.nodyssey.data.proxy.ProxySettings
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProxySettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeProxySettings()
    private val tester = FakeProxyConnectionTester()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The screen is a draft. A host typed halfway is not a proxy anyone asked to route through, and
     * neither is a switch that is one tap into a longer edit.
     */
    @Test
    fun `nothing reaches storage until 保存`() = runTest(dispatcher) {
        val viewModel = ProxySettingsViewModel(settings, tester)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("789")
        viewModel.setForumOnly(true)
        advanceUntilIdle()

        assertEquals(ProxyConfig(), settings.saved.value)
    }

    @Test
    fun `保存 commits the draft, scope included`() = runTest(dispatcher) {
        val viewModel = ProxySettingsViewModel(settings, tester)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.updateHost(" 127.0.0.1 ")
        viewModel.updatePort("7890")
        viewModel.setForumOnly(true)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            ProxyConfig(enabled = true, host = "127.0.0.1", port = 7890, scope = ProxyScope.FORUM_ONLY),
            settings.saved.value,
        )
        assertNull(viewModel.uiState.value.problem)
    }

    /** An address with no port is the typo this screen exists to catch before OkHttp does. */
    @Test
    fun `an incomplete address is refused rather than saved`() = runTest(dispatcher) {
        val viewModel = ProxySettingsViewModel(settings, tester)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.updateHost("127.0.0.1")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(ProxyConfigProblem.INVALID_PORT, viewModel.uiState.value.problem)
        assertEquals(ProxyConfig(), settings.saved.value)
        assertEquals(0, tester.calls)
    }

    /** 测试连接 goes through the client the forum uses, so it has to save before it can mean anything. */
    @Test
    fun `测试连接 saves first, then reports what the round trip said`() = runTest(dispatcher) {
        val viewModel = ProxySettingsViewModel(settings, tester)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.updateHost("127.0.0.1")
        viewModel.updatePort("7890")
        viewModel.test()
        advanceUntilIdle()

        assertEquals("127.0.0.1", settings.saved.value.host)
        assertEquals(1, tester.calls)
        assertEquals(false, viewModel.uiState.value.testing)
    }
}

private class FakeProxySettings : ProxySettings {
    val saved = MutableStateFlow(ProxyConfig())

    override val config: Flow<ProxyConfig> = saved

    override suspend fun save(config: ProxyConfig) {
        saved.value = config
    }
}

private class FakeProxyConnectionTester : ProxyConnectionTester {
    var calls = 0
        private set

    override suspend fun test(): Result<Unit> {
        calls++
        return Result.success(Unit)
    }
}
