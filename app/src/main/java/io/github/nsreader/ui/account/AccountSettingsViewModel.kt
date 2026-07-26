package io.github.nsreader.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.Board
import io.github.nsreader.data.CategoryRepository
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.data.settings.SettingsRepository
import io.github.nsreader.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 账号设置 (8g).
 *
 * The screen is a hub, and the only thing that makes it more than a list of links is the current-value
 * subtitle on each row. Those come from three different places — the forum profile, the site's setting
 * page, and the app's own preferences — so the merge happens here rather than in the composable.
 *
 * A failure to read the site half is not an error state. The rows still navigate, and 8g showing an
 * error screen because the blocked-user count could not be fetched would block access to 首页版块,
 * which is entirely local and always works.
 */
class AccountSettingsViewModel(
    private val account: AccountSettingsRepository,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    categories: CategoryRepository,
    private val posts: PostRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val remote = MutableStateFlow(RemoteAccountState())
    private var loadJob: Job? = null
    private var signOutJob: Job? = null

    val uiState: StateFlow<AccountSettingsUiState> =
        combine(remote, settings.settings, categories.boards) { site, preferences, boards ->
            val selectable = boards.filter { it.slug != null }
            AccountSettingsUiState(
                avatarUrl = site.avatarUrl,
                displayName = site.displayName,
                fields = site.fields,
                twoFactorEnabled = site.twoFactorEnabled,
                email = site.email,
                blockedCount = site.blockedCount,
                homeBoardCount = visibleHomeBoards(selectable, preferences.homeBoards).size,
                totalBoardCount = selectable.size,
                homeBoardsRestricted = preferences.homeBoards.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AccountSettingsUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                // The forum profile is the half that works today, so it is read on its own rather than
                // inside the same runCatching as the settings-page calls that are still stubbed out.
                runCatchingExceptCancellation { profiles.profile() }
                    .onSuccess { profile ->
                        remote.update { it.copy(avatarUrl = profile.avatarUrl, displayName = profile.name) }
                    }
                runCatchingExceptCancellation { account.profileFields() }
                    .onSuccess { fields -> remote.update { it.copy(fields = fields) } }
                runCatchingExceptCancellation { account.twoFactor() }
                    .onSuccess { state -> remote.update { it.copy(twoFactorEnabled = state.enabled) } }
                runCatchingExceptCancellation { account.contact() }
                    .onSuccess { contact -> remote.update { it.copy(email = contact.email) } }
                runCatchingExceptCancellation { account.blockedUsers() }
                    .onSuccess { blocked -> remote.update { it.copy(blockedCount = blocked.size) } }
            }
    }

    fun signOut(onSignedOut: () -> Unit) {
        if (signOutJob?.isActive == true) return
        signOutJob =
            viewModelScope.launch {
                // Same order as 我的: drop authenticated rows before the signed-out state is published,
                // or the tabs still holding a Navigation 3 entry keep showing what they already drew.
                posts.clearSessionData()
                session.signOut()
                onSignedOut()
            }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AccountSettingsViewModel(
                        account = container.accountSettingsRepository,
                        profiles = container.profileRepository,
                        settings = container.settingsRepository,
                        categories = container.categoryRepository,
                        posts = container.postRepository,
                        session = container.sessionRepository,
                    )
                }
            }
    }
}

/** Everything 8g reads off the network, kept apart from the preferences it merges with. */
private data class RemoteAccountState(
    val avatarUrl: String? = null,
    val displayName: String = "",
    val fields: AccountProfileFields? = null,
    val twoFactorEnabled: Boolean? = null,
    val email: String? = null,
    val blockedCount: Int? = null,
)

data class AccountSettingsUiState(
    val avatarUrl: String? = null,
    val displayName: String = "",
    val fields: AccountProfileFields? = null,
    val twoFactorEnabled: Boolean? = null,
    val email: String? = null,
    val blockedCount: Int? = null,
    val homeBoardCount: Int = 0,
    val totalBoardCount: Int = 0,
    val homeBoardsRestricted: Boolean = false,
)

/**
 * Narrows a board list to the user's home-strip preference.
 *
 * Callers pass only the real boards — 综合 is not one, has no slug, and is prepended by whoever draws
 * the strip. An empty preference means unrestricted. A preference that no longer matches anything, as
 * happens when every chosen board is renamed server-side, also falls back to unrestricted rather than
 * to nothing: an empty strip is indistinguishable from a broken one.
 */
internal fun visibleHomeBoards(boards: List<Board>, preference: Set<String>): List<Board> {
    if (preference.isEmpty()) return boards
    return boards.filter { it.slug in preference }.ifEmpty { boards }
}
