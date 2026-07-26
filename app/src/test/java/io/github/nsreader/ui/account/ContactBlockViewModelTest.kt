package io.github.nsreader.ui.account

import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.BlockedUser
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The verified badge is what this screen is really about: an address that looks saved but is not
 * verified cannot recover the account, so the rules for when the badge disappears are load-bearing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val verified =
        AccountContact(
            email = "hikari.zhg@gmail.com",
            emailVerified = true,
            backupEmail = "ns.backup@outlook.com",
            backupEmailVerified = false,
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeAccountSettingsRepository) =
        ContactBlockViewModel(repository)

    @Test
    fun `loads the address and the blocked list`() =
        runTest(dispatcher) {
            val repository =
                FakeAccountSettingsRepository(
                    contact = verified,
                    blocked = listOf(BlockedUser(uid = 1, name = "机场信仰充值中")),
                )
            val vm = viewModel(repository)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isLoading)
            assertEquals("hikari.zhg@gmail.com", state.email)
            assertTrue(state.emailVerified)
            assertFalse(state.backupEmailVerified)
            assertEquals(1, state.blocked.size)
        }

    /** The whole point: typing a new address must not keep claiming the old one was verified. */
    @Test
    fun `editing the address revokes the verified badge immediately`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.emailVerified)

            vm.updateEmail("someone.else@gmail.com")

            assertFalse(vm.uiState.value.emailVerified)
        }

    @Test
    fun `typing the original address back restores the badge`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()

            vm.updateEmail("someone.else@gmail.com")
            vm.updateEmail("hikari.zhg@gmail.com")

            assertTrue(vm.uiState.value.emailVerified)
        }

    /** A saved-but-changed address is unverified until the user clicks the mail the server just sent. */
    @Test
    fun `saving a changed address leaves it unverified`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.updateEmail("someone.else@gmail.com")
            vm.save()
            advanceUntilIdle()

            assertEquals("someone.else@gmail.com" to "ns.backup@outlook.com", repository.savedContact)
            assertFalse(vm.uiState.value.emailVerified)
        }

    @Test
    fun `saving without changing the address keeps the badge`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository(contact = verified)
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.updateBackupEmail("other.backup@outlook.com")
            vm.save()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.emailVerified)
        }

    @Test
    fun `save is blocked while nothing changed and while an address is malformed`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(contact = verified))
            advanceUntilIdle()
            assertFalse("unchanged form must not be saveable", vm.uiState.value.canSave)

            vm.updateEmail("not-an-address")
            assertTrue(vm.uiState.value.isEmailMalformed)
            assertFalse(vm.uiState.value.canSave)

            vm.updateEmail("fine@example.com")
            assertTrue(vm.uiState.value.canSave)
        }

    @Test
    fun `unblocking removes the row only after the dialog is confirmed`() =
        runTest(dispatcher) {
            val target = BlockedUser(uid = 7, name = "vps_matthew")
            val repository = FakeAccountSettingsRepository(blocked = listOf(target))
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.requestUnblock(target)
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.blocked.size)
            assertTrue(repository.unblocked.isEmpty())

            vm.confirmUnblock()
            advanceUntilIdle()

            assertEquals(listOf(7L), repository.unblocked)
            assertTrue(vm.uiState.value.blocked.isEmpty())
        }

    @Test
    fun `dismissing the dialog unblocks nobody`() =
        runTest(dispatcher) {
            val target = BlockedUser(uid = 7, name = "vps_matthew")
            val repository = FakeAccountSettingsRepository(blocked = listOf(target))
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.requestUnblock(target)
            vm.dismissUnblock()
            vm.confirmUnblock()
            advanceUntilIdle()

            assertTrue(repository.unblocked.isEmpty())
            assertEquals(1, vm.uiState.value.blocked.size)
        }

    /** A pending endpoint raises the banner and stays out of the snackbar; see the ViewModel. */
    @Test
    fun `a pending endpoint shows the banner without a snackbar`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository.pendingEndpoints())
            advanceUntilIdle()

            assertTrue(vm.uiState.value.endpointPending)
            assertEquals(null, vm.uiState.value.message)
        }
}
