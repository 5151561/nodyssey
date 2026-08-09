package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.TimeFormat
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** What a moderation entry did. Drives the leading icon, which is the only scannable part of the row. */
enum class RulingKind {
    PENALTY,
    BAN,
    MOVE,
    PERMISSION,
    REWARD,
}

/**
 * What the decision was about.
 *
 * [USER] is not "a post by a user" — it is the account itself, which the payload marks by addressing
 * `target.uid` instead of a comment id and sending `post_id: -1`. Those rows have nothing to link to
 * but the space page.
 */
enum class RulingTarget {
    POST,
    COMMENT,
    USER,
}

/**
 * One verb of a moderation decision.
 *
 * Structured rather than pre-rendered because the data layer does not write user copy — and because
 * the site's own wording is built for a five-column table ("因“灌水”被-10鸡腿"), where a phone row wants
 * the reason once, on its own line, and the verbs joined after it.
 *
 * The set is the whole of the site's `formatAction`, read out of its `ruling` bundle on 2026-08-02.
 * [Title] and [Pin] are the two branches that never turned up while sampling five pages of live data;
 * they are here because the formatter has them, not because they were observed.
 */
sealed interface RulingAction {
    /** 鸡腿, signed: negative is a deduction. The reason travels on [RulingRecord.reason]. */
    data class Coin(val diff: Int) : RulingAction

    /** 星辰, signed the same way. */
    data class Stardust(val diff: Int) : RulingAction

    data class Title(val title: String) : RulingAction

    /** Board slug, not a title: resolving it is the UI's job, and old rows name boards that moved. */
    data class Move(val boardSlug: String) : RulingAction

    /**
     * 阅读权限, as the raw number the site prints.
     *
     * Not labelled "Lv" here. Real rows carry 255, which is no level anyone holds, and the site itself
     * renders the bare figure — dressing it up as a level would be inventing a meaning for it.
     */
    data class ReadRank(val rank: Int) : RulingAction

    data class Lock(val locked: Boolean) : RulingAction

    /** 推荐阅读 — `false` is a withdrawal, which is why this is not a marker object. */
    data class Award(val award: Boolean) : RulingAction

    /** [wholeUser] is the account-wide form, which the site words as 隐藏全部内容. */
    data class Hide(val hidden: Boolean, val wholeUser: Boolean) : RulingAction

    data class Pin(val pinned: Boolean) : RulingAction

    /** 禁言. `null` days is the lifting of one, which the payload marks with `status: false`. */
    data class Suspend(val days: Int?) : RulingAction
}

/**
 * One line of the public moderation log.
 *
 * The site's table splits a single decision across columns; a phone row cannot, so [actions] keeps the
 * compound decision as the separate verbs it is and lets the row join them. Losing that split would
 * make the longest and most informative entries unreadable.
 *
 * [postId] and [floor] are what make a row worth tapping: the log says someone was penalised, and the
 * only useful next question is what for. Both are absent on [RulingTarget.USER] rows.
 */
data class RulingRecord(
    val id: Long,
    val targetName: String,
    val targetUid: Long?,
    val target: RulingTarget,
    val postId: Long?,
    val floor: Int?,
    val reason: String?,
    val actions: List<RulingAction>,
    val moderatorName: String?,
    val createdAtMillis: Long?,
    val kind: RulingKind,
)

data class RulingPage(
    val records: List<RulingRecord>,
    val page: Int,
    val totalPages: Int,
)

interface RulingRepository {
    suspend fun records(page: Int = 1): RulingPage
}

/**
 * 管理记录, read out of the site's own `ruling` bundle on 2026-08-02.
 *
 * `/ruling` renders client-side, so this repository answered [SiteError.NotWired] for as long as
 * the payload behind it was a guess — the same place `/stardust/list` and `/fans` were in before it.
 * The guessing stopped being necessary once the bundle was read: the table is one call to
 * `/api/admin/ruling/page-N` answering `{"success":true,"data":[…],"total":30212}`, and every row's
 * decision is a **JSON document inside a JSON string** under `request`, which the site parses again in
 * its own formatter.
 *
 * `admin` in the path is the endpoint's name, not a permission: any signed-in account reads the log,
 * and the site only shows the extra search box and edit column to `isAdmin` users. Signed out is an
 * HTTP 500 rather than an empty list — see [NodeSeekJsonClient] for where that becomes
 * [SiteError.LoginRequired] — so unlike `/fans` this needs no guard of its own to avoid reporting
 * a signed-out reader an empty log.
 */
