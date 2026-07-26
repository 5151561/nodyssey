package io.github.nsreader.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.RulingRecord
import io.github.nsreader.data.RulingRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulingUiState(
    val isLoading: Boolean = true,
    val error: NodeSeekError? = null,
    val records: List<RulingRecord> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
)

/** 管理记录 — the public log of penalties and rewards, paged by number like the site's table. */
class RulingViewModel(
    private val repository: RulingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RulingUiState())
    val uiState: StateFlow<RulingUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The page the user last asked for — [RulingUiState.page] only moves on success. */
    private var requestedPage = 1

    init {
        load(1)
    }

    fun load(page: Int) {
        requestedPage = page
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                runCatchingExceptCancellation { repository.records(page) }
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                records = result.records,
                                page = result.page,
                                totalPages = result.totalPages,
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

    // Retries the page that just failed, not the one still on screen from the last success.
    fun retry() = load(requestedPage)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { RulingViewModel(container.rulingRepository) }
            }
    }
}
