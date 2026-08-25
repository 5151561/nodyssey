package io.github.nodyssey.data.session

import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.NoOpPostRepository
import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineSettings
import io.github.nodyssey.data.OfflineState
import io.github.nodyssey.data.OfflineUsage
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.UserProfile
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.CommentDraft
import io.github.nodyssey.data.composer.CommentSubmission
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostDraft
import io.github.nodyssey.data.composer.PostSubmission
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.testPreferenceStore
import io.github.plaza.core.net.SessionCookies
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-out list, exercised end to end where it can be and recorded where it cannot.
 *
 * The list exists because it kept drifting — the offline library, the draft stores and the poll
 * bookkeeping were each added after the two sign-out buttons were written, and none of them were
 * cleared on the way out. Each assertion here is one of those regressions pinned.
 */
class DefaultAccountSignOutTest {

    @Test
    fun `signing out clears every store the account left data in, then the session`() = runTest {
        val cookies = FakeSessionCookieStore()
        cookies.setCookie(NodeSeekSite.BASE_URL, "session=abc123")
        val session = SessionRepository(SessionCookies(NodeSeekSite.CONFIG, cookies))
        session.sync()
        assertTrue(session.state.value.isSignedIn)

        val posts = RecordingPostRepository()
        val profiles = RecordingProfileRepository()
        val offline = RecordingOfflineLibrary()
        val postDrafts = RecordingPostComposer()
        val commentDrafts = RecordingCommentComposer()
        val settings = SettingsRepository(testPreferenceStore(backgroundScope, name = "sign-out"))
        settings.setNotificationSeenCounts(NotificationCounts(replies = 40, mentions = 3, messages = 7))

        DefaultAccountSignOut(
            posts = posts,
            profiles = profiles,
            offline = offline,
            postDrafts = postDrafts,
            commentDrafts = commentDrafts,
            settings = settings,
            session = session,
        ).signOut()

        // The downloaded copies: fetched signed in, and the row the review pinned this on —
        // "logout must fail closed" covers bytes on disk too.
        assertTrue(offline.cleared)
        // The account's own unsent words, both editors.
        assertTrue(postDrafts.draftDeleted)
        assertTrue(commentDrafts.allDraftsDeleted)
        // The poll baseline: account A's 40-reply watermark would swallow account B's first unread.
        assertEquals(NotificationCounts(), settings.notificationSeenCounts())
        // Content cleared, and the session itself published as signed out — cookies actually gone.
        assertTrue(posts.sessionDataCleared)
        assertTrue(profiles.cacheCleared)
        assertFalse(session.state.value.isSignedIn)
        assertNull(cookies.cookieHeader(NodeSeekSite.BASE_URL))
    }
}

private class RecordingPostRepository : PostRepository by NoOpPostRepository() {
    var sessionDataCleared = false

    override suspend fun clearSessionData() {
        sessionDataCleared = true
    }
}

private class RecordingProfileRepository : ProfileRepository {
    var cacheCleared = false

    override suspend fun clearCachedProfile() {
        cacheCleared = true
    }

    override suspend fun profile(refresh: Boolean): UserProfile = error("not part of signing out")

    override suspend fun profile(uid: Long): UserProfile = error("not part of signing out")
}

private class RecordingOfflineLibrary : OfflineLibrary {
    var cleared = false

    override val isAvailable: Boolean = true
    override val states: Flow<Map<Long, OfflineState>> = flowOf(emptyMap())
    override val usage: Flow<OfflineUsage> = flowOf(OfflineUsage())
    override val settings: Flow<OfflineSettings> = MutableStateFlow(OfflineSettings())

    override suspend fun download(postIds: Collection<Long>) = Unit

    override suspend fun noteReplyCounts(counts: Map<Long, Int>) = Unit

    override suspend fun estimateBytes(postIds: Collection<Long>): Long? = null

    override suspend fun cancel(postId: Long) = Unit

    override suspend fun clearAll() {
        cleared = true
    }

    override suspend fun updateSettings(settings: OfflineSettings) = Unit
}

private class RecordingPostComposer : PostComposerRepository {
    var draftDeleted = false

    override val draft: Flow<PostDraft?> = flowOf(null)

    override suspend fun saveDraft(draft: PostDraft) = Unit

    override suspend fun deleteDraft() {
        draftDeleted = true
    }

    override suspend fun publish(submission: PostSubmission): Long? = null
}

private class RecordingCommentComposer : CommentComposerRepository {
    var allDraftsDeleted = false

    override fun draft(postId: Long): Flow<CommentDraft?> = flowOf(null)

    override suspend fun saveDraft(postId: Long, draft: CommentDraft) = Unit

    override suspend fun deleteDraft(postId: Long) = Unit

    override suspend fun deleteAllDrafts() {
        allDraftsDeleted = true
    }

    override suspend fun publish(submission: CommentSubmission): Int? = null
}
