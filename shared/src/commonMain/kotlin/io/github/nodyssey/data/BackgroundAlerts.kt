package io.github.nodyssey.data

import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.model.PostSummary
import io.github.nodyssey.model.TitleKeywordKind

/** A new thread whose title holds one of the reader's 提醒关键词. */
data class KeywordHit(
    val postId: Long,
    val title: String,
    /** The keyword it matched, as stored — lower-cased. */
    val keyword: String,
)

/** A followed thread that has gained [newReplies] since the reader was last told about it. */
data class TrackedThreadUpdate(
    val postId: Long,
    val title: String,
    val newReplies: Int,
)

/**
 * 提醒关键词's one decision, kept apart from any request so it can be checked on its own.
 *
 * [baseline] is the newest thread id a previous run already looked at. Null means this is the first
 * run, which only records where "new" starts: notifying on it would announce a whole page of threads
 * that were posted before the reader added a single word.
 */
fun keywordHits(
    baseline: Long?,
    latest: List<PostSummary>,
    keywords: List<String>,
): Pair<List<KeywordHit>, Long?> {
    val newest = latest.maxOfOrNull { it.postId }
    val nextBaseline = listOfNotNull(baseline, newest).maxOrNull()
    if (baseline == null) return emptyList<KeywordHit>() to nextBaseline
    val hits =
        latest
            // Blocked rows are the site saying "not for this reader"; a notification would undo that.
            .filter { it.postId > baseline && !it.isBlocked }
            .mapNotNull { post ->
                val title = post.title.lowercase()
                keywords.firstOrNull { title.contains(it) }?.let { KeywordHit(post.postId, post.title, it) }
            }.sortedBy { it.postId }
    return hits to nextBaseline
}

/**
 * 追踪新回复's one decision: which followed threads show more replies on the list than the reader
 * has been told about.
 *
 * A negative [TrackedThread.lastKnownCount] is "followed before the count was known" — the
 * first sighting sets it without a notification, the same rule [keywordHits] has for its first run.
 * A thread absent from [listed] says nothing either way and is left as it was.
 */
fun trackedThreadUpdates(
    tracked: List<TrackedThread>,
    listed: List<PostSummary>,
): List<Pair<TrackedThread, Int>> {
    val counts = listed.associate { it.postId to it.commentCount }
    return tracked.mapNotNull { thread ->
        val count = counts[thread.postId] ?: return@mapNotNull null
        if (count > thread.lastKnownCount) thread to count else null
    }
}

/**
 * The background poll's own half: reads the lists, decides, records how far it got, and returns what
 * is worth a notification. Posting them is the platform's business.
 *
 * Neither check needs an account — the lists are public — so this runs signed out as well.
 * [loadList] is the site's thread list as the network hands it over, never the Room cache: a stale
 * cached page would make an old thread look new.
 */
class BackgroundAlertChecker(
    private val loadList: suspend (sort: FeedSort, page: Int) -> List<PostSummary>,
    private val keywords: TitleKeywordStore,
    private val tracked: TrackedThreadStore,
    private val settings: SettingsRepository,
) {
    /** One request, and none at all while there is no keyword to look for. */
    suspend fun checkKeywords(): List<KeywordHit> {
        val words = keywords.current(TitleKeywordKind.ALERT)
        if (words.isEmpty()) return emptyList()
        val (hits, baseline) = keywordHits(settings.keywordAlertBaseline(), loadList(FeedSort.POST_TIME, 1), words)
        baseline?.let { settings.setKeywordAlertBaseline(it) }
        return hits
    }

    /**
     * Two requests — the first two pages sorted by latest reply, which is where a thread that just
     * gained one sits — and none while nothing is followed.
     */
    suspend fun checkTrackedThreads(): List<TrackedThreadUpdate> {
        val following = tracked.all()
        if (following.isEmpty()) return emptyList()
        val listed = (1..TRACKED_PAGES).flatMap { page -> loadList(FeedSort.LAST_REPLY, page) }
        return trackedThreadUpdates(following, listed).mapNotNull { (thread, count) ->
            tracked.advance(thread.postId, count)
            // First sighting: recorded, not announced.
            if (thread.lastKnownCount < 0) return@mapNotNull null
            TrackedThreadUpdate(thread.postId, thread.title, count - thread.lastKnownCount)
        }
    }

    companion object {
        const val TRACKED_PAGES = 2
    }
}
