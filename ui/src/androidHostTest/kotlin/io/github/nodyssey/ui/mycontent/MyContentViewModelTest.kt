package io.github.nodyssey.ui.mycontent

import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.SpaceComment
import io.github.nodyssey.data.SpacePage
import io.github.nodyssey.data.SpacePost
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.UserSpaceRepository
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 我的主题帖 / 我的评论 — boards n2 and n3.
 *
 * What is worth pinning is the honesty of the two chips. Both act on the rows that have been loaded
 * and on nothing else, because the endpoint behind them takes neither a board nor a sort: 最早发布
 * reverses what is in hand rather than fetching the account's oldest thread, and the board menu is
 * built from boards that have actually arrived rather than from a list of the forum's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyContentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first page arrives newest first with the profile's own total`() =
        runTest(dispatcher) {
            val vm = MyTopicsViewModel(FakeProfileRepository(), PagedSpaceRepository())
            advanceUntilIdle()

            assertEquals(listOf("t1", "t2", "t3"), vm.uiState.value.items.map(SpacePost::title))
            assertEquals(3, vm.uiState.value.loadedCount)
            // Not `loadedCount`: the header answers "how many do I have", and that number must not
            // creep upward as pages arrive.
            assertEquals(7, vm.uiState.value.totalCount)
            assertFalse(vm.uiState.value.endReached)
        }

    @Test
    fun `loadMore appends the next page and stops at the end`() =
        runTest(dispatcher) {
            val vm = MyTopicsViewModel(FakeProfileRepository(), PagedSpaceRepository())
            advanceUntilIdle()

            vm.loadMore()
            advanceUntilIdle()

            assertEquals(6, vm.uiState.value.loadedCount)
            assertTrue(vm.uiState.value.endReached)

            // Nothing left to ask for, so a list scrolled to its end does not spin forever.
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(6, vm.uiState.value.loadedCount)
        }

    @Test
    fun `the oldest-first chip reverses the rows that have loaded`() =
        runTest(dispatcher) {
            val vm = MyTopicsViewModel(FakeProfileRepository(), PagedSpaceRepository())
            advanceUntilIdle()

            vm.selectSort(MyContentSort.OLDEST)

            assertEquals(listOf("t3", "t2", "t1"), vm.uiState.value.items.map(SpacePost::title))
            // Still every row that has arrived — a sort is not a filter.
            assertEquals(3, vm.uiState.value.loadedCount)
        }

    @Test
    fun `the board menu holds only boards that have actually arrived`() =
        runTest(dispatcher) {
            val vm = MyTopicsViewModel(FakeProfileRepository(), PagedSpaceRepository())
            advanceUntilIdle()

            assertEquals(listOf("技术", "日常"), vm.uiState.value.boards)

            vm.selectBoard("日常")
            assertEquals(listOf("t2"), vm.uiState.value.items.map(SpacePost::title))
            assertEquals(3, vm.uiState.value.loadedCount)

            vm.selectBoard(null)
            assertEquals(3, vm.uiState.value.items.size)
        }

    /**
     * The comment payload names the thread but never its board, so board n3's first chip has
     * nothing to offer and the screen hides it. Reading a board off the title would be a guess with
     * a colour on it.
     */
    @Test
    fun `comments offer no board filter at all`() =
        runTest(dispatcher) {
            val vm = MyCommentsViewModel(FakeProfileRepository(), PagedSpaceRepository())
            advanceUntilIdle()

            assertTrue(vm.uiState.value.boards.isEmpty())
            assertEquals(96, vm.uiState.value.totalCount)
        }

    @Test
    fun `a refused first page is an error rather than an empty list`() =
        runTest(dispatcher) {
            val vm = MyTopicsViewModel(FakeProfileRepository(), FailingSpaceRepository)
            advanceUntilIdle()

            assertEquals(SiteError.Cloudflare, vm.uiState.value.error)
            assertFalse(vm.uiState.value.isEmpty)
        }
}

private class FakeProfileRepository : ProfileRepository {
    override suspend fun profile(refresh: Boolean): UserProfile = profile(UID)

    override suspend fun profile(uid: Long): UserProfile =
        UserProfile(uid = uid, name = "nssk", avatarUrl = "", topicCount = 7, commentCount = 96)
}

/** Two pages of three, newest first, the way the site returns them. */
private class PagedSpaceRepository : UserSpaceRepository {
    override suspend fun topics(uid: Long, page: Int): SpacePage<SpacePost> =
        when (page) {
            1 ->
                SpacePage(
                    items =
                    listOf(
                        post(1, "t1", "技术"),
                        post(2, "t2", "日常"),
                        post(3, "t3", "技术"),
                    ),
                    page = 1,
                    hasNextPage = true,
                )

            else ->
                SpacePage(
                    items = listOf(post(4, "t4", "技术"), post(5, "t5", "技术"), post(6, "t6", "技术")),
                    page = page,
                    hasNextPage = false,
                )
        }

    override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> =
        SpacePage(
            items =
            listOf(
                SpaceComment(postId = 1, commentId = 11, postTitle = "t1", excerpt = "c1", createdAtText = "3 天前"),
            ),
            page = page,
            hasNextPage = false,
        )

    override suspend fun collections(page: Int): SpacePage<SpacePost> =
        SpacePage(items = emptyList(), page = page, hasNextPage = false)

    private fun post(id: Long, title: String, board: String) =
        SpacePost(
            postId = id,
            title = title,
            categoryTitle = board,
            categorySlug = null,
            authorName = null,
            commentCount = null,
            viewCount = null,
            createdAtText = null,
        )
}

private object FailingSpaceRepository : UserSpaceRepository {
    override suspend fun topics(uid: Long, page: Int): SpacePage<SpacePost> =
        throw SiteException(SiteError.Cloudflare)

    override suspend fun comments(uid: Long, page: Int): SpacePage<SpaceComment> =
        throw SiteException(SiteError.Cloudflare)

    override suspend fun collections(page: Int): SpacePage<SpacePost> =
        throw SiteException(SiteError.Cloudflare)
}

private const val UID = 88423L
