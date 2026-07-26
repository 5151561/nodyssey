package io.github.nsreader.data.composer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.nsreader.core.AppClock
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.core.net.NodeSeekException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.commentComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "comment-composer",
)

/**
 * An unsent reply.
 *
 * [quotedFloor]/[quotedText] survive the round trip so reopening the sheet restores the quote chip
 * along with the text — a reply written against a quote reads as a non sequitur without it.
 */
@Serializable
data class CommentDraft(
    val body: String = "",
    val quotedFloor: Int? = null,
    val quotedAuthor: String? = null,
    val quotedText: String? = null,
    val savedAtMillis: Long = 0L,
) {
    val hasContent: Boolean get() = body.isNotBlank()
}

data class CommentSubmission(
    val postId: Long,
    val body: String,
    val quotedFloor: Int? = null,
)

interface CommentComposerRepository {
    fun draft(postId: Long): Flow<CommentDraft?>

    suspend fun saveDraft(postId: Long, draft: CommentDraft)

    suspend fun deleteDraft(postId: Long)

    /** @return the new floor number when the site reports one. */
    suspend fun publish(submission: CommentSubmission): Int?
}

/**
 * Drafts are real and local; publishing is not wired up yet.
 *
 * NodeSeek's comment endpoint has not been captured from a signed-in session, and this sandbox
 * cannot reach the site to find out — every request comes back as a Cloudflare challenge. Rather
 * than post to a guessed URL (which would fail in a way indistinguishable from an expired session,
 * or worse, half-succeed), [publish] fails with a message that says what is actually missing. The
 * editor's failure path is the same one a network error takes, so wiring the real request in later
 * changes this class and nothing above it.
 */
class LocalCommentComposerRepository(
    context: Context,
    private val clock: AppClock,
) : CommentComposerRepository {
    private val dataStore = context.applicationContext.commentComposerDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override fun draft(postId: Long): Flow<CommentDraft?> = dataStore.data.map { preferences ->
        preferences[key(postId)]?.let { encoded ->
            runCatching { json.decodeFromString<CommentDraft>(encoded) }.getOrNull()
        }
    }

    override suspend fun saveDraft(postId: Long, draft: CommentDraft) {
        dataStore.edit { preferences ->
            preferences[key(postId)] = json.encodeToString(draft.copy(savedAtMillis = clock.nowMillis()))
        }
    }

    override suspend fun deleteDraft(postId: Long) {
        dataStore.edit { preferences -> preferences.remove(key(postId)) }
    }

    override suspend fun publish(submission: CommentSubmission): Int? = throw NodeSeekException(
        error = NodeSeekError.Unknown,
        detail = UNAVAILABLE_DETAIL,
    )

    private fun key(postId: Long) = stringPreferencesKey("draft-$postId")

    companion object {
        const val UNAVAILABLE_DETAIL = "评论发布接口尚未接入"
    }
}
