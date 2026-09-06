package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.TimeFormat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/**
 * What the 通知 screen actually offers, which is one group fewer than the site has.
 *
 * The site keeps @我 and 回复主题 apart, and for the reader they are mostly the same events twice:
 * the 回复 button writes `@name #7` at the head of every reply it sends, so one comment raises a row
 * in both lists. Reading them separately means reading each of those comments twice and marking it
 * read twice — so [INTERACTIONS] fetches both and folds the rows that name the same comment
 * together, and the screen shows one list. The split survives underneath, because the endpoints,
 * the badges and `markViewed` are still per [NotificationCategory]; it is only the reading of them
 * that is joined.
 *
 * An App decision, not the site's — noted as such in `docs/implementation-status.md`.
 */
enum class NotificationTab(val categories: List<NotificationCategory>) {
    INTERACTIONS(listOf(NotificationCategory.REPLIES, NotificationCategory.MENTIONS)),
    MESSAGES(listOf(NotificationCategory.MESSAGES)),
}

data class NotificationCounts(
    val replies: Int = 0,
    val mentions: Int = 0,
    val messages: Int = 0,
    /**
     * How many unread rows the loaded page proved are one comment counted twice.
     *
     * The server counts each group on its own, so a reply that also @-ed me is a 1 in both — two
     * unread, one thing to read. Merging the lists is what discovers those pairs, so this is set by
     * [NotificationRepository.interactions] and carried through the count refreshes that follow;
     * before the first load it is 0 and the badge is the server's own sum, which errs high rather
     * than pointing at nothing.
     */
    val overlap: Int = 0,
) {
    /** The 通知 badge: both groups, less what they double-counted. */
    val interactions: Int get() = (replies + mentions - overlap).coerceAtLeast(0)

    val all: Int get() = interactions + messages

    fun forCategory(category: NotificationCategory): Int =
        when (category) {
            NotificationCategory.MENTIONS -> mentions
            NotificationCategory.REPLIES -> replies
            NotificationCategory.MESSAGES -> messages
        }

    fun forTab(tab: NotificationTab): Int =
        when (tab) {
            NotificationTab.INTERACTIONS -> interactions
            NotificationTab.MESSAGES -> messages
        }
}

/**
 * One group's row about one comment: which list it came from, and how it is cleared.
 *
 * [viewedId] is the row id `markViewed` takes, when the endpoint sent one. Separate from
 * [ForumNotification.id] because that is a display key which falls back to a synthesised string,
 * and posting a synthesised key would clear whatever row happens to own that number server-side.
 */
data class NotificationSource(
    val category: NotificationCategory,
    val viewedId: Long?,
    val isUnread: Boolean,
)

/**
 * One notification row, which may be the same comment as seen by both groups — see [NotificationTab].
 *
 * The sentence is not stored: board 7d renders it as "{actor} 在帖子 {title} 中@了我" with the actor
 * and the thread styled differently, so the screen composes it from these parts and a string
 * resource, and [sources] is what decides whether that sentence says 回复, @, or both.
 * [createdAtMillis] is null when the endpoint pre-rendered the time, in which case [createdAtText]
 * is all we have.
 */
data class ForumNotification(
    val id: String,
    /** Every group this comment raised a row in; one entry unless the two groups were folded. */
    val sources: List<NotificationSource>,
    /**
     * The comment this points at, when the endpoint named it.
     *
     * The merge key, and the reason it is a field of its own rather than [id] read back: [id] falls
     * back to a synthesised string, and two synthesised strings that happen to match are not
     * evidence of anything.
     */
    val commentId: Long?,
    val postId: Long?,
    val floor: String?,
    val actorUid: Long?,
    val actorName: String,
    val avatarUrl: String?,
    /** What was written, flattened to the one line a row can show — see [contentPreview]. */
    val preview: List<PreviewPart>,
    val threadTitle: String?,
    val createdAtMillis: Long?,
    val createdAtText: String?,
) {
    /** Unread while any group still holds it: reading it here has to clear both, or the badge returns. */
    val isUnread: Boolean get() = sources.any(NotificationSource::isUnread)

    val isReply: Boolean get() = hasCategory(NotificationCategory.REPLIES)

    val isMention: Boolean get() = hasCategory(NotificationCategory.MENTIONS)

    fun read(): ForumNotification = copy(sources = sources.map { it.copy(isUnread = false) })

    private fun hasCategory(category: NotificationCategory) = sources.any { it.category == category }
}

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
            // Carried, not re-read: `unread-count` answers per group and knows nothing about which
            // of them are the same comment. Only a merged load discovers that, and dropping the
            // number here would make the badge jump every time anything asked for the counts.
            overlap = _counts.value.overlap,
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

    private suspend fun postViewed(
        category: NotificationCategory,
        ids: List<Long>,
    ) {
        val endpoint = category.endpoint ?: return
        jsonSource.postJson(
            path = NodeSeekJsonClient.markViewedPath(endpoint),
            body = markViewedBody(category, ids),
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.NOTIFICATION_PATH,
        )
    }

    /**
     * Clears one row server-side, the way clicking one on the site does — every group it arrived in.
     *
     * `notification.js` posts the row ids to the group's own `markViewed` and then re-reads
     * `unread-count`; both halves matter here, because the endpoint answers with a bare ack and the
     * badge is only ever as right as the last count we were given.
     *
     * A folded row was two unread rows on the server, and clearing only the group whose sentence the
     * screen happened to show would leave the other one to raise the badge again on the next
     * refresh. The badges move together too — two groups lose one each, and the pair they were
     * double-counting stops being counted at all, so the number falls by one row rather than by two.
     */
    suspend fun markViewed(sources: List<NotificationSource>) {
        val pending = sources.filter { it.isUnread && it.viewedId != null }
        if (pending.isEmpty()) return
        _counts.update { current ->
            current.copy(overlap = (current.overlap - (pending.size - 1)).coerceAtLeast(0))
        }
        pending.groupBy({ it.category }, { requireNotNull(it.viewedId) }).forEach { (category, ids) ->
            noteRead(category, ids.size)
            postViewed(category, ids)
        }
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

    /**
     * 回复 and @我 as the one list [NotificationTab.INTERACTIONS] shows.
     *
     * Both requests go out together because neither answer is worth showing without the other: the
     * screen has a single list, and rendering the replies first only to reshuffle them a moment
     * later when the mentions land would move rows under the reader's thumb. A failure on either
     * side fails the load, for the same reason — half a notification list, silently, is worse than
     * the error state and its retry.
     *
     * The pass over the merged rows is also where [NotificationCounts.overlap] comes from: every row
     * still unread in both groups is one the server counted twice.
     */
    suspend fun interactions(): List<ForumNotification> =
        coroutineScope {
            val replies = async { notifications(NotificationCategory.REPLIES) }
            val mentions = async { notifications(NotificationCategory.MENTIONS) }
            mergeInteractions(replies.await(), mentions.await()).also { merged ->
                val overlap =
                    merged.sumOf { row -> (row.sources.count(NotificationSource::isUnread) - 1).coerceAtLeast(0) }
                _counts.update { it.copy(overlap = overlap) }
            }
        }
}

