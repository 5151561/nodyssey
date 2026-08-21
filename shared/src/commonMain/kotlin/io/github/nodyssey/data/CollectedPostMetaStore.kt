package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.local.CollectedPostMetaDao
import io.github.nodyssey.data.local.CollectedPostMetaEntity
import io.github.plaza.core.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the app has been told about one collected thread, over and above its id and title.
 *
 * Every field is nullable and null always means "nobody has said", never "empty". A source that
 * cannot answer leaves the field alone rather than blanking what another source already filled in.
 */
data class CollectedPostMeta(
    val postId: Long,
    val title: String? = null,
    val categoryTitle: String? = null,
    val categorySlug: String? = null,
    val authorName: String? = null,
    /** See [CollectedPostMetaEntity.avatarUrl] — the page's own URL, not one built from the uid. */
    val avatarUrl: String? = null,
    val authorUid: Long? = null,
    /** The site's own reply count as last stated, never a count of what happens to be stored. */
    val commentCount: Int? = null,
    val createdAtText: String? = null,
) {
    /**
     * Where to ask for the author's picture, or null when nothing here names the author.
     *
     * The page's own URL first, because that is the one an offline download stored a file under and
     * therefore the one that resolves with the network off. `/avatar/<uid>.png` is the fallback and
     * is not a guess — it is the address this site serves every account's picture from, uploaded or
     * generated, which is what the rest of the app already builds avatars out of.
     */
    val resolvedAvatarUrl: String?
        get() = avatarUrl ?: authorUid?.let(NodeSeekSite::avatarUrl)
}

/**
 * The 收藏 list's local memory of what the site has said about the threads on it.
 *
 * It exists because `list-collection` answers with a title and little else — no board, no author,
 * no reply count — so a row built from that endpoint alone is a bare headline where the same
 * `ThreadRow` on the front page carries four pieces of information. Everything the app *has* been
 * told about those threads it was told somewhere else: on the feed the thread was scrolled past on,
 * on the page opened to press the star, in the pages an offline download fetched.
 *
 * So the rule is: whenever something learns a fact about a thread this account has collected, it
 * says so here. The list then draws the site's own answer where there is one and this where there
 * is not — which is the same shape as the rest of the app's caching, and never a made-up value.
 */
interface CollectedPostMetaStore {
    fun observe(): Flow<Map<Long, CollectedPostMeta>>

    /**
     * The collection itself, in collection order, as of the last complete walk of the endpoint.
     *
     * The 收藏 list reads from here rather than from the request that filled it, which is what makes
     * the screen open onto content with the network off. Empty until a walk has recorded one — a
     * device that has never listed the collection has no honest answer, and 「还没有收藏」 with a
     * retry is a better one than a list assembled out of whatever else happens to be remembered.
     */
    fun observeCollection(): Flow<List<CollectedPostMeta>>

    /** Fills in whatever these carry, leaving every field they are silent about as it was. */
    suspend fun remember(metas: List<CollectedPostMeta>)

    suspend fun remember(meta: CollectedPostMeta) = remember(listOf(meta))

    /**
     * Records a complete walk of the collection endpoint as *the* collection, in the given order.
     *
     * Wholesale: threads no longer in [metas] come off the list, because a walk that did not return
     * one is the only way this device hears that it was un-collected somewhere else. Their details
     * stay — see the DAO — so re-collecting one does not cost the row what it knew.
     */
    suspend fun rememberCollection(metas: List<CollectedPostMeta>)

    /** Takes threads off the list, for the moment this account un-collects one. */
    suspend fun forget(postIds: Collection<Long>)

    /**
     * Makes exactly these the list, in this order — 撤销 for [forget].
     *
     * 收藏 removes rows before the site has answered, and that write lands on disk now. A refusal
     * has to be undone here rather than by reloading, because the refusal that matters most is the
     * one that says there is no network to reload from.
     */
    suspend fun relist(postIds: List<Long>)
}

class RoomCollectedPostMetaStore(
    private val dao: CollectedPostMetaDao,
    private val clock: AppClock,
) : CollectedPostMetaStore {
    override fun observe(): Flow<Map<Long, CollectedPostMeta>> =
        dao.observeAll().map { rows -> rows.associate { it.postId to it.toMeta() } }

    override fun observeCollection(): Flow<List<CollectedPostMeta>> =
        dao.observeCollection().map { rows -> rows.map { it.toMeta() } }

    override suspend fun remember(metas: List<CollectedPostMeta>) {
        if (metas.isEmpty()) return
        dao.remember(metas.map { it.toEntity(clock.nowMillis()) }, clock.nowMillis())
        dao.trimTo(MAX_REMEMBERED)
    }

    override suspend fun rememberCollection(metas: List<CollectedPostMeta>) {
        val now = clock.nowMillis()
        // Not short-circuited on empty: a collection emptied on another device is a fact this walk
        // has just established, and returning early here would leave the old list on screen forever.
        dao.replaceCollection(metas.map { it.toEntity(now) }, now)
        dao.trimTo(MAX_REMEMBERED)
    }

    override suspend fun forget(postIds: Collection<Long>) {
        if (postIds.isEmpty()) return
        dao.unlist(postIds.toList())
    }

    override suspend fun relist(postIds: List<Long>) = dao.replaceOrder(postIds)

    companion object {
        /**
         * How many *off-list* threads' details are worth keeping.
         *
         * The collection itself is not counted against this and never trimmed — those rows are the
         * list 收藏 draws, and evicting one would take a thread out of the collection on this device
         * alone. The bound is for what is left behind: a device which has collected and un-collected
         * for years accumulates rows nobody will list again, and this is where they stop.
         */
        const val MAX_REMEMBERED = 2_000
    }
}

private fun CollectedPostMetaEntity.toMeta() =
    CollectedPostMeta(
        postId = postId,
        title = title,
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        authorName = authorName,
        avatarUrl = avatarUrl,
        authorUid = authorUid,
        commentCount = commentCount,
        createdAtText = createdAtText,
    )

private fun CollectedPostMeta.toEntity(nowMillis: Long) =
    CollectedPostMetaEntity(
        postId = postId,
        title = title,
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        authorName = authorName,
        avatarUrl = avatarUrl,
        authorUid = authorUid,
        commentCount = commentCount,
        createdAtText = createdAtText,
        updatedAtMillis = nowMillis,
    )

/** True when this carries nothing worth a write — every source drew a blank. */
val CollectedPostMeta.isEmpty: Boolean
    get() =
        title == null &&
            categoryTitle == null &&
            categorySlug == null &&
            authorName == null &&
            avatarUrl == null &&
            authorUid == null &&
            commentCount == null &&
            createdAtText == null
