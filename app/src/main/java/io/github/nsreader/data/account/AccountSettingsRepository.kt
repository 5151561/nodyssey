package io.github.nsreader.data.account

/** 个人信息 · `/setting#introduction`. Every field is Markdown except [bio], which is one plain line. */
data class AccountProfileFields(
    val bio: String = "",
    val signature: String = "",
    val readme: String = "",
)

/** 联系方式 · `/setting#contact`. The backup address is optional and starts out unverified. */
data class AccountContact(
    val email: String = "",
    val emailVerified: Boolean = false,
    val backupEmail: String = "",
    val backupEmailVerified: Boolean = false,
)

/** 双因素验证 · `/setting#2fa`. TOTP is the only second factor the site offers. */
data class TwoFactorState(
    val enabled: Boolean = false,
)

/** 屏蔽用户 · `/setting#block`. */
data class BlockedUser(
    val uid: Long,
    val name: String,
    val avatarUrl: String? = null,
)

/** An avatar chosen on the device, already downscaled by the picker. */
data class AvatarUpload(
    val bytes: ByteArray,
    val mimeType: String,
) {
    // Data classes compare arrays by identity, which would make two reads of the same image unequal
    // and defeat any `distinctUntilChanged` this ever passes through.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is AvatarUpload && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

/**
 * The seven groups of NodeSeek's own `/setting` page.
 *
 * Split out from [io.github.nsreader.data.ProfileRepository] because the two answer different
 * questions: that one reads the account the *forum* shows (name, avatar, chicken legs) and every
 * screen in the app uses it, while this one is the read/write surface for the settings page and is
 * touched by five screens only.
 *
 * 常用偏好 (`#preference`) and 首页版块 (`#homepage`) are deliberately absent. Both are presentation
 * choices the app already owns locally — see [io.github.nsreader.data.settings.SettingsRepository] —
 * and round-tripping them through the site would make the app's own settings screen lie whenever the
 * network was down.
 */
interface AccountSettingsRepository {
    suspend fun profileFields(): AccountProfileFields

    suspend fun saveProfileFields(fields: AccountProfileFields)

    suspend fun uploadAvatar(upload: AvatarUpload)

    suspend fun removeAvatar()

    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun twoFactor(): TwoFactorState

    /** Starts TOTP enrolment; returns the `otpauth://` URI the authenticator app scans. */
    suspend fun beginTwoFactorEnrolment(): String

    suspend fun contact(): AccountContact

    suspend fun saveContact(email: String, backupEmail: String)

    suspend fun resendVerification(email: String)

    suspend fun blockedUsers(): List<BlockedUser>

    suspend fun unblock(uid: Long)
}

/**
 * Thrown for every operation whose NodeSeek endpoint has not been observed yet.
 *
 * NodeSeek has no public API and `/setting` is a client-rendered page whose writes go out as XHRs, so
 * the request shapes cannot be read off the HTML the way the list and detail parsers were written.
 * They have to be captured from a signed-in session on a device — this sandbox gets a Cloudflare 403
 * for anything authenticated.
 *
 * Guessing was the alternative and was rejected: `/api/account/…` is plausible enough that a wrong
 * guess would most likely return a cheerful `{"success":false}` that reads as "saved" to a caller
 * that only checks for exceptions, and the operations here include changing a password and an email
 * address — the two places where silently doing nothing, or silently doing the wrong thing, costs the
 * user their account.
 *
 * Filling this in is one file's work. The probe checklist is in the KDoc of each
 * [NetworkAccountSettingsRepository] member.
 */
class EndpointNotVerifiedException(
    val operation: String,
) : Exception("NodeSeek endpoint for `$operation` has not been verified yet")

/**
 * The real implementation, pending endpoint capture.
 *
 * Every member documents what to look for in the network log of a signed-in `/setting` session, so
 * the person holding the device does not have to re-derive the list. Replace a `notVerified` call
 * with the request as soon as its shape is known; the screens need no change, and the pending banner
 * they show is driven by this exception, so it disappears on its own.
 */
class NetworkAccountSettingsRepository : AccountSettingsRepository {

    /** Probe: open `/setting#introduction` — the page must GET bio/signature/readme from somewhere. */
    override suspend fun profileFields(): AccountProfileFields = notVerified("profileFields")

    /** Probe: edit Bio and press save; expect one POST carrying all three fields. */
    override suspend fun saveProfileFields(fields: AccountProfileFields): Unit =
        notVerified("saveProfileFields")

    /** Probe: upload an avatar; expect `multipart/form-data`. Note the field name and any size cap. */
    override suspend fun uploadAvatar(upload: AvatarUpload): Unit = notVerified("uploadAvatar")

    override suspend fun removeAvatar(): Unit = notVerified("removeAvatar")

    /** Probe: `/setting#security`. Watch for a 2FA code field appearing once TOTP is bound. */
    override suspend fun changePassword(currentPassword: String, newPassword: String): Unit =
        notVerified("changePassword")

    /** Probe: `/setting#2fa` on load. */
    override suspend fun twoFactor(): TwoFactorState = notVerified("twoFactor")

    /** Probe: press 绑定验证器; the response should carry the secret or a full `otpauth://` URI. */
    override suspend fun beginTwoFactorEnrolment(): String = notVerified("beginTwoFactorEnrolment")

    /** Probe: `/setting#contact` on load; note how the verified flag is spelled. */
    override suspend fun contact(): AccountContact = notVerified("contact")

    override suspend fun saveContact(email: String, backupEmail: String): Unit =
        notVerified("saveContact")

    override suspend fun resendVerification(email: String): Unit = notVerified("resendVerification")

    /** Probe: `/setting#block` on load; check whether it pages once the list is long. */
    override suspend fun blockedUsers(): List<BlockedUser> = notVerified("blockedUsers")

    override suspend fun unblock(uid: Long): Unit = notVerified("unblock")

    private fun notVerified(operation: String): Nothing = throw EndpointNotVerifiedException(operation)
}
