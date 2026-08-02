package io.github.nodyssey.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.nodyssey.data.Board
import io.github.nodyssey.model.PostContent
import io.github.nodyssey.model.PostSummary

/*
 * The database schema.
 *
 * Two shapes are worth explaining, because both are deliberate and neither is the obvious first
 * draft:
 *
 * 1. Post identity is separate from feed membership. A post appears on the mixed front page *and* on
 *    its own board, and its comment count keeps changing. If the row lived inside a feed then the
 *    same post would be stored twice and the two copies would disagree. PostEntity holds the post
 *    once; FeedPositionEntity records "post P sits at index N of feed F".
 *
 * 2. Feed order is an explicit column, not insertion order. NodeSeek sorts by last activity, so the
 *    order is not derivable from anything stored in the post itself. `sortIndex` is assigned as pages
 *    arrive and is the only thing the paging query trusts.
 */

// ---------------------------------------------------------------------------------------------
// Boards
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "boards")
data class BoardEntity(
    @PrimaryKey val slug: String,
    val title: String,
    val description: String?,
    val adminOnly: Boolean,
    /** Server order. The board strip must not reshuffle itself between launches. */
    val sortIndex: Int,
)

fun BoardEntity.toBoard() =
    Board(
        slug = slug,
        title = title,
        description = description,
        adminOnly = adminOnly,
    )

fun Board.toEntity(sortIndex: Int) =
    BoardEntity(
        // Only real boards are persisted; the synthetic front page is prepended by the repository.
        slug = requireNotNull(slug) { "the front page is synthetic and must not be stored" },
        title = title,
        description = description,
        adminOnly = adminOnly,
        sortIndex = sortIndex,
    )

// ---------------------------------------------------------------------------------------------
// Post list
// ---------------------------------------------------------------------------------------------

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val postId: Long,
    val title: String,
    val authorName: String,
    val authorUid: Long?,
    val avatarUrl: String?,
    val categoryTitle: String?,
    val categorySlug: String?,
    val viewCount: Int?,
    val commentCount: Int?,
    val lastActiveText: String?,
    val lastActiveTitle: String?,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val lockLevel: Int?,
    val cachedAtMillis: Long,
)

fun PostEntity.toSummary() =
    PostSummary(
        postId = postId,
        title = title,
        authorName = authorName,
        authorUid = authorUid,
        avatarUrl = avatarUrl,
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        viewCount = viewCount,
        commentCount = commentCount,
        lastActiveText = lastActiveText,
        lastActiveTitle = lastActiveTitle,
        isPinned = isPinned,
        isLocked = isLocked,
        lockLevel = lockLevel,
    )

fun PostSummary.toEntity(cachedAtMillis: Long) =
    PostEntity(
        postId = postId,
        title = title,
        authorName = authorName,
        authorUid = authorUid,
        avatarUrl = avatarUrl,
        categoryTitle = categoryTitle,
        categorySlug = categorySlug,
        viewCount = viewCount,
        commentCount = commentCount,
        lastActiveText = lastActiveText,
        lastActiveTitle = lastActiveTitle,
        isPinned = isPinned,
        isLocked = isLocked,
        lockLevel = lockLevel,
        cachedAtMillis = cachedAtMillis,
    )

/**
 * One post's slot in one feed.
 *
 * The foreign key onto [PostEntity] cascades: a post that gets evicted must not leave a position
 * row pointing at nothing, or the paging query would return fewer items than it counted.
 */
@Entity(
    tableName = "feed_positions",
    primaryKeys = ["feedKey", "postId"],
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["postId"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("postId"), Index(value = ["feedKey", "sortIndex"])],
)
data class FeedPositionEntity(
    val feedKey: String,
    val postId: Long,
    val sortIndex: Int,
)

/**
 * Where the next page of a feed starts, plus when the feed was last written.
 *
 * `nextPage == null` means the site said there is nothing after what we already hold, which is what
 * lets the mediator report end-of-pagination without issuing a request that would return an empty
 * page.
 */
