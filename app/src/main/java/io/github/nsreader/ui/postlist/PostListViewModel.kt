package io.github.nsreader.ui.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.Board
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.PostRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.PostSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostListViewModel(
    private val repository: PostRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Boards are owned by the repository; the ViewModel mirrors them into UiState but never
        // becomes their source of truth.
        categoryRepository.boards
            .onEach { boards -> _uiState.update { it.copy(boards = boards) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { categoryRepository.refreshIfNeeded() }
        load(page = 1, append = false)
    }

    fun selectCategory(slug: String?) {
        if (_uiState.value.categorySlug == slug) return
        _uiState.update { it.copy(categorySlug = slug, posts = emptyList(), page = 1) }
        load(page = 1, append = false)
    }

    fun refresh() = load(page = 1, append = false)

    /** Called when the list nears its end. Ignored while another page is already in flight. */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isAppending || !state.hasNextPage) return
        load(page = state.page + 1, append = true)
    }

    private fun load(page: Int, append: Boolean) {
        loadJob?.cancel()
        // Captured up front: a slow response for board A must never be applied after the user has
        // already switched to board B.
        val requestedSlug = _uiState.value.categorySlug

        _uiState.update { it.copy(isLoading = !append, isAppending = append, error = null) }

        loadJob = viewModelScope.launch {
            val result = try {
                repository.loadList(requestedSlug, page)
            } catch (e: CancellationException) {
                // Never report cancellation as a failure: the caller already moved on.
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    if (state.categorySlug != requestedSlug) return@update state
                    state.copy(
                        isLoading = false,
                        isAppending = false,
                        error = e.toNodeSeekError(),
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                if (state.categorySlug != requestedSlug) return@update state
                state.copy(
                    posts = if (append) state.posts + result.posts else result.posts,
                    page = result.page,
                    hasNextPage = result.hasNextPage,
                    isLoading = false,
                    isAppending = false,
                )
            }
        }
    }

    /** The URL a WebView should open to clear the current error. */
    fun challengeUrl(): String =
        NodeSeekSite.BASE_URL + NodeSeekSite.listPath(_uiState.value.categorySlug, 1)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PostListViewModel(container.postRepository, container.categoryRepository)
            }
        }
    }
}

data class PostListUiState(
    val boards: List<Board> = listOf(CategoryRepository.FRONT_PAGE),
    val categorySlug: String? = null,
    val posts: List<PostSummary> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val error: NodeSeekError? = null,
) {
    val selectedBoardIndex: Int
        get() = boards.indexOfFirst { it.slug == categorySlug }.coerceAtLeast(0)
}

internal fun Throwable.toNodeSeekError(): NodeSeekError =
    (this as? NodeSeekException)?.error ?: NodeSeekError.Unknown
