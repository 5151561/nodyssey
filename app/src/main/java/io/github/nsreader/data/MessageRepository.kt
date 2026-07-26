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
        val rows = listRawMessages()
        val ownUid = inferOwnUid(rows)
        // The site pins 系统通知 to the top of the list; the endpoint does not promise that order.
        return rows
            .groupBy { it.counterpart(ownUid) }
            .mapNotNull { (uid, messages) -> uid?.let { foldConversation(it, messages) } }
            .sortedWith(
                compareByDescending(MessageConversation::isSystem)
                    .thenByDescending { it.updatedAtMillis ?: 0L },
            )
    }

    /**
     * Confirmed on a device (Galaxy S24, 2026-07-26): the message endpoint answers with a flat list
     * of individual messages — one row per *message*, both directions, the same counterparty
     * repeated. Both screens are projections of it: 7e folds the rows into conversations (without
     * which the uid-keyed list crashed on the first person with two messages), 7f slices out one
     * counterparty's rows.
     */
    private suspend fun listRawMessages(): List<RawMessage> {
        val root = json.parseToJsonElement(jsonApi.getJson(NodeSeekJsonClient.messageListPath(), REFERER))
        return root
            .findObjectArray(PREFERRED_LIST_KEYS)
            .mapNotNull { element -> (element as? JsonObject)?.toRawMessage() }
    }

    /**
     * We are a party to every message, so ours is the uid that shows up most across the list; a
     * counterparty's cannot — two conversations never share one. A single-conversation list is
     * genuinely ambiguous, but there [RawMessage.counterpart] still resolves: whichever id this
     * picks, the other becomes the conversation.
     */
    private fun inferOwnUid(rows: List<RawMessage>): Long? =
        rows
            .flatMap { listOfNotNull(it.senderId, it.receiverId) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    private fun foldConversation(
        uid: Long,
        messages: List<RawMessage>,
    ): MessageConversation {
        val newest = messages.maxBy { it.createdAtMillis ?: 0L }
        val name =
            messages.firstNotNullOfOrNull { it.nameOf(uid) } ?: "NodeSeek 用户"
        val isSystem =
            name == MessageConversation.SYSTEM_NAME || messages.any { it.isSystem }
        return MessageConversation(
            uid = uid,
            userName = name,
            avatarUrl = if (isSystem) null else NodeSeekSite.avatarUrl(uid),
            snippet = newest.content,
            isSnippetMine = newest.senderId != null && newest.senderId != uid,
            updatedAtMillis = newest.createdAtMillis,
            updatedAtText = newest.createdAtText,
            unreadCount = messages.count { !it.viewed && it.senderId == uid },
            isSystem = isSystem,
        )
    }

    override suspend fun thread(uid: Long): MessageThread {
        // No per-conversation endpoint exists — the talk-path guess 404ed on a real device — so the
        // thread is the flat list filtered to this counterparty. See [listRawMessages].
        val rows = listRawMessages()
        val ownUid = inferOwnUid(rows)
        val messages =
            rows
                .filter { it.counterpart(ownUid) == uid }
                .sortedBy { it.createdAtMillis ?: 0L }
        return MessageThread(
            uid = uid,
            userName = messages.firstNotNullOfOrNull { it.nameOf(uid) } ?: "NodeSeek 用户",
            avatarUrl = NodeSeekSite.avatarUrl(uid),
            level = null,
            messages = messages.mapIndexed { index, raw -> raw.toDirectMessage(uid, index) },
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
    }
}

/** One wire row of the flat message list, before folding into conversations. */
private data class RawMessage(
    val id: String?,
    val senderId: Long?,
    val receiverId: Long?,
    val senderName: String?,
    val receiverName: String?,
    val content: String,
    val createdAtMillis: Long?,
    val createdAtText: String?,
    val viewed: Boolean,
    val isEdited: Boolean,
    val isSystem: Boolean,
) {
    /** The conversation this message belongs to: whichever end of it is not us. */
    fun counterpart(ownUid: Long?): Long? =
        when {
            receiverId == null -> senderId
            senderId == null -> receiverId
            senderId == ownUid -> receiverId
            else -> senderId
        }

    fun nameOf(uid: Long): String? =
        when (uid) {
            senderId -> senderName
            receiverId -> receiverName
            else -> null
        } ?: senderName.takeIf { receiverId == null }

    fun toDirectMessage(
        counterpartUid: Long,
        index: Int,
    ) = DirectMessage(
        id = id ?: "$counterpartUid-$index-${content.hashCode()}",
        isMine = senderId != null && senderId != counterpartUid,
        content = content,
        sentAtMillis = createdAtMillis,
        sentAtText = createdAtText,
        isEdited = isEdited,
    )
}

private fun JsonObject.toRawMessage(): RawMessage? {
    val content =
        text("content", "last_content", "message", "excerpt")?.collapseWhitespace()?.ifBlank { null }
            ?: return null
    val createdAt = text("created_at", "time", "sent_at", "last_time")
    val updatedAt = text("updated_at", "edited_at")
    val name = text("member_name", "username", "name")
    return RawMessage(
        id = text("id", "message_id", "msg_id"),
        senderId = long("sender_id", "from", "member_id", "uid"),
        receiverId = long("receiver_id", "to", "target_id"),
        senderName = text("sender_name") ?: name,
        receiverName = text("receiver_name", "target_name") ?: name,
        content = content,
        createdAtMillis = TimeFormat.parseTimestamp(createdAt ?: updatedAt),
        createdAtText = (createdAt ?: updatedAt)?.trim()?.ifBlank { null },
        // Absent flag reads as read: inventing unread noise is worse than missing some.
        viewed = boolean("viewed", "is_read", "read") ?: true,
        isEdited =
        boolean("edited", "is_edited")
            ?: (createdAt != null && updatedAt != null && updatedAt != createdAt),
        isSystem =
        boolean("is_system", "system") == true ||
            name == MessageConversation.SYSTEM_NAME ||
            text("sender_name") == MessageConversation.SYSTEM_NAME,
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

private fun JsonObject.text(vararg names: String): String? {
    names.forEach { name -> this[name]?.jsonPrimitive?.contentOrNull?.let { return it } }
    return null
}

private fun JsonObject.long(vararg names: String): Long? {
    names.forEach { name -> this[name]?.jsonPrimitive?.longOrNull?.let { return it } }
    return null
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
