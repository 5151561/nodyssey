package io.github.nsreader.data

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

enum class NotificationCategory(val endpoint: String?) {
    REPLIES("reply-to-me"),
    MENTIONS("at-me"),
    MESSAGES("message"),
    SYSTEM(null),
}

data class NotificationCounts(
    val replies: Int = 0,
    val mentions: Int = 0,
    val messages: Int = 0,
    val all: Int = 0,
) {
    fun forCategory(category: NotificationCategory): Int =
        when (category) {
            NotificationCategory.REPLIES -> replies
            NotificationCategory.MENTIONS -> mentions
            NotificationCategory.MESSAGES -> messages
            NotificationCategory.SYSTEM -> (all - replies - mentions - messages).coerceAtLeast(0)
        }
}

data class ForumNotification(
    val id: String,
    val postId: Long?,
    val floor: String?,
    val actorName: String,
    val action: String,
    val excerpt: String?,
    val threadTitle: String?,
    val createdAt: String?,
    val isUnread: Boolean,
)

class NotificationRepository(
    private val jsonSource: JsonSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun unreadCounts(): NotificationCounts {
        val root = json.parseToJsonElement(jsonSource.getJson(NodeSeekJsonClient.PATH_UNREAD_COUNT))
        val replies = root.int("reply", "replyCount")
        val mentions = root.int("atMe", "at_me", "mention")
        val messages = root.int("message", "messages", "msg")
        val declaredAll = root.int("all", "total")
        return NotificationCounts(
            replies = replies,
            mentions = mentions,
            messages = messages,
            all = if (declaredAll > 0) declaredAll else replies + mentions + messages,
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
    val actor = text("commenter_name", "username", "sender_name", "name") ?: "NodeSeek 用户"
    val viewed =
        this["viewed"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.intOrNull ?: 0) != 0 } ?: false
    return ForumNotification(
        id = text("comment_id", "id", "message_id") ?: "${category.name}-$postId-$index",
        postId = postId,
        floor = floorValue?.let { if (it.startsWith('#')) it else "#$it" },
        actorName = actor,
        action =
        when (category) {
            NotificationCategory.REPLIES -> "回复了你的帖子"
            NotificationCategory.MENTIONS -> "提到了你"
            NotificationCategory.MESSAGES -> "给你发来私信"
            NotificationCategory.SYSTEM -> "发来系统通知"
        },
        excerpt = text("content", "comment_content", "excerpt", "message"),
        threadTitle = text("post_title", "discussion_title", "title"),
        createdAt = text("created_at", "createdAt", "time"),
        isUnread = !viewed,
    )
}
