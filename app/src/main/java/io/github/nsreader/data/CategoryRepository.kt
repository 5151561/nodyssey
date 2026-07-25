package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Boards come from `/api/content/list-categories` — one of the few endpoints NodeSeek actually
 * serves as JSON. It is authoritative: the hardcoded list in [NodeSeekSite] had already drifted
 * (`meaningless` no longer exists), so the API wins and the static list is only an offline fallback.
 */
class CategoryRepository(
    private val client: NodeSeekJsonClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: List<Board>? = null

    suspend fun loadBoards(): List<Board> {
        cached?.let { return it }
        val boards = runCatching {
            val body = client.getJson(NodeSeekJsonClient.PATH_CATEGORIES)
            json.decodeFromString<CategoriesResponse>(body)
                .takeIf { it.success }
                ?.data
                ?.map { it.toBoard() }
                .orEmpty()
        }.getOrElse { emptyList() }

        val result = listOf(FRONT_PAGE) + boards.ifEmpty { fallbackBoards() }
        cached = result
        return result
    }

    private fun fallbackBoards(): List<Board> =
        NodeSeekSite.categories
            .filter { it.slug != null }
            .map { Board(slug = it.slug, title = it.title, description = null) }

    companion object {
        /** The mixed front page is not a board, so the API never returns it. */
        val FRONT_PAGE = Board(slug = null, title = "综合", description = "全站最新")
    }
}

/** A board tab. `slug == null` is the mixed front page. */
data class Board(
    val slug: String?,
    val title: String,
    val description: String?,
    val adminOnly: Boolean = false,
)

@Serializable
private data class CategoriesResponse(
    val success: Boolean = false,
    val data: List<CategoryDto> = emptyList(),
)

@Serializable
private data class CategoryDto(
    val key: String,
    @SerialName("cn_text") val cnText: String,
    val description: String? = null,
    val adminOnly: Boolean = false,
) {
    fun toBoard() = Board(
        slug = key,
        title = cnText,
        description = description?.ifBlank { null },
        adminOnly = adminOnly,
    )
}
