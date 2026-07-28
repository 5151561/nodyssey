package io.github.nodyssey

import io.github.nodyssey.data.UserSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemberLinkNavigationTest {
    @Test
    fun `matching member opens the in-app profile`() =
        runTest {
            var resolvedUid: Long? = null
            var fellBack = false

            resolveMemberLink(
                name = "Alice",
                searchUsers = { listOf(user(uid = 42, name = "alice")) },
                onResolved = { resolvedUid = it },
                onFailure = { fellBack = true },
            )

            assertEquals(42L, resolvedUid)
            assertFalse(fellBack)
        }

    @Test
    fun `search failure opens the original member link`() =
        runTest {
            var resolved = false
            var fellBack = false

            resolveMemberLink(
                name = "Alice",
                searchUsers = { throw IllegalStateException("offline") },
                onResolved = { resolved = true },
                onFailure = { fellBack = true },
            )

            assertFalse(resolved)
            assertTrue(fellBack)
        }

    @Test
    fun `cancellation neither navigates nor opens the browser`() =
        runTest {
            var resolved = false
            var fellBack = false

            try {
                resolveMemberLink(
                    name = "Alice",
                    searchUsers = { throw CancellationException("screen left") },
                    onResolved = { resolved = true },
                    onFailure = { fellBack = true },
                )
                fail("CancellationException should propagate")
            } catch (_: CancellationException) {
                // Expected: leaving the screen cancels the whole navigation attempt.
            }

            assertFalse(resolved)
            assertFalse(fellBack)
        }

    private fun user(uid: Long, name: String) =
        UserSearchResult(
            uid = uid,
            name = name,
            avatarUrl = null,
            level = null,
            bio = null,
            joinedText = null,
            topicCount = null,
            commentCount = null,
        )
}
