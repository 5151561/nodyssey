package io.github.nsreader.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.SpaceComment
import io.github.nsreader.data.SpacePage
import io.github.nsreader.data.SpacePost
import io.github.nsreader.data.UserProfile
import io.github.nsreader.data.UserSpaceRepository
import io.github.nsreader.di.AppContainer
import io.github.nsreader.ui.postlist.toNodeSeekError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

enum class SpaceTab {
    GENERAL,
    TOPICS,
    COMMENTS,
    COLLECTIONS,
}

/**
 * One tab's list.
 *
 * [loaded] is not `items.isNotEmpty()`: an account with no topics has loaded successfully and must not
 * be re-fetched every time the tab is selected, which is exactly the loop that flag prevents.
 */
data class SpaceListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: NodeSeekError? = null,
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val loaded: Boolean = false,
)

data class UserSpaceUiState(
    val uid: Long,
    val isSelf: Boolean,
    val isLoadingProfile: Boolean = true,
    val error: NodeSeekError? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val level: Int? = null,
    val bio: String? = null,
    val readme: String? = null,
    val joinedDays: Int? = null,
    val chickenCount: Int? = null,
    val topicCount: Int? = null,
    val commentCount: Int? = null,
    val selectedTab: SpaceTab = SpaceTab.GENERAL,
    val topics: SpaceListState<SpacePost> = SpaceListState(),
    val comments: SpaceListState<SpaceComment> = SpaceListState(),
    val collections: SpaceListState<SpacePost> = SpaceListState(),
) {
    /** Others have no 收藏 tab: the site publishes nobody else's collections. */
    val tabs: List<SpaceTab>
        get() =
            if (isSelf) {
                listOf(SpaceTab.GENERAL, SpaceTab.TOPICS, SpaceTab.COMMENTS, SpaceTab.COLLECTIONS)
            } else {
                listOf(SpaceTab.GENERAL, SpaceTab.TOPICS, SpaceTab.COMMENTS)
            }

    val hasProfile: Boolean get() = name.isNotBlank()

    /** `null` for 概况, which has no list of its own. */
    fun listFor(tab: SpaceTab): SpaceListState<*>? =
        when (tab) {
            SpaceTab.GENERAL -> null
            SpaceTab.TOPICS -> topics
            SpaceTab.COMMENTS -> comments
            SpaceTab.COLLECTIONS -> collections
        }
}

/**
 * State holder for a user's space, ours or anyone's.
 *
 * The header and each tab load independently. That is deliberate: the profile call decides whether the
 * screen can be drawn at all, while a tab failing — or being a page the site only renders client-side —
 * has to stay contained inside that tab instead of blanking the header the user came to read.
 */
