package io.github.nodyssey.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.CommunityRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CommunityStatsUiState {
    data object Loading : CommunityStatsUiState

    data class Content(val memberCount: Long) : CommunityStatsUiState

    data object Error : CommunityStatsUiState
}

class AboutCommunityViewModel(
    private val repository: CommunityRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<CommunityStatsUiState>(CommunityStatsUiState.Loading)
    val uiState: StateFlow<CommunityStatsUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                mutableUiState.value = CommunityStatsUiState.Loading
                runCatchingExceptCancellation { repository.memberCount() }
                    .onSuccess { count ->
                        mutableUiState.value = CommunityStatsUiState.Content(count)
                    }.onFailure {
                        mutableUiState.value = CommunityStatsUiState.Error
                    }
            }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AboutCommunityViewModel(container.communityRepository) }
        }
    }
}
