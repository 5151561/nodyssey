package io.github.nodyssey.ui.account

import io.github.nodyssey.data.account.TwoFactorState
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * The two riskiest writes in the app. What is tested here is that neither can happen on one tap, and
 * that a failed attempt does not cost the user everything they typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private val viewModels = ViewModels()

    private fun viewModel(repository: FakeAccountSettingsRepository) =
        viewModels.track(SecurityViewModel(repository))

    private fun TestScope.readyViewModel(repository: FakeAccountSettingsRepository): SecurityViewModel {
        val vm = viewModel(repository)
        vm.currentPasswordState.typeText("old-password")
        vm.newPasswordState.typeText("Correct-Horse-9")
        vm.confirmPasswordState.typeText("Correct-Horse-9")
        runCurrent()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `reads the two-factor state on open`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository(twoFactor = TwoFactorState(true)))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertEquals(true, vm.uiState.value.twoFactorEnabled)
        }

    @Test
    fun `requesting a password change only opens the dialog`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = readyViewModel(repository)
            advanceUntilIdle()

            vm.requestPasswordChange()
            advanceUntilIdle()

            assertEquals(SecurityConfirmation.Password, vm.uiState.value.confirming)
            assertNull("the request alone must not reach the network", repository.changedPassword)
        }

    @Test
    fun `only the confirmation submits`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = readyViewModel(repository)
            advanceUntilIdle()

            vm.requestPasswordChange()
            vm.confirmPasswordChange()
            advanceUntilIdle()

            assertEquals("old-password" to "Correct-Horse-9", repository.changedPassword)
            assertNull(vm.uiState.value.confirming)
        }

    @Test
    fun `a successful change clears all three fields`() =
        runTest(dispatcher) {
            val vm = readyViewModel(FakeAccountSettingsRepository())
            advanceUntilIdle()

            vm.confirmPasswordChange()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("", state.currentPassword)
            assertEquals("", state.newPassword)
            assertEquals("", state.confirmPassword)
        }

    /** Wiping the form on failure would make the user retype three passwords to retry. */
    @Test
    fun `a failed change keeps what was typed`() =
        runTest(dispatcher) {
            val vm = readyViewModel(FakeAccountSettingsRepository.failing())
            advanceUntilIdle()

            vm.confirmPasswordChange()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("old-password", state.currentPassword)
            assertEquals("Correct-Horse-9", state.newPassword)
            assertTrue(state.message is AccountMessage.Failure)
        }

    @Test
    fun `an incomplete or mismatched form cannot be submitted`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.currentPasswordState.typeText("old-password")
            vm.newPasswordState.typeText("Correct-Horse-9")
            vm.confirmPasswordState.typeText("Correct-Horse-8")
            runCurrent()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isMismatched)
            assertFalse(vm.uiState.value.canSubmitPassword)

            vm.confirmPasswordChange()
            advanceUntilIdle()
            assertNull(repository.changedPassword)
        }

    @Test
    fun `a too-short password cannot be submitted`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.currentPasswordState.typeText("old-password")
            vm.newPasswordState.typeText("short")
            vm.confirmPasswordState.typeText("short")
            runCurrent()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isTooShort)
            vm.confirmPasswordChange()
            advanceUntilIdle()
            assertNull(repository.changedPassword)
        }

    @Test
    fun `two-factor enrolment also waits for its confirmation, then hands out the uri`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.requestTwoFactorEnrolment()
            advanceUntilIdle()
            assertEquals(SecurityConfirmation.TwoFactor, vm.uiState.value.confirming)
            assertFalse(repository.calls.contains("beginTwoFactorEnrolment"))

            // The site's enrolment endpoint takes the account password, so the dialog collects one.
            vm.twoFactorPasswordState.typeText("hunter2!")
            runCurrent()
            advanceUntilIdle()
            vm.confirmTwoFactorEnrolment()
            advanceUntilIdle()

            assertEquals(repository.enrolmentUri, vm.uiState.value.enrolmentUri)
            assertEquals("hunter2!", repository.enrolmentPassword)
            assertEquals("the password is not parked in state", "", vm.uiState.value.twoFactorPassword)
        }

    /**
     * The dialog disables its confirm without a password, but the guard lives here too: this is the
     * only place the rule can be tested, since a text field in a dialog never idles under Robolectric.
     */
    @Test
    fun `enrolment without a password never reaches the site`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()

            vm.requestTwoFactorEnrolment()
            vm.confirmTwoFactorEnrolment()
            advanceUntilIdle()

            assertFalse(repository.calls.contains("beginTwoFactorEnrolment"))
            assertNull(vm.uiState.value.enrolmentUri)
        }

    /** The URI is handed over once; keeping it would re-launch the authenticator on every recomposition. */
    @Test
    fun `the enrolment uri is consumed after being handed over`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository())
            advanceUntilIdle()
            vm.twoFactorPasswordState.typeText("hunter2!")
            runCurrent()
            advanceUntilIdle()
            vm.confirmTwoFactorEnrolment()
            advanceUntilIdle()

            vm.consumeEnrolmentUri()

            assertNull(vm.uiState.value.enrolmentUri)
        }

    @Test
    fun `a missing authenticator app is reported rather than swallowed`() =
        runTest(dispatcher) {
            val vm = viewModel(FakeAccountSettingsRepository())
            advanceUntilIdle()

            vm.reportMissingAuthenticatorApp()

            assertTrue(vm.uiState.value.message is AccountMessage.Info)
        }
}
