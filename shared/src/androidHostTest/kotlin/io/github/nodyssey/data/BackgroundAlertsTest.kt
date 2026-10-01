package io.github.nodyssey.data

import io.github.nodyssey.data.local.NodeSeekDatabase
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.TitleKeywordKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 提醒关键词 and 追踪新回复: what turns into a notification, and — as much the point — what does not.
 * Every case here is one where getting it wrong either spams the reader or goes silent.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundAlertsTest {
    private lateinit var database: NodeSeekDatabase
    private val clock = MutableClock()
    private val requests = mutableListOf<Pair<FeedSort, Int>>()
    private var lists: Map<Pair<FeedSort, Int>, List<PostSummary>> = emptyMap()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() = database.close()

    private fun post(
        postId: Long,
        title: String = "post $postId",
        commentCount: Int = 0,
        blocked: Boolean = false,
    ) = PostSummary(
        postId = postId,
        title = title,
        authorName = "tester",
        authorUid = 1,
        avatarUrl = null,
        categoryTitle = null,
        categorySlug = null,
        viewCount = 0,
        commentCount = commentCount,
        lastActiveText = null,
        lastActiveTitle = null,
        isBlocked = blocked,
    )

    private val keywords by lazy { TitleKeywordStore(database.titleKeywordDao(), clock) }
    private val tracked by lazy { TrackedThreadStore(database.trackedThreadDao(), clock) }

    private fun kotlinx.coroutines.test.TestScope.checker() =
        BackgroundAlertChecker(
            loadList = { sort, page ->
                requests += sort to page
                lists[sort to page].orEmpty()
            },
            keywords = keywords,
            tracked = tracked,
            settings = testSettingsRepository(backgroundScope),
        )

    @Test
    fun `the first keyword run records where new starts and announces nothing`() {
        val (hits, baseline) = keywordHits(baseline = null, latest = listOf(post(5, "出 VPS"), post(9)), keywords = listOf("vps"))

        assertEquals(emptyList<KeywordHit>(), hits)
        assertEquals(9L, baseline)
    }

    @Test
    fun `only threads newer than the baseline match, ignoring ASCII case`() {
        val latest = listOf(post(4, "出 VPS 一台"), post(7, "收 vps"), post(8, "闲聊"), post(9, "VpS 测评"))

        val (hits, baseline) = keywordHits(baseline = 6, latest = latest, keywords = listOf("vps"))

        assertEquals(listOf(7L, 9L), hits.map { it.postId })
        assertEquals(9L, baseline)
    }

    @Test
    fun `a thread the site marked blocked is never announced`() {
        val (hits, _) = keywordHits(baseline = 1, latest = listOf(post(2, "vps", blocked = true)), keywords = listOf("vps"))

        assertEquals(emptyList<KeywordHit>(), hits)
    }

    @Test
    fun `no alert keywords means no request at all`() =
        runTest {
            keywords.add(TitleKeywordKind.BLOCK, "vps")

            checker().checkKeywords()

            assertEquals(emptyList<Pair<FeedSort, Int>>(), requests)
        }

    @Test
    fun `a keyword hit is announced once across runs`() =
        runTest {
            keywords.add(TitleKeywordKind.ALERT, "VPS")
            val checker = checker()
            lists = mapOf((FeedSort.POST_TIME to 1) to listOf(post(10)))
            checker.checkKeywords()
            lists = mapOf((FeedSort.POST_TIME to 1) to listOf(post(11, "出 vps"), post(10)))

            assertEquals(listOf(11L), checker.checkKeywords().map { it.postId })
            assertEquals(emptyList<KeywordHit>(), checker.checkKeywords())
        }

    @Test
    fun `a followed thread announces its new replies once`() =
        runTest {
            tracked.track(42, "thread 42", commentCount = 3)
            val checker = checker()
            lists = mapOf((FeedSort.LAST_REPLY to 2) to listOf(post(42, commentCount = 5)))

            assertEquals(listOf(TrackedThreadUpdate(42, "thread 42", newReplies = 2)), checker.checkTrackedThreads())
            assertEquals(emptyList<TrackedThreadUpdate>(), checker.checkTrackedThreads())
            assertEquals(listOf(FeedSort.LAST_REPLY to 1, FeedSort.LAST_REPLY to 2), requests.take(2))
        }

    /** Followed from page 1 of a long thread: the first count seen is the baseline, not news. */
    @Test
    fun `a thread followed without a known count takes its first sighting silently`() =
        runTest {
            tracked.track(42, "thread 42", commentCount = null)
            val checker = checker()
            lists = mapOf((FeedSort.LAST_REPLY to 1) to listOf(post(42, commentCount = 400)))

            assertEquals(emptyList<TrackedThreadUpdate>(), checker.checkTrackedThreads())
            lists = mapOf((FeedSort.LAST_REPLY to 1) to listOf(post(42, commentCount = 401)))
            assertEquals(listOf(1), checker.checkTrackedThreads().map { it.newReplies })
        }

    @Test
    fun `a followed thread missing from the lists is left as it was`() =
        runTest {
            tracked.track(42, "thread 42", commentCount = 3)

            assertEquals(emptyList<TrackedThreadUpdate>(), checker().checkTrackedThreads())
            assertEquals(listOf(3), tracked.all().map { it.lastKnownCount })
        }

    /** Reading the replies in the app is being told about them; the poll must not repeat them. */
    @Test
    fun `replies the reader has already read are not announced`() =
        runTest {
            tracked.track(42, "thread 42", commentCount = 3)
            tracked.advance(42, 5)
            lists = mapOf((FeedSort.LAST_REPLY to 1) to listOf(post(42, commentCount = 5)))

            assertEquals(emptyList<TrackedThreadUpdate>(), checker().checkTrackedThreads())
        }
}
