package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.TimeFormat
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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
    private val jsonSource: JsonSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun unreadCounts(): NotificationCounts {
        val root = json.parseToJsonElement(jsonSource.getJson(NodeSeekJsonClient.PATH_UNREAD_COUNT))
        return NotificationCounts(
            replies = root.int("reply", "replyCount"),
            mentions = root.int("atMe", "at_me", "mention"),
            messages = root.int("message", "messages", "msg"),
        )
    }

    suspend fun notifications(category: NotificationCategory): List<ForumNotification> {
        val endpoint = category.endpoint ?: return emptyList()
        val root =
            json.parseToJsonElement(
                jsonSource.getJson(NodeSeekJsonClient.notificationListPath(endpoint)),
            )
        val rows = root.findNotificationArray()
        return rows.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            item.toNotification(category, index)
        }
    }
}

private fun JsonElement.int(vararg names: String): Int {
    val objectValue = this as? JsonObject ?: return 0
    names.forEach { name ->
        objectValue[name]?.jsonPrimitive?.intOrNull?.let { return it }
    }
    objectValue.values.forEach { child ->
        val nested = child as? JsonObject ?: return@forEach
        names.forEach { name -> nested[name]?.jsonPrimitive?.intOrNull?.let { return it } }
    }
    return 0
}

private fun JsonElement.findNotificationArray(): JsonArray {
    if (this is JsonArray && any { it is JsonObject }) return this
    if (this !is JsonObject) return JsonArray(emptyList())
    val preferred = listOf("replyList", "atList", "msgArray", "notifications", "list", "data")
    preferred.forEach { key ->
        val child = this[key] ?: return@forEach
        val found = child.findNotificationArray()
        if (found.isNotEmpty()) return found
    }
    values.forEach { child ->
        val found = child.findNotificationArray()
        if (found.isNotEmpty()) return found
    }
    return JsonArray(emptyList())
}

private fun JsonObject.toNotification(
    category: NotificationCategory,
    index: Int,
): ForumNotification {
    fun text(vararg names: String): String? {
        names.forEach { name -> this[name]?.jsonPrimitive?.contentOrNull?.let { return it } }
        return null
    }

    fun long(vararg names: String): Long? {
        names.forEach { name -> this[name]?.jsonPrimitive?.longOrNull?.let { return it } }
        return null
    }

    val postId = long("post_id", "postId", "discussion_id")
    val floorValue = text("floor_id", "floor", "floorId")
    val actorUid = long("member_id", "commenter_id", "sender_id", "uid", "user_id")
    val actor = text("commenter_name", "username", "sender_name", "name") ?: "NodeSeek 用户"
    val createdAt = text("created_at", "createdAt", "time")
    val viewed =
        this["viewed"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull ?: 0) != 0 } ?: false
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
