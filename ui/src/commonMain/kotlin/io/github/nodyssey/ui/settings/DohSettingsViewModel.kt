package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import io.github.nodyssey.data.dns.DohSupport
import io.github.nodyssey.data.dns.dohServerProblem
import io.github.nodyssey.data.dns.nextCustomId
import io.github.nodyssey.data.dns.parseBootstrapAddresses
import io.github.nodyssey.data.dns.problem
import io.github.nodyssey.ui.account.AccountMessage
import io.github.nodyssey.ui.account.toAccountMessage
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.doh_saved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 加密 DNS.
 *
 * Built the same way 代理设置 is, and for the same reasons: the list is a draft committed by 保存,
 * because a server address typed halfway is not a resolver anyone asked the app to trust, and the
 * master switch is the exception that writes as it is tapped — 保存 is only offered while the switch
 * is on, so a switch that waited for it could be turned on and never off again.
 *
 * One server's details — its address, its bootstrap addresses — are a draft inside that draft
 * ([DohSettingsUiState.editing]), checked when its sheet is closed with 确定 rather than at 保存, so
 * the list itself never holds a row that could not be saved.
 *
 * 测试解析 saves first and then asks. That is what makes the answer worth reading: it is the resolver
 * every other request in the app will use from that moment, not a resolver assembled for the test.
 */
