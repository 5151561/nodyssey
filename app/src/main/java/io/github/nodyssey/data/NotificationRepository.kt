package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The site's three notification groups, in the order `/notification` shows them.
 *
 * There is no fourth "system" group: what looks like one on the web is a pinned conversation named
 * 系统通知 inside [MESSAGES] — see `docs/design-requirements-remaining.md` §0.1. [MESSAGES] has no
 * notification rows of its own either; selecting it shows the conversation list (board 7e), which is
 * why its endpoint lives in [MessageRepository] rather than here.
 */
enum class NotificationCategory(val endpoint: String?) {
    MENTIONS("at-me"),
    REPLIES("reply-to-me"),
    MESSAGES(null),
}

data class NotificationCounts(
    val replies: Int = 0,
    val mentions: Int = 0,
    val messages: Int = 0,
) {
    val all: Int get() = replies + mentions + messages

    fun forCategory(category: NotificationCategory): Int =
        when (category) {
            NotificationCategory.MENTIONS -> mentions
            NotificationCategory.REPLIES -> replies
            NotificationCategory.MESSAGES -> messages
        }
}

/**
 * One notification row.
 *
 * The sentence is not stored: board 7d renders it as "{actor} 在帖子 {title} 中@了我" with the actor
 * and the thread styled differently, so the screen composes it from these parts and a string
 * resource. [createdAtMillis] is null when the endpoint pre-rendered the time, in which case
 * [createdAtText] is all we have.
 */
data class ForumNotification(
    val id: String,
    val category: NotificationCategory,
    val postId: Long?,
    val floor: String?,
    val actorUid: Long?,
    val actorName: String,
    val avatarUrl: String?,
    val excerpt: String?,
    val threadTitle: String?,
    val createdAtMillis: Long?,
    val createdAtText: String?,
    val isUnread: Boolean,
)

class NotificationRepository(
    private val jsonSource: JsonApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun unreadCounts(): NotificationCounts {
        val root = json.parseToJsonElement(jsonSource.getJson(NodeSeekJsonClient.PATH_UNREAD_COUNT))
        return NotificationCounts(
            replies = root.count("reply", "replyCount"),
            mentions = root.count("atMe", "at_me", "mention"),
            messages = root.count("message", "messages", "msg"),
        )
    }

    /**
     * Clears the group server-side.
     *
     * The screen used to only grey the rows out locally, so the badge came back on the next refresh.
     * The site's own 全部标记已读 posts to `markViewed?all=true` per group — see `notification.js`.
     */
    suspend fun markAllRead(category: NotificationCategory) {
        val endpoint = category.endpoint ?: return
        jsonSource.postJson(
            path = NodeSeekJsonClient.markAllViewedPath(endpoint),
            body = "",
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH,
        )
    }

    suspend fun notifications(category: NotificationCategory): List<ForumNotification> {
        val endpoint = category.endpoint ?: return emptyList()
        val root =
            json.parseToJsonElement(
                jsonSource.getJson(NodeSeekJsonClient.notificationListPath(endpoint)),
            )
        val rows =
            root.findObjectArray("replyList", "atList", "msgArray", "notifications", "list", "data")
        return rows.orEmpty().mapIndexed { index, item -> item.toNotification(category, index) }
    }
}

/** The counts endpoint sometimes nests its numbers one level down; zero is the honest default. */
private fun JsonElement.count(vararg names: String): Int {
    val objectValue = this as? JsonObject ?: return 0
    objectValue.int(*names)?.let { return it }
    objectValue.values.forEach { child ->
        (child as? JsonObject)?.int(*names)?.let { return it }
    }
    return 0
}

private fun JsonObject.toNotification(
    category: NotificationCategory,
    index: Int,
): ForumNotification {
    val postId = long("post_id", "postId", "discussion_id")
    val floorValue = text("floor_id", "floor", "floorId")
    val actorUid = long("member_id", "commenter_id", "sender_id", "uid", "user_id")
    val actor = text("commenter_name", "username", "sender_name", "name") ?: "NodeSeek 用户"
    val createdAt = text("created_at", "createdAt", "time")
    val viewed = bool("viewed") ?: false
    return ForumNotification(
        id = text("comment_id", "id", "message_id") ?: "${category.name}-$postId-$index",
        category = category,
        postId = postId,
        floor = floorValue?.let { if (it.startsWith('#')) it else "#$it" },
        actorUid = actorUid,
        actorName = actor,
        avatarUrl = actorUid?.let { NodeSeekSite.avatarUrl(it) },
        excerpt = text("content", "comment_content", "excerpt", "message")?.trim()?.ifBlank { null },
        threadTitle = text("post_title", "discussion_title", "title"),
        createdAtMillis = TimeFormat.parseTimestamp(createdAt),
        createdAtText = createdAt?.trim()?.ifBlank { null },
        isUnread = !viewed,
    )
}
