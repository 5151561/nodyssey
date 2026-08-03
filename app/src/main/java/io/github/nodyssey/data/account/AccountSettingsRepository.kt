package io.github.nodyssey.data.account

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.SiteBootstrap
import io.github.nodyssey.core.net.HtmlSource
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.MultipartWriteSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.bool
import io.github.nodyssey.data.findObjectArray
import io.github.nodyssey.data.long
import io.github.nodyssey.data.obj
import io.github.nodyssey.data.settings.OPTIONAL_HOME_BOARD_SLUGS
import io.github.nodyssey.data.text
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
 *
 * [emailVerified] is derived, not reported: the site stores no verified flag anywhere, and it does
 * not need one — an address can only be set by typing back a code mailed to it, at registration and
 * again at every change. A present address is therefore verified by construction.
 */
data class AccountContact(
    val email: String = "",
    val emailVerified: Boolean = false,
)

/**
 * Telegram 提醒 · `/setting#contact`.
 *
 * The site's only off-site notification channel. Bound-ness comes from the page bootstrap's
 * `telegram_id`; the rest is whatever Telegram's login widget handed the site at bind time and the
 * site kept — of which its own settings page displays exactly three fields, the two names and the
 * photo. There is no bound-at date to show: nothing in the payload carries one.
 */
data class TelegramBinding(
    val bound: Boolean = false,
    /** First and last name as Telegram reported them; absent for a binding the detail call missed. */
    val displayName: String? = null,
    val avatarUrl: String? = null,
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
 * live in [io.github.nodyssey.data.settings.SettingsRepository]'s DataStore.
 *
 * [hiddenBoards] holds slugs from
 * [io.github.nodyssey.data.settings.OPTIONAL_HOME_BOARD_SLUGS], hidden meaning "off".
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
 * Split out from [io.github.nodyssey.data.ProfileRepository] because the two answer different
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

    /**
     * Replaces the avatar. There is no counterpart: the site's settings page offers 设置头像 and
     * nothing else, so an account cannot go back to having no avatar once it has one.
     */
    suspend fun uploadAvatar(upload: AvatarUpload)

    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun twoFactor(): TwoFactorState

    /**
     * Starts TOTP enrolment; returns the `otpauth://` URI the authenticator app scans.
     *
     * The password is the site's requirement, not a precaution of ours — enrolment is one endpoint
     * that takes `{action, password}`, and it refuses without it.
     */
    suspend fun beginTwoFactorEnrolment(password: String): String

    suspend fun contact(): AccountContact

    /**
     * Whether Telegram is bound, and who to.
     *
     * There is no counterpart that *binds*: the site does it through telegram.org's login widget, a
     * script that needs a real browser and hands its callback object straight to the server. The app
     * opens `/setting#contact` for that and calls this again on the way back.
     */
    suspend fun telegramBinding(): TelegramBinding

    suspend fun unbindTelegram()

    suspend fun remotePreferences(): RemoteAccountPreferences

    suspend fun setHolidayTheme(enabled: Boolean)

    /** [slug] is one of the three the site allows (`OPTIONAL_HOME_BOARD_SLUGS`); hidden = 关. */
    suspend fun setHomeBoardHidden(slug: String, hidden: Boolean)

    suspend fun blockedUsers(): List<BlockedUser>

    /**
     * Blocks by username, because that is the only handle the site's own form takes.
     *
     * There is no uid overload to add: `/api/block-list/add` reads `block_member_name` and nothing
     * else, so a screen holding a uid still has to send the name it displays.
     */
    suspend fun block(name: String)

    suspend fun unblock(uid: Long)
}

/**
 * The production implementation.
 *
 * Every request here was read off the site's own settings bundle
 * (`/assets/index-*.js`, the `/setting` chunk) rather than guessed. That mattered: `/api/account/…`
 * is plausible enough that a wrong guess would most likely return a cheerful `{"success":false}`
 * that reads as "saved" to a caller which only checks for exceptions, and these operations include
 * changing a password — where silently doing nothing costs the user their account.
 *
 * The full contract, including the two flows that cannot be native, is in `docs/private/api-notes.md`.
 */
