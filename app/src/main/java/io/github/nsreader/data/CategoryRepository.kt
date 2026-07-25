package io.github.nsreader.data

import io.github.nsreader.core.NodeSeekSite
import io.github.nsreader.core.runCatchingExceptCancellation
import io.github.nsreader.core.net.JsonSource
import io.github.nsreader.core.net.NodeSeekJsonClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the board list.
 *
 * Boards come from `/api/content/list-categories` — one of the few endpoints NodeSeek serves as
 * JSON. It is authoritative: the hardcoded list in [NodeSeekSite] had already drifted
 * (`meaningless` no longer exists), so the API wins and the static list is only an offline fallback.
 *
 * Consumers **observe** [boards] rather than calling a getter and keeping a copy, so a later
 * refresh reaches every screen without anyone synchronising anything by hand.
 */
class CategoryRepository(
    private val client: JsonSource,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _boards = MutableStateFlow(listOf(FRONT_PAGE))
    val boards: StateFlow<List<Board>> = _boards.asStateFlow()

    private val refreshMutex = Mutex()
    private var loadedFromNetwork = false

    /** Idempotent: concurrent callers collapse into one request, and a success is not re-fetched. */
    suspend fun refreshIfNeeded() {
        if (loadedFromNetwork) return
        refreshMutex.withLock {
            if (loadedFromNetwork) return
            val remote = runCatchingExceptCancellation {
                val body = client.getJson(NodeSeekJsonClient.PATH_CATEGORIES)
                json.decodeFromString<CategoriesResponse>(body)
                    .takeIf { it.success }
                    ?.data
                    ?.map { it.toBoard() }
                    .orEmpty()
            }.getOrElse { emptyList() }

            if (remote.isNotEmpty()) {
                _boards.value = listOf(FRONT_PAGE) + remote
                loadedFromNetwork = true
            } else {
                _boards.value = listOf(FRONT_PAGE) + fallbackBoards()
            }
        }
    }

    private fun fallbackBoards(): List<Board> =
        NodeSeekSite.categories
            .filter { it.slug != null }
            .map { Board(slug = it.slug, title = it.title, description = null) }

    companion object {
        /** The mixed front page is not a board, so the API never returns it. */
        val FRONT_PAGE = Board(slug = null, title = "综合", description = null)
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
