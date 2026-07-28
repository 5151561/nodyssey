package io.github.nodyssey.data

import io.github.nodyssey.core.AppClock
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.core.runCatchingExceptCancellation
import io.github.nodyssey.data.local.BoardDao
import io.github.nodyssey.data.local.toBoard
import io.github.nodyssey.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
 * (`meaningless` no longer exists), so the API wins and the static list is only a first-run fallback.
 *
 * The truth now lives in Room rather than in a `StateFlow` field, so on a cold offline start the tab
 * strip is populated before the first frame instead of appearing one request later.
 */
class CategoryRepository(
    private val client: JsonSource,
    private val boardDao: BoardDao,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The front page is prepended here rather than stored.
     *
     * It is not a board — the API never returns it and it has no slug — so persisting it would mean
     * inventing a row that every query then has to filter back out.
     */
    val boards: Flow<List<Board>> =
        boardDao.observeBoards().map { entities ->
            listOf(FRONT_PAGE) + entities.map { it.toBoard() }
        }

    private val refreshMutex = Mutex()
    private var lastRefreshedAtMillis = 0L

    /** Idempotent: concurrent callers collapse into one request, and a fresh list is not re-fetched. */
    suspend fun refreshIfNeeded() {
        if (isFresh()) return
        refreshMutex.withLock {
            if (isFresh()) return
            val remote =
                runCatchingExceptCancellation {
                    val body = client.getJson(NodeSeekJsonClient.PATH_CATEGORIES)
                    json
                        .decodeFromString<CategoriesResponse>(body)
                        .takeIf { it.success }
                        ?.data
                        ?.map { it.toBoard() }
                        .orEmpty()
                }.getOrElse { emptyList() }

            when {
                remote.isNotEmpty() -> {
                    boardDao.replaceAll(remote.mapIndexed { index, board -> board.toEntity(index) })
                    lastRefreshedAtMillis = clock.nowMillis()
                }

                // A failed refresh must not blank out a list that already works offline. The static
                // fallback is only for a database that has never been filled.
                boardDao.count() == 0 -> {
                    boardDao.replaceAll(
                        fallbackBoards().mapIndexed { index, board -> board.toEntity(index) },
                    )
                }
            }
        }
    }

    private fun isFresh(): Boolean = lastRefreshedAtMillis != 0L && clock.nowMillis() - lastRefreshedAtMillis < CACHE_TTL_MILLIS

    private fun fallbackBoards(): List<Board> =
        NodeSeekSite.categories
            .filter { it.slug != null }
            .map { Board(slug = it.slug, title = it.title, description = null) }

    companion object {
        /** The mixed front page is not a board, so the API never returns it. */
        val FRONT_PAGE = Board(slug = null, title = "综合", description = null)

        /** Boards change perhaps twice a year; refreshing twice a day is already generous. */
        const val CACHE_TTL_MILLIS = 12 * 60 * 60 * 1000L
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
    fun toBoard() =
        Board(
            slug = key,
            title = cnText,
            description = description?.ifBlank { null },
            adminOnly = adminOnly,
        )
}
