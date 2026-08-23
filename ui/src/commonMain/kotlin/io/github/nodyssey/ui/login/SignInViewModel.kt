package io.github.nodyssey.ui.login

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInCredentials
import io.github.nodyssey.data.session.SignInOutcome
import io.github.nodyssey.data.session.SignInRefusal
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.session.TwoFactorChallenge
import io.github.nodyssey.di.AppContainer
import io.github.nodyssey.ui.postlist.toSiteError
import io.github.plaza.core.AppClock
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for 登录 · 原生表单 (h1).
 *
 * The three cards on the board are three values of [SignInUiState], not three screens: card 1 is the
 * resting state, card 2 is [SignInUiState.isSubmitting] and [SignInUiState.refusal] together, card 3
 * is [SignInStep.TwoFactor]. Keeping them one state is what makes card 2's rule cheap to hold — a
 * submit must not lose what was typed — because nothing is cleared on the way between them.
 *
 * The password is never copied into [SignInUiState]; only its length reaches the state, which is all
 * the button needs to decide whether it can be pressed. Same reason `SecurityViewModel` reads
 * straight from the field at submit time: what gets sent must be exactly what is on screen, and a
 * mirrored copy is a frame behind.
 */
class SignInViewModel(
    private val signIn: SignInRepository,
    private val session: SessionRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    val accountState = TextFieldState()
    val passwordState = TextFieldState()

    /** Card 3's box — six digits, filtered by the screen's own input transformation. */
    val codeState = TextFieldState()

    private var submitJob: Job? = null

    init {
        snapshotFlow { accountState.text.toString() }
            .onEach { value -> _uiState.update { it.copy(account = value) } }
            .launchIn(viewModelScope)
        // Length, not text. Nothing on this screen needs to render the password back, and a copy in
        // an immutable state object outlives the field it came from.
        snapshotFlow { passwordState.text.length }
            .onEach { value -> _uiState.update { it.copy(passwordLength = value) } }
            .launchIn(viewModelScope)
        snapshotFlow { codeState.text.toString() }
            .onEach { value -> _uiState.update { it.copy(code = value) } }
            .launchIn(viewModelScope)
    }

    /** Cloudflare's widget handed over a token — see `TurnstileWidget`. */
    fun onVerified(token: String) =
        _uiState.update { it.copy(verification = VerificationState.Passed(token), refusal = null) }

    /**
     * The token aged out on its own.
     *
     * No generation bump: Turnstile's own `expired-callback` is the widget saying it already knows,
     * and asking it to reset on top of that would fight it.
     */
    fun onVerificationExpired() = _uiState.update { it.copy(verification = VerificationState.Expired) }

    /**
     * There is no widget — a platform with no web view, or a script that never arrived.
     *
     * Distinct from [onVerificationExpired] because no amount of waiting or tapping fixes it: the
     * block says so and 登录 stays down, which beats a button that sends a request the site is
     * certain to refuse.
     */
    fun onVerificationUnavailable() =
        _uiState.update { it.copy(verification = VerificationState.NotWired) }

    fun submitCredentials() {
        if (submitJob?.isActive == true) return
        if (!_uiState.value.canSubmitCredentials) return
        val account = accountState.text.toString()
        val password = passwordState.text.toString()
        val token = (_uiState.value.verification as? VerificationState.Passed)?.token
        submitJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, refusal = null, failure = null) }
                runCatchingExceptCancellation {
                    signIn.signIn(
                        SignInCredentials(
                            account = account,
                            password = password,
                            verificationToken = token,
                        ),
                    )
                }.onSuccess(::applyOutcome).onFailure(::applyFailure)
            }
    }

    fun submitTwoFactor() {
        if (submitJob?.isActive == true) return
        val state = _uiState.value
        if (!state.canSubmitCode) return
        val challenge = state.challenge ?: return
        val code = codeState.text.toString()
        submitJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, refusal = null, failure = null) }
                runCatchingExceptCancellation { signIn.verifyTwoFactor(challenge, code) }
                    .onSuccess(::applyOutcome)
                    .onFailure(::applyFailure)
            }
    }

    /** Card 3's back arrow — returns to the form with both fields still filled in. */
    fun leaveTwoFactor() {
        if (_uiState.value.step != SignInStep.TwoFactor) return
        returnToCredentials(refusal = null)
    }

    fun consumeFailure() = _uiState.update { it.copy(failure = null, failureDetail = null) }

    /** The snackbar's 重试 — the same submit, with the same input, for whichever step is showing. */
    fun retry() {
        consumeFailure()
        when (_uiState.value.step) {
            SignInStep.Credentials -> submitCredentials()
            SignInStep.TwoFactor -> submitTwoFactor()
        }
    }

    private fun applyOutcome(outcome: SignInOutcome) {
        when (outcome) {
            SignInOutcome.Signed -> {
                // Publish rather than peek: this is exactly the moment the rest of the app is meant
                // to notice, and it is the app's own request that changed the jar — no challenge is
                // half-finished underneath it.
                session.sync()
                _uiState.update { it.copy(isSubmitting = false, signedIn = true) }
            }

            is SignInOutcome.TwoFactorRequired -> {
                codeState.clearText()
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        step = SignInStep.TwoFactor,
                        challenge = outcome.challenge,
                        code = "",
                    )
                }
            }

            is SignInOutcome.Refused ->
                if (outcome.reason == SignInRefusal.TwoFactorSessionExpired) {
                    // Nothing left to post from card 3 — the session the code would have gone with is
                    // gone. Card 1 gets the user back, carrying the site's sentence so the trip does
                    // not read as the app losing their place for no reason.
                    returnToCredentials(refusal = outcome)
                } else {
                    _uiState.update { it.spend().copy(isSubmitting = false, refusal = outcome) }
                }
        }
    }

    /**
     * A failure of the request itself, which h1 answers with a snackbar and nothing else.
     *
     * Neither field is touched. "网络超时，未提交" on card 2 is a promise that pressing 重试 sends the
     * same thing again rather than making the user retype a password.
     */
    private fun applyFailure(throwable: Throwable) {
        _uiState.update {
            it.copy(
                isSubmitting = false,
                failure = throwable.toSiteError(),
                failureDetail = (throwable as? SiteException)?.detail,
            )
        }
    }

    private fun returnToCredentials(refusal: SignInOutcome.Refused?) {
        codeState.clearText()
        _uiState.update {
            it.spend().copy(
                isSubmitting = false,
                step = SignInStep.Credentials,
                code = "",
                challenge = null,
                refusal = refusal,
            )
        }
    }

    /**
     * Card 3's 当前验证码剩余 N 秒, for whoever is drawing it.
     *
     * A function off the clock rather than a field in [SignInUiState] kept fresh by a loop in here.
     * Two reasons, and the second is the one that matters. A TOTP window is a property of the wall
     * clock, so a countdown started when the step opened would already be wrong by however long the
     * credentials leg took — reading the clock each time is simply the correct answer. And a
     * `while (true) { delay(…) }` in `viewModelScope` is a state holder that never goes idle: under
     * `runTest` it makes `advanceUntilIdle()` spin through virtual time forever, so every test that
     * so much as reaches this step hangs instead of failing. The repeat belongs to the composition
     * that shows it, where leaving the screen ends it — see `SignInRoute`.
     *
     * Nothing here talks to the server. The period is TOTP's own, the same one the app already
     * assumes wherever it hands out an `otpauth://` enrolment.
     */
    fun secondsUntilNextCode(): Int {
        val elapsed = (clock.nowMillis() / 1_000L).mod(TOTP_PERIOD_SECONDS.toLong())
        return (TOTP_PERIOD_SECONDS - elapsed).toInt()
    }

    companion object {
        /** The TOTP step every authenticator app uses, and the one the site's enrolment URI implies. */
        const val TOTP_PERIOD_SECONDS = 30

        /** Fine enough that the number never appears to skip, cheap enough to leave running. */
        const val TICK_MILLIS = 250L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SignInViewModel(
                        signIn = container.signInRepository,
                        session = container.sessionRepository,
                        clock = container.clock,
                    )
                }
            }
    }
}

