package io.github.nodyssey.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The invite screen needs exactly one number — the chicken balance behind the confirm dialog — so it
 * gets its own sliver of a ViewModel rather than borrowing [io.github.nodyssey.ui.assets.AssetsViewModel]:
 * borrowing shipped that screen's sign-in side effects and full growth load into a flow that shows
 * one figure, and coupled the two screens' evolution.
 */
class InviteViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _chickenCount = MutableStateFlow<Int?>(null)

    /** Null while loading or when the balance could not be read; the screen renders that honestly. */
    val chickenCount: StateFlow<Int?> = _chickenCount.asStateFlow()

    init {
        viewModelScope.launch {
            _chickenCount.value =
                runCatchingExceptCancellation { profileRepository.profile() }
                    .getOrNull()
                    ?.chickenCount
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { InviteViewModel(container.profileRepository) }
            }
    }
}
