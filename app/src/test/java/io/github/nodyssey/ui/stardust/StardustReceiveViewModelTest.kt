package io.github.nodyssey.ui.stardust

import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustLedgerPage
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.StardustType
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.session.SessionState
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.richtext.RichNode
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The tally a 收款码 shows, and the one write behind its button.
 *
 * Every assertion here is about a number a reader will act on with real stardust, so the interesting
 * cases are the ones where the card must say *nothing* rather than something plausible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StardustReceiveViewModelTest {
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
        repository: StardustRepository,
        uid: Long? = 9,
        node: RichNode.StardustReceive = CODE,
    ) = StardustReceiveViewModel(node, repository, session, FakeProfile(uid))

    @Test
    fun `counts the payers and sums only what came in`() =
        runTest(dispatcher) {
            val repository =
                FakeStardustRepository(
                    // A refund out against the same ref is a real row, and adding it to "收到" would
                    // report more than the payee ever received.
                    all = listOf(entry(id = 1, peerId = 11, diff = 2), entry(id = 2, peerId = 12, diff = 3), entry(id = 3, peerId = 11, diff = -2)),
                    mine = emptyList(),
                )

            val vm = viewModel(repository)
            advanceUntilIdle()

            assertEquals(3, vm.uiState.value.payerCount)
            assertEquals(5, vm.uiState.value.received)
            assertEquals(false, vm.uiState.value.paidByMe)
        }

    /** The narrowed read is what answers "have I paid", and a row of my own is a yes. */
    @Test
    fun `reads a payment of my own as having paid`() =
        runTest(dispatcher) {
            val repository =
                FakeStardustRepository(
                    all = listOf(entry(id = 1, peerId = 9, diff = 2)),
                    mine = listOf(entry(id = 1, peerId = 9, diff = 2)),
                )

            val vm = viewModel(repository)
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.paidByMe)
            assertEquals(listOf(9L), repository.narrowedTo)
        }

    /**
     * Signed out, "你未付款" is a claim the app cannot make.
     *
     * It would also be the wrong one to guess: the reader may well have paid from the web, and a card
     * telling them otherwise is how somebody pays a one-off code twice and cannot get it back.
     */
    @Test
    fun `says nothing about my own payment when the account is unknown`() =
        runTest(dispatcher) {
            val repository = FakeStardustRepository(all = emptyList(), mine = emptyList())

            val vm = viewModel(repository, uid = null)
            advanceUntilIdle()

            assertNull(vm.uiState.value.paidByMe)
            assertTrue(repository.narrowedTo.isEmpty())
            // The public half still loaded; only the personal question went unasked.
            assertEquals(0, vm.uiState.value.payerCount)
        }

    /** The code's own `onetime` has to reach the send, or a one-off code becomes payable twice. */
    @Test
    fun `pays with the code's amount, ref and onetime flag`() =
        runTest(dispatcher) {
            val repository = FakeStardustRepository(all = emptyList(), mine = emptyList())

            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.pay()
            advanceUntilIdle()

            assertEquals(listOf(Sent(recipientUid = 52_425, amount = 2, refId = 100, onetime = true)), repository.sent)
        }

    /** A refused payment keeps the site's own sentence — "余额不足" beats anything written here. */
    @Test
    fun `holds the site's refusal for the card to show`() =
        runTest(dispatcher) {
            val repository =
                FakeStardustRepository(
                    all = emptyList(),
                    mine = emptyList(),
                    sendFailure = SiteException(SiteError.Unknown, detail = "余额不足"),
                )

            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.pay()
            advanceUntilIdle()

            assertEquals("余额不足", vm.uiState.value.failure?.detail)
            vm.onFailureShown()
            assertNull(vm.uiState.value.failure)
        }

    /** A tally that will not read is an error, not an empty code: zero would be a lie. */
    @Test
    fun `a failed read leaves the tally unset rather than zero`() =
        runTest(dispatcher) {
            val repository =
                FakeStardustRepository(all = emptyList(), mine = emptyList(), readFailure = SiteException(SiteError.Network))

            val vm = viewModel(repository)
            advanceUntilIdle()

            assertNull(vm.uiState.value.payerCount)
            assertNull(vm.uiState.value.received)
            assertEquals(SiteError.Network, vm.uiState.value.error)
        }

    private fun entry(
        id: Long,
        peerId: Long,
        diff: Int,
    ) = StardustEntry(
        id = id,
        type = StardustType.TRANSFER,
        rawType = null,
        diff = diff,
        balanceAfter = null,
        peerUid = peerId,
        commentId = null,
        refId = 100,
        createdAtMillis = null,
    )

    private companion object {
        val CODE =
            RichNode.StardustReceive(
                memberId = 52_425,
                refId = 100,
                amount = 2,
                description = "请我喝杯咖啡",
                onetime = true,
            )
    }
}

private data class Sent(
    val recipientUid: Long,
    val amount: Int,
    val refId: Long,
    val onetime: Boolean,
)

private class FakeStardustRepository(
    private val all: List<StardustEntry>,
    private val mine: List<StardustEntry>,
    private val readFailure: Throwable? = null,
    private val sendFailure: Throwable? = null,
) : StardustRepository {
    val sent = mutableListOf<Sent>()

    /** Every `peer_id` the card asked about, so a signed-out read can be shown never to have asked. */
    val narrowedTo = mutableListOf<Long>()

    override suspend fun entries(
        memberId: Long,
        beforeId: Long?,
    ) = StardustLedgerPage(entries = emptyList(), cursor = null, hasMore = false)

    override suspend fun recipientName(
        recipientUid: Long,
        viewerUid: Long,
    ): String? = null

    override suspend fun send(
        recipientUid: Long,
        amount: Int,
        refId: Long,
        viewerUid: Long,
        onetime: Boolean,
    ) {
        sendFailure?.let { throw it }
        sent += Sent(recipientUid, amount, refId, onetime)
    }

    override suspend fun receipts(
        memberId: Long,
        refId: Long,
        peerId: Long?,
    ): List<StardustEntry> {
        readFailure?.let { throw it }
        peerId?.let { narrowedTo += it }
        return if (peerId == null) all else mine
    }
}

private class FakeProfile(
    override val selfUid: Long?,
) : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile =
        UserProfile(uid = selfUid ?: 0L, name = "我", avatarUrl = "", rank = 3)

    override suspend fun profile(uid: Long): UserProfile = profile(refresh = false)
}
