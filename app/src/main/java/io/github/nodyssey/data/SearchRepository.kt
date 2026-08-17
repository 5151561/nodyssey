package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.HtmlSource
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
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

/**
 * Member lookup, and only member lookup.
 *
 * Post search used to live here too, with a fetch-and-page implementation of its own. It is now
 * [PostRepository.searchFeed] — `/search?q=…` is a board listing at another route, so it belongs on
 * the pipeline that already knows how to read one. This half stays separate because it genuinely is
 * different: `/api/account/find/…` is real JSON, unpaged, and answers in one request.
 */
interface SearchRepository {
    suspend fun searchUsers(query: String): List<UserSearchResult>

    /**
     * Resolves an exact `@mention` username to its uid via the site's own `/member?t=` redirect —
     * the same lookup the site performs when that link is clicked. [searchUsers] cannot stand in for
     * this: it is a substring search capped to a page of results, so a short or common name (`xy`,
     * `cloud`) can rank outside that page even though the site resolves it without any ambiguity.
     */
    suspend fun resolveMemberUid(name: String): Long?
}

class NetworkSearchRepository(
    private val jsonSource: JsonSource,
    private val htmlSource: HtmlSource,
    private val dispatchers: AppDispatchers,
) : SearchRepository {
    private val json = Json { ignoreUnknownKeys = true }

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
                    .getOrElse { throw SiteException(SiteError.Unparsable, it) }
            if (!response.success) throw SiteException(SiteError.LoginRequired)
            response.memberList.map(UserSearchDto::toResult)
        }
    }

    override suspend fun resolveMemberUid(name: String): Long? {
        val redirected = htmlSource.resolveRedirect(NodeSeekSite.memberMentionPath(name))
        return redirected?.let(NodeSeekSite::parseUid)
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
