package io.github.nodyssey.ui.profile

import android.webkit.CookieManager
import androidx.paging.PagingData
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.data.AttendanceMode
import io.github.nodyssey.data.AttendanceResult
import io.github.nodyssey.data.AttendanceStatus
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.data.FreeChickenLegs
import io.github.nodyssey.data.GrowthSnapshot
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.ThreadSnapshot
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.WebViewCookieJar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val cookieManager = CookieManager.getInstance()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cookieManager.removeAllCookies(null)
        cookieManager.setCookie(NodeSeekSite.BASE_URL, "session=test")
    }

    @After
    fun tearDown() {
        cookieManager.removeAllCookies(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `publishes real profile values for the signed in account`() =
        runTest(dispatcher) {
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        UserProfile(
                            uid = 31037,
                            name = "缭雾",
                            avatarUrl = "https://www.nodeseek.com/avatar/31037.png",
                            rank = 2,
                            createdAt = "2025-04-27T14:29:22.000Z",
                            chickenCount = 305,
                            starCount = 7,
                        ),
                    ),
                    assetsRepository = FakeAssetsRepository(),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("缭雾", state.displayName)
            assertEquals("Lv 2", state.level)
            assertEquals("2025年4月 注册 · UID 31037", state.memberSince)
            assertEquals(305, state.chickenCount)
            assertEquals(7, state.starCount)
            assertFalse(state.isLoading)
            assertEquals(null, state.error)
        }

    @Test
    fun `keeps a typed error when profile loading fails`() =
        runTest(dispatcher) {
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        error = SiteException(SiteError.Network),
                    ),
                    assetsRepository = FakeAssetsRepository(),
                )

            advanceUntilIdle()

            assertEquals(SiteError.Network, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `shows the persisted profile while its refresh is still running`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val cached =
                UserProfile(
                    uid = 31037,
                    name = "缓存名字",
                    avatarUrl = "https://www.nodeseek.com/avatar/31037.png",
                    chickenCount = 292,
                )
            val fresh = cached.copy(name = "网络新名字", chickenCount = 305)
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        profile = fresh,
                        cachedProfile = cached,
                        refreshGate = gate,
                    ),
                    assetsRepository = FakeAssetsRepository(),
                )

            runCurrent()

            assertEquals("缓存名字", viewModel.uiState.value.displayName)
            assertEquals(292, viewModel.uiState.value.chickenCount)
            assertEquals(true, viewModel.uiState.value.hasProfile)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals("网络新名字", viewModel.uiState.value.displayName)
            assertEquals(305, viewModel.uiState.value.chickenCount)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `publishes today's attendance gain from the board`() =
        runTest(dispatcher) {
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        UserProfile(
                            uid = 31037,
                            name = "缭雾",
                            avatarUrl = "https://www.nodeseek.com/avatar/31037.png",
                        ),
                    ),
                    assetsRepository = FakeAssetsRepository(gain = 7),
                )

            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.hasSignedInToday)
            assertEquals(7, viewModel.uiState.value.attendanceGain)
            assertFalse(viewModel.uiState.value.isCheckingAttendance)
        }

    @Test
    fun `keeps today's receipt on screen while a re-check runs`() =
        runTest(dispatcher) {
            val assets = FakeAssetsRepository(gain = 7)
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        UserProfile(
                            uid = 31037,
                            name = "缭雾",
                            avatarUrl = "https://www.nodeseek.com/avatar/31037.png",
                        ),
                    ),
                    assetsRepository = assets,
                )
            advanceUntilIdle()

            // What returning to the foreground does. The button must not fall back to 检查中… — the
            // answer it is already showing stays true until the ledger says otherwise.
            viewModel.refreshAttendance()
            runCurrent()

            assertFalse(viewModel.uiState.value.isAttendanceUnknown)
            assertEquals(true, viewModel.uiState.value.hasSignedInToday)
            assertEquals(7, viewModel.uiState.value.attendanceGain)

            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isAttendanceUnknown)
            assertEquals(7, viewModel.uiState.value.attendanceGain)
        }

    @Test
    fun `opens attendance board in the profile state without navigation`() =
        runTest(dispatcher) {
            val entries =
                listOf(
                    AttendanceBoardEntry(
                        uid = 31037,
                        name = "缭雾",
                        gain = 7,
                        timeText = "刚刚",
                    ),
                )
            val viewModel =
                ProfileViewModel(
                    session = SessionRepository(WebViewCookieJar(NodeSeekSite.CONFIG, cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        UserProfile(
                            uid = 31037,
                            name = "缭雾",
                            avatarUrl = "https://www.nodeseek.com/avatar/31037.png",
                        ),
                    ),
                    assetsRepository = FakeAssetsRepository(gain = 7, board = entries),
                )
            advanceUntilIdle()

            viewModel.openAttendanceBoard()
            advanceUntilIdle()

            assertEquals(true, viewModel.uiState.value.boardOpen)
            assertEquals(entries, viewModel.uiState.value.board)
            assertFalse(viewModel.uiState.value.isLoadingBoard)
        }
}

