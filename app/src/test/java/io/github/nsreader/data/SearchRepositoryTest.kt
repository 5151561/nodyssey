package io.github.nsreader.data

import io.github.nsreader.data.local.NodeSeekDatabase
import io.github.nsreader.data.local.toEntity
import io.github.nsreader.model.PostSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchRepositoryTest {
    private lateinit var database: NodeSeekDatabase
    private lateinit var repository: OfflineFirstPostRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = OfflineFirstPostRepository(database, FakePostRemoteDataSource(), MutableClock())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `search matches cached title and author`() =
        runTest {
            database.feedDao().upsertPosts(
                listOf(
                    post(1, "腾讯云轻量测评", "cloudpeak"),
                    post(2, "普通帖子", "腾讯云用户"),
                    post(3, "不相关", "someone"),
                ).map { it.toEntity(1_000) },
            )

            assertEquals(listOf(2L, 1L), repository.search("腾讯云").first().map { it.summary.postId })
        }

    @Test
    fun `like wildcards are searched as ordinary characters`() =
        runTest {
            database.feedDao().upsertPosts(
                listOf(post(1, "100% 可用", "tester"), post(2, "普通帖子", "tester"))
                    .map { it.toEntity(1_000) },
            )

            assertEquals(listOf(1L), repository.search("%").first().map { it.summary.postId })
        }

    private fun post(
        id: Long,
        title: String,
        author: String,
    ) = PostSummary(
        postId = id,
        title = title,
        authorName = author,
        authorUid = null,
        avatarUrl = null,
        categoryTitle = null,
        categorySlug = null,
        viewCount = null,
        commentCount = null,
        lastActiveText = null,
        lastActiveTitle = null,
    )
}