class NetworkAccountSettingsRepository(
    private val jsonApi: JsonApi,
    private val multipartWriteSource: MultipartWriteSource,
    private val htmlSource: HtmlSource,
    private val profileRepository: ProfileRepository,
) : AccountSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun profileFields(): AccountProfileFields {
        val uid = profileRepository.profile().uid
        val root =
            getObject(
                path = NodeSeekJsonClient.accountSettingsInfoPath(uid),
                operation = "profileFields",
            )
        val detail = root.obj("detail", "data", "account", "result")
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
        return AccountProfileFields(
            bio = detail.text("bio", "introduction").orEmpty(),
            signature = detail.text("signature_markdown", "signature", "signatureMarkdown").orEmpty(),
            readme = detail.text("readme", "readMe", "read_me").orEmpty(),
        )
    }

    override suspend fun saveProfileFields(fields: AccountProfileFields) {
        postObject(
            path = NodeSeekJsonClient.PATH_ACCOUNT_INTRODUCTION,
            body =
            JsonObject(
                mapOf(
                    "bio" to JsonPrimitive(fields.bio),
                    "signature" to JsonPrimitive(fields.signature),
                    "readme" to JsonPrimitive(fields.readme),
                ),
            ),
            operation = "saveProfileFields",
        )
    }

    override suspend fun uploadAvatar(upload: AvatarUpload) {
        val extension =
            when (upload.mimeType.lowercase()) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                else -> "jpg"
            }
        val body =
            multipartWriteSource.postMultipart(
                path = NodeSeekJsonClient.PATH_AVATAR_UPLOAD,
                fields = mapOf("token" to "123456798", "name" to "avatar"),
                fileField = "img",
                fileName = "img.$extension",
                fileBytes = upload.bytes,
                fileMimeType = upload.mimeType,
                headers = mapOf("x-csrf-challenge" to "simple-token"),
                referer = SETTINGS_REFERER,
            )
        parseObject(body, "uploadAvatar")
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        postObject(
            path = NodeSeekJsonClient.PATH_ACCOUNT_CHANGE_PASSWORD,
            body =
            JsonObject(
                mapOf(
                    "oldPassword" to JsonPrimitive(currentPassword),
                    "password" to JsonPrimitive(newPassword),
                ),
            ),
            operation = "changePassword",
        )
    }

    override suspend fun twoFactor(): TwoFactorState {
        val root =
            getObject(
                path = NodeSeekJsonClient.PATH_ACCOUNT_OTP_STATUS,
                operation = "twoFactor",
            )
        val enabled = root.bool("enabled") ?: throw NodeSeekException(NodeSeekError.Unparsable)
        return TwoFactorState(enabled)
    }

    override suspend fun beginTwoFactorEnrolment(password: String): String {
        val root =
            postObject(
                path = NodeSeekJsonClient.PATH_ACCOUNT_OTP,
                body =
                JsonObject(
                    mapOf(
                        "action" to JsonPrimitive("create"),
                        "password" to JsonPrimitive(password),
                    ),
                ),
                operation = "beginTwoFactorEnrolment",
            )
        // The response also carries the bare `secret` the site prints as a backup code. It is not
        // read here: the app hands the URI to an authenticator and keeps no copy of either.
        return root.text("uri") ?: throw NodeSeekException(NodeSeekError.Unparsable)
    }

    override suspend fun contact(): AccountContact {
        val email = settingsUser().text("email").orEmpty()
        return AccountContact(email = email, emailVerified = email.isNotEmpty())
    }

    override suspend fun telegramBinding(): TelegramBinding {
        // The bootstrap is what the site itself checks before it will even ask for the detail.
        if (settingsUser().text("telegram_id") == null) return TelegramBinding(bound = false)

        val detail =
            getObject(
                path = NodeSeekJsonClient.PATH_ACCOUNT_TELEGRAM,
                operation = "telegramBinding",
            ).obj("telegramDetail")
        return TelegramBinding(
            bound = true,
            displayName =
            listOfNotNull(detail?.text("first_name"), detail?.text("last_name"))
                .joinToString(" ")
                .takeIf(String::isNotBlank),
            avatarUrl = detail?.text("photo_url"),
        )
    }

    override suspend fun unbindTelegram() {
        // The site sends this one with no body at all, so it goes out the same way.
        val response =
            jsonApi.postJson(
                path = NodeSeekJsonClient.PATH_ACCOUNT_UNBIND_TELEGRAM,
                referer = SETTINGS_REFERER,
            )
        if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
            throw NodeSeekException(NodeSeekError.LoginRequired)
        }
        // Body first: a refusal arrives as JSON carrying the sentence to show, whatever the status.
        parseObject(response.body, "unbindTelegram")
        if (!response.isSuccessful) throw NodeSeekException(NodeSeekError.Http(response.code))
    }

    override suspend fun remotePreferences(): RemoteAccountPreferences {
        val preference =
            postObject(
                path = NodeSeekJsonClient.PATH_PREFERENCE_LIST,
                body =
                JsonObject(
                    mapOf(
                        "keys" to
                            JsonArray(
                                listOf(JsonPrimitive(REMOTE_HOLIDAY_THEME_KEY)),
                            ),
                    ),
                ),
                operation = "remotePreferences",
            )
        val holidayTheme =
            preference.obj("data")?.bool(REMOTE_HOLIDAY_THEME_KEY)
                ?: throw NodeSeekException(NodeSeekError.Unparsable)

        val homepage =
            getObject(
                path = NodeSeekJsonClient.PATH_HOMEPAGE,
                operation = "remotePreferences",
            )
        val rows = homepage.findObjectArray("data")
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val hiddenBoards =
            rows.mapNotNullTo(mutableSetOf()) { row ->
                val slug = row.text("category") ?: return@mapNotNullTo null
                if (slug !in OPTIONAL_HOME_BOARD_SLUGS) return@mapNotNullTo null
                val shown = row.bool("showInIndex", "show_in_index") ?: return@mapNotNullTo null
                slug.takeUnless { shown }
            }
        return RemoteAccountPreferences(
            holidayTheme = holidayTheme,
            hiddenBoards = hiddenBoards,
        )
    }

    override suspend fun setHolidayTheme(enabled: Boolean) {
        postObject(
            path = NodeSeekJsonClient.PATH_PREFERENCE_SET,
            body = JsonObject(mapOf(REMOTE_HOLIDAY_THEME_KEY to JsonPrimitive(enabled))),
            operation = "setHolidayTheme",
        )
    }

    override suspend fun setHomeBoardHidden(slug: String, hidden: Boolean) {
        require(slug in OPTIONAL_HOME_BOARD_SLUGS) { "Unsupported home board: $slug" }
        postObject(
            path = NodeSeekJsonClient.PATH_HOMEPAGE,
            body =
            JsonObject(
                mapOf(
                    "data" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "category" to JsonPrimitive(slug),
                                        "showInIndex" to JsonPrimitive(!hidden),
                                    ),
                                ),
                            ),
                        ),
                ),
            ),
            operation = "setHomeBoardHidden",
        )
    }

    override suspend fun blockedUsers(): List<BlockedUser> {
        val root =
            getObject(
                path = NodeSeekJsonClient.PATH_BLOCK_LIST,
                operation = "blockedUsers",
            )
        val rows = root.findObjectArray("data", "list", "blocked", "blockList")
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val users =
            rows.mapNotNull { row ->
                /*
                 * `block_member_id` / `block_member_name` are what the site's own block panel reads —
                 * its table renders `o.block_member_name` linked to `/space/o.block_member_id`
                 * (bundle, 2026-08-03). The names are listed first and alone on purpose: this used to
                 * accept a spread of plausible-looking aliases, none of which was the real one, so
                 * every non-empty list came back Unparsable while the tests stayed green against
                 * invented fixtures.
                 */
                val uid = row.long("block_member_id") ?: return@mapNotNull null
                val name = row.text("block_member_name") ?: return@mapNotNull null
                BlockedUser(
                    uid = uid,
                    name = name,
                    // The endpoint returns no avatar; the site builds one from the uid the same way.
                    avatarUrl = NodeSeekSite.absoluteUrl("/avatar/$uid.png"),
                )
            }
        if (rows.isNotEmpty() && users.isEmpty()) throw NodeSeekException(NodeSeekError.Unparsable)
        return users
    }

    override suspend fun block(name: String) {
        postObject(
            path = NodeSeekJsonClient.PATH_BLOCK_ADD,
            body = JsonObject(mapOf("block_member_name" to JsonPrimitive(name))),
            operation = "block",
        )
    }

    override suspend fun unblock(uid: Long) {
        postObject(
            path = NodeSeekJsonClient.PATH_BLOCK_DELETE,
            body = JsonObject(mapOf("block_member_id" to JsonPrimitive(uid))),
            operation = "unblock",
        )
    }

    /**
     * The `user` half of the page bootstrap.
     *
     * Deliberately uncached and re-fetched per call: the two values read from it — the email address
     * and whether Telegram is bound — are exactly the two the user comes back to this screen to
     * re-check after finishing something on the site, and a cache would answer with the state they
     * just changed.
     */
    private suspend fun settingsUser(): JsonObject {
        val bootstrap = SiteBootstrap.decode(htmlSource.getHtml(SETTINGS_PATH))
        val root =
            try {
                json.parseToJsonElement(bootstrap) as? JsonObject
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            } ?: throw NodeSeekException(NodeSeekError.Unparsable)
        return root.obj("user") ?: throw NodeSeekException(NodeSeekError.LoginRequired)
    }

    private suspend fun getObject(path: String, operation: String): JsonObject {
        val body = jsonApi.getJson(path = path, referer = SETTINGS_REFERER)
        return parseObject(body, operation)
    }

    private suspend fun postObject(
        path: String,
        body: JsonObject,
        operation: String,
    ): JsonObject {
        val response =
            jsonApi.postJson(
                path = path,
                body = body.toString(),
                referer = SETTINGS_REFERER,
            )
        return parseObject(response, operation)
    }

    private fun parseObject(body: String, operation: String): JsonObject {
        val root =
            try {
                json.parseToJsonElement(body) as? JsonObject
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            } ?: throw NodeSeekException(NodeSeekError.Unparsable)
        if (root.bool("success") == false) {
            throw NodeSeekException(
                error = NodeSeekError.Unknown,
                detail = root.text("message", "error", "detail") ?: operation,
            )
        }
        return root
    }

    private companion object {
        const val SETTINGS_PATH = "/setting"
        const val SETTINGS_REFERER = NodeSeekSite.BASE_URL + SETTINGS_PATH
        const val REMOTE_HOLIDAY_THEME_KEY = "enable_festivous_style"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
