package io.github.nodyssey.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostDetail
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.RichNode
import io.github.plaza.core.AppClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import java.io.File
import java.nio.file.Files

/**
 * An in-memory database on the real Room implementation.
 *
 * Robolectric rather than a fake DAO on purpose: the interesting logic in phase two is *SQL* — a
 * three-table join, an upsert that must not reorder, a cascading delete. A hand-written fake would
 * only prove that the fake works.
 *
 * Pass [dispatcher] when the test asserts on state that a Room `Flow` pushed, rather than awaiting
 * the flow itself. Room delivers those emissions on its own query executor, so without this
 * `advanceUntilIdle()` returns before the observer has run and the assertion sees an empty state.
 */
internal fun inMemoryDatabase(dispatcher: CoroutineDispatcher? = null): NodeSeekDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NodeSeekDatabase::class.java,
        ).apply {
            if (dispatcher != null) {
                val executor = dispatcher.asExecutor()
                setQueryExecutor(executor)
                setTransactionExecutor(executor)
                // Room's main-thread assertion has nothing to say here: the test dispatcher *is* the main
                // dispatcher, which is the whole point of running everything on one deterministic thread.
                // Production code is still held to the rule — nothing calls this outside tests.
                allowMainThreadQueries()
            }
        }.build()

/** A clock the test moves by hand, so cache-expiry logic can be tested without sleeping. */
internal class MutableClock(
    var nowMillis: Long = 1_000_000L,
) : AppClock {
    override fun nowMillis(): Long = nowMillis

    fun advanceBy(millis: Long) {
        nowMillis += millis
    }
}

/**
 * Test double for the network side.
 *
 * [gate] holds a response open, which is the only way to reproduce ordering and cancellation bugs
 * such as a slow board-A response landing after the user switched to board B.
 */
internal class FakePostRemoteDataSource : PostRemoteDataSource {
    var listResult: (String?, Int) -> PostListPage = { slug, page ->
        PostListPage(posts = listOf(summary(slug, page)), page = page, hasNextPage = true)
    }

    var detailResult: (Long, Int) -> PostDetail = { postId, page ->
        detail(postId, page)
    }

    var listError: Throwable? = null
    var detailError: Throwable? = null

    /** When set, both loaders suspend until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    /** Search answers with [listResult] too, unless a test overrides it. */
    var searchResult: ((SearchRequest) -> PostListPage)? = null

    val listRequests = mutableListOf<Pair<String?, Int>>()
    val sortRequests = mutableListOf<FeedSort>()
    val searchRequests = mutableListOf<SearchRequest>()
    val detailRequests = mutableListOf<Pair<Long, Int>>()

    /** What one `/search` call was asked for, so a test can assert the whole shape at once. */
    data class SearchRequest(
        val query: String,
        val page: Int,
        val categorySlug: String?,
        val sort: FeedSort,
    )

    override suspend fun loadList(
        categorySlug: String?,
        page: Int,
        sort: FeedSort,
    ): PostListPage {
        listRequests += categorySlug to page
        sortRequests += sort
        gate?.await()
        listError?.let { throw it }
        return listResult(categorySlug, page)
    }

    override suspend fun loadSearch(
        query: String,
        page: Int,
        categorySlug: String?,
        sort: FeedSort,
    ): PostListPage {
        val request = SearchRequest(query, page, categorySlug, sort)
        searchRequests += request
        gate?.await()
        listError?.let { throw it }
        return searchResult?.invoke(request) ?: listResult(categorySlug, page)
    }

    override suspend fun loadDetail(
        postId: Long,
        page: Int,
    ): PostDetail {
        detailRequests += postId to page
        gate?.await()
        detailError?.let { throw it }
        return detailResult(postId, page)
    }

    companion object {
        /** A page of [count] posts whose ids start at [firstId], so pages never overlap by accident. */
        fun page(
            slug: String?,
            page: Int,
            firstId: Long,
            count: Int,
            hasNextPage: Boolean = true,
            /** What the site's pager claims; defaults to "this page is the only one", as the model does. */
            totalPages: Int = page,
        ) = PostListPage(
            posts =
            (0 until count).map { offset ->
                summary(slug, page).copy(
                    postId = firstId + offset,
                    title = "post ${firstId + offset}",
                )
            },
            page = page,
            hasNextPage = hasNextPage,
            totalPages = totalPages,
        )

        fun summary(
            slug: String?,
            page: Int,
            commentCount: Int = 0,
        ) = PostSummary(
            postId = "${slug.orEmpty()}$page".hashCode().toLong() and 0xffffff,
            title = "post from ${slug ?: "front"} page $page",
            authorName = "tester",
            authorUid = 1,
            avatarUrl = null,
            categoryTitle = slug,
            categorySlug = slug,
            viewCount = 1,
            commentCount = commentCount,
            lastActiveText = "1s ago",
            lastActiveTitle = null,
        )

        fun detail(
            postId: Long,
            page: Int,
            body: PostContent? = content("body of $postId"),
            commentCount: Int = 2,
            totalPages: Int = 1,
            collected: Boolean? = null,
            collectionCount: Int? = null,
        ) = PostDetail(
            postId = postId,
            title = "thread $postId",
            body = body,
            comments = (0 until commentCount).map { content("page $page comment $it") },
            page = page,
            totalPages = totalPages,
            hasNextPage = page < totalPages,
            collected = collected,
            collectionCount = collectionCount,
        )

        fun content(text: String) =
            PostContent(
                commentId = text.hashCode().toLong(),
                floor = null,
                authorName = "tester",
                authorUid = 1,
                avatarUrl = null,
                isOriginalPoster = false,
                badges = emptyList(),
                createdAtText = null,
                createdAtTitle = null,
                categoryTitle = null,
                nodes = listOf(RichNode.CodeBlock(text, language = null)),
            )
    }
}

/**
 * A [SettingsRepository] on a real DataStore in a throwaway directory.
 *
 * The real store rather than an interface fake, for the same reason [inMemoryDatabase] uses real
 * Room: the behaviour worth testing is the encoding — a set that round-trips through one delimited
 * string — and a fake would only prove the fake encodes correctly.
 *
 * [scope] should be a test's `backgroundScope` so the store's collector is cancelled with the test.
 */
internal fun testSettingsRepository(scope: CoroutineScope): SettingsRepository {
    val directory = Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }
    return SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") },
    )
}
