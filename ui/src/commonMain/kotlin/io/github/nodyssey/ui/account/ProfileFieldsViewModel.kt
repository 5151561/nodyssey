package io.github.nodyssey.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.account.AccountProfileFields
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_action_saved
import io.github.nodyssey.ui.resources.account_avatar_failed
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 个人信息 (d6 1/4) — avatar, Bio, 签名 and Readme.
 *
 * The three text fields are one form with one save button because the site treats them as one group,
 * and splitting them would mean three chances to half-save. The avatar is separate: it goes out as its
 * own request, so it gets its own result. There is no removing it — the site offers upload only.
 */
class ProfileFieldsViewModel(
    private val account: AccountSettingsRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileFieldsUiState())
    val uiState: StateFlow<ProfileFieldsUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            runCatchingExceptCancellation { profiles.profile() }
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(avatarUrl = profile.avatarUrl, displayName = profile.name)
                    }
                }

            runCatchingExceptCancellation { account.profileFields() }
                .onSuccess { fields ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saved = fields,
                            bio = fields.bio,
                            signature = fields.signature,
                            readme = fields.readme,
                        )
                    }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, message = throwable.toAccountMessage())
                    }
                }
        }
    }

    fun updateBio(value: String) {
        // Bio is one line on the site; a pasted paragraph would be silently truncated server-side.
        _uiState.update { it.copy(bio = value.replace('\n', ' ')) }
    }

    fun updateSignature(value: String) = _uiState.update { it.copy(signature = value) }

    fun updateReadme(value: String) = _uiState.update { it.copy(readme = value) }

    fun setPendingAvatar(avatar: PendingAvatar) =
        _uiState.update { it.copy(pendingAvatar = avatar, message = null) }

    fun reportAvatarFailure() =
        _uiState.update { it.copy(message = AccountMessage.Info(Res.string.account_avatar_failed)) }

    fun save() {
        if (saveJob?.isActive == true) return
        saveJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, message = null) }
                val current = _uiState.value
                val fields =
                    AccountProfileFields(
                        bio = current.bio.trim(),
                        signature = current.signature.trim(),
                        readme = current.readme.trim(),
                    )

                // The avatar goes first: if it fails there is no point writing the text fields and
                // reporting success, and if the text fields fail the avatar is still worth keeping.
                val avatarResult =
                    current.pendingAvatar?.let { pending ->
                        runCatchingExceptCancellation { account.uploadAvatar(pending.upload) }
                    }
                if (avatarResult?.isFailure == true) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message =
                            avatarResult.exceptionOrNull()!!
                                .toAccountMessage(),
                        )
                    }
                    return@launch
                }

                runCatchingExceptCancellation { account.saveProfileFields(fields) }
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saved = fields,
                                pendingAvatar = null,
                                message = AccountMessage.Info(Res.string.account_action_saved),
                            )
                        }
                    }.onFailure { throwable ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                message = throwable.toAccountMessage(),
                            )
                        }
                    }
            }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProfileFieldsViewModel(
                        account = container.accountSettingsRepository,
                        profiles = container.profileRepository,
                    )
                }
            }
    }
}

data class ProfileFieldsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val avatarUrl: String? = null,
    val displayName: String = "",
    val pendingAvatar: PendingAvatar? = null,
    val bio: String = "",
    val signature: String = "",
    val readme: String = "",
    /** What the server last confirmed, so the save button knows whether anything actually changed. */
    val saved: AccountProfileFields? = null,
    val message: AccountMessage? = null,
) {
    val isDirty: Boolean
        get() {
            if (pendingAvatar != null) return true
            val baseline = saved ?: AccountProfileFields()
            return bio.trim() != baseline.bio ||
                signature.trim() != baseline.signature ||
                readme.trim() != baseline.readme
        }

    val canSave: Boolean get() = isDirty && !isSaving
}
