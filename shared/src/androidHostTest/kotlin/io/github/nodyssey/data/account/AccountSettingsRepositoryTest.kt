package io.github.nodyssey.data.account

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.JsonPostResponse
import io.github.nodyssey.core.net.MultipartWriteSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.plaza.core.net.HtmlSource
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class AccountSettingsRepositoryTest {
    @Test
    fun `reads editable profile fields from the verified account endpoint`() =
        runTest {
            val source =
                FakeSettingsJsonSource(
                    """
                    {"success":true,"detail":{
                      "member_id":42,"bio":"一句话","signature_markdown":"**签名**","readme":"# 说明"
                    }}
                    """.trimIndent(),
                )
            val repository = repository(source)

            val fields = repository.profileFields()

            assertEquals(AccountProfileFields("一句话", "**签名**", "# 说明"), fields)
            assertEquals(NodeSeekJsonClient.accountSettingsInfoPath(42), source.requestedPath)
            assertEquals("${NodeSeekSite.BASE_URL}/setting", source.requestedReferer)
        }

    @Test
    fun `treats nullable profile fields as empty edits`() =
        runTest {
            val repository =
                repository(
                    FakeSettingsJsonSource(
                        """{"success":true,"detail":{"bio":null,"signature_markdown":null,"readme":null}}""",
                    ),
                )

            assertEquals(AccountProfileFields(), repository.profileFields())
        }

    @Test
    fun `reads two factor status from otp status`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true,"enabled":true}""")

            val state = repository(source).twoFactor()

            assertTrue(state.enabled)
            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_OTP_STATUS, source.requestedPath)
        }

    /**
     * The field names are the site's, not plausible ones: its own block panel renders
     * `block_member_name` linked to `/space/block_member_id`. An earlier version of this test used
     * invented aliases and passed while the real list came back Unparsable on every device.
     */
    @Test
    fun `reads the blocked list the site actually returns`() =
        runTest {
            val source =
                FakeSettingsJsonSource(
                    """
                    {"success":true,"data":[
                      {"block_member_id":7,"block_member_name":"alpha"},
                      {"block_member_id":"8","block_member_name":"beta"}
                    ]}
                    """.trimIndent(),
                )

            val users = repository(source).blockedUsers()

            assertEquals(
                listOf(
                    BlockedUser(7, "alpha", "${NodeSeekSite.BASE_URL}/avatar/7.png"),
                    BlockedUser(8, "beta", "${NodeSeekSite.BASE_URL}/avatar/8.png"),
                ),
                users,
            )
            assertEquals(NodeSeekJsonClient.PATH_BLOCK_LIST, source.requestedPath)
        }

    @Test
    fun `accepts the verified empty block list shape`() =
        runTest {
            val users =
                repository(FakeSettingsJsonSource("""{"success":true,"data":[]}"""))
                    .blockedUsers()

            assertTrue(users.isEmpty())
        }

    @Test
    fun `reports an unrecognisable non-empty block list`() =
        runTest {
            val failure =
                runCatching {
                    repository(FakeSettingsJsonSource("""{"success":true,"data":[{"unexpected":1}]}"""))
                        .blockedUsers()
                }.exceptionOrNull()

            assertEquals(SiteError.Unparsable, (failure as? SiteException)?.error)
        }

    @Test
    fun `reads the email address off the page bootstrap`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            val contact = repository(source).contact()

            assertEquals("hikari.zhg@gmail.com", contact.email)
            // The site stores no verified flag; an address it holds got there by proving receipt.
            assertTrue(contact.emailVerified)
            assertEquals("/setting", source.requestedHtmlPath)
        }

    @Test
    fun `an unbound account never asks for telegram detail`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            val binding = repository(source).telegramBinding()

            assertEquals(TelegramBinding(bound = false), binding)
            assertNull(source.requestedPath)
        }

    @Test
    fun `a bound account reads the name and photo the site shows`() =
        runTest {
            val source =
                FakeSettingsJsonSource(
                    response =
                    """
                    {"success":true,"telegramDetail":{
                      "first_name":"Hikari","last_name":"Zhg","photo_url":"https://t.me/i/u/1.jpg"
                    }}
                    """.trimIndent(),
                    telegramId = 80231,
                )

            val binding = repository(source).telegramBinding()

            assertEquals(TelegramBinding(true, "Hikari Zhg", "https://t.me/i/u/1.jpg"), binding)
            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_TELEGRAM, source.requestedPath)
        }

    /** A binding whose detail call came back empty is still a binding. */
    @Test
    fun `a bound account without detail keeps the binding`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""", telegramId = 80231)

            assertEquals(TelegramBinding(bound = true), repository(source).telegramBinding())
        }

    @Test
    fun `unbinding telegram posts with no body`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).unbindTelegram()

            assertEquals(
                listOf(NodeSeekJsonClient.PATH_ACCOUNT_UNBIND_TELEGRAM),
                source.bodylessPosts,
            )
            assertTrue("no JSON body belongs on this one", source.posts.isEmpty())
        }

    @Test
    fun `two factor enrolment sends the password and returns the otpauth uri`() =
        runTest {
            val source =
                FakeSettingsJsonSource(
                    """{"success":true,"secret":"ABC","uri":"otpauth://totp/NodeSeek:tester?secret=ABC"}""",
                )

            val uri = repository(source).beginTwoFactorEnrolment("hunter2!")

            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_OTP, source.posts.single().path)
            assertJsonEquals(
                """{"action":"create","password":"hunter2!"}""",
                source.posts.single().body,
            )
            assertEquals("otpauth://totp/NodeSeek:tester?secret=ABC", uri)
        }

    @Test
    fun `a signed-out page bootstrap is reported as such`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""", settingHtml = "<html></html>")

            val failure = runCatching { repository(source).contact() }.exceptionOrNull()

            assertEquals(SiteError.LoginRequired, (failure as? SiteException)?.error)
        }

    @Test
    fun `saves all editable profile fields in one verified request`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source)
                .saveProfileFields(AccountProfileFields("简介", "**签名**", "# Readme"))

            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_INTRODUCTION, source.posts.single().path)
            assertJsonEquals(
                """{"bio":"简介","signature":"**签名**","readme":"# Readme"}""",
                source.posts.single().body,
            )
            assertEquals("${NodeSeekSite.BASE_URL}/setting", source.posts.single().referer)
        }

    @Test
    fun `uploads avatar with the settings page multipart contract`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")
            val upload = AvatarUpload(byteArrayOf(1, 2, 3), "image/jpeg")

            repository(source).uploadAvatar(upload)

            val request = source.multipartRequests.single()
            assertEquals(NodeSeekJsonClient.PATH_AVATAR_UPLOAD, request.path)
            assertEquals(mapOf("token" to "123456798", "name" to "avatar"), request.fields)
            assertEquals("img", request.fileField)
            assertEquals("img.jpg", request.fileName)
            assertTrue(request.fileBytes.contentEquals(upload.bytes))
            assertEquals("image/jpeg", request.fileMimeType)
            assertEquals(mapOf("x-csrf-challenge" to "simple-token"), request.headers)
            assertEquals("${NodeSeekSite.BASE_URL}/setting", request.referer)
        }

    @Test
    fun `changes password with the verified field names`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).changePassword("old-secret", "new-secret")

            assertEquals(NodeSeekJsonClient.PATH_ACCOUNT_CHANGE_PASSWORD, source.posts.single().path)
            assertJsonEquals(
                """{"oldPassword":"old-secret","password":"new-secret"}""",
                source.posts.single().body,
            )
        }

    @Test
    fun `reads remote holiday theme and hidden home boards`() =
        runTest {
            val source =
                FakeSettingsJsonSource(
                    response = """{"success":true}""",
                    getResponses =
                    mapOf(
                        NodeSeekJsonClient.PATH_HOMEPAGE to
                            """
                                {"success":true,"data":[
                                  {"category":"trade","showInIndex":true},
                                  {"category":"life","showInIndex":false},
                                  {"category":"photo-share","showInIndex":false},
                                  {"category":"daily","showInIndex":false}
                                ]}
                            """.trimIndent(),
                    ),
                    postResponses =
                    mapOf(
                        NodeSeekJsonClient.PATH_PREFERENCE_LIST to
                            """{"success":true,"data":{"enable_festivous_style":true}}""",
                    ),
                )

            val preferences = repository(source).remotePreferences()

            assertEquals(
                RemoteAccountPreferences(
                    holidayTheme = true,
                    hiddenBoards = setOf("life", "photo-share"),
                ),
                preferences,
            )
            assertJsonEquals(
                """{"keys":["enable_festivous_style"]}""",
                source.posts.single().body,
            )
            assertEquals(NodeSeekJsonClient.PATH_HOMEPAGE, source.requestedPath)
        }

    @Test
    fun `writes remote holiday theme`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).setHolidayTheme(false)

            assertEquals(NodeSeekJsonClient.PATH_PREFERENCE_SET, source.posts.single().path)
            assertJsonEquals(
                """{"enable_festivous_style":false}""",
                source.posts.single().body,
            )
        }

    @Test
    fun `writes inverse hidden state for a home board`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).setHomeBoardHidden("trade", true)

            assertEquals(NodeSeekJsonClient.PATH_HOMEPAGE, source.posts.single().path)
            assertJsonEquals(
                """{"data":[{"category":"trade","showInIndex":false}]}""",
                source.posts.single().body,
            )
        }

    @Test
    fun `rejects a home board the site does not expose`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            val failure =
                runCatching { repository(source).setHomeBoardHidden("daily", true) }
                    .exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertTrue(source.posts.isEmpty())
        }

    /** Blocking takes a name and only a name: the site's own form has no uid to send. */
    @Test
    fun `blocks by member name`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).block("alpha")

            assertEquals(NodeSeekJsonClient.PATH_BLOCK_ADD, source.posts.single().path)
            assertJsonEquals("""{"block_member_name":"alpha"}""", source.posts.single().body)
        }

    @Test
    fun `carries the site's refusal of an unknown name`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":false,"message":"用户不存在"}""")

            val failure = runCatching { repository(source).block("nobody") }.exceptionOrNull()

            assertEquals("用户不存在", (failure as? SiteException)?.detail)
        }

    @Test
    fun `unblocks by member id`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":true}""")

            repository(source).unblock(91)

            assertEquals(NodeSeekJsonClient.PATH_BLOCK_DELETE, source.posts.single().path)
            assertJsonEquals("""{"block_member_id":91}""", source.posts.single().body)
        }

    @Test
    fun `surfaces a rejected verified write`() =
        runTest {
            val source = FakeSettingsJsonSource("""{"success":false,"message":"拒绝修改"}""")

            val failure =
                runCatching { repository(source).setHolidayTheme(true) }
                    .exceptionOrNull()

            assertEquals(SiteError.Unknown, (failure as? SiteException)?.error)
            assertEquals("拒绝修改", (failure as? SiteException)?.detail)
        }

    private fun repository(source: FakeSettingsJsonSource) =
        NetworkAccountSettingsRepository(
            jsonApi = source,
            multipartWriteSource = source,
            htmlSource = source,
            profileRepository = FakeProfileRepository,
        )

    private fun assertJsonEquals(expected: String, actual: String) {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(actual))
    }
}