class DohSettingsViewModel(
    private val settings: DohSettings,
    private val tester: DnsResolutionTester,
    capabilities: DohCapabilities,
) : ViewModel() {
    // Seeded in this platform's shape, so the frame before the stored list arrives does not show two
    // radio buttons on where only one server can be used.
    private val _uiState =
        MutableStateFlow(
            DohSettingsUiState(
                capabilities = capabilities,
                servers = DefaultDohServers.forScreen(capabilities.triesServersInOrder),
            ),
        )
    val uiState: StateFlow<DohSettingsUiState> = _uiState.asStateFlow()

    private val ordered = capabilities.triesServersInOrder

    init {
        settings.config
            .take(1)
            .onEach { config ->
                _uiState.update {
                    it.copy(
                        enabled = config.enabled,
                        servers = config.servers.forScreen(ordered),
                        includeIPv6 = config.includeIPv6,
                        fallbackToSystem = config.fallbackToSystem,
                    )
                }
            }.launchIn(viewModelScope)
    }

    /** The one control here that is not part of the draft — see the class KDoc. */
    fun setEnabled(value: Boolean) {
        _uiState.update { it.copy(enabled = value, problem = null, testFailure = null, resolution = null) }
        viewModelScope.launch { settings.setEnabled(value) }
    }

    /**
     * The box, or the radio button, at the start of a row. Where the servers are tried in order it
     * ticks or unticks one; where only one can be used it picks that one.
     */
    fun toggleServer(id: String) =
        editList { if (ordered) it.withToggled(id) else it.withOnlyChecked(id) }

    /** A drag in the ticked block ended with [id] at [toIndex]. */
    fun moveServer(
        id: String,
        toIndex: Int,
    ) = editList { it.withMoved(id, toIndex) }

    fun openServer(id: String) {
        val server = _uiState.value.servers.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                editing = DohServerDraft(
                    id = server.id,
                    preset = server.preset,
                    urlInput = server.customUrl,
                    bootstrapInput = server.bootstrapOverride ?: server.preset?.bootstrap.orEmpty().joinToString(", "),
                ),
            )
        }
    }

    fun addServer() = _uiState.update { it.copy(editing = DohServerDraft(id = null, preset = null, urlInput = "", bootstrapInput = "")) }

    fun updateEditorUrl(value: String) = editDraft { it.copy(urlInput = value.trim(), problem = null) }

    fun updateEditorBootstrap(value: String) = editDraft { it.copy(bootstrapInput = value, problem = null) }

    /** A preset's bootstrap addresses, back to the ones it ships with. */
    fun resetEditorBootstrap() =
        editDraft { draft ->
            draft.preset?.let { draft.copy(bootstrapInput = it.bootstrap.joinToString(", "), problem = null) } ?: draft
        }

    fun dismissEditor() = _uiState.update { it.copy(editing = null) }

    /** 确定: checks the sheet, and puts what it holds into the list. */
    fun confirmEditor() {
        val draft = _uiState.value.editing ?: return
        val problem = dohServerProblem(url = draft.urlInput.takeIf { draft.preset == null }, bootstrap = draft.bootstrapInput)
        if (problem != null) {
            editDraft { it.copy(problem = problem) }
            return
        }
        val bootstrap = draft.bootstrapInput.trim()
        editList { servers ->
            when {
                draft.preset != null -> servers.map { server ->
                    if (server.id != draft.id) {
                        server
                    } else {
                        // Typed back to exactly what it ships with is the same as never touched, so
                        // a later build that corrects a preset's addresses reaches this row too.
                        val isDefault = parseBootstrapAddresses(bootstrap) == draft.preset.bootstrap
                        server.copy(bootstrapOverride = bootstrap.takeUnless { isDefault })
                    }
                }

                draft.id != null -> servers.map { server ->
                    if (server.id == draft.id) server.copy(customUrl = draft.urlInput, bootstrapOverride = bootstrap) else server
                }

                // Added to be used: ticked, and last among the ticked where they are tried in turn —
                // a server somebody typed in a moment ago has not earned the 首选 slot.
                else -> {
                    val added = DohServer(id = servers.nextCustomId(), customUrl = draft.urlInput, bootstrapOverride = bootstrap)
                    val appended = servers + added
                    if (ordered) appended.withToggled(added.id) else appended.withOnlyChecked(added.id)
                }
            }
        }
        _uiState.update { it.copy(editing = null) }
    }

    /** Removes the custom server the sheet is open on. A preset has nothing to delete — untick it. */
    fun deleteEditorServer() {
        val draft = _uiState.value.editing ?: return
        if (draft.preset != null || draft.id == null) return
        editList { servers ->
            val remaining = servers.filterNot { it.id == draft.id }
            // Where exactly one server is in use, deleting it hands the choice to the first one left
            // rather than leaving a list of radio buttons with none of them on.
            if (!ordered && remaining.none(DohServer::checked) && remaining.isNotEmpty()) {
                remaining.withOnlyChecked(remaining.first().id)
            } else {
                remaining
            }
        }
        _uiState.update { it.copy(editing = null) }
    }

    fun setIncludeIPv6(value: Boolean) =
        _uiState.update { it.copy(includeIPv6 = value, testFailure = null, resolution = null) }

    fun setFallbackToSystem(value: Boolean) =
        _uiState.update { it.copy(fallbackToSystem = value, testFailure = null) }

    fun save() {
        val config = validated() ?: return
        viewModelScope.launch {
            settings.save(config)
            _uiState.update { it.copy(message = AccountMessage.Info(Res.string.doh_saved)) }
        }
    }

    fun test() {
        val config = validated() ?: return
        viewModelScope.launch {
            settings.save(config)
            _uiState.update { it.copy(testing = true, testFailure = null, resolution = null) }
            tester
                .resolve()
                .onSuccess { resolution ->
                    _uiState.update { it.copy(testing = false, resolution = resolution, testFailure = null) }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            testing = false,
                            resolution = null,
                            // The type only. A resolver's own error text can carry the server URL and
                            // the name being looked up, and a screenshot of this screen is a thing
                            // people post.
                            testFailure = throwable::class.simpleName.orEmpty(),
                            message = throwable.toAccountMessage(),
                        )
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun editList(change: (List<DohServer>) -> List<DohServer>) =
        _uiState.update { it.copy(servers = change(it.servers), problem = null, testFailure = null, resolution = null) }

    private fun editDraft(change: (DohServerDraft) -> DohServerDraft) =
        _uiState.update { state -> state.copy(editing = state.editing?.let(change)) }

    private fun validated(): DohConfig? {
        val config = _uiState.value.toConfig()
        val problem = config.problem()
        _uiState.update { it.copy(problem = problem) }
        return if (problem == null) config else null
    }

    companion object {
        /**
         * Takes the feature rather than the container, because on this screen it is the thing that
         * may not be there: `AppContainer.doh` is null on a platform that cannot apply a DoH server,
         * and the entry that leads here is what checks. A factory reading it off the container would
         * have to answer that question again, with nothing to say if it came out the other way.
         */
        fun factory(doh: DohSupport): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DohSettingsViewModel(doh.settings, doh.tester, doh.capabilities) }
            }
    }
}

