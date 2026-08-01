package io.github.nodyssey.data

import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException

/*
 * The page NodeSeek renders entirely in the browser.
 *
 * `/ruling` returns an empty shell to a plain GET and builds its table from XHRs whose payload we have
 * not been able to observe. Rather than ship a parser written against a guessed payload — which would
 * fail silently and look like an empty moderation log — the repository here declares its shape, and the
 * default implementation answers [NodeSeekError.NotWired].
 *
 * That keeps three things true at once: the screen is finished and renders real rows the moment an
 * implementation exists, the UiState contract is already the one the site's fields imply, and the user
 * is told the truth instead of being shown a plausible empty list.
 *
 * Two pages used to be here. `/stardust/list` left on 2026-07-30 and `/fans` on 2026-08-02, both once
 * their contract was read out of the site's own bundle instead of guessed — see
 * [NetworkStardustRepository] and [NetworkFollowRepository]. That is the intended exit route for the
 * one below as well.
 */

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

class SiteOnlyRulingRepository : RulingRepository {
    override suspend fun records(page: Int): RulingPage = throw NodeSeekException(NodeSeekError.NotWired)
}
