package io.github.nodyssey.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.runCatchingExceptCancellation
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
 * Both tabs are the whole list in one shot, because the endpoint behind them is: neither
 * `/api/fans/follow` nor `/api/fans/fans` takes a page, and the site renders everything it returns.
 * `hasNextPage` is therefore always false here, and that is a fact about the site rather than a
 * simplification.
 *
 * The rows carry no 关注/取关 button. The site has both writes, but the flag that says which of the two
 * a row needs is only known for certain from the space page's own payload — so the row is a link there,
 * where the button can be right rather than probably right.
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
                            it.listFor(tab).copy(isLoading = false, error = throwable.toSiteError()),
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