private class FakeAssetsRepository(
    private val gain: Int? = null,
    private val board: List<AttendanceBoardEntry> = emptyList(),
) : AssetsRepository {
    private val status = MutableStateFlow<AttendanceStatus?>(null)
    var refreshCount = 0
        private set

    override fun observeAttendanceStatus(): StateFlow<AttendanceStatus?> = status

    override suspend fun growth(): GrowthSnapshot = error("Not used")

    override suspend fun refreshAttendanceStatus(uid: Long): AttendanceStatus {
        refreshCount++
        return AttendanceStatus(
            uid = uid,
            date = TODAY,
            hasSignedIn = gain != null,
            gain = gain,
        ).also { status.value = it }
    }

    override suspend fun signInForToday(mode: AttendanceMode): AttendanceResult = error("Not used")

    override suspend fun attendanceBoard(page: Int): List<AttendanceBoardEntry> = board

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 8, 2)
    }
}

private class FakeProfileRepository(
    private val profile: UserProfile? = null,
    cachedProfile: UserProfile? = null,
    private val error: Throwable? = null,
    private val refreshGate: CompletableDeferred<Unit>? = null,
) : ProfileRepository {
    private val stored = MutableStateFlow(cachedProfile)

    override fun observeProfile(sessionFingerprint: Int): Flow<UserProfile?> = stored

    override suspend fun refreshProfile(sessionFingerprint: Int) {
        refreshGate?.await()
        error?.let { throw it }
        stored.value = requireNotNull(profile)
    }

    override suspend fun profile(refresh: Boolean): UserProfile {
        error?.let { throw it }
        return requireNotNull(profile)
    }

    override suspend fun profile(uid: Long): UserProfile = profile()
}

private object NoOpPostRepository : PostRepository {
    override fun feed(
        categorySlug: String?,
        sort: FeedSort,
        startPage: Int,
    ): Flow<PagingData<FeedPost>> = emptyFlow()

    override fun feedTotalPages(categorySlug: String?, sort: FeedSort): Flow<Int> = emptyFlow()

    override suspend fun feedRowIndexOfPage(
        categorySlug: String?,
        sort: FeedSort,
        page: Int,
    ): Int? = null

    override fun searchFeed(
        query: String,
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>> = emptyFlow()

    override fun search(query: String): Flow<List<FeedPost>> = emptyFlow()

    override suspend fun invalidateCaches() = Unit

    override suspend fun clearSessionData() = Unit

    override suspend fun clearCache(isSignedIn: Boolean, fingerprint: Int) = Unit

    override suspend fun reconcileSession(isSignedIn: Boolean, fingerprint: Int): Boolean = false

    override fun thread(postId: Long): Flow<ThreadSnapshot?> = emptyFlow()

    override suspend fun refreshThread(postId: Long, page: Int) = Unit

    override suspend fun extendThread(postId: Long, page: Int) = Unit

    override suspend fun isThreadFresh(postId: Long): Boolean = false

    override suspend fun hasUnreadReplies(postId: Long): Boolean = false

    override suspend fun cachedPages(postId: Long): IntRange? = null

    override suspend fun markThreadRead(postId: Long) = Unit

    override suspend fun react(postId: Long, commentId: Long, action: ReactionAction) = Unit

    override suspend fun freeChickenLegs(): FreeChickenLegs? = null

    override suspend fun setCollected(postId: Long, collected: Boolean) = Unit

    override fun readHistory(): Flow<List<ReadHistoryEntry>> = emptyFlow()

    override suspend fun removeFromHistory(postId: Long) = Unit

    override suspend fun restoreToHistory(entry: ReadHistoryEntry) = Unit

    override suspend fun trimReadHistory() = Unit

    override suspend fun clearReadHistory() = Unit
}
