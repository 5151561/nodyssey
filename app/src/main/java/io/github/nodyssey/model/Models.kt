package io.github.nodyssey.model

import io.github.plaza.core.ansi.AnsiSpan
import io.github.plaza.core.richtext.RichNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row in a topic list. */
data class PostSummary(
    val postId: Long,
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
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    /** The reader level a locked post demands, when the list shows one next to the lock icon. */
    val lockLevel: Int? = null,
    /**
     * The author is on this account's block list, as the *server* decided.
     *
     * NodeSeek blocks server-side and sends the row anyway, marked `class="blocked-post"`, which its
     * own stylesheet hides with `display:none`. The app never derives this from a local list: the
     * block list is account state, so a row is blocked because the site said so or not at all.
     */
    val isBlocked: Boolean = false,
)

data class PostListPage(
    val posts: List<PostSummary>,
    val page: Int,
    val hasNextPage: Boolean,
    /**
     * Highest page the pager offers, or [page] when it offers none.
     *
     * The feed ignores this — it scrolls — but the lists the site pages by number (curated threads,
     * the moderation log) need the total to draw "1 … 18", and it is only knowable from the markup we
     * are already holding.
     */
    val totalPages: Int = page,
)

/**
 * The comments on one page of a thread, plus the opening post if that page carried it.
 *
 * [body] is null on page 2 and later, because NodeSeek renders the opening post only on page 1.
 * That distinction is not cosmetic: the cache has to tell "this page did not include the body" apart
 * from "the body is empty", or appending page 2 would overwrite the stored body with nothing.
 */
data class PostDetail(
    val postId: Long,
    val title: String,
    val body: PostContent?,
    val comments: List<PostContent>,
    val page: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    /**
     * Whether this account has the thread collected, per the page's own `__config__` blob.
     *
     * Null when the page carried no blob — a different claim from "not collected", and the reason
     * the star can be missing rather than merely unlit.
     */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
)

/**
 * A thread as currently held in the database — what the detail screen renders, online or not.
 *
 * Unlike [PostDetail] this is not one fetch: [comments] accumulates the pages read so far, which is
 * how the thread reads as one scroll on a phone. Those pages are contiguous but need not start at
 * the beginning — jumping to page 12 loads page 12, not pages 1 through 12 — so the slice is
 * described by both its ends.
 */
data class ThreadSnapshot(
    val postId: Long,
    val title: String,
    val body: PostContent?,
    val comments: List<PostContent>,
    val firstLoadedPage: Int,
    val lastLoadedPage: Int,
    val totalPages: Int,
    val cachedAtMillis: Long,
    /** The site page each comment came from, index-aligned with [comments]. */
    val commentPages: List<Int> = emptyList(),
    /** See [PostDetail.collected]; null survives into the cache for the same reason. */
    val collected: Boolean? = null,
    val collectionCount: Int? = null,
) {
    val hasNextPage: Boolean get() = lastLoadedPage < totalPages
}

/**
 * A post body or a single comment — both use the same markup on NodeSeek.
 *
 * Rich content is stored in the database as a JSON blob rather than as normalised tables: nothing
 * queries *into* a post body, so columns per node type would be cost without benefit.
 *
 * Every [Serializable] type below therefore carries an explicit, short [SerialName]. The discriminator
 * ends up inside rows that outlive the install, so it must not be a Kotlin class name that a refactor
 * could silently change and make old rows unreadable.
 */