@Entity(tableName = "feed_remote_keys")
data class FeedRemoteKeyEntity(
    @PrimaryKey val feedKey: String,
    val nextPage: Int?,
    val refreshedAtMillis: Long,
)

// ---------------------------------------------------------------------------------------------
// Post detail
// ---------------------------------------------------------------------------------------------

/**
 * A cached thread, plus which slice of it is cached.
 *
 * [firstLoadedPage]..[lastLoadedPage] is a *window*, not a prefix: opening a notification for #127
 * fetches the one page that floor lives on and nothing before it, so the window can start anywhere.
 * Both ends are stored because both survive process death and neither is derivable from the comment
 * rows — a page whose comments were all deleted still counts as loaded.
 */
@Entity(tableName = "post_details")
data class PostDetailEntity(
    @PrimaryKey val postId: Long,
    val title: String,
    /** Null when only later comment pages have been fetched — page 1 is the one that carries it. */
    val body: PostContent?,
    val totalPages: Int,
    /** The window's first page. 1 for a thread read from the top, which is most of them. */
    val firstLoadedPage: Int = 1,
    /**
     * The window's last page, so "is there more" survives process death.
     *
     * The column keeps its old name: it used to be a count of a prefix, which for a window starting
     * at page 1 is the same number, so v4 rows migrate by adding [firstLoadedPage] and nothing else.
     */
    @ColumnInfo(name = "loadedPages") val lastLoadedPage: Int,
    val cachedAtMillis: Long,
)

/**
 * A single comment, addressed by where it appeared rather than by its own id.
 *
 * NodeSeek does not give every comment a stable id in the markup, so `(postId, page, position)` is
 * the only key that lets a re-fetch of page 3 replace exactly the old page 3 and nothing else.
 */
@Entity(
    tableName = "post_comments",
    primaryKeys = ["postId", "page", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PostDetailEntity::class,
            parentColumns = ["postId"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CommentEntity(
    val postId: Long,
    val page: Int,
    val position: Int,
    val content: PostContent,
)

// ---------------------------------------------------------------------------------------------
// Read state
// ---------------------------------------------------------------------------------------------

/**
 * What the user has already seen.
 *
 * [lastSeenCommentCount] is stored alongside the timestamp so the list can say "4 new replies"
 * rather than only "visited" — the count at the time of reading is the only baseline that makes the
 * difference meaningful.
 */
@Entity(tableName = "post_read_marks")
data class ReadMarkEntity(
    @PrimaryKey val postId: Long,
    val lastReadAtMillis: Long,
    val lastSeenCommentCount: Int,
)

/**
 * Whether the current post cache may contain content fetched with an authenticated cookie jar.
 *
 * This survives process death. If the cookie expires while the app is stopped, the next reader can
 * clear the authenticated cache before exposing any Room-backed rows to a signed-out UI.
 */
@Entity(tableName = "cache_session")
data class CacheSessionEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val authenticated: Boolean,
    /** Opaque cookie fingerprint; used only to prevent one account inheriting another's cache. */
    val fingerprint: Int,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

// ---------------------------------------------------------------------------------------------
// Signed-in profile
// ---------------------------------------------------------------------------------------------

/**
 * The last profile fetched for the signed-in account.
 *
 * [sessionFingerprint] is deliberately part of the row instead of trusting the last known uid. The
 * cookie jar is the authority for which account is active, so a profile is readable only while the
 * same deciding cookies are present. Refresh replaces the whole table and sign-out deletes it.
 */
@Entity(tableName = "self_profile")
data class SelfProfileEntity(
    @PrimaryKey val sessionFingerprint: Int,
    val uid: Long,
    val name: String,
    val avatarUrl: String,
    val rank: Int?,
    val createdAt: String?,
    val chickenCount: Int?,
    val starCount: Int?,
    val streakDays: Int?,
    val bio: String?,
    val readme: String?,
    val topicCount: Int?,
    val commentCount: Int?,
    val cachedAtMillis: Long,
)
