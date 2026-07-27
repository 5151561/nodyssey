package io.github.nsreader.ui.account

import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.AvatarUpload
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.data.account.EndpointNotVerifiedException
import io.github.nsreader.data.account.RemoteAccountPreferences
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.data.account.TwoFactorState

/**
 * An [AccountSettingsRepository] whose reads are canned and whose writes are recorded.
 *
 * Exists so the ViewModel logic can be tested *now*, while the real implementation still throws
 * [EndpointNotVerifiedException] for everything. That timing is the point: once the endpoints are
 * captured, the only file that changes is the network repository, and nobody will think to come back
 * and cover the ordering and revocation rules these tests pin down.
 *
 * [failWith] makes every call throw, which is how the pending-endpoint and error paths are exercised.
 */
internal class FakeAccountSettingsRepository(
    var fields: AccountProfileFields = AccountProfileFields(),
    var contact: AccountContact = AccountContact(),
    var telegram: TelegramBinding = TelegramBinding(),
    var twoFactor: TwoFactorState = TwoFactorState(),
    var remotePreferences: RemoteAccountPreferences = RemoteAccountPreferences(),
    var blocked: List<BlockedUser> = emptyList(),
    var failWith: (() -> Throwable)? = null,
) : AccountSettingsRepository {

    /** Every mutating call, in the order it happened — the ordering contracts are what matter. */
    val calls = mutableListOf<String>()

    var savedFields: AccountProfileFields? = null
    var sentEmailChangeCode: Pair<String, String>? = null
    var confirmedEmailChange: Triple<String, String, String>? = null
    var changedPassword: Pair<String, String>? = null
    var uploadedAvatar: AvatarUpload? = null
    var unblocked = mutableListOf<Long>()
    var holidayThemeWrites = mutableListOf<Boolean>()
    var boardHiddenWrites = mutableListOf<Pair<String, Boolean>>()
    var enrolmentUri: String = "otpauth://totp/NodeSeek:tester?secret=ABC"
    var bindUrl: String = "https://t.me/nodeseek_bot?start=test-token"

    private fun record(name: String) {
        calls += name
        failWith?.let { throw it() }
    }

    override suspend fun profileFields(): AccountProfileFields {
        record("profileFields")
        return fields
    }

    override suspend fun saveProfileFields(fields: AccountProfileFields) {
        record("saveProfileFields")
        savedFields = fields
    }

    override suspend fun uploadAvatar(upload: AvatarUpload) {
        record("uploadAvatar")
        uploadedAvatar = upload
    }

    override suspend fun removeAvatar() = record("removeAvatar")

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        record("changePassword")
        changedPassword = currentPassword to newPassword
    }

    override suspend fun twoFactor(): TwoFactorState {
        record("twoFactor")
        return twoFactor
    }

    override suspend fun beginTwoFactorEnrolment(): String {
        record("beginTwoFactorEnrolment")
        return enrolmentUri
    }

    override suspend fun contact(): AccountContact {
        record("contact")
        return contact
    }

    override suspend fun sendEmailChangeCode(password: String, newEmail: String) {
        record("sendEmailChangeCode")
        sentEmailChangeCode = password to newEmail
    }

    override suspend fun confirmEmailChange(password: String, newEmail: String, code: String) {
        record("confirmEmailChange")
        confirmedEmailChange = Triple(password, newEmail, code)
    }

    override suspend fun telegramBinding(): TelegramBinding {
        record("telegramBinding")
        return telegram
    }

    override suspend fun beginTelegramBinding(): String {
        record("beginTelegramBinding")
        return bindUrl
    }

    override suspend fun unbindTelegram() = record("unbindTelegram")

    override suspend fun remotePreferences(): RemoteAccountPreferences {
        record("remotePreferences")
        return remotePreferences
    }

    override suspend fun setHolidayTheme(enabled: Boolean) {
        record("setHolidayTheme")
        holidayThemeWrites += enabled
    }

    override suspend fun setHomeBoardHidden(slug: String, hidden: Boolean) {
        record("setHomeBoardHidden")
        boardHiddenWrites += slug to hidden
    }

    override suspend fun blockedUsers(): List<BlockedUser> {
        record("blockedUsers")
        return blocked
    }

    override suspend fun unblock(uid: Long) {
        record("unblock")
        unblocked += uid
    }

    companion object {
        /** The state the app ships in today: nothing about `/setting` has been wired up. */
        fun pendingEndpoints() =
            FakeAccountSettingsRepository(failWith = { EndpointNotVerifiedException("test") })
    }
}