class UserSpaceViewModel(
    private val uid: Long,
    isSelf: Boolean,
    private val profileRepository: ProfileRepository,
    private val spaceRepository: UserSpaceRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserSpaceUiState(uid = uid, isSelf = isSelf))
    val uiState: StateFlow<UserSpaceUiState> = _uiState.asStateFlow()

    private var profileJob: Job? = null
    private val tabJobs = mutableMapOf<SpaceTab, Job>()

    init {
        refreshProfile()
        // Your own space opens on 主题帖 — you know who you are; a stranger's opens on 概况, which is
        // the reason to be there. Either way the first tab starts loading immediately.
        selectTab(if (isSelf) SpaceTab.TOPICS else SpaceTab.GENERAL)
    }

    fun refreshProfile() {
        profileJob?.cancel()
        profileJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingProfile = true, error = null) }
                runCatchingExceptCancellation { profileRepository.profile(uid) }
                    .onSuccess { profile -> _uiState.update { it.withProfile(profile) } }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoadingProfile = false, error = throwable.toNodeSeekError())
                        }
                    }
            }
    }

    fun selectTab(tab: SpaceTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val list = _uiState.value.listFor(tab) ?: return
        if (!list.loaded && !list.isLoading) load(tab, page = 1)
    }

    fun loadMore(tab: SpaceTab) {
        val list = _uiState.value.listFor(tab) ?: return
        if (list.isLoading || !list.hasNextPage) return
        load(tab, page = list.page + 1)
    }

    fun retryTab(tab: SpaceTab) = load(tab, page = 1)

    private fun load(tab: SpaceTab, page: Int) {
        tabJobs[tab]?.cancel()
        tabJobs[tab] =
            viewModelScope.launch {
                when (tab) {
                    SpaceTab.GENERAL -> Unit

                    SpaceTab.TOPICS ->
                        loadInto(
                            page = page,
                            read = { topics },
                            write = { copy(topics = it) },
                            fetch = { spaceRepository.topics(uid, page) },
                        )

                    SpaceTab.COMMENTS ->
                        loadInto(
                            page = page,
                            read = { comments },
                            write = { copy(comments = it) },
                            fetch = { spaceRepository.comments(uid, page) },
                        )

                    SpaceTab.COLLECTIONS ->
                        loadInto(
                            page = page,
                            read = { collections },
                            write = { copy(collections = it) },
                            fetch = { spaceRepository.collections(page) },
                        )
                }
            }
    }

    /**
     * The one load routine, given a lens onto whichever list is being filled.
     *
     * Passing the accessors rather than switching on the tab inside is what keeps the item type real:
     * casting a shared list field to make one function fit three types is how a comment ends up in the
     * topics tab after a refactor.
     */
    private suspend fun <T> loadInto(
        page: Int,
        read: UserSpaceUiState.() -> SpaceListState<T>,
        write: UserSpaceUiState.(SpaceListState<T>) -> UserSpaceUiState,
        fetch: suspend () -> SpacePage<T>,
    ) {
        _uiState.update { it.write(it.read().copy(isLoading = true, error = null)) }
        runCatchingExceptCancellation { fetch() }
            .onSuccess { result ->
                _uiState.update { state ->
                    val current = state.read()
                    state.write(
                        current.copy(
                            // Page 1 replaces rather than appends, so a retry cannot double every row.
                            items = if (page == 1) result.items else current.items + result.items,
                            isLoading = false,
                            error = null,
                            page = page,
                            hasNextPage = result.hasNextPage,
                            loaded = true,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.write(
                        state.read().copy(isLoading = false, error = throwable.toNodeSeekError()),
                    )
                }
            }
    }

    private fun UserSpaceUiState.withProfile(profile: UserProfile): UserSpaceUiState =
        copy(
            isLoadingProfile = false,
            error = null,
            name = profile.name,
            avatarUrl = profile.avatarUrl,
            level = profile.rank,
            bio = profile.bio,
            readme = profile.readme,
            joinedDays = joinedDays(profile.createdAt, clock.nowMillis()),
            chickenCount = profile.chickenCount,
            topicCount = profile.topicCount,
            commentCount = profile.commentCount,
        )

    companion object {
        fun factory(
            container: AppContainer,
            uid: Long,
            isSelf: Boolean,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UserSpaceViewModel(
                        uid = uid,
                        isSelf = isSelf,
                        profileRepository = container.profileRepository,
                        spaceRepository = container.userSpaceRepository,
                        clock = container.clock,
                    )
                }
            }
    }
}

/**
 * Days since registration, which is the site's own headline statistic for an account.
 *
 * `created_at` is an ISO instant on some accounts and a plain date on others, so only the date part is
 * read. An unparseable value yields null and the stat renders as "—" rather than as a wrong number.
 */
internal fun joinedDays(createdAt: String?, nowMillis: Long): Int? {
    val datePart = createdAt?.take(10)?.takeIf { it.length == 10 } ?: return null
    val joined =
        try {
            LocalDate.parse(datePart)
        } catch (_: DateTimeParseException) {
            return null
        }
    val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(joined, today).toInt().coerceAtLeast(0)
}