/**
 * Folds the two groups into one list, newest first.
 *
 * Two rows are the same event when they name the same comment, or — for an endpoint that sends no
 * `comment_id` — the same floor of the same thread. Anything with neither is left alone: a row that
 * cannot be identified is shown twice, which is today's behaviour, rather than folded into a
 * stranger.
 *
 * The reply row is the one kept, because it is the row that carries the floor and therefore the tap
 * target; the mention only adds what the reply was missing, and its [NotificationSource] so that
 * reading the row clears both. Rows the site sent wording for instead of a timestamp cannot be
 * placed among the others and keep their group's order at the end of the list.
 */
internal fun mergeInteractions(
    replies: List<ForumNotification>,
    mentions: List<ForumNotification>,
): List<ForumNotification> {
    val folded = LinkedHashMap<String, ForumNotification>()
    val unidentified = mutableListOf<ForumNotification>()
    (replies + mentions).forEach { row ->
        val key = row.mergeKey
        if (key == null) {
            unidentified += row
        } else {
            folded[key] = folded[key]?.foldIn(row) ?: row
        }
    }
    return (folded.values + unidentified).sortedByDescending { it.createdAtMillis ?: Long.MIN_VALUE }
}

/** What identifies the comment behind a row, or null when the endpoint said nothing that does. */
private val ForumNotification.mergeKey: String?
    get() =
        commentId?.let { "comment-$it" }
            ?: postId?.let { post -> floor?.let { "post-$post-$it" } }

private fun ForumNotification.foldIn(other: ForumNotification): ForumNotification =
    copy(
        sources = sources + other.sources,
        floor = floor ?: other.floor,
        actorUid = actorUid ?: other.actorUid,
        avatarUrl = avatarUrl ?: other.avatarUrl,
        preview = preview.ifEmpty { other.preview },
        threadTitle = threadTitle ?: other.threadTitle,
        createdAtMillis = createdAtMillis ?: other.createdAtMillis,
        createdAtText = createdAtText ?: other.createdAtText,
    )

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
    val commentId = long("comment_id", "commentId")
    return ForumNotification(
        // The comment's own id when there is one, and otherwise a key that carries the group it came
        // from: the two lists are shown as one now, and their row ids are two separate sequences —
        // an `id` of 7 from each would collide as a list key without ever being the same comment.
        id = commentId?.toString() ?: "${category.name}-${text("id", "message_id") ?: "$postId-$index"}",
        sources =
        listOf(
            NotificationSource(
                category = category,
                // `id`, not `comment_id`: the site's own 标为已读 posts the row's `id`, and on the
                // reply endpoint those two are different numbers.
                viewedId = long("id"),
                isUnread = !viewed,
            ),
        ),
        commentId = commentId,
        postId = postId,
        floor = floorValue?.let { if (it.startsWith('#')) it else "#$it" },
        actorUid = actorUid,
        actorName = actor,
        avatarUrl = actorUid?.let { NodeSeekSite.avatarUrl(it) },
        preview = contentPreview(text("content", "comment_content", "excerpt", "message")),
        threadTitle = text("post_title", "discussion_title", "title"),
        createdAtMillis = TimeFormat.parseTimestamp(createdAt),
        createdAtText = createdAt?.trim()?.ifBlank { null },
    )
}