/** Which of h1's two pages is showing. */
enum class SignInStep { Credentials, TwoFactor }

/**
 * Where h1's 人机验证 block stands.
 *
 * [NotWired] is what ships today. The widget is Cloudflare's Turnstile and cannot be drawn by
 * Compose — it needs a web view to run — so the block says so and the submit button stays down
 * rather than sending a request the site is certain to refuse: its own form will not submit without
 * a token either.
 */
sealed interface VerificationState {
    /** No widget behind the block yet. Draws the placeholder; blocks submission. */
    data object NotWired : VerificationState

    /** The site did not ask for verification this time. */
    data object NotRequired : VerificationState

    /** The widget is up and waiting for the user. */
    data object Pending : VerificationState

    data class Passed(val token: String) : VerificationState

    /** The token aged out, or was spent on an attempt that failed. Card 2's 验证已过期 strip. */
    data object Expired : VerificationState
}

data class SignInUiState(
    val step: SignInStep = SignInStep.Credentials,
    val account: String = "",
    /** Not the password — see the note on [SignInViewModel]. */
    val passwordLength: Int = 0,
    val code: String = "",
    val verification: VerificationState = VerificationState.Pending,
    /**
     * Bumped every time a token is spent, and handed to the widget as its reset signal.
     *
     * A counter rather than a boolean because the widget has to be able to tell one refusal from the
     * next: two failed attempts in a row need two fresh tokens, and a flag that was already true the
     * second time would ask for none.
     */
    val verificationGeneration: Int = 0,
    val challenge: TwoFactorChallenge? = null,
    val isSubmitting: Boolean = false,
    /** The site's refusal, shown inline. Survives until the next submit. */
    val refusal: SignInOutcome.Refused? = null,
    /** A failure of the request itself, shown once in a snackbar. */
    val failure: SiteError? = null,
    val failureDetail: String? = null,
    val signedIn: Boolean = false,
) {
    /**
     * Card 2's other half: everything on the form is read-only while a request is in flight, so a
     * second tap cannot send a second attempt into whatever the site counts as too many.
     */
    val isFormEnabled: Boolean get() = !isSubmitting

    val isVerified: Boolean
        get() = verification is VerificationState.Passed || verification is VerificationState.NotRequired

    val canSubmitCredentials: Boolean
        get() = !isSubmitting && account.isNotBlank() && passwordLength > 0 && isVerified

    val canSubmitCode: Boolean
        get() = !isSubmitting && challenge != null && code.length == TWO_FACTOR_CODE_LENGTH

    /** True when the refusal belongs to the two fields on card 1 rather than to the code box. */
    val hasCredentialRefusal: Boolean get() = refusal != null && refusal.reason != SignInRefusal.TwoFactorCode

    val hasCodeRefusal: Boolean get() = refusal?.reason == SignInRefusal.TwoFactorCode

    /**
     * The state after an attempt the site refused, with the token treated as gone.
     *
     * A token is single-use, and the site's own form resets its widget on every refusal. Saying so is
     * what stops the user pressing 登录 again against a token that is already spent and reading the
     * second, identical refusal as their password being wrong twice — and the bumped
     * [verificationGeneration] is what actually asks the widget for the replacement.
     */
    internal fun spend(): SignInUiState =
        if (verification is VerificationState.Passed) {
            copy(verification = VerificationState.Expired, verificationGeneration = verificationGeneration + 1)
        } else {
            this
        }
}

/** Six, which is what every authenticator app produces and what card 3 draws. */
const val TWO_FACTOR_CODE_LENGTH = 6
