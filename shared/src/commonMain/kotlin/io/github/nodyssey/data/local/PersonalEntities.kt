package io.github.nodyssey.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.nodyssey.model.TitleKeywordKind

// ---------------------------------------------------------------------------------------------
// What the person holding this phone has decided, as opposed to what the site said
// ---------------------------------------------------------------------------------------------
//
// Every table in this file belongs to the device's owner rather than to the signed-in account, which
// is why none of them is cleared when the session changes (see `clearPostData`): a note that a seller
// once let you down is still true after you sign in with another account. The database file is per
// site already, so a NodeSeek note never shows up against a DeepFlood member with the same uid.

/** 备注 — a line of the reader's own about one member, drawn beside their name everywhere. */
@Entity(tableName = "user_notes")
data class UserNoteEntity(
    @PrimaryKey val uid: Long,
    val note: String,
    val updatedAtMillis: Long,
)

/**
 * One word the reader watches thread titles for.
 *
 * [keyword] is stored lower-cased and matched with `instr(lower(title), keyword)`, so neither side
 * needs `LIKE` escaping and matching ignores ASCII case — SQLite's `lower` leaves CJK alone, which
 * has no case to ignore anyway.
 */
@Entity(tableName = "title_keywords", primaryKeys = ["kind", "keyword"])
data class TitleKeywordEntity(
    val kind: TitleKeywordKind,
    val keyword: String,
    val addedAtMillis: Long,
)

/**
 * 追踪新回复 — a thread the reader asked to hear about when somebody replies.
 *
 * [lastKnownCount] is the reply count the reader has already been told about, by a notification or
 * by reading the thread; a poll that finds more is what posts one.
 */
@Entity(tableName = "tracked_threads")
data class TrackedThreadEntity(
    @PrimaryKey val postId: Long,
    val title: String,
    val lastKnownCount: Int,
    val addedAtMillis: Long,
)
