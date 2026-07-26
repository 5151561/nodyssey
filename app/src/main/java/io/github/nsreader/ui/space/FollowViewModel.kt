package io.github.nsreader.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.FollowRepository
import io.github.nsreader.data.FollowUser
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FollowTab {
    FOLLOWING,
    FOLLOWERS,
}

data class FollowUiState(
    val selectedTab: FollowTab = FollowTab.FOLLOWING,
    val following: SpaceListState<FollowUser> = SpaceListState(),
    val followers: SpaceListState<FollowUser> = SpaceListState(),
) {
    fun listFor(tab: FollowTab): SpaceListState<FollowUser> =
        when (tab) {
            FollowTab.FOLLOWING -> following
            FollowTab.FOLLOWERS -> followers
        }
}

/**
 * 我的关注 / 我的粉丝 — the signed-in user's, and only theirs.
 *
 * Both tabs are lists of accounts and nothing else: no follow button, no 互关 badge, no counts. The
 * site has the two lists but exposes no action to change them, so a button here would be a control
 * that cannot work.
 */
class FollowViewModel(
    private val repository: FollowRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FollowUiState())
    val uiState: StateFlow<FollowUiState> = _uiState.asStateFlow()

    private val jobs = mutableMapOf<FollowTab, Job>()

    init {
        load(FollowTab.FOLLOWING)
    }

    fun selectTab(tab: FollowTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val list = _uiState.value.listFor(tab)
        if (!list.loaded && !list.isLoading) load(tab)
    }

    fun retry(tab: FollowTab) = load(tab)

    private fun load(tab: FollowTab) {
        jobs[tab]?.cancel()
        jobs[tab] =
            viewModelScope.launch {
                _uiState.update { it.write(tab, it.listFor(tab).copy(isLoading = true, error = null)) }
                runCatchingExceptCancellation {
                    when (tab) {
                        FollowTab.FOLLOWING -> repository.following()
                        FollowTab.FOLLOWERS -> repository.followers()
                    }
                }.onSuccess { users ->
                    _uiState.update {
                        it.write(
                            tab,
                            SpaceListState(items = users, page = 1, hasNextPage = false, loaded = true),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.write(
                            tab,
                            it.listFor(tab).copy(isLoading = false, error = throwable.toNodeSeekError()),
                        )
                    }
                }
            }
    }

    private fun FollowUiState.write(tab: FollowTab, list: SpaceListState<FollowUser>): FollowUiState =
        when (tab) {
            FollowTab.FOLLOWING -> copy(following = list)
            FollowTab.FOLLOWERS -> copy(followers = list)
        }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { FollowViewModel(container.followRepository) }
            }
    }
}
