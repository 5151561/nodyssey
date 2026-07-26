package io.github.nsreader.ui.viewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the gallery-save side effect so it outlives the composition: launched from the screen's own
 * scope, a rotation mid-write cancelled the download and dropped the outcome the user was owed.
 */
class ImageViewerViewModel(
    private val appContext: Context,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _saveOutcome = MutableStateFlow<SaveOutcome?>(null)

    /** Null until a save has run; then the latest save's outcome, for the screen to word. */
    val saveOutcome: StateFlow<SaveOutcome?> = _saveOutcome.asStateFlow()

    fun save(url: String) {
        viewModelScope.launch {
            _saveOutcome.value = saveImageToGallery(appContext, url, dispatchers)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val application =
                        checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    ImageViewerViewModel(application, container.dispatchers)
                }
            }
    }
}
