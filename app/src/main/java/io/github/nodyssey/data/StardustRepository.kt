package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.TimeFormat
import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.core.net.NodeSeekJsonClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * What moved a stardust balance.
 *
 * These are the site's own five kinds, taken from the label map in its `stardustList` bundle rather
 * than guessed. Recording them matters because the app previously assumed stardust had exactly one
 * source and one use — "评论被点赞 +1" and transfer — and that is wrong: an admin grant, a system
 * adjustment and buying an invite code all move the same balance, and only [UPVOTE] is reliably a gain.
 */
enum class StardustType(val wireValue: String) {
    UPVOTE("upvote"),
    TRANSFER("transfer"),
    BUY_CODE("buyCode"),
    SYSTEM("system"),
    ADMIN("admin"),

    /** A sixth kind we have not seen. [StardustEntry.rawType] then carries the site's own word. */
    UNKNOWN(""),
    ;

    companion object {
        fun fromWire(raw: String?): StardustType =
            entries.firstOrNull { it.wireValue.isNotEmpty() && it.wireValue == raw } ?: UNKNOWN
    }
}

/**
 * One stardust movement.
 *
 * [diff] is signed — a transfer out and an invite-code purchase are negative — and [balanceAfter] is
 * the balance the movement left behind, which is what makes a row auditable without arithmetic.
 * [peerUid] is the other party: whoever liked the comment, or whoever the transfer went to or from.
 */
data class StardustEntry(
    val id: Long,
    val type: StardustType,
    /** Only meaningful for [StardustType.UNKNOWN]; the row shows it rather than inventing a label. */
    val rawType: String?,
    val diff: Int,
    val balanceAfter: Int?,
    val peerUid: Long?,
    val commentId: Long?,
    val refId: Long?,
    val createdAtMillis: Long?,
)

data class StardustLedgerPage(
    val entries: List<StardustEntry>,
    /** Feed back as `before_id` to get the next page; null when the payload omitted it. */
    val cursor: Long?,
    val hasMore: Boolean,
)

interface StardustRepository {
    /**
     * One page of a member's ledger, newest first.
     *
     * [beforeId] is the previous page's [StardustLedgerPage.cursor]; null asks for the newest rows.
     */
    suspend fun entries(
        memberId: Long,
        beforeId: Long? = null,
    ): StardustLedgerPage

    /**
     * The name the site shows for [recipientUid] before a transfer, or null when it answered without
     * one.
     *
     * A refusal — the uid does not exist, the account cannot receive — arrives as a
     * [NodeSeekException] carrying the site's own sentence. Null means the call succeeded and simply
     * named nobody, which is not the same thing and must not be shown as an error.
     *
     * [viewerUid] only builds the referer: this runs from the signed-in user's own ledger page.
     */
    suspend fun recipientName(
        recipientUid: Long,
        viewerUid: Long,
    ): String?

    /**
     * Sends [amount] stardust to [recipientUid], quoting [refId].
     *
     * Returns normally only when the site said it landed. There is no undo on either side.
     */
    suspend fun send(
        recipientUid: Long,
        amount: Int,
        refId: Long,
        viewerUid: Long,
    )
}

/**
 * The stardust ledger and the two writes that move it, read from the endpoints the site's own pages use.
 *
 * `/stardust/list` renders client-side, so for a long time this repository answered
 * [NodeSeekError.NotWired] rather than guess at a payload. The guessing stopped being necessary on
 * 2026-07-30: the contract came out of the site's own `stardustList` bundle and was then checked
 * against a live signed-in account, so the field names below are read, not inferred.
 *
 * Paging is by cursor and the server enforces a parameter whitelist — see
 * [NodeSeekJsonClient.stardustListPath] for why a stray parameter is an HTTP 422 rather than a
 * silently ignored one.
 */
