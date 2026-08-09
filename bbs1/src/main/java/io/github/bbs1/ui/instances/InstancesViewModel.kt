package io.github.bbs1.ui.instances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.model.ForumInstance
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * One add attempt, from confirm to saved or refused.
 *
 * @property succeeded One-shot: the screen navigates on seeing it, then calls [InstancesViewModel.consumeAdd].
 */
data class AddInstanceUiState(
    val probing: Boolean = false,
    val error: ApiErrorUi? = null,
    val succeeded: Boolean = false,
)

/**
 * Held at the navigation root rather than per screen: the instance list and the home placeholder
 * are two views of the same handful of state, and one owner is what keeps them from drifting.
 */
class InstancesViewModel(
    private val repository: InstanceRepository,
    private val api: Bbs1Api,
) : ViewModel() {
    val uiState: StateFlow<InstancesUiState> =
        repository.snapshot
            .map { InstancesUiState(loading = false, instances = it.instances, currentId = it.currentId) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = InstancesUiState(loading = true),
            )

    private val _addState = MutableStateFlow(AddInstanceUiState())
    val addState: StateFlow<AddInstanceUiState> = _addState.asStateFlow()

    /**
     * Probes the site before saving it: a `meta` round trip proves the address is a bbs1org site
     * with the API plugin enabled, and its `site.name` becomes the display name when the user left
     * theirs blank — the site knows what it is called better than its hostname does.
     *
     * [baseUrl] is already normalized — the dialog validates with [io.github.bbs1.data.normalizeInstanceUrl].
     */
    fun add(baseUrl: String, name: String?) {
        if (_addState.value.probing) return
        _addState.value = AddInstanceUiState(probing = true)
        viewModelScope.launch {
            try {
                val meta = api.meta(baseUrl)
                repository.add(baseUrl, name ?: meta.site.name.takeIf { it.isNotBlank() })
                _addState.value = AddInstanceUiState(succeeded = true)
            } catch (e: Bbs1ApiException) {
                _addState.value = AddInstanceUiState(error = e.toUi())
            }
        }
    }

    /** The dialog acknowledges a finished attempt — success after navigating, failure on dismiss. */
    fun consumeAdd() {
        _addState.value = AddInstanceUiState()
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun select(id: String) {
        viewModelScope.launch { repository.select(id) }
    }
}
