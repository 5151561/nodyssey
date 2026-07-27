package io.github.nsreader.data.account

/** 个人信息 · `/setting#introduction`. Every field is Markdown except [bio], which is one plain line. */
data class AccountProfileFields(
    val bio: String = "",
    val signature: String = "",
    val readme: String = "",
)

/**
 * 联系方式 · `/setting#contact`.
 *
 * One address only. v1 invented a backup address; the 2026-07-27 recheck (additions.md §1.1) found
 * the site has no such field, so it is gone rather than kept "for later".
 */
data class AccountContact(
    val email: String = "",
    val emailVerified: Boolean = false,
)

/**
 * Telegram 提醒 · `/setting#contact`.
 *
 * The site's only off-site notification channel. Only the unbound state has been observed on a real
 * account (additions.md §1.1: "暂未绑定telegram…" + 「立即绑定」); the bound shape — a username and,
 * ideally, when it was bound — is the design's assumption from f3 and must be corrected against the
 * real payload when the endpoint is captured.
 *
 * [boundAtDisplay] is whatever date string the site reports, uninterpreted. Parsing it into a real
 * timestamp before the format has ever been seen would be invention twice over.
 */
data class TelegramBinding(
    val bound: Boolean = false,
    val username: String? = null,
    val boundAtDisplay: String? = null,
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

/**
 * The account-scoped half of 常用偏好/首页版块 — the rows the site itself marks Remote.
 *
 * The site splits its preference page into Local (this browser) and Remote (this account) storage,
 * and only the Remote rows belong here: 启用节日主题, and the three hideable home boards
 * (交易 / 生活 / 贴图 — the site opens no others). The Local rows never touch this repository; they
 * live in [io.github.nsreader.data.settings.SettingsRepository]'s DataStore.
 *
 * [hiddenBoards] holds slugs from
 * [io.github.nsreader.data.settings.OPTIONAL_HOME_BOARD_SLUGS], hidden meaning "off".
 */
data class RemoteAccountPreferences(
    val holidayTheme: Boolean = false,
    val hiddenBoards: Set<String> = emptySet(),
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
 * The read/write surface of NodeSeek's own `/setting` page.
 *
 * Split out from [io.github.nsreader.data.ProfileRepository] because the two answer different
 * questions: that one reads the account the *forum* shows (name, avatar, chicken legs) and every
 * screen in the app uses it, while this one is the settings page's and is touched by a handful of
 * screens only.
 *
 * The Remote preference rows (节日主题, the three home-board switches) are here because the site
 * stores them on the account, and d6 5/5 badges them Remote for exactly that reason. Their app-side
 * mirror in `SettingsRepository` exists so the home feed and theme can keep working offline; this
 * interface is the authority the mirror syncs against.
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

    /**
     * Step ① + ② of the site's change-email flow: password check, then a code to the new address.
     * The site sends the code only after the password is accepted, so the two travel together.
     */
    suspend fun sendEmailChangeCode(password: String, newEmail: String)

    /** Step ② 「确定」: the 6-digit code from the new mailbox commits the change. */
    suspend fun confirmEmailChange(password: String, newEmail: String, code: String)

    suspend fun telegramBinding(): TelegramBinding

    /**
     * What 「立即绑定」 does: returns the URL the app should open — assumed to be a `t.me` bot deep
     * link carrying a one-time token. **The mechanism is unverified** (f3 is explicit about this):
     * nobody has pressed the real button on a signed-in session, because pressing it may bind the
     * tester's own account. Capture the click on a device and correct this signature if the site
     * turns out to navigate, POST first, or embed the token differently.
     */
    suspend fun beginTelegramBinding(): String

    suspend fun unbindTelegram()

    suspend fun remotePreferences(): RemoteAccountPreferences

    suspend fun setHolidayTheme(enabled: Boolean)

    /** [slug] is one of the three the site allows (`OPTIONAL_HOME_BOARD_SLUGS`); hidden = 关. */
    suspend fun setHomeBoardHidden(slug: String, hidden: Boolean)

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

    /**
     * Probe: 修改邮箱 → type the password → 「发送验证码」. Expect one request carrying the password
     * (its rejection is what surfaces a wrong password) and one that fires the mail; they may be the
     * same request.
     */
    override suspend fun sendEmailChangeCode(password: String, newEmail: String): Unit =
        notVerified("sendEmailChangeCode")

    /** Probe: 「确定」 with the 6-digit code. Note how an expired or wrong code comes back. */
    override suspend fun confirmEmailChange(password: String, newEmail: String, code: String): Unit =
        notVerified("confirmEmailChange")

    /**
     * Probe: `/setting#contact` on load, on an account that has bound TG if at all possible — the
     * bound payload (username? chat id? bound-at?) decides [TelegramBinding]'s real shape.
     */
    override suspend fun telegramBinding(): TelegramBinding = notVerified("telegramBinding")

    /**
     * Probe: press 「立即绑定」 **on a throwaway account** and watch both the network log and where
     * the tab goes. Expected: either a direct `t.me/<bot>?start=<token>` navigation or an XHR that
     * returns such a link. Whatever it is, return the URL to open from here.
     */
    override suspend fun beginTelegramBinding(): String = notVerified("beginTelegramBinding")

    /** Probe: the bound card's 解绑 control, again on a throwaway account. */
    override suspend fun unbindTelegram(): Unit = notVerified("unbindTelegram")

    /**
     * Probe: `/setting#preference` and `/setting#homepage` on load. 启用节日主题 and the three
     * 首页版块 switches are the Remote rows; note whether they arrive with the page or via XHR.
     */
    override suspend fun remotePreferences(): RemoteAccountPreferences =
        notVerified("remotePreferences")

    /** Probe: flip 启用节日主题 and capture the write. */
    override suspend fun setHolidayTheme(enabled: Boolean): Unit = notVerified("setHolidayTheme")

    /** Probe: flip 交易/生活/贴图 under `/setting#homepage` and capture the write. */
    override suspend fun setHomeBoardHidden(slug: String, hidden: Boolean): Unit =
        notVerified("setHomeBoardHidden")

    /** Probe: `/setting#block` on load; check whether it pages once the list is long. */
    override suspend fun blockedUsers(): List<BlockedUser> = notVerified("blockedUsers")

    override suspend fun unblock(uid: Long): Unit = notVerified("unblock")

    private fun notVerified(operation: String): Nothing = throw EndpointNotVerifiedException(operation)
}
