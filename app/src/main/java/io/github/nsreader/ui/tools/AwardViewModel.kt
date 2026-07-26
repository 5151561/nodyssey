package io.github.nsreader.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.AwardRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.PostSummary
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AwardUiState(
    val isLoading: Boolean = true,
    val error: NodeSeekError? = null,
    val posts: List<PostSummary> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
)

/**
 * 推荐阅读 — the curated threads, one page at a time.
 *
 * Page-numbered rather than infinitely scrolled, matching the site: eighteen pages of hand-picked
 * threads are something people come back to at a remembered position, and a pager keeps that possible.
 */
class AwardViewModel(
    private val repository: AwardRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AwardUiState())
    val uiState: StateFlow<AwardUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The page the user last asked for — [AwardUiState.page] only moves on success. */
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
                runCatchingExceptCancellation { repository.page(page) }
                    .onSuccess { result ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                posts = result.posts,
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
                initializer { AwardViewModel(container.awardRepository) }
            }
    }
}
