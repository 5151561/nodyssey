package io.github.nsreader.ui.account

import io.github.nsreader.data.account.TwoFactorState
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
 * The two riskiest writes in the app. What is tested here is that neither can happen on one tap, and
 * that a failed attempt does not cost the user everything they typed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun readyViewModel(repository: FakeAccountSettingsRepository): SecurityViewModel {
        val vm = SecurityViewModel(repository)
        vm.updateCurrentPassword("old-password")
        vm.updateNewPassword("Correct-Horse-9")
        vm.updateConfirmPassword("Correct-Horse-9")
        return vm
    }

    @Test
    fun `reads the two-factor state on open`() =
        runTest(dispatcher) {
            val vm = SecurityViewModel(FakeAccountSettingsRepository(twoFactor = TwoFactorState(true)))
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
            val vm = readyViewModel(FakeAccountSettingsRepository.pendingEndpoints())
            advanceUntilIdle()

            vm.confirmPasswordChange()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("old-password", state.currentPassword)
            assertEquals("Correct-Horse-9", state.newPassword)
            assertTrue(state.message is AccountMessage.EndpointPending)
        }

    @Test
    fun `an incomplete or mismatched form cannot be submitted`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = SecurityViewModel(repository)
            advanceUntilIdle()

            vm.updateCurrentPassword("old-password")
            vm.updateNewPassword("Correct-Horse-9")
            vm.updateConfirmPassword("Correct-Horse-8")
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
            val vm = SecurityViewModel(repository)
            advanceUntilIdle()

            vm.updateCurrentPassword("old-password")
            vm.updateNewPassword("short")
            vm.updateConfirmPassword("short")

            assertTrue(vm.uiState.value.isTooShort)
            vm.confirmPasswordChange()
            advanceUntilIdle()
            assertNull(repository.changedPassword)
        }

    @Test
    fun `two-factor enrolment also waits for its confirmation, then hands out the uri`() =
        runTest(dispatcher) {
            val repository = FakeAccountSettingsRepository()
            val vm = SecurityViewModel(repository)
            advanceUntilIdle()

            vm.requestTwoFactorEnrolment()
            advanceUntilIdle()
            assertEquals(SecurityConfirmation.TwoFactor, vm.uiState.value.confirming)
            assertFalse(repository.calls.contains("beginTwoFactorEnrolment"))

            vm.confirmTwoFactorEnrolment()
            advanceUntilIdle()

            assertEquals(repository.enrolmentUri, vm.uiState.value.enrolmentUri)
        }

    /** The URI is handed over once; keeping it would re-launch the authenticator on every recomposition. */
    @Test
    fun `the enrolment uri is consumed after being handed over`() =
        runTest(dispatcher) {
            val vm = SecurityViewModel(FakeAccountSettingsRepository())
            advanceUntilIdle()
            vm.confirmTwoFactorEnrolment()
            advanceUntilIdle()

            vm.consumeEnrolmentUri()

            assertNull(vm.uiState.value.enrolmentUri)
        }

    @Test
    fun `a missing authenticator app is reported rather than swallowed`() =
        runTest(dispatcher) {
            val vm = SecurityViewModel(FakeAccountSettingsRepository())
            advanceUntilIdle()

            vm.reportMissingAuthenticatorApp()

            assertTrue(vm.uiState.value.message is AccountMessage.Info)
        }
}
