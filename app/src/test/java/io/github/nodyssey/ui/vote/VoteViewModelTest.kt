package io.github.nodyssey.ui.vote

import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.session.SessionState
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = MutableStateFlow(SessionState(isSignedIn = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: VoteRepository,
        uid: Long? = 52425,
        admin: Boolean = false,
    ) = VoteViewModel(VOTE_ID, repository, session, FakeProfile(uid, admin))

    @Test
    fun `reads the vote on creation`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = unvoted())

            val vm = viewModel(repository)
            advanceUntilIdle()

            assertEquals("哪个运营商比较好", vm.uiState.value.vote?.title)
            assertFalse(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.error)
        }

    /** No results before this account votes, which is what the card keys every read-out off. */
    @Test
    fun `an unvoted vote shows no results`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted()))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.hasVoted)
            assertFalse(vm.uiState.value.showsResults)
        }

    /** Single choice replaces; two taps must not end up submitting both options. */
    @Test
    fun `a single-choice selection replaces rather than accumulates`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted()))
            advanceUntilIdle()

            vm.toggleSelection(13201)
            vm.toggleSelection(13203)

            assertEquals(setOf(13203L), vm.uiState.value.selectedIds)
        }

    @Test
    fun `a multiple-choice selection accumulates and toggles off`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted(multiple = true)))
            advanceUntilIdle()

            vm.toggleSelection(13201)
            vm.toggleSelection(13203)
            assertEquals(setOf(13201L, 13203L), vm.uiState.value.selectedIds)

            vm.toggleSelection(13201)
            assertEquals(setOf(13203L), vm.uiState.value.selectedIds)
        }

    @Test
    fun `a locked vote cannot be ticked`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted(locked = true)))
            advanceUntilIdle()

            vm.toggleSelection(13201)

            assertTrue(vm.uiState.value.selectedIds.isEmpty())
        }

    /**
     * The counts appear for the first time with this vote, so there is nothing local to increment —
     * the only way to show a result is to ask again.
     */
    @Test
    fun `submitting re-reads the vote instead of guessing the new tally`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = unvoted())
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.toggleSelection(13201)
            repository.vote = voted()

            vm.submit()
            advanceUntilIdle()

            assertEquals(listOf(listOf(13201L)), repository.submitted)
            assertEquals(2, repository.reads)
            assertTrue(vm.uiState.value.showsResults)
            assertEquals(12, vm.uiState.value.vote?.items?.first()?.count)
            // The pending tick is gone: it is a fact on the server now, not a local intention.
            assertTrue(vm.uiState.value.selectedIds.isEmpty())
        }

    @Test
    fun `submitting nothing sends nothing`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = unvoted())
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.submit()
            advanceUntilIdle()

            assertTrue(repository.submitted.isEmpty())
        }

    @Test
    fun `a refused submit keeps the site's sentence`() =
        runTest(dispatcher) {
            val repository =
                FakeVoteRepository(
                    vote = unvoted(),
                    failure = NodeSeekException(NodeSeekError.Unknown, detail = "投票已结束"),
                )
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.toggleSelection(13201)

            vm.submit()
            advanceUntilIdle()

            assertEquals("投票已结束", vm.uiState.value.failure?.detail)
            assertFalse(vm.uiState.value.isSubmitting)

            vm.onFailureShown()
            assertNull(vm.uiState.value.failure)
        }

    @Test
    fun `a read failure is held apart from a write failure`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = null, readFailure = NodeSeekException(NodeSeekError.Network))

            val vm = viewModel(repository)
            advanceUntilIdle()

            assertEquals(NodeSeekError.Network, vm.uiState.value.error)
            assertNull(vm.uiState.value.failure)
            assertNull(vm.uiState.value.vote)
        }

    // --- Permissions, which the site enforces asymmetrically ------------------

    @Test
    fun `the owner may lock but not unlock or delete`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted()), uid = OWNER_UID)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.canManage)
            assertTrue(vm.uiState.value.canLock)
            assertFalse(vm.uiState.value.canDelete)
        }

    /** A locked vote leaves its owner nothing to do — unlocking is a moderator's. */
    @Test
    fun `the owner of a locked vote has no operations left`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted(locked = true)), uid = OWNER_UID)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.canLock)
            assertFalse(vm.uiState.value.canUnlock)
            assertFalse(vm.uiState.value.canDelete)
        }

    @Test
    fun `a moderator may unlock and delete anyone's vote`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted(locked = true)), uid = 999, admin = true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.canManage)
            assertTrue(vm.uiState.value.canUnlock)
            assertTrue(vm.uiState.value.canDelete)
        }

    @Test
    fun `a bystander gets no manage menu at all`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeVoteRepository(vote = unvoted()), uid = 999)
            advanceUntilIdle()

            assertFalse(vm.uiState.value.canManage)
        }

    @Test
    fun `deleting leaves the card saying so rather than re-reading a vote that is gone`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = unvoted())
            val vm = viewModel(repository, uid = OWNER_UID, admin = true)
            advanceUntilIdle()
            val readsBefore = repository.reads

            vm.delete()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.deleted)
            assertNull(vm.uiState.value.vote)
            assertEquals(readsBefore, repository.reads)
        }

    // --- Voters ---------------------------------------------------------------

    /** The first page rides along with the vote, so opening the strip must not cost a request. */
    @Test
    fun `opening a voter list uses the page that came with the vote`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = voted(firstPageVoters = (1L..10L).toList()))
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.expandVoters(13201)
            advanceUntilIdle()

            assertEquals((1L..10L).toList(), vm.uiState.value.voters[13201]?.uids)
            assertTrue(repository.voterPages.isEmpty())
        }

    @Test
    fun `loading more appends the next page and stops on a short one`() =
        runTest(dispatcher) {
            val repository =
                FakeVoteRepository(
                    vote = voted(firstPageVoters = (1L..10L).toList()),
                    voterPagesByPage = mapOf(2 to (11L..13L).toList()),
                )
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.expandVoters(13201)
            advanceUntilIdle()

            vm.expandVoters(13201)
            advanceUntilIdle()

            val list = requireNotNull(vm.uiState.value.voters[13201])
            assertEquals((1L..13L).toList(), list.uids)
            assertFalse(list.hasMore)
            assertEquals(listOf(2), repository.voterPages)
        }

    /** Anonymous votes have no voter list, and asking for one would be a request that cannot answer. */
    @Test
    fun `an anonymous vote never asks for voters`() =
        runTest(dispatcher) {
            val repository = FakeVoteRepository(vote = voted(isPublic = false))
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.expandVoters(13201)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.voters.isEmpty())
        }

    private companion object {
        const val VOTE_ID = 2871L
        const val OWNER_UID = 57815L

        fun unvoted(
            multiple: Boolean = false,
            locked: Boolean = false,
        ) = Vote(
            id = VOTE_ID,
            title = "哪个运营商比较好",
            ownerUid = OWNER_UID,
            isPublic = true,
            locked = locked,
            multiple = multiple,
            items =
            listOf(
                VoteItem(13201, "移动", voted = false),
                VoteItem(13202, "联通", voted = false),
                VoteItem(13203, "电信", voted = false),
            ),
        )

        fun voted(
            isPublic: Boolean = true,
            firstPageVoters: List<Long> = emptyList(),
        ) = Vote(
            id = VOTE_ID,
            title = "哪个运营商比较好",
            ownerUid = OWNER_UID,
            isPublic = isPublic,
            locked = false,
            multiple = false,
            items =
            listOf(
                VoteItem(13201, "移动", voted = true, count = 12, voters = firstPageVoters),
                VoteItem(13202, "联通", voted = false, count = 5),
                VoteItem(13203, "电信", voted = false, count = 23),
            ),
        )
    }
}

