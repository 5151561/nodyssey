package io.github.nsreader.ui.profile

import android.webkit.CookieManager
import androidx.paging.PagingData
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.WebViewCookieJar
import io.github.nsreader.data.FeedPost
import io.github.nsreader.data.PostRepository
import io.github.nsreader.data.ProfileRepository
import io.github.nsreader.data.UserProfile
import io.github.nsreader.data.session.SessionRepository
import io.github.nsreader.model.FeedSort
import io.github.nsreader.model.ThreadSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
                    session = SessionRepository(WebViewCookieJar(cookieManager)),
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
                    session = SessionRepository(WebViewCookieJar(cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        error = NodeSeekException(NodeSeekError.Network),
                    ),
                )

            advanceUntilIdle()

            assertEquals(NodeSeekError.Network, viewModel.uiState.value.error)
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
                    session = SessionRepository(WebViewCookieJar(cookieManager)),
                    postRepository = NoOpPostRepository,
                    profileRepository =
                    FakeProfileRepository(
                        profile = fresh,
                        cachedProfile = cached,
                        refreshGate = gate,
                    ),
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
    override fun feed(categorySlug: String?, sort: FeedSort): Flow<PagingData<FeedPost>> = emptyFlow()

    override fun search(query: String): Flow<List<FeedPost>> = emptyFlow()

    override suspend fun invalidateCaches() = Unit

    override suspend fun clearSessionData() = Unit

    override suspend fun clearCache(isSignedIn: Boolean, fingerprint: Int) = Unit

    override suspend fun reconcileSession(isSignedIn: Boolean, fingerprint: Int): Boolean = false

    override fun thread(postId: Long): Flow<ThreadSnapshot?> = emptyFlow()

    override suspend fun refreshThread(postId: Long, page: Int) = Unit

    override suspend fun isThreadFresh(postId: Long): Boolean = false

    override suspend fun markThreadRead(postId: Long) = Unit
}
