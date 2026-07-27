package io.github.nsreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.data.TermsRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.model.TermsDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PrivacyUiState {
    data object Loading : PrivacyUiState

    data class Content(val document: TermsDocument) : PrivacyUiState

    data object Error : PrivacyUiState
}

class PrivacyViewModel(
    private val repository: TermsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<PrivacyUiState>(PrivacyUiState.Loading)
    val uiState: StateFlow<PrivacyUiState> = mutableUiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            mutableUiState.value = PrivacyUiState.Loading
            mutableUiState.value = try {
                PrivacyUiState.Content(repository.terms())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PrivacyUiState.Error
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PrivacyViewModel(container.termsRepository)
            }
        }
    }
}
