package io.github.nodyssey.ui.space

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.FollowUser
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePage
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.UserSpaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The follow button on a public space page.
 *
 * The behaviour worth pinning is that the flip is *optimistic and reversible*: the button must move on
 * the tap and move back on a refusal, because the site refuses a follow for reasons that are ordinary
 * ("对方已屏蔽你", a session that expired) rather than exceptional, and a button left showing 已关注
 * after one of those is a lie the user has no way to notice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserSpaceFollowTest {
    private val dispatcher = StandardTestDispatcher()
    private val follow = FakeFollowRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        followed: Boolean? = false,
        isSelf: Boolean = false,
    ) = UserSpaceViewModel(
        uid = UID,
        isSelf = isSelf,
        profileRepository = FakeProfileRepository(followed),
        spaceRepository = EmptySpaceRepository,
        followRepository = follow,
        clock = AppClock.System,
    )

    @Test
    fun `takes the followed flag from the profile`() =
        runTest(dispatcher) {
            val vm = viewModel(followed = true)
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.followed)
            assertTrue(vm.uiState.value.canFollow)
        }

    @Test
    fun `flips to followed before the site answers`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            follow.gate = CompletableDeferred()

            vm.toggleFollow()
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.followed)
            assertTrue(vm.uiState.value.followPending)

            follow.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(UID to true), follow.calls)
            assertFalse(vm.uiState.value.followPending)
            assertNull(vm.uiState.value.followFailure)
        }

    @Test
    fun `unfollows when already following`() =
        runTest(dispatcher) {
            val vm = viewModel(followed = true)
            advanceUntilIdle()

            vm.toggleFollow()
            advanceUntilIdle()

            assertEquals(listOf(UID to false), follow.calls)
            assertEquals(false, vm.uiState.value.followed)
        }

    @Test
    fun `reverts the button and keeps the site's sentence when the follow is refused`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            follow.error = NodeSeekException(NodeSeekError.Unknown, detail = "对方已屏蔽你")

            vm.toggleFollow()
            advanceUntilIdle()

            assertEquals(false, vm.uiState.value.followed)
            assertFalse(vm.uiState.value.followPending)
            assertEquals("对方已屏蔽你", vm.uiState.value.followFailure?.detail)

            vm.onFollowFailureShown()
            assertNull(vm.uiState.value.followFailure)
        }

    /** A second tap while the first is in flight would send the opposite write and race it. */
    @Test
    fun `ignores a second tap while a write is in flight`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            follow.gate = CompletableDeferred()

            vm.toggleFollow()
            advanceUntilIdle()
            vm.toggleFollow()
            advanceUntilIdle()
            follow.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(UID to true), follow.calls)
        }

    /** You cannot follow yourself, and the site's card hides the button on your own page too. */
    @Test
    fun `offers nothing on your own page`() =
        runTest(dispatcher) {
            val vm = viewModel(isSelf = true)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.canFollow)
            vm.toggleFollow()
            advanceUntilIdle()
            assertTrue(follow.calls.isEmpty())
        }

    /**
     * A profile refresh that lands mid-write carries a flag fetched before the write, so applying it
     * would silently undo the flip the user just made.
     */
    @Test
    fun `a refresh mid-write does not undo the optimistic flip`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            follow.gate = CompletableDeferred()

            vm.toggleFollow()
            advanceUntilIdle()
            vm.refreshProfile()
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.followed)

            follow.gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.followed)
        }

    private companion object {
        const val UID = 52_425L
    }
}

private class FakeFollowRepository : FollowRepository {
    /** Each write as `uid to isFollow`, so ordering and the add/del choice are both assertable. */
    val calls = mutableListOf<Pair<Long, Boolean>>()
    var error: Throwable? = null

    /** Holds a write open, which is the only way to observe the pending state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun following(): List<FollowUser> = emptyList()

    override suspend fun followers(): List<FollowUser> = emptyList()

    override suspend fun follow(uid: Long) = write(uid, isFollow = true)

    override suspend fun unfollow(uid: Long) = write(uid, isFollow = false)

    private suspend fun write(uid: Long, isFollow: Boolean) {
        calls += uid to isFollow
        gate?.await()
        error?.let { throw it }
    }
}

private class FakeProfileRepository(
    private val followed: Boolean?,
) : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile = profile(0)

    override suspend fun profile(uid: Long): UserProfile =
        UserProfile(
            uid = uid,
            name = "nssk",
            avatarUrl = "https://www.nodeseek.com/avatar/$uid.png",
            followed = followed,
        )
}

private object EmptySpaceRepository : UserSpaceRepository {
    override suspend fun topics(uid: Long, page: Int): SpacePage<SpacePost> = empty()

    override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> = empty()

    override suspend fun collections(page: Int): SpacePage<SpacePost> = empty()

    private fun <T> empty() = SpacePage<T>(items = emptyList(), page = 1, hasNextPage = false)
}
