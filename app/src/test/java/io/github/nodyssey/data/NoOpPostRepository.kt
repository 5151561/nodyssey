package io.github.nodyssey.data

import androidx.paging.PagingData
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.ReactionAction
import io.github.nodyssey.model.ThreadSnapshot
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A `PostRepository` that answers nothing, for the view models that take one but do not use it.
 *
 * Open rather than an object so a test can override the two or three members it actually cares
 * about; every other member is here so that adding one to the interface does not break six tests
 * that were never about it.
 */
internal open class NoOpPostRepository : PostRepository {
    override fun feed(
        categorySlug: String?,
        sort: FeedSort,
        startPage: Int,
    ): Flow<PagingData<FeedPost>> = emptyFlow()

    override fun feedTotalPages(categorySlug: String?, sort: FeedSort): Flow<Int> = emptyFlow()

    override suspend fun feedRowIndexOfPage(
        categorySlug: String?,
        sort: FeedSort,
        page: Int,
    ): Int? = null

    override fun searchFeed(
        query: String,
        categorySlug: String?,
        sort: FeedSort,
    ): Flow<PagingData<FeedPost>> = emptyFlow()

    override fun search(query: String): Flow<List<FeedPost>> = emptyFlow()

    override suspend fun invalidateCaches() = Unit

    override suspend fun clearSessionData() = Unit

    override suspend fun clearCache(isSignedIn: Boolean, fingerprint: Int) = Unit

    override suspend fun reconcileSession(isSignedIn: Boolean, fingerprint: Int): Boolean = false

    override fun thread(postId: Long): Flow<ThreadSnapshot?> = emptyFlow()

    override suspend fun refreshThread(postId: Long, page: Int) = Unit

    override suspend fun extendThread(postId: Long, page: Int) = Unit

    override suspend fun isThreadFresh(postId: Long): Boolean = false

    override suspend fun hasUnreadReplies(postId: Long): Boolean = false

    override suspend fun cachedPages(postId: Long): IntRange? = null

    override suspend fun markThreadRead(postId: Long) = Unit

    override suspend fun react(postId: Long, commentId: Long, action: ReactionAction) = Unit

    override suspend fun freeChickenLegs(): FreeChickenLegs? = null

    override suspend fun setCollected(postId: Long, collected: Boolean) = Unit

    override fun readHistory(): Flow<List<ReadHistoryEntry>> = emptyFlow()

    override suspend fun removeFromHistory(postId: Long) = Unit

    override suspend fun restoreToHistory(entry: ReadHistoryEntry) = Unit

    override suspend fun trimReadHistory() = Unit

    override suspend fun clearReadHistory() = Unit
}