@Serializable
data class PostContent(
    val commentId: Long?,
    val floor: String?,
    val authorName: String,
    val authorUid: Long?,
    val avatarUrl: String?,
    val isOriginalPoster: Boolean,
    val badges: List<String>,
    val createdAtText: String?,
    val createdAtTitle: String?,
    val categoryTitle: String?,
    val nodes: List<RichNode>,
    /*
     * Defaults are load-bearing on the fields below: rows serialized before they existed have
     * no such keys, and `encodeDefaults = false` means rows written now omit them when false/null.
     */
    /** The header's `edited Xmin ago` marker (additions.md §1.4) — posts and comments alike. */
    val isEdited: Boolean = false,
    /** The marker's own text, kept verbatim for the 已编辑 row's accessibility label. */
    val editedAtText: String? = null,
    /** The public Markdown signature rendered below this floor, empty when the user has none. */
    val signatureNodes: List<RichNode> = emptyList(),
    /** Counts and this account's own marks, or null when the page did not carry them. */
    val reactions: PostReactions? = null,
    /**
     * The author is on this account's block list, as the *server* decided.
     *
     * Comes from the page's own `__config__` (`postData.comments[].blocked`), with the markup's
     * `blocked-comment` class as a second source. Never derived on the device — see
     * [PostSummary.isBlocked].
     */
    val isBlocked: Boolean = false,
    /**
     * This account wrote this floor, as the *server* said (`__config__`'s `poster.isMe`).
     *
     * What puts 编辑 in the floor's menu, and nothing else — the same condition the site's own client
     * uses. Not derived from [authorUid]: a signed-out read carries no blob and therefore no claim of
     * ownership, and comparing uids on the device would offer the action to a reader whose session
     * has quietly expired, whose only feedback would be a refusal after they had finished typing.
     */
    val isMine: Boolean = false,
)

/**
 * The three tallies under a floor, plus whether this account has already spent one.
 *
 * Null rather than zeroes is the whole point of hanging this off [PostContent] as an object: the
 * numbers live in the page's `__config__` blob, and a page parsed without it (a signed-out read, or
 * a template change) knows *nothing* about them. Zero would render as "nobody has upvoted this",
 * which is a different claim from "we were not told".
 *
 * [liked], [disliked] and [upvoted] are one-way. The site has no remove action for them: its own
 * client refuses a second attempt client-side rather than sending one, so these three latch true and
 * the controls stay spent for good.
 */
@Serializable
data class PostReactions(
    /** 加鸡腿 — costs the reader one chicken leg, or nothing while the daily free allowance lasts. */
    val likeCount: Int = 0,
    /** 反对 — costs the reader two chicken legs. */
    val dislikeCount: Int = 0,
    /** 点赞 — free, and pays the author in stardust rather than chicken legs. */
    val upvoteCount: Int = 0,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val upvoted: Boolean = false,
)

/**
 * One of the three marks, named for what it does to the reader rather than for the site's wire word.
 *
 * The wire words are a trap worth keeping at arm's length: the site's `like` is 加鸡腿 and costs a
 * chicken leg, while the free approving one is `upvote`. [apiAction] is the only place the two
 * vocabularies meet.
 */
enum class ReactionAction(
    val apiAction: String,
    /** Chicken legs this spends. Zero for [Upvote], which pays the author in stardust instead. */
    val chickenLegCost: Int,
) {
    Upvote(apiAction = "upvote", chickenLegCost = 0),
    ChickenLeg(apiAction = "like", chickenLegCost = 1),
    Dislike(apiAction = "dislike", chickenLegCost = 2),
}

/** Whether this account has already spent [action] on the floor. */
fun PostReactions.hasSpent(action: ReactionAction): Boolean =
    when (action) {
        ReactionAction.Upvote -> upvoted
        ReactionAction.ChickenLeg -> liked
        ReactionAction.Dislike -> disliked
    }

/** The tally [action] moves. */
fun PostReactions.countOf(action: ReactionAction): Int =
    when (action) {
        ReactionAction.Upvote -> upvoteCount
        ReactionAction.ChickenLeg -> likeCount
        ReactionAction.Dislike -> dislikeCount
    }

/**
 * The Markdown one page of a thread was written in, for the editor to load.
 *
 * Read from the post page's own `__config__` — see [io.github.nodyssey.core.html.PostSourceParser]
 * for why the rendered HTML cannot stand in for it. [title] and [rank] describe the thread and are
 * only meaningful when the 主楼 is being edited; the site requires both back on every such save, so
 * an editor that never showed them would still have to send them.
 */
data class PostSource(
    val postId: Long,
    val title: String,
    /** 阅读权限 as the wire spells it; see `PostPermission`. */
    val rank: Int,
    val floors: List<PostSourceFloor>,
) {
    fun floor(commentId: Long): PostSourceFloor? = floors.firstOrNull { it.commentId == commentId }
}

data class PostSourceFloor(
    val commentId: Long,
    val isOpeningPost: Boolean,
    val markdown: String,
    val isMine: Boolean,
)
