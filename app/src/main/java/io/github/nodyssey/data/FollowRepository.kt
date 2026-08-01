package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A row of 我的关注 / 我的粉丝.
 *
 * No relationship state on the row on purpose. The payload feeds the site's own user card, which does
 * carry a `followed` flag, but we have only ever seen that card's *rendering* code — not a signed-in
 * response — so the row would be showing a field we guessed at. Tapping through to the space page gets
 * the flag from [ProfileRepository], which is a source we have read.
 */
data class FollowUser(
    val uid: Long,
    val name: String,
    val avatarUrl: String?,
)

/**
 * 我的关注 / 我的粉丝, and the two writes that change them.
 *
 * Neither list is paged: see [NodeSeekJsonClient.fansListPath].
 */
interface FollowRepository {
    /** Accounts the signed-in user follows. */
    suspend fun following(): List<FollowUser>

    /** Accounts that follow the signed-in user. */
    suspend fun followers(): List<FollowUser>

    /** Starts following [uid]. A refusal carries the site's sentence in [NodeSeekException.detail]. */
    suspend fun follow(uid: Long)

    suspend fun unfollow(uid: Long)
}

/**
 * The lists and the follow button, read out of the site's own `fans` bundle on 2026-08-02.
 *
 * `/fans` renders client-side, so this repository used to answer [NodeSeekError.NotWired] rather than
 * guess at a payload — the same place `/stardust/list` was in before it. The guessing stopped being
 * necessary once the bundle was read: `/api/fans/{follow|fans}` answers
 * `{"success":true,"memberList":[…]}` and the card that consumes a row addresses it as `member_id` /
 * `member_name`, building the avatar URL from the id rather than reading one out of the payload.
 *
 * [isSignedIn] is not defensive tidiness, it is the whole reason this class cannot just call and parse.
 * A signed-out `GET /api/fans/follow` answers **HTTP 200 with an empty `memberList`** — indistinguishable
 * from "you follow nobody". The site guards the same way, checking `__config__.user` before it fetches
 * and saying "用户未登录" instead. What the cookie cannot tell us is whether a *present* session is still
 * valid; a stale one still reads as an empty list, and no endpoint here distinguishes the two.
 */
class NetworkFollowRepository(
    private val api: JsonApi,
    private val dispatchers: AppDispatchers,
    private val isSignedIn: () -> Boolean,
) : FollowRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun following(): List<FollowUser> = list(followers = false)

    override suspend fun followers(): List<FollowUser> = list(followers = true)

    private suspend fun list(followers: Boolean): List<FollowUser> {
        if (!isSignedIn()) throw NodeSeekException(NodeSeekError.LoginRequired)
        val body =
            api.getJson(
                path = NodeSeekJsonClient.fansListPath(followers),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.fansPath(followers),
            )
        return withContext(dispatchers.default) {
            val root = body.asJsonObject()
            root.refusal()?.let { throw it }
            // An empty `memberList` is a real answer; no `memberList` at all means the shape changed,
            // and reporting that as "no follows" is exactly the lie this screen refused to tell while
            // it was unwired.
            val rows =
                root.findObjectArray("memberList")
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            rows.mapNotNull(JsonObject::toFollowUser)
        }
    }

    override suspend fun follow(uid: Long) = changeFollow(NodeSeekJsonClient.PATH_FANS_ADD, uid)

    override suspend fun unfollow(uid: Long) = changeFollow(NodeSeekJsonClient.PATH_FANS_DEL, uid)

    private suspend fun changeFollow(path: String, uid: Long) {
        val answer =
            api.postJson(
                path = path,
                body = """{"followed_member_id":$uid}""",
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.spacePath(uid),
            )
        withContext(dispatchers.default) {
            answer.asJsonObject().refusal()?.let { throw it }
        }
    }

    private fun String.asJsonObject(): JsonObject =
        runCatching { json.parseToJsonElement(this) as? JsonObject }
            .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
            ?: throw NodeSeekException(NodeSeekError.Unparsable)

    /**
     * `success:false` is a refusal, not a fault — the site answers it with 200 and a sentence.
     *
     * Returned rather than thrown so both the read and the write path raise it at their own call site.
     */
    private fun JsonObject.refusal(): NodeSeekException? =
        if (bool("success") == false) {
            NodeSeekException(NodeSeekError.Unknown, detail = text("message"))
        } else {
            null
        }
}

private fun JsonObject.toFollowUser(): FollowUser? {
    val uid = long("member_id", "uid", "user_id") ?: return null
    val name = text("member_name", "username", "name") ?: return null
    return FollowUser(
        uid = uid,
        name = name,
        // The row carries no avatar field: the site's card builds `/avatar/{member_id}.png` from the
        // id, and so do we. Accounts without an upload 404 there and draw their initial instead.
        avatarUrl =
        NodeSeekSite.absoluteUrl(text("avatar", "avatarUrl", "avatar_url"))
            ?: NodeSeekSite.avatarUrl(uid),
    )
}
