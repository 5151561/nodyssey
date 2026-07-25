package io.github.nsreader.data

import io.github.nsreader.model.PostDetail
import io.github.nsreader.model.PostListPage
import io.github.nsreader.model.PostSummary
import kotlinx.coroutines.CompletableDeferred

/**
 * Test double for [PostRepository].
 *
 * [gate] lets a test hold a response open, which is the only way to reproduce ordering bugs such as
 * a slow board-A response landing after the user switched to board B.
 */
class FakePostRepository : PostRepository {

    var listResult: (String?, Int) -> PostListPage = { slug, page ->
        PostListPage(posts = listOf(post(slug, page)), page = page, hasNextPage = true)
    }

    var listError: Throwable? = null

    /** When set, `loadList` suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    val requestedSlugs = mutableListOf<String?>()

    override suspend fun loadList(categorySlug: String?, page: Int): PostListPage {
        requestedSlugs += categorySlug
        gate?.await()
        listError?.let { throw it }
        return listResult(categorySlug, page)
    }

    override suspend fun loadDetail(postId: Long, page: Int): PostDetail =
        throw NotImplementedError("not used")

    companion object {
        fun post(slug: String?, page: Int) = PostSummary(
            postId = "${slug.orEmpty()}$page".hashCode().toLong() and 0xffffff,
            title = "post from ${slug ?: "front"} page $page",
            authorName = "tester",
            authorUid = 1,
            avatarUrl = null,
            categoryTitle = slug,
            categorySlug = slug,
            viewCount = 1,
            commentCount = 0,
            lastActiveText = "1s ago",
            lastActiveTitle = null,
        )
    }
}
