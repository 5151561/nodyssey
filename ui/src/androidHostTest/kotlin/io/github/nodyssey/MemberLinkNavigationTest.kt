package io.github.nodyssey

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemberLinkNavigationTest {
    @Test
    fun `resolved member opens the in-app profile`() =
        runTest {
            var resolvedUid: Long? = null
            var fellBack = false

            resolveMemberLink(
                name = "Alice",
                resolveMemberUid = { 42L },
                onResolved = { resolvedUid = it },
                onFailure = { fellBack = true },
            )

            assertEquals(42L, resolvedUid)
            assertFalse(fellBack)
        }

    @Test
    fun `unresolved member opens the original member link`() =
        runTest {
            var resolved = false
            var fellBack = false

            resolveMemberLink(
                name = "Alice",
                resolveMemberUid = { null },
                onResolved = { resolved = true },
                onFailure = { fellBack = true },
            )

            assertFalse(resolved)
            assertTrue(fellBack)
        }

    @Test
    fun `lookup failure opens the original member link`() =
        runTest {
            var resolved = false
            var fellBack = false

            resolveMemberLink(
                name = "Alice",
                resolveMemberUid = { throw IllegalStateException("offline") },
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
                    resolveMemberUid = { throw CancellationException("screen left") },
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
}
