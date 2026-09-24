package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.dns.DefaultDohServers
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohConfigProblem
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohServer
import io.github.nodyssey.data.dns.DohServerProblem
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

    /** Android's answers by default; Apple's — one server at a time — where a test says so. */
    private fun viewModel(ordered: Boolean = true) =
        DohSettingsViewModel(
            settings,
            tester,
            DohCapabilities(canChooseRecordTypes = ordered, canFallBackToSystem = ordered, triesServersInOrder = ordered),
        )

    private fun DohSettingsViewModel.ids() = uiState.value.servers.map { it.id }

    private fun DohSettingsViewModel.ticked() = uiState.value.servers.filter { it.checked }.map { it.id }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The screen is a draft: a server address typed halfway is not one to start resolving through. */
    @Test
    fun `no change reaches storage until 保存`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleServer("GOOGLE")
        viewModel.moveServer("GOOGLE", 0)
        viewModel.addServer()
        viewModel.updateEditorUrl("https://doh.example/dns-query")
        viewModel.confirmEditor()
        viewModel.setIncludeIPv6(false)
        advanceUntilIdle()

        assertEquals(DohConfig(), settings.saved.value)
    }

    /** 保存 is only offered while the switch is on, so the switch cannot be part of the draft. */
    @Test
    fun `关掉主开关不用点保存`() = runTest(dispatcher) {
        val stored = DohConfig(enabled = true, servers = DefaultDohServers.reversed())
        settings.saved.value = stored
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(stored.copy(enabled = false), settings.saved.value)
    }

    /** What is saved is the order on screen: that is the order `AppDns` asks them in. */
    @Test
    fun `保存 commits the servers in the order they were arranged`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.toggleServer("CLOUDFLARE")
        viewModel.moveServer("CLOUDFLARE", 0)
        viewModel.setFallbackToSystem(true)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("CLOUDFLARE", "ALIDNS", "DNSPOD"), settings.saved.value.chain.map { it.id })
        assertEquals(true, settings.saved.value.fallbackToSystem)
        assertNull(viewModel.uiState.value.problem)
    }

    /**
     * Ticking joins the end of the ticked block and unticking heads the rest, so the ticked rows stay
     * together on top — where their position on screen is their place in the order.
     */
    @Test
    fun `a ticked server is tried last and an unticked one leaves the order`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleServer("GOOGLE")
        assertEquals(listOf("ALIDNS", "DNSPOD", "GOOGLE", "CLOUDFLARE"), viewModel.ids())

        viewModel.toggleServer("ALIDNS")
        assertEquals(listOf("DNSPOD", "GOOGLE", "ALIDNS", "CLOUDFLARE"), viewModel.ids())
        assertEquals(listOf("DNSPOD", "GOOGLE"), viewModel.ticked())
    }

    /** Dragged past the last ticked row, a server would be untried without having been unticked. */
    @Test
    fun `a drag stays inside the ticked servers`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.moveServer("ALIDNS", 3)
        assertEquals(listOf("DNSPOD", "ALIDNS", "CLOUDFLARE", "GOOGLE"), viewModel.ids())

        viewModel.moveServer("GOOGLE", 0)
        assertEquals(listOf("DNSPOD", "ALIDNS", "CLOUDFLARE", "GOOGLE"), viewModel.ids())
    }

    /** A stored list from anywhere but this screen is shown ticked-first, so a drag means what it shows. */
    @Test
    fun `a stored list with ticked servers below unticked ones opens ticked first`() = runTest(dispatcher) {
        settings.saved.value = DohConfig(
            servers = listOf(
                DohServer.preset(DohProvider.CLOUDFLARE),
                DohServer.preset(DohProvider.GOOGLE, checked = true),
                DohServer.preset(DohProvider.ALIDNS),
                DohServer.preset(DohProvider.DNSPOD, checked = true),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("GOOGLE", "DNSPOD", "CLOUDFLARE", "ALIDNS"), viewModel.ids())
    }

    @Test
    fun `an added server is ticked and tried after the ones already ticked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addServer()
        viewModel.updateEditorUrl(" https://doh.example/dns-query ")
        viewModel.updateEditorBootstrap("10.0.0.53")
        viewModel.confirmEditor()

        val added = viewModel.uiState.value.servers[2]
        assertEquals(listOf("ALIDNS", "DNSPOD", added.id), viewModel.ticked())
        assertEquals("https://doh.example/dns-query", added.url)
        assertEquals(listOf("10.0.0.53"), added.bootstrap)
        assertNull(viewModel.uiState.value.editing)
    }

    @Test
    fun `a server that is not an https url is refused rather than added`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addServer()
        viewModel.updateEditorUrl("http://doh.example/dns-query")
        viewModel.confirmEditor()

        assertEquals(DohServerProblem.INVALID_URL, viewModel.uiState.value.editing?.problem)
        assertEquals(DefaultDohServers, viewModel.uiState.value.servers)
    }

    /**
     * A preset put back to its own addresses stores nothing of its own, so a later build that corrects
     * those addresses reaches it; a stored copy of today's list would pin it to them for good.
     */
    @Test
    fun `a preset put back to its defaults follows the preset again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openServer("ALIDNS")
        viewModel.updateEditorBootstrap("223.5.5.5")
        viewModel.confirmEditor()
        assertEquals("223.5.5.5", viewModel.uiState.value.servers.first { it.id == "ALIDNS" }.bootstrapOverride)

        viewModel.openServer("ALIDNS")
        viewModel.resetEditorBootstrap()
        viewModel.confirmEditor()
        assertNull(viewModel.uiState.value.servers.first { it.id == "ALIDNS" }.bootstrapOverride)
    }

    @Test
    fun `保存 with nothing ticked is refused`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.toggleServer("ALIDNS")
        viewModel.toggleServer("DNSPOD")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(DohConfigProblem.NO_SERVER, viewModel.uiState.value.problem)
        // The switch wrote itself; the empty list it refused did not land.
        assertEquals(DohConfig(enabled = true), settings.saved.value)
        assertEquals(0, tester.calls)
    }

    /**
     * Apple takes one server. The defaults tick two, so the screen there opens with the one already in
     * use — the first — and picking another unpicks it without moving any row.
     */
    @Test
    fun `where one server is all there can be picking one unpicks the rest`() = runTest(dispatcher) {
        val viewModel = viewModel(ordered = false)
        advanceUntilIdle()
        assertEquals(listOf("ALIDNS"), viewModel.ticked())

        viewModel.toggleServer("GOOGLE")
        viewModel.setEnabled(true)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf("GOOGLE"), viewModel.ticked())
        assertEquals(DefaultDohServers.map { it.id }, viewModel.ids())
        assertEquals(listOf("GOOGLE"), settings.saved.value.chain.map { it.id })
    }

    /** Deleting the one server in use must not leave a list of radio buttons with none of them on. */
    @Test
    fun `deleting the server in use hands the choice to the first one left`() = runTest(dispatcher) {
        val viewModel = viewModel(ordered = false)
        advanceUntilIdle()
        viewModel.addServer()
        viewModel.updateEditorUrl("https://doh.example/dns-query")
        viewModel.confirmEditor()
        val added = viewModel.ticked().single()

        viewModel.openServer(added)
        viewModel.deleteEditorServer()

        assertEquals(listOf("ALIDNS"), viewModel.ticked())
        assertEquals(DefaultDohServers.map { it.id }, viewModel.ids())
    }

    /** 测试解析 asks the live resolver, so it has to save the draft before the answer means anything. */
    @Test
    fun `测试解析 saves first then reports the addresses`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setEnabled(true)
        viewModel.toggleServer("GOOGLE")
        viewModel.test()
        advanceUntilIdle()

        assertEquals(listOf("ALIDNS", "DNSPOD", "GOOGLE"), settings.saved.value.chain.map { it.id })
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

        viewModel.toggleServer("CLOUDFLARE")
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
