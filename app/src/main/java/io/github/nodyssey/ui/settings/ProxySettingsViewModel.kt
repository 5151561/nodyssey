package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.R
import io.github.nodyssey.data.proxy.ProxyConfig
import io.github.nodyssey.data.proxy.ProxyConfigProblem
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.proxy.ProxyType
import io.github.nodyssey.data.proxy.problem
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.account.AccountMessage
import io.github.nodyssey.ui.account.toAccountMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 代理设置.
 *
 * Fields are a draft, committed only on [save] — like 图床's credential fields and unlike most of the
 * rest of Settings, which write on every keystroke. A proxy is a host, a port and a type that only
 * mean something together; persisting a half-typed port the instant a digit lands would make
 * [io.github.nodyssey.di.AppContainer.okHttpClient] route through it before the user meant it to.
 */
class ProxySettingsViewModel(
    private val settings: ProxySettings,
    private val tester: ProxyConnectionTester,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProxySettingsUiState())
    val uiState: StateFlow<ProxySettingsUiState> = _uiState.asStateFlow()

    init {
        settings.config
            .onEach { config ->
                _uiState.update {
                    it.copy(
                        enabled = config.enabled,
                        type = config.type,
                        hostInput = config.host,
                        portInput = if (config.port == 0) "" else config.port.toString(),
                        usernameInput = config.username,
                        passwordInput = config.password,
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun setEnabled(value: Boolean) = _uiState.update { it.copy(enabled = value, problem = null) }

    fun setType(value: ProxyType) = _uiState.update { it.copy(type = value, problem = null) }

    fun updateHost(value: String) = _uiState.update { it.copy(hostInput = value, problem = null) }

    fun updatePort(value: String) =
        _uiState.update { it.copy(portInput = value.filter(Char::isDigit).take(PORT_DIGITS), problem = null) }

    fun updateUsername(value: String) = _uiState.update { it.copy(usernameInput = value) }

    fun updatePassword(value: String) = _uiState.update { it.copy(passwordInput = value) }

    fun save() {
        val config = validated() ?: return
        viewModelScope.launch {
            settings.save(config)
            _uiState.update { it.copy(message = AccountMessage.Info(R.string.proxy_saved)) }
        }
    }

    fun test() {
        val config = validated() ?: return
        viewModelScope.launch {
            settings.save(config)
            _uiState.update { it.copy(testing = true) }
            tester.test()
                .onSuccess {
                    _uiState.update {
                        it.copy(testing = false, message = AccountMessage.Info(R.string.proxy_test_success))
                    }
                }.onFailure { throwable ->
                    _uiState.update { it.copy(testing = false, message = throwable.toAccountMessage()) }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Validates the current draft against itself, surfacing a [ProxyConfigProblem] instead of saving. */
    private fun validated(): ProxyConfig? {
        val config = _uiState.value.toConfig()
        val problem = config.problem()
        _uiState.update { it.copy(problem = problem) }
        return if (problem == null) config else null
    }

    companion object {
        private const val PORT_DIGITS = 5

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProxySettingsViewModel(container.proxySettings, container.proxyConnectionTester) }
        }
    }
}

data class ProxySettingsUiState(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val hostInput: String = "",
    val portInput: String = "",
    val usernameInput: String = "",
    val passwordInput: String = "",
    /** Set when a save was refused, and cleared by the next keystroke. */
    val problem: ProxyConfigProblem? = null,
    val testing: Boolean = false,
    val message: AccountMessage? = null,
)

internal fun ProxySettingsUiState.toConfig() = ProxyConfig(
    enabled = enabled,
    type = type,
    host = hostInput.trim(),
    port = portInput.toIntOrNull() ?: 0,
    username = usernameInput.trim(),
    password = passwordInput,
)
