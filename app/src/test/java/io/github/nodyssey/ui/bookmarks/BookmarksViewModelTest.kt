package io.github.nodyssey.ui.bookmarks

import io.github.nodyssey.data.CollectedPostMeta
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.NoOpPostRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePage
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserSpaceRepository
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 收藏's view model, whose whole reason to exist is that it does *not* page.
 *
 * 「全部 12 / 已下载 5」, 全选 and 「全部下载 · 7 篇」 are statements about the whole collection, so the
 * walk to the end of the endpoint is the behaviour under test — along with the bound on it, and the
 * optimistic removal that the screen offers no confirmation for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val space = FakeSpaceRepository()
    private val posts = FakePostRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val known = FakeCollectedPostMetaStore()

    private fun viewModel(offline: OfflineLibrary = FakeOfflineLibrary()) =
        BookmarksViewModel(space, posts, offline, known)

    @Test
    fun `the whole collection is loaded, not one page of it`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = true), page(2, 4..5, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(5, viewModel.uiState.value.entries.size)
            assertEquals(listOf(1, 2), space.requested)
            assertEquals(false, viewModel.uiState.value.truncated)
        }

    /**
     * The endpoint answers with a title and, in practice, nothing else.
     *
     * Without this the 收藏 list is a column of bare headlines while the same row on the front page
     * carries a board, an author and a reply count — all of which the site itself told this device
     * somewhere else.
     */
    @Test
    fun `a row the endpoint left bare is filled from what this device was told`() =
        runTest(dispatcher) {
            known.remember(
                CollectedPostMeta(
                    postId = 1,
                    categoryTitle = "日常",
                    authorName = "原作者",
                    commentCount = 12,
                    createdAtText = "3 天前",
                ),
            )
            space.pages = listOf(page(1, 1..1, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()

            val row = viewModel.uiState.value.entries.single()
            assertEquals("日常", row.categoryTitle)
            assertEquals("原作者", row.authorName)
            assertEquals(12, row.commentCount)
            assertEquals("3 天前", row.createdAtText)
        }

    /**
     * The collection payload carries no author at all, so the avatar can only come from memory.
     *
     * The stored URL wins over the one built from the uid, because that is the address an offline
     * download stored a file under and therefore the one that resolves with the network off.
     */
    @Test
    fun `the avatar comes from what was remembered, page URL first`() =
        runTest(dispatcher) {
            known.remember(
                listOf(
                    CollectedPostMeta(postId = 1, authorUid = 77, avatarUrl = "https://ns/uploaded.png"),
                    CollectedPostMeta(postId = 2, authorUid = 88),
                ),
            )
            space.pages = listOf(page(1, 1..2, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()

            val rows = viewModel.uiState.value.entries
            assertEquals("https://ns/uploaded.png", rows[0].avatarUrl)
            assertTrue(rows[1].avatarUrl.orEmpty().endsWith("/avatar/88.png"))
        }

    /** Nothing known about the author means no picture asked for, and the row keeps its initial. */
    @Test
    fun `a thread nothing has ever seen still has no avatar`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..1, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.entries.single().avatarUrl)
        }

    /** A remembered answer is a fallback, never an override: the site is the one being asked. */
    @Test
    fun `the site's own answer wins over what was remembered`() =
        runTest(dispatcher) {
            known.remember(CollectedPostMeta(postId = 1, categoryTitle = "旧板块", commentCount = 1))
            space.pages =
                listOf(page(1, 1..1, hasNext = false) { it.copy(categoryTitle = "技术", commentCount = 9) })
            val viewModel = viewModel()
            advanceUntilIdle()

            val row = viewModel.uiState.value.entries.single()
            assertEquals("技术", row.categoryTitle)
            assertEquals(9, row.commentCount)
        }

    @Test
    fun `whatever the endpoint did say is written down for next time`() =
        runTest(dispatcher) {
            space.pages =
                listOf(page(1, 1..1, hasNext = false) { it.copy(categoryTitle = "技术", commentCount = 9) })
            viewModel()
            advanceUntilIdle()

            val stored = requireNotNull(known.observe().first()[1L])
            assertEquals("技术", stored.categoryTitle)
            assertEquals(9, stored.commentCount)
        }

    /**
     * A download backfills the row, and the list must pick it up without being reloaded.
     *
     * This is the route that reaches a collection made on the web years ago: nothing on the device
     * has ever seen the thread, and the pages the download fetches are what finally name it.
     */
    @Test
    fun `a row fills in as soon as something learns more about the thread`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..1, hasNext = false, row = ::bare))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.entries.single().authorName)

            known.remember(CollectedPostMeta(postId = 1, authorName = "原作者"))
            advanceUntilIdle()

            assertEquals("原作者", viewModel.uiState.value.entries.single().authorName)
        }

    /** An empty page ends the walk even when the payload claims another — see the loader's comment. */
    @Test
    fun `an empty page ends the walk regardless of what hasNextPage says`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..2, hasNext = true), page(2, IntRange.EMPTY, hasNext = true))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.entries.size)
            assertEquals(listOf(1, 2), space.requested)
        }

    @Test
    fun `the walk is bounded and says so when the bound bites`() =
        runTest(dispatcher) {
            space.pages = (1..BookmarksViewModel.MAX_PAGES + 3).map { page(it, 1..1, hasNext = true) }
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(BookmarksViewModel.MAX_PAGES, space.requested.size)
            assertTrue(viewModel.uiState.value.truncated)
        }

    @Test
    fun `a failed load reports the site's reason rather than an empty collection`() =
        runTest(dispatcher) {
            space.failure = SiteException(SiteError.LoginRequired)
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(SiteError.LoginRequired, viewModel.uiState.value.error)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun `filters and counts run over the whole collection`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..4, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        1L to OfflineState.Downloaded(bytes = 100),
                        2L to OfflineState.Stale(behindReplies = 3, bytes = 100),
                        3L to OfflineState.Failed(io.github.nodyssey.data.OfflineFailure.Network),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.downloadedCount)
            assertEquals(1, state.newReplyCount)
            // 未下载 (#4) plus 失败 (#3): both are threads with nothing stored.
            assertEquals(2, state.pendingDownloadCount)

            viewModel.setFilter(BookmarkFilter.DOWNLOADED)
            // 已下载 includes the stale copy: it is stored, it is merely behind.
            assertEquals(listOf(1L, 2L), viewModel.uiState.value.visible.map { it.postId })
        }

    @Test
    fun `search looks at titles and authors and nothing else`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSearching(true)
            viewModel.setQuery("帖子 2")
            assertEquals(listOf(2L), viewModel.uiState.value.visible.map { it.postId })

            viewModel.setQuery("技术")
            assertEquals(emptyList<Long>(), viewModel.uiState.value.visible.map { it.postId })
        }

    /** 全选 is a toggle; a second tap on a full selection has to clear it, not re-select it. */
    @Test
    fun `select-all toggles`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            viewModel.toggleSelectAll()
            assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selection)
            assertTrue(viewModel.uiState.value.allVisibleSelected)

            viewModel.toggleSelectAll()
            assertEquals(emptySet<Long>(), viewModel.uiState.value.selection)
        }

    /** 全选 selects what is on screen. A filter is a claim about what the reader means by "all". */
    @Test
    fun `select-all under a filter selects only what the filter shows`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        2L to OfflineState.Downloaded(bytes = 100),
                        3L to OfflineState.Downloaded(bytes = 100),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            viewModel.setFilter(BookmarkFilter.DOWNLOADED)
            viewModel.startSelection(2L)
            viewModel.toggleSelectAll()

            // #1 is in the collection and not in the selection: it is not on screen.
            assertEquals(setOf(2L, 3L), viewModel.uiState.value.selection)
        }

    @Test
    fun `removal takes the rows out before the site answers`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            viewModel.toggleSelection(3L)
            var removed: List<BookmarkEntry>? = null
            var failure: SiteError? = null
            viewModel.removeSelected { entries, error ->
                removed = entries
                failure = error
            }
            advanceUntilIdle()

            assertEquals(listOf(2L), viewModel.uiState.value.entries.map { it.postId })
            assertEquals(listOf(1L to false, 3L to false), posts.collectionWrites)
            assertEquals(listOf(1L, 3L), removed?.map { it.postId })
            assertNull(failure)
            assertNull(viewModel.uiState.value.selection)
        }

    /** A refusal has to put the rows back — the screen removed them without asking anyone. */
    @Test
    fun `a refused removal reloads and reports why`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..3, hasNext = false))
            posts.collectionFailure = SiteException(SiteError.LoginRequired)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.startSelection(1L)
            var failure: SiteError? = null
            viewModel.removeSelected { _, error -> failure = error }
            advanceUntilIdle()

            assertEquals(SiteError.LoginRequired, failure)
            assertEquals(listOf(1L, 2L, 3L), viewModel.uiState.value.entries.map { it.postId })
        }

    @Test
    fun `download-all queues everything with nothing stored, and only that`() =
        runTest(dispatcher) {
            space.pages = listOf(page(1, 1..4, hasNext = false))
            val offline =
                FakeOfflineLibrary(
                    states =
                    mapOf(
                        1L to OfflineState.Downloaded(bytes = 100),
                        2L to OfflineState.Downloading(progress = 0.5f),
                        3L to OfflineState.Failed(io.github.nodyssey.data.OfflineFailure.Network),
                    ),
                )
            val viewModel = viewModel(offline)
            advanceUntilIdle()

            viewModel.downloadPending()
            advanceUntilIdle()

            assertEquals(listOf(3L, 4L), offline.queued.flatten())
        }

    // --- fakes ------------------------------------------------------------------------------------

    /**
     * @param row shapes each generated row — used to reproduce what `list-collection` actually
     * answers with, which is a title and, for most of these fields, nothing.
     */
    private fun page(
        page: Int,
        ids: IntRange,
        hasNext: Boolean,
        row: (SpacePost) -> SpacePost = { it },
    ) = SpacePage(
        items =
        ids.map {
            row(
                SpacePost(
                    postId = it.toLong(),
                    title = "帖子 $it",
                    categoryTitle = "日常",
                    categorySlug = "daily",
                    authorName = "作者$it",
                    commentCount = it,
                    viewCount = null,
                    createdAtText = "上周",
                ),
            )
        },
        page = page,
        hasNextPage = hasNext,
    )

    /** What the collection endpoint is observed to answer with: a title, and nothing else. */
    private fun bare(post: SpacePost) =
        post.copy(
            categoryTitle = null,
            categorySlug = null,
            authorName = null,
            commentCount = null,
            createdAtText = null,
        )

    private class FakeSpaceRepository : UserSpaceRepository {
        var pages: List<SpacePage<SpacePost>> = emptyList()
        var failure: Throwable? = null
        val requested = mutableListOf<Int>()

        override suspend fun collections(page: Int): SpacePage<SpacePost> {
            failure?.let { throw it }
            requested += page
            return pages.getOrElse(page - 1) { SpacePage(emptyList(), page, false) }
        }

        override suspend fun topics(uid: Long, page: Int) = throw UnsupportedOperationException()

        override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> =
            throw UnsupportedOperationException()
    }

    /** Records the collection writes and can be told to refuse them. */
    private class FakePostRepository : NoOpPostRepository() {
        val collectionWrites = mutableListOf<Pair<Long, Boolean>>()
        var collectionFailure: Throwable? = null

        override suspend fun setCollected(
            postId: Long,
            collected: Boolean,
        ) {
            collectionFailure?.let { throw it }
            collectionWrites += postId to collected
        }
    }

    /** In-memory, with the same fill-the-gaps rule the Room store applies. */
    private class FakeCollectedPostMetaStore : CollectedPostMetaStore {
        private val rows = MutableStateFlow(emptyMap<Long, CollectedPostMeta>())

        override fun observe(): Flow<Map<Long, CollectedPostMeta>> = rows

        override suspend fun remember(metas: List<CollectedPostMeta>) {
            rows.update { stored ->
                stored +
                    metas.associate { fresh ->
                        val old = stored[fresh.postId]
                        fresh.postId to
                            CollectedPostMeta(
                                postId = fresh.postId,
                                title = fresh.title ?: old?.title,
                                categoryTitle = fresh.categoryTitle ?: old?.categoryTitle,
                                categorySlug = fresh.categorySlug ?: old?.categorySlug,
                                authorName = fresh.authorName ?: old?.authorName,
                                avatarUrl = fresh.avatarUrl ?: old?.avatarUrl,
                                authorUid = fresh.authorUid ?: old?.authorUid,
                                commentCount = fresh.commentCount ?: old?.commentCount,
                                createdAtText = fresh.createdAtText ?: old?.createdAtText,
                            )
                    }
            }
        }
    }

    private class FakeOfflineLibrary(
        states: Map<Long, OfflineState> = emptyMap(),
        override val isAvailable: Boolean = true,
    ) : OfflineLibrary {
        val queued = mutableListOf<List<Long>>()
        override val states: Flow<Map<Long, OfflineState>> = MutableStateFlow(states)
        override val usage: Flow<OfflineUsage> = MutableStateFlow(OfflineUsage())
        override val settings: Flow<OfflineSettings> = MutableStateFlow(OfflineSettings())

        val notedCounts = mutableListOf<Map<Long, Int>>()

        override suspend fun download(postIds: Collection<Long>) {
            queued += postIds.toList()
        }

        override suspend fun noteReplyCounts(counts: Map<Long, Int>) {
            notedCounts += counts
        }

        override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null

        override suspend fun cancel(postId: Long) = Unit

        override suspend fun clearAll() = Unit

        override suspend fun updateSettings(settings: OfflineSettings) = Unit
    }
}
