package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The site's three notification groups, in the order `/notification` shows them.
 *
 * There is no fourth "system" group: what looks like one on the web is a pinned conversation named
 * 系统通知 inside [MESSAGES] — see `docs/design-requirements-remaining.md` §0.1. [MESSAGES] has no
 * notification rows of its own either; selecting it shows the conversation list (board 7e), which is
 * why its endpoint lives in [MessageRepository] rather than here.
 *
 * [viewedField] is the name `markViewed` wants its id array under. It is a different word per group
 * — `atMe`, `replys`, `messages` — read off the site's own `notification.js` (2026-08-02); there is
 * no shared `ids` spelling to fall back on.
 */
enum class NotificationCategory(val endpoint: String?, val viewedField: String) {
    MENTIONS("at-me", "atMe"),
    REPLIES("reply-to-me", "replys"),
    MESSAGES(null, "messages"),
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
    /**
     * The row id `markViewed` takes, when the endpoint sent one.
     *
     * Separate from [id] because [id] is a display key that falls back to a synthesised string, and
     * posting a synthesised key would clear whatever row happens to own that number server-side.
     */
    val viewedId: Long?,
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

    /**
     * The unread badges, as one value the whole app reads.
     *
     * The tab badge and the group chips used to be a field copied into one screen's UiState, which
     * meant the only thing that could change them was that screen re-running its own load — so
     * opening the item you were being badged about left the badge exactly where it was. Keeping the
     * counts here instead lets a read anywhere (a notification opened, a conversation opened, the
     * poll worker) settle them for everyone.
     */
    private val _counts = MutableStateFlow(NotificationCounts())
    val counts: StateFlow<NotificationCounts> = _counts.asStateFlow()

    suspend fun refreshCounts(): NotificationCounts {
        val root = json.parseToJsonElement(jsonSource.getJson(NodeSeekJsonClient.PATH_UNREAD_COUNT))
        return NotificationCounts(
            replies = root.count("reply", "replyCount"),
            mentions = root.count("atMe", "at_me", "mention"),
            messages = root.count("message", "messages", "msg"),
        ).also { _counts.value = it }
    }

    /** Signing out: the next account's badge must not start at the last one's. */
    fun clearCounts() {
        _counts.value = NotificationCounts()
    }

    /**
     * Takes [count] off a group's badge before the server has been asked anything.
     *
     * Opening an item has to move the badge in the same frame as the row greys out, or the two
     * disagree for as long as the round trip takes and the badge reads as stuck. Whatever the
     * server says next — from [markViewed], or the next [refreshCounts] — replaces this.
     */
    fun noteRead(
        category: NotificationCategory,
        count: Int = 1,
    ) {
        if (count <= 0) return
        _counts.update { current ->
            when (category) {
                NotificationCategory.MENTIONS ->
                    current.copy(mentions = (current.mentions - count).coerceAtLeast(0))

                NotificationCategory.REPLIES ->
                    current.copy(replies = (current.replies - count).coerceAtLeast(0))

                NotificationCategory.MESSAGES ->
                    current.copy(messages = (current.messages - count).coerceAtLeast(0))
            }
        }
    }

    /**
     * Clears single rows server-side, the way clicking one on the site does.
     *
     * `notification.js` posts the row ids to the group's own `markViewed` and then re-reads
     * `unread-count`; both halves matter here, because the endpoint answers with a bare ack and the
     * badge is only ever as right as the last count we were given.
     */
    suspend fun markViewed(
        category: NotificationCategory,
        ids: List<Long>,
    ) {
        val endpoint = category.endpoint ?: return
        if (ids.isEmpty()) return
        noteRead(category, ids.size)
        jsonSource.postJson(
            path = NodeSeekJsonClient.markViewedPath(endpoint),
            body = markViewedBody(category, ids),
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH,
        )
        refreshCounts()
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
        refreshCounts()
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

/** `{"atMe":[1,2]}` / `{"replys":[…]}` / `{"messages":[…]}` — numbers, as the site sends them. */
// Public rather than `internal` only because the test that pins it is still in `:app`: the
// fakes it shares with the ViewModel tests are one file, and two copies of a fake drift. Step
// D1 brings `ui/` down here and the whole test tree with it.
internal fun markViewedBody(
    category: NotificationCategory,
    ids: List<Long>,
): String =
    buildJsonObject {
        put(category.viewedField, JsonArray(ids.map(::JsonPrimitive)))
    }.toString()

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
        // `id`, not `comment_id`: the site's own 标为已读 posts the row's `id`, and on the reply
        // endpoint those two are different numbers.
        viewedId = long("id"),
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
