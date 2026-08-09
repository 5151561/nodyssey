package io.github.nodyssey.data

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup

/** A thread as it appears in a space tab: the user's own topics, or a row of their collections. */
data class SpacePost(
    val postId: Long,
    val title: String,
    val categoryTitle: String?,
    val categorySlug: String?,
    val authorName: String?,
    val commentCount: Int?,
    val viewCount: Int?,
    val createdAtText: String?,
)

/** One of the user's comments, with enough of the thread around it to be worth tapping. */
data class SpaceComment(
    val postId: Long,
    val commentId: Long?,
    val postTitle: String?,
    val excerpt: String,
    val createdAtText: String?,
    /** Site floor format ("#3"), so tapping the row can land on the comment, not just the thread. */
    val floor: String? = null,
)

data class SpacePage<T>(
    val items: List<T>,
    val page: Int,
    val hasNextPage: Boolean,
)

/**
 * The three lists behind a space page's tabs.
 *
 * These are the site's own XHR endpoints rather than scraped HTML, because the space page renders
 * client-side: fetching `/space/12` returns an empty shell. The payload shapes are undocumented, so
 * every field is read by candidate name (see [text]) and a response that yields no rows at all is
 * reported as [SiteError.Unparsable] — the screen then offers the web page, which is the truth we
 * can still show.
 */
interface UserSpaceRepository {
    suspend fun topics(uid: Long, page: Int): SpacePage<SpacePost>

    suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment>

    /** Only the signed-in user's: the site exposes no one else's collections. */
    suspend fun collections(page: Int): SpacePage<SpacePost>
}

class NetworkUserSpaceRepository(
    private val jsonSource: JsonSource,
    private val dispatchers: AppDispatchers,
) : UserSpaceRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun topics(uid: Long, page: Int): SpacePage<SpacePost> =
        load(
            path = NodeSeekJsonClient.discussionListPath(uid, page),
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.spaceTabPath(uid, NodeSeekSite.SPACE_TAB_DISCUSSIONS),
            page = page,
            arrayNames = arrayOf("discussions", "postList", "list", "data"),
            map = JsonObject::toSpacePost,
        )

    override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> =
        load(
            path = NodeSeekJsonClient.commentListPath(uid, page),
            referer = NodeSeekSite.BASE_URL + NodeSeekSite.spaceTabPath(uid, NodeSeekSite.SPACE_TAB_COMMENTS),
            page = page,
            arrayNames = arrayOf("comments", "commentList", "list", "data"),
            map = JsonObject::toSpaceComment,
        )

    override suspend fun collections(page: Int): SpacePage<SpacePost> =
        load(
            path = NodeSeekJsonClient.collectionListPath(page),
            referer = NodeSeekSite.BASE_URL + "/",
            page = page,
            arrayNames = arrayOf("collections", "collectionList", "list", "data"),
            map = JsonObject::toSpacePost,
        )

    private suspend fun <T> load(
        path: String,
        referer: String,
        page: Int,
        arrayNames: Array<String>,
        map: (JsonObject) -> T?,
    ): SpacePage<T> {
        val body = jsonSource.getJson(path = path, referer = referer)
        return withContext(dispatchers.default) {
            val root =
                runCatching { json.parseToJsonElement(body) }
                    .getOrElse { throw SiteException(SiteError.Unparsable, it) }
            val rows = root.findObjectArray(*arrayNames)
            // An empty array is a real answer ("no topics yet"); no array at all means we guessed the
            // payload shape wrong, and pretending that is an empty list would hide the mismatch.
            if (rows == null) throw SiteException(SiteError.Unparsable)
            val items = rows.mapNotNull(map)
            SpacePage(
                items = items,
                page = page,
                hasNextPage = (root as? JsonObject)?.hasNextPage(page, rows.size) ?: false,
            )
        }
    }
}

/**
 * Whether asking for the next page is worth a request.
 *
 * Explicit paging metadata when the payload carries it; otherwise any non-empty page is treated as
 * "probably more". Guessing from row count would hide every later page the moment the endpoint's real
 * page size dips below the guess, so the fallback instead costs one wasted request at the end of a
 * list and never hides a page.
 */
private fun JsonObject.hasNextPage(page: Int, rowCount: Int): Boolean {
    pagingBool("hasNext", "has_next", "hasMore", "has_more")?.let { return it }
    pagingInt("totalPage", "total_page", "totalPages", "pages")?.let { return page < it }
    return rowCount > 0
}

/** The paging fields ride either on the payload root or inside its envelope (`data`, `result`). */
private fun JsonObject.pagingBool(vararg names: String): Boolean? {
    bool(*names)?.let { return it }
    values.forEach { child -> (child as? JsonObject)?.bool(*names)?.let { return it } }
    return null
}

private fun JsonObject.pagingInt(vararg names: String): Int? {
    int(*names)?.let { return it }
    values.forEach { child -> (child as? JsonObject)?.int(*names)?.let { return it } }
    return null
}

private const val COMMENT_EXCERPT_LENGTH = 140

private fun JsonObject.toSpacePost(): SpacePost? {
    val postId = long("post_id", "postId", "pid", "id") ?: return null
    val title = text("title", "post_title", "subject") ?: return null
    return SpacePost(
        postId = postId,
        title = title,
        categoryTitle = text("category_title", "categoryTitle", "category_name"),
        categorySlug = text("category", "category_slug", "categorySlug"),
        authorName = text("member_name", "author", "username", "user_name"),
        commentCount = int("comments", "comment_count", "nComment", "reply_count"),
        viewCount = int("views", "view_count", "click", "nView"),
        createdAtText = text("created_at_str", "created_at", "createdAt", "time", "date"),
    )
}

private fun JsonObject.toSpaceComment(): SpaceComment? {
    val postId = long("post_id", "postId", "pid") ?: return null
    // The live payload calls the body `text` (verified 2026-07); the rest are defensive.
    val raw = text("text", "content", "comment", "body", "excerpt").orEmpty()
    return SpaceComment(
        postId = postId,
        commentId = long("comment_id", "commentId", "id"),
        postTitle = text("post_title", "title", "subject"),
        // Comments come back as the site's rendered markup. Rendering it properly is the detail
        // screen's job; here it has to fit one line, so it is flattened to text.
        excerpt = Jsoup.parse(raw).text().trim().take(COMMENT_EXCERPT_LENGTH),
        createdAtText = text("created_at_str", "created_at", "createdAt", "time", "date"),
        floor = long("floor_id", "floorId", "floor")?.let { "#$it" },
    )
}
