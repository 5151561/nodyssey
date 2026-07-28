package io.github.nodyssey.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The transfer form: amount, recipient uid, reference id.
 *
 * Three text fields rather than three numbers because a half-typed uid is a real state the user passes
 * through, and [isComplete] is what decides whether the confirmation step may be reached at all.
 */
data class TransferForm(
    val amount: String = "",
    val recipientUid: String = "",
    val refId: String = "",
) {
    val amountValue: Int? get() = amount.trim().toIntOrNull()?.takeIf { it > 0 }
    val recipientValue: Long? get() = recipientUid.trim().toLongOrNull()?.takeIf { it > 0 }
    val refValue: Long? get() = refId.trim().toLongOrNull()?.takeIf { it > 0 }

    val isComplete: Boolean get() = amountValue != null && recipientValue != null && refValue != null
}

data class StardustUiState(
    val isLoading: Boolean = true,
    val error: NodeSeekError? = null,
    /** Needed to reach the site's own ledger, whose URL is per-member. */
    val uid: Long? = null,
    val balance: Int? = null,
    val entries: List<StardustEntry> = emptyList(),
    val transferOpen: Boolean = false,
    val confirmOpen: Boolean = false,
    val form: TransferForm = TransferForm(),
) {
    /** How far the balance falls short of the amount typed, or null when it covers it. */
    val shortfall: Int?
        get() {
            val amount = form.amountValue ?: return null
            val balance = balance ?: return null
            return (amount - balance).takeIf { it > 0 }
        }
}

/**
 * State holder for 星辰.
 *
 * The balance comes from the account endpoint, which does publish it; the ledger comes from a page the
 * site renders client-side, which does not. So the balance card is real and the list says it is not
 * wired — a distinction worth keeping visible, since the balance is what a transfer depends on.
 */
class StardustViewModel(
    private val profileRepository: ProfileRepository,
    private val stardustRepository: StardustRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StardustUiState())
    val uiState: StateFlow<StardustUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val profile =
                    runCatchingExceptCancellation { profileRepository.profile() }.getOrNull()
                runCatchingExceptCancellation { stardustRepository.entries() }
                    .onSuccess { entries ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = null,
                                uid = profile?.uid,
                                balance = profile?.starCount,
                                entries = entries,
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                uid = profile?.uid,
                                balance = profile?.starCount,
                                error = throwable.toNodeSeekError(),
                            )
                        }
                    }
            }
    }

    fun openTransfer() = _uiState.update { it.copy(transferOpen = true) }

    fun dismissTransfer() = _uiState.update { it.copy(transferOpen = false, confirmOpen = false) }

    fun updateForm(form: TransferForm) = _uiState.update { it.copy(form = form) }

    fun requestConfirm() {
        if (!_uiState.value.form.isComplete) return
        _uiState.update { it.copy(confirmOpen = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirmOpen = false) }

    /** Clears the form after the transfer has been handed to the site's own page. */
    fun transferHandedOff() =
        _uiState.update { it.copy(transferOpen = false, confirmOpen = false, form = TransferForm()) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    StardustViewModel(
                        profileRepository = container.profileRepository,
                        stardustRepository = container.stardustRepository,
                    )
                }
            }
    }
}