private class FakeSettingsJsonSource(
    private val response: String,
    private val getResponses: Map<String, String> = emptyMap(),
    private val postResponses: Map<String, String> = emptyMap(),
    private val multipartResponse: String = response,
    telegramId: Long? = null,
    private val settingHtml: String = bootstrapPage(telegramId),
) : JsonApi,
    MultipartWriteSource,
    HtmlSource {
    var requestedPath: String? = null
        private set
    var requestedReferer: String? = null
        private set
    var requestedHtmlPath: String? = null
        private set
    val posts = mutableListOf<CapturedJsonPost>()
    val bodylessPosts = mutableListOf<String>()
    val multipartRequests = mutableListOf<CapturedMultipartPost>()

    override suspend fun getHtml(path: String): String {
        requestedHtmlPath = path
        return settingHtml
    }

    override suspend fun resolveRedirect(path: String): String? = null

    override suspend fun getJson(path: String, referer: String): String {
        requestedPath = path
        requestedReferer = referer
        return getResponses[path] ?: response
    }

    override suspend fun postJson(path: String, body: String, referer: String): String {
        posts += CapturedJsonPost(path, body, referer)
        return postResponses[path] ?: response
    }

    override suspend fun postJson(path: String, referer: String): JsonPostResponse {
        bodylessPosts += path
        return JsonPostResponse(code = 200, body = postResponses[path] ?: response)
    }

    override suspend fun postMultipart(
        path: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMimeType: String,
        headers: Map<String, String>,
        referer: String,
    ): String {
        multipartRequests +=
            CapturedMultipartPost(
                path = path,
                fields = fields,
                fileField = fileField,
                fileName = fileName,
                fileBytes = fileBytes,
                fileMimeType = fileMimeType,
                headers = headers,
                referer = referer,
            )
        return multipartResponse
    }
}

