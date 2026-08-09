package io.github.nodyssey.ui.viewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppDispatchers
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
        /*
         * Takes the context as a parameter instead of reading APPLICATION_KEY from CreationExtras:
         * Navigation 3's ViewModelStoreNavEntryDecorator does not supply that key, so the lookup
         * crashed on every open.
         */
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer { ImageViewerViewModel(appContext, container.dispatchers) }
            }
        }
    }
}
