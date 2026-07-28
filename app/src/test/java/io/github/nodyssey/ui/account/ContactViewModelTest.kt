package io.github.nodyssey.ui.account

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.account.AccountContact
import io.github.nodyssey.data.account.TelegramBinding
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
 * The seams around the two actions this screen hands to the website.
 *
 * Both leave the app, so what is worth pinning down is what happens on the way out and on the way
 * back: which URL is published, that the bind waits for an answer, and that 解绑 — the one Telegram
 * write the app can make itself — never fires without its dialog.
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
                    telegram = TelegramBinding(bound = true, displayName = "Hikari Zhg"),
                )
            val vm = ContactViewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals("hikari.zhg@gmail.com", state.email)
            assertTrue(state.emailVerified)
            assertEquals(true, state.telegram?.bound)
        }

    /** 修改邮箱 opens the site's own contact tab; nothing is submitted from here. */
    @Test
    fun `changing the address hands off to the site contact tab`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = ContactViewModel(repository)
            advanceUntilIdle()

            vm.changeEmailOnSite()

            assertEquals(
                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_CONTACT),
                vm.uiState.value.urlToOpen,
            )
            assertEquals(
                "reading is all this screen does to the address",
                listOf("contact", "telegramBinding"),
                repository.calls,
            )

            vm.consumeUrl()
            assertNull(vm.uiState.value.urlToOpen)
        }

    @Test
    fun `confirming the bind dialog opens the site and starts waiting`() =
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
            assertEquals(
                NodeSeekSite.BASE_URL + NodeSeekSite.settingPath(NodeSeekSite.SETTING_CONTACT),
                state.urlToOpen,
            )
            assertTrue(state.awaitingBinding)

            vm.consumeUrl()
            assertNull(vm.uiState.value.urlToOpen)
        }

    /** The seam the whole f3 flow exists for: coming back from the site polls until bound. */
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

            // The binding lands on the website while the app is polling.
            repository.telegram = TelegramBinding(bound = true, displayName = "Hikari Zhg")
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

            repository.telegram = TelegramBinding(bound = true, displayName = "Hikari Zhg")
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
                    telegram = TelegramBinding(bound = true, displayName = "Hikari Zhg"),
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
    fun `a failed load says so instead of showing an empty address`() =
        runTest(dispatcher) {
            val vm = ContactViewModel(FakeAccountSettingsRepository.failing())
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.message is AccountMessage.Failure)
            assertEquals("", state.email)
        }
}
