package io.github.bbs1.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.bbs1.data.InstanceRepository
import io.github.bbs1.model.InstanceSession
import io.github.bbs1.net.Bbs1Api
import io.github.bbs1.net.Bbs1ApiException
import io.github.bbs1.ui.common.ApiErrorUi
import io.github.bbs1.ui.common.toUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @property succeeded One-shot: the screen leaves on seeing it. Not derived from the stored session,
 *   because a site the user was already signed in to would then look like a login that just landed.
 */
data class LoginUiState(
    val submitting: Boolean = false,
    val error: ApiErrorUi? = null,
    val succeeded: Boolean = false,
)

/**
 * Signing in to one site.
 *
 * The password is a call argument and nothing else: it goes to [Bbs1Api.login] and what comes back —
 * a token — is the only part that is stored. Nothing here keeps it in state, where it would ride
 * along in every recomposition and in whatever a crash reporter serializes.
 */
class LoginViewModel(
    private val api: Bbs1Api,
    private val repository: InstanceRepository,
    private val instanceId: String,
    private val baseUrl: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun submit(username: String, password: String) {
        if (_uiState.value.submitting) return
        if (username.isBlank() || password.isEmpty()) return
        _uiState.value = LoginUiState(submitting = true)
        viewModelScope.launch {
            try {
                val auth = api.login(baseUrl, username.trim(), password)
                repository.saveSession(
                    instanceId,
                    InstanceSession(
                        token = auth.token,
                        expiresAt = auth.tokenExpiresAt,
                        userId = auth.user.id,
                        username = auth.user.username,
                        avatarUrl = auth.user.avatar.url,
                    ),
                )
                _uiState.value = LoginUiState(succeeded = true)
            } catch (e: Bbs1ApiException) {
                // A wrong password comes back as a Server refusal with the site's own wording
                // ("用户名或密码错误"), which is already the message to show.
                _uiState.value = LoginUiState(error = e.toUi())
            }
        }
    }

    /** The screen acknowledges a shown failure so the next attempt starts clean. */
    fun consumeError() {
        if (_uiState.value.error != null) _uiState.value = LoginUiState()
    }
}
