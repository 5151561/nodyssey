package io.github.nodyssey.data.composer

import io.github.nodyssey.data.PostRemoteDataSource
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostDetail
import io.github.nodyssey.model.PostListPage
import io.github.nodyssey.model.PostSource
import io.github.nodyssey.model.PostSourceFloor
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the site's two edit endpoints a floor goes to, and what the editor does when the page it
 * needs cannot be read.
 */
class PostEditorTest {

    private val target = PostEditTarget(postId = 876332, commentId = 9, page = 1, isOpeningPost = true)
    private val replyTarget = PostEditTarget(postId = 876332, commentId = 10, page = 3, isOpeningPost = false)

    private val source =
        PostSource(
            postId = 876332,
            title = "原标题",
            rank = 2,
            floors =
            listOf(
                PostSourceFloor(commentId = 9, isOpeningPost = true, markdown = "主楼正文", isMine = true),
                PostSourceFloor(commentId = 10, isOpeningPost = false, markdown = "回复正文", isMine = true),
            ),
        )

    @Test
    fun `loading a 主楼 brings back the thread's own title and 阅读权限`() = runTest {
        val content = editor(FakeSource(source)).load(target)

        assertEquals("原标题", content.title)
        assertEquals(PostPermission(2), content.permission)
        assertEquals("主楼正文", content.body)
    }

    @Test
    fun `loading a reply brings back that floor's markdown`() = runTest {
        assertEquals("回复正文", editor(FakeSource(source)).load(replyTarget).body)
    }

    /** No blob means nobody is signed in, and signing in is the only thing that changes that. */
    @Test
    fun `a page with no readable source asks the user to sign in`() = runTest {
        val error =
            try {
                editor(FakeSource(null)).load(target)
                throw AssertionError("load should have failed")
            } catch (exception: SiteException) {
                exception.error
            }

        assertEquals(SiteError.LoginRequired, error)
    }

    @Test
    fun `a 主楼 goes to edit-discussion and a reply goes to edit-comment`() = runTest {
        val posts = RecordingPostComposer()
        val comments = RecordingCommentComposer()
        val editor = editor(FakeSource(source), posts, comments)
        val content = PostEditContent("新标题", PostPermission.PRIVATE, "新正文")

        editor.save(target, content)

        assertEquals(
            PostEditSubmission(876332, "新标题", "新正文", PostPermission.PRIVATE),
            posts.submission,
        )
        assertNull(comments.body)

        editor.save(replyTarget, PostEditContent("", PostPermission.PUBLIC, "新回复"))

        assertEquals(10L, comments.commentId)
        assertEquals("新回复", comments.body)
    }

    /**
     * The page is re-read so the thread behind the editor updates itself — and `extend`, not
     * `refresh`, so the other pages the reader has scrolled through stay in the cache.
     */
    @Test
    fun `a saved edit re-reads the page it was on`() = runTest {
        val reloaded = mutableListOf<Pair<Long, Int>>()

        editor(FakeSource(source), threads = { postId, page -> reloaded += postId to page })
            .save(replyTarget, PostEditContent("", PostPermission.PUBLIC, "x"))

        assertEquals(listOf(876332L to 3), reloaded)
    }

    /** The write landed. Reporting a failed re-read as a failed save is how an edit gets sent twice. */
    @Test
    fun `a failed re-read does not fail the save`() = runTest {
        editor(FakeSource(source), threads = { _, _ -> throw SiteException(SiteError.Network) })
            .save(replyTarget, PostEditContent("", PostPermission.PUBLIC, "x"))
    }

    private fun editor(
        remote: PostRemoteDataSource,
        posts: PostComposerRepository = RecordingPostComposer(),
        comments: CommentComposerRepository = RecordingCommentComposer(),
        threads: ThreadReloader = ThreadReloader { _, _ -> },
    ) = DefaultPostEditor(remote, posts, comments, threads)
}

private class FakeSource(private val source: PostSource?) : PostRemoteDataSource {
    override suspend fun loadList(categorySlug: String?, page: Int, sort: FeedSort): PostListPage =
        throw UnsupportedOperationException()

    override suspend fun loadSearch(query: String, page: Int, categorySlug: String?, sort: FeedSort): PostListPage =
        throw UnsupportedOperationException()

    override suspend fun loadDetail(postId: Long, page: Int): PostDetail = throw UnsupportedOperationException()

    override suspend fun loadSource(postId: Long, page: Int): PostSource? = source
}

private class RecordingPostComposer : PostComposerRepository {
    var submission: PostEditSubmission? = null

    override val draft = flowOf<PostDraft?>(null)

    override suspend fun saveDraft(draft: PostDraft) = Unit

    override suspend fun deleteDraft() = Unit

    override suspend fun publish(submission: PostSubmission): Long? = null

    override suspend fun edit(submission: PostEditSubmission) {
        this.submission = submission
    }
}

private class RecordingCommentComposer : CommentComposerRepository {
    var commentId: Long? = null
    var body: String? = null

    override fun draft(postId: Long) = flowOf<CommentDraft?>(null)

    override suspend fun saveDraft(postId: Long, draft: CommentDraft) = Unit

    override suspend fun deleteDraft(postId: Long) = Unit

    override suspend fun deleteAllDrafts() = Unit

    override suspend fun publish(submission: CommentSubmission): Int? = null

    override suspend fun edit(postId: Long, commentId: Long, body: String) {
        this.commentId = commentId
        this.body = body
    }
}
