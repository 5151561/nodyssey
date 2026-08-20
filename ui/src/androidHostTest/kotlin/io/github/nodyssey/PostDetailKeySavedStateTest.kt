package io.github.nodyssey

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The thread key survives process death with everything it carries.
 *
 * `rememberNavBackStack` saves the stack by encoding every key through
 * `androidx.savedstate.serialization`, which is what this exercises. [ThreadPreview] is the first
 * nested class any key in this app holds — the rest are strings, longs and enums — so "a data class
 * inside a key round-trips" is a fact worth pinning rather than assuming. Losing it would not throw:
 * the thread would simply come back from a rotation with four grey bars where its own title, board,
 * avatar and author had been.
 */
@RunWith(RobolectricTestRunner::class)
class PostDetailKeySavedStateTest {
    @Test
    fun `a thread key carries what the row told it through saved state`() {
        val key =
            PostDetailKey(
                postId = 703863,
                page = 4,
                preview =
                ThreadPreview(
                    title = "NodeSeek 签到脚本更新",
                    authorName = "someone",
                    avatarUrl = "https://www.nodeseek.com/avatar/1.png",
                    categoryTitle = "技术",
                    categorySlug = "tech",
                ),
            )

        val restored = decodeFromSavedState<PostDetailKey>(encodeToSavedState(key))

        assertEquals(key, restored)
    }

    /** A deep link or a notification has no row behind it, and null has to survive as null. */
    @Test
    fun `a thread key opened without a row round-trips without one`() {
        val key = PostDetailKey(postId = 703863, floor = "#127")

        val restored = decodeFromSavedState<PostDetailKey>(encodeToSavedState(key))

        assertEquals(key, restored)
    }
}
