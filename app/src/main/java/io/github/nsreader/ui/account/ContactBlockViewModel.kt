package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.R
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.data.account.EndpointNotVerifiedException
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 联系方式与屏蔽 (d6 3/4).
 *
 * Two of the site's seven groups on one page, because `#contact` is two fields and `#block` is a list
 * — separately each is a screen with more chrome than content, and neither has anything to do with
 * scrolling past the other.
 */
class ContactBlockViewModel(
    private val account: AccountSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ContactBlockUiState())
    val uiState: StateFlow<ContactBlockUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.contact() }
                .onSuccess { contact ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saved = contact,
                            email = contact.email,
                            backupEmail = contact.backupEmail,
                        )
                    }
                }.onFailure { throwable ->
                    val pending = throwable is EndpointNotVerifiedException
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            endpointPending = pending,
                            message =
                            if (pending) {
                                null
                            } else {
                                throwable.toAccountMessage(R.string.account_contact_section)
                            },
                        )
                    }
                }

            runCatchingExceptCancellation { account.blockedUsers() }
                .onSuccess { blocked -> _uiState.update { it.copy(blocked = blocked) } }
        }
    }

    fun updateEmail(value: String) = _uiState.update { it.copy(email = value.trim()) }

    fun updateBackupEmail(value: String) = _uiState.update { it.copy(backupEmail = value.trim()) }

    fun save() {
        if (saveJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canSave) return
        saveJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, message = null) }
                runCatchingExceptCancellation { account.saveContact(state.email, state.backupEmail) }
                    .onSuccess {
                        // An address that changed is unverified again until the user clicks the mail
                        // the server just sent; one that did not keeps whatever it already had.
                        val previous = state.saved
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saved =
                                AccountContact(
                                    email = state.email,
                                    emailVerified =
                                    previous?.emailVerified == true && previous.email == state.email,
                                    backupEmail = state.backupEmail,
                                    backupEmailVerified =
                                    previous?.backupEmailVerified == true &&
                                        previous.backupEmail == state.backupEmail,
                                ),
                                message = AccountMessage.Info(R.string.account_action_saved),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                message = throwable.toAccountMessage(R.string.account_contact_save),
                            )
                        }
                    }
            }
    }

    fun resendVerification(address: String) {
        viewModelScope.launch {
            runCatchingExceptCancellation { account.resendVerification(address) }
                .onSuccess {
                    _uiState.update {
                        it.copy(message = AccountMessage.Info(R.string.account_email_verification_sent))
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = throwable.toAccountMessage(R.string.account_email_resend))
                    }
                }
        }
    }

    fun requestUnblock(user: BlockedUser) = _uiState.update { it.copy(unblocking = user) }

    fun dismissUnblock() = _uiState.update { it.copy(unblocking = null) }

    fun confirmUnblock() {
        val target = _uiState.value.unblocking ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(unblocking = null) }
            runCatchingExceptCancellation { account.unblock(target.uid) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(blocked = state.blocked.filterNot { it.uid == target.uid })
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(message = throwable.toAccountMessage(R.string.account_block_unblock))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ContactBlockViewModel(container.accountSettingsRepository) }
            }
    }
}

data class ContactBlockUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val endpointPending: Boolean = false,
    val email: String = "",
    val backupEmail: String = "",
    val saved: AccountContact? = null,
    val blocked: List<BlockedUser> = emptyList(),
    val unblocking: BlockedUser? = null,
    val message: AccountMessage? = null,
) {
    val emailVerified: Boolean
        get() = saved?.emailVerified == true && email == saved.email

    val backupEmailVerified: Boolean
        get() = saved?.backupEmailVerified == true && backupEmail == saved.backupEmail

    val isEmailMalformed: Boolean get() = email.isNotEmpty() && !isEmailAddress(email)

    val isBackupMalformed: Boolean
        get() = backupEmail.isNotEmpty() && !isEmailAddress(backupEmail)

    val canSave: Boolean
        get() {
            if (isSaving || isEmailMalformed || isBackupMalformed || email.isEmpty()) return false
            val baseline = saved ?: AccountContact()
            return email != baseline.email || backupEmail != baseline.backupEmail
        }
}

/**
 * Enough of a check to catch a typo, and deliberately no more.
 *
 * A regex that tries to implement RFC 5322 rejects addresses that work; the server is the only thing
 * that knows whether an address is deliverable, and this only exists so an obvious slip is caught
 * before a verification mail goes to nobody.
 */
internal fun isEmailAddress(value: String): Boolean {
    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@')) return false
    val domain = value.substring(at + 1)
    return domain.length >= 3 && domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}
