package io.github.nodyssey.data

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
    /** The site's own reply count as last stated, never a count of what happens to be stored. */
    val commentCount: Int? = null,
    val createdAtText: String? = null,
)

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

    /** Fills in whatever these carry, leaving every field they are silent about as it was. */
    suspend fun remember(metas: List<CollectedPostMeta>)

    suspend fun remember(meta: CollectedPostMeta) = remember(listOf(meta))
}

class RoomCollectedPostMetaStore(
    private val dao: CollectedPostMetaDao,
    private val clock: AppClock,
) : CollectedPostMetaStore {
    override fun observe(): Flow<Map<Long, CollectedPostMeta>> =
        dao.observeAll().map { rows -> rows.associate { it.postId to it.toMeta() } }

    override suspend fun remember(metas: List<CollectedPostMeta>) {
        if (metas.isEmpty()) return
        dao.remember(metas.map { it.toEntity(clock.nowMillis()) }, clock.nowMillis())
        dao.trimTo(MAX_REMEMBERED)
    }

    companion object {
        /**
         * How many threads' details are worth keeping.
         *
         * Far above any collection the screen will ever list — it walks 20 pages at most — and the
         * point is only that a device which has collected and un-collected for years has a bound.
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
        commentCount = commentCount,
        createdAtText = createdAtText,
        updatedAtMillis = nowMillis,
    )

/** True when this carries nothing worth a write — every source drew a blank. */
internal val CollectedPostMeta.isEmpty: Boolean
    get() =
        title == null &&
            categoryTitle == null &&
            categorySlug == null &&
            authorName == null &&
            commentCount == null &&
            createdAtText == null
