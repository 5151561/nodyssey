package io.github.nodyssey.ui.assets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
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
 * The transfer form as numbers, mirrored one way out of the fields that hold the text.
 *
 * The text itself lives in [StardustViewModel]'s `TextFieldState`s. Only the parsed values are needed
 * outside them — to enable 下一步, to work out the shortfall, and to read the transfer back on the
 * confirmation step — and a half-typed uid simply parses to null, which is the state the form is in
 * while the user is still typing.
 */
data class TransferForm(
    val amountValue: Int? = null,
    val recipientValue: Long? = null,
    val refValue: Long? = null,
) {
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
    val amount = TextFieldState()
    val recipientUid = TextFieldState()
    val refId = TextFieldState()

    private val _uiState = MutableStateFlow(StardustUiState())
    val uiState: StateFlow<StardustUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
        // One-way mirror of the parsed numbers, for 下一步's enabled state and the shortfall line.
        viewModelScope.launch {
            snapshotFlow { parseForm() }.collect { form -> _uiState.update { it.copy(form = form) } }
        }
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

    fun requestConfirm() {
        // Parsed from the fields rather than read off the mirror: what the confirmation step shows
        // back has to be what is on screen at the moment 下一步 was tapped, and the mirror is a frame
        // behind. Writing it alongside `confirmOpen` also means the dialog cannot disagree with the
        // check that let it open.
        val form = parseForm()
        if (!form.isComplete) return
        _uiState.update { it.copy(form = form, confirmOpen = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirmOpen = false) }

    /** Clears the form after the transfer has been handed to the site's own page. */
    fun transferHandedOff() {
        amount.clearText()
        recipientUid.clearText()
        refId.clearText()
        _uiState.update { it.copy(transferOpen = false, confirmOpen = false, form = TransferForm()) }
    }

    private fun parseForm(): TransferForm =
        TransferForm(
            amountValue = amount.text.toString().trim().toIntOrNull()?.takeIf { it > 0 },
            recipientValue = recipientUid.text.toString().trim().toLongOrNull()?.takeIf { it > 0 },
            refValue = refId.text.toString().trim().toLongOrNull()?.takeIf { it > 0 },
        )

    companion object {
        /** The cap every field on this form rejects past. */
        const val MAX_FIELD_LENGTH = 12

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
