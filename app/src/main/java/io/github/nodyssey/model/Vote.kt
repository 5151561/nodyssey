package io.github.nodyssey.model

/**
 * A vote as the server currently describes it.
 *
 * Kept out of `Models.kt` deliberately. Everything there is `@Serializable` with an explicit short
 * `@SerialName`, because those types are stored as JSON inside Room rows that outlive the install.
 * A vote is never stored: the post body holds only [RichNode.VotePlaceholder]'s id, and the vote
 * itself is fetched fresh every time because it is per-account server state. Putting it next to the
 * durable types would invite the next reader to assume it carries the same compatibility promises.
 */
data class Vote(
    val id: Long,
    val title: String,
    /** Who opened it. With [isAdmin] this is what decides whether the manage menu appears. */
    val ownerUid: Long,
    /** Public votes name their voters; anonymous ones do not, and have no voter list to fetch. */
    val isPublic: Boolean,
    val locked: Boolean,
    val multiple: Boolean,
    val items: List<VoteItem>,
)

/**
 * One option.
 *
 * [count] being nullable is the point of this type. The site hides results until this account has
 * voted: before that the `items[]` entries carry no `count` key at all. Rendering a missing count as
 * zero would say "nobody picked this", which is a claim the server never made and, on a vote with
 * hundreds of participants, a false one.
 */
data class VoteItem(
    val itemId: Long,
    val text: String,
    val voted: Boolean,
    val count: Int? = null,
    /** The first page of voters, on a public vote whose results this account can see. */
    val voters: List<Long> = emptyList(),
)

/** This account has already voted, which is also the only state in which results exist. */
val Vote.hasVoted: Boolean get() = items.any(VoteItem::voted)

/** Total votes cast, or null if any option is still withholding its count — no total, no percentages. */
val Vote.totalCount: Int?
    get() {
        var sum = 0
        items.forEach { item -> sum += item.count ?: return null }
        return sum
    }

/**
 * Whether [selfUid] may open the manage menu at all.
 *
 * Matching the site: the owner or a moderator. What the menu then *offers* differs between the two —
 * the owner can only lock, while unlocking and deleting are moderator-only.
 */
fun Vote.canManage(
    selfUid: Long?,
    isAdmin: Boolean,
): Boolean = isAdmin || (selfUid != null && selfUid == ownerUid)
