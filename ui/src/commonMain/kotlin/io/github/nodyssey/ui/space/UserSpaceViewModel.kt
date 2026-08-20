package io.github.nodyssey.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
    val error: SiteError? = null,
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val loaded: Boolean = false,
)

data class UserSpaceUiState(
    val uid: Long,
    val isSelf: Boolean,
    val isLoadingProfile: Boolean = true,
    val error: SiteError? = null,
    val name: String = "",
    val avatarUrl: String? = null,
    val level: Int? = null,
    val bio: String? = null,
    val readme: String? = null,
    val joinedDays: Int? = null,
    val chickenCount: Int? = null,
    val topicCount: Int? = null,
    val commentCount: Int? = null,
    /** Null until the profile answers; rendered as "not following", which is what the button assumes. */
    val followed: Boolean? = null,
    /** A follow write is in flight; the button shows the target state and refuses a second tap. */
    val followPending: Boolean = false,
    /** A refused follow, held until the screen has shown it once. */
    val followFailure: FollowFailure? = null,
    val selectedTab: SpaceTab = SpaceTab.GENERAL,
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

    /** You cannot follow yourself, and the site's own card hides the button on your own page too. */
    val canFollow: Boolean get() = !isSelf
}

/**
 * Why a follow did not land.
 *
 * [detail] is the site's own sentence and wins when there is one; [error] is the fallback for the
 * failures that never reached the site at all — including the signed-out case, which is the only one
 * the screen turns into an action rather than a plain message.
 */
data class FollowFailure(
    val error: SiteError,
    val detail: String?,
)

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
    private val followRepository: FollowRepository,
    private val clock: AppClock,
    /**
     * Land on 收藏 rather than the usual first tab — 我的收藏 in the profile menu comes in this way.
     *
     * Only meaningful together with [isSelf]: the site publishes nobody else's collections, so the
     * tab does not exist on anyone else's space.
     */
    openCollections: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserSpaceUiState(uid = uid, isSelf = isSelf))
    val uiState: StateFlow<UserSpaceUiState> = _uiState.asStateFlow()

    val topics: Flow<PagingData<SpacePost>> =
        spacePager { page -> spaceRepository.topics(uid, page) }.cachedIn(viewModelScope)
    val comments: Flow<PagingData<SpaceComment>> =
        spacePager { page -> spaceRepository.comments(uid, page) }.cachedIn(viewModelScope)
    val collections: Flow<PagingData<SpacePost>> =
        spacePager { page -> spaceRepository.collections(page) }.cachedIn(viewModelScope)

    private var profileJob: Job? = null
    private var followJob: Job? = null

    init {
        refreshProfile()
        selectTab(
            when {
                isSelf && openCollections -> SpaceTab.COLLECTIONS
                isSelf -> SpaceTab.TOPICS
                else -> SpaceTab.GENERAL
            },
        )
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
                            it.copy(isLoadingProfile = false, error = throwable.toSiteError())
                        }
                    }
            }
    }

    fun selectTab(tab: SpaceTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Follows or unfollows, whichever the current state is not.
     *
     * Optimistic, and reverted on refusal. The alternative — spin the button until the site answers —
     * makes the one control on this screen feel broken on a slow connection, and the write is cheap to
     * undo: there is no counter to correct and no list to reorder, only this one flag.
     */
    fun toggleFollow() {
        val current = _uiState.value
        if (!current.canFollow || current.followPending) return
        val target = !(current.followed ?: false)
        _uiState.update { it.copy(followed = target, followPending = true, followFailure = null) }
        followJob?.cancel()
        followJob =
            viewModelScope.launch {
                runCatchingExceptCancellation {
                    if (target) followRepository.follow(uid) else followRepository.unfollow(uid)
                }.onSuccess {
                    _uiState.update { it.copy(followPending = false) }
                }.onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            followed = !target,
                            followPending = false,
                            followFailure =
                            FollowFailure(
                                error = throwable.toSiteError(),
                                detail = (throwable as? SiteException)?.detail,
                            ),
                        )
                    }
                }
            }
    }

    /** The failure has been shown; stop holding it. */
    fun onFollowFailureShown() {
        _uiState.update { it.copy(followFailure = null) }
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
            // A refresh that lands while a follow is in flight must not undo the optimistic flip: the
            // payload it carries was fetched before the write and is already stale.
            followed = if (followPending) followed else profile.followed,
        )

    companion object {
        fun factory(
            container: AppContainer,
            uid: Long,
            isSelf: Boolean,
            openCollections: Boolean = false,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UserSpaceViewModel(
                        uid = uid,
                        isSelf = isSelf,
                        profileRepository = container.profileRepository,
                        spaceRepository = container.userSpaceRepository,
                        followRepository = container.followRepository,
                        clock = container.clock,
                        openCollections = openCollections,
                    )
                }
            }
    }
}

private fun <T : Any> spacePager(fetch: suspend (Int) -> io.github.nodyssey.data.SpacePage<T>): Flow<PagingData<T>> =
    Pager(PagingConfig(pageSize = SPACE_PAGE_SIZE, initialLoadSize = SPACE_PAGE_SIZE)) {
        UserSpacePagingSource(fetch)
    }.flow

internal class UserSpacePagingSource<T : Any>(
    private val fetch: suspend (Int) -> io.github.nodyssey.data.SpacePage<T>,
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> =
        try {
            val page = params.key ?: 1
            val result = fetch(page)
            LoadResult.Page(
                data = result.items,
                prevKey = if (result.page > 1) result.page - 1 else null,
                nextKey = if (result.hasNextPage) result.page + 1 else null,
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LoadResult.Error(throwable)
        }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }
}

private const val SPACE_PAGE_SIZE = 20

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
