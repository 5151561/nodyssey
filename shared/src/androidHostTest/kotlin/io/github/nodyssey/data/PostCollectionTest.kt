package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonApi
import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.data.local.toEntity
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Collecting a thread, from the request through to what the screen ends up reading.
 *
 * The star renders from Room like everything else on the detail screen, so a toggle the site
 * accepted but that never reached the database would leave the reader looking at the state they had
 * just changed away from.
 */
@RunWith(RobolectricTestRunner::class)
class PostCollectionTest {
    private lateinit var database: NodeSeekDatabase
    private val remote = FakePostRemoteDataSource()
    private val clock = MutableClock()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(api: JsonApi) =
        OfflineFirstPostRepository(
            database,
            remote,
            clock,
            collections = PostCollectionWriter(api),
            collectedMeta = RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock),
        )

    @Test
    fun `the page's own blob decides the star's starting state`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = true, collectionCount = 7)
            }
            val repository = repository(api("""{"success":true}"""))

            repository.refreshThread(postId = 42, page = 1)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    /** A page with no blob leaves it unknown, which is what keeps the star from being drawn at all. */
    @Test
    fun `a page that said nothing about the collection leaves it unknown`() =
        runTest {
            val repository = repository(api("""{"success":true}"""))

            repository.refreshThread(postId = 42, page = 1)

            val thread = requireNotNull(repository.thread(42).first())
            assertNull(thread.collected)
            assertNull(thread.collectionCount)
        }

    @Test
    fun `collecting writes the new state and count into the cached thread`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":true,"message":"added","postCollectionCount":7}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = true)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    @Test
    fun `un-collecting writes the removal through`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = true, collectionCount = 7)
            }
            val repository = repository(api("""{"success":true,"message":"removed","postCollectionCount":6}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = false)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
            assertEquals(6, thread.collectionCount)
        }

    /**
     * The site is the one that knows. A double tap, or a star drawn from a stale page, both end with
     * the request and the answer disagreeing — and following the request would leave Room holding a
     * state the server never entered.
     */
    @Test
    fun `the site's echo wins over what was asked for`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":true,"message":"removed","postCollectionCount":5}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.setCollected(postId = 42, collected = true)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
        }

    @Test
    fun `a refusal carries the site's own sentence and leaves the cache alone`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(postId, page, collected = false, collectionCount = 6)
            }
            val repository = repository(api("""{"success":false,"message":"收藏夹已满"}"""))
            repository.refreshThread(postId = 42, page = 1)

            val thrown =
                assertThrows(SiteException::class.java) {
                    runBlocking { repository.setCollected(postId = 42, collected = true) }
                }

            assertEquals("收藏夹已满", thrown.detail)
            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(false, thread.collected)
        }

    /** A build that never wired the writer refuses outright rather than reporting a write it never sent. */
    @Test
    fun `an unwired repository refuses the toggle`() =
        runTest {
            val repository = OfflineFirstPostRepository(database, remote, clock)

            val thrown =
                assertThrows(SiteException::class.java) {
                    runBlocking { repository.setCollected(postId = 42, collected = true) }
                }

            assertEquals(SiteError.NotWired, thrown.error)
        }

    /**
     * Only page 1 is guaranteed to carry the blob today, and a later page arriving without it must
     * not turn a known star into an unknown one.
     */
    @Test
    fun `appending a page that carried no blob keeps the known collection state`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource.detail(
                    postId,
                    page,
                    totalPages = 2,
                    collected = if (page == 1) true else null,
                    collectionCount = if (page == 1) 7 else null,
                )
            }
            val repository = repository(api("""{"success":true}"""))
            repository.refreshThread(postId = 42, page = 1)

            repository.extendThread(postId = 42, page = 2)

            val thread = requireNotNull(repository.thread(42).first())
            assertEquals(true, thread.collected)
            assertEquals(7, thread.collectionCount)
        }

    /**
     * Collecting is the last moment the app is holding these facts.
     *
     * `list-collection` will not repeat the board, the author or the reply count, and the read mark
     * that carries some of them is trimmed by 浏览历史保留条数 — so if the star does not write them
     * down now, 收藏 has nothing but a headline to draw with for the life of the collection.
     */
    @Test
    fun `collecting writes down what this device knows about the thread`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("the opening post")
                            .copy(authorName = "原作者", createdAtText = "3 天前"),
                    )
            }
            val repository = repository(api("""{"success":true,"collected":true}"""))
            repository.refreshThread(postId = 42, page = 1)
            database.feedDao().upsertPosts(
                listOf(
                    FakePostRemoteDataSource
                        .summary("daily", 1)
                        .copy(postId = 42, categoryTitle = "日常", categorySlug = "daily", commentCount = 12)
                        .toEntity(clock.nowMillis),
                ),
            )

            repository.setCollected(postId = 42, collected = true)

            val known = requireNotNull(store().observe().first()[42L])
            assertEquals("日常", known.categoryTitle)
            assertEquals("daily", known.categorySlug)
            assertEquals("原作者", known.authorName)
            assertEquals(12, known.commentCount)
            assertEquals("3 天前", known.createdAtText)
        }

    /**
     * The route the reader actually takes: tap a bare row, and it is complete when they come back.
     *
     * Opening the thread fetches the page that names its board, its author, its avatar and when it
     * was posted — all of which `list-collection` withheld — so the list has no reason to still be
     * drawing a headline afterwards.
     */
    @Test
    fun `reading a collected thread writes down what the page said`() =
        runTest {
            remote.detailResult = { postId, page ->
                FakePostRemoteDataSource
                    .detail(postId, page, collected = true)
                    .copy(
                        body =
                        FakePostRemoteDataSource
                            .content("the opening post")
                            .copy(
                                authorName = "原作者",
                                authorUid = 77,
                                avatarUrl = "https://ns/avatar/77.png",
                                categoryTitle = "日常",
                                createdAtText = "3 天前",
                            ),
                    )
            }

            repository(api("""{"success":true}""")).refreshThread(postId = 42, page = 1)

            val known = requireNotNull(store().observe().first()[42L])
            assertEquals("原作者", known.authorName)
            assertEquals("https://ns/avatar/77.png", known.avatarUrl)
            assertEquals(77L, known.authorUid)
            assertEquals("日常", known.categoryTitle)
            assertEquals("3 天前", known.createdAtText)
        }

    /**
     * This runs for every thread anybody opens; the table is about the ones in the collection.
     *
     * A page carrying no `__config__` is a signed-out read, and a signed-out reader has no
     * collection for the row to be about — so "we were not told" is not treated as a yes.
     */
    @Test
    fun `reading a thread nobody collected writes nothing down`() =
        runTest {
            remote.detailResult = { postId, page -> FakePostRemoteDataSource.detail(postId, page, collected = false) }
            repository(api("""{"success":true}""")).refreshThread(postId = 42, page = 1)
            assertTrue(store().observe().first().isEmpty())

            remote.detailResult = { postId, page -> FakePostRemoteDataSource.detail(postId, page, collected = null) }
            repository(api("""{"success":true}""")).refreshThread(postId = 43, page = 1)
            assertTrue(store().observe().first().isEmpty())
        }

    /** Un-collecting is not new information about the thread, and must not blank what is known. */
    @Test
    fun `un-collecting leaves what is known alone`() =
        runTest {
            val repository = repository(api("""{"success":true,"collected":true}"""))
            repository.refreshThread(postId = 42, page = 1)
            repository.setCollected(postId = 42, collected = true)

            val undo = repository(api("""{"success":true,"collected":false}"""))
            undo.setCollected(postId = 42, collected = false)

            assertEquals("thread 42", store().observe().first()[42L]?.title)
        }

    /**
     * The list 收藏 draws lives on this device now, so a star pressed off has to reach it.
     *
     * Without this the thread is out of the site's collection and still on the screen until the next
     * successful walk — which, on the aeroplane the stored list exists for, is never.
     */
    @Test
    fun `un-collecting takes the thread off the stored list`() =
        runTest {
            val store = store()
            store.rememberCollection(listOf(CollectedPostMeta(postId = 42, title = "一篇收藏")))
            assertEquals(listOf(42L), store.observeCollection().first().map { it.postId })

            repository(api("""{"success":true,"collected":false}""")).setCollected(postId = 42, collected = false)

            assertTrue(store.observeCollection().first().isEmpty())
            // Off the list, not forgotten: re-collecting it should not cost the row what it knew.
            assertEquals("一篇收藏", store.observe().first()[42L]?.title)
        }

    /**
     * A walk is a statement about the whole collection, including what is no longer in it.
     *
     * A thread un-collected on the web announces itself by being absent, and nothing else — so the
     * list has to be replaced rather than added to, or it only ever grows.
     */
    @Test
    fun `a walk replaces the list rather than adding to it`() =
        runTest {
            val store = store()
            store.rememberCollection(
                listOf(CollectedPostMeta(postId = 1, title = "一"), CollectedPostMeta(postId = 2, title = "二")),
            )

            store.rememberCollection(listOf(CollectedPostMeta(postId = 2, title = "二")))

            assertEquals(listOf(2L), store.observeCollection().first().map { it.postId })
            assertEquals("一", store.observe().first()[1L]?.title)
        }

    /** Collection order is the site's own, and the only thing 「收藏顺序」 can mean off disk. */
    @Test
    fun `the stored list comes back in the order the walk saw it`() =
        runTest {
            val store = store()
            store.rememberCollection(
                listOf(
                    CollectedPostMeta(postId = 7, title = "七"),
                    CollectedPostMeta(postId = 3, title = "三"),
                    CollectedPostMeta(postId = 5, title = "五"),
                ),
            )

            assertEquals(listOf(7L, 3L, 5L), store.observeCollection().first().map { it.postId })
        }

    /**
     * The bound is for threads collected and un-collected over the years, not for the list itself.
     *
     * Evicting a listed row would take a thread out of the collection on this device alone — which
     * would look exactly like the site having dropped it.
     */
    @Test
    fun `the trim never evicts a thread that is on the list`() =
        runTest {
            val store = store()
            store.remember(CollectedPostMeta(postId = 9, title = "路过看过的"))
            store.rememberCollection(listOf(CollectedPostMeta(postId = 1, title = "一"), CollectedPostMeta(postId = 2, title = "二")))

            database.collectedPostMetaDao().trimTo(0)

            assertEquals(listOf(1L, 2L), store.observeCollection().first().map { it.postId })
            assertEquals(setOf(1L, 2L), store.observe().first().keys)
        }

    private fun store() = RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock)

    private fun api(answer: String) = FakeCollectionJsonApi(answer)
}

private class FakeCollectionJsonApi(
    private val answer: String,
) : JsonApi {
    override suspend fun getJson(path: String, referer: String): String = error("unexpected read of $path")

    override suspend fun postJson(path: String, body: String, referer: String): String {
        require(path == "/api/statistics/collection") { "unexpected write to $path" }
        return answer
    }
}
