package io.github.nodyssey.ui.login

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.session.FakeSessionCookieStore
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInCredentials
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.session.TwoFactorChallenge
import io.github.nodyssey.ui.ViewModels
import io.github.nodyssey.ui.typeText
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
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
 * 登录 · 原生表单 (h1)'s state machine.
 *
 * What is held here is card 2's promise — that pressing 登录 can cost the user a refusal but never
 * the text they typed — and the two transitions the endpoint's shape forces: a second factor is a
 * different page, and an expired `otpSession` is the one refusal that has to walk back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModels()
    private val cookies = FakeSessionCookieStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModels.clear(dispatcher.scheduler)
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeSignInRepository) =
        viewModels.track(
            SignInViewModel(
                signIn = repository,
                session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies)),
                clock = AppClock { FIXED_MILLIS },
            ),
        )

    /** Filled in and verified — the only state from which 登录 is pressable. */
    private fun TestScope.readyViewModel(repository: FakeSignInRepository): SignInViewModel {
        val vm = viewModel(repository)
        vm.accountState.typeText("nssk")
        vm.passwordState.typeText("hunter2hunter2")
        runCurrent()
        vm.onVerified("turnstile-token")
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `the button stays down until the verification block answers`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository()
            val vm = viewModel(repository)
            vm.accountState.typeText("nssk")
            vm.passwordState.typeText("hunter2hunter2")
            advanceUntilIdle()

            // The widget is up and waiting, which is not the same as answered.
            assertEquals(VerificationState.Pending, vm.uiState.value.verification)
            assertFalse("a filled form with no token must not be submittable", vm.uiState.value.canSubmitCredentials)

            vm.submitCredentials()
            advanceUntilIdle()

            assertNull("pressing a disabled button must not reach the network", repository.sent)

            // And the same when there is no widget at all — the desktop target, or one that failed
            // to start. Nothing about that state makes the form sendable.
            vm.onVerificationUnavailable()
            advanceUntilIdle()

            assertEquals(VerificationState.NotWired, vm.uiState.value.verification)
            assertFalse(vm.uiState.value.canSubmitCredentials)
        }

    @Test
    fun `the token travels with the credentials`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository()
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            assertEquals(
                SignInCredentials("nssk", "hunter2hunter2", "turnstile-token"),
                repository.sent,
            )
            assertTrue(vm.uiState.value.signedIn)
        }

    @Test
    fun `a refusal keeps both fields and spends the verification`() =
        runTest(dispatcher) {
            val repository =
                FakeSignInRepository(
                    outcome = SignInOutcome.Refused(SignInRefusal.Credentials, "用户名或密码不正确"),
                )
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("nssk", vm.accountState.text.toString())
            assertEquals("hunter2hunter2", vm.passwordState.text.toString())
            assertTrue(state.hasCredentialRefusal)
            assertEquals("用户名或密码不正确", state.refusal?.detail)
            // The site resets its own widget on every refusal; a spent token must not look reusable.
            assertEquals(VerificationState.Expired, state.verification)
            assertFalse(state.canSubmitCredentials)
            // …and the widget has to be told, or the next press reuses a token that is already gone.
            assertEquals(1, state.verificationGeneration)
        }

    @Test
    fun `a second factor opens its own step with an empty box`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge))
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SignInStep.TwoFactor, state.step)
            assertEquals(challenge, state.challenge)
            assertEquals("", vm.codeState.text.toString())
            assertFalse("an empty box cannot be submitted", state.canSubmitCode)
            // 30-second window, and the clock sits 12s into one — see FIXED_MILLIS. A reader, not a
            // loop: a `while (true) { delay(…) }` in the ViewModel would make this very
            // `advanceUntilIdle()` spin through virtual time and never come back.
            assertEquals(18, vm.secondsUntilNextCode())
        }

    @Test
    fun `six digits arm the verify button and reach the endpoint`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge))
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            repository.outcome = SignInOutcome.Signed
            vm.codeState.typeText("49172")
            runCurrent()
            assertFalse("five digits is not a code", vm.uiState.value.canSubmitCode)

            vm.codeState.typeText("491723")
            runCurrent()
            assertTrue(vm.uiState.value.canSubmitCode)

            vm.submitTwoFactor()
            advanceUntilIdle()

            assertEquals(challenge to "491723", repository.verified)
            assertTrue(vm.uiState.value.signedIn)
        }

    @Test
    fun `an expired otp session walks back to the password`() =
        runTest(dispatcher) {
            val challenge = TwoFactorChallenge(account = "nssk", otpSession = "otp-1")
            val repository = FakeSignInRepository(outcome = SignInOutcome.TwoFactorRequired(challenge))
            val vm = readyViewModel(repository)
            vm.submitCredentials()
            advanceUntilIdle()

            repository.outcome =
                SignInOutcome.Refused(SignInRefusal.TwoFactorSessionExpired, "OTP_EXPIRED_OR_NOT_EXIST")
            vm.codeState.typeText("491723")
            runCurrent()
            vm.submitTwoFactor()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(SignInStep.Credentials, state.step)
            assertNull("nothing is left to post the code with", state.challenge)
            assertEquals("the account is still there to try again", "nssk", vm.accountState.text.toString())
            assertEquals(SignInRefusal.TwoFactorSessionExpired, state.refusal?.reason)
        }

    @Test
    fun `a transport failure is a snackbar, and retry sends the same thing again`() =
        runTest(dispatcher) {
            val repository = FakeSignInRepository(failure = SiteException(SiteError.Network))
            val vm = readyViewModel(repository)

            vm.submitCredentials()
            advanceUntilIdle()

            assertEquals(SiteError.Network, vm.uiState.value.failure)
            assertNull("a failed request is not a refusal", vm.uiState.value.refusal)
            assertEquals("hunter2hunter2", vm.passwordState.text.toString())

            repository.failure = null
            vm.retry()
            advanceUntilIdle()

            assertEquals(SignInCredentials("nssk", "hunter2hunter2", "turnstile-token"), repository.sent)
            assertTrue(vm.uiState.value.signedIn)
        }

    private companion object {
        /** 1970-01-01T00:00:12Z — twelve seconds into a 30-second TOTP window, so 18 are left. */
        const val FIXED_MILLIS = 12_000L
    }
}

private class FakeSignInRepository(
    var outcome: SignInOutcome = SignInOutcome.Signed,
    var failure: Throwable? = null,
) : SignInRepository {
    var sent: SignInCredentials? = null
        private set
    var verified: Pair<TwoFactorChallenge, String>? = null
        private set

    override suspend fun signIn(credentials: SignInCredentials): SignInOutcome {
        sent = credentials
        failure?.let { throw it }
        return outcome
    }

    override suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome {
        verified = challenge to code
        failure?.let { throw it }
        return outcome
    }
}