data class DohSettingsUiState(
    /**
     * Which controls this platform has anything to do with — see [DohCapabilities]. Fixed for the
     * life of the screen, and part of the state rather than a parameter because it decides which
     * rows exist at all.
     */
    val capabilities: DohCapabilities = DohCapabilities(
        canChooseRecordTypes = true,
        canFallBackToSystem = true,
        triesServersInOrder = true,
    ),
    val enabled: Boolean = false,
    /** Every row, in order; the ticked ones first where [DohCapabilities.triesServersInOrder]. */
    val servers: List<DohServer> = DefaultDohServers,
    val includeIPv6: Boolean = true,
    val fallbackToSystem: Boolean = false,
    /** Set when a save was refused, and cleared by the next change to the list. */
    val problem: DohConfigProblem? = null,
    /** The sheet open on one server, or null. */
    val editing: DohServerDraft? = null,
    val testing: Boolean = false,
    /** What 测试解析 came back with, addresses and all. */
    val resolution: DnsResolution? = null,
    /** The failed lookup's exception type, or null. */
    val testFailure: String? = null,
    val message: AccountMessage? = null,
)

/**
 * One server's details as its sheet holds them.
 *
 * @param id the row being edited, or null for one being added.
 * @param preset set for a preset, whose address is shown but not typed.
 */
data class DohServerDraft(
    val id: String?,
    val preset: DohProvider?,
    val urlInput: String,
    val bootstrapInput: String,
    /** Set when 确定 was refused, and cleared by the next keystroke. */
    val problem: DohServerProblem? = null,
)

internal fun DohSettingsUiState.toConfig() = DohConfig(
    enabled = enabled,
    servers = servers,
    includeIPv6 = includeIPv6,
    fallbackToSystem = fallbackToSystem,
)

/**
 * A stored list as this platform's screen draws it. A stored list is not guaranteed to have come from
 * this screen — the defaults tick two servers, for one — so:
 *
 * - where the order is the setting, the ticked rows move to the top, so the order a reader drags is
 *   the order that is tried;
 * - where one server is all there can be, only the first ticked one stays ticked, which is the one
 *   the platform was already using ([DohConfig.chain]'s first).
 */
internal fun List<DohServer>.forScreen(ordered: Boolean): List<DohServer> =
    if (ordered) {
        sortedByDescending(DohServer::checked)
    } else {
        firstOrNull(DohServer::checked)?.let { withOnlyChecked(it.id) } ?: this
    }

/**
 * [id] ticked or unticked, and moved to the seam between the two blocks: a newly ticked server joins
 * the end of the ticked block — tried last — and a newly unticked one heads the rest. That keeps the
 * ticked rows together at the top, which is what lets their position on screen *be* their order.
 */
internal fun List<DohServer>.withToggled(id: String): List<DohServer> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this
    val server = this[index]
    val rest = toMutableList().apply { removeAt(index) }
    val seam = rest.indexOfFirst { !it.checked }.takeIf { it >= 0 } ?: rest.size
    rest.add(seam, server.copy(checked = !server.checked))
    return rest
}

/** Exactly [id] ticked, and the rows where they were — a radio list does not reorder when picked. */
internal fun List<DohServer>.withOnlyChecked(id: String): List<DohServer> = map { it.copy(checked = it.id == id) }

/**
 * [id] moved to [toIndex], held inside the ticked block: an unticked row is not tried, so there is no
 * order among those to set, and dragging a ticked one below them would untick it by stealth.
 */
internal fun List<DohServer>.withMoved(
    id: String,
    toIndex: Int,
): List<DohServer> {
    val from = indexOfFirst { it.id == id }
    if (from < 0 || !this[from].checked) return this
    val to = toIndex.coerceIn(0, count(DohServer::checked) - 1)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
