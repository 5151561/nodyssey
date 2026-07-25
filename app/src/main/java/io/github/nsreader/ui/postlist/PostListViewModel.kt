package io.github.nsreader.ui.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.ChallengeDetector
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.PostRepository
import io.github.nsreader.model.PostSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostListViewModel(
    private val repository: PostRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectCategory(slug: String?) {
        if (_uiState.value.categorySlug == slug) return
        _uiState.update { it.copy(categorySlug = slug, posts = emptyList(), page = 1) }
        refresh()
    }

    fun refresh() {
        load(page = 1, append = false)
    }

    /** Called when the list nears its end. Ignored while another page is already in flight. */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.page + 1, append = true)
    }

    private fun load(page: Int, append: Boolean) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = !append,
                isAppending = append,
                error = null,
                challenge = null,
            )
        }
        loadJob = viewModelScope.launch {
            val slug = _uiState.value.categorySlug
            runCatching { repository.loadList(slug, page) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            // Guard against a category switch that landed while this page loaded.
                            posts = if (append) state.posts + result.posts else result.posts,
                            page = result.page,
                            hasNextPage = result.hasNextPage,
                            isLoading = false,
                            isAppending = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isAppending = false,
                            error = throwable.message ?: "加载失败",
                            challenge = (throwable as? NodeSeekException)?.challenge,
                        )
                    }
                }
        }
    }

    /** The URL a WebView should open to clear the current challenge. */
    fun challengeUrl(): String =
        NodeSeekSite.BASE_URL + NodeSeekSite.listPath(_uiState.value.categorySlug, 1)
}

data class PostListUiState(
    val categorySlug: String? = null,
    val posts: List<PostSummary> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val error: String? = null,
    val challenge: ChallengeDetector.Challenge? = null,
)
