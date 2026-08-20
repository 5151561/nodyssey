package io.github.nodyssey.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the gallery-save side effect so it outlives the composition: launched from the screen's own
 * scope, a rotation mid-write cancelled the download and dropped the outcome the user was owed.
 */
class ImageViewerViewModel(
    private val saver: ImageGallerySaver,
) : ViewModel() {

    private val _saveOutcome = MutableStateFlow<SaveOutcome?>(null)

    /** Null until a save has run; then the latest save's outcome, for the screen to word. */
    val saveOutcome: StateFlow<SaveOutcome?> = _saveOutcome.asStateFlow()

    fun save(url: String) {
        viewModelScope.launch {
            _saveOutcome.value = saver.save(url)
        }
    }

    companion object {
        /*
         * Takes the saver as a parameter instead of reading APPLICATION_KEY from CreationExtras:
         * Navigation 3's ViewModelStoreNavEntryDecorator does not supply that key, so the lookup
         * crashed on every open. It was the Android context that used to arrive this way; since step
         * D1 the thing that needs one is `rememberImageGallerySaver`, and the route reads it.
         */
        fun factory(saver: ImageGallerySaver): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ImageViewerViewModel(saver) }
            }
    }
}
