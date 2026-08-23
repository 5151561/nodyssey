package io.github.nodyssey.data.session

import io.github.plaza.core.net.SiteException

/**
 * 登录 (h1) — signing in with a username and a password, rather than by driving the site's own page.
 *
 * NodeSeek answers both legs on one endpoint, `POST /api/account/signIn`. The first carries the
 * credentials and the human-verification token; if the account has a second factor the answer comes
 * back `need2FA` with an `otpSession`, and the second leg posts that session plus the six digits —
 * without the credentials and without the verification token. [NodeSeekSignInRepository] holds the
 * wire detail; this interface is what the screen is written against.
 *
 * Refusals come back as [SignInOutcome.Refused] rather than thrown. A wrong password is not a
 * failure of the request: the site read it, understood it and said no, and h1 answers that inline —
 * the field turns red and a banner carries the site's own sentence — where a thrown [SiteException]
 * would land in the snackbar the *transport* failures use. That distinction is the whole of card 2.
 */
interface SignInRepository {
    /**
     * @throws SiteException when the request could not be made or the answer could not be read.
     */
    suspend fun signIn(credentials: SignInCredentials): SignInOutcome

    /**
     * The second leg, for accounts that carry a second factor.
     *
     * Its own call rather than a second argument to [signIn] because the site decides between the
     * two: the form cannot know an account has 2FA switched on until the first call comes back
     * [SignInOutcome.TwoFactorRequired].
     */
    suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome
}

/**
 * @param account what the user typed in 用户名 / 邮箱, sent as the endpoint's `username`. The site's
 *   own form labels one field for both, so the app does too and lets the server decide.
 * @param verificationToken the Cloudflare Turnstile token, sent as `x-captcha-token`. Nullable
 *   because the screen can be in a state that has none — see `VerificationState` on the screen
 *   side — not because the endpoint tolerates its absence: the site's own form refuses to submit
 *   without one.
 */
data class SignInCredentials(
    val account: String,
    val password: String,
    val verificationToken: String?,
)

/**
 * What the first leg handed back to identify the second.
 *
 * [otpSession] is the endpoint's own `otpSession` field, and it is the *whole* of what the second
 * leg is authenticated by — the credentials are not sent again. [account] never reaches the wire;
 * card 3 names the account whose code it is asking for.
 */
data class TwoFactorChallenge(
    val account: String,
    val otpSession: String,
)

/** How far a sign-in attempt got. */
sealed interface SignInOutcome {
    /**
     * Done — the session cookie is in the shared jar.
     *
     * Carries nothing because there is nothing to carry: cookies *are* the session here, and the
     * caller's next move is [SessionRepository.sync], not reading a token out of this. The `redirect`
     * the site sends alongside is a web-page concern and is dropped.
     */
    data object Signed : SignInOutcome

    /** The account has a second factor; ask for it and call [SignInRepository.verifyTwoFactor]. */
    data class TwoFactorRequired(val challenge: TwoFactorChallenge) : SignInOutcome

    /**
     * The site understood the attempt and declined it.
     *
     * [detail] is the endpoint's `message`, and it is the sentence the screen shows. The app writes
     * no wording of its own for a refusal: it cannot know the lockout threshold, so the placeholder
     * on the board ("连续 5 次失败后需要等待 10 分钟再试") is deliberately in no string resource.
     */
    data class Refused(val reason: SignInRefusal, val detail: String? = null) : SignInOutcome
}

/**
 * Which control on h1 a refusal points at.
 *
 * Three cases and not one more, because three is what the wire distinguishes: the endpoint answers
 * `{"success":false,"message":…}` and names exactly one machine-readable code,
 * [NodeSeekSignInRepository.OTP_SESSION_DEAD]. Anything finer — "wrong password" apart from "locked
 * out" — would be this app inventing a distinction out of a sentence meant for a human to read.
 */
enum class SignInRefusal {
    /** The credentials leg said no. Marks the password field; keeps what was typed. */
    Credentials,

    /** The six digits were not accepted. Card 3 stays open. */
    TwoFactorCode,

    /**
     * The half-finished sign-in expired before the code arrived.
     *
     * Its own case because it is the one refusal that cannot be answered where it happened: the
     * `otpSession` is gone, so card 3 has nothing left to post and the user has to start at the
     * password again.
     */
    TwoFactorSessionExpired,
}
