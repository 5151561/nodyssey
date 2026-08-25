package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.diagnostics.NetworkDiagnostics
import io.github.nodyssey.data.diagnostics.NetworkEnvironment
import io.github.nodyssey.data.diagnostics.ProbeResult
import io.github.nodyssey.data.diagnostics.ProbeTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 网络自检.
 *
 * Runs on arrival rather than waiting for a tap. The reader who navigated here did so to obtain a
 * number, and a screen that opens empty behind a 开始 button spends a step asking them to confirm
 * what they already said. 重新检测 is there for the second reading, which is the one a tap is
 * actually for — after switching off a VPN, or off Wi-Fi.
 */
class NetworkCheckViewModel(
    private val diagnostics: NetworkDiagnostics,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NetworkCheckUiState())
    val uiState: StateFlow<NetworkCheckUiState> = _uiState.asStateFlow()

    private var run: Job? = null

    init {
        run()
    }

    fun run() {
        // A second run started over the first would have both probes on the wire at once, which is
        // the one thing this screen must never do — see the sequencing note below.
        if (run?.isActive == true) return
        run =
            viewModelScope.launch {
                // The environment survives: it is instant, it does not change between runs of
                // the same visit, and blanking it would hide the rows most likely to hold the
                // answer for as long as the slow half takes.
                _uiState.update { it.copy(running = true, forum = null, updates = null) }
                _uiState.update { it.copy(environment = diagnostics.environment()) }
                // One at a time, deliberately. Two probes in flight together share the connection
                // and each one measures the other's contention, which on the connections this screen
                // exists for is most of what it would be measuring. The screen is slower to fill in
                // and every number on it is about one transfer.
                val forum = diagnostics.probe(ProbeTarget.FORUM)
                _uiState.update { it.copy(forum = forum) }
                val updates = diagnostics.probe(ProbeTarget.UPDATES)
                _uiState.update { it.copy(updates = updates, running = false) }
            }
    }

    companion object {
        /**
         * Takes the feature rather than the container, for the reason
         * [DohSettingsViewModel.Companion.factory] does: `AppContainer.networkDiagnostics` is null
         * where a platform has no implementation, and the entry that leads here is what checks.
         */
        fun factory(diagnostics: NetworkDiagnostics): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { NetworkCheckViewModel(diagnostics) }
            }
    }
}

/**
 * @property environment filled in first and kept across a re-run's probes, because it is instant
 *   and because blanking it would hide the rows most likely to hold the answer while the slow half
 *   runs.
 * @property forum null until [ProbeTarget.FORUM] answers.
 * @property updates null until [ProbeTarget.UPDATES] answers, which is after [forum] by design.
 */
data class NetworkCheckUiState(
    val running: Boolean = false,
    val environment: NetworkEnvironment? = null,
    val forum: ProbeResult? = null,
    val updates: ProbeResult? = null,
)
