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
 * is no separate system notification group — but its messages arrive as Markdown, so the row renders
 * the snippet as inline rich text rather than plain characters.
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

/** One bubble in board 7f. */
data class DirectMessage(
    val id: String,
    val isMine: Boolean,
    val content: String,
    /** The site stores this per message; the composer's own switch must not decide how one renders. */
    val isMarkdown: Boolean,
    val sentAtMillis: Long?,
    val sentAtText: String?,
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

    /** Clears every unread message in the 私信 group. */
    suspend fun markAllRead()
}

/**
 * Direct messages, over the same three endpoints the site's own `notification.js` calls.
 *
 * Read off the live bundle rather than guessed (2026-07-26, signed-in device): `message/list` is one
 * row per *conversation* carrying only its latest message, `message/with/{uid}` is the full history,
 * and `message/send` takes `receiverUid` — not the `receiver_id` the other endpoints use, which is
 * the kind of detail no amount of consistency reasoning would have produced.
 *
 * Parsing stays shape-tolerant (field names probed in order, as in [NotificationRepository]) so a
 * server-side rename costs a field rather than the screen.
 */
class NetworkMessageRepository(
    private val jsonApi: JsonApi,
    /**
     * Our own uid, for the one case the rows cannot resolve on their own. Suspending and nullable
     * because the only source is a network round trip that is allowed to fail — see [inferOwnUid].
     */
    private val currentUid: suspend () -> Long?,
) : MessageRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun conversations(): List<MessageConversation> {
        val body = jsonApi.getJson(NodeSeekJsonClient.messageListPath(), REFERER)
        val rows =
            json
                .parseToJsonElement(body)
                .findObjectArray(PREFERRED_LIST_KEYS)
                .mapNotNull { element -> (element as? JsonObject)?.toRawMessage() }
        /*
         * Each row names both ends of a conversation and neither is labelled "the other person", so
         * the counterparty is whichever uid is not ours — the same reduction `notification.js` does.
         * Grouping rather than mapping is what fixed the crash this screen shipped with: keying the
         * list on a uid picked field-by-field made every row key on the sender, which is us.
         */
        val ownUid = inferOwnUid(rows)
        return rows
            .groupBy { it.counterpart(ownUid) }
            .mapNotNull { (uid, messages) -> uid?.let { conversationOf(it, messages, ownUid) } }
            // The site pins 系统通知 to the top; the endpoint does not promise that order.
            .sortedWith(
                compareByDescending(MessageConversation::isSystem)
                    .thenByDescending { it.updatedAtMillis ?: 0L },
            )
    }

    override suspend fun thread(uid: Long): MessageThread {
        val body =
            jsonApi.getJson(
                path = NodeSeekJsonClient.messageThreadPath(uid),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.messageThreadWebPath(uid),
            )
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw NodeSeekException(NodeSeekError.Unparsable)
        val header = root["talkTo"] as? JsonObject
        val messages =
            root
                .findObjectArray(PREFERRED_THREAD_KEYS)
                .mapNotNull { element -> (element as? JsonObject)?.toRawMessage() }
                .sortedBy { it.createdAtMillis ?: 0L }
        return MessageThread(
            uid = uid,
            userName = header?.text("member_name", "username", "name") ?: "NodeSeek 用户",
            avatarUrl = NodeSeekSite.avatarUrl(uid),
            level = header?.get("rank")?.jsonPrimitive?.intOrNull,
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
                // `receiverUid`, camel-cased, unlike every other field in this API.
                put("receiverUid", uid)
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
        // The endpoint answers with a bare ack; the screen keeps its own optimistic bubble.
        return (root["data"] as? JsonObject)
            ?.toRawMessage()
            ?.toDirectMessage(uid, index = 0)
    }

    override suspend fun markAllRead() {
        jsonApi.postJson(
            path = NodeSeekJsonClient.PATH_MESSAGE_MARK_VIEWED_ALL,
            body = "",
            referer = REFERER,
        )
    }

    /**
     * Which uid in these rows is us.
     *
     * We are a party to every conversation, so our uid is in the intersection of every row's two
     * ends; a counterparty's cannot be, because no two conversations share one. That settles it as
     * soon as there are two conversations.
     *
     * One conversation is genuinely ambiguous — both ids appear once — and guessing there is not
     * harmless: a new account's inbox holds exactly the system conversation, and picking wrong keys
     * the row on ourselves and flips the whole thread. So that case asks who we are instead. A
     * failed lookup falls back to the sender, which is right whenever the last word was theirs.
     */
    private suspend fun inferOwnUid(rows: List<RawMessage>): Long? {
        val candidates =
            rows
                .map { setOfNotNull(it.senderId, it.receiverId) }
                .reduceOrNull { shared, row -> shared intersect row }
                .orEmpty()
        return candidates.singleOrNull()
            ?: currentUid()?.takeIf { it in candidates }
            ?: candidates.firstOrNull { it != rows.firstOrNull()?.senderId }
    }

    private fun conversationOf(
        uid: Long,
        rows: List<RawMessage>,
        ownUid: Long?,
    ): MessageConversation {
        val newest = rows.maxBy { it.createdAtMillis ?: 0L }
        val name = rows.firstNotNullOfOrNull { it.nameOf(uid) } ?: "NodeSeek 用户"
        val isSystem = name == MessageConversation.SYSTEM_NAME
        return MessageConversation(
            uid = uid,
            userName = name,
            avatarUrl = if (isSystem) null else NodeSeekSite.avatarUrl(uid),
            snippet = newest.content,
            isSnippetMine = newest.senderId != null && newest.senderId == ownUid,
            updatedAtMillis = newest.createdAtMillis,
            updatedAtText = newest.createdAtText,
            // The list carries one row per conversation, so this is "has unread", counted as one.
            unreadCount = rows.count { !it.viewed && it.senderId == uid },
            isSystem = isSystem,
        )
    }

    private companion object {
        val REFERER = NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH
        val PREFERRED_LIST_KEYS = listOf("msgArray", "messageList", "conversations", "list", "data")
        val PREFERRED_THREAD_KEYS = listOf("msgArray", "messageList", "list", "data")
    }
}

