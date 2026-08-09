package io.github.bbs1.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.data.authed
import io.github.bbs1.model.ForumInstance
import io.github.bbs1.model.InstanceSession
import io.github.bbs1.net.ApiForum
import io.github.bbs1.net.ApiTopicSummary
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * @property loading The full-screen first load of a site — false once content or an error is up.
 * @property error With an empty [topics] this is the whole screen; beside a loaded list it is a
 *   footer on the list (the append that failed), and the list stays.
 */
data class HomeUiState(
    val instance: ForumInstance? = null,
    val loading: Boolean = false,
    val forums: List<ApiForum> = emptyList(),
    val selectedForumId: Long? = null,
    val topics: List<ApiTopicSummary> = emptyList(),
    val hasNextPage: Boolean = false,
    val appending: Boolean = false,
    val error: ApiErrorUi? = null,
) {
    val session: InstanceSession? get() = instance?.session

    /**
     * Whether to offer the compose button at all: the server answers `can_post` per board and per
     * identity, so an account with no board open to it gets no button rather than a button that
     * opens a composer with an empty picker.
     */
    val canPost: Boolean get() = session != null && forums.any { it.canPost }
}

/**
 * The feed of whichever site is current. Watches the instance repository rather than taking a site
 * as a parameter: switching sites in the switcher must swap this screen's content out from under it,
 * and one collector resetting state is how that stays one code path.
 *
 * Signing in and out go down that same path, because the credential is part of the instance: the
 * server answers permissions per identity, so a login is a different set of forums and a different
 * feed, not the same one with a name attached.
 */
class HomeViewModel(
    private val repository: InstanceRepository,
    private val api: Bbs1Api,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var nextPage = 1

    init {
        viewModelScope.launch {
            repository.snapshot
                .map { it.current }
                .distinctUntilChanged()
                .collect { instance ->
                    // A new site invalidates everything the old one loaded, including in-flight work.
                    loadJob?.cancel()
                    _uiState.value = HomeUiState(instance = instance)
                    if (instance != null) loadFirstPage()
                }
        }
    }

    /** Re-runs the first load: the error state's retry and the app bar's refresh are the same act. */
    fun refresh() {
        val state = _uiState.value
        if (state.instance == null) return
        loadJob?.cancel()
        _uiState.value = state.copy(loading = false, topics = emptyList(), error = null, appending = false)
        loadFirstPage()
    }

    fun selectForum(forumId: Long?) {
        val state = _uiState.value
        if (state.instance == null || forumId == state.selectedForumId) return
        loadJob?.cancel()
        _uiState.value = state.copy(selectedForumId = forumId, topics = emptyList(), error = null, appending = false)
        loadFirstPage()
    }

    fun loadMore() {
        val state = _uiState.value
        val instance = state.instance ?: return
        if (state.loading || state.appending || state.error != null || !state.hasNextPage) return
        _uiState.value = state.copy(appending = true)
        loadJob = viewModelScope.launch {
            try {
                val page =
                    repository.authed(instance.id) {
                        api.topics(instance.baseUrl, state.selectedForumId, nextPage, state.session?.token)
                    }
                nextPage++
                _uiState.value = _uiState.value.copy(
                    topics = _uiState.value.topics + page.topics,
                    hasNextPage = page.hasNextPage,
                    appending = false,
                )
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(appending = false, error = e.toUi())
            }
        }
    }

    /**
     * Signs out of the current site. Nothing else is needed: dropping the credential changes the
     * instance, the collector above sees it, and the feed reloads as an anonymous reader would see it.
     */
    fun signOut() {
        val instance = _uiState.value.instance ?: return
        viewModelScope.launch { repository.clearSession(instance.id) }
    }

    /** Clears a footer error so the next scroll to the end retries the append. */
    fun retryAppend() {
        val state = _uiState.value
        if (state.topics.isEmpty() || state.error == null) return
        _uiState.value = state.copy(error = null)
        loadMore()
    }

    private fun loadFirstPage() {
        val instance = _uiState.value.instance ?: return
        val forumId = _uiState.value.selectedForumId
        val token = _uiState.value.session?.token
        nextPage = 1
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                repository.authed(instance.id) {
                    coroutineScope {
                        // The forum list is per-site, not per-filter: only fetch it when it is missing.
                        val forums = _uiState.value.forums.ifEmpty { null }
                        val forumsDeferred =
                            if (forums == null) async { api.forums(instance.baseUrl, token) } else null
                        val topicsPage = api.topics(instance.baseUrl, forumId, 1, token)
                        nextPage = 2
                        _uiState.value = _uiState.value.copy(
                            loading = false,
                            forums = forumsDeferred?.await() ?: _uiState.value.forums,
                            topics = topicsPage.topics,
                            hasNextPage = topicsPage.hasNextPage,
                        )
                    }
                }
            } catch (e: Bbs1ApiException) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.toUi())
            }
        }
    }
}