class NetworkRulingRepository(
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : RulingRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun records(page: Int): RulingPage {
        // The cap is the server's, not a display choice: page 101 is answered `{"success":false,
        // "message":"max page is 100"}` with an HTTP 200, and page 0 with "wrong page number".
        val requested = page.coerceIn(1, NodeSeekJsonClient.RULING_MAX_PAGES)
        val body =
            jsonSource.getJson(
                path = NodeSeekJsonClient.rulingPagePath(requested),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.rulingPath(requested),
            )
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) as? JsonObject }
                    .getOrElse { throw SiteException(SiteError.Unparsable, it) }
                    ?: throw SiteException(SiteError.Unparsable)
            if (root.bool("success") == false) {
                throw SiteException(SiteError.Unknown, detail = root.text("message"))
            }
            // An empty `data` is a real answer for a page past the end; a missing one means the shape
            // changed, and reporting that as "no decisions" is the lie this screen refused to tell
            // while it was unwired.
            val rows =
                root.findObjectArray("data")
                    ?: throw SiteException(SiteError.Unparsable)
            RulingPage(
                records = rows.mapNotNull { it.toRulingRecord(json) },
                page = requested,
                totalPages = totalPages(root.long("total"), fallback = requested),
            )
        }
    }

    /** `nPage = Math.min(100, Math.ceil(total / 20))`, which is what the site's own paginator computes. */
    private fun totalPages(total: Long?, fallback: Int): Int {
        val rows = total ?: return fallback
        val size = NodeSeekJsonClient.RULING_PAGE_SIZE.toLong()
        val pages = (rows + size - 1) / size
        return pages.coerceIn(1L, NodeSeekJsonClient.RULING_MAX_PAGES.toLong()).toInt()
    }
}

/**
 * A row missing its id is dropped; a row whose `request` will not parse is kept.
 *
 * The two are not the same loss. Without an id there is no stable list key. Without the decision there
 * is still a true statement left — this moderator acted on this member at this time — and the row
 * renders with an empty action list rather than disappearing from a log whose whole purpose is that
 * nothing disappears from it.
 */
private fun JsonObject.toRulingRecord(json: Json): RulingRecord? {
    val id = long("id") ?: return null
    val request =
        text("request")
            ?.let { raw -> runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }
    val postId = long("post_id")?.takeIf { it > 0 }
    val floor = int("floor_index")?.takeIf { it >= 0 }
    val actions = request?.toRulingActions().orEmpty()
    return RulingRecord(
        id = id,
        targetName = text("target_member_name").orEmpty(),
        targetUid = long("target_member_id"),
        target =
        when {
            postId == null -> RulingTarget.USER
            floor == null || floor == 0 -> RulingTarget.POST
            else -> RulingTarget.COMMENT
        },
        postId = postId,
        floor = floor?.takeIf { it > 0 },
        reason = request?.obj("coin")?.text("reason"),
        actions = actions,
        moderatorName = text("admin_member_name"),
        createdAtMillis = TimeFormat.parseTimestamp(text("created_at", "createdAt")),
        kind = actions.kind(),
    )
}

/**
 * The decision, in the order the site's `formatAction` emits it.
 *
 * Order is part of the reading: 因…被扣鸡腿 first, then what happened to the post, then what happened
 * to the account. Sorting these by anything else would make two identical decisions read differently.
 */
private fun JsonObject.toRulingActions(): List<RulingAction> =
    buildList {
        obj("coin")?.int("coin_diff")?.let { add(RulingAction.Coin(it)) }
        obj("stardust")?.int("stardust_diff")?.let { add(RulingAction.Stardust(it)) }
        obj("postSummary")?.let { summary ->
            summary.text("title")?.let { add(RulingAction.Title(it)) }
            summary.text("category")?.let { add(RulingAction.Move(it)) }
            summary.int("rank")?.let { add(RulingAction.ReadRank(it)) }
            summary.bool("locked")?.let { add(RulingAction.Lock(it)) }
            summary.bool("award")?.let { add(RulingAction.Award(it)) }
        }
        obj("hideComment")?.let { hide ->
            add(
                RulingAction.Hide(
                    hidden = hide.bool("status") ?: true,
                    // The site reads the account-wide form off the *target*, not off this block.
                    wholeUser = obj("target")?.long("uid") != null,
                ),
            )
        }
        obj("pinComment")?.let { pin -> add(RulingAction.Pin(pin.bool("status") ?: true)) }
        obj("suspend")?.let { entry ->
            add(RulingAction.Suspend(days = entry.int("value")?.takeIf { entry.bool("status") != false }))
        }
    }

/**
 * Which icon the row leads with.
 *
 * A compound decision has to pick one, so the order is by what a reader scanning the log is looking
 * for: a silencing outranks the 鸡腿 that came with it, and a reward outranks the housekeeping.
 */
private fun List<RulingAction>.kind(): RulingKind =
    when {
        any { it is RulingAction.Suspend && it.days != null } -> RulingKind.BAN
        any { it.isReward() } -> RulingKind.REWARD
        any { it is RulingAction.Move } -> RulingKind.MOVE
        any { it is RulingAction.ReadRank } -> RulingKind.PERMISSION
        else -> RulingKind.PENALTY
    }

private fun RulingAction.isReward(): Boolean =
    when (this) {
        is RulingAction.Coin -> diff > 0
        is RulingAction.Stardust -> diff > 0
        is RulingAction.Award -> award
        else -> false
    }
