package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.dns.DnsResolution
import io.github.nodyssey.data.dns.DnsResolutionTester
import io.github.nodyssey.data.dns.DohCapabilities
import io.github.nodyssey.data.dns.DohConfig
import io.github.nodyssey.data.dns.DohConfigProblem
import io.github.nodyssey.data.dns.DohProvider
import io.github.nodyssey.data.dns.DohSettings
import io.github.nodyssey.data.dns.DohSupport
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
 * Built the same way 代理设置 is, and for the same reasons: the fields are a draft committed by 保存,
 * because a server address typed halfway is not a resolver anyone asked the app to trust, and the
 * master switch is the exception that writes as it is tapped — 保存 is only offered while the switch
 * is on, so a switch that waited for it could be turned on and never off again.
 *
 * 测试解析 saves first and then asks. That is what makes the answer worth reading: it is the resolver
 * every other request in the app will use from that moment, not a resolver assembled for the test.
 */
class DohSettingsViewModel(
    private val settings: DohSettings,
    private val tester: DnsResolutionTester,
    capabilities: DohCapabilities,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DohSettingsUiState(capabilities = capabilities))
    val uiState: StateFlow<DohSettingsUiState> = _uiState.asStateFlow()

    init {
        settings.config
            .take(1)
            .onEach { config ->
                _uiState.update {
                    it.copy(
                        enabled = config.enabled,
                        provider = config.provider,
                        urlInput = config.customUrl,
                        bootstrapInput = config.customBootstrap,
                        includeIPv6 = config.includeIPv6,
                        fallbackToSystem = config.fallbackToSystem,
                    )
                }
            }.launchIn(viewModelScope)
    }

    /** The one control here that is not part of the draft — see the class KDoc. */
    fun setEnabled(value: Boolean) {
        _uiState.update { it.copy(enabled = value, problem = null, testFailure = null) }
        viewModelScope.launch { settings.setEnabled(value) }
    }

    fun setProvider(value: DohProvider) =
        _uiState.update { it.copy(provider = value, problem = null, testFailure = null, resolution = null) }

    fun updateUrl(value: String) =
        _uiState.update { it.copy(urlInput = value.trim(), problem = null, testFailure = null, resolution = null) }

    fun updateBootstrap(value: String) =
        _uiState.update { it.copy(bootstrapInput = value, problem = null, testFailure = null) }

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
    ),
    val enabled: Boolean = false,
    val provider: DohProvider = DohConfig().provider,
    val urlInput: String = "",
    val bootstrapInput: String = "",
    val includeIPv6: Boolean = true,
    val fallbackToSystem: Boolean = false,
    /** Set when a save was refused, and cleared by the next keystroke. */
    val problem: DohConfigProblem? = null,
    val testing: Boolean = false,
    /** What 测试解析 came back with, addresses and all. */
    val resolution: DnsResolution? = null,
    /** The failed lookup's exception type, or null. */
    val testFailure: String? = null,
    val message: AccountMessage? = null,
)

internal fun DohSettingsUiState.toConfig() = DohConfig(
    enabled = enabled,
    provider = provider,
    customUrl = urlInput.trim(),
    customBootstrap = bootstrapInput.trim(),
    includeIPv6 = includeIPv6,
    fallbackToSystem = fallbackToSystem,
)
