package io.github.nodyssey.data

import io.github.nodyssey.data.local.FeedPositionEntity
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostSummary
import io.github.plaza.core.richtext.RichNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The browsing history, which is the read-mark table read the other way round.
 *
 * The case that drives the whole design is the deep link: a thread reached from a notification or an
 * external URL has never been in a feed, so `posts` holds nothing for it and a join would list it as
 * a blank row. The snapshot columns exist for exactly that.
 */
@RunWith(RobolectricTestRunner::class)
class ReadHistoryTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineFirstPostRepository(database, remote, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Puts one post in the front-page feed, the way a reader who scrolled to it would have. */
    private suspend fun givenListedPost(
        postId: Long,
        title: String,
        author: String = "tester",
        category: String? = "日常",
        commentCount: Int = 10,
    ) {
        val summary =
            PostSummary(
                postId = postId,
                title = title,
                authorName = author,
                authorUid = 1,
                avatarUrl = null,
                categoryTitle = category,
                categorySlug = "daily",
                viewCount = 0,
                commentCount = commentCount,
                lastActiveText = null,
                lastActiveTitle = null,
            )
        database.feedDao().upsertPosts(listOf(summary.toEntity(clock.nowMillis())))
        database.feedDao().insertPositions(
            listOf(FeedPositionEntity(feedKey = FRONT_PAGE_FEED_KEY, postId = postId, sortIndex = 0)),
        )
    }

    @Test
    fun `a listed post carries its title and author into the history`() =
        runTest {
            givenListedPost(postId = 7, title = "绿云抢鸡竞赛", author = "ipv4")

            repository.markThreadRead(7)

            val entry = repository.readHistory().first().single()
            assertEquals(7L, entry.postId)
            assertEquals("绿云抢鸡竞赛", entry.title)
            assertEquals("ipv4", entry.authorName)
            assertEquals("日常", entry.categoryTitle)
            assertEquals(clock.nowMillis(), entry.lastReadAtMillis)
        }

    /**
     * The reason the snapshot exists at all. Nothing is in `posts`, so the title has to come from the
     * thread that was actually rendered.
     */
    @Test
    fun `a deep-linked thread that was never in a feed still has a title`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(
                    postId,
                    page,
                    body = opener(author = "someone", category = "技术"),
                )
            }
            repository.refreshThread(postId = 99, page = 1)

            repository.markThreadRead(99)

            val entry = repository.readHistory().first().single()
            assertEquals("thread 99", entry.title)
            assertEquals("someone", entry.authorName)
            assertEquals("技术", entry.categoryTitle)
            // No feed ever carried it, so there is no reply count to have seen.
            assertNull(entry.commentCount)
        }

    /**
     * Page 3 of a thread whose page 1 was never fetched has no opening post, so this read knows no
     * title. Overwriting with null would blank a row the reader can still see in the list.
     */
    @Test
    fun `a later read that knows no title keeps the one already captured`() =
        runTest {
            givenListedPost(postId = 7, title = "绿云抢鸡竞赛")
            repository.markThreadRead(7)

            database.feedDao().clearAllPosts()
            database.postDetailDao().clearAllThreads()
            clock.advanceBy(1_000)
            repository.markThreadRead(7)

            val entry = repository.readHistory().first().single()
            assertEquals("绿云抢鸡竞赛", entry.title)
            assertEquals(clock.nowMillis(), entry.lastReadAtMillis)
        }

    @Test
    fun `history is most recently read first`() =
        runTest {
            givenListedPost(postId = 1, title = "first")
            givenListedPost(postId = 2, title = "second")

            repository.markThreadRead(1)
            clock.advanceBy(1_000)
            repository.markThreadRead(2)

            assertEquals(listOf(2L, 1L), repository.readHistory().first().map { it.postId })
        }

    @Test
    fun `removing one entry leaves the rest`() =
        runTest {
            givenListedPost(postId = 1, title = "first")
            givenListedPost(postId = 2, title = "second")
            repository.markThreadRead(1)
            repository.markThreadRead(2)

            repository.removeFromHistory(1)

            assertEquals(listOf(2L), repository.readHistory().first().map { it.postId })
        }

    /** Clearing takes the unread baselines with it — the same rows do both jobs. */
    @Test
    fun `clearing empties the history and the unread baselines`() =
        runTest {
            givenListedPost(postId = 1, title = "first")
            repository.markThreadRead(1)

            repository.clearReadHistory()

            assertTrue(repository.readHistory().first().isEmpty())
            assertNull(database.readMarkDao().find(1))
        }

    /**
     * Forgetting a thread includes forgetting where it was left off.
     *
     * Otherwise a reader who swipes a thread out of their history and opens it again is offered
     * 上次阅读 for a read the app has just told them it no longer has a record of.
     */
    @Test
    fun `removing one entry forgets where that thread was left off`() =
        runTest {
            val positions = readingPositions()
            givenListedPost(postId = 1, title = "first")
            givenListedPost(postId = 2, title = "second")
            repository.markThreadRead(1)
            repository.markThreadRead(2)
            positions.setReadingPosition(1, ReadingPosition(page = 4, floor = "#31"))
            positions.setReadingPosition(2, ReadingPosition(page = 9))

            repository.removeFromHistory(1)

            assertNull(positions.readingPosition(1))
            assertEquals(ReadingPosition(page = 9), positions.readingPosition(2))
        }

    /** 撤销 has to be an undo, which means the bookmark comes back with the row. */
    @Test
    fun `putting a removed entry back restores where it was left off`() =
        runTest {
            val positions = readingPositions()
            givenListedPost(postId = 7, title = "绿云抢鸡竞赛", author = "ipv4")
            repository.markThreadRead(7)
            positions.setReadingPosition(7, ReadingPosition(page = 4, floor = "#31"))
            val removed = repository.readHistory().first().single()

            repository.removeFromHistory(7)
            repository.restoreToHistory(removed)

            assertEquals(ReadingPosition(page = 4, floor = "#31"), positions.readingPosition(7))
            assertEquals(removed, repository.readHistory().first().single())
        }

    /** A thread read without scrolling has no bookmark, and its history row must survive anyway. */
    @Test
    fun `a thread with no bookmark still lists and still restores`() =
        runTest {
            givenListedPost(postId = 7, title = "绿云抢鸡竞赛")
            repository.markThreadRead(7)
            val removed = repository.readHistory().first().single()
            assertNull(removed.readingPosition)

            repository.removeFromHistory(7)
            repository.restoreToHistory(removed)

            assertEquals(removed, repository.readHistory().first().single())
        }

    @Test
    fun `clearing forgets where every thread was left off`() =
        runTest {
            val positions = readingPositions()
            givenListedPost(postId = 1, title = "first")
            repository.markThreadRead(1)
            positions.setReadingPosition(1, ReadingPosition(page = 4, floor = "#31"))

            repository.clearReadHistory()

            assertNull(positions.readingPosition(1))
        }

    /**
     * 保留条数 is one number for how much reading this app remembers, so it bounds the bookmarks too.
     *
     * The order is the point: the place dropped is the one nobody has come back to in the longest,
     * on the same axis the read marks are trimmed by, not whichever postId happens to sort last.
     */
    @Test
    fun `lowering the limit trims the bookmarks along with the history`() =
        runTest {
            val cap = 3
            repository = repositoryWithLimit(MutableStateFlow(cap))
            val positions = readingPositions()
            repeat(cap + 2) { index ->
                val postId = index + 1L
                givenListedPost(postId = postId, title = "post $postId")
                repository.markThreadRead(postId)
                clock.advanceBy(1000)
                positions.setReadingPosition(postId, ReadingPosition(page = index + 1))
            }

            repository.trimReadHistory()

            assertEquals(cap, database.readingPositionDao().count())
            assertNull(positions.readingPosition(1))
            assertEquals(ReadingPosition(page = cap + 2), positions.readingPosition(cap + 2L))
        }

    /** 无上限 keeps every place, for the same reason it keeps every row: it was asked to. */
    @Test
    fun `an unlimited history keeps every bookmark`() =
        runTest {
            repository = repositoryWithLimit(MutableStateFlow(SettingsRepository.READ_HISTORY_UNLIMITED))
            val positions = readingPositions()
            repeat(5) { index ->
                val postId = index + 1L
                givenListedPost(postId = postId, title = "post $postId")
                repository.markThreadRead(postId)
                clock.advanceBy(1000)
                positions.setReadingPosition(postId, ReadingPosition(page = index + 1))
            }

            repository.trimReadHistory()

            assertEquals(5, database.readingPositionDao().count())
        }

    private fun readingPositions() = RoomReadingPositionStore(database.readingPositionDao(), clock)

    /** Undo for a swiped-away row: the snapshot is enough to write the whole mark again. */
    @Test
    fun `a removed entry can be put back`() =
        runTest {
            givenListedPost(postId = 7, title = "绿云抢鸡竞赛", author = "ipv4")
            repository.markThreadRead(7)
            val removed = repository.readHistory().first().single()

            repository.removeFromHistory(7)
            repository.restoreToHistory(removed)

            val restored = repository.readHistory().first().single()
            assertEquals(removed, restored)
            // And with it the unread baseline, which is the half of this row nobody can see.
            assertEquals(10, database.readMarkDao().find(7)?.lastSeenCommentCount)
        }

    /** The table gains a row per thread ever opened, so something has to bound it. */
    @Test
    fun `the history is trimmed to its cap, oldest first`() =
        runTest {
            val cap = 8
            repository = repositoryWithLimit(MutableStateFlow(cap))
            givenReadThreads(cap + 5)

            val history = repository.readHistory().first()
            assertEquals(cap, history.size)
            assertEquals((cap + 5).toLong(), history.first().postId)
            // The five oldest went, and so did their read marks.
            assertNull(database.readMarkDao().find(1))
        }

    /** 无上限 keeps everything — including the read marks the feed greys its rows with. */
    @Test
    fun `an unlimited history is never trimmed`() =
        runTest {
            repository =
                repositoryWithLimit(MutableStateFlow(SettingsRepository.READ_HISTORY_UNLIMITED))
            givenReadThreads(12)

            assertEquals(12, repository.readHistory().first().size)
        }

    /**
     * Lowering 保留条数 has to take effect on rows nobody is about to re-read: they would otherwise
     * go on greying out their feed rows until the next thread is opened.
     */
    @Test
    fun `lowering the limit shortens the list and drops the rows on request`() =
        runTest {
            val limit = MutableStateFlow(SettingsRepository.READ_HISTORY_UNLIMITED)
            repository = repositoryWithLimit(limit)
            givenReadThreads(12)

            limit.value = 5

            // The list re-lengths on its own, because the query is re-run with the new limit.
            assertEquals(5, repository.readHistory().first().size)
            // The rows themselves survive until something asks, which is what the setting screen does.
            assertEquals(12, database.readMarkDao().observeHistory(Int.MAX_VALUE).first().size)

            repository.trimReadHistory()

            assertEquals(5, database.readMarkDao().observeHistory(Int.MAX_VALUE).first().size)
            assertNull(database.readMarkDao().find(1))
        }

    private fun repositoryWithLimit(limit: Flow<Int>) =
        OfflineFirstPostRepository(database, remote, clock, readHistoryLimit = limit)

    /** Reads threads 1..[count], one second apart, so the oldest is the lowest id. */
    private suspend fun givenReadThreads(count: Int) {
        repeat(count) { index ->
            val postId = index + 1L
            givenListedPost(postId = postId, title = "post $postId")
            repository.markThreadRead(postId)
            clock.advanceBy(1_000)
        }
    }

    private fun opener(
        author: String,
        category: String,
    ) = PostContent(
        commentId = 1L,
        floor = "#0",
        authorName = author,
        authorUid = 42,
        avatarUrl = null,
        isOriginalPoster = true,
        badges = emptyList(),
        createdAtText = null,
        createdAtTitle = null,
        categoryTitle = category,
        nodes = listOf(RichNode.CodeBlock("body", language = null)),
    )
}
