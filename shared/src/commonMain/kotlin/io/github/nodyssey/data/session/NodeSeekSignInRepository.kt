package io.github.nodyssey.data.session

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonWriteSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.bool
import io.github.nodyssey.data.text
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 登录 against NodeSeek's own endpoint, `POST /api/account/signIn`.
 *
 * Both legs are that one path. The first sends `{"username","password"}` with Cloudflare's Turnstile
 * token in `x-captcha-token` (and `x-captcha-source: turnstile` beside it); when the account carries
 * a second factor the answer is `success` *with* `need2FA` and an `otpSession`, and the second leg
 * sends `{"otp_code","otp_session"}` — no credentials, and no verification headers, because the
 * session already stands in for both.
 *
 * There is nothing to store on the way out. A successful call answers `Set-Cookie`, the transport's
 * jar is the same one the sign-in web view and every other request read, and the caller's job is to
 * tell [SessionRepository] to look. The `redirect` the site sends with it is where *its* page would
 * navigate; the app has its own idea of where the user was going.
 *
 * @param verificationSource the value of `x-captcha-source`. A parameter with the site's own default
 *   rather than a constant, because it names which vendor issued the token in [SignInCredentials] —
 *   if the widget behind the screen ever stops being Turnstile, this is the half that has to change
 *   with it.
 */
class NodeSeekSignInRepository(
    private val writes: JsonWriteSource,
    private val verificationSource: String = TURNSTILE,
) : SignInRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun signIn(credentials: SignInCredentials): SignInOutcome {
        val body =
            JsonObject(
                mapOf(
                    "username" to JsonPrimitive(credentials.account),
                    "password" to JsonPrimitive(credentials.password),
                ),
            )
        val root =
            post(
                body = body,
                extraHeaders =
                credentials.verificationToken
                    ?.let { mapOf(HEADER_CAPTCHA_TOKEN to it, HEADER_CAPTCHA_SOURCE to verificationSource) }
                    .orEmpty(),
            )

        if (root.bool("success") != true) {
            return SignInOutcome.Refused(SignInRefusal.Credentials, root.text("message"))
        }
        // `need2FA` rides on a *successful* answer: the credentials were right and the sign-in is
        // half done, which is why the second leg needs nothing but the session it hands back.
        if (root.bool("need2FA") == true) {
            val session = root.text("otpSession") ?: throw SiteException(SiteError.Unparsable)
            return SignInOutcome.TwoFactorRequired(
                TwoFactorChallenge(account = credentials.account, otpSession = session),
            )
        }
        return SignInOutcome.Signed
    }

    override suspend fun verifyTwoFactor(challenge: TwoFactorChallenge, code: String): SignInOutcome {
        val root =
            post(
                body =
                JsonObject(
                    mapOf(
                        "otp_code" to JsonPrimitive(code),
                        "otp_session" to JsonPrimitive(challenge.otpSession),
                    ),
                ),
                extraHeaders = emptyMap(),
            )

        if (root.bool("success") == true) return SignInOutcome.Signed

        val message = root.text("message")
        // The one machine-readable code this endpoint has. It means the half-finished sign-in aged
        // out, so there is nothing left for card 3 to post and the user starts at the password again
        // — a distinction worth carrying, unlike "wrong code" versus "locked", which the endpoint
        // only ever expresses as a sentence.
        val reason =
            if (message?.contains(OTP_SESSION_DEAD) == true) {
                SignInRefusal.TwoFactorSessionExpired
            } else {
                SignInRefusal.TwoFactorCode
            }
        return SignInOutcome.Refused(reason, message)
    }

    /**
     * One call to the endpoint, with the failures that are not refusals turned into [SiteException].
     *
     * The status is read rather than trusted: this endpoint puts its meaning in the body on every
     * status it uses, which is why it goes through `sendJson` — but a body that is not JSON at all is
     * not the site answering, and a caller that let that through would show Cloudflare's HTML in the
     * banner as if the forum had said it.
     */
    private suspend fun post(body: JsonObject, extraHeaders: Map<String, String>): JsonObject {
        val response =
            writes.sendJson(
                method = "POST",
                path = NodeSeekJsonClient.PATH_ACCOUNT_SIGN_IN,
                body = json.encodeToString(JsonObject.serializer(), body),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.SIGN_IN_PATH,
                extraHeaders = extraHeaders,
            )
        return runCatching { json.parseToJsonElement(response.body) as? JsonObject }
            .getOrNull()
            ?: throw SiteException(SiteError.Unparsable)
    }

    companion object {
        const val HEADER_CAPTCHA_TOKEN = "x-captcha-token"
        const val HEADER_CAPTCHA_SOURCE = "x-captcha-source"

        /** What the site's own form sends in [HEADER_CAPTCHA_SOURCE]. */
        const val TURNSTILE = "turnstile"

        /** The endpoint's word for "that `otpSession` is gone". */
        const val OTP_SESSION_DEAD = "OTP_EXPIRED_OR_NOT_EXIST"
    }
}
