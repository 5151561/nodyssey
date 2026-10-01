package io.github.nodyssey.data

import io.github.nodyssey.data.local.TitleKeywordDao
import io.github.nodyssey.data.local.TitleKeywordEntity
import io.github.nodyssey.data.local.TrackedThreadDao
import io.github.nodyssey.data.local.TrackedThreadEntity
import io.github.nodyssey.data.local.UserNoteDao
import io.github.nodyssey.data.local.UserNoteEntity
import io.github.nodyssey.model.TitleKeywordKind
import io.github.plaza.core.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 备注: the reader's own line about a member, keyed by uid.
 *
 * Kept on this device only and never sent anywhere — the site has no such field, and a note is the
 * kind of thing one writes precisely because nobody else will read it.
 */
class UserNoteStore(
    private val dao: UserNoteDao,
    private val clock: AppClock,
) {
    /** Every note, as uid → text. One map rather than a lookup per row: a feed draws fifty names. */
    val notes: Flow<Map<Long, String>> = dao.observeAll().map { rows -> rows.associate { it.uid to it.note } }

    /** Saves [note] for [uid]; a blank one deletes it, which is how the editor clears a note. */
    suspend fun setNote(
        uid: Long,
        note: String,
    ) {
        val trimmed = note.trim().take(MAX_NOTE_LENGTH)
        if (trimmed.isEmpty()) {
            dao.delete(uid)
        } else {
            dao.upsert(UserNoteEntity(uid = uid, note = trimmed, updatedAtMillis = clock.nowMillis()))
        }
    }

    companion object {
        /** A tag beside a name, not a diary — long enough for "出过两次鸽子，慎重". */
        const val MAX_NOTE_LENGTH = 40
    }
}

/**
 * The words the reader watches thread titles for — to hide them ([TitleKeywordKind.BLOCK]) or to be
 * told about them ([TitleKeywordKind.ALERT]).
 *
 * Stored lower-cased, because that is the form the feed query and the alert poll both match against.
 */
class TitleKeywordStore(
    private val dao: TitleKeywordDao,
    private val clock: AppClock,
) {
    fun keywords(kind: TitleKeywordKind): Flow<List<String>> = dao.observe(kind).map { rows -> rows.map { it.keyword } }

    suspend fun current(kind: TitleKeywordKind): List<String> = dao.list(kind).map { it.keyword }

    /** False when [keyword] is blank once normalized — nothing was added. */
    suspend fun add(
        kind: TitleKeywordKind,
        keyword: String,
    ): Boolean {
        val normalized = normalizeKeyword(keyword) ?: return false
        dao.insert(TitleKeywordEntity(kind = kind, keyword = normalized, addedAtMillis = clock.nowMillis()))
        return true
    }

    suspend fun remove(
        kind: TitleKeywordKind,
        keyword: String,
    ) = dao.delete(kind, keyword)

    companion object {
        const val MAX_KEYWORD_LENGTH = 30

        /** The stored form of [keyword], or null when there is nothing left of it to match. */
        fun normalizeKeyword(keyword: String): String? = keyword.trim().lowercase().take(MAX_KEYWORD_LENGTH).ifEmpty { null }
    }
}

/**
 * A followed thread. [lastKnownCount] is the reply count the reader has been told about; negative
 * while it is not known yet — see [TrackedThreadStore.track].
 */
data class TrackedThread(
    val postId: Long,
    val title: String,
    val lastKnownCount: Int,
)

/** 追踪新回复 — the threads the reader wants to hear about, and how far they have been told. */
class TrackedThreadStore(
    private val dao: TrackedThreadDao,
    private val clock: AppClock,
) {
    val tracked: Flow<List<TrackedThread>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun isTracked(postId: Long): Flow<Boolean> = dao.observeIsTracked(postId)

    suspend fun all(): List<TrackedThread> = dao.all().map { it.toModel() }

    /**
     * False when the list is already at [MAX_TRACKED] and nothing was added.
     *
     * [commentCount] is null when the caller cannot know the total — a thread read from page 1 of 40
     * has seen ten floors, not four hundred. The next poll then takes the count it finds as the
     * baseline instead of announcing four hundred new replies.
     */
    suspend fun track(
        postId: Long,
        title: String,
        commentCount: Int?,
    ): Boolean {
        if (dao.count() >= MAX_TRACKED) return false
        dao.upsert(
            TrackedThreadEntity(
                postId = postId,
                title = title,
                lastKnownCount = commentCount ?: UNKNOWN_COUNT,
                addedAtMillis = clock.nowMillis(),
            ),
        )
        return true
    }

    suspend fun untrack(postId: Long) = dao.delete(postId)

    /** The reader now knows about [count] replies — by a notification, or by reading the thread. */
    suspend fun advance(
        postId: Long,
        count: Int,
    ) = dao.advance(postId, count)

    companion object {
        const val MAX_TRACKED = 50

        /** [TrackedThread.lastKnownCount] before any count has been seen. */
        const val UNKNOWN_COUNT = -1
    }
}

private fun TrackedThreadEntity.toModel() = TrackedThread(postId, title, lastKnownCount)
