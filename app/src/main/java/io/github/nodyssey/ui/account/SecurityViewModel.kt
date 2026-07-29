package io.github.nodyssey.ui.account

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.R
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 安全 (d6 2/4) — password changes and two-factor enrolment.
 *
 * The two riskiest writes in the app live here, so both are two-step: the button only opens a dialog,
 * and only the dialog's confirm actually calls the repository. Nothing on this screen is a switch.
 */
class SecurityViewModel(
    private val account: AccountSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    /**
     * The four password boxes' own state.
     *
     * `SecureTextField` needs a `TextFieldState`, and holding them here keeps clearing-on-success a
     * one-liner. [uiState] mirrors the text so the validation below — too short, mismatched, ready —
     * stays where the rest of the screen's rules live.
     */
    val currentPasswordState = TextFieldState()
    val newPasswordState = TextFieldState()
    val confirmPasswordState = TextFieldState()
    val twoFactorPasswordState = TextFieldState()

    private var submitJob: Job? = null

    init {
        mirror(currentPasswordState) { state, value -> state.copy(currentPassword = value) }
        mirror(newPasswordState) { state, value -> state.copy(newPassword = value) }
        mirror(confirmPasswordState) { state, value -> state.copy(confirmPassword = value) }
        mirror(twoFactorPasswordState) { state, value -> state.copy(twoFactorPassword = value) }

        viewModelScope.launch {
            runCatchingExceptCancellation { account.twoFactor() }
                .onSuccess { two ->
                    _uiState.update { it.copy(isLoading = false, twoFactorEnabled = two.enabled) }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = throwable.toAccountMessage(),
                        )
                    }
                }
        }
    }

    fun requestPasswordChange() = _uiState.update { it.copy(confirming = SecurityConfirmation.Password) }

    fun requestTwoFactorEnrolment() = _uiState.update { it.copy(confirming = SecurityConfirmation.TwoFactor) }

    /** Leaving the dialog abandons the password it collected rather than parking it in state. */
    fun dismissConfirmation() {
        twoFactorPasswordState.clearText()
        _uiState.update { it.copy(confirming = null, twoFactorPassword = "") }
    }

    fun confirmPasswordChange() {
        if (submitJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canSubmitPassword) return
        // Read straight from the fields rather than from the mirrored copy: the mirror is a frame
        // behind by construction, and what gets sent to the server must be exactly what is on screen.
        val currentPassword = currentPasswordState.text.toString()
        val newPassword = newPasswordState.text.toString()
        submitJob =
            viewModelScope.launch {
                _uiState.update { it.copy(confirming = null, isSubmitting = true, message = null) }
                runCatchingExceptCancellation {
                    account.changePassword(currentPassword, newPassword)
                }.onSuccess {
                    // The fields are cleared on success and only on success: a failed attempt that
                    // wipes what the user typed makes them re-enter three passwords to retry.
                    currentPasswordState.clearText()
                    newPasswordState.clearText()
                    confirmPasswordState.clearText()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            message = AccountMessage.Info(R.string.account_action_saved),
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            message = throwable.toAccountMessage(),
                        )
                    }
                }
            }
    }

    fun confirmTwoFactorEnrolment() {
        if (submitJob?.isActive == true) return
        val password = twoFactorPasswordState.text.toString()
        if (password.isEmpty()) return
        submitJob =
            viewModelScope.launch {
                twoFactorPasswordState.clearText()
                _uiState.update {
                    it.copy(confirming = null, twoFactorPassword = "", isSubmitting = true, message = null)
                }
                runCatchingExceptCancellation { account.beginTwoFactorEnrolment(password) }
                    .onSuccess { uri ->
                        _uiState.update { it.copy(isSubmitting = false, enrolmentUri = uri) }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                message = throwable.toAccountMessage(),
                            )
                        }
                    }
            }
    }

    fun consumeEnrolmentUri() = _uiState.update { it.copy(enrolmentUri = null) }

    /**
     * No installed app claims `otpauth://`.
     *
     * Worth saying out loud: the user pressed 开始绑定 and, without this, absolutely nothing happened —
     * which reads as the app being broken rather than as a missing authenticator.
     */
    fun reportMissingAuthenticatorApp() =
        _uiState.update {
            it.copy(message = AccountMessage.Info(R.string.account_two_factor_no_app))
        }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Keeps [uiState]'s copy of one field in step with the box the user is typing into. */
    private fun mirror(
        field: TextFieldState,
        write: (SecurityUiState, String) -> SecurityUiState,
    ) {
        snapshotFlow { field.text.toString() }
            .onEach { value -> _uiState.update { state -> write(state, value) } }
            .launchIn(viewModelScope)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SecurityViewModel(container.accountSettingsRepository) }
            }
    }
}

/** Which high-risk dialog is open, if any. */
enum class SecurityConfirmation { Password, TwoFactor }

data class SecurityUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val twoFactorEnabled: Boolean? = null,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val confirming: SecurityConfirmation? = null,
    /**
     * Collected inside the 2FA dialog, not on the form above it: enrolment is its own endpoint and
     * the site demands the account password for it, independently of any password change.
     */
    val twoFactorPassword: String = "",
    /** The `otpauth://` URI returned by enrolment, handed to whatever authenticator app claims it. */
    val enrolmentUri: String? = null,
    val message: AccountMessage? = null,
) {
    val strength: PasswordStrength? get() = passwordStrength(newPassword)

    val isTooShort: Boolean
        get() = newPassword.isNotEmpty() && newPassword.length < MIN_PASSWORD_LENGTH

    /** Only once the user has actually typed a confirmation — nagging from the first keystroke is noise. */
    val isMismatched: Boolean
        get() = confirmPassword.isNotEmpty() && confirmPassword != newPassword

    val canSubmitPassword: Boolean
        get() =
            !isSubmitting &&
                currentPassword.isNotEmpty() &&
                newPassword.length >= MIN_PASSWORD_LENGTH &&
                confirmPassword == newPassword
}
