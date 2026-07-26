package io.github.nsreader.data

import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException

/*
 * The pages NodeSeek renders entirely in the browser.
 *
 * `/fans`, `/stardust/list` and `/ruling` return an empty shell to a plain GET and build their tables
 * from XHRs that are not part of any documented contract; we could not observe them without a signed-in
 * browser session. Rather than ship a parser written against a guessed payload — which would fail
 * silently and look like "you have no followers" — each repository here declares its shape, and the
 * default implementation answers [NodeSeekError.NotWired].
 *
 * That keeps three things true at once: the screens are finished and render real rows the moment an
 * implementation exists, the UiState contracts are already the ones the site's fields imply, and the
 * user is told the truth instead of being shown a plausible empty list.
 */

/** A row of 我的关注 / 我的粉丝. No relationship button: the site has no follow action to offer. */
data class FollowUser(
    val uid: Long,
    val name: String,
    val avatarUrl: String?,
)

interface FollowRepository {
    suspend fun following(page: Int = 1): List<FollowUser>

    suspend fun followers(page: Int = 1): List<FollowUser>
}

/**
 * One stardust movement.
 *
 * Stardust has exactly one source — a comment of yours being liked, +1 a time — and exactly one use,
 * transfer. So a row is the amount, the balance it left behind, and which comment earned it.
 */
data class StardustEntry(
    val amount: Int,
    val balanceAfter: Int?,
    val commentId: Long?,
    val timestampText: String?,
)

interface StardustRepository {
    suspend fun entries(page: Int = 1): List<StardustEntry>
}

/** What a moderation entry did. Drives the leading icon, which is the only scannable part of the row. */
enum class RulingKind {
    PENALTY,
    BAN,
    MOVE,
    PERMISSION,
    REWARD,
}

/**
 * One line of the public moderation log.
 *
 * The site's table splits a single decision across columns; a phone row cannot, so [actions] keeps the
 * compound action ("扣 20 鸡腿" + "移动版块至 促销" + "锁定修改") as the separate verbs it is and lets
 * the row join them. Losing that split would make the longest and most informative entries unreadable.
 */
data class RulingRecord(
    val id: Long,
    val targetName: String,
    val targetKind: String,
    val reason: String?,
    val actions: List<String>,
    val moderatorName: String?,
    val timeText: String?,
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

/** The single place that says "not wired yet", so switching one page to real data is one class. */
private fun notWired(): Nothing = throw NodeSeekException(NodeSeekError.NotWired)

class SiteOnlyFollowRepository : FollowRepository {
    override suspend fun following(page: Int): List<FollowUser> = notWired()

    override suspend fun followers(page: Int): List<FollowUser> = notWired()
}

class SiteOnlyStardustRepository : StardustRepository {
    override suspend fun entries(page: Int): List<StardustEntry> = notWired()
}

class SiteOnlyRulingRepository : RulingRepository {
    override suspend fun records(page: Int): RulingPage = notWired()
}
