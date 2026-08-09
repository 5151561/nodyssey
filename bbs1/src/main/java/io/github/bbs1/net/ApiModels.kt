package io.github.bbs1.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the API plugin's responses look like, reduced to the fields this app reads.
 *
 * The plugin sends more (`api`, `limits`, permission flags, …) and `ignoreUnknownKeys` drops the
 * rest, so adding a feature later means adding a field here, not versioning anything. Every field
 * except an object's identity is defaulted: a missing field from an older plugin build degrades to
 * an empty value instead of failing the whole page.
 */
@Serializable
data class ApiMeta(
    val site: ApiSite = ApiSite(),
)

@Serializable
data class ApiSite(
    val name: String = "",
    val description: String = "",
)

@Serializable
data class ApiForumsPage(
    val forums: List<ApiForum> = emptyList(),
)

/**
 * @property canPost Whether the identity that made the request may open a thread here. The server
 *   answers per request, so the same forum reads differently before and after a login — which is
 *   exactly why the token rides along on the read calls too.
 */
@Serializable
data class ApiForum(
    val id: Long,
    val name: String = "",
    val description: String = "",
    @SerialName("can_post") val canPost: Boolean = false,
    @SerialName("can_reply") val canReply: Boolean = false,
)

@Serializable
data class ApiAvatar(
    val url: String = "",
)

@Serializable
data class ApiUserBrief(
    val id: Long = 0,
    val username: String = "",
    val avatar: ApiAvatar = ApiAvatar(),
)

/**
 * The signed-in user, as `login`, `register` and `me` all describe them.
 *
 * [canSpeak] defaults to true because a plugin build that does not send it is not a plugin that
 * silenced anyone — and the server refuses a muted user's write on its own regardless of what the
 * client believed.
 */
@Serializable
data class ApiUser(
    val id: Long = 0,
    val username: String = "",
    val avatar: ApiAvatar = ApiAvatar(),
    @SerialName("group_name") val groupName: String = "",
    @SerialName("can_speak") val canSpeak: Boolean = true,
)

/**
 * A freshly issued credential.
 *
 * @property tokenExpiresAt Unix seconds. The token is an HMAC the server can verify without storing
 *   it, so nothing invalidates it early except a password change — the client is what has to stop
 *   sending an expired one.
 */
@Serializable
data class ApiAuth(
    val token: String = "",
    @SerialName("token_expires_at") val tokenExpiresAt: Long = 0,
    val user: ApiUser = ApiUser(),
)

@Serializable
data class ApiTopicSummary(
    val id: Long,
    @SerialName("forum_id") val forumId: Long = 0,
    @SerialName("forum_name") val forumName: String = "",
    val title: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("last_reply_at") val lastReplyAt: Long = 0,
    @SerialName("is_pinned") val isPinned: Int = 0,
    val author: ApiUserBrief = ApiUserBrief(),
)

@Serializable
data class ApiTopicsPage(
    val topics: List<ApiTopicSummary> = emptyList(),
    val page: Int = 1,
    @SerialName("has_next_page") val hasNextPage: Boolean = false,
)

/** The topic list item plus what only the detail endpoint sends: the body and the view count. */
@Serializable
data class ApiTopicDetail(
    val id: Long,
    @SerialName("forum_id") val forumId: Long = 0,
    @SerialName("forum_name") val forumName: String = "",
    val title: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("is_pinned") val isPinned: Int = 0,
    val author: ApiUserBrief = ApiUserBrief(),
    /** Markdown source; this client renders it itself with the shared parser. */
    val body: String = "",
    @SerialName("view_count") val viewCount: Int = 0,
    /** 0 = oldest reply first, 1 = newest first. Per topic, chosen by its author. */
    @SerialName("reply_order") val replyOrder: Int = 0,
)

@Serializable
data class ApiReply(
    val id: Long,
    val body: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    val author: ApiUserBrief = ApiUserBrief(),
    /** Position in the thread, already ordered by the server's reply_order setting. */
    val floor: Int = 0,
)

@Serializable
data class ApiTopicPage(
    val topic: ApiTopicDetail,
    val replies: List<ApiReply> = emptyList(),
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 50,
    @SerialName("reply_count") val replyCount: Int = 0,
    /** Whether the identity behind this request may reply — group permission and mute together. */
    @SerialName("can_reply") val canReply: Boolean = false,
) {
    /** The server sends totals, not a cursor; whether more pages exist is arithmetic. */
    val hasNextPage: Boolean get() = page * pageSize < replyCount
}

/** What `topic_create` answers with. The list item it also returns is not what the app needs next. */
@Serializable
data class ApiTopicCreated(
    @SerialName("topic_id") val topicId: Long,
)

/** What `reply_create` answers with: the saved reply, minus a floor the server does not number here. */
@Serializable
data class ApiReplyCreated(
    @SerialName("reply_id") val replyId: Long,
    @SerialName("topic_id") val topicId: Long = 0,
    val reply: ApiReply,
)