private class FakeVoteRepository(
    var vote: Vote?,
    private val readFailure: Throwable? = null,
    private val failure: Throwable? = null,
    private val voterPagesByPage: Map<Int, List<Long>> = emptyMap(),
) : VoteRepository {
    var reads = 0
        private set
    val submitted = mutableListOf<List<Long>>()

    /** Which pages of voters were actually requested — page 1 should never appear here. */
    val voterPages = mutableListOf<Int>()

    override suspend fun info(voteId: Long): Vote {
        reads++
        readFailure?.let { throw it }
        return requireNotNull(vote)
    }

    override suspend fun submit(voteId: Long, itemIds: List<Long>) {
        failure?.let { throw it }
        submitted += itemIds
    }

    override suspend fun create(title: String, multiple: Boolean, isPublic: Boolean, items: List<String>): Long = 1L

    override suspend fun setLocked(voteId: Long, locked: Boolean) {
        failure?.let { throw it }
    }

    override suspend fun delete(voteId: Long) {
        failure?.let { throw it }
    }

    override suspend fun voters(itemId: Long, page: Int): List<Long> {
        voterPages += page
        return voterPagesByPage[page].orEmpty()
    }
}

private class FakeProfile(
    override val selfUid: Long?,
    override val selfIsAdmin: Boolean,
) : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile = error("not used")

    override suspend fun profile(uid: Long): UserProfile = error("not used")
}
