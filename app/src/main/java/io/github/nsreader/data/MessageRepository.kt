package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.TimeFormat
import io.github.nsreader.core.net.JsonApi
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * One row of board 7e's conversation list.
 *
 * [isSystem] is the pinned 系统通知 conversation. It is an ordinary conversation on the site — there
 * is no separate system notification group — but it is the one whose snippet arrives as Markdown, so
 * the row renders it as inline rich text rather than plain characters.
 */
data class MessageConversation(
    val uid: Long,
    val userName: String,
    val avatarUrl: String?,
    val snippet: String,
    /** The last message is ours, which the design shows as a `你：` prefix. */
    val isSnippetMine: Boolean,
    val updatedAtMillis: Long?,
    val updatedAtText: String?,
    val unreadCount: Int,
    val isSystem: Boolean,
) {
    companion object {
        const val SYSTEM_NAME = "系统通知"
    }
}

/** One bubble in board 7f. Private messages are editable on NodeSeek, hence [isEdited]. */
data class DirectMessage(
    val id: String,
    val isMine: Boolean,
    val content: String,
    val sentAtMillis: Long?,
    val sentAtText: String?,
    val isEdited: Boolean,
)

data class MessageThread(
    val uid: Long,
    val userName: String,
    val avatarUrl: String?,
    val level: Int?,
    /** Oldest first, which is the order the conversation reads in. */
    val messages: List<DirectMessage>,
)

interface MessageRepository {
    suspend fun conversations(): List<MessageConversation>

    suspend fun thread(uid: Long): MessageThread

    /** Returns the accepted message so the optimistic bubble can be replaced by the real one. */
    suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ): DirectMessage?
}

/**
 * Direct messages over the site's notification API.
 *
 * **The endpoints are inferred** — see [NodeSeekJsonClient]'s companion. Parsing is therefore
 * deliberately shape-tolerant in the same way [NotificationRepository] is: field names are probed in
 * order rather than bound to a `@Serializable` schema, so a name that differs from the guess costs a
 * missing field instead of an unparsable screen.
 *
 * Direction is derived by comparing each message's sender against the person being talked to, which
 * avoids a second round trip just to learn our own uid.
 */
class NetworkMessageRepository(
    private val jsonApi: JsonApi,
) : MessageRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun conversations(): List<MessageConversation> {
        val root = json.parseToJsonElement(jsonApi.getJson(NodeSeekJsonClient.messageListPath(), REFERER))
        val rows = root.findObjectArray(PREFERRED_LIST_KEYS)
        val conversations =
            rows.mapNotNull { element ->
                (element as? JsonObject)?.toConversation()
            }
        // The site pins 系统通知 to the top of the list; the endpoint does not promise that order.
        return conversations.sortedWith(
            compareByDescending(MessageConversation::isSystem)
                .thenByDescending { it.updatedAtMillis ?: 0L },
        )
    }

    override suspend fun thread(uid: Long): MessageThread {
        val root =
            json.parseToJsonElement(
                jsonApi.getJson(
                    path = NodeSeekJsonClient.messageThreadPath(uid),
                    referer = NodeSeekSite.BASE_URL + NodeSeekSite.messageThreadWebPath(uid),
                ),
            )
        val rows = root.findObjectArray(PREFERRED_THREAD_KEYS)
        val messages =
            rows
                .mapIndexedNotNull { index, element ->
                    (element as? JsonObject)?.toMessage(otherUid = uid, index = index)
                }.sortedBy { it.sentAtMillis ?: 0L }
        val header = root.findMemberObject()
        return MessageThread(
            uid = uid,
            userName = header?.text("member_name", "username", "name") ?: "NodeSeek 用户",
            avatarUrl = NodeSeekSite.avatarUrl(uid),
            level = header?.get("rank")?.jsonPrimitive?.intOrNull,
            messages = messages,
        )
    }

    override suspend fun send(
        uid: Long,
        content: String,
        markdown: Boolean,
    ): DirectMessage? {
        val payload =
            buildJsonObject {
                put("receiver_id", uid)
                put("content", content)
                put("markdown", markdown)
            }
        val body =
            jsonApi.postJson(
                path = NodeSeekJsonClient.PATH_MESSAGE_SEND,
                body = payload.toString(),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.messageThreadWebPath(uid),
            )
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
        // `success: false` carries a human-readable reason — a blocked recipient, a rate limit — and
        // that reason is worth more to the user than the retry affordance alone.
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!success) {
            throw NodeSeekException(
                error = NodeSeekError.Unknown,
                detail = root.text("message", "error"),
            )
        }
        val accepted =
            listOf("data", "message", "detail").firstNotNullOfOrNull { key ->
                root[key] as? JsonObject
            }
        return accepted?.toMessage(otherUid = uid, index = 0)
    }

    private companion object {
        val REFERER = NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH
        val PREFERRED_LIST_KEYS = listOf("msgArray", "messageList", "conversations", "list", "data")
        val PREFERRED_THREAD_KEYS = listOf("msgArray", "messageList", "talkList", "list", "data")
    }
}

