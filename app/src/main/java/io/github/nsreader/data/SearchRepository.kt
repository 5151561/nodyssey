package io.github.nsreader.data

import io.github.nsreader.core.AppDispatchers
import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.html.SearchParser
import io.github.nsreader.core.net.HtmlSource
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import io.github.nsreader.model.FeedSort
import io.github.nsreader.model.PostSummary
import io.github.nsreader.model.SearchSort
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UserSearchResult(
    val uid: Long,
    val name: String,
    val avatarUrl: String?,
    val level: Int?,
    val bio: String?,
    val joinedText: String?,
    val topicCount: Int?,
    val commentCount: Int?,
)

interface SearchRepository {
    suspend fun searchPosts(
        query: String,
        categorySlugs: Set<String>,
        sort: SearchSort,
    ): List<PostSummary>

    suspend fun searchUsers(query: String): List<UserSearchResult>
}

class NetworkSearchRepository(
    private val htmlSource: HtmlSource,
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : SearchRepository {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun searchPosts(
        query: String,
        categorySlugs: Set<String>,
        sort: SearchSort,
    ): List<PostSummary> = coroutineScope {
        // NodeSeek accepts one category per request. A single selected board can therefore be sent
        // to the server directly; for a multi-board range, search once globally and filter the real
        // server result set locally instead of multiplying one search into dozens of requests.
        val serverCategory = categorySlugs.singleOrNull()
        loadPostScope(query, serverCategory, sort)
            .filter { post -> categorySlugs.isEmpty() || post.categorySlug in categorySlugs }
            .distinctBy(PostSummary::postId)
            .let { posts ->
                if (sort == SearchSort.RELEVANCE) {
                    posts.sortedByDescending { it.relevanceFor(query) }
                } else {
                    posts.sortedByDescending(PostSummary::postId)
                }
            }
    }

    override suspend fun searchUsers(query: String): List<UserSearchResult> {
        val normalized = query.trim()
        val body =
            jsonSource.getJson(
                path = NodeSeekSite.userSearchApiPath(normalized),
                referer = NodeSeekSite.BASE_URL + NodeSeekSite.userSearchPath(normalized),
            )
        return withContext(dispatchers.default) {
            val response =
                runCatching { json.decodeFromString<UserSearchResponse>(body) }
                    .getOrElse { throw NodeSeekException(NodeSeekError.Unparsable, it) }
            if (!response.success) throw NodeSeekException(NodeSeekError.LoginRequired)
            response.memberList.map(UserSearchDto::toResult)
        }
    }

    private suspend fun loadPostScope(
        query: String,
        categorySlug: String?,
        sort: SearchSort,
    ): List<PostSummary> = coroutineScope {
        val feedSort = if (sort == SearchSort.TIME) FeedSort.POST_TIME else FeedSort.LAST_REPLY
        val firstHtml =
            htmlSource.getHtml(
                NodeSeekSite.postSearchPath(query.trim(), categorySlug = categorySlug, sort = feedSort),
            )
        val first = withContext(dispatchers.default) { SearchParser.parsePosts(firstHtml, page = 1) }
        if (first.totalPages == 1) return@coroutineScope first.posts

        val remaining =
            (2..first.totalPages)
                .map { page ->
                    async {
                        val html = htmlSource.getHtml(NodeSeekSite.postSearchPath(query, page, categorySlug, feedSort))
                        withContext(dispatchers.default) { SearchParser.parsePosts(html, page).posts }
                    }
                }.awaitAll()
        first.posts + remaining.flatten()
    }
}

private fun PostSummary.relevanceFor(query: String): Int {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return 0
    val titleText = title.lowercase()
    val authorText = authorName.lowercase()
    return when {
        titleText == normalized -> 1_000
        titleText.startsWith(normalized) -> 800
        titleText.contains(normalized) -> 600
        authorText == normalized -> 500
        authorText.contains(normalized) -> 300
        else -> normalized.split(Regex("\\s+")).count(titleText::contains) * 100
    }
}

@Serializable
private data class UserSearchResponse(
    val success: Boolean = false,
    @SerialName("memberList") val memberList: List<UserSearchDto> = emptyList(),
)

@Serializable
private data class UserSearchDto(
    @SerialName("member_id") val uid: Long,
    @SerialName("member_name") val name: String,
    val rank: Int? = null,
    val bio: String? = null,
    @SerialName("created_at_str") val joinedText: String? = null,
    @SerialName("nPost") val topicCount: Int? = null,
    @SerialName("nComment") val commentCount: Int? = null,
) {
    fun toResult() =
        UserSearchResult(
            uid = uid,
            name = name,
            avatarUrl = NodeSeekSite.avatarUrl(uid),
            level = rank,
            bio = bio?.trim()?.ifBlank { null },
            joinedText = joinedText?.trim()?.ifBlank { null },
            topicCount = topicCount,
            commentCount = commentCount,
        )
}
