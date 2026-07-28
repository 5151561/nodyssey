package io.github.nodyssey.ui.account

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

    private var submitJob: Job? = null

    init {
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

    fun updateCurrentPassword(value: String) = _uiState.update { it.copy(currentPassword = value) }

    fun updateNewPassword(value: String) = _uiState.update { it.copy(newPassword = value) }

    fun updateConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value) }

    fun toggleCurrentVisible() = _uiState.update { it.copy(currentVisible = !it.currentVisible) }

    fun toggleNewVisible() = _uiState.update { it.copy(newVisible = !it.newVisible) }

    fun toggleConfirmVisible() = _uiState.update { it.copy(confirmVisible = !it.confirmVisible) }

    fun requestPasswordChange() = _uiState.update { it.copy(confirming = SecurityConfirmation.Password) }

    fun requestTwoFactorEnrolment() = _uiState.update { it.copy(confirming = SecurityConfirmation.TwoFactor) }

    fun updateTwoFactorPassword(value: String) = _uiState.update { it.copy(twoFactorPassword = value) }

    /** Leaving the dialog abandons the password it collected rather than parking it in state. */
    fun dismissConfirmation() = _uiState.update { it.copy(confirming = null, twoFactorPassword = "") }

    fun confirmPasswordChange() {
        if (submitJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canSubmitPassword) return
        submitJob =
            viewModelScope.launch {
                _uiState.update { it.copy(confirming = null, isSubmitting = true, message = null) }
                runCatchingExceptCancellation {
                    account.changePassword(state.currentPassword, state.newPassword)
                }.onSuccess {
                    // The fields are cleared on success and only on success: a failed attempt that
                    // wipes what the user typed makes them re-enter three passwords to retry.
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
        val password = _uiState.value.twoFactorPassword
        if (password.isEmpty()) return
        submitJob =
            viewModelScope.launch {
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
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val confirmVisible: Boolean = false,
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
