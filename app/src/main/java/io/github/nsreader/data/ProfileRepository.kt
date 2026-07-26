package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import io.github.nsreader.core.runCatchingExceptCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.util.Base64

data class UserProfile(
    val uid: Long,
    val name: String,
    val avatarUrl: String,
    val rank: Int? = null,
    val createdAt: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val streakDays: Int? = null,
)

interface ProfileRepository {
    suspend fun profile(): UserProfile
}

/**
 * Loads the signed-in account shown by NodeSeek itself.
 *
 * The home page hydration identifies the current user without inspecting authentication-cookie
 * values. Once the UID is known, the account endpoint is used as the freshest source. The bootstrap
 * record remains a fallback because Cloudflare can occasionally allow the document request while
 * rejecting the following XHR.
 */
class NetworkProfileRepository(
    private val htmlSource: HtmlSource,
    private val jsonSource: JsonSource,
) : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun profile(): UserProfile {
        val bootstrap = parseProfilePage(htmlSource.getHtml("/"))
        val uid = bootstrap.uid ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val account =
            runCatchingExceptCancellation {
                parseProfileJson(
                    jsonSource.getJson(
                        path = NodeSeekJsonClient.accountInfoPath(uid),
                        referer = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(uid),
                    ),
                )
            }.getOrNull()

        return bootstrap.merge(account).toProfile()
    }

    internal fun parseProfilePage(html: String): RawProfile {
        val encoded =
            Jsoup.parse(html)
                .getElementById(PROFILE_BOOTSTRAP_ELEMENT_ID)
                ?.data()
                ?.trim()
                .orEmpty()
        if (encoded.isEmpty()) throw NodeSeekException(NodeSeekError.Unparsable)

        val decoded =
            try {
                String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            }
        return parseProfileJson(decoded)
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
    }

    internal fun parseProfileJson(body: String): RawProfile? {
        val root =
            try {
                json.parseToJsonElement(body)
            } catch (exception: IllegalArgumentException) {
                throw NodeSeekException(NodeSeekError.Unparsable, exception)
            }
        return root.findProfileObject()?.toRawProfile()
    }

    private fun RawProfile.merge(newer: RawProfile?): RawProfile {
        if (newer == null) return this
        return RawProfile(
            uid = newer.uid ?: uid,
            name = newer.name ?: name,
            avatarUrl = newer.avatarUrl ?: avatarUrl,
            rank = newer.rank ?: rank,
            createdAt = newer.createdAt ?: createdAt,
            chickenCount = newer.chickenCount ?: chickenCount,
            starCount = newer.starCount ?: starCount,
            streakDays = newer.streakDays ?: streakDays,
        )
    }

    private fun RawProfile.toProfile(): UserProfile {
        val resolvedUid = uid ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val resolvedName = name?.takeIf(String::isNotBlank) ?: throw NodeSeekException(NodeSeekError.Unparsable)
        return UserProfile(
            uid = resolvedUid,
            name = resolvedName,
            avatarUrl =
            NodeSeekSite.absoluteUrl(avatarUrl)
                ?: "${NodeSeekSite.BASE_URL}/avatar/$resolvedUid.png",
            rank = rank,
            createdAt = createdAt,
            chickenCount = chickenCount,
            starCount = starCount,
            streakDays = streakDays,
        )
    }

    private companion object {
        const val PROFILE_BOOTSTRAP_ELEMENT_ID = "temp-script"
    }
}

internal data class RawProfile(
    val uid: Long? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val rank: Int? = null,
    val createdAt: String? = null,
    val chickenCount: Int? = null,
    val starCount: Int? = null,
    val streakDays: Int? = null,
)

private fun JsonElement.findProfileObject(): JsonObject? {
    if (this !is JsonObject) return null
    if (keys.any { it in PROFILE_ID_KEYS } && keys.any { it in PROFILE_NAME_KEYS }) return this

    PROFILE_CONTAINER_KEYS.forEach { key ->
        this[key]?.findProfileObject()?.let { return it }
    }
    values.forEach { child ->
        child.findProfileObject()?.let { return it }
    }
    return null
}

private fun JsonObject.toRawProfile(): RawProfile =
    RawProfile(
        uid = long(*PROFILE_ID_KEYS.toTypedArray()),
        name = text(*PROFILE_NAME_KEYS.toTypedArray()),
        avatarUrl = text("avatar", "avatarUrl", "avatar_url"),
        rank = int("rank", "level"),
        createdAt = text("created_at", "createdAt", "registered_at"),
        chickenCount = int("coin", "chicken", "chickenCount", "chicken_count"),
        starCount = int("stardust", "star", "stars", "starCount", "star_count"),
        streakDays = int("streak", "streakDays", "streak_days"),
    )

private fun JsonObject.text(vararg names: String): String? {
    names.forEach { name -> this[name]?.jsonPrimitive?.contentOrNull?.let { return it } }
    return null
}

private fun JsonObject.long(vararg names: String): Long? {
    names.forEach { name -> this[name]?.jsonPrimitive?.longOrNull?.let { return it } }
    return null
}

private fun JsonObject.int(vararg names: String): Int? {
    names.forEach { name -> this[name]?.jsonPrimitive?.intOrNull?.let { return it } }
    return null
}

private val PROFILE_ID_KEYS = setOf("member_id", "uid", "user_id")
private val PROFILE_NAME_KEYS = setOf("member_name", "username", "name")
private val PROFILE_CONTAINER_KEYS = listOf("user", "data", "account", "result")
