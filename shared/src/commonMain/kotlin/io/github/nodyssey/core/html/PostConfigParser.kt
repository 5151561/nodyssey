package io.github.nodyssey.core.html

import com.fleeksoft.ksoup.nodes.Document
import io.github.nodyssey.model.PostReactions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * What the post page's own `__config__` blob knows about each floor, keyed by `commentId`.
 *
 * [reactions] is empty and [blockedCommentIds] is empty when the page carried no blob — which is a
 * different claim from "no marks and nothing blocked", and why both are read in one pass.
 */
internal data class PostConfig(
    val reactions: Map<Long, PostReactions> = emptyMap(),
    val blockedCommentIds: Set<Long> = emptySet(),
    /**
     * The floors this account wrote, as the *server* labelled them (`comments[].poster.isMe`).
     *
     * Empty when the page carried no blob, which for this one is the right answer rather than a lost
     * one: no blob means nobody is signed in, and a signed-out reader owns no floor. Not derived by
     * comparing uids on the device — the same reasoning as [blockedCommentIds], and the site's own
     * client puts 编辑 behind exactly this flag.
     */
    val ownCommentIds: Set<Long> = emptySet(),
    /**
     * When each floor was last edited, per the blob's `comments[].time`.
     *
     * Holds only the floors the site named a time for: an unedited floor arrives with
     * `editedDate: null`, so an absent key is "never edited" rather than "not told".
     */
    val editedTimes: Map<Long, FloorEditTime> = emptyMap(),
    /**
     * Whether this account has the thread in its collection.
     *
     * Null when the page carried no blob — which is not "not collected". The site has no endpoint
     * that answers this question on its own, so a page without a blob leaves it genuinely unknown
     * and the star has to stay untappable rather than offer to remove a collection that may exist.
     */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
)

/**
 * When a floor was last edited, in the site's own words.
 *
 * [relative] is the blob's `editedDateRel` ("5min ago"), [absolute] its `editedDateFormated`
 * ("2026-04-27 15:57:00") — the same pair the site renders the created time from, and the reason the
 * marker can say *when* rather than only *that*. Either half can be missing on its own; the floor
 * was still edited, which is the claim the marker itself makes.
 */
internal data class FloorEditTime(
    val relative: String?,
    val absolute: String?,
)

/**
 * Per-floor state, read out of the post page's own `__config__` blob rather than off the markup.
 *
 * The rendered floor shows the three counts, but not whether *this* account has already spent a
 * chicken leg on it — the site's client keeps that in [SiteBootstrap]'s payload and greys its own
 * buttons from there. Scraping the visible numbers would therefore get us two thirds of the state and
 * leave the third to be discovered by having a write rejected. `blocked` is in the same payload and
 * is likewise the server's answer, not ours.
 *
 * Every failure here is silent and yields no entry. A thread whose blob is missing or reshaped still
 * has an article worth reading, and [PostDetailParser] must not lose it over a tally.
 */
internal object PostConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** The blob's per-floor state, empty when the page carried none. The body is a comment here too. */
    fun parse(document: Document): PostConfig {
        val decoded = SiteBootstrap.decodeOrNull(document) ?: return PostConfig()
        val postData =
            try {
                json.parseToJsonElement(decoded).jsonObject["postData"]?.jsonObject
            } catch (exception: IllegalArgumentException) {
                null
            } ?: return PostConfig()

        val reactions = mutableMapOf<Long, PostReactions>()
        val blocked = mutableSetOf<Long>()
        val own = mutableSetOf<Long>()
        val edited = mutableMapOf<Long, FloorEditTime>()
        // Absent on a reshaped page, which costs the tallies but must not cost the thread-level
        // state sitting next to them — hence a loop over nothing rather than an early return.
        postData["comments"]?.jsonArray.orEmpty().forEach { entry ->
            val comment = entry as? JsonObject ?: return@forEach
            val commentId = comment.long("commentId") ?: return@forEach
            reactions[commentId] =
                PostReactions(
                    likeCount = comment.int("likeCount"),
                    dislikeCount = comment.int("dislikeCount"),
                    upvoteCount = comment.int("upvoteCount"),
                    liked = comment.bool("liked"),
                    disliked = comment.bool("disliked"),
                    upvoted = comment.bool("upvoted"),
                )
            if (comment.bool("blocked")) blocked += commentId
            // Safe cast, not `jsonObject`: that accessor throws on a reshaped `poster`, and this
            // loop is inside the pass that must not cost the page its tallies.
            if ((comment["poster"] as? JsonObject)?.bool("isMe") == true) own += commentId
            (comment["time"] as? JsonObject)?.let { time -> edited.putEdit(commentId, time) }
        }
        return PostConfig(
            reactions = reactions,
            blockedCommentIds = blocked,
            ownCommentIds = own,
            editedTimes = edited,
            // Thread-level, so they sit on `postData` itself rather than inside `comments`.
            collected = postData.boolOrNull("collected"),
            collectionCount = postData.intOrNull("collectionCount"),
        )
    }

    /**
     * The floor's edit stamp, or no entry at all.
     *
     * `editedDate` is the field the site nulls out on a floor nobody has edited, and the two
     * rendered forms follow it — but any one of the three is enough to state the fact, so a blob
     * that carried only the relative string still marks the floor.
     */
    private fun MutableMap<Long, FloorEditTime>.putEdit(
        commentId: Long,
        time: JsonObject,
    ) {
        val relative = time.string("editedDateRel")
        val absolute = time.string("editedDateFormated")
        if (time.string("editedDate") == null && relative == null && absolute == null) return
        this[commentId] = FloorEditTime(relative = relative, absolute = absolute)
    }

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    /*
     * Via `as?` rather than `jsonPrimitive`: a reshaped `time` whose fields arrived as objects would
     * otherwise throw out of the loop, and the marker is not worth the page's tallies.
     */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }

    /*
     * Via the string form, not `intOrNull`: the counts have arrived as both `12` and `"12"` depending
     * on the field, and a tally that silently reads zero is worse than one that reads slightly late.
     */
    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

    /*
     * The nullable pair, for the thread-level fields. Kept separate from [bool] and [int] rather
     * than made the general case: a floor with no `liked` key really has not been marked, but a
     * page with no `collected` key has told us nothing, and collapsing the two would put a
     * tappable "remove from collection" in front of a reader whose page never mentioned it.
     */
    private fun JsonObject.boolOrNull(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.intOrNull(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0)
}
