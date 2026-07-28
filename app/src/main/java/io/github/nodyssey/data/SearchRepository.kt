package io.github.nodyssey.data

import io.github.nodyssey.core.AppDispatchers
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.html.SearchParser
import io.github.nodyssey.core.net.HtmlSource
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.core.net.NodeSeekException
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.SearchSort
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

/** One server page of post results, so the caller can page on demand instead of prefetching. */
data class PostSearchResults(
    val posts: List<PostSummary>,
    val page: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

interface SearchRepository {
    suspend fun searchPosts(
        query: String,
        page: Int,
        categorySlugs: Set<String>,
        sort: SearchSort,
    ): PostSearchResults

    suspend fun searchUsers(query: String): List<UserSearchResult>
}

class NetworkSearchRepository(
    private val htmlSource: HtmlSource,
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : SearchRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /*
     * Exactly one request per call, exactly the site's own search route. Prefetching every result
     * page in parallel was what tripped Cloudflare's rate limiting: a popular keyword turned one
     * search into dozens of simultaneous requests. Ordering is the server's — its relevance ranking
     * for RELEVANCE, `sortBy=postTime` for TIME — so pages append without reshuffling.
     */
    override suspend fun searchPosts(
        query: String,
        page: Int,
        categorySlugs: Set<String>,
        sort: SearchSort,
    ): PostSearchResults {
        val feedSort = if (sort == SearchSort.TIME) FeedSort.POST_TIME else FeedSort.LAST_REPLY
        // NodeSeek accepts one category per request. A single selected board goes to the server;
        // a multi-board range searches globally and filters each page locally as it arrives.
        val serverCategory = categorySlugs.singleOrNull()
        val html =
            htmlSource.getHtml(
                NodeSeekSite.postSearchPath(query.trim(), page, serverCategory, feedSort),
            )
        val parsed = withContext(dispatchers.default) { SearchParser.parsePosts(html, page) }
        return PostSearchResults(
            posts =
            parsed.posts
                .filter { post -> categorySlugs.size < 2 || post.categorySlug in categorySlugs }
                .distinctBy(PostSummary::postId),
            page = page,
            totalPages = parsed.totalPages,
            hasNextPage = parsed.hasNextPage,
        )
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