private fun JsonObject.toConversation(): MessageConversation? {
    val uid =
        long("member_id", "uid", "to", "target_id", "user_id", "sender_id")
            ?: return null
    val name = text("member_name", "username", "name") ?: "NodeSeek 用户"
    val lastSender = long("sender_id", "last_sender_id", "from")
    val updatedAt = text("created_at", "updated_at", "time", "last_time")
    val isSystem = name == MessageConversation.SYSTEM_NAME || boolean("is_system", "system") == true
    return MessageConversation(
        uid = uid,
        userName = name,
        avatarUrl = if (isSystem) null else NodeSeekSite.avatarUrl(uid),
        snippet = text("content", "last_content", "message", "excerpt")?.collapseWhitespace().orEmpty(),
        isSnippetMine = lastSender != null && lastSender != uid,
        updatedAtMillis = TimeFormat.parseTimestamp(updatedAt),
        updatedAtText = updatedAt?.trim()?.ifBlank { null },
        unreadCount = int("unread", "unread_count", "unreadCount", "nUnread"),
        isSystem = isSystem,
    )
}

private fun JsonObject.toMessage(
    otherUid: Long,
    index: Int,
): DirectMessage? {
    val content = text("content", "message", "text") ?: return null
    val sender = long("sender_id", "member_id", "from", "uid")
    val sentAt = text("created_at", "time", "sent_at")
    val updatedAt = text("updated_at", "edited_at")
    return DirectMessage(
        id = text("id", "message_id", "msg_id") ?: "$otherUid-$index-${content.hashCode()}",
        // Unknown sender reads as theirs: showing an incoming message on the right is a lie about who
        // said it, while showing our own on the left is only a cosmetic misalignment.
        isMine = sender != null && sender != otherUid,
        content = content,
        sentAtMillis = TimeFormat.parseTimestamp(sentAt),
        sentAtText = sentAt?.trim()?.ifBlank { null },
        isEdited =
        boolean("edited", "is_edited")
            ?: (updatedAt != null && updatedAt != sentAt),
    )
}

private fun JsonElement.findObjectArray(preferred: List<String>): JsonArray {
    if (this is JsonArray && any { it is JsonObject }) return this
    if (this !is JsonObject) return JsonArray(emptyList())
    preferred.forEach { key ->
        val found = this[key]?.findObjectArray(preferred) ?: return@forEach
        if (found.isNotEmpty()) return found
    }
    values.forEach { child ->
        val found = child.findObjectArray(preferred)
        if (found.isNotEmpty()) return found
    }
    return JsonArray(emptyList())
}

/** The counterparty's name and level, when the endpoint wraps the rows in a header object. */
private fun JsonElement.findMemberObject(): JsonObject? {
    val root = this as? JsonObject ?: return null
    listOf("member", "target", "user", "to").forEach { key ->
        (root[key] as? JsonObject)?.let { return it }
    }
    return null
}

private fun JsonObject.text(vararg names: String): String? {
    names.forEach { name -> this[name]?.jsonPrimitive?.contentOrNull?.let { return it } }
    return null
}

private fun JsonObject.long(vararg names: String): Long? {
    names.forEach { name -> this[name]?.jsonPrimitive?.longOrNull?.let { return it } }
    return null
}

private fun JsonObject.int(vararg names: String): Int {
    names.forEach { name -> this[name]?.jsonPrimitive?.intOrNull?.let { return it } }
    return 0
}

private fun JsonObject.boolean(vararg names: String): Boolean? {
    names.forEach { name ->
        this[name]?.jsonPrimitive?.let { primitive ->
            primitive.booleanOrNull?.let { return it }
            primitive.intOrNull?.let { return it != 0 }
        }
    }
    return null
}

private fun String.collapseWhitespace(): String = trim().replace(Regex("\\s+"), " ")
