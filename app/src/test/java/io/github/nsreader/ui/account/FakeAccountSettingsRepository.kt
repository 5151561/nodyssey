package io.github.nsreader.ui.account

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.AvatarUpload
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.data.account.RemoteAccountPreferences
import io.github.nsreader.data.account.TelegramBinding
import io.github.nsreader.data.account.TwoFactorState

/**
 * An [AccountSettingsRepository] whose reads are canned and whose writes are recorded.
 *
 * The ordering contracts are what these tests are for — avatar before text fields, no write before a
 * confirmation — and those live in the ViewModels, not in the requests.
 *
 * [failWith] makes every call throw, which is how the error paths are exercised.
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
    var changedPassword: Pair<String, String>? = null
    var uploadedAvatar: AvatarUpload? = null
    var unblocked = mutableListOf<Long>()
    var holidayThemeWrites = mutableListOf<Boolean>()
    var boardHiddenWrites = mutableListOf<Pair<String, Boolean>>()
    var enrolmentUri: String = "otpauth://totp/NodeSeek:tester?secret=ABC"
    var enrolmentPassword: String? = null

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

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        record("changePassword")
        changedPassword = currentPassword to newPassword
    }

    override suspend fun twoFactor(): TwoFactorState {
        record("twoFactor")
        return twoFactor
    }

    override suspend fun beginTwoFactorEnrolment(password: String): String {
        record("beginTwoFactorEnrolment")
        enrolmentPassword = password
        return enrolmentUri
    }

    override suspend fun contact(): AccountContact {
        record("contact")
        return contact
    }

    override suspend fun telegramBinding(): TelegramBinding {
        record("telegramBinding")
        return telegram
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
        /** Every call fails the way a dropped connection would — the shared unhappy path. */
        fun failing() =
            FakeAccountSettingsRepository(
                failWith = { NodeSeekException(NodeSeekError.Network) },
            )
    }
}