/**
 * A page carrying the site's own `__config__` record — base64 JSON in `<script id="temp-script">`,
 * exactly as NodeSeek server-renders it.
 */
private fun bootstrapPage(telegramId: Long?): String {
    val telegram = telegramId?.toString() ?: "null"
    val config =
        """
        {"pageType":"setting","user":{"member_id":42,"member_name":"tester",
        "email":"hikari.zhg@gmail.com","phone":null,"telegram_id":$telegram,"rank":3}}
        """.trimIndent()
    val encoded = Base64.getEncoder().encodeToString(config.toByteArray(StandardCharsets.UTF_8))
    return "<html><body><script id=\"temp-script\">$encoded</script></body></html>"
}

private data class CapturedJsonPost(
    val path: String,
    val body: String,
    val referer: String,
)

private data class CapturedMultipartPost(
    val path: String,
    val fields: Map<String, String>,
    val fileField: String,
    val fileName: String,
    val fileBytes: ByteArray,
    val fileMimeType: String,
    val headers: Map<String, String>,
    val referer: String,
)

private object FakeProfileRepository : ProfileRepository {
    private val profile =
        UserProfile(
            uid = 42,
            name = "tester",
            avatarUrl = "${NodeSeekSite.BASE_URL}/avatar/42.png",
        )

    override suspend fun profile(refresh: Boolean): UserProfile = profile

    override suspend fun profile(uid: Long): UserProfile = profile.copy(uid = uid)
}
