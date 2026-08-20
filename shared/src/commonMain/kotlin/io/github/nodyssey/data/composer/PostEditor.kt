package io.github.nodyssey.data.composer

import io.github.nodyssey.data.PostRemoteDataSource
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.runCatchingExceptCancellation
import kotlinx.serialization.Serializable

/**
 * The floor an editor was opened on.
 *
 * Travels in the navigation key, which is why it is [Serializable] and why it carries the page: the
 * source of a reply is only in the `__config__` of the page that reply is on, and the screen that
 * offered 编辑 is the one that knows which page that was.
 */
@Serializable
data class PostEditTarget(
    val postId: Long,
    val commentId: Long,
    val page: Int,
    /**
     * 主楼 rather than a reply.
     *
     * Decides both which endpoint the save goes to and whether there is a title and a 阅读权限 on
     * screen at all — a reply has neither, and the site's own editor hides both for one.
     */
    val isOpeningPost: Boolean,
)

/**
 * Re-reads one page of a thread.
 *
 * A one-method seam rather than the whole `PostRepository`: the editor's only interest in the cache
 * is putting the rewritten floor back into it, and taking the repository would make every test of
 * this file implement twenty methods it does not use.
 */
fun interface ThreadReloader {
    suspend fun reload(postId: Long, page: Int)
}

/** What the editor opens with: the floor's own Markdown, plus the thread fields a 主楼 edit needs. */
data class PostEditContent(
    val title: String,
    val permission: PostPermission,
    val body: String,
)

/**
 * 编辑 — reading a floor back as Markdown and writing it again.
 *
 * One interface over three collaborators because the composer should not have to know that loading
 * comes off a scraped page while the two saves go to two different endpoints. It is also the seam
 * the composer's tests use: the editor is the only thing they have to stand in for.
 */
interface PostEditor {
    suspend fun load(target: PostEditTarget): PostEditContent

    suspend fun save(target: PostEditTarget, content: PostEditContent)
}

class DefaultPostEditor(
    private val remote: PostRemoteDataSource,
    private val posts: PostComposerRepository,
    private val comments: CommentComposerRepository,
    private val threads: ThreadReloader,
) : PostEditor {
    override suspend fun load(target: PostEditTarget): PostEditContent {
        // No blob is how the page looks to a signed-out reader, and the only floor a signed-out
        // reader could have reached 编辑 on is one whose session expired between listing and tapping.
        val source =
            remote.loadSource(target.postId, target.page)
                ?: throw SiteException(SiteError.LoginRequired)
        val floor =
            source.floor(target.commentId)
                ?: throw SiteException(SiteError.Unparsable)
        return PostEditContent(
            title = source.title,
            permission = PostPermission(source.rank),
            body = floor.markdown,
        )
    }

    override suspend fun save(target: PostEditTarget, content: PostEditContent) {
        if (target.isOpeningPost) {
            posts.edit(
                PostEditSubmission(
                    postId = target.postId,
                    title = content.title,
                    body = content.body,
                    permission = content.permission,
                ),
            )
        } else {
            comments.edit(postId = target.postId, commentId = target.commentId, body = content.body)
        }
        // Neither endpoint answers with the rewritten floor, so the thread is re-read and the screen
        // behind the editor updates off Room rather than being told to. `extend`, not `refresh`: the
        // reader may have several pages loaded and a refresh would drop every one but this.
        //
        // Swallowed on failure — the save landed, and reporting a failed re-read as a failed save is
        // how a user ends up sending the same edit twice.
        runCatchingExceptCancellation { threads.reload(target.postId, target.page) }
    }
}
