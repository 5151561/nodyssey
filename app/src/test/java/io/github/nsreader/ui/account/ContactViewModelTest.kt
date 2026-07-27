package io.github.nsreader.ui.account

import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.TelegramBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * The two flows that can cost the user their notifications or their account access: the site's
 * two-step email change, and the Telegram bind whose second half happens in another app entirely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val verified = AccountContact(email = "hikari.zhg@gmail.com", emailVerified = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the address and the telegram binding`() =
        runTest(dispatcher) {
            val repository =
                FakeAccountSettingsRepository(
                    contact = verified,
                    telegram = TelegramBinding(bound = true, username = "@hikari_zhg"),
                )
            val vm = ContactViewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals("hikari.zhg@gmail.com", state.email)
            assertTrue(state.emailVerified)
            assertEquals(true, state.telegram?.bound)
        }

    @Test
    fun `the code cannot be sent without a password and a well-formed new address`() =
        runTest(dispatcher) {
            val vm = ContactViewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()
            vm.toggleEmailChange()

            assertFalse(vm.uiState.value.canSendCode)
            vm.updatePassword("hunter2!")
            vm.updateNewEmail("not-an-address")
            assertFalse(vm.uiState.value.canSendCode)

            vm.updateNewEmail("ns.hikari@outlook.com")
            assertTrue(vm.uiState.value.canSendCode)
        }

    @Test
    fun `confirming needs a sent code of the full six digits`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()
            vm.toggleEmailChange()
            vm.updatePassword("hunter2!")
            vm.updateNewEmail("ns.hikari@outlook.com")
            vm.sendCode()
            advanceUntilIdle()

            assertEquals("hunter2!" to "ns.hikari@outlook.com", repository.sentEmailChangeCode)
            vm.updateCode("123")
            assertFalse(vm.uiState.value.canConfirmChange)
            vm.updateCode("123456")
            assertTrue(vm.uiState.value.canConfirmChange)
        }

    /** Typing a different target address invalidates a code mailed to the previous one. */
    @Test
    fun `changing the new address after sending resets the code step`() =
        runTest(dispatcher) {
            val vm = ContactViewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()
            vm.toggleEmailChange()
            vm.updatePassword("hunter2!")
            vm.updateNewEmail("ns.hikari@outlook.com")
            vm.sendCode()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.codeSent)

            vm.updateNewEmail("other@outlook.com")

            assertFalse(vm.uiState.value.codeSent)
            assertEquals("", vm.uiState.value.code)
        }

    @Test
    fun `a confirmed change updates the address and collapses the flow`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()
            vm.toggleEmailChange()
            vm.updatePassword("hunter2!")
            vm.updateNewEmail("ns.hikari@outlook.com")
            vm.sendCode()
            advanceUntilIdle()
            vm.updateCode("123456")
            vm.confirmEmailChange()
            advanceUntilIdle()

            assertEquals(
                Triple("hunter2!", "ns.hikari@outlook.com", "123456"),
                repository.confirmedEmailChange,
            )
            val state = vm.uiState.value
            assertEquals("ns.hikari@outlook.com", state.email)
            // The code came from the new mailbox; the address is verified by construction.
            assertTrue(state.emailVerified)
            assertFalse(state.changeExpanded)
            assertEquals("", state.password)
        }

    @Test
    fun `collapsing the change flow abandons the typed password`() =
        runTest(dispatcher) {
            val vm = ContactViewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()
            vm.toggleEmailChange()
            vm.updatePassword("hunter2!")

            vm.toggleEmailChange()
            vm.toggleEmailChange()

            assertEquals("", vm.uiState.value.password)
        }

    @Test
    fun `confirming the bind dialog surfaces the bot link and starts waiting`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()

            vm.requestBind()
            assertTrue(vm.uiState.value.showBindDialog)
            vm.confirmBind()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.showBindDialog)
            assertEquals(repository.bindUrl, state.bindUrlToOpen)
            assertTrue(state.awaitingBinding)

            vm.consumeBindUrl()
            assertNull(vm.uiState.value.bindUrlToOpen)
        }

    /** The seam the whole f3 flow exists for: coming back from Telegram polls until bound. */
    @Test
    fun `returning from telegram polls until the binding lands`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()
            vm.confirmBind()
            advanceUntilIdle()

            vm.onResumed()
            advanceTimeBy(ContactViewModel.BIND_POLL_INTERVAL_MILLIS + 1)
            assertTrue(vm.uiState.value.awaitingBinding)

            // The bot finishes its half while the app is polling.
            repository.telegram = TelegramBinding(bound = true, username = "@hikari_zhg")
            advanceTimeBy(ContactViewModel.BIND_POLL_INTERVAL_MILLIS + 1)

            val state = vm.uiState.value
            assertEquals(true, state.telegram?.bound)
            assertFalse(state.awaitingBinding)
        }

    @Test
    fun `polling gives up after its attempts and leaves the manual refresh to finish`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()
            vm.confirmBind()
            advanceUntilIdle()

            vm.onResumed()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.awaitingBinding)

            repository.telegram = TelegramBinding(bound = true, username = "@hikari_zhg")
            vm.refreshBinding()
            advanceUntilIdle()

            assertEquals(true, vm.uiState.value.telegram?.bound)
            assertFalse(vm.uiState.value.awaitingBinding)
        }

    @Test
    fun `unbinding needs the dialog to be confirmed`() =
        runTest(dispatcher) {
            val repository =
                FakeAccountSettingsRepository(
                    contact = verified,
                    telegram = TelegramBinding(bound = true, username = "@hikari_zhg"),
                )
            val vm = ContactViewModel(repository)
            advanceUntilIdle()

            vm.requestUnbind()
            vm.dismissUnbind()
            advanceUntilIdle()
            assertFalse("unbindTelegram" in repository.calls)

            vm.requestUnbind()
            vm.confirmUnbind()
            advanceUntilIdle()

            assertTrue("unbindTelegram" in repository.calls)
            assertEquals(false, vm.uiState.value.telegram?.bound)
        }

    @Test
    fun `pending endpoints raise the banner and never a snackbar`() =
        runTest(dispatcher) {
            val vm = ContactViewModel(FakeAccountSettingsRepository.pendingEndpoints())
            advanceUntilIdle()

            assertTrue(vm.uiState.value.endpointPending)
            assertNull(vm.uiState.value.message)
        }
}