class NetworkStardustRepository(
    private val api: JsonApi,
    private val dispatchers: AppDispatchers,
) : StardustRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun entries(
        memberId: Long,
        beforeId: Long?,
    ): StardustLedgerPage {
        val body =
            api.getJson(
                path = NodeSeekJsonClient.stardustListPath(memberId, beforeId),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.stardustPath(memberId),
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) as? JsonObject }
                    .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            val rows =
                root.findObjectArray("records", "list", "data")
                    ?: throw NodeSeekException(NodeSeekError.Unparsable)
            val entries = rows.mapNotNull(JsonObject::toStardustEntry)
            StardustLedgerPage(
                entries = entries,
                cursor = root.long("cursor") ?: entries.lastOrNull()?.id,
                // `exist_more` is authoritative when present. Without it a full page is assumed to
                // have a successor: stopping early would hide rows, and one wasted request would not.
                hasMore =
                root.bool("exist_more", "existMore", "hasMore")
                    ?: (entries.size >= NodeSeekJsonClient.STARDUST_PAGE_SIZE),
            )
        }
    }

    override suspend fun recipientName(
        recipientUid: Long,
        viewerUid: Long,
    ): String? {
        val answer =
            api.postJson(
                path = NodeSeekJsonClient.PATH_STARDUST_PREPARE,
                body = """{"receiver_id":$recipientUid,"origin":"$TRANSFER_ORIGIN"}""",
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.stardustPath(viewerUid),
            )
        return withContext(dispatchers.default) {
            val root = answer.asJsonObject()
            root.refusal()?.let { throw it }
            root.text("receiver_name", "receiverName", "member_name")
        }
    }

    override suspend fun send(
        recipientUid: Long,
        amount: Int,
        refId: Long,
        viewerUid: Long,
    ) {
        val answer =
            api.postJson(
                path = NodeSeekJsonClient.PATH_STARDUST_SEND,
                body = """{"member_id":$recipientUid,"diff":$amount,"ref_id":$refId}""",
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.stardustPath(viewerUid),
            )
        withContext(dispatchers.default) {
            answer.asJsonObject().refusal()?.let { throw it }
        }
    }

    private fun String.asJsonObject(): JsonObject =
        runCatching { json.parseToJsonElement(this) as? JsonObject }
            .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
            ?: throw NodeSeekException(NodeSeekError.Unparsable)

    /**
     * `success:false` is a refusal, not a fault — the site answers it with 200 and a sentence.
     *
     * Which makes it the only thing standing between "sent" and "the site said no": a send that reads
     * status alone would report every refusal as a completed transfer.
     */
    private fun JsonObject.refusal(): NodeSeekException? =
        if (bool("success") == false) {
            NodeSeekException(NodeSeekError.Unknown, detail = text("message"))
        } else {
            null
        }

    companion object {
        /**
         * The `origin` `payment-prepare` asks for, which the API note records the *name* of but not
         * its accepted values.
         *
         * This is the site's own word for the movement in the ledger (`type: "transfer"`, beside
         * `buyCode` — the other thing that spends stardust, which is the likely reason one lookup
         * endpoint takes an origin at all). It is a guess, and it is deliberately a cheap one to be
         * wrong about: a rejected lookup costs the name echo on the confirmation step and nothing
         * else, because [send] does not depend on it.
         */
        internal const val TRANSFER_ORIGIN = "transfer"
    }
}

/**
 * A row without an id is dropped rather than kept.
 *
 * The id is the cursor: a row that cannot be paged past would make the next request repeat the page
 * it came from, so an unidentifiable row is worse than a missing one.
 */
private fun JsonObject.toStardustEntry(): StardustEntry? {
    val id = long("id") ?: return null
    val rawType = text("type")
    return StardustEntry(
        id = id,
        type = StardustType.fromWire(rawType),
        rawType = rawType,
        diff = int("diff", "amount") ?: return null,
        balanceAfter = int("result", "balance"),
        peerUid = long("peer_id", "peerId"),
        commentId = long("comment_id", "commentId"),
        refId = long("ref_id", "refId"),
        createdAtMillis = TimeFormat.parseTimestamp(text("created_at", "createdAt")),
    )
}
