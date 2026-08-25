package io.github.nodyssey.data.session

import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.settings.SettingsRepository

/**
 * The one sign-out. Both screens that offer it call this rather than each keeping its own list of
 * stores to clear — see [DefaultAccountSignOut] for the list and why it lives in one place.
 */
fun interface AccountSignOut {
    suspend fun signOut()
}

/**
 * Everything sign-out takes with it, in one place.
 *
 * One place, because the list is what kept drifting: the post cache learned to clear itself when
 * sessions were first modelled, and then the offline library, the two draft stores and the poll
 * worker's seen counts were each added later — none of which came back to extend the two screens
 * that sign out. The rule they were breaking is already written on [PostRepository.clearSessionData]:
 * logout must fail closed, because nothing can prove which stored rows came from a signed-in page.
 * A new store that holds the account's data gets a line *here*, and both sign-out buttons have it.
 *
 * Order matters at both ends. Authenticated content goes first, cookies last: other tabs keep their
 * Navigation 3 entries alive, so clearing cookies alone would leave their already-rendered private
 * rows readable. And [SessionRepository.signOut] is what publishes the signed-out state, so
 * everything the next account must not see has to be gone before it runs.
 */
class DefaultAccountSignOut(
    private val posts: PostRepository,
    private val profiles: ProfileRepository,
    /** Downloaded thread bodies and images — fetched signed in, readable signed out otherwise. */
    private val offline: OfflineLibrary,
    private val postDrafts: PostComposerRepository,
    private val commentDrafts: CommentComposerRepository,
    /** Owns the poll worker's seen counts; stale ones would swallow the next account's first unread. */
    private val settings: SettingsRepository,
    private val session: SessionRepository,
) : AccountSignOut {
    override suspend fun signOut() {
        posts.clearSessionData()
        profiles.clearCachedProfile()
        offline.clearAll()
        postDrafts.deleteDraft()
        commentDrafts.deleteAllDrafts()
        settings.setNotificationSeenCounts(NotificationCounts())
        session.signOut()
    }
}
