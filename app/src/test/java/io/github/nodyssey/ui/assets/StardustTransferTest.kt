package io.github.nodyssey.ui.assets

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustLedgerPage
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.UserProfile
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
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
 * 星辰转账, which is the one thing this app does that cannot be undone by doing it again.
 *
 * Everything pinned here is a way the screen could send something the user did not agree to: twice,
 * or to the uid they had already corrected, or without ever having been told whose uid it was. The
 * balance guard is in the same family — a send the site would refuse anyway, but refusing it here is
 * what keeps the confirmation step honest about the numbers it just showed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StardustTransferTest {
    private val dispatcher = StandardTestDispatcher()
    private val stardust = FakeStardustRepository()
    private val profile = FakeProfileRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StardustViewModel(profile, stardust)

    private fun StardustViewModel.fillForm(
        amount: String = "2",
        recipient: String = "9",
        ref: String = "866042",
    ) {
        openTransfer()
        this.amount.setTextAndPlaceCursorAtEnd(amount)
        recipientUid.setTextAndPlaceCursorAtEnd(recipient)
        refId.setTextAndPlaceCursorAtEnd(ref)
    }

    @Test
    fun `names the recipient the site names when the confirmation opens`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm()

            vm.requestConfirm()
            assertEquals(RecipientCheck.Checking, vm.uiState.value.recipient)
            advanceUntilIdle()

            assertEquals(listOf(9L to VIEWER), stardust.lookups)
            assertEquals(RecipientCheck.Named("站长"), vm.uiState.value.recipient)
            assertTrue(vm.uiState.value.confirmOpen)
        }

    /**
     * A lookup that fails must not take the transfer down with it.
     *
     * `payment-prepare` is a courtesy — `send` does not depend on it — so a refusal, or an `origin`
     * the server stops accepting, has to leave the user with a working transfer and a warning rather
     * than a button that can never be pressed again.
     */
    @Test
    fun `still sends when the recipient lookup is refused`() =
        runTest(dispatcher) {
            stardust.lookupError = SiteException(SiteError.Unknown, detail = "用户不存在")
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm()

            vm.requestConfirm()
            advanceUntilIdle()

            assertEquals(RecipientCheck.Unnamed("用户不存在"), vm.uiState.value.recipient)

            vm.confirmTransfer()
            advanceUntilIdle()

            assertEquals(listOf(Sent(9, 2, 866_042)), stardust.sent)
        }

    /** A lookup landing after the user went back and retyped would otherwise name the old uid. */
    @Test
    fun `drops a lookup that answers about a uid the form no longer holds`() =
        runTest(dispatcher) {
            stardust.lookupGate = CompletableDeferred()
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm(recipient = "9")
            vm.requestConfirm()
            advanceUntilIdle()

            vm.dismissConfirm()
            vm.fillForm(recipient = "10")
            vm.requestConfirm()
            stardust.lookupGate?.complete(Unit)
            advanceUntilIdle()

            // The second lookup is the one on screen; the first was cancelled with its dialog.
            assertEquals(listOf(9L to VIEWER, 10L to VIEWER), stardust.lookups)
            assertEquals(RecipientCheck.Named("站长"), vm.uiState.value.recipient)
        }

    @Test
    fun `sends once when 确认 is tapped twice`() =
        runTest(dispatcher) {
            stardust.sendGate = CompletableDeferred()
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm()
            vm.requestConfirm()
            advanceUntilIdle()

            vm.confirmTransfer()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSending)

            vm.confirmTransfer()
            // The layer cannot be closed out from under an in-flight send either.
            vm.dismissConfirm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.confirmOpen)

            stardust.sendGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(Sent(9, 2, 866_042)), stardust.sent)
        }

    @Test
    fun `refuses to send more than the balance`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm(amount = "9")
            vm.requestConfirm()
            advanceUntilIdle()

            vm.confirmTransfer()
            advanceUntilIdle()

            assertEquals(3, vm.uiState.value.shortfall)
            assertTrue(stardust.sent.isEmpty())
        }

    @Test
    fun `clears the form and says how much left once the send lands`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm()
            vm.requestConfirm()
            advanceUntilIdle()

            vm.confirmTransfer()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(StardustMessage.Sent(2), state.message)
            assertFalse(state.transferOpen)
            assertFalse(state.confirmOpen)
            assertFalse(state.isSending)
            assertEquals("", vm.amount.text.toString())
            assertEquals("", vm.recipientUid.text.toString())
            assertEquals("", vm.refId.text.toString())
            // The balance and the ledger both moved, so both were asked again.
            assertEquals(2, profile.calls)
        }

    /**
     * A refusal leaves the three fields alone.
     *
     * "余额不足" and a mistyped Ref ID are both fixed in this form, and retyping a uid from memory is
     * exactly the moment a digit goes missing.
     */
    @Test
    fun `keeps the typed form and the site's sentence when the send is refused`() =
        runTest(dispatcher) {
            stardust.sendError = SiteException(SiteError.Unknown, detail = "余额不足")
            val vm = viewModel()
            advanceUntilIdle()
            vm.fillForm()
            vm.requestConfirm()
            advanceUntilIdle()

            vm.confirmTransfer()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(StardustMessage.Failed(SiteError.Unknown, "余额不足"), state.message)
            assertTrue(state.transferOpen)
            assertFalse(state.confirmOpen)
            assertFalse(state.isSending)
            assertEquals("9", vm.recipientUid.text.toString())
            assertEquals("866042", vm.refId.text.toString())

            vm.consumeMessage()
            assertNull(vm.uiState.value.message)
        }

    private companion object {
        const val VIEWER = 52_425L
    }
}

private data class Sent(
    val recipientUid: Long,
    val amount: Int,
    val refId: Long,
)

private class FakeStardustRepository : StardustRepository {
    val lookups = mutableListOf<Pair<Long, Long>>()
    val sent = mutableListOf<Sent>()
    var lookupGate: CompletableDeferred<Unit>? = null
    var sendGate: CompletableDeferred<Unit>? = null
    var lookupError: Throwable? = null
    var sendError: Throwable? = null

    override suspend fun entries(
        memberId: Long,
        beforeId: Long?,
    ) = StardustLedgerPage(entries = emptyList(), cursor = null, hasMore = false)

    override suspend fun recipientName(
        recipientUid: Long,
        viewerUid: Long,
    ): String? {
        lookups += recipientUid to viewerUid
        lookupGate?.await()
        lookupError?.let { throw it }
        return "站长"
    }

    override suspend fun send(
        recipientUid: Long,
        amount: Int,
        refId: Long,
        viewerUid: Long,
        onetime: Boolean,
    ) {
        sendGate?.await()
        sendError?.let { throw it }
        sent += Sent(recipientUid, amount, refId)
    }

    override suspend fun receipts(
        memberId: Long,
        refId: Long,
        peerId: Long?,
    ): List<StardustEntry> = emptyList()
}

private class FakeProfileRepository : ProfileRepository {
    var calls = 0
        private set

    override suspend fun profile(refresh: Boolean): UserProfile {
        calls++
        return profile(52_425)
    }

    override suspend fun profile(uid: Long): UserProfile =
        UserProfile(
            uid = uid,
            name = "nssk",
            avatarUrl = "https://www.nodeseek.com/avatar/$uid.png",
            starCount = 6,
        )
}
