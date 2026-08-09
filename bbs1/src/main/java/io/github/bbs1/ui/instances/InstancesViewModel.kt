package io.github.bbs1.ui.instances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.model.ForumInstance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @property loading True only before the first DataStore read lands, so the UI can tell "no sites
 *   yet" apart from "not read yet" and not flash the empty state at every launch.
 */
data class InstancesUiState(
    // No default: both construction sites below say which side of the first read they are on, and a
    // defaulted `false` would misread as "the usual state" when the initial state is loading.
    val loading: Boolean,
    val instances: List<ForumInstance> = emptyList(),
    val currentId: String? = null,
) {
    val current: ForumInstance? get() = instances.firstOrNull { it.id == currentId }
}

/**
 * Held at the navigation root rather than per screen: the instance list and the home placeholder
 * are two views of the same handful of state, and one owner is what keeps them from drifting.
 */
class InstancesViewModel(
    private val repository: InstanceRepository,
) : ViewModel() {
    val uiState: StateFlow<InstancesUiState> =
        repository.snapshot
            .map { InstancesUiState(loading = false, instances = it.instances, currentId = it.currentId) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = InstancesUiState(loading = true),
            )

    /** [baseUrl] is already normalized — the dialog validates with [io.github.bbs1.data.normalizeInstanceUrl]. */
    fun add(baseUrl: String, name: String?) {
        viewModelScope.launch { repository.add(baseUrl, name) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun select(id: String) {
        viewModelScope.launch { repository.select(id) }
    }
}
