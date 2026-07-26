package io.github.nsreader.ui.account

import io.github.nsreader.data.account.AccountContact
import io.github.nsreader.data.account.AccountProfileFields
import io.github.nsreader.data.account.AccountSettingsRepository
import io.github.nsreader.data.account.AvatarUpload
import io.github.nsreader.data.account.BlockedUser
import io.github.nsreader.data.account.EndpointNotVerifiedException
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
    var twoFactor: TwoFactorState = TwoFactorState(),
    var blocked: List<BlockedUser> = emptyList(),
    var failWith: (() -> Throwable)? = null,
) : AccountSettingsRepository {

    /** Every mutating call, in the order it happened — the ordering contracts are what matter. */
    val calls = mutableListOf<String>()

    var savedFields: AccountProfileFields? = null
    var savedContact: Pair<String, String>? = null
    var changedPassword: Pair<String, String>? = null
    var uploadedAvatar: AvatarUpload? = null
    var unblocked = mutableListOf<Long>()
    var enrolmentUri: String = "otpauth://totp/NodeSeek:tester?secret=ABC"

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

    override suspend fun saveContact(email: String, backupEmail: String) {
        record("saveContact")
        savedContact = email to backupEmail
    }

    override suspend fun resendVerification(email: String) = record("resendVerification")

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