/** One wire row. The list and thread endpoints share a shape; only the completeness differs. */
private data class RawMessage(
    val id: String?,
    val senderId: Long?,
    val receiverId: Long?,
    val senderName: String?,
    val receiverName: String?,
    val content: String,
    val isMarkdown: Boolean,
    val createdAtMillis: Long?,
    val createdAtText: String?,
    val viewed: Boolean,
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
        isMarkdown = isMarkdown,
        sentAtMillis = createdAtMillis,
        sentAtText = createdAtText,
    )
}

private fun JsonObject.toRawMessage(): RawMessage? {
    val content =
        text("content", "last_content", "message", "excerpt")?.trim()?.ifBlank { null }
            ?: return null
    val createdAt = text("created_at", "time", "sent_at", "last_time")
    val name = text("member_name", "username", "name")
    return RawMessage(
        id = text("id", "message_id", "msg_id"),
        senderId = long("sender_id", "from", "member_id", "uid"),
        receiverId = long("receiver_id", "to", "target_id"),
        senderName = text("sender_name") ?: name,
        receiverName = text("receiver_name", "target_name") ?: name,
        content = content,
        // Absent flag reads as Markdown: this forum writes Markdown, and rendering a plain message
        // as Markdown is at worst a lost asterisk, while the reverse leaks link syntax into a bubble.
        isMarkdown = boolean("is_markdown", "markdown") ?: true,
        createdAtMillis = TimeFormat.parseTimestamp(createdAt),
        createdAtText = createdAt?.trim()?.ifBlank { null },
        // Absent flag reads as read: inventing unread noise is worse than missing some.
        viewed = boolean("viewed", "is_read", "read") ?: true,
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
